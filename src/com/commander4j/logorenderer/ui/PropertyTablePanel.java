package com.commander4j.logorenderer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.util.Arrays;
import java.util.EventObject;
import java.util.List;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import com.commander4j.logorenderer.FontMapper;

/**
 * A two-column property grid: "Property" (read-only label) and "Value"
 * (editable, with editor type dispatched per row via {@link PropertyDef}).
 *
 * <p>Bind to a source line with {@link #bind(String[], List)}. The panel
 * stores the parts array and patches it in place on cell edits. Call
 * {@link #getModifiedLine()} to retrieve the reconstructed line.
 */
public final class PropertyTablePanel extends JPanel
{

	private static final long serialVersionUID = 1L;
	private static final int ROW_HEIGHT = 22;
	private static final LineBorder FIELD_BORDER = new LineBorder(Color.GRAY);
	private static final Color ALT_BG = new Color(214, 217, 223);

	/** Row background matching the Source list's zebra striping. */
	private static Color rowBg(JTable t, int row, boolean sel)
	{
		if (sel) return t.getSelectionBackground();
		return row % 2 == 0 ? Color.WHITE : ALT_BG;
	}

	private final PropTableModel tableModel;
	private final JTable         table;
	private final String         propertyColumnName;

	private String[] parts;
	private List<PropertyDef> defs = List.of();
	private String[] initialSnapshot;

	private Runnable dirtyListener;

