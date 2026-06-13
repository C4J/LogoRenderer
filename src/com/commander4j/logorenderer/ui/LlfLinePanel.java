package com.commander4j.logorenderer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JSplitPane;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

import com.commander4j.gui.JButton4j;
import com.commander4j.gui.JLabel4j_std;
import com.commander4j.gui.JMenuItem4j;
import com.commander4j.gui.JTextField4j;
import com.commander4j.sys.Common;

/**
 * Right-hand panel showing the raw LLF source lines in a JList with per-line
 * checkboxes. Only lines whose checkbox is ticked are passed to the parser when
 * the user requests a preview refresh.
 *
 * Features: - 4-digit line number to the left of each checkbox - Single click
 * toggles the checkbox and immediately refreshes the preview - Tooltip on each
 * row shows a human-readable description of the LLF record
 */
public class LlfLinePanel extends JPanel
{

	private static final long serialVersionUID = 1L;

	/** Callback fired when the preview should be refreshed. */
	public interface RefreshListener
	{
		void onRefresh(List<String> enabledLines);
	}

	private final DefaultListModel<LlfLineItem> model = new DefaultListModel<>();

	// Subclass JList so we can provide per-row tooltips via
	// getToolTipText(MouseEvent)
	private final JList<LlfLineItem> list = new JList<LlfLineItem>(model)
	{
		@Override
		public String getToolTipText(MouseEvent e)
		{
			int index = locationToIndex(e.getPoint());
			if (index < 0)
				return null;
			return describeItem(model.getElementAt(index));
		}
	};

	private RefreshListener refreshListener;

	private final PropertyTablePanel sourceTable;
	private final PropertyTablePanel fontTable;
	private final com.commander4j.logorenderer.FontMapper fontMapper;
	private final JButton4j btnApply;
	private final JButton4j btnCancel;
	/** Model index currently bound to the source property table. */
	private int boundSourceIndex = -1;

	/** Tracks the last-committed selection so we can revert on "Cancel". */
	private int prevSelectedIndex = -1;

	/** Flag to skip the listener during a programmatic selection restore. */
	private boolean suppressSelectionListener;

	public LlfLinePanel()
	{
		this(null);
	}

	public LlfLinePanel(com.commander4j.logorenderer.FontMapper fontMapper)
	{
		this.fontMapper = fontMapper;
		this.sourceTable = new PropertyTablePanel("Property");
		this.fontTable   = new PropertyTablePanel("Font Property");

		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(400, 0));

		list.setCellRenderer(new LlfLineRenderer());
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		list.setSelectionBackground(Common.color_listSelectionBackground);
		list.setSelectionForeground(Common.color_listSelectionForeground);
		ToolTipManager.sharedInstance().registerComponent(list);

