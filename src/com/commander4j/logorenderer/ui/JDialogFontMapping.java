package com.commander4j.logorenderer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileFilter;

import com.commander4j.gui.JButton4j;
import com.commander4j.gui.JComboBox4j;
import com.commander4j.gui.JLabel4j_std;
import com.commander4j.gui.JTextField4j;
import com.commander4j.logorenderer.FontMapper;
import com.commander4j.logorenderer.ParseLogger;

import java.util.logging.Logger;

import com.commander4j.sys.Common;

/**
 * Dialog for editing the LLF font name → Java font family mappings stored in
 * {@code xml/config/fonts.xml}.
 *
 * <p>Each row represents one {@code <font>} entry and exposes all fields from
 * the updated schema: label name, Java family, style, reference height, reference
 * width, optional TTF filename, renderer type and spacing type.</p>
 *
 * <p>"Scan Label" compares the font names used in the currently loaded LLF
 * against the mapped entries and adds pre-filled rows (highlighted in yellow)
 * for any that are missing.</p>
 *
 * <p>On OK the changes are saved to fonts.xml, the FontMapper is reloaded, and
 * the supplied {@code onSave} callback is invoked (typically a canvas repaint).</p>
 */
public class JDialogFontMapping extends JDialog
{
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger.getLogger(JDialogFontMapping.class.getName());

	/** Background colour for rows added by Scan Label. */
	static final Color HIGHLIGHT = new Color(255, 255, 180);

	/** Background colour for the row pre-selected via the source listing. */
	static final Color INITIAL_HIGHLIGHT = new Color(220, 235, 255);

	/** Insets + vertical scrollbar allowance subtracted from parent width. */
	private static final int CHROME = 50;
	/** Minimum description column width regardless of parent size. */
	private static final int MIN_DESC_W = 90;

	private final JPanel      rowsPanel = new JPanel();
	private final FontMapper  fontMapper;
	private final Set<String> labelFontNames;
	private final Runnable    onSave;
	private final int         descWidth;
	private final String      initialFontName;
	private ParseLogger       logger;
	/** Snapshot of rows as loaded, keyed by labelName — used for diff logging on save. */
	private final Map<String, String[]> initialRows = new LinkedHashMap<>();

	/** Column headings used in change-diff messages. */
	private static final String[] FIELD_LABELS = {
		"labelName", "family", "style", "refH", "refW",
		"ttf", "renderer", "spacing",
		"ascentFactor", "descentFactor",
		"description"
	};

	/** Open with no pre-selected row. */
	public JDialogFontMapping(Frame parent, FontMapper fontMapper,
	                          Set<String> labelFontNames, Runnable onSave)
	{
		this(parent, fontMapper, labelFontNames, onSave, null);
	}

	/**
	 * Open and pre-scroll to the row matching {@code initialFontName}
	 * (case-insensitive). Pass {@code null} for no pre-selection.
	 */
	public JDialogFontMapping(Frame parent, FontMapper fontMapper,
	                          Set<String> labelFontNames, Runnable onSave,
	                          String initialFontName)
	{
		// Non-modal so Apply can repaint the canvas behind the dialog and the
		// user can see the effect of Asc/Desc tweaks without closing/reopening.
		super(parent, "Font Mappings", false);
		this.fontMapper      = fontMapper;
		this.labelFontNames  = labelFontNames;
		this.onSave          = onSave;
		this.initialFontName = initialFontName;

		int available = parent.getWidth() - FontRowPanel.FIXED_COLS - CHROME;
		this.descWidth = Math.max(MIN_DESC_W, available) / 2;

		buildUI();
		populateRows();
		snapshotInitialRows();
		pack();
		setLocationRelativeTo(parent);
		scrollToInitialFont();
	}

	// -------------------------------------------------------------------------
	// UI construction
	// -------------------------------------------------------------------------

