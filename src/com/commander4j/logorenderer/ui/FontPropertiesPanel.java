package com.commander4j.logorenderer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;

import com.commander4j.gui.JButton4j;
import com.commander4j.gui.JComboBox4j;
import com.commander4j.gui.JTextField4j;
import com.commander4j.logorenderer.FontMapper;
import com.commander4j.logorenderer.FontProperties;
import com.commander4j.sys.Common;

/**
 * Inline editor for a single font's properties. Populated from the current
 * selection in {@link LlfLinePanel} and hidden while no T/S line is selected.
 *
 * <p>The host ({@link LlfLinePanel}) owns the shared Apply/Cancel buttons and
 * delegates to {@link #applyPending()} / {@link #discardPending()}. This panel
 * notifies the host of dirty-state changes via the {@link #setDirtyListener}
 * callback so the buttons can be enabled/disabled accordingly.
 */
public final class FontPropertiesPanel extends JPanel
{

	private static final long serialVersionUID = 1L;

	private static final String[] STYLES    = { "PLAIN", "BOLD", "ITALIC", "BOLD_ITALIC" };
	private static final String[] RENDERERS = { "SCALEABLE", "BITMAP" };
	private static final String[] SPACINGS  = { "PROPORTIONAL", "MONOSPACED" };

	private static final int ROW_H = 26;

	private final FontMapper fontMapper;

	// Widgets (ten font-property fields — description is intentionally omitted)
	private final JTextField4j        fldName;
	private final JTextField4j        fldFamily;
	private final JComboBox4j<String> fldStyle;
	private final JTextField4j        fldRefH;
	private final JTextField4j        fldRefW;
	private final JTextField4j        fldFilename;
	private final JComboBox4j<String> fldRenderer;
	private final JComboBox4j<String> fldSpacing;
	private final JTextField4j        fldAscent;
	private final JTextField4j        fldDescent;

	// Dirty-state listener — the host button bar polls this to enable Apply/Cancel
	private Runnable dirtyListener;

	/** Fired after a successful Apply so the canvas can repaint with new fonts. */
	private Runnable onFontSaved;

	/** All editable widgets — toggled as a group when the panel binds/unbinds. */
	private final java.util.List<Component> editableWidgets = new java.util.ArrayList<>();

	/** Label name currently bound to the editor (null → panel is hidden). */
	private String currentFontName;

	/** Widget-value snapshot at last {@link #setFont(String)} or successful Apply. */
	private String[] initialValues;

	/** Suppress dirty-tracking while we're programmatically loading values. */
	private boolean loading;

	public FontPropertiesPanel(FontMapper fontMapper)
	{
		this.fontMapper = fontMapper;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createTitledBorder("Font properties"));

		fldName     = readOnlyField(150);
		fldFamily   = readOnlyField(160);
		fldStyle    = combo(STYLES,    110);
		fldRefH     = text(50);
		fldRefH.setHorizontalAlignment(SwingConstants.CENTER);
		fldRefW     = text(50);
		fldRefW.setHorizontalAlignment(SwingConstants.CENTER);
		fldFilename = readOnlyField(160);
		fldRenderer = combo(RENDERERS, 110);
		fldSpacing  = combo(SPACINGS,  120);
		fldAscent   = text(55);
		fldAscent.setHorizontalAlignment(SwingConstants.CENTER);
		fldDescent  = text(55);
		fldDescent.setHorizontalAlignment(SwingConstants.CENTER);

		JButton4j btnFamily = iconBtn(Common.icon_font, ROW_H, "Pick a Java font family");
		btnFamily.addActionListener(_ -> {
			Window w = SwingUtilities.getWindowAncestor(this);
			String picked = JDialogFamilyPicker.pick(w, fldFamily.getText());
			if (picked != null) {
				fldFamily.setText(picked);
				fldFilename.setText(""); // family chosen manually clears TTF
			}
		});

		JButton4j btnFile = iconBtn(Common.icon_open, ROW_H, "Browse fonts/ for a TTF file");
		btnFile.addActionListener(_ -> pickTtfFile());

