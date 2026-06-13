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
import java.util.Set;
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
 * Inline editor for L (logo/image), G (graphic), and C (barcode) element
 * properties. Switches layout depending on the element type.
 */
public final class ElementPropertiesPanel extends JPanel
{

	private static final long serialVersionUID = 1L;
	private static final int ROW_H = 26;
	static final Set<String> SUPPORTED = Set.of("L", "G", "C");

	// --- shared widgets ---
	private final JTextField4j fldId;
	private final JTextField4j fldX;
	private final JTextField4j fldY;

	// --- L (image) widgets ---
	private final JTextField4j fldImageName;
	private final JTextField4j fldLRotation;
	private final JTextField4j fldLZoom;

	// --- G (graphic) widgets ---
	private final JTextField4j fldX2;
	private final JTextField4j fldY2;
	private final JTextField4j fldShape;
	private final JTextField4j fldPenW;
	private final JTextField4j fldPenH;
	private final JTextField4j fldRender;

	// --- C (barcode) widgets ---
	private final JTextField4j fldHeight;
	private final JTextField4j fldCodeType;
	private final JTextField4j fldCZoom;
	private final JTextField4j fldBarWidth;

	// --- alignment radios (L and C have orientation flags) ---
	private final JRadioButton radHLeft;
	private final JRadioButton radHCentre;
	private final JRadioButton radHRight;
	private final JRadioButton radVTop;
	private final JRadioButton radVMiddle;
	private final JRadioButton radVBottom;
	private final JPanel       hAlignPanel;
	private final JPanel       vAlignPanel;
	private final JLabel       lblHAlign;
	private final JLabel       lblVAlign;

	// --- flag checkboxes ---
	private final JCheckBox chkU;
	private final JCheckBox chkD;
	private final JCheckBox chkB;
	private final JCheckBox chkE;
	private final JCheckBox chkP;
	private final JPanel    flagPanel;
	private final JLabel    lblFlags;

	// Spinner wrappers (needed for visibility toggling)
	private final JPanel spnX;
	private final JPanel spnY;

	// L row components
	private final JLabel lblImageName;
	private final JLabel lblLRotation;
	private final JPanel spnLRotation;
	private final JLabel lblLZoom;
	private final JPanel spnLZoom;

	// G row components
	private final JLabel lblX2;
	private final JPanel spnX2;
	private final JLabel lblY2;
	private final JPanel spnY2;
	private final JLabel lblShape;
	private final JLabel lblPenW;
	private final JPanel spnPenW;
	private final JLabel lblPenH;
	private final JPanel spnPenH;
	private final JLabel lblRender;
	private final JPanel spnRender;

	// C row components
	private final JLabel lblHeight;
	private final JPanel spnHeight;
	private final JLabel lblCodeType;
	private final JLabel lblCZoom;
	private final JPanel spnCZoom;
	private final JLabel lblBarWidth;
	private final JPanel spnBarWidth;

	// --- state ---
	private Runnable dirtyListener;
	private BiConsumer<Integer, String> lineCallback;
	private int      boundIndex = -1;
	private String[] originalParts;
	private char     boundType; // 'L', 'G', or 'C'
	private String[] initialValues;
	private boolean  loading;
	private final List<Component> editableWidgets = new ArrayList<>();