	public PropertyTablePanel(String propertyColumnName)
	{
		this.propertyColumnName = propertyColumnName;
		setLayout(new BorderLayout());

		tableModel = new PropTableModel();
		table = new JTable(tableModel) {
			@Override
			public TableCellEditor getCellEditor(int row, int column)
			{
				if (column == 1 && row < defs.size()) {
					return createEditor(defs.get(row));
				}
				return super.getCellEditor(row, column);
			}

			@Override
			public TableCellRenderer getCellRenderer(int row, int column)
			{
				if (column == 1 && row < defs.size()) {
					return createRenderer(defs.get(row));
				}
				return super.getCellRenderer(row, column);
			}
		};
		table.setRowHeight(ROW_HEIGHT);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getTableHeader().setReorderingAllowed(false);
		table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		table.setShowHorizontalLines(true);
		table.setShowVerticalLines(true);
		table.setGridColor(new Color(0xD0D0D0));
		table.setBackground(Color.WHITE);
		table.setIntercellSpacing(new Dimension(1, 1));

		// Safety net for macOS Aqua L&F: pre-set the focus-ring colour that
		// AquaFocusHandler.swapSelectionColors reads, preventing NPE if any
		// residual Aqua listener fires on this table.
		if (table.getClientProperty("JComponent.cellFocusRingColor") == null) {
			table.putClientProperty("JComponent.cellFocusRingColor", new Color(0x6B9EFA));
		}

		// Column widths — Property label narrower, Value gets remaining space
		table.getColumnModel().getColumn(0).setPreferredWidth(130);
		table.getColumnModel().getColumn(1).setPreferredWidth(370);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

		// Label column — right-aligned, alternating row background
		DefaultTableCellRenderer labelRenderer = new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;
			@Override
			public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col)
			{
				super.getTableCellRendererComponent(t, v, sel, focus, row, col);
				setBackground(rowBg(t, row, sel));
				return this;
			}
		};
		labelRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
		table.getColumnModel().getColumn(0).setCellRenderer(labelRenderer);

		JScrollPane scroll = new JScrollPane(table);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		add(scroll, BorderLayout.CENTER);
	}

	// -----------------------------------------------------------------
	// Public API
	// -----------------------------------------------------------------

	public void setDirtyListener(Runnable listener) { this.dirtyListener = listener; }

	/**
	 * Bind the table to a parsed source line. {@code parts} is the
	 * comma-split array; {@code defs} describes which properties to show.
	 */
	public void bind(String[] parts, List<PropertyDef> defs)
	{
		commitPendingEdit();
		this.parts = parts.clone();
		this.defs  = defs;
		initialSnapshot = snapshotValues();
		tableModel.fireTableDataChanged();
		setVisible(true);
	}

	/** Unbind — hides the panel. */
	public void unbind()
	{
		commitPendingEdit();
		this.parts = null;
		this.defs  = List.of();
		this.initialSnapshot = null;
		tableModel.fireTableDataChanged();
		setVisible(false);
	}

	public boolean isBound() { return parts != null; }

	/** True if any value has changed since the last bind or apply. */
	public boolean hasPendingChanges()
	{
		if (parts == null || initialSnapshot == null) return false;
		return !Arrays.equals(snapshotValues(), initialSnapshot);
	}

	/** Commit current edits and return the reconstructed line, or null if unbound. */
	public String getModifiedLine()
	{
		commitPendingEdit();
		return parts != null ? String.join(",", parts) : null;
	}

	/** Reset the dirty baseline to the current state (call after external save). */
	public void resetBaseline()
	{
		initialSnapshot = snapshotValues();
		if (dirtyListener != null) dirtyListener.run();
	}

	/** Revert all values to the last baseline. */
	public void discardPending()
	{
		if (parts == null || initialSnapshot == null) return;
		for (int i = 0; i < defs.size() && i < initialSnapshot.length; i++) {
			parts = defs.get(i).setValue(parts, initialSnapshot[i]);
		}
		tableModel.fireTableDataChanged();
		if (dirtyListener != null) dirtyListener.run();
	}

	/** Snapshot current values for dirty comparison. */
	private String[] snapshotValues()
	{
		if (parts == null) return new String[0];
		String[] snap = new String[defs.size()];
		for (int i = 0; i < defs.size(); i++) {
			snap[i] = defs.get(i).getValue(parts);
		}
		return snap;
	}

	private void commitPendingEdit()
	{
		if (table.isEditing()) {
			table.getCellEditor().stopCellEditing();
		}
	}

	private void onValueChanged()
	{
		if (dirtyListener != null) dirtyListener.run();
	}

	// -----------------------------------------------------------------
	// Table model
	// -----------------------------------------------------------------

	private final class PropTableModel extends AbstractTableModel
	{
		private static final long serialVersionUID = 1L;

		@Override public int getRowCount()    { return defs.size(); }
		@Override public int getColumnCount() { return 2; }

		@Override
		public String getColumnName(int col)
		{
			return col == 0 ? propertyColumnName : "Value";
		}

		@Override
		public boolean isCellEditable(int row, int col)
		{
			if (col == 0) return false;
			return row < defs.size()
					&& defs.get(row).getEditorType() != PropertyDef.EditorType.READONLY;
		}

		@Override
		public Object getValueAt(int row, int col)
		{
			if (row >= defs.size() || parts == null) return "";
			PropertyDef def = defs.get(row);
			return col == 0 ? def.getLabel() : def.getValue(parts);
		}

		@Override
		public void setValueAt(Object value, int row, int col)
		{
			if (col != 1 || row >= defs.size() || parts == null) return;
			parts = defs.get(row).setValue(parts, String.valueOf(value));
			fireTableCellUpdated(row, col);
			onValueChanged();
		}
	}

	// -----------------------------------------------------------------
	// Cell editors
	// -----------------------------------------------------------------

	private TableCellEditor createEditor(PropertyDef def)
	{
		return switch (def.getEditorType()) {
			case READONLY     -> null; // shouldn't be called
			case TEXT         -> new DefaultCellEditor(new JTextField());
			case SPINNER      -> new SpinnerEditor(def.getStep());
			case COMBO        -> new ComboEditor(def.getComboValues());
			case FLAGS        -> new FlagsEditor(def.getFlagDefs());
			case FILE_PICKER  -> new FilePickerEditor();
			case FONT_PICKER  -> new FontPickerEditor();
			case IMAGE_PICKER -> new ImagePickerEditor();
		};
	}

	private TableCellRenderer createRenderer(PropertyDef def)
	{
		return switch (def.getEditorType()) {
			case READONLY     -> new ReadOnlyRenderer();
			case TEXT         -> new CellBackgroundRenderer();
			case SPINNER      -> new SpinnerRenderer();
			case COMBO        -> new ComboRenderer(def.getComboValues());
			case FLAGS        -> new FlagsRenderer(def.getFlagDefs());
			case FILE_PICKER  -> new ButtonRenderer("...");
			case FONT_PICKER  -> new ButtonRenderer("...");
			case IMAGE_PICKER -> new ButtonRenderer("...");
		};
	}

	// --- Renderers that show controls even when not editing ---

	/** Standard value renderer: uses the table background so all rows are consistent. */
	private static final class CellBackgroundRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		@Override
		public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col)
		{
			super.getTableCellRendererComponent(t, v, sel, focus, row, col);
			setBackground(rowBg(t, row, sel));
			return this;
		}
	}

	/** Read-only: slightly muted foreground. */
	private static final class ReadOnlyRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		@Override
		public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col)
		{
			super.getTableCellRendererComponent(t, v, sel, focus, row, col);
			setBackground(rowBg(t, row, sel));
			if (!sel) setForeground(Color.DARK_GRAY);
			return this;
		}
	}

	/** Spinner: text field with ▲▼ arrows on the right, fixed width, left-aligned. */
	private static final class SpinnerRenderer implements TableCellRenderer
	{
		private static final int SPINNER_WIDTH = 100;
		private final JPanel     wrapper;
		private final JPanel     spinner;
		private final JTextField field;
		private final JPanel     arrows;

		SpinnerRenderer()
		{
			field = new JTextField();
			field.setEditable(false);
			field.setHorizontalAlignment(SwingConstants.CENTER);
			field.setBorder(FIELD_BORDER);

			JButton up   = flatArrowButton("\u25B2");
			JButton down = flatArrowButton("\u25BC");

			arrows = new JPanel();
			arrows.setLayout(new BoxLayout(arrows, BoxLayout.Y_AXIS));
			arrows.add(up);
			arrows.add(down);

			spinner = new JPanel(new BorderLayout(0, 0));
			spinner.add(field, BorderLayout.CENTER);
			spinner.add(arrows, BorderLayout.EAST);
			Dimension sz = new Dimension(SPINNER_WIDTH, ROW_HEIGHT);
			spinner.setPreferredSize(sz);
			spinner.setMaximumSize(sz);

			wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			wrapper.add(spinner);
		}

		private static JButton flatArrowButton(String label)
		{
			JButton b = new JButton(label);
			b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
			b.setBorder(BorderFactory.createLineBorder(Color.GRAY));
			b.setBackground(new Color(0xE8E8E8));
			Dimension d = new Dimension(18, ROW_HEIGHT / 2);
			b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d);
			b.setMargin(new Insets(0, 0, 0, 0));
			b.setFont(b.getFont().deriveFont(7f));
			b.setFocusable(false);
			return b;
		}

		@Override
		public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col)
		{
			field.setText(v != null ? v.toString() : "");
			Color bg = rowBg(t, row, sel);
			Color fg = sel ? t.getSelectionForeground() : t.getForeground();
			field.setBackground(sel ? bg : Color.WHITE);
			field.setForeground(fg);
			spinner.setBackground(bg);
			arrows.setBackground(bg);
			wrapper.setBackground(bg);
			return wrapper;
		}
	}

	/** Combo: show value with a small dropdown indicator, auto-sized to widest value. */
	private static final class ComboRenderer implements TableCellRenderer
	{
		private final JComboBox<String> combo;
		private final JPanel wrapper;

		ComboRenderer(String[] values)
		{
			combo = new JComboBox<>(values);
			int width = comboWidthForValues(combo, values);
			Dimension sz = new Dimension(width, ROW_HEIGHT);
			combo.setPreferredSize(sz);
			combo.setMaximumSize(sz);

			wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			wrapper.add(combo);
		}

		@Override
		public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col)
		{
			combo.setSelectedItem(v != null ? v.toString() : "");
			Color bg = rowBg(t, row, sel);
			combo.setBackground(bg);
			combo.setForeground(sel ? t.getSelectionForeground() : t.getForeground());
			wrapper.setBackground(bg);
			return wrapper;
		}
	}

	/** Flags renderer: shows human-readable flag names with a "..." button. */
	private static final class FlagsRenderer implements TableCellRenderer
	{
		private final JPanel     panel;
		private final JTextField field;
		private final JButton    btn;
		private final String[] flagDefs;

		FlagsRenderer(String[] flagDefs)
		{
			this.flagDefs = flagDefs;
			field = new JTextField();
			field.setEditable(false);
			field.setBorder(FIELD_BORDER);
			btn = new JButton("...");
			btn.setPreferredSize(new Dimension(24, ROW_HEIGHT));
			btn.setMargin(new Insets(0, 2, 0, 2));

			panel = new JPanel(new BorderLayout(0, 0));
			panel.add(field, BorderLayout.CENTER);
			panel.add(btn, BorderLayout.EAST);
		}

		@Override
		public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col)
		{
			String flags = v != null ? v.toString() : "";
			field.setText(formatFlags(flags));
			Color bg = rowBg(t, row, sel);
			Color fg = sel ? t.getSelectionForeground() : t.getForeground();
			field.setBackground(sel ? bg : Color.WHITE);
			field.setForeground(fg);
			panel.setBackground(bg);
			return panel;
		}

		private String formatFlags(String flags)
		{
			if (flags == null || flags.isEmpty()) return "(none)";
			StringBuilder sb = new StringBuilder();
			for (char c : flags.toCharArray()) {
				String desc = null;
				for (String fd : flagDefs) {
					if (fd.charAt(0) == c) {
						String[] kv = fd.split("=", 2);
						desc = kv.length > 1 ? kv[1] : String.valueOf(c);
						break;
					}
				}
				if (sb.length() > 0) sb.append(", ");
				sb.append(desc != null ? desc : String.valueOf(c));
			}
			return sb.toString();
		}
	}

	/** Button renderer: text field (non-editable, shows border) with a "..." button on the right. */
	private static final class ButtonRenderer implements TableCellRenderer
	{
		private final JPanel     panel;
		private final JTextField field;
		private final JButton    btn;

		ButtonRenderer(String btnText)
		{
			field = new JTextField();
			field.setEditable(false);
			field.setBorder(FIELD_BORDER);
			btn = new JButton(btnText);
			btn.setPreferredSize(new Dimension(24, ROW_HEIGHT));
			btn.setMargin(new Insets(0, 2, 0, 2));

			panel = new JPanel(new BorderLayout(0, 0));
			panel.add(field, BorderLayout.CENTER);
			panel.add(btn, BorderLayout.EAST);
		}

		@Override
		public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col)
		{
			String text = v != null ? v.toString() : "";
			field.setText(text);
			Color bg = rowBg(t, row, sel);
			Color fg = sel ? t.getSelectionForeground() : t.getForeground();
			field.setBackground(sel ? bg : Color.WHITE);
			field.setForeground(fg);
			panel.setBackground(bg);
			return panel;
		}
	}

	// --- Spinner editor: text field + ▲▼ buttons ---

	private final class SpinnerEditor extends AbstractCellEditor implements TableCellEditor
	{
		private static final long serialVersionUID = 1L;
		private static final int SPINNER_WIDTH = 100;
		private final JPanel     wrapper;
		private final JPanel     spinner;
		private final JTextField field;


		SpinnerEditor(double step)
		{

			field = new JTextField();
			field.setHorizontalAlignment(SwingConstants.CENTER);
			field.setBorder(FIELD_BORDER);

			JButton up   = arrowButton("\u25B2", step);
			JButton down = arrowButton("\u25BC", -step);

			JPanel arrows = new JPanel();
			arrows.setLayout(new BoxLayout(arrows, BoxLayout.Y_AXIS));
			arrows.add(up);
			arrows.add(down);

			spinner = new JPanel(new BorderLayout(0, 0));
			spinner.add(field, BorderLayout.CENTER);
			spinner.add(arrows, BorderLayout.EAST);
			Dimension sz = new Dimension(SPINNER_WIDTH, ROW_HEIGHT);
			spinner.setPreferredSize(sz);
			spinner.setMaximumSize(sz);

			wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			wrapper.add(spinner);
		}

		private JButton arrowButton(String label, double delta)
		{
			JButton b = new JButton(label);
			// Force flat painting — Nimbus button chrome is too tall for micro arrows
			b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
			b.setBorder(BorderFactory.createLineBorder(Color.GRAY));
			b.setBackground(new Color(0xE8E8E8));
			Dimension d = new Dimension(18, ROW_HEIGHT / 2);
			b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d);
			b.setMargin(new Insets(0, 0, 0, 0));
			b.setFont(b.getFont().deriveFont(7f));
			b.setFocusable(false);
			b.addActionListener(_ -> {
				String text = field.getText().trim();
				if (text.isEmpty()) { if (delta <= 0) return; text = "0"; }
				boolean isInt = (Math.abs(delta) >= 1.0) && !text.contains(".");
				try {
					if (isInt) {
						field.setText(String.valueOf(Integer.parseInt(text) + (int) delta));
					} else {
						double val = Double.parseDouble(text) + delta;
						int dec = decimalPlaces(delta);
						field.setText(String.format("%." + dec + "f", val));
					}
					stopCellEditing(); // commit immediately so Apply enables
				} catch (NumberFormatException ignored) {}
			});
			return b;
		}

		@Override public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int row, int col)
		{
			field.setText(v != null ? v.toString() : "");
			return wrapper;
		}

		@Override public Object getCellEditorValue() { return field.getText(); }

		@Override
		public boolean isCellEditable(EventObject e) { return true; }

		private static int decimalPlaces(double step)
		{
			String s = String.valueOf(Math.abs(step));
			int dot = s.indexOf('.');
			return dot < 0 ? 0 : s.length() - dot - 1;
		}
	}

	// --- Combo editor: auto-sized, left-aligned ---

	private final class ComboEditor extends AbstractCellEditor implements TableCellEditor
	{
		private static final long serialVersionUID = 1L;
		private final JComboBox<String> combo;
		private final JPanel wrapper;

		ComboEditor(String[] values)
		{
			combo = new JComboBox<>(values);
			int width = comboWidthForValues(combo, values);
			Dimension sz = new Dimension(width, ROW_HEIGHT);
			combo.setPreferredSize(sz);
			combo.setMaximumSize(sz);
			combo.addActionListener(_ -> stopCellEditing());

			wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			wrapper.add(combo);
		}

		@Override public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int row, int col)
		{
			combo.setSelectedItem(v != null ? v.toString() : "");
			wrapper.setBackground(t.getBackground());
			return wrapper;
		}

		@Override public Object getCellEditorValue() { return combo.getSelectedItem(); }
		@Override public boolean isCellEditable(EventObject e) { return true; }
	}

	// --- Flags editor: button that opens a checkbox popup ---

	private final class FlagsEditor extends AbstractCellEditor implements TableCellEditor
	{
		private static final long serialVersionUID = 1L;
		private final String[] flagDefs; // "X=Description"
		private final JTextField display;
		private final JButton    btn;
		private final JPanel     panel;
		private String currentValue = "";

		FlagsEditor(String[] flagDefs)
		{
			this.flagDefs = flagDefs;
			display = new JTextField();
			display.setEditable(false);
			display.setBorder(FIELD_BORDER);
			btn = new JButton("...");
			btn.setPreferredSize(new Dimension(24, ROW_HEIGHT));
			btn.setMargin(new Insets(0, 2, 0, 2));
			btn.setFocusable(false);
			btn.addActionListener(_ -> showPopup());

			panel = new JPanel(new BorderLayout(0, 0));
			panel.add(display, BorderLayout.CENTER);
			panel.add(btn, BorderLayout.EAST);
		}

		private void showPopup()
		{
			JPanel content = new JPanel();
			content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
			JCheckBox[] boxes = new JCheckBox[flagDefs.length];
			for (int i = 0; i < flagDefs.length; i++) {
				String[] kv = flagDefs[i].split("=", 2);
				char flag = kv[0].charAt(0);
				String desc = kv.length > 1 ? kv[1] : String.valueOf(flag);
				boxes[i] = new JCheckBox(desc);
				boxes[i].setSelected(currentValue.indexOf(flag) >= 0);
				content.add(boxes[i]);
			}

			int result = javax.swing.JOptionPane.showConfirmDialog(
					SwingUtilities.getWindowAncestor(panel),
					content, "Select Options",
					javax.swing.JOptionPane.OK_CANCEL_OPTION,
					javax.swing.JOptionPane.PLAIN_MESSAGE);

			if (result == javax.swing.JOptionPane.OK_OPTION) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < flagDefs.length; i++) {
					if (boxes[i].isSelected()) {
						sb.append(flagDefs[i].charAt(0));
					}
				}
				// Preserve any flags not covered by flagDefs
				for (char c : currentValue.toCharArray()) {
					boolean known = false;
					for (String fd : flagDefs) { if (fd.charAt(0) == c) { known = true; break; } }
					if (!known) sb.append(c);
				}
				currentValue = sb.toString();
				display.setText(formatFlags(currentValue));
				stopCellEditing();
			}
		}

		@Override public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int row, int col)
		{
			currentValue = v != null ? v.toString() : "";
			display.setText(formatFlags(currentValue));
			return panel;
		}

		@Override public Object getCellEditorValue() { return currentValue; }

		@Override public boolean isCellEditable(EventObject e) { return true; }

		private String formatFlags(String flags)
		{
			if (flags == null || flags.isEmpty()) return "(none)";
			StringBuilder sb = new StringBuilder();
			for (char c : flags.toCharArray()) {
				for (String fd : flagDefs) {
					if (fd.charAt(0) == c) {
						if (sb.length() > 0) sb.append(", ");
						String[] kv = fd.split("=", 2);
						sb.append(kv.length > 1 ? kv[1] : String.valueOf(c));
						break;
					}
				}
			}
			return sb.length() > 0 ? sb.toString() : flags;
		}
	}

	// --- File picker editor ---

	private final class FilePickerEditor extends AbstractCellEditor implements TableCellEditor
	{
		private static final long serialVersionUID = 1L;
		private final JTextField field;
		private final JButton    btn;
		private final JPanel     panel;

		FilePickerEditor()
		{
			field = new JTextField();
			field.setBorder(FIELD_BORDER);
			btn = new JButton("...");
			btn.setPreferredSize(new Dimension(24, ROW_HEIGHT));
			btn.setMargin(new Insets(0, 2, 0, 2));
			btn.setFocusable(false);
			btn.addActionListener(_ -> {
				File fontsDir = new File(FontMapper.FONTS_DIR);
				JFileChooser fc = new JFileChooser(fontsDir.exists() ? fontsDir : new File("."));
				fc.setFileFilter(new FileFilter() {
					@Override public boolean accept(File f) {
						return f.isDirectory() || f.getName().toLowerCase().endsWith(".ttf");
					}
					@Override public String getDescription() { return "TrueType Fonts (*.ttf)"; }
				});
				if (fc.showOpenDialog(btn) == JFileChooser.APPROVE_OPTION) {
					field.setText(fc.getSelectedFile().getName());
					stopCellEditing();
				}
			});

			panel = new JPanel(new BorderLayout(0, 0));
			panel.add(field, BorderLayout.CENTER);
			panel.add(btn, BorderLayout.EAST);
		}

		@Override public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int row, int col)
		{
			field.setText(v != null ? v.toString() : "");
			return panel;
		}

		@Override public Object getCellEditorValue() { return field.getText(); }
		@Override public boolean isCellEditable(EventObject e) { return true; }
	}

	// --- Font picker editor ---

	private final class FontPickerEditor extends AbstractCellEditor implements TableCellEditor
	{
		private static final long serialVersionUID = 1L;
		private final JTextField field;
		private final JButton    btn;
		private final JPanel     panel;

		FontPickerEditor()
		{
			field = new JTextField();
			field.setBorder(FIELD_BORDER);
			btn = new JButton("...");
			btn.setPreferredSize(new Dimension(24, ROW_HEIGHT));
			btn.setMargin(new Insets(0, 2, 0, 2));
			btn.setFocusable(false);
			btn.addActionListener(_ -> {
				Window w = SwingUtilities.getWindowAncestor(btn);
				String picked = JDialogFamilyPicker.pick(w, field.getText());
				if (picked != null) {
					field.setText(picked);
					stopCellEditing();
				}
			});

			panel = new JPanel(new BorderLayout(0, 0));
			panel.add(field, BorderLayout.CENTER);
			panel.add(btn, BorderLayout.EAST);
		}

		@Override public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int row, int col)
		{
			field.setText(v != null ? v.toString() : "");
			return panel;
		}

		@Override public Object getCellEditorValue() { return field.getText(); }
		@Override public boolean isCellEditable(EventObject e) { return true; }
	}

	// --- Image picker editor: browse virtual_disk/c0 for image files ---

	private final class ImagePickerEditor extends AbstractCellEditor implements TableCellEditor
	{
		private static final long serialVersionUID = 1L;
		private final JTextField field;
		private final JButton    btn;
		private final JPanel     panel;

		ImagePickerEditor()
		{
			field = new JTextField();
			field.setBorder(FIELD_BORDER);
			btn = new JButton("...");
			btn.setPreferredSize(new Dimension(24, ROW_HEIGHT));
			btn.setMargin(new Insets(0, 2, 0, 2));
			btn.setFocusable(false);
			btn.addActionListener(_ -> {
				File imgDir = new File("virtual_disk/c0");
				if (!imgDir.isDirectory()) imgDir = new File(".");
				JFileChooser fc = new JFileChooser(imgDir);
				fc.setFileFilter(new FileFilter() {
					@Override public boolean accept(File f) {
						if (f.isDirectory()) return true;
						String n = f.getName().toLowerCase();
						return n.endsWith(".pcx") || n.endsWith(".bmp")
								|| n.endsWith(".png") || n.endsWith(".jpg")
								|| n.endsWith(".jpeg") || n.endsWith(".gif");
					}
					@Override public String getDescription() {
						return "Image files (*.pcx, *.bmp, *.png, *.jpg)";
					}
				});
				if (fc.showOpenDialog(btn) == JFileChooser.APPROVE_OPTION) {
					File chosen = fc.getSelectedFile();
					String name = chosen.getName();
					field.setText("/c0/" + name);
					stopCellEditing();
				}
			});

			panel = new JPanel(new BorderLayout(0, 0));
			panel.add(field, BorderLayout.CENTER);
			panel.add(btn, BorderLayout.EAST);
		}

		@Override public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int row, int col)
		{
			field.setText(v != null ? v.toString() : "");
			return panel;
		}

		@Override public Object getCellEditorValue() { return field.getText(); }
		@Override public boolean isCellEditable(EventObject e) { return true; }
	}

	// -----------------------------------------------------------------
	// Utility
	// -----------------------------------------------------------------

	/** Measure the widest value string and return a comfortable combo width. */
	private static int comboWidthForValues(JComboBox<?> combo, String[] values)
	{
		java.awt.FontMetrics fm = combo.getFontMetrics(combo.getFont());
		int maxText = 0;
		for (String v : values) {
			maxText = Math.max(maxText, fm.stringWidth(v));
		}
		// Nimbus arrow button (~20px) + left/right internal padding + border
		return maxText + 50;
	}
}
