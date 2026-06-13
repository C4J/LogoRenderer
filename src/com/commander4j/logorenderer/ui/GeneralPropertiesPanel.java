package com.commander4j.logorenderer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.commander4j.gui.JTextField4j;

/**
 * Inline editor for data commands: FD, FF, UD, UF, VAR, UVAR, BD, QUE, FL.
 * Populated from the currently selected source line in {@link LlfLinePanel}.
 * Edits are applied back to the source line text.
 *
 * <p>The host owns shared Apply/Cancel buttons and delegates to
 * {@link #applyPending()} / {@link #discardPending()}.
 */
public final class GeneralPropertiesPanel extends JPanel
{

	private static final long serialVersionUID = 1L;
	private static final int ROW_H = 26;

	/** Commands this panel can edit. */
	private static final Set<String> SUPPORTED = Set.of(
			"FD", "FF", "UD", "UF", "VAR", "UVAR", "BD", "QUE", "FL");

	/** Human-readable descriptions for each command type. */
	private static String describeCommand(String cmd)
	{
		return switch (cmd) {
			case "FD"   -> "Field Data";
			case "FF"   -> "Field Data (Flash)";
			case "UD"   -> "User Data";
			case "UF"   -> "User Data (Flash)";
			case "VAR"  -> "Variable Formula";
			case "UVAR" -> "User Variable";
			case "BD"   -> "Block Data";
			case "QUE"  -> "Operator Prompt";
			case "FL"   -> "Field Link";
			default     -> cmd;
		};
	}

	// --- widgets ---
	private final JTextField4j fldCommand;
	private final JTextField4j fldId;
	private final JLabel       lblId;
	private final JTextField4j fldValue;
	private final JLabel       lblValue;

	// --- state ---
	private Runnable dirtyListener;
	private BiConsumer<Integer, String> lineCallback;

	private int      boundIndex = -1;
	private String[] originalParts;

	private String[] initialValues;
	private boolean  loading;

	private final List<Component> editableWidgets = new ArrayList<>();

