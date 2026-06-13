package com.commander4j.logorenderer.ui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.commander4j.gui.JButton4j;
import com.commander4j.gui.JLabel4j_std;
import com.commander4j.gui.JTextField4j;

/**
 * Simple font family picker dialog.
 * Shows a filterable list of all available Java font families with a live preview.
 * Call {@link #pick(Dialog, String)} to open it and get the selected family name.
 */
public class JDialogFamilyPicker extends JDialog
{
	private static final long serialVersionUID = 1L;

	private String result = null;
	private final javax.swing.JList<String> list = new javax.swing.JList<>();
	private final JLabel4j_std preview = new JLabel4j_std("AaBbCcXxYyZz 0123456789");
	private String[] allFamilies;

	/** Opens the picker and returns the selected family name, or null if cancelled. */
	public static String pick(Dialog parent, String current)
	{
		JDialogFamilyPicker d = new JDialogFamilyPicker(parent, current);
		d.setVisible(true);
		return d.result;
	}

	/**
	 * Overload accepting any {@link java.awt.Window} ancestor (Frame or Dialog).
	 * Lets non-dialog hosts (e.g. the inline font-properties panel) open the
	 * picker without manufacturing a throwaway Dialog owner.
	 */
	public static String pick(java.awt.Window parent, String current)
	{
		JDialogFamilyPicker d;
		if (parent instanceof Dialog)
			d = new JDialogFamilyPicker((Dialog) parent, current);
		else if (parent instanceof java.awt.Frame)
			d = new JDialogFamilyPicker((java.awt.Frame) parent, current);
		else
			d = new JDialogFamilyPicker((java.awt.Frame) null, current);
		d.setVisible(true);
		return d.result;
	}

	public JDialogFamilyPicker(java.awt.Frame parent, String current)
	{
		super(parent, "Choose Font Family", true);
		initCommon(current);
	}

	public JDialogFamilyPicker(Dialog parent, String current)
	{
		super(parent, "Choose Font Family", true);
		initCommon(current);
	}

	private void initCommon(String current)
	{
		allFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();

		DefaultListModel<String> model = new DefaultListModel<>();
		for (String f : allFamilies)
			model.addElement(f);
		list.setModel(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// Select current family if present
		for (int i = 0; i < allFamilies.length; i++)
		{
			if (allFamilies[i].equals(current))
			{
				list.setSelectedIndex(i);
				list.ensureIndexIsVisible(i);
				break;
			}
		}

		list.addListSelectionListener(_ -> updatePreview());

		// Filter field
		JTextField4j filter = new JTextField4j();
		filter.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e)  { applyFilter(filter.getText()); }
			public void removeUpdate(DocumentEvent e)  { applyFilter(filter.getText()); }
			public void changedUpdate(DocumentEvent e) {}
		});

		// Preview label
		preview.setBorder(BorderFactory.createEtchedBorder());
		preview.setHorizontalAlignment(SwingConstants.CENTER);
		preview.setPreferredSize(new java.awt.Dimension(380, 42));

		// Buttons
		JButton4j ok = new JButton4j("OK");
		ok.addActionListener(_ ->
		{
			result = list.getSelectedValue();
			dispose();
		});
		JButton4j cancel = new JButton4j("Cancel");
		cancel.addActionListener(_ -> dispose());

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(ok);
		buttons.add(cancel);

		JPanel north = new JPanel(new BorderLayout(4, 0));
		north.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		north.add(new JLabel4j_std("Filter: "), BorderLayout.WEST);
		north.add(filter, BorderLayout.CENTER);

		JPanel south = new JPanel(new BorderLayout(4, 4));
		south.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
		south.add(preview, BorderLayout.CENTER);
		south.add(buttons, BorderLayout.SOUTH);

		getContentPane().setLayout(new BorderLayout(0, 0));
		getContentPane().add(north, BorderLayout.NORTH);
		getContentPane().add(new JScrollPane(list), BorderLayout.CENTER);
		getContentPane().add(south, BorderLayout.SOUTH);

		getRootPane().setDefaultButton(ok);
		setSize(400, 520);
		setLocationRelativeTo(getOwner());
		updatePreview();
	}

	private void applyFilter(String text)
	{
		DefaultListModel<String> model = (DefaultListModel<String>) list.getModel();
		model.clear();
		String lower = text.toLowerCase();
		for (String f : allFamilies)
		{
			if (f.toLowerCase().contains(lower))
				model.addElement(f);
		}
		if (!model.isEmpty())
			list.setSelectedIndex(0);
	}

	private void updatePreview()
	{
		String sel = list.getSelectedValue();
		if (sel != null)
			preview.setFont(new Font(sel, Font.PLAIN, 18));
	}
}
