package com.commander4j.logorenderer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.commander4j.gui.JTextField4j;

/**
 * Inline editor for T/S element line properties. Populated from the currently
 * selected source line in {@link LlfLinePanel}. Edits are applied back to the
 * source line text (not fonts.xml).
 *
 * <p>The host owns shared Apply/Cancel buttons and delegates to
 * {@link #applyPending()} / {@link #discardPending()}.
 */
public final class TextPropertiesPanel extends JPanel
{

	private static final long serialVersionUID = 1L;
	private static final int ROW_H = 26;

	// --- widgets: position / dimension ---
	private final JTextField4j fldId;
	private final JTextField4j fldX;
	private final JTextField4j fldY;
	private final JTextField4j fldWidth;
	private final JLabel       lblX;
	private final JLabel       lblY;
	private final JLabel       lblWidth;
	private final JTextField4j fldFont;

	// T-only widgets
	private final JLabel       lblRotation;
	private final JTextField4j fldRotation;
	private final JLabel       lblZoomW;
	private final JTextField4j fldZoomW;
	private final JLabel       lblZoomH;
	private final JTextField4j fldZoomH;

	// S-only widgets
	private final JLabel       lblHeight;
	private final JTextField4j fldHeight;
	private final JLabel       lblAngle;
	private final JTextField4j fldAngle;
	private final JLabel       lblCharWidth;
	private final JTextField4j fldCharWidth;

	// Spinner wrapper panels (for visibility toggling of overlaid rows)
	private final JPanel       spnRotation;
	private final JPanel       spnZoomW;
	private final JPanel       spnZoomH;
	private final JPanel       spnHeight;
	private final JPanel       spnAngle;
	private final JPanel       spnCharWidth;

	// --- alignment radios ---
	private final JRadioButton radHLeft;
	private final JRadioButton radHCentre;
	private final JRadioButton radHRight;
	private final JRadioButton radVTop;
	private final JRadioButton radVMiddle;
	private final JRadioButton radVBottom;

	// --- flag checkboxes ---
	private final JCheckBox chkU; // upside-down (T only)
	private final JCheckBox chkV; // vertical    (T only)
	private final JCheckBox chkB; // borders
	private final JCheckBox chkD; // disabled
	private final JCheckBox chkE; // redraw
	private final JCheckBox chkP; // protected

	// --- state ---
	private Runnable dirtyListener;
	private BiConsumer<Integer, String> lineCallback; // (modelIndex, newLine)

	/** Model index of the source line currently bound. */
	private int boundIndex = -1;

	/** Original comma-split tokens — patched in place on Apply. */
	private String[] originalParts;

	/** 'T' or 'S' — the element type of the bound line. */
	private char boundType;

	/** Snapshot of widget values at bind time (for dirty detection). */
	private String[] initialValues;

	/** Suppress dirty notifications during programmatic loads. */
	private boolean loading;

	/** All editable widgets — toggled as a group when the panel binds/unbinds. */
	private final List<Component> editableWidgets = new ArrayList<>();

