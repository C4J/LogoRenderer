package com.commander4j.logorenderer.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.commander4j.gui.JButton4j;
import com.commander4j.logorenderer.LlfLayout;
import com.commander4j.sys.Common;

/**
 * Dynamically builds operator input fields from the QUE prompts in the current
 * layout.  Submitting applies the values via the same path as an FD command.
 */
public class QuePanel extends JPanel
{

	private static final long serialVersionUID = 1L;

	/** Callback receives (fieldId, value) pairs — same contract as FD commands. */
	public interface QueSubmitListener
	{
		void onFieldSubmit(String fieldId, String value);
	}

	private final JPanel             formPanel = new JPanel(new GridBagLayout());
	private final JLabel             emptyLabel = new JLabel("No operator prompts in this layout.");
	private final Map<Integer, JTextField> inputs = new LinkedHashMap<>();
	private final QueSubmitListener  listener;
	private JTextField               firstInput;

	public QuePanel(QueSubmitListener listener)
	{
		this.listener = listener;
		setLayout(new BorderLayout());

		formPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		emptyLabel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		emptyLabel.setFont(Common.font_std);

		JScrollPane scroll = new JScrollPane(formPanel);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
		add(buildButtonBar(), BorderLayout.SOUTH);
	}

	private JPanel buildButtonBar()
	{
		JButton4j btnApply = new JButton4j(Common.icon_ok);
		btnApply.setText("Apply");
		btnApply.setToolTipText("Apply QUE input values as FD commands");
		btnApply.addActionListener(_ -> applyAll());

		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		bar.add(btnApply);
		return bar;
	}

	/**
	 * Rebuild the form from the given layout's QUE prompts. Current field values
	 * (QA defaults or FD overrides) are used to populate the text fields.
	 */
	public void loadPrompts(LlfLayout layout)
	{
		formPanel.removeAll();
		inputs.clear();
		firstInput = null;

		if (layout == null || layout.getQuePrompts().isEmpty())
		{
			GridBagConstraints gc = new GridBagConstraints();
			gc.gridx = 0; gc.gridy = 0;
			gc.anchor = GridBagConstraints.NORTHWEST;
			gc.weightx = 1.0; gc.weighty = 1.0;
			gc.fill = GridBagConstraints.BOTH;
			formPanel.add(emptyLabel, gc);
			formPanel.revalidate();
			formPanel.repaint();
			return;
		}

		int row = 0;
		for (Map.Entry<Integer, String> entry : layout.getQuePrompts().entrySet())
		{
			int    fieldId = entry.getKey();
			String prompt  = entry.getValue();

			JLabel lblPrompt = new JLabel(prompt);
			lblPrompt.setFont(Common.font_std);

			JLabel lblId = new JLabel("[" + fieldId + "]");
			lblId.setFont(Common.font_std);
			lblId.setForeground(java.awt.Color.GRAY);

			JTextField tf = new JTextField(layout.getField(fieldId));
			tf.setFont(Common.font_input);
			tf.setPreferredSize(new Dimension(260, 22));
			tf.addActionListener(_ -> applyAll());

			inputs.put(fieldId, tf);
			if (firstInput == null) firstInput = tf;

			GridBagConstraints gcId = new GridBagConstraints();
			gcId.gridx = 0; gcId.gridy = row;
			gcId.anchor = GridBagConstraints.WEST;
			gcId.insets = new Insets(2, 2, 2, 6);
			formPanel.add(lblId, gcId);

			GridBagConstraints gcLabel = new GridBagConstraints();
			gcLabel.gridx = 1; gcLabel.gridy = row;
			gcLabel.anchor = GridBagConstraints.WEST;
			gcLabel.insets = new Insets(2, 2, 2, 6);
			formPanel.add(lblPrompt, gcLabel);

			GridBagConstraints gcInput = new GridBagConstraints();
			gcInput.gridx = 2; gcInput.gridy = row;
			gcInput.anchor = GridBagConstraints.WEST;
			gcInput.fill   = GridBagConstraints.HORIZONTAL;
			gcInput.weightx = 1.0;
			gcInput.insets = new Insets(2, 2, 2, 2);
			formPanel.add(tf, gcInput);

			row++;
		}

		// Filler to push rows to the top
		GridBagConstraints filler = new GridBagConstraints();
		filler.gridx = 0; filler.gridy = row;
		filler.weighty = 1.0;
		filler.fill = GridBagConstraints.VERTICAL;
		formPanel.add(new JPanel(), filler);

		formPanel.revalidate();
		formPanel.repaint();
	}

	/** Move keyboard focus to the first input field, if any. */
	public void focusFirstInput()
	{
		if (firstInput != null)
		{
			SwingUtilities.invokeLater(() -> {
				firstInput.requestFocusInWindow();
				firstInput.selectAll();
			});
		}
	}

	private void applyAll()
	{
		if (listener == null) return;
		for (Map.Entry<Integer, JTextField> entry : inputs.entrySet())
		{
			String id    = String.valueOf(entry.getKey());
			String value = entry.getValue().getText();
			listener.onFieldSubmit(id, value);
		}
	}
}