		list.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!SwingUtilities.isLeftMouseButton(e))
					return;
				int index = list.locationToIndex(e.getPoint());
				if (index < 0)
					return;
				if (isCheckboxHit(e.getPoint(), index))
				{
					LlfLineItem item = model.getElementAt(index);
					item.setEnabled(!item.isEnabled());
					model.set(index, item);
					fireRefresh();
				}
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger())
					showPopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger())
					showPopup(e);
			}
		});

		JScrollPane scroll = new JScrollPane(list);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		// Source list with its own right-side button bar (select/deselect/refresh)
		JPanel sourceArea = new JPanel(new BorderLayout());
		sourceArea.add(scroll, BorderLayout.CENTER);
		sourceArea.add(buildSourceButtonBar(), BorderLayout.EAST);

		// Apply / Cancel buttons
		btnApply  = iconBtn(Common.icon_ok,     36, "Apply changes");
		btnCancel = iconBtn(Common.icon_cancel,  36, "Discard changes");
		btnApply.setEnabled(false);
		btnCancel.setEnabled(false);

		// Wire dirty listeners
		sourceTable.setDirtyListener(this::updateApplyCancel);
		fontTable.setDirtyListener(this::updateApplyCancel);

		list.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting() || suppressSelectionListener)
				return;
			handleSelectionChange();
		});

		// Apply commits both tables; Cancel reverts both
		btnApply.addActionListener(_ -> applyAll());
		btnCancel.addActionListener(_ -> cancelAll());

		// Property tables stacked vertically in a scroll-safe panel
		sourceTable.setVisible(false);
		fontTable.setVisible(false);

		JPanel tables = new JPanel();
		tables.setLayout(new BoxLayout(tables, BoxLayout.Y_AXIS));
		tables.add(sourceTable);
		tables.add(fontTable);

		// Bottom area = vertical button bar on the right, tables in center
		JPanel bottom = new JPanel(new BorderLayout());
		bottom.add(buildPropertyButtonBar(), BorderLayout.EAST);
		bottom.add(tables, BorderLayout.CENTER);

		// Split pane: source list on top, property tables below
		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sourceArea, bottom);
		splitPane.setResizeWeight(0.6); // source list gets 60% of space
		splitPane.setDividerSize(6);
		splitPane.setContinuousLayout(true);

		add(splitPane, BorderLayout.CENTER);
	}

	// -----------------------------------------------------------------------
	// Public API
	// -----------------------------------------------------------------------

	public void setRefreshListener(RefreshListener listener)
	{
		this.refreshListener = listener;
	}

	/** Callback fired after font properties are saved to fonts.xml. */
	private Runnable fontSaveCallback;

	public void setFontSaveCallback(Runnable callback)
	{
		this.fontSaveCallback = callback;
	}

	/** Callback fired when the Font Mappings toolbar button is clicked. */
	private Runnable fontMappingsCallback;

	public void setFontMappingsCallback(Runnable callback)
	{
		this.fontMappingsCallback = callback;
	}

	/** Replace the displayed lines (all enabled by default). */
	public void loadLines(List<String> lines)
	{
		model.clear();
		prevSelectedIndex = -1;
		highlightedIndices = Collections.emptySet();
		for (String line : lines)
		{
			model.addElement(new LlfLineItem(line, true));
		}
	}

	/** Row indices (0-based) currently tinted as the "chain" for a clicked element. */
	private Set<Integer> highlightedIndices = Collections.emptySet();

	/**
	 * Tint the given row indices to show they're all part of the same
	 * element's data chain. Pass an empty set to clear the highlight.
	 * Scrolls the first highlighted row into view.
	 *
	 * <p>If {@code selectIndex} is non-negative, that row is also selected in
	 * the list — firing the normal selection-change handler so the property
	 * grid populates as if the user had clicked the row directly.</p>
	 */
	public void highlightChain(Set<Integer> indices, int selectIndex)
	{
		this.highlightedIndices = (indices == null) ? Collections.emptySet() : new HashSet<>(indices);
		list.repaint();
		if (!this.highlightedIndices.isEmpty())
		{
			int first = Integer.MAX_VALUE;
			for (int i : this.highlightedIndices) if (i < first) first = i;
			if (first != Integer.MAX_VALUE && first < model.size())
			{
				Rectangle cellBounds = list.getCellBounds(first, first);
				if (cellBounds != null) list.scrollRectToVisible(cellBounds);
			}
		}
		if (selectIndex >= 0 && selectIndex < model.size())
		{
			list.setSelectedIndex(selectIndex);
			Rectangle sel = list.getCellBounds(selectIndex, selectIndex);
			if (sel != null) list.scrollRectToVisible(sel);
		}
	}

	/** Overload that only tints, without changing the current selection. */
	public void highlightChain(Set<Integer> indices)
	{
		highlightChain(indices, -1);
	}

	/**
	 * If the currently selected line is a T or S element, returns the font name
	 * embedded in it (the token starting with {@code F}). Returns {@code null}
	 * if nothing is selected, the selected line is not a text element, or no
	 * font token is present.
	 */
	public String getSelectedFontName()
	{
		LlfLineItem item = list.getSelectedValue();
		if (item == null)
			return null;
		String[] parts = item.getLine().trim().split(",");
		if (parts.length < 2)
			return null;
		String type = parts[0].trim().toUpperCase();
		if (!type.equals("T") && !type.equals("S"))
			return null;
		for (int i = 1; i < parts.length; i++)
		{
			String p = parts[i].trim();
			if (p.length() > 1 && p.charAt(0) == 'F' && Character.isLetter(p.charAt(1)))
				return p.substring(1);
		}
		return null;
	}

	/** Returns every line currently in the list (for saving). */
	public List<String> getAllLines()
	{
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < model.size(); i++)
		{
			lines.add(model.getElementAt(i).getLine());
		}
		return lines;
	}

	// -----------------------------------------------------------------------
	// Internal helpers
	// -----------------------------------------------------------------------

	/**
	 * Called when the list selection changes. If any property table has unsaved
	 * edits, prompt the user before rebinding.
	 */
	private void handleSelectionChange()
	{
		boolean srcDirty  = sourceTable.hasPendingChanges();
		boolean fontDirty = fontTable.hasPendingChanges();

		if (srcDirty || fontDirty) {
			Object[] options = { "Apply", "Discard", "Cancel" };
			int choice = javax.swing.JOptionPane.showOptionDialog(this,
					"You have unsaved property changes.\n"
					+ "Apply them, discard them, or stay on the current row?",
					"Unsaved changes",
					javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
					javax.swing.JOptionPane.QUESTION_MESSAGE,
					null, options, options[0]);

			if (choice == 2 || choice == javax.swing.JOptionPane.CLOSED_OPTION) {
				suppressSelectionListener = true;
				try {
					if (prevSelectedIndex >= 0 && prevSelectedIndex < model.size())
						list.setSelectedIndex(prevSelectedIndex);
					else
						list.clearSelection();
				} finally {
					suppressSelectionListener = false;
				}
				return;
			}
			if (choice == 0) applyAll();
			else             cancelAll();
		}

		// Determine selection — hide tables if 0 or >1 rows selected
		int[] sel = list.getSelectedIndices();
		if (sel.length != 1) {
			sourceTable.unbind();
			fontTable.unbind();
			boundSourceIndex = -1;
			prevSelectedIndex = -1;
			updateApplyCancel();
			return;
		}

		int selIdx = sel[0];
		String line = model.getElementAt(selIdx).getLine();
		String cmd = lineCommand(line);
		java.util.List<PropertyDef> defs = LinePropertyDefs.forLineType(cmd);

		// Bind source property table
		if (!defs.isEmpty()) {
			sourceTable.bind(line.split(",", -1), defs);
			boundSourceIndex = selIdx;
		} else {
			sourceTable.unbind();
			boundSourceIndex = -1;
		}

		// Bind font table for T/S lines
		if (fontMapper != null && ("T".equals(cmd) || "S".equals(cmd))) {
			String fontName = getSelectedFontName();
			bindFontTable(fontName);
		} else {
			fontTable.unbind();
		}

		prevSelectedIndex = selIdx;
		updateApplyCancel();
	}

	/** Bind the font property table from fonts.xml data. */
	private void bindFontTable(String fontName)
	{
		if (fontName == null || fontName.isBlank()) {
			fontTable.unbind();
			return;
		}
		// Find the row in fonts.xml
		String[] fontRow = null;
		for (String[] row : fontMapper.getAllEntries()) {
			if (row.length > 0 && fontName.equals(row[0])) {
				fontRow = row;
				break;
			}
		}
		if (fontRow == null) {
			fontTable.unbind();
			return;
		}
		fontTable.bind(fontRow, FontPropertyDefs.defs());
	}

	/** Extract the command keyword from a source line. */
	private static String lineCommand(String line)
	{
		if (line == null || line.isBlank()) return null;
		int comma = line.indexOf(',');
		return (comma > 0 ? line.substring(0, comma) : line).trim().toUpperCase();
	}

	/** Apply both tables. */
	private void applyAll()
	{
		if (sourceTable.hasPendingChanges()) {
			String newLine = sourceTable.getModifiedLine();
			if (newLine != null && boundSourceIndex >= 0 && boundSourceIndex < model.size()) {
				LlfLineItem item = model.getElementAt(boundSourceIndex);
				model.set(boundSourceIndex, new LlfLineItem(newLine, item.isEnabled()));
				// Re-bind so the table has the committed parts
				sourceTable.resetBaseline();
				fireRefresh();
			}
		}
		if (fontTable.hasPendingChanges() && fontMapper != null) {
			String[] modifiedRow = fontTable.isBound() ? getModifiedFontRow() : null;
			if (modifiedRow != null) {
				java.util.List<String[]> rows = fontMapper.getAllEntries();
				for (int i = 0; i < rows.size(); i++) {
					if (rows.get(i).length > 0 && modifiedRow[0].equals(rows.get(i)[0])) {
						// Preserve description (index 10)
						if (rows.get(i).length > 10 && modifiedRow.length > 10)
							modifiedRow[10] = rows.get(i)[10];
						rows.set(i, modifiedRow);
						break;
					}
				}
				fontMapper.saveToXml(rows);
				fontMapper.reload();
				fontTable.resetBaseline();
				if (fontSaveCallback != null) fontSaveCallback.run();
			}
		}
		updateApplyCancel();
	}

	/** Cancel both tables. */
	private void cancelAll()
	{
		sourceTable.discardPending();
		fontTable.discardPending();
		updateApplyCancel();
	}

	/** Reconstruct the font row from the font table's current parts. */
	private String[] getModifiedFontRow()
	{
		String line = fontTable.getModifiedLine();
		if (line == null) return null;
		return line.split(",", -1);
	}

	private void updateApplyCancel()
	{
		boolean dirty = sourceTable.hasPendingChanges() || fontTable.hasPendingChanges();
		btnApply.setEnabled(dirty);
		btnCancel.setEnabled(dirty);
	}

	private JPanel buildSourceButtonBar()
	{
		JPanel bar = new JPanel();
		bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
		bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JButton4j selectAll    = new JButton4j(Common.icon_select);
		JButton4j deselectAll  = new JButton4j(Common.icon_deselect);
		JButton4j refresh      = new JButton4j(Common.icon_reload);
		JButton4j insertBefore = new JButton4j(Common.icon_insert_before);
		JButton4j insertAfter  = new JButton4j(Common.icon_insert_after);
		JButton4j edit         = new JButton4j(Common.icon_edit);
		JButton4j delete       = new JButton4j(Common.icon_delete);
		JButton4j fontMappings = new JButton4j(Common.icon_font);

		JButton4j[] buttons = { selectAll, deselectAll, refresh,
				insertBefore, insertAfter, edit, delete, fontMappings };

		Dimension btnDim = new Dimension(36, 36);
		for (JButton4j b : buttons)
		{
			b.setPreferredSize(btnDim);
			b.setMinimumSize(btnDim);
			b.setMaximumSize(btnDim);
			b.setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		selectAll.setToolTipText("Enable all lines");
		deselectAll.setToolTipText("Disable all lines");
		refresh.setToolTipText("Refresh preview");
		insertBefore.setToolTipText("Insert row before selection");
		insertAfter.setToolTipText("Insert row after selection");
		edit.setToolTipText("Edit selected row");
		delete.setToolTipText("Delete selected rows");
		fontMappings.setToolTipText("Font Mappings...");

		selectAll.addActionListener(_ -> setAllEnabled(true));
		deselectAll.addActionListener(_ -> setAllEnabled(false));
		refresh.addActionListener(_ -> fireRefresh());
		insertBefore.addActionListener(_ -> {
			int[] sel = list.getSelectedIndices();
			if (sel.length == 0) return;
			addRow(sel[0], false);
		});
		insertAfter.addActionListener(_ -> {
			int[] sel = list.getSelectedIndices();
			if (sel.length == 0) return;
			addRow(sel[sel.length - 1], true);
		});
		edit.addActionListener(_ -> {
			int[] sel = list.getSelectedIndices();
			if (sel.length != 1) return;
			editRow(sel[0]);
		});
		delete.addActionListener(_ -> {
			int[] sel = list.getSelectedIndices();
			if (sel.length == 0) return;
			deleteSelected(sel);
		});
		fontMappings.addActionListener(_ -> {
			if (fontMappingsCallback != null) fontMappingsCallback.run();
		});

		// Enable Insert/Delete on any selection; Edit only on single-row selection
		insertBefore.setEnabled(false);
		insertAfter.setEnabled(false);
		edit.setEnabled(false);
		delete.setEnabled(false);
		list.addListSelectionListener(ev -> {
			if (ev.getValueIsAdjusting()) return;
			int count = list.getSelectedIndices().length;
			insertBefore.setEnabled(count > 0);
			insertAfter.setEnabled(count > 0);
			edit.setEnabled(count == 1);
			delete.setEnabled(count > 0);
		});

		for (int i = 0; i < buttons.length; i++)
		{
			if (i > 0) bar.add(javax.swing.Box.createVerticalStrut(4));
			bar.add(buttons[i]);
		}
		return bar;
	}

	private JPanel buildPropertyButtonBar()
	{
		JPanel bar = new JPanel();
		bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
		bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		Dimension btnDim = new Dimension(36, 36);
		for (JButton4j b : new JButton4j[] { btnApply, btnCancel })
		{
			b.setPreferredSize(btnDim);
			b.setMinimumSize(btnDim);
			b.setMaximumSize(btnDim);
			b.setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		bar.add(btnApply);
		bar.add(javax.swing.Box.createVerticalStrut(4));
		bar.add(btnCancel);
		return bar;
	}

	private void setAllEnabled(boolean enabled)
	{
		for (int i = 0; i < model.size(); i++)
		{
			LlfLineItem item = model.getElementAt(i);
			item.setEnabled(enabled);
			model.set(i, item);
		}
		fireRefresh();
	}

	private void fireRefresh()
	{
		if (refreshListener == null)
			return;
		List<String> enabled = new ArrayList<>();
		for (int i = 0; i < model.size(); i++)
		{
			LlfLineItem item = model.getElementAt(i);
			if (item.isEnabled())
				enabled.add(item.getLine());
		}
		refreshListener.onRefresh(enabled);
	}

	/**
	 * Returns true if the given click point landed on the JCheckBox widget (the
	 * small tick-box itself) within the renderer for the given row index.
	 */
	private boolean isCheckboxHit(Point clickPoint, int index)
	{
		Rectangle cellBounds = list.getCellBounds(index, index);
		if (cellBounds == null)
			return false;
		Component renderer = list.getCellRenderer().getListCellRendererComponent(list, model.getElementAt(index), index, false, false);
		renderer.setSize(cellBounds.width, cellBounds.height);
		renderer.validate(); // recursively lays out nested panels
		Point rel = new Point(clickPoint.x - cellBounds.x, clickPoint.y - cellBounds.y);
		return SwingUtilities.getDeepestComponentAt(renderer, rel.x, rel.y) instanceof JCheckBox;
	}

	/** Shows the right-click context menu. */
	private void showPopup(MouseEvent e)
	{
		int clickedIndex = list.locationToIndex(e.getPoint());
		if (clickedIndex < 0)
			return;

		// If the right-clicked row is outside the current selection, replace
		// it.
		if (!list.isSelectedIndex(clickedIndex))
		{
			list.setSelectedIndex(clickedIndex);
		}

		int[] selected = list.getSelectedIndices();
		if (selected.length == 0)
			return;
		int first = selected[0];
		int last = selected[selected.length - 1];

		JMenuItem4j enable = new JMenuItem4j("Enable");
		JMenuItem4j disable = new JMenuItem4j("Disable");
		JMenuItem4j delete = new JMenuItem4j("Delete");
		JMenuItem4j addBefore = new JMenuItem4j("Add Row Before");
		JMenuItem4j addAfter = new JMenuItem4j("Add Row After");
		JMenuItem4j enableAll = new JMenuItem4j("Enable All");
		JMenuItem4j disableAll = new JMenuItem4j("Disable All");

		enable.addActionListener(_ -> setSelectedEnabled(selected, true));
		disable.addActionListener(_ -> setSelectedEnabled(selected, false));
		delete.addActionListener(_ -> deleteSelected(selected));
		addBefore.addActionListener(_ -> addRow(first, false));
		addAfter.addActionListener(_ -> addRow(last, true));
		enableAll.addActionListener(_ -> setAllEnabled(true));
		disableAll.addActionListener(_ -> setAllEnabled(false));

		JPopupMenu popup = new JPopupMenu();
		popup.add(enable);
		popup.add(disable);
		popup.addSeparator();
		popup.add(delete);
		if (selected.length == 1)
		{
			JMenuItem4j edit = new JMenuItem4j("Edit");
			edit.addActionListener(_ -> editRow(first));
			popup.add(edit);
		}
		popup.addSeparator();
		popup.add(addBefore);
		popup.add(addAfter);
		popup.addSeparator();
		popup.add(enableAll);
		popup.add(disableAll);
		popup.show(e.getComponent(), e.getX(), e.getY());
	}

	private void setSelectedEnabled(int[] indices, boolean enabled)
	{
		for (int i : indices)
		{
			LlfLineItem item = model.getElementAt(i);
			item.setEnabled(enabled);
			model.set(i, item);
		}
		fireRefresh();
	}

	private void deleteSelected(int[] indices)
	{
		if (indices.length == 0) return;
		String msg = indices.length == 1
				? "Delete selected row?"
				: "Delete " + indices.length + " selected rows?";
		int choice = javax.swing.JOptionPane.showConfirmDialog(this,
				msg,
				"Confirm Delete",
				javax.swing.JOptionPane.OK_CANCEL_OPTION,
				javax.swing.JOptionPane.WARNING_MESSAGE);
		if (choice != javax.swing.JOptionPane.OK_OPTION) return;

		// Remove in reverse order so earlier indices stay valid.
		for (int i = indices.length - 1; i >= 0; i--)
		{
			model.remove(indices[i]);
		}
		fireRefresh();
	}

	private void editRow(int index)
	{
		LlfLineItem item = model.getElementAt(index);
		String result = showLineEditDialog("Edit Row", item.getLine());
		if (result != null)
		{
			model.set(index, new LlfLineItem(result, item.isEnabled()));
			list.setSelectedIndex(index);
			fireRefresh();
		}
	}

	private void addRow(int referenceIndex, boolean after)
	{
		String result = showLineEditDialog("Add Row", "");
		if (result != null)
		{
			int insertAt = after ? referenceIndex + 1 : referenceIndex;
			model.add(insertAt, new LlfLineItem(result, true));
			list.setSelectedIndex(insertAt);
			list.ensureIndexIsVisible(insertAt);
			fireRefresh();
		}
	}

	/**
	 * Shows a modal dialog with a wide text field for editing a single LLF
	 * line. Returns the edited text, or null if the user cancelled.
	 */
	private String showLineEditDialog(String title, String initialValue)
	{
		Window owner = SwingUtilities.getWindowAncestor(this);
		JDialog dialog = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);

		JTextField4j field = new JTextField4j(initialValue);
		field.setColumns(80);
		field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		// Map Ctrl+C/X/V/A to the standard text actions so they work on all
		// platforms (on macOS the Swing default binds Cmd+C/X/V/A, not Ctrl).
		field.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_DOWN_MASK), "copy");
		field.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.CTRL_DOWN_MASK), "cut");
		field.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, java.awt.event.InputEvent.CTRL_DOWN_MASK), "paste");
		field.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, java.awt.event.InputEvent.CTRL_DOWN_MASK), "select-all");

		JButton4j ok = new JButton4j("OK");
		JButton4j cancel = new JButton4j("Cancel");

		boolean[] accepted =
		{ false };
		ok.addActionListener(_ -> {
			accepted[0] = true;
			dialog.dispose();
		});
		cancel.addActionListener(_ -> dialog.dispose());

		// pressing Enter in the field confirms
		field.addActionListener(_ -> {
			accepted[0] = true;
			dialog.dispose();
		});

		// pressing Escape cancels
		dialog.getRootPane().registerKeyboardAction(_ -> dialog.dispose(), KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		dialog.getRootPane().setDefaultButton(ok);

		// Ensure the text field has focus (not the OK button) when the dialog
		// opens,
		// so Ctrl+C / Ctrl+V work immediately without requiring a mouse click.
		dialog.addWindowListener(new java.awt.event.WindowAdapter()
		{
			@Override
			public void windowOpened(java.awt.event.WindowEvent e)
			{
				field.requestFocusInWindow();
				field.selectAll();
			}
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.add(ok);
		buttons.add(cancel);

		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		content.add(new JLabel4j_std("Line:"), BorderLayout.NORTH);
		content.add(field, BorderLayout.CENTER);
		content.add(buttons, BorderLayout.SOUTH);

		dialog.setContentPane(content);
		dialog.pack();
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);

		return accepted[0] ? field.getText() : null;
	}

	// -----------------------------------------------------------------------
	// Tooltip: human-readable description of a single LLF line
	// -----------------------------------------------------------------------

	private static String describeItem(LlfLineItem item)
	{
		String line = item.getLine().trim();
		if (line.isEmpty())
			return "Empty line";

		String[] p = line.split(",", -1);
		String type = p[0].trim().toUpperCase();

		return switch (type)
		{
		case "NOTE" -> "Metadata/comment: " + (p.length > 1 ? p[1].trim() : "");
		case "LAY" -> "Layout bounds — printable area: x=" + get(p, 2) + " y=" + get(p, 3) + " w=" + get(p, 4) + " h=" + get(p, 5);
		case "FONT" -> "Font declaration — logical name: " + get(p, 1);
		case "SPD" -> "Physical font alias: " + get(p, 1);
		case "IMG" -> "Image path declaration: " + get(p, 1);
		case "T" -> "Text element #" + get(p, 1) + " at (" + get(p, 2) + "," + get(p, 3) + ") dots";
		case "S" -> "Scalable text element #" + get(p, 1) + " at (" + get(p, 2) + "," + get(p, 3) + ") mm";
		case "L" -> "Image/logo element #" + get(p, 1) + " at (" + get(p, 2) + "," + get(p, 3) + ") dots";
		case "G" -> "Graphic element #" + get(p, 1) + " — " + get(p, 6) + " from (" + get(p, 2) + "," + get(p, 3) + ") to (" + get(p, 4) + "," + get(p, 5) + ") dots";
		case "C" -> "Barcode element #" + get(p, 1) + " at (" + get(p, 2) + "," + get(p, 3) + ") dots" + (p.length > 6 ? " type=" + get(p, 6).replaceFirst("^[Cc]", "") : "");
		case "FD", "FF" -> "Field data: field #" + get(p, 1) + " = \"" + get(p, 2) + "\"";
		case "UD", "UF" -> "User data: field #" + get(p, 1) + " = \"" + get(p, 2) + "\"";
		case "VAR", "UVAR" -> "Variable formula #" + get(p, 1) + " = " + get(p, 2);
		case "FL" -> "Field link: element #" + get(p, 2) + " ← data source #" + get(p, 1);
		case "QUE" -> "Field prompt label for field #" + get(p, 1) + ": \"" + get(p, 2) + "\"";
		case "ROTL" -> "Rotation label";
		default -> type + " record";
		};
	}

	/** Safe indexed access into a split array. */
	private static String get(String[] parts, int index)
	{
		return (index < parts.length) ? parts[index].trim() : "";
	}

	private static JButton4j iconBtn(javax.swing.Icon icon, int size, String tooltip)
	{
		JButton4j b = new JButton4j(icon);
		b.setPreferredSize(new Dimension(size, size));
		b.setToolTipText(tooltip);
		b.setFocusable(false);
		return b;
	}

	// -----------------------------------------------------------------------
	// Cell renderer — 4-digit line number + checkbox + monospaced text
	// -----------------------------------------------------------------------

	private final class LlfLineRenderer implements ListCellRenderer<LlfLineItem>
	{

		private static final Color ALT_BG = new Color(214, 217, 223);
		private static final Color HIGHLIGHT_BG     = new Color(255, 243, 176);
		private static final Color HIGHLIGHT_ALT_BG = new Color(240, 222, 150);
		private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 11);

		private final JPanel panel = new JPanel(new BorderLayout());
		private final JLabel lineNum = new JLabel();
		private final JCheckBox check = new JCheckBox(); // icon only — no text
		private final JLabel lineText = new JLabel();
		private final JPanel centerPanel = new JPanel(new BorderLayout());

		LlfLineRenderer()
		{
			lineNum.setFont(MONO);
			lineNum.setOpaque(false);
			lineNum.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

			check.setOpaque(false); // text stays empty so the widget is
									// icon-width only

			lineText.setFont(MONO);
			lineText.setOpaque(false);

			centerPanel.setOpaque(false);
			centerPanel.add(check, BorderLayout.WEST);
			centerPanel.add(lineText, BorderLayout.CENTER);

			panel.setOpaque(true);
			panel.add(lineNum, BorderLayout.WEST);
			panel.add(centerPanel, BorderLayout.CENTER);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends LlfLineItem> list, LlfLineItem value, int index, boolean isSelected, boolean cellHasFocus)
		{

			lineNum.setText(String.format("%04d", index + 1));
			lineText.setText(value.getLine());
			check.setSelected(value.isEnabled());

			boolean highlighted = highlightedIndices.contains(index);
			Color defaultBg = (index % 2 == 0 ? Color.WHITE : ALT_BG);
			Color chainBg   = (index % 2 == 0 ? HIGHLIGHT_BG : HIGHLIGHT_ALT_BG);
			Color bg = isSelected ? list.getSelectionBackground() : (highlighted ? chainBg : defaultBg);
			Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();

			panel.setBackground(bg);
			centerPanel.setBackground(bg);
			lineNum.setForeground(isSelected ? fg : Color.GRAY);
			lineText.setForeground(fg);

			return panel;
		}
	}
}