	public TextPropertiesPanel()
	{
		setLayout(new BorderLayout());

		fldId       = readOnlyField(50);
		fldX        = text(60);
		fldY        = text(60);
		fldWidth    = text(60);
		fldFont     = readOnlyField(120);
		fldRotation = text(35);
		fldZoomW    = text(35);
		fldZoomH    = text(35);
		fldHeight   = text(50);
		fldAngle    = text(50);
		fldCharWidth = text(50);

		fldX.setHorizontalAlignment(SwingConstants.CENTER);
		fldY.setHorizontalAlignment(SwingConstants.CENTER);
		fldWidth.setHorizontalAlignment(SwingConstants.CENTER);
		fldRotation.setHorizontalAlignment(SwingConstants.CENTER);
		fldZoomW.setHorizontalAlignment(SwingConstants.CENTER);
		fldZoomH.setHorizontalAlignment(SwingConstants.CENTER);
		fldHeight.setHorizontalAlignment(SwingConstants.CENTER);
		fldAngle.setHorizontalAlignment(SwingConstants.CENTER);
		fldCharWidth.setHorizontalAlignment(SwingConstants.CENTER);

		// Build spinner wrappers (text field + ▲▼ buttons)
		JPanel spnX     = spinnerWrap(fldX,     1);    // T: dots
		JPanel spnY     = spinnerWrap(fldY,     1);
		JPanel spnWidth = spinnerWrap(fldWidth, 1);
		spnRotation     = spinnerWrap(fldRotation, 2); // 0,2,4,6,8
		spnZoomW        = spinnerWrap(fldZoomW,    1);
		spnZoomH        = spinnerWrap(fldZoomH,    1);
		spnHeight       = spinnerWrap(fldHeight,     0.5);
		spnAngle        = spinnerWrap(fldAngle,      1.0);
		spnCharWidth    = spinnerWrap(fldCharWidth,  1.0);

		// Alignment radios
		radHLeft   = new JRadioButton("Left");
		radHCentre = new JRadioButton("Centre");
		radHRight  = new JRadioButton("Right");
		ButtonGroup hGroup = new ButtonGroup();
		hGroup.add(radHLeft);
		hGroup.add(radHCentre);
		hGroup.add(radHRight);
		radHLeft.setSelected(true);

		radVTop    = new JRadioButton("Top");
		radVMiddle = new JRadioButton("Middle");
		radVBottom = new JRadioButton("Bottom");
		ButtonGroup vGroup = new ButtonGroup();
		vGroup.add(radVTop);
		vGroup.add(radVMiddle);
		vGroup.add(radVBottom);
		radVTop.setSelected(true);

		// Flag checkboxes — descriptive labels with tooltip for the raw flag letter
		chkU = new JCheckBox("Upside Down");  chkU.setToolTipText("U flag — rotate element 180° (T only)");
		chkV = new JCheckBox("Vertical");     chkV.setToolTipText("V flag — render text vertically (T only)");
		chkB = new JCheckBox("Borders");      chkB.setToolTipText("B flag — draw borders only (box outline)");
		chkD = new JCheckBox("Disabled");     chkD.setToolTipText("D flag — element will not be printed");
		chkE = new JCheckBox("Redraw");       chkE.setToolTipText("E flag — redraw field every time");
		chkP = new JCheckBox("Protected");    chkP.setToolTipText("P flag — field may not be changed");

		// Track editable widgets
		editableWidgets.addAll(List.of(
				fldX, fldY, fldWidth, fldRotation, fldZoomW, fldZoomH,
				fldHeight, fldAngle, fldCharWidth,
				radHLeft, radHCentre, radHRight,
				radVTop, radVMiddle, radVBottom,
				chkU, chkV, chkB, chkD, chkE, chkP));

		// Dirty tracking
		DocumentListener dirty = new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e)  { checkDirty(); }
			@Override public void removeUpdate(DocumentEvent e)  { checkDirty(); }
			@Override public void changedUpdate(DocumentEvent e) { checkDirty(); }
		};
		fldX.getDocument().addDocumentListener(dirty);
		fldY.getDocument().addDocumentListener(dirty);
		fldWidth.getDocument().addDocumentListener(dirty);
		fldRotation.getDocument().addDocumentListener(dirty);
		fldZoomW.getDocument().addDocumentListener(dirty);
		fldZoomH.getDocument().addDocumentListener(dirty);
		fldHeight.getDocument().addDocumentListener(dirty);
		fldAngle.getDocument().addDocumentListener(dirty);
		fldCharWidth.getDocument().addDocumentListener(dirty);
		radHLeft  .addActionListener(_ -> checkDirty());
		radHCentre.addActionListener(_ -> checkDirty());
		radHRight .addActionListener(_ -> checkDirty());
		radVTop   .addActionListener(_ -> checkDirty());
		radVMiddle.addActionListener(_ -> checkDirty());
		radVBottom.addActionListener(_ -> checkDirty());
		chkU.addActionListener(_ -> checkDirty());
		chkV.addActionListener(_ -> checkDirty());
		chkB.addActionListener(_ -> checkDirty());
		chkD.addActionListener(_ -> checkDirty());
		chkE.addActionListener(_ -> checkDirty());
		chkP.addActionListener(_ -> checkDirty());

		// Layout grid
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setBorder(BorderFactory.createEmptyBorder(2, 4, 10, 4));

		// Row 0: Element ID, Font Name (both read-only)
		addRow(grid, 0, "Element", fldId, "Font", fldFont);

		// Row 1: X Position, Y Position (with spinner wrappers)
		lblX = addRow(grid, 1, "X Position", spnX, "Y Position", spnY);
		lblY = labelAt(grid, 1, 2);

		// Row 2: Max Width + Rotation (T) / Height (S) — overlaid
		lblWidth    = addRow(grid, 2, "Max Width", spnWidth, "Rotation", spnRotation);
		lblRotation = labelAt(grid, 2, 2);
		lblHeight   = smallLabel("Height (mm)");
		addOverlay(grid, 2, 2, lblHeight, 3, spnHeight);

		// Row 3: Zoom Width/Height (T) / Angle/Char Width (S) — overlaid
		lblZoomW     = addRow(grid, 3, "Zoom Width", spnZoomW, "Zoom Height", spnZoomH);
		lblZoomH     = labelAt(grid, 3, 2);
		lblAngle     = smallLabel("Angle (\u00B0)");
		lblCharWidth = smallLabel("Char Width %");
		addOverlay(grid, 3, 0, lblAngle, 1, spnAngle);
		addOverlay(grid, 3, 2, lblCharWidth, 3, spnCharWidth);

		// Row 4: Horizontal alignment
		JPanel hAlignPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		hAlignPanel.add(radHLeft);
		hAlignPanel.add(radHCentre);
		hAlignPanel.add(radHRight);
		addWideRow(grid, 4, "Horizontal", hAlignPanel);

		// Row 5: Vertical alignment
		JPanel vAlignPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		vAlignPanel.add(radVTop);
		vAlignPanel.add(radVMiddle);
		vAlignPanel.add(radVBottom);
		addWideRow(grid, 5, "Vertical", vAlignPanel);

		// Row 6: Flag checkboxes — first group
		JPanel flagRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		flagRow1.add(chkD);
		flagRow1.add(chkB);
		flagRow1.add(chkP);
		addWideRow(grid, 6, "Options", flagRow1);

		// Row 7: Flag checkboxes — second group (T-specific + E)
		JPanel flagRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		flagRow2.add(chkU);
		flagRow2.add(chkV);
		flagRow2.add(chkE);
		addWideRow(grid, 7, "", flagRow2);

		add(grid, BorderLayout.CENTER);

		// Start disabled; S-only fields hidden by default
		for (Component c : editableWidgets) c.setEnabled(false);
		lblHeight.setVisible(false);
		spnHeight.setVisible(false);
		lblAngle.setVisible(false);
		spnAngle.setVisible(false);
		lblCharWidth.setVisible(false);
		spnCharWidth.setVisible(false);
	}

	// ---------------------------------------------------------------------
	// Public API
	// ---------------------------------------------------------------------

	public void setDirtyListener(Runnable listener) { this.dirtyListener = listener; }

	/**
	 * Callback invoked on Apply with (modelIndex, reconstructedLine).
	 * The host uses this to update the list model and fire a refresh.
	 */
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

	/** The model index currently bound, or -1 if unbound. */
	public int getBoundIndex() { return boundIndex; }

	/**
	 * Bind to a source line at the given model index, or unbind if the line
	 * is not a T/S element.
	 */
	public void bindLine(int modelIndex, String line)
	{
		if (line == null || line.isBlank()) {
			unbind();
			return;
		}
		String[] parts = line.split(",", -1);
		String type = parts[0].trim().toUpperCase();
		if (!type.equals("T") && !type.equals("S")) {
			unbind();
			return;
		}

		loading = true;
		try {
			boundIndex = modelIndex;
			boundType = type.charAt(0);
			originalParts = parts.clone();
			populateFromParts(parts);
			configureForType();
		} finally {
			loading = false;
		}
		initialValues = getCurrentValues();
		for (Component c : editableWidgets) c.setEnabled(true);
		// Disable T-only or S-only widgets as appropriate
		if (boundType == 'S') {
			fldRotation.setEnabled(false);
			fldZoomW.setEnabled(false);
			fldZoomH.setEnabled(false);
			chkU.setEnabled(false);
			chkV.setEnabled(false);
		} else {
			fldHeight.setEnabled(false);
			fldAngle.setEnabled(false);
			fldCharWidth.setEnabled(false);
		}
		if (dirtyListener != null) dirtyListener.run();
	}

	public void unbind()
	{
		boundIndex = -1;
		originalParts = null;
		initialValues = null;
		loading = true;
		try {
			fldId.setText("");
			fldX.setText("");
			fldY.setText("");
			fldWidth.setText("");
			fldFont.setText("");
			fldRotation.setText("");
			fldZoomW.setText("");
			fldZoomH.setText("");
			fldHeight.setText("");
			fldAngle.setText("");
			fldCharWidth.setText("");
			radHLeft.setSelected(true);
			radVTop.setSelected(true);
			chkU.setSelected(false);
			chkV.setSelected(false);
			chkB.setSelected(false);
			chkD.setSelected(false);
			chkE.setSelected(false);
			chkP.setSelected(false);
		} finally {
			loading = false;
		}
		for (Component c : editableWidgets) c.setEnabled(false);
		if (dirtyListener != null) dirtyListener.run();
	}

	// ---------------------------------------------------------------------
	// Parse source line into widgets
	// ---------------------------------------------------------------------

	private void populateFromParts(String[] parts)
	{
		fldId.setText(parts.length > 1 ? parts[1].trim() : "");

		if (boundType == 'T') {
			fldX.setText(parts.length > 2 ? parts[2].trim() : "");
			fldY.setText(parts.length > 3 ? parts[3].trim() : "");
			fldWidth.setText(parts.length > 4 ? parts[4].trim() : "");
		} else {
			// S: positional args, with optional wmax
			fldX.setText(parts.length > 2 ? parts[2].trim() : "");
			fldY.setText(parts.length > 3 ? parts[3].trim() : "");
			// parts[4] is wmax if it starts with a digit/dot, else it's a flag
			if (parts.length > 4 && startsWithNumeric(parts[4].trim())) {
				fldWidth.setText(parts[4].trim());
			} else {
				fldWidth.setText("");
			}
		}

		// Scan flagged tokens
		String font = "";
		String options = "";
		int rotation = 0;
		int zoomW = 1, zoomH = 1;
		double height = 0, angle = 0, charWidth = 0;

		int flagStart = (boundType == 'T') ? 5 : 4;
		// For S, skip past numeric wmax if present
		if (boundType == 'S' && parts.length > 4 && startsWithNumeric(parts[4].trim())) {
			flagStart = 5;
		}

		for (int i = flagStart; i < parts.length; i++) {
			String p = parts[i].trim();
			if (p.startsWith("F"))
				font = p.substring(1);
			else if (p.startsWith("O"))
				options = p.substring(1);
			else if (p.startsWith("R") && p.length() > 1 && Character.isDigit(p.charAt(1)))
				rotation = parseIntSafe(p.substring(1));
			else if (p.startsWith("Z")) {
				String[] zParts = p.substring(1).split(",");
				zoomW = parseIntSafe(zParts[0]);
				if (zParts.length > 1) {
					zoomH = parseIntSafe(zParts[1]);
				} else if (i + 1 < parts.length) {
					String next = parts[i + 1].trim();
					if (!next.isEmpty() && Character.isDigit(next.charAt(0))) {
						zoomH = parseIntSafe(next);
						i++; // consumed
					} else {
						zoomH = zoomW;
					}
				} else {
					zoomH = zoomW;
				}
			}
			else if (p.startsWith("H"))
				height = parseDoubleSafe(p.substring(1));
			else if (p.startsWith("A"))
				angle = parseDoubleSafe(p.substring(1));
			else if (p.startsWith("W") && p.length() > 1 && Character.isDigit(p.charAt(1)))
				charWidth = parseDoubleSafe(p.substring(1));
		}

		fldFont.setText(font);
		fldRotation.setText(String.valueOf(rotation));
		fldZoomW.setText(String.valueOf(zoomW));
		fldZoomH.setText(String.valueOf(zoomH));
		fldHeight.setText(height != 0 ? String.valueOf(height) : "");
		fldAngle.setText(angle != 0 ? String.valueOf(angle) : "");
		fldCharWidth.setText(charWidth != 0 ? String.valueOf(charWidth) : "");

		// Options → alignment radios + flag checkboxes
		if (options.contains("C"))      radHCentre.setSelected(true);
		else if (options.contains("R")) radHRight.setSelected(true);
		else                            radHLeft.setSelected(true);

		if (options.contains("M"))      radVMiddle.setSelected(true);
		else if (options.contains("L")) radVBottom.setSelected(true);
		else                            radVTop.setSelected(true);

		chkU.setSelected(options.contains("U"));
		chkV.setSelected(options.contains("V"));
		chkB.setSelected(options.contains("B"));
		chkD.setSelected(options.contains("D"));
		chkE.setSelected(options.contains("E"));
		chkP.setSelected(options.contains("P"));
	}

	/** Show/hide T-only vs S-only rows and update unit labels. */
	private void configureForType()
	{
		boolean isT = (boundType == 'T');

		// Labels with units
		lblX.setText(isT ? "X Position" : "X Pos (mm)");
		lblY.setText(isT ? "Y Position" : "Y Pos (mm)");
		lblWidth.setText(isT ? "Max Width" : "Max W (mm)");

		// T-only fields: rotation, zoom
		lblRotation.setVisible(isT);
		spnRotation.setVisible(isT);
		lblZoomW.setVisible(isT);
		spnZoomW.setVisible(isT);
		lblZoomH.setVisible(isT);
		spnZoomH.setVisible(isT);

		// S-only fields: height, angle, char-width
		lblHeight.setVisible(!isT);
		spnHeight.setVisible(!isT);
		lblAngle.setVisible(!isT);
		spnAngle.setVisible(!isT);
		lblCharWidth.setVisible(!isT);
		spnCharWidth.setVisible(!isT);

		// T-only flag checkboxes
		chkU.setVisible(isT);
		chkV.setVisible(isT);
	}

	// ---------------------------------------------------------------------
	// Patch original tokens and reconstruct the source line
	// ---------------------------------------------------------------------

	private void patchOriginalParts()
	{
		// Positional args
		if (originalParts.length > 2) originalParts[2] = fldX.getText().trim();
		if (originalParts.length > 3) originalParts[3] = fldY.getText().trim();

		if (boundType == 'T') {
			if (originalParts.length > 4) originalParts[4] = fldWidth.getText().trim();
		} else {
			// S: wmax slot — if originally present (numeric at [4]), patch it
			if (originalParts.length > 4 && startsWithNumeric(originalParts[4].trim())) {
				String w = fldWidth.getText().trim();
				originalParts[4] = w.isEmpty() ? "0" : w;
			}
		}

		// Rebuild the options string from alignment radios + flag checkboxes
		StringBuilder opts = new StringBuilder();
		if (radHCentre.isSelected()) opts.append('C');
		if (radHRight.isSelected())  opts.append('R');
		if (radVBottom.isSelected()) opts.append('L');
		if (radVMiddle.isSelected()) opts.append('M');
		if (chkU.isSelected()) opts.append('U');
		if (chkV.isSelected()) opts.append('V');
		if (chkB.isSelected()) opts.append('B');
		if (chkD.isSelected()) opts.append('D');
		if (chkE.isSelected()) opts.append('E');
		if (chkP.isSelected()) opts.append('P');
		// Preserve any flags we don't model (F, O, Q)
		String oldOpts = findToken(originalParts, 'O');
		if (oldOpts != null) {
			for (char c : oldOpts.toCharArray()) {
				if ("CRLMUVBDEP".indexOf(c) < 0 && opts.indexOf(String.valueOf(c)) < 0) {
					opts.append(c);
				}
			}
		}

		// Patch flagged tokens in place
		int flagStart = (boundType == 'T') ? 5 : 4;
		if (boundType == 'S' && originalParts.length > 4
				&& startsWithNumeric(originalParts[4].trim())) {
			flagStart = 5;
		}

		boolean foundO = false;
		for (int i = flagStart; i < originalParts.length; i++) {
			String p = originalParts[i].trim();
			if (p.startsWith("O")) {
				originalParts[i] = opts.length() > 0 ? "O" + opts : "";
				foundO = true;
			} else if (p.startsWith("R") && p.length() > 1
					&& Character.isDigit(p.charAt(1)) && boundType == 'T') {
				originalParts[i] = "R" + fldRotation.getText().trim();
			} else if (p.startsWith("Z") && boundType == 'T') {
				// Always write combined Zw,h form
				originalParts[i] = "Z" + fldZoomW.getText().trim()
						+ "," + fldZoomH.getText().trim();
				// If the next token was a bare-integer zoom height, blank it
				if (!p.substring(1).contains(",") && i + 1 < originalParts.length) {
					String next = originalParts[i + 1].trim();
					if (!next.isEmpty() && Character.isDigit(next.charAt(0))) {
						List<String> list = new ArrayList<>(Arrays.asList(originalParts));
						list.remove(i + 1);
						originalParts = list.toArray(new String[0]);
					}
				}
			} else if (p.startsWith("H") && boundType == 'S') {
				String h = fldHeight.getText().trim();
				originalParts[i] = h.isEmpty() ? "H0" : "H" + h;
			} else if (p.startsWith("A") && boundType == 'S') {
				String a = fldAngle.getText().trim();
				originalParts[i] = a.isEmpty() ? "A0" : "A" + a;
			} else if (p.startsWith("W") && p.length() > 1
					&& Character.isDigit(p.charAt(1)) && boundType == 'S') {
				String w = fldCharWidth.getText().trim();
				originalParts[i] = w.isEmpty() ? "W0" : "W" + w;
			}
		}

		// If options string changed from empty to non-empty and there was no
		// O token originally, insert one before the first flag token.
		if (!foundO && opts.length() > 0) {
			List<String> list = new ArrayList<>(Arrays.asList(originalParts));
			list.add(flagStart, "O" + opts);
			originalParts = list.toArray(new String[0]);
		}
		// Remove empty O token if options were cleared
		if (foundO && opts.length() == 0) {
			List<String> list = new ArrayList<>(Arrays.asList(originalParts));
			list.removeIf(s -> s.trim().equals("O"));
			originalParts = list.toArray(new String[0]);
		}
	}

	// ---------------------------------------------------------------------
	// Dirty tracking / snapshot
	// ---------------------------------------------------------------------

	private String[] getCurrentValues()
	{
		return new String[] {
			fldX.getText().trim(),
			fldY.getText().trim(),
			fldWidth.getText().trim(),
			fldRotation.getText().trim(),
			fldZoomW.getText().trim(),
			fldZoomH.getText().trim(),
			fldHeight.getText().trim(),
			fldAngle.getText().trim(),
			fldCharWidth.getText().trim(),
			radHCentre.isSelected() ? "C" : radHRight.isSelected() ? "R" : "L",
			radVMiddle.isSelected() ? "M" : radVBottom.isSelected() ? "B" : "T",
			bool(chkU), bool(chkV), bool(chkB), bool(chkD), bool(chkE), bool(chkP)
		};
	}

	private void restoreFromInitial()
	{
		if (initialValues == null || initialValues.length < 17) return;
		fldX.setText(initialValues[0]);
		fldY.setText(initialValues[1]);
		fldWidth.setText(initialValues[2]);
		fldRotation.setText(initialValues[3]);
		fldZoomW.setText(initialValues[4]);
		fldZoomH.setText(initialValues[5]);
		fldHeight.setText(initialValues[6]);
		fldAngle.setText(initialValues[7]);
		fldCharWidth.setText(initialValues[8]);

		switch (initialValues[9]) {
			case "C" -> radHCentre.setSelected(true);
			case "R" -> radHRight.setSelected(true);
			default  -> radHLeft.setSelected(true);
		}
		switch (initialValues[10]) {
			case "M" -> radVMiddle.setSelected(true);
			case "B" -> radVBottom.setSelected(true);
			default  -> radVTop.setSelected(true);
		}
		chkU.setSelected("1".equals(initialValues[11]));
		chkV.setSelected("1".equals(initialValues[12]));
		chkB.setSelected("1".equals(initialValues[13]));
		chkD.setSelected("1".equals(initialValues[14]));
		chkE.setSelected("1".equals(initialValues[15]));
		chkP.setSelected("1".equals(initialValues[16]));
	}

	private void checkDirty()
	{
		if (loading || boundIndex < 0 || initialValues == null) return;
		if (dirtyListener != null) dirtyListener.run();
	}

	// ---------------------------------------------------------------------
	// Spinner compound widget
	// ---------------------------------------------------------------------

	/**
	 * Wraps a text field with small ▲/▼ buttons for incremental adjustment.
	 * If the field is blank, clicking ▲ starts from 0; clicking ▼ does nothing.
	 * The step can be integer (1, 2) or fractional (0.5, 0.1).
	 */
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

		// Track arrow buttons for enable/disable
		editableWidgets.add(btnUp);
		editableWidgets.add(btnDown);

		return wrapper;
	}

	/**
	 * Increment or decrement the numeric value in a text field.
	 * Blank fields start from 0 on increment; decrement on blank is a no-op.
	 */
	private void adjustField(JTextField4j field, double delta)
	{
		String text = field.getText().trim();
		if (text.isEmpty()) {
			if (delta <= 0) return; // don't decrement from blank
			text = "0";
		}
		boolean isInt = (Math.abs(delta) >= 1.0) && !text.contains(".");
		try {
			if (isInt) {
				int val = Integer.parseInt(text) + (int) delta;
				field.setText(String.valueOf(val));
			} else {
				double val = Double.parseDouble(text) + delta;
				// Avoid floating-point noise: round to the step's precision
				int decimals = decimalPlaces(delta);
				String fmt = "%." + decimals + "f";
				field.setText(String.format(fmt, val));
			}
		} catch (NumberFormatException ignored) {
			// Non-numeric text — leave unchanged
		}
	}

	/** Count decimal places in a step value (e.g. 0.5 → 1, 0.01 → 2). */
	private static int decimalPlaces(double step)
	{
		String s = String.valueOf(Math.abs(step));
		int dot = s.indexOf('.');
		return (dot < 0) ? 0 : s.length() - dot - 1;
	}

	// ---------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------

	private static String bool(JCheckBox cb) { return cb.isSelected() ? "1" : "0"; }

	private static String findToken(String[] parts, char prefix)
	{
		for (String p : parts) {
			String t = p.trim();
			if (t.length() > 1 && t.charAt(0) == prefix) return t.substring(1);
		}
		return null;
	}

	private static boolean startsWithNumeric(String s)
	{
		if (s == null || s.isEmpty()) return false;
		char c = s.charAt(0);
		return Character.isDigit(c) || c == '.' || c == '-';
	}

	private static int parseIntSafe(String s)
	{
		try { return Integer.parseInt(s.trim()); }
		catch (NumberFormatException e) { return 0; }
	}

	private static double parseDoubleSafe(String s)
	{
		try { return Double.parseDouble(s.trim()); }
		catch (NumberFormatException e) { return 0; }
	}

	// ----- layout helpers ------------------------------------------------

	/**
	 * Add a row with left-label, left-field, right-label, right-field.
	 * Returns the left-side JLabel for later reference.
	 */
	private JLabel addRow(JPanel grid, int row,
			String leftLabel, Component leftField,
			String rightLabel, Component rightField)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);
		c.gridy = row;

		JLabel ll = smallLabel(leftLabel);
		c.gridx = 0; c.anchor = GridBagConstraints.EAST;
		grid.add(ll, c);

		c.gridx = 1; c.anchor = GridBagConstraints.WEST;
		grid.add(leftField, c);

		JLabel rl = smallLabel(rightLabel);
		c.gridx = 2; c.anchor = GridBagConstraints.EAST;
		grid.add(rl, c);

		c.gridx = 3; c.anchor = GridBagConstraints.WEST;
		grid.add(rightField, c);

		return ll;
	}

	/** Add a row spanning columns 1–3 (label in col 0, wide component in 1–3). */
	private void addWideRow(JPanel grid, int row, String label, Component wide)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);
		c.gridy = row;

		c.gridx = 0; c.anchor = GridBagConstraints.EAST;
		grid.add(smallLabel(label), c);

		c.gridx = 1; c.gridwidth = 3; c.anchor = GridBagConstraints.WEST;
		grid.add(wide, c);
	}

	/**
	 * Add overlay components at the same grid cell as existing ones.
	 * Used to place S-only fields on top of T-only fields — only one set is
	 * visible at a time.
	 */
	private static void addOverlay(JPanel grid, int row, int labelCol,
			JLabel label, int fieldCol, Component field)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);
		c.gridy = row;

		c.gridx = labelCol; c.anchor = GridBagConstraints.EAST;
		grid.add(label, c);

		c.gridx = fieldCol; c.anchor = GridBagConstraints.WEST;
		grid.add(field, c);
	}

	/** Retrieve the label component at (row, col) in the grid — used for lblY etc. */
	private static JLabel labelAt(JPanel grid, int row, int col)
	{
		for (Component comp : grid.getComponents()) {
			GridBagConstraints gbc = ((GridBagLayout) grid.getLayout()).getConstraints(comp);
			if (gbc.gridy == row && gbc.gridx == col && comp instanceof JLabel) {
				return (JLabel) comp;
			}
		}
		return new JLabel(); // fallback — shouldn't happen
	}

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
