package com.commander4j.logorenderer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.commander4j.gui.JButton4j;
import com.commander4j.logorenderer.ParseLogger;
import com.commander4j.sys.Common;

/**
 * Panel showing a scrollable, colour-coded diagnostic log.
 * Implements {@link ParseLogger} so it can be wired directly to the parser.
 */
public class LogPanel extends JPanel implements ParseLogger
{

	private static final long serialVersionUID = 1L;

	private final JTextPane textPane = new JTextPane();

	private final SimpleAttributeSet attrInfo  = new SimpleAttributeSet();
	private final SimpleAttributeSet attrWarn  = new SimpleAttributeSet();
	private final SimpleAttributeSet attrError = new SimpleAttributeSet();

	public LogPanel()
	{
		setLayout(new BorderLayout());

		textPane.setEditable(false);
		textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

		StyleConstants.setForeground(attrInfo,  Color.BLACK);
		StyleConstants.setForeground(attrWarn,  new Color(180, 120, 0));
		StyleConstants.setForeground(attrError, Color.RED);
		StyleConstants.setBold(attrError, true);

		add(new JScrollPane(textPane), BorderLayout.CENTER);
		add(buildButtonBar(), BorderLayout.SOUTH);
	}

	private JPanel buildButtonBar()
	{
		JButton4j btnClear = new JButton4j(Common.icon_cancel);
		btnClear.setToolTipText("Clear Log");
		btnClear.addActionListener(_ -> clear());

		JButton4j btnSave = new JButton4j(Common.icon_save);
		btnSave.setToolTipText("Save Log...");
		btnSave.addActionListener(_ -> saveLog());

		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		bar.add(btnClear);
		bar.add(btnSave);
		return bar;
	}

	// -----------------------------------------------------------------------
	// ParseLogger implementation
	// -----------------------------------------------------------------------

	@Override
	public void info(String message)
	{
		append(message + "\n", attrInfo);
	}

	@Override
	public void warn(String message)
	{
		append("WARN: " + message + "\n", attrWarn);
	}

	@Override
	public void error(String message)
	{
		append("ERROR: " + message + "\n", attrError);
	}

	/** Append a general message with a specific colour (for server log etc). */
	public void appendColoured(String message, Color c)
	{
		SimpleAttributeSet attr = new SimpleAttributeSet();
		StyleConstants.setForeground(attr, c);
		append(message, attr);
	}

	// -----------------------------------------------------------------------
	// Actions
	// -----------------------------------------------------------------------

	public void clear()
	{
		textPane.setText("");
	}

	private void saveLog()
	{
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
		fc.setSelectedFile(new File("parse_log.txt"));
		if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			File out = fc.getSelectedFile();
			if (!out.getName().toLowerCase().endsWith(".txt"))
				out = new File(out.getParentFile(), out.getName() + ".txt");
			try
			{
				String text = textPane.getDocument().getText(0, textPane.getDocument().getLength());
				Files.writeString(out.toPath(), text, StandardCharsets.UTF_8);
			}
			catch (BadLocationException | IOException ex)
			{
				JOptionPane.showMessageDialog(this,
						"Save failed: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	// -----------------------------------------------------------------------
	// Internal
	// -----------------------------------------------------------------------

	private void append(String text, SimpleAttributeSet attr)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			doAppend(text, attr);
		}
		else
		{
			SwingUtilities.invokeLater(() -> doAppend(text, attr));
		}
	}

	private void doAppend(String text, SimpleAttributeSet attr)
	{
		StyledDocument doc = textPane.getStyledDocument();
		try
		{
			doc.insertString(doc.getLength(), text, attr);
			textPane.setCaretPosition(doc.getLength());
		}
		catch (BadLocationException ignored) {}
	}
}