	private void buildUI()
	{
		setResizable(true);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		getContentPane().setLayout(new BorderLayout(0, 4));
		((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		getContentPane().add(buildHeader(), BorderLayout.NORTH);

		rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
		JScrollPane scroll = new JScrollPane(rowsPanel);
		int totalW = FontRowPanel.FIXED_COLS + descWidth;
		scroll.setPreferredSize(new Dimension(totalW + 22, 350));
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		getContentPane().add(scroll, BorderLayout.CENTER);
		getContentPane().add(buildButtons(), BorderLayout.SOUTH);
	}

	private JPanel buildHeader()
	{
		int totalW = FontRowPanel.FIXED_COLS + descWidth;
		JPanel header = new JPanel(null);
		header.setPreferredSize(new Dimension(totalW, 18));

		int x = 0;
		x = addHdr(header, "Label Name",  x, FontRowPanel.NAME_W,
				"Font name as it appears in the LLF file (e.g. sw050bsn)");
		x = addHdr(header, "Java Family", x, FontRowPanel.FAMILY_W + FontRowPanel.BTN_W,
				"AWT font family to use when no TTF filename is set");
		x = addHdr(header, "Style",       x, FontRowPanel.STYLE_W);
		x = addHdr(header, "Ref H",       x, FontRowPanel.REFH_W,
				"Reference character-cell height in printer dots (encoded in fixed font names)");
		x = addHdr(header, "Ref W",       x, FontRowPanel.REFW_W,
				"Reference character-cell width in printer dots");
		x = addHdr(header, "TTF Filename",x, FontRowPanel.FILE_W + FontRowPanel.BTN_W,
				"Optional TTF file in the fonts/ folder. When set, overrides Java Family.");
		x = addHdr(header, "Renderer",    x, FontRowPanel.RENDER_W,
				"SCALEABLE = tight GlyphVector ink bounds; BITMAP = loose FontMetrics bounds");
		x = addHdr(header, "Spacing",     x, FontRowPanel.SPACING_W,
				"PROPORTIONAL or MONOSPACED — controls how width is derived from Ref H");
		x = addHdr(header, "Asc",         x, FontRowPanel.FACTOR_W,
				"Cell ascent as a multiple of Ref H (top-of-cell to baseline). Calibrate per font.");
		x = addHdr(header, "Desc",        x, FontRowPanel.FACTOR_W,
				"Cell descent as a multiple of Ref H (baseline to bottom-of-cell). Calibrate per font.");
		    addHdr(header, "Description", x, descWidth);

		return header;
	}

	private int addHdr(JPanel panel, String text, int x, int w)
	{
		return addHdr(panel, text, x, w, null);
	}

	private int addHdr(JPanel panel, String text, int x, int w, String tooltip)
	{
		JLabel4j_std lbl = new JLabel4j_std(text);
		lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
		lbl.setBounds(x + 2, 1, w - 2, 16);
		if (tooltip != null)
			lbl.setToolTipText(tooltip);
		panel.add(lbl);
		return x + w;
	}

	private JPanel buildButtons()
	{
		JButton4j addBtn = new JButton4j("Add Row");
		addBtn.addActionListener(_ -> addNewRow());

		JButton4j scanBtn = new JButton4j("Scan Label");
		scanBtn.setToolTipText("Add rows for any fonts used in the current label that are not yet mapped");
		scanBtn.setEnabled(!labelFontNames.isEmpty());
		scanBtn.addActionListener(_ -> scanLabel());

		JButton4j applyBtn = new JButton4j("Apply");
		applyBtn.setToolTipText("Save changes and repaint the canvas without closing this dialog — "
				+ "drag the dialog aside to see the effect of Asc/Desc tweaks");
		applyBtn.addActionListener(_ -> applyChanges());

		JButton4j okBtn = new JButton4j("OK");
		okBtn.addActionListener(_ -> save());
		getRootPane().setDefaultButton(okBtn);

		JButton4j cancelBtn = new JButton4j("Cancel");
		cancelBtn.addActionListener(_ -> dispose());

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		left.add(addBtn);
		left.add(scanBtn);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		right.add(applyBtn);
		right.add(okBtn);
		right.add(cancelBtn);

		JPanel bar = new JPanel(new BorderLayout());
		bar.add(left,  BorderLayout.WEST);
		bar.add(right, BorderLayout.EAST);
		return bar;
	}

	// -------------------------------------------------------------------------
	// Row management
	// -------------------------------------------------------------------------

	private void populateRows()
	{
		for (String[] entry : fontMapper.getAllEntries())
		{
			boolean isInitial = initialFontName != null
					&& initialFontName.equalsIgnoreCase(entry[0]);
			rowsPanel.add(new FontRowPanel(this, entry, descWidth, false, isInitial));
		}
	}

	/** Scroll the pre-selected row into view after the dialog is shown. */
	private void scrollToInitialFont()
	{
		if (initialFontName == null || initialFontName.isBlank())
			return;
		SwingUtilities.invokeLater(() ->
		{
			for (Component c : rowsPanel.getComponents())
			{
				if (c instanceof FontRowPanel row
						&& row.getLabelName().equalsIgnoreCase(initialFontName))
				{
					row.scrollRectToVisible(
							new java.awt.Rectangle(0, 0, row.getWidth(), row.getHeight()));
					break;
				}
			}
		});
	}

	private void addNewRow()
	{
		rowsPanel.add(new FontRowPanel(this,
				new String[] { "", "SansSerif", "PLAIN", "30", "18", "", "SCALEABLE", "PROPORTIONAL",
				               "1.0", "0.25", "" },
				descWidth, false, false));
		rowsPanel.revalidate();
		rowsPanel.repaint();
	}

	/**
	 * Adds a highlighted row for each font used in the label that has no
	 * existing mapping row.  Rows already present (by label name) are skipped.
	 */
	private void scanLabel()
	{
		Set<String> alreadyMapped = new java.util.HashSet<>();
		for (Component c : rowsPanel.getComponents())
			if (c instanceof FontRowPanel)
				alreadyMapped.add(((FontRowPanel) c).getLabelName());

		int added = 0;
		for (String fontName : new java.util.TreeSet<>(labelFontNames))
		{
			if (!alreadyMapped.contains(fontName))
			{
				rowsPanel.add(new FontRowPanel(this,
						new String[] { fontName, "SansSerif", "PLAIN", "30", "18",
						               "", "SCALEABLE", "PROPORTIONAL",
						               "1.0", "0.25", "" },
						descWidth, true, false));
				added++;
			}
		}

		if (added == 0)
		{
			javax.swing.JOptionPane.showMessageDialog(this,
					"All fonts used in the current label are already mapped.",
					"Scan Label", javax.swing.JOptionPane.INFORMATION_MESSAGE);
		}
		else
		{
			rowsPanel.revalidate();
			rowsPanel.repaint();
		}
	}

	void deleteRow(FontRowPanel row)
	{
		rowsPanel.remove(row);
		rowsPanel.revalidate();
		rowsPanel.repaint();
	}

	// -------------------------------------------------------------------------
	// Save
	// -------------------------------------------------------------------------

	private void save()
	{
		applyChanges();
		dispose();
	}

	/**
	 * Commit current row values to {@code fonts.xml}, reload the mapper, and
	 * repaint the canvas — all without closing the dialog. Re-snapshots the
	 * initial rows so the next Apply / OK reports only subsequent changes.
	 */
	private void applyChanges()
	{
		List<String[]> rows = new ArrayList<>();
		for (Component c : rowsPanel.getComponents())
			if (c instanceof FontRowPanel)
				rows.add(((FontRowPanel) c).getValues());
		logChanges(rows);
		fontMapper.saveToXml(rows);
		fontMapper.reload();
		onSave.run();
		snapshotInitialRows();
	}

	/** Attach a logger that receives a diff summary of edits on Save. */
	public void setLogger(ParseLogger logger)
	{
		this.logger = logger;
	}

	/** Capture initial row values by labelName so Save can report per-field diffs. */
	private void snapshotInitialRows()
	{
		initialRows.clear();
		for (Component c : rowsPanel.getComponents())
			if (c instanceof FontRowPanel row)
			{
				String[] v = row.getValues();
				if (!v[0].isEmpty())
					initialRows.put(v[0], v);
			}
	}

	/**
	 * Emit one log line per added / removed / changed row, comparing the rows
	 * about to be saved against the snapshot taken when the dialog opened.
	 */
	private void logChanges(List<String[]> currentRows)
	{
		Map<String, String[]> current = new HashMap<>();
		for (String[] row : currentRows)
			if (!row[0].isEmpty())
				current.put(row[0], row);

		int changes = 0;
		for (Map.Entry<String, String[]> e : current.entrySet())
		{
			String[] now   = e.getValue();
			String[] before = initialRows.get(e.getKey());
			if (before == null)
			{
				emit("Font mapping added: " + e.getKey()
						+ " family=" + safe(now, 1)
						+ " refH=" + safe(now, 3) + " refW=" + safe(now, 4));
				changes++;
				continue;
			}
			String diff = buildFieldDiff(before, now);
			if (!diff.isEmpty())
			{
				emit("Font mapping changed: " + e.getKey() + " — " + diff);
				changes++;
			}
		}
		for (String name : initialRows.keySet())
			if (!current.containsKey(name))
			{
				emit("Font mapping removed: " + name);
				changes++;
			}

		if (changes == 0)
			emit("Font mappings saved — no changes detected");
	}

	/** Route a message to both the UI log (if attached) and java.util.logging. */
	private void emit(String msg)
	{
		LOG.info(msg);
		if (logger != null)
			logger.info(msg);
	}

	private static String buildFieldDiff(String[] before, String[] after)
	{
		StringBuilder sb = new StringBuilder();
		int n = Math.max(before.length, after.length);
		for (int i = 1; i < n && i < FIELD_LABELS.length; i++) // skip labelName (key)
		{
			String a = safe(before, i);
			String b = safe(after,  i);
			if (!a.equals(b))
			{
				if (sb.length() > 0) sb.append(", ");
				sb.append(FIELD_LABELS[i]).append(": ")
				  .append(a.isEmpty() ? "∅" : a)
				  .append(" → ")
				  .append(b.isEmpty() ? "∅" : b);
			}
		}
		return sb.toString();
	}

	private static String safe(String[] arr, int i)
	{
		return (arr != null && i < arr.length && arr[i] != null) ? arr[i].trim() : "";
	}

	// =========================================================================
	// Inner class: one row in the font mapping table
	// =========================================================================

	static final class FontRowPanel extends JPanel
	{
		private static final long serialVersionUID = 1L;

		// Column widths — kept consistent with the ZPL renderer's JPanelFontData
		static final int ROW_H     = 32;
		static final int NAME_W    = 150;
		static final int FAMILY_W  = 150;
		static final int BTN_W     = 32;
		static final int STYLE_W   = 100;
		static final int REFH_W    = 32;
		static final int REFW_W    = 32;
		static final int FILE_W    = 180;
		static final int RENDER_W  = 100;
		static final int SPACING_W = 120;
		static final int FACTOR_W  = 48;
		static final int DEL_W     = 32;

		/** Sum of all fixed-width columns (everything except DESC_W). */
		static final int FIXED_COLS =
			NAME_W + FAMILY_W + BTN_W + STYLE_W +
			REFH_W + REFW_W +
			FILE_W + BTN_W +
			RENDER_W + SPACING_W +
			FACTOR_W + FACTOR_W +
			DEL_W;

		private static final String[] STYLES   = { "PLAIN", "BOLD", "ITALIC", "BOLD_ITALIC" };
		private static final String[] RENDERERS = { "SCALEABLE", "BITMAP" };
		private static final String[] SPACINGS  = { "PROPORTIONAL", "MONOSPACED" };

		private final JTextField4j        fldName;
		private final JTextField4j        fldFamily;
		private final JTextField4j        fldRefH;
		private final JTextField4j        fldRefW;
		private final JTextField4j        fldFilename;
		private final JTextField4j        fldAscent;
		private final JTextField4j        fldDescent;
		private final JTextField4j        fldDesc;
		private final JComboBox4j<String> fldStyle;
		private final JComboBox4j<String> fldRenderer;
		private final JComboBox4j<String> fldSpacing;

		FontRowPanel(JDialogFontMapping parent, String[] entry, int descWidth,
		             boolean highlighted, boolean initial)
		{
			int totalW = FIXED_COLS + descWidth;
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			setBorder(new LineBorder(Color.LIGHT_GRAY));
			Color bg = highlighted ? HIGHLIGHT : initial ? INITIAL_HIGHLIGHT : Color.WHITE;
			setBackground(bg);
			setPreferredSize(new Dimension(totalW, ROW_H));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_H));

			// [0] Label Name
			fldName = field(entry, 0, NAME_W, bg);
			add(fldName);

			// [1] Java Family + picker
			// fldFilename must be initialised before the btnFamily lambda captures it
			fldFilename = field(entry, 5, FILE_W, bg);
			fldFilename.setToolTipText("TTF file in fonts/ directory. Overrides Java Family when set.");

			fldFamily = field(entry, 1, FAMILY_W, bg);
			add(fldFamily);
			JButton4j btnFamily = iconBtn(Common.icon_font, BTN_W, ROW_H);
			btnFamily.addActionListener(_ ->
			{
				String picked = JDialogFamilyPicker.pick(parent, fldFamily.getText());
				if (picked != null)
				{
					fldFamily.setText(picked);
					fldFilename.setText(""); // family chosen manually — clear TTF
				}
			});
			add(btnFamily);

			// [2] Style
			fldStyle = combo(STYLES, entry.length > 2 ? entry[2] : "PLAIN", STYLE_W, ROW_H);
			add(fldStyle);

			// [3] Ref Height
			fldRefH = field(entry, 3, REFH_W, bg);
			fldRefH.setHorizontalAlignment(SwingConstants.CENTER);
			fldRefH.setToolTipText("Reference character-cell height in printer dots");
			add(fldRefH);

			// [4] Ref Width
			fldRefW = field(entry, 4, REFW_W, bg);
			fldRefW.setHorizontalAlignment(SwingConstants.CENTER);
			fldRefW.setToolTipText("Reference character-cell width in printer dots");
			add(fldRefW);

			// [5] TTF Filename + file picker
			add(fldFilename);
			JButton4j btnFile = iconBtn(Common.icon_open, BTN_W, ROW_H);
			btnFile.setToolTipText("Browse fonts/ directory for a TTF file");
			btnFile.addActionListener(_ -> pickTtfFile(parent));
			add(btnFile);

			// [6] Renderer
			fldRenderer = combo(RENDERERS, entry.length > 6 ? entry[6] : "SCALEABLE", RENDER_W, ROW_H);
			fldRenderer.setToolTipText("SCALEABLE = tight GlyphVector ink bounds; BITMAP = loose FontMetrics");
			add(fldRenderer);

			// [7] Spacing
			fldSpacing = combo(SPACINGS, entry.length > 7 ? entry[7] : "PROPORTIONAL", SPACING_W, ROW_H);
			fldSpacing.setToolTipText("PROPORTIONAL or MONOSPACED");
			add(fldSpacing);

			// [8] Ascent factor
			fldAscent = field(entry, 8, FACTOR_W, bg);
			fldAscent.setHorizontalAlignment(SwingConstants.CENTER);
			fldAscent.setToolTipText("Cell ascent as a multiple of Ref H (top-of-cell to baseline)");
			add(fldAscent);

			// [9] Descent factor
			fldDescent = field(entry, 9, FACTOR_W, bg);
			fldDescent.setHorizontalAlignment(SwingConstants.CENTER);
			fldDescent.setToolTipText("Cell descent as a multiple of Ref H (baseline to bottom-of-cell)");
			add(fldDescent);

			// [10] Description
			fldDesc = field(entry, 10, descWidth, bg);
			add(fldDesc);

			// Delete button
			JButton4j btnDel = new JButton4j("✕");
			btnDel.setPreferredSize(new Dimension(DEL_W, ROW_H));
			btnDel.setMaximumSize(new Dimension(DEL_W, ROW_H));
			btnDel.setFocusable(false);
			btnDel.addActionListener(_ -> parent.deleteRow(this));
			add(btnDel);
		}

		/** The label font name currently in the name field. */
		String getLabelName()
		{
			return fldName.getText().trim();
		}

		/**
		 * Returns the eleven field values for this row:
		 * {@code {labelName, family, style, refHeight, refWidth,
		 *          filename, renderer, spacing,
		 *          ascentFactor, descentFactor, description}}.
		 */
		String[] getValues()
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
				fldDesc.getText().trim()
			};
		}

		// -------------------------------------------------------------------------
		// TTF file picker — opens a file chooser in the fonts/ directory
		// -------------------------------------------------------------------------

		private void pickTtfFile(JDialogFontMapping parent)
		{
			File fontsDir = new File(FontMapper.FONTS_DIR);
			JFileChooser fc = new JFileChooser(fontsDir.exists() ? fontsDir : new File("."));
			fc.setApproveButtonText("Select");
			fc.setMultiSelectionEnabled(false);
			fc.setFileFilter(new FileFilter()
			{
				@Override
				public boolean accept(File f)
				{
					return f.isDirectory() || f.getName().toLowerCase().endsWith(".ttf");
				}

				@Override
				public String getDescription()
				{
					return "TrueType Fonts (*.ttf)";
				}
			});

			if (fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
			{
				File chosen = fc.getSelectedFile();
				fldFilename.setText(chosen.getName());
				// Auto-populate the family name from the TTF metadata
				try
				{
					java.awt.Font loaded = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, chosen);
					fldFamily.setText(loaded.getFamily());
				}
				catch (Exception ex)
				{
					// File selected but not readable as a font — leave family unchanged
				}
			}
		}

		// -------------------------------------------------------------------------
		// Factory helpers
		// -------------------------------------------------------------------------

		private static JTextField4j field(String[] entry, int idx, int width, Color bg)
		{
			JTextField4j f = new JTextField4j(entry.length > idx ? entry[idx] : "");
			f.setPreferredSize(new Dimension(width, ROW_H));
			f.setMaximumSize(new Dimension(width, ROW_H));
			f.setBackground(bg);
			f.setCaretPosition(0);
			return f;
		}

		private static JComboBox4j<String> combo(String[] items, String selected, int width, int height)
		{
			JComboBox4j<String> cb = new JComboBox4j<>();
			cb.setModel(new DefaultComboBoxModel<>(items));
			cb.setSelectedItem(selected);
			cb.setPreferredSize(new Dimension(width, height));
			cb.setMaximumSize(new Dimension(width, height));
			return cb;
		}

		private static JButton4j iconBtn(javax.swing.Icon icon, int w, int h)
		{
			JButton4j btn = new JButton4j(icon);
			btn.setPreferredSize(new Dimension(w, h));
			btn.setMaximumSize(new Dimension(w, h));
			btn.setFocusable(false);
			return btn;
		}
	}
}