	public ElementPropertiesPanel()
	{
		setLayout(new BorderLayout());

		// Shared
		fldId = readOnlyField(50);
		fldX  = text(60);  fldX.setHorizontalAlignment(SwingConstants.CENTER);
		fldY  = text(60);  fldY.setHorizontalAlignment(SwingConstants.CENTER);
		spnX  = spinnerWrap(fldX, 1);
		spnY  = spinnerWrap(fldY, 1);

		// L widgets
		fldImageName = readOnlyField(140);
		fldLRotation = text(35);  fldLRotation.setHorizontalAlignment(SwingConstants.CENTER);
		fldLZoom     = text(35);  fldLZoom.setHorizontalAlignment(SwingConstants.CENTER);
		spnLRotation = spinnerWrap(fldLRotation, 2);
		spnLZoom     = spinnerWrap(fldLZoom, 1);

		// G widgets
		fldX2     = text(60);  fldX2.setHorizontalAlignment(SwingConstants.CENTER);
		fldY2     = text(60);  fldY2.setHorizontalAlignment(SwingConstants.CENTER);
		fldShape  = readOnlyField(60);
		fldPenW   = text(35);  fldPenW.setHorizontalAlignment(SwingConstants.CENTER);
		fldPenH   = text(35);  fldPenH.setHorizontalAlignment(SwingConstants.CENTER);
		fldRender = text(35);  fldRender.setHorizontalAlignment(SwingConstants.CENTER);
		spnX2     = spinnerWrap(fldX2, 1);
		spnY2     = spinnerWrap(fldY2, 1);
		spnPenW   = spinnerWrap(fldPenW, 1);
		spnPenH   = spinnerWrap(fldPenH, 1);
		spnRender = spinnerWrap(fldRender, 1);

		// C widgets
		fldHeight   = text(60);  fldHeight.setHorizontalAlignment(SwingConstants.CENTER);
		fldCodeType = readOnlyField(80);
		fldCZoom    = text(35);  fldCZoom.setHorizontalAlignment(SwingConstants.CENTER);
		fldBarWidth = text(35);  fldBarWidth.setHorizontalAlignment(SwingConstants.CENTER);
		spnHeight   = spinnerWrap(fldHeight, 1);
		spnCZoom    = spinnerWrap(fldCZoom, 1);
		spnBarWidth = spinnerWrap(fldBarWidth, 1);

		// Alignment radios (shared by L and C)
		radHLeft   = new JRadioButton("Left");
		radHCentre = new JRadioButton("Centre");
		radHRight  = new JRadioButton("Right");
		ButtonGroup hGroup = new ButtonGroup();
		hGroup.add(radHLeft); hGroup.add(radHCentre); hGroup.add(radHRight);
		radHLeft.setSelected(true);

		radVTop    = new JRadioButton("Top");
		radVMiddle = new JRadioButton("Middle");
		radVBottom = new JRadioButton("Bottom");
		ButtonGroup vGroup = new ButtonGroup();
		vGroup.add(radVTop); vGroup.add(radVMiddle); vGroup.add(radVBottom);
		radVTop.setSelected(true);

		hAlignPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		hAlignPanel.add(radHLeft); hAlignPanel.add(radHCentre); hAlignPanel.add(radHRight);
		vAlignPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		vAlignPanel.add(radVTop); vAlignPanel.add(radVMiddle); vAlignPanel.add(radVBottom);

		// Flag checkboxes
		chkU = new JCheckBox("Upside Down");  chkU.setToolTipText("U flag");
		chkD = new JCheckBox("Disabled");      chkD.setToolTipText("D flag");
		chkB = new JCheckBox("Borders");       chkB.setToolTipText("B flag");
		chkE = new JCheckBox("Redraw");        chkE.setToolTipText("E flag");
		chkP = new JCheckBox("Protected");     chkP.setToolTipText("P flag");
		flagPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		flagPanel.add(chkD); flagPanel.add(chkU); flagPanel.add(chkB);
		flagPanel.add(chkE); flagPanel.add(chkP);

		// Track editable widgets
		editableWidgets.addAll(List.of(
				fldX, fldY, fldLRotation, fldLZoom,
				fldX2, fldY2, fldPenW, fldPenH, fldRender,
				fldHeight, fldCZoom, fldBarWidth,
				radHLeft, radHCentre, radHRight,
				radVTop, radVMiddle, radVBottom,
				chkU, chkD, chkB, chkE, chkP));

		// Dirty tracking
		DocumentListener dirty = new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent e)  { checkDirty(); }
			@Override public void removeUpdate(DocumentEvent e)  { checkDirty(); }
			@Override public void changedUpdate(DocumentEvent e) { checkDirty(); }
		};
		for (Component c : editableWidgets) {
			if (c instanceof JTextField4j tf) tf.getDocument().addDocumentListener(dirty);
			else if (c instanceof JRadioButton rb) rb.addActionListener(_ -> checkDirty());
			else if (c instanceof JCheckBox cb) cb.addActionListener(_ -> checkDirty());
		}

		// --- Grid layout ---
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setBorder(BorderFactory.createEmptyBorder(2, 4, 10, 4));

		// Row 0: Element ID + type-specific read-only field
		addRow(grid, 0, "Element", fldId, "Image", fldImageName);
		lblImageName = labelAt(grid, 0, 2);
		// Overlay code-type for C in same slot
		lblCodeType = smallLabel("Code Type");
		addOverlay(grid, 0, 2, lblCodeType, 3, fldCodeType);
		// Overlay shape for G in same slot
		lblShape = smallLabel("Shape");
		addOverlay(grid, 0, 2, lblShape, 3, fldShape);

		// Row 1: X Position, Y Position
		addRow(grid, 1, "X Position", spnX, "Y Position", spnY);

		// Row 2: type-specific row
		// L: Rotation + Zoom
		lblLRotation = addRow(grid, 2, "Rotation", spnLRotation, "Zoom", spnLZoom);
		lblLZoom = labelAt(grid, 2, 2);
		// G: X2 + Y2  (overlaid)
		lblX2 = smallLabel("X2");
		lblY2 = smallLabel("Y2");
		addOverlay(grid, 2, 0, lblX2, 1, spnX2);
		addOverlay(grid, 2, 2, lblY2, 3, spnY2);
		// C: Height + Zoom (overlaid)
		lblHeight = smallLabel("Height");
		lblCZoom  = smallLabel("Zoom");
		addOverlay(grid, 2, 0, lblHeight, 1, spnHeight);
		addOverlay(grid, 2, 2, lblCZoom, 3, spnCZoom);

		// Row 3: type-specific row
		// G: Pen Width + Pen Height
		lblPenW = addRow(grid, 3, "Pen Width", spnPenW, "Pen Height", spnPenH);
		lblPenH = labelAt(grid, 3, 2);
		// C: Bar Width (overlaid)
		lblBarWidth = smallLabel("Bar Width");
		addOverlay(grid, 3, 0, lblBarWidth, 1, spnBarWidth);
		// G: Render option (overlaid in right slot)
		lblRender = smallLabel("Render");
		addOverlay(grid, 3, 2, lblRender, 3, spnRender);

		// Row 4: Horizontal alignment (L and C)
		lblHAlign = smallLabel("Horizontal");
		addWideRow(grid, 4, lblHAlign, hAlignPanel);

		// Row 5: Vertical alignment (L and C)
		lblVAlign = smallLabel("Vertical");
		addWideRow(grid, 5, lblVAlign, vAlignPanel);

		// Row 6: Flags
		lblFlags = smallLabel("Options");
		addWideRow(grid, 6, lblFlags, flagPanel);

		add(grid, BorderLayout.CENTER);

		// Start disabled, hide type-specific fields
		for (Component c : editableWidgets) c.setEnabled(false);
		hideAllTypeFields();
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
		try { restoreFromInitial(); }
		finally { loading = false; }
		if (dirtyListener != null) dirtyListener.run();
	}

	public int getBoundIndex() { return boundIndex; }

	public void bindLine(int modelIndex, String line)
	{
		if (line == null || line.isBlank()) { unbind(); return; }
		String[] parts = line.split(",", -1);
		String cmd = parts[0].trim().toUpperCase();
		if (!SUPPORTED.contains(cmd)) { unbind(); return; }

		loading = true;
		try {
			boundIndex = modelIndex;
			boundType = cmd.charAt(0);
			originalParts = parts.clone();
			populateFromParts(parts);
			configureForType();
		} finally {
			loading = false;
		}
		initialValues = getCurrentValues();
		for (Component c : editableWidgets) c.setEnabled(true);
		disableIrrelevantWidgets();
		if (dirtyListener != null) dirtyListener.run();
	}

	public void unbind()
	{
		boundIndex = -1;
		originalParts = null;
		initialValues = null;
		loading = true;
		try { clearAllFields(); }
		finally { loading = false; }
		for (Component c : editableWidgets) c.setEnabled(false);
		if (dirtyListener != null) dirtyListener.run();
	}

	// ---------------------------------------------------------------------
	// Parse / populate
	// ---------------------------------------------------------------------

	private void populateFromParts(String[] parts)
	{
		fldId.setText(parts.length > 1 ? parts[1].trim() : "");
		fldX.setText(parts.length > 2 ? parts[2].trim() : "");
		fldY.setText(parts.length > 3 ? parts[3].trim() : "");

		// Reset all type-specific fields
		clearTypeFields();

		String options = "";

		switch (boundType) {
		case 'L' -> {
			for (int i = 4; i < parts.length; i++) {
				String p = parts[i].trim();
				if (p.startsWith("L"))      fldImageName.setText(p.substring(1));
				else if (p.startsWith("O")) options = p.substring(1);
				else if (p.startsWith("R")) fldLRotation.setText(p.substring(1));
				else if (p.startsWith("Z")) fldLZoom.setText(p.substring(1).split(",")[0]);
			}
		}
		case 'G' -> {
			fldX2.setText(parts.length > 4 ? parts[4].trim() : "");
			fldY2.setText(parts.length > 5 ? parts[5].trim() : "");
			fldShape.setText(parts.length > 6 ? parts[6].trim() : "");
			for (int i = 7; i < parts.length; i++) {
				String p = parts[i].trim();
				if (p.startsWith("O"))      options = p.substring(1);
				else if (p.startsWith("L")) {
					fldPenW.setText(p.substring(1));
					if (i + 1 < parts.length) fldPenH.setText(parts[++i].trim());
				}
				else if (p.startsWith("R")) fldRender.setText(p.substring(1));
			}
		}
		case 'C' -> {
			// C,id,x,y,0,height,Ccodetype,...
			fldHeight.setText(parts.length > 5 ? parts[5].trim() : "");
			for (int i = 6; i < parts.length; i++) {
				String p = parts[i].trim();
				if (p.startsWith("C"))      fldCodeType.setText(p.substring(1));
				else if (p.startsWith("O")) options = p.substring(1);
				else if (p.startsWith("Z")) fldCZoom.setText(p.substring(1).split(",")[0]);
				else if (p.startsWith("W")) fldBarWidth.setText(p.substring(1).split(",")[0]);
			}
		}
		}

		// Options → alignment + flags (L and C have orientation flags; G has options too)
		if (options.contains("C"))      radHCentre.setSelected(true);
		else if (options.contains("R")) radHRight.setSelected(true);
		else                            radHLeft.setSelected(true);

		if (options.contains("M"))      radVMiddle.setSelected(true);
		else if (options.contains("L")) radVBottom.setSelected(true);
		else                            radVTop.setSelected(true);

		chkU.setSelected(options.contains("U"));
		chkD.setSelected(options.contains("D"));
		chkB.setSelected(options.contains("B"));
		chkE.setSelected(options.contains("E"));
		chkP.setSelected(options.contains("P"));
	}

	private void configureForType()
	{
		hideAllTypeFields();
		switch (boundType) {
		case 'L' -> {
			show(lblImageName, fldImageName);
			show(lblLRotation, spnLRotation, lblLZoom, spnLZoom);
			show(lblHAlign, hAlignPanel, lblVAlign, vAlignPanel);
			show(lblFlags, flagPanel);
		}
		case 'G' -> {
			show(lblShape, fldShape);
			show(lblX2, spnX2, lblY2, spnY2);
			show(lblPenW, spnPenW, lblPenH, spnPenH);
			show(lblRender, spnRender);
			show(lblFlags, flagPanel);
			chkU.setVisible(false); // U not meaningful for G
		}
		case 'C' -> {
			show(lblCodeType, fldCodeType);
			show(lblHeight, spnHeight, lblCZoom, spnCZoom);
			show(lblBarWidth, spnBarWidth);
			show(lblHAlign, hAlignPanel, lblVAlign, vAlignPanel);
			show(lblFlags, flagPanel);
			chkU.setVisible(false); // U not meaningful for C
		}
		}
	}

	private void disableIrrelevantWidgets()
	{
		// Disable widgets that belong to other types
		switch (boundType) {
		case 'L' -> {
			fldX2.setEnabled(false); fldY2.setEnabled(false);
			fldPenW.setEnabled(false); fldPenH.setEnabled(false); fldRender.setEnabled(false);
			fldHeight.setEnabled(false); fldCZoom.setEnabled(false); fldBarWidth.setEnabled(false);
		}
		case 'G' -> {
			fldLRotation.setEnabled(false); fldLZoom.setEnabled(false);
			fldHeight.setEnabled(false); fldCZoom.setEnabled(false); fldBarWidth.setEnabled(false);
			radHLeft.setEnabled(false); radHCentre.setEnabled(false); radHRight.setEnabled(false);
			radVTop.setEnabled(false); radVMiddle.setEnabled(false); radVBottom.setEnabled(false);
		}
		case 'C' -> {
			fldLRotation.setEnabled(false); fldLZoom.setEnabled(false);
			fldX2.setEnabled(false); fldY2.setEnabled(false);
			fldPenW.setEnabled(false); fldPenH.setEnabled(false); fldRender.setEnabled(false);
		}
		}
	}

	// ---------------------------------------------------------------------
	// Patch and reconstruct
	// ---------------------------------------------------------------------

	private void patchOriginalParts()
	{
		if (originalParts.length > 2) originalParts[2] = fldX.getText().trim();
		if (originalParts.length > 3) originalParts[3] = fldY.getText().trim();

		// Rebuild options string
		StringBuilder opts = new StringBuilder();
		if (radHCentre.isSelected()) opts.append('C');
		if (radHRight.isSelected())  opts.append('R');
		if (radVBottom.isSelected()) opts.append('L');
		if (radVMiddle.isSelected()) opts.append('M');
		if (chkU.isSelected()) opts.append('U');
		if (chkD.isSelected()) opts.append('D');
		if (chkB.isSelected()) opts.append('B');
		if (chkE.isSelected()) opts.append('E');
		if (chkP.isSelected()) opts.append('P');
		// Preserve unmodelled flags
		String oldOpts = findToken(originalParts, 'O');
		if (oldOpts != null) {
			for (char c : oldOpts.toCharArray()) {
				if ("CRLMUDBE P".indexOf(c) < 0 && opts.indexOf(String.valueOf(c)) < 0)
					opts.append(c);
			}
		}

		switch (boundType) {
		case 'L' -> {
			patchFlagTokens(4, opts);
		}
		case 'G' -> {
			if (originalParts.length > 4) originalParts[4] = fldX2.getText().trim();
			if (originalParts.length > 5) originalParts[5] = fldY2.getText().trim();
			// Patch flagged tokens starting at index 7
			for (int i = 7; i < originalParts.length; i++) {
				String p = originalParts[i].trim();
				if (p.startsWith("O")) {
					originalParts[i] = opts.length() > 0 ? "O" + opts : "";
				} else if (p.startsWith("L") && !p.equalsIgnoreCase("line")) {
					originalParts[i] = "L" + fldPenW.getText().trim();
					if (i + 1 < originalParts.length) {
						originalParts[i + 1] = fldPenH.getText().trim();
						i++;
					}
				} else if (p.startsWith("R")) {
					originalParts[i] = "R" + fldRender.getText().trim();
				}
			}
			// Handle O token insert/remove
			handleOToken(7, opts);
		}
		case 'C' -> {
			if (originalParts.length > 5) originalParts[5] = fldHeight.getText().trim();
			patchFlagTokens(6, opts);
		}
		}
	}

	/** Patch flagged tokens (O, R, Z, W) from flagStart onward — used by L and C. */
	private void patchFlagTokens(int flagStart, StringBuilder opts)
	{
		boolean foundO = false;
		for (int i = flagStart; i < originalParts.length; i++) {
			String p = originalParts[i].trim();
			if (p.startsWith("O")) {
				originalParts[i] = opts.length() > 0 ? "O" + opts : "";
				foundO = true;
			} else if (p.startsWith("R") && p.length() > 1 && Character.isDigit(p.charAt(1))) {
				if (boundType == 'L') originalParts[i] = "R" + fldLRotation.getText().trim();
			} else if (p.startsWith("Z")) {
				if (boundType == 'L') originalParts[i] = "Z" + fldLZoom.getText().trim();
				if (boundType == 'C') originalParts[i] = "Z" + fldCZoom.getText().trim();
			} else if (p.startsWith("W") && boundType == 'C') {
				originalParts[i] = "W" + fldBarWidth.getText().trim();
			}
		}
		handleOToken(flagStart, opts);
		if (!foundO && opts.length() > 0) {
			// already handled by handleOToken
		}
	}

	private void handleOToken(int flagStart, StringBuilder opts)
	{
		boolean hasO = false;
		for (int i = flagStart; i < originalParts.length; i++) {
			if (originalParts[i].trim().startsWith("O")) { hasO = true; break; }
		}
		if (!hasO && opts.length() > 0) {
			List<String> list = new ArrayList<>(Arrays.asList(originalParts));
			list.add(flagStart, "O" + opts);
			originalParts = list.toArray(new String[0]);
		}
		if (hasO && opts.length() == 0) {
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
			fldX.getText().trim(), fldY.getText().trim(),
			fldLRotation.getText().trim(), fldLZoom.getText().trim(),
			fldX2.getText().trim(), fldY2.getText().trim(),
			fldPenW.getText().trim(), fldPenH.getText().trim(), fldRender.getText().trim(),
			fldHeight.getText().trim(), fldCZoom.getText().trim(), fldBarWidth.getText().trim(),
			radHCentre.isSelected() ? "C" : radHRight.isSelected() ? "R" : "L",
			radVMiddle.isSelected() ? "M" : radVBottom.isSelected() ? "B" : "T",
			bool(chkU), bool(chkD), bool(chkB), bool(chkE), bool(chkP)
		};
	}

	private void restoreFromInitial()
	{
		if (initialValues == null || initialValues.length < 19) return;
		fldX.setText(initialValues[0]);          fldY.setText(initialValues[1]);
		fldLRotation.setText(initialValues[2]);  fldLZoom.setText(initialValues[3]);
		fldX2.setText(initialValues[4]);         fldY2.setText(initialValues[5]);
		fldPenW.setText(initialValues[6]);       fldPenH.setText(initialValues[7]);
		fldRender.setText(initialValues[8]);
		fldHeight.setText(initialValues[9]);     fldCZoom.setText(initialValues[10]);
		fldBarWidth.setText(initialValues[11]);
		switch (initialValues[12]) {
			case "C" -> radHCentre.setSelected(true);
			case "R" -> radHRight.setSelected(true);
			default  -> radHLeft.setSelected(true);
		}
		switch (initialValues[13]) {
			case "M" -> radVMiddle.setSelected(true);
			case "B" -> radVBottom.setSelected(true);
			default  -> radVTop.setSelected(true);
		}
		chkU.setSelected("1".equals(initialValues[14]));
		chkD.setSelected("1".equals(initialValues[15]));
		chkB.setSelected("1".equals(initialValues[16]));
		chkE.setSelected("1".equals(initialValues[17]));
		chkP.setSelected("1".equals(initialValues[18]));
	}

	private void checkDirty()
	{
		if (loading || boundIndex < 0 || initialValues == null) return;
		if (dirtyListener != null) dirtyListener.run();
	}

	// ---------------------------------------------------------------------
	// Visibility helpers
	// ---------------------------------------------------------------------

	private void hideAllTypeFields()
	{
		hide(lblImageName, fldImageName, lblCodeType, fldCodeType, lblShape, fldShape);
		hide(lblLRotation, spnLRotation, lblLZoom, spnLZoom);
		hide(lblX2, spnX2, lblY2, spnY2);
		hide(lblPenW, spnPenW, lblPenH, spnPenH, lblRender, spnRender);
		hide(lblHeight, spnHeight, lblCZoom, spnCZoom, lblBarWidth, spnBarWidth);
		hide(lblHAlign, hAlignPanel, lblVAlign, vAlignPanel);
		hide(lblFlags, flagPanel);
	}

	private void clearTypeFields()
	{
		fldImageName.setText(""); fldLRotation.setText(""); fldLZoom.setText("");
		fldX2.setText(""); fldY2.setText(""); fldShape.setText("");
		fldPenW.setText(""); fldPenH.setText(""); fldRender.setText("");
		fldHeight.setText(""); fldCodeType.setText(""); fldCZoom.setText(""); fldBarWidth.setText("");
		radHLeft.setSelected(true); radVTop.setSelected(true);
		chkU.setSelected(false); chkD.setSelected(false); chkB.setSelected(false);
		chkE.setSelected(false); chkP.setSelected(false);
	}

	private void clearAllFields()
	{
		fldId.setText(""); fldX.setText(""); fldY.setText("");
		clearTypeFields();
	}

	private static void show(Component... comps) { for (Component c : comps) c.setVisible(true); }
	private static void hide(Component... comps) { for (Component c : comps) c.setVisible(false); }
	private static String bool(JCheckBox cb) { return cb.isSelected() ? "1" : "0"; }

	private static String findToken(String[] parts, char prefix)
	{
		for (String p : parts) {
			String t = p.trim();
			if (t.length() > 1 && t.charAt(0) == prefix) return t.substring(1);
		}
		return null;
	}

	// ---------------------------------------------------------------------
	// Spinner compound widget
	// ---------------------------------------------------------------------

	private JPanel spinnerWrap(JTextField4j field, double step)
	{
		JButton btnUp   = new JButton("\u25B2");
		JButton btnDown = new JButton("\u25BC");
		Dimension arrowSize = new Dimension(18, ROW_H / 2);
		for (JButton b : new JButton[]{ btnUp, btnDown }) {
			b.setPreferredSize(arrowSize);
			b.setMinimumSize(arrowSize);
			b.setMaximumSize(arrowSize);
			b.setMargin(new Insets(0, 0, 0, 0));
			b.setFont(b.getFont().deriveFont(7f));
			b.setFocusable(false);
		}
		btnUp.addActionListener(_ -> adjustField(field, step));
		btnDown.addActionListener(_ -> adjustField(field, -step));

		JPanel arrows = new JPanel();
		arrows.setLayout(new BoxLayout(arrows, BoxLayout.Y_AXIS));
		arrows.add(btnUp); arrows.add(btnDown);

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
		if (text.isEmpty()) { if (delta <= 0) return; text = "0"; }
		try {
			int val = Integer.parseInt(text) + (int) delta;
			field.setText(String.valueOf(val));
		} catch (NumberFormatException ignored) {}
	}

	// ----- layout helpers ------------------------------------------------

	private JLabel addRow(JPanel grid, int row,
			String leftLabel, Component leftField,
			String rightLabel, Component rightField)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);
		c.gridy = row;
		JLabel ll = smallLabel(leftLabel);
		c.gridx = 0; c.anchor = GridBagConstraints.EAST; grid.add(ll, c);
		c.gridx = 1; c.anchor = GridBagConstraints.WEST; grid.add(leftField, c);
		JLabel rl = smallLabel(rightLabel);
		c.gridx = 2; c.anchor = GridBagConstraints.EAST; grid.add(rl, c);
		c.gridx = 3; c.anchor = GridBagConstraints.WEST; grid.add(rightField, c);
		return ll;
	}

	private static void addWideRow(JPanel grid, int row, JLabel label, Component wide)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);
		c.gridy = row;
		c.gridx = 0; c.anchor = GridBagConstraints.EAST; grid.add(label, c);
		c.gridx = 1; c.gridwidth = 3; c.anchor = GridBagConstraints.WEST; grid.add(wide, c);
	}

	private static void addOverlay(JPanel grid, int row, int labelCol,
			JLabel label, int fieldCol, Component field)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);
		c.gridy = row;
		c.gridx = labelCol; c.anchor = GridBagConstraints.EAST; grid.add(label, c);
		c.gridx = fieldCol; c.anchor = GridBagConstraints.WEST; grid.add(field, c);
	}

	private static JLabel labelAt(JPanel grid, int row, int col)
	{
		for (Component comp : grid.getComponents()) {
			GridBagConstraints gbc = ((GridBagLayout) grid.getLayout()).getConstraints(comp);
			if (gbc.gridy == row && gbc.gridx == col && comp instanceof JLabel)
				return (JLabel) comp;
		}
		return new JLabel();
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
		c.setPreferredSize(d); c.setMinimumSize(d); c.setMaximumSize(d);
	}
}