	public GeneralPropertiesPanel()
	{
		setLayout(new BorderLayout());

		fldCommand = readOnlyField(130);
		fldId      = text(60);
		fldId.setHorizontalAlignment(SwingConstants.CENTER);
		// Value field uses no fixed width — it stretches to fill via GridBag HORIZONTAL
		fldValue   = new JTextField4j();
		fldValue.setPreferredSize(new Dimension(300, ROW_H));
		fldValue.setMinimumSize(new Dimension(100, ROW_H));

		// Spinner on the field-ID
		JPanel spnId = spinnerWrap(fldId, 1);

		editableWidgets.addAll(List.of(fldId, fldValue));

		// Dirty tracking
		DocumentListener dirty = new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e)  { checkDirty(); }
			@Override public void removeUpdate(DocumentEvent e)  { checkDirty(); }
			@Override public void changedUpdate(DocumentEvent e) { checkDirty(); }
		};
		fldId.getDocument().addDocumentListener(dirty);
		fldValue.getDocument().addDocumentListener(dirty);

		// Layout grid
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setBorder(BorderFactory.createEmptyBorder(2, 4, 10, 4));

		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);

		// Row 0: Command type (read-only) + Field ID (with spinner)
		c.gridy = 0;
		c.gridx = 0; c.anchor = GridBagConstraints.EAST;
		grid.add(smallLabel("Command"), c);
		c.gridx = 1; c.anchor = GridBagConstraints.WEST;
		grid.add(fldCommand, c);
		lblId = smallLabel("Field ID");
		c.gridx = 2; c.anchor = GridBagConstraints.EAST;
		grid.add(lblId, c);
		c.gridx = 3; c.anchor = GridBagConstraints.WEST;
		grid.add(spnId, c);

		// Row 1: Value (spans full width, stretches horizontally)
		c.gridy = 1;
		lblValue = smallLabel("Value");
		c.gridx = 0; c.anchor = GridBagConstraints.EAST; c.weightx = 0;
		grid.add(lblValue, c);
		c.gridx = 1; c.gridwidth = 3; c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
		grid.add(fldValue, c);
		c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0; // reset

		add(grid, BorderLayout.CENTER);

		// Start disabled
		for (Component comp : editableWidgets) comp.setEnabled(false);
	}

	// ---------------------------------------------------------------------
	// Public API
	// ---------------------------------------------------------------------

	public void setDirtyListener(Runnable listener) { this.dirtyListener = listener; }
	public void setLineCallback(BiConsumer<Integer, String> cb) { this.lineCallback = cb; }

	public boolean hasPendingChanges()
	{
		return boundIndex >= 0 && initialValues != null
				&& !Arrays.equals(getCurrentValues(), initialValues);
	}

	public void applyPending()
	{
		if (boundIndex < 0 || originalParts == null) return;
		patchOriginalParts();
		String newLine = String.join(",", originalParts);
		initialValues = getCurrentValues();
		if (dirtyListener != null) dirtyListener.run();
		if (lineCallback != null) lineCallback.accept(boundIndex, newLine);
	}

	public void discardPending()
	{
		if (initialValues == null) return;
		loading = true;
		try {
			restoreFromInitial();
		} finally {
			loading = false;
		}
		if (dirtyListener != null) dirtyListener.run();
	}

	public int getBoundIndex() { return boundIndex; }

	/**
	 * Bind to a source line at the given model index, or unbind if it's not
	 * a supported data command.
	 */
	public void bindLine(int modelIndex, String line)
	{
		if (line == null || line.isBlank()) {
			unbind();
			return;
		}
		String[] parts = line.split(",", 3); // split into at most 3: cmd, id, value
		String cmd = parts[0].trim().toUpperCase();
		if (!SUPPORTED.contains(cmd)) {
			unbind();
			return;
		}

		loading = true;
		try {
			boundIndex = modelIndex;

			// Store the original full parts for reconstruction
			originalParts = line.split(",", 3);

			fldCommand.setText(describeCommand(cmd));
			fldId.setText(parts.length > 1 ? parts[1].trim() : "");
			fldValue.setText(parts.length > 2 ? parts[2] : "");

			// Adjust labels for FL which has different semantics
			if ("FL".equals(cmd)) {
				lblId.setText("Source ID");
				lblValue.setText("Element ID");
			} else if ("VAR".equals(cmd) || "UVAR".equals(cmd)) {
				lblId.setText("Variable ID");
				lblValue.setText("Expression");
			} else if ("QUE".equals(cmd)) {
				lblId.setText("Field ID");
				lblValue.setText("Prompt");
			} else if ("BD".equals(cmd)) {
				lblId.setText("Field ID");
				lblValue.setText("Block Text");
			} else {
				lblId.setText("Field ID");
				lblValue.setText("Value");
			}
		} finally {
			loading = false;
		}
		initialValues = getCurrentValues();
		for (Component comp : editableWidgets) comp.setEnabled(true);
		if (dirtyListener != null) dirtyListener.run();

		// Auto-focus the value field so the user can start typing immediately
		javax.swing.SwingUtilities.invokeLater(() -> fldValue.requestFocusInWindow());
	}

	public void unbind()
	{
		boundIndex = -1;
		originalParts = null;

		initialValues = null;
		loading = true;
		try {
			fldCommand.setText("");
			fldId.setText("");
			fldValue.setText("");
			lblId.setText("Field ID");
			lblValue.setText("Value");
		} finally {
			loading = false;
		}
		for (Component comp : editableWidgets) comp.setEnabled(false);
		if (dirtyListener != null) dirtyListener.run();
	}

	// ---------------------------------------------------------------------
	// Internals
	// ---------------------------------------------------------------------

	private void patchOriginalParts()
	{
		// Preserve the original command case from the source
		if (originalParts.length > 1)
			originalParts[1] = fldId.getText().trim();
		if (originalParts.length > 2)
			originalParts[2] = fldValue.getText();
		else if (!fldValue.getText().isEmpty()) {
			// Value was added where there was none — extend the array
			originalParts = new String[] {
				originalParts[0],
				originalParts.length > 1 ? originalParts[1] : "",
				fldValue.getText()
			};
		}
	}

	private String[] getCurrentValues()
	{
		return new String[] {
			fldId.getText().trim(),
			fldValue.getText()
		};
	}

	private void restoreFromInitial()
	{
		if (initialValues == null || initialValues.length < 2) return;
		fldId.setText(initialValues[0]);
		fldValue.setText(initialValues[1]);
	}

	private void checkDirty()
	{
		if (loading || boundIndex < 0 || initialValues == null) return;
		if (dirtyListener != null) dirtyListener.run();
	}

	// ---------------------------------------------------------------------
	// Spinner compound widget (same pattern as TextPropertiesPanel)
	// ---------------------------------------------------------------------

	private JPanel spinnerWrap(JTextField4j field, double step)
	{
		JButton btnUp   = new JButton("\u25B2");
		JButton btnDown = new JButton("\u25BC");
		Dimension arrowSize = new Dimension(18, ROW_H / 2);
		btnUp.setPreferredSize(arrowSize);
		btnDown.setPreferredSize(arrowSize);
		btnUp.setMinimumSize(arrowSize);
		btnDown.setMinimumSize(arrowSize);
		btnUp.setMaximumSize(arrowSize);
		btnDown.setMaximumSize(arrowSize);
		btnUp.setMargin(new Insets(0, 0, 0, 0));
		btnDown.setMargin(new Insets(0, 0, 0, 0));
		btnUp.setFont(btnUp.getFont().deriveFont(7f));
		btnDown.setFont(btnDown.getFont().deriveFont(7f));
		btnUp.setFocusable(false);
		btnDown.setFocusable(false);

		btnUp.addActionListener(_ -> adjustField(field, step));
		btnDown.addActionListener(_ -> adjustField(field, -step));

		JPanel arrows = new JPanel();
		arrows.setLayout(new BoxLayout(arrows, BoxLayout.Y_AXIS));
		arrows.add(btnUp);
		arrows.add(btnDown);

		JPanel wrapper = new JPanel(new BorderLayout(0, 0));
		wrapper.add(field, BorderLayout.CENTER);
		wrapper.add(arrows, BorderLayout.EAST);

		editableWidgets.add(btnUp);
		editableWidgets.add(btnDown);

		return wrapper;
	}

	private static void adjustField(JTextField4j field, double delta)
	{
		String text = field.getText().trim();
		if (text.isEmpty()) {
			if (delta <= 0) return;
			text = "0";
		}
		try {
			int val = Integer.parseInt(text) + (int) delta;
			if (val >= 0) field.setText(String.valueOf(val));
		} catch (NumberFormatException ignored) {
			// non-numeric — leave unchanged
		}
	}

	// ----- layout helpers ------------------------------------------------

	private static JLabel smallLabel(String text)
	{
		JLabel lbl = new JLabel(text);
		lbl.setFont(lbl.getFont().deriveFont(11f));
		lbl.setHorizontalAlignment(SwingConstants.RIGHT);
		return lbl;
	}

	private static JTextField4j text(int width)
	{
		JTextField4j f = new JTextField4j();
		lockSize(f, width, ROW_H);
		return f;
	}

	private static JTextField4j readOnlyField(int width)
	{
		JTextField4j f = text(width);
		f.setEditable(false);
		f.setBackground(new Color(0xF0F0F0));
		return f;
	}

	private static void lockSize(javax.swing.JComponent c, int w, int h)
	{
		Dimension d = new Dimension(w, h);
		c.setPreferredSize(d);
		c.setMinimumSize(d);
		c.setMaximumSize(d);
	}
}