		// Track all editable widgets so we can enable/disable as a group
		editableWidgets.addAll(java.util.List.of(
				fldFamily, fldStyle, fldRefH, fldRefW, fldFilename,
				fldRenderer, fldSpacing, fldAscent, fldDescent,
				btnFamily, btnFile));

		// Wire dirty-tracking to every editable control. Readonly fields still
		// need listeners because the pickers mutate them programmatically.
		DocumentListener dirty = new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e)  { checkDirty(); }
			@Override public void removeUpdate(DocumentEvent e)  { checkDirty(); }
			@Override public void changedUpdate(DocumentEvent e) { checkDirty(); }
		};
		fldFamily  .getDocument().addDocumentListener(dirty);
		fldFilename.getDocument().addDocumentListener(dirty);
		fldRefH    .getDocument().addDocumentListener(dirty);
		fldRefW    .getDocument().addDocumentListener(dirty);
		fldAscent  .getDocument().addDocumentListener(dirty);
		fldDescent .getDocument().addDocumentListener(dirty);
		fldStyle   .addActionListener(_ -> checkDirty());
		fldRenderer.addActionListener(_ -> checkDirty());
		fldSpacing .addActionListener(_ -> checkDirty());

		// Layout: GridBagLayout with four columns. Left column holds labels
		// "Name / Style / Render / Ref H / Ref W" (right-aligned) above their
		// fields in column 1. Right column pairs with "Family / TTF / Space /
		// Asc / Dsc" in column 3. A picker button slot (column 4) trails the
		// right-hand field when present. This makes the panel narrower than
		// the earlier 6-column variant at the cost of an extra row.
		//
		// col 0: left label (right-aligned)
		// col 1: first field (left-aligned)
		// col 2: right label (right-aligned)
		// col 3: second field (left-aligned)
		// col 4: picker button (optional)
		JPanel grid = new JPanel(new GridBagLayout());
		// Bottom padding (last arg) gives the final row of fields some
		// breathing room above the titled-border baseline.
		grid.setBorder(BorderFactory.createEmptyBorder(2, 4, 10, 4));
		addRow(grid, 0, "Name",   fldName,     "Family", fldFamily,   btnFamily);
		addRow(grid, 1, "Style",  fldStyle,    "TTF",    fldFilename, btnFile);
		addRow(grid, 2, "Render", fldRenderer, "Space",  fldSpacing,  null);
		addRow(grid, 3, "Ref H",  fldRefH,     "Asc",    fldAscent,   null);
		addRow(grid, 4, "Ref W",  fldRefW,     "Dsc",    fldDescent,  null);

		add(grid, BorderLayout.CENTER);

		// Start with all editable widgets disabled — they're enabled when
		// setFont() binds to a real font name.
		for (Component c : editableWidgets) c.setEnabled(false);
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	public void setOnFontSaved(Runnable onFontSaved) { this.onFontSaved = onFontSaved; }

	/** Register a callback that fires whenever the dirty state might have changed. */
	public void setDirtyListener(Runnable listener) { this.dirtyListener = listener; }

	/** Label name currently bound to the panel, or {@code null} if hidden. */
	public String getCurrentFontName() { return currentFontName; }

	/** True when the user has edited a widget since the last snapshot. */
	public boolean hasPendingChanges()
	{
		return currentFontName != null
		    && initialValues != null
		    && !Arrays.equals(getCurrentValues(), initialValues);
	}

	/** Commit any pending edits — equivalent to pressing the Apply button. */
	public void applyPending()  { applyChanges();  }

	/** Revert any pending edits — equivalent to pressing the Cancel button. */
	public void discardPending(){ cancelChanges(); }

	/**
	 * Bind the panel to a font by its label name. {@code null} (or a name
	 * absent from fonts.xml) hides the panel and its button-bar buttons.
	 */
	public void setFont(String fontName)
	{
		if (fontName == null || fontName.isBlank()) {
			hideAll();
			return;
		}
		FontProperties fp = lookup(fontName);
		if (fp == null) {
			hideAll();
			return;
		}

		loading = true;
		try {
			currentFontName = fontName;
			fldName    .setText(defaultStr(fp.labelName, fontName));
			fldFamily  .setText(defaultStr(fp.family,    ""));
			fldStyle   .setSelectedItem(defaultStr(fp.style,    "PLAIN"));
			fldRefH    .setText(defaultStr(fp.refHeight, ""));
			fldRefW    .setText(defaultStr(fp.refWidth,  ""));
			fldFilename.setText(defaultStr(fp.filename,  ""));
			fldRenderer.setSelectedItem(defaultStr(fp.renderer, "SCALEABLE"));
			fldSpacing .setSelectedItem(defaultStr(fp.spacing,  "PROPORTIONAL"));
			fldAscent  .setText(defaultStr(fp.ascentFactor,  "1.0"));
			fldDescent .setText(defaultStr(fp.descentFactor, "0.25"));
		} finally {
			loading = false;
		}
		initialValues = getCurrentValues();

		for (Component c : editableWidgets) c.setEnabled(true);
		if (dirtyListener != null) dirtyListener.run();
	}

	// -------------------------------------------------------------------------
	// Internals
	// -------------------------------------------------------------------------

	private void hideAll()
	{
		currentFontName = null;
		initialValues = null;
		// Clear fields and disable widgets — we stay visible inside the tab
		loading = true;
		try {
			fldName.setText("");
			fldFamily.setText("");
			fldStyle.setSelectedIndex(0);
			fldRefH.setText("");
			fldRefW.setText("");
			fldFilename.setText("");
			fldRenderer.setSelectedIndex(0);
			fldSpacing.setSelectedIndex(0);
			fldAscent.setText("");
			fldDescent.setText("");
		} finally {
			loading = false;
		}
		for (Component c : editableWidgets) c.setEnabled(false);
		if (dirtyListener != null) dirtyListener.run();
	}

	private void checkDirty()
	{
		if (loading || currentFontName == null || initialValues == null)
			return;
		if (dirtyListener != null) dirtyListener.run();
	}

	private void applyChanges()
	{
		if (currentFontName == null) return;

		List<String[]> rows = fontMapper.getAllEntries();
		String[] updated = getCurrentValues();

		boolean replaced = false;
		for (int i = 0; i < rows.size(); i++) {
			if (currentFontName.equals(rows.get(i)[0])) {
				// Preserve the existing description (index 10) — not edited here
				if (rows.get(i).length > 10)
					updated[10] = rows.get(i)[10];
				rows.set(i, updated);
				replaced = true;
				break;
			}
		}
		if (!replaced) rows.add(updated);

		fontMapper.saveToXml(rows);
		fontMapper.reload();

		// Saved state is now the new baseline.
		initialValues = getCurrentValues();
		if (dirtyListener != null) dirtyListener.run();
		if (onFontSaved != null) onFontSaved.run();
	}

	private void cancelChanges()
	{
		if (initialValues == null) return;
		loading = true;
		try {
			// initialValues slots: 0 name, 1 family, 2 style, 3 refH, 4 refW,
			// 5 filename, 6 renderer, 7 spacing, 8 asc, 9 dsc
			fldFamily  .setText(initialValues[1]);
			fldStyle   .setSelectedItem(initialValues[2]);
			fldRefH    .setText(initialValues[3]);
			fldRefW    .setText(initialValues[4]);
			fldFilename.setText(initialValues[5]);
			fldRenderer.setSelectedItem(initialValues[6]);
			fldSpacing .setSelectedItem(initialValues[7]);
			fldAscent  .setText(initialValues[8]);
			fldDescent .setText(initialValues[9]);
		} finally {
			loading = false;
		}
		if (dirtyListener != null) dirtyListener.run();
	}

	/** Snapshot of the current widget values as an 11-column row (desc blank). */
	private String[] getCurrentValues()
	{
		return new String[] {
			fldName.getText().trim(),
			fldFamily.getText().trim(),
			(String) fldStyle.getSelectedItem(),
			fldRefH.getText().trim(),
			fldRefW.getText().trim(),
			fldFilename.getText().trim(),
			(String) fldRenderer.getSelectedItem(),
			(String) fldSpacing.getSelectedItem(),
			fldAscent.getText().trim(),
			fldDescent.getText().trim(),
			""
		};
	}

	private FontProperties lookup(String name)
	{
		// getFontProperties silently returns "default" for unknown names;
		// round-trip via getAllEntries to distinguish real hits from fallbacks.
		for (String[] row : fontMapper.getAllEntries()) {
			if (row.length > 0 && name.equals(row[0])) {
				return fontMapper.getFontProperties(name);
			}
		}
		return null;
	}

	private static String defaultStr(String s, String fallback)
	{
		return (s == null || s.isEmpty()) ? fallback : s;
	}

	private void pickTtfFile()
	{
		File fontsDir = new File(FontMapper.FONTS_DIR);
		JFileChooser fc = new JFileChooser(fontsDir.exists() ? fontsDir : new File("."));
		fc.setApproveButtonText("Select");
		fc.setMultiSelectionEnabled(false);
		fc.setFileFilter(new FileFilter() {
			@Override public boolean accept(File f) {
				return f.isDirectory() || f.getName().toLowerCase().endsWith(".ttf");
			}
			@Override public String getDescription() { return "TrueType Fonts (*.ttf)"; }
		});
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File chosen = fc.getSelectedFile();
			fldFilename.setText(chosen.getName());
			try {
				java.awt.Font loaded = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, chosen);
				fldFamily.setText(loaded.getFamily());
			} catch (Exception ex) {
				// file unreadable as font — leave family as-is
			}
		}
	}

	// ----- layout factory helpers --------------------------------------------

	/**
	 * Add a single 5-column row to the GridBagLayout grid.
	 * Columns 0 & 2 carry right-aligned labels so their fields' left edges
	 * align with the rest of the grid. The trailing {@code picker} slot (col 4)
	 * is optional — pass {@code null} when the row has no picker button.
	 */
	private static void addRow(JPanel grid, int row,
			String leftLabel,  Component leftField,
			String rightLabel, Component rightField, Component picker)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);
		c.gridy = row;

		c.gridx = 0; c.anchor = GridBagConstraints.EAST;
		grid.add(smallLabel(leftLabel), c);

		c.gridx = 1; c.anchor = GridBagConstraints.WEST;
		grid.add(leftField, c);

		c.gridx = 2; c.anchor = GridBagConstraints.EAST;
		grid.add(smallLabel(rightLabel), c);

		c.gridx = 3; c.anchor = GridBagConstraints.WEST;
		grid.add(rightField, c);

		c.gridx = 4; c.anchor = GridBagConstraints.WEST;
		grid.add(picker != null ? picker : Box.createHorizontalGlue(), c);
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

	private static JComboBox4j<String> combo(String[] items, int width)
	{
		JComboBox4j<String> cb = new JComboBox4j<>();
		cb.setModel(new DefaultComboBoxModel<>(items));
		lockSize(cb, width, ROW_H);
		return cb;
	}

	/**
	 * Pin a component to an exact size. Setting all three (preferred, minimum,
	 * maximum) is the reliable way to stop GridBagLayout / BoxLayout / the
	 * component's own L&F from collapsing a JTextField to a 1px-wide sliver or
	 * stretching a combo past its intended width.
	 */
	private static void lockSize(javax.swing.JComponent c, int w, int h)
	{
		Dimension d = new Dimension(w, h);
		c.setPreferredSize(d);
		c.setMinimumSize(d);
		c.setMaximumSize(d);
	}

	private static JButton4j iconBtn(Icon icon, int size, String tooltip)
	{
		JButton4j b = new JButton4j(icon);
		b.setPreferredSize(new Dimension(size, size));
		b.setToolTipText(tooltip);
		b.setFocusable(false);
		return b;
	}
}
