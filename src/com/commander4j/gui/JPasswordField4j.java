package com.commander4j.gui;

import java.awt.Color;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JPasswordField;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;

import com.commander4j.filters.JFixedSizeFilter;
import com.commander4j.sys.Common;

public class JPasswordField4j extends JPasswordField
{

    private static final long serialVersionUID = 1L;
    AbstractDocument doc1;
    JFixedSizeFilter tsf;
    private static final Border EMPTY_BORDER = new LineBorder(Color.GRAY);

    FocusListener fl = new FocusListener()
    {
        public void focusGained(FocusEvent e)
        {
            setForeground(Common.color_textfield_foreground_focus_color);
            setBackground(Common.color_textfield_background_focus_color);
        }

        public void focusLost(FocusEvent e)
        {
            setForeground(Common.color_textfield_forground_nofocus_color);
            setBackground(Common.color_textfield_background_nofocus_color);
        }
    };

    private void init()
    {
        setDisabledTextColor(Common.color_textfield_foreground_disabled);
        setBorder(EMPTY_BORDER);
        setFont(Common.font_input);
    }

    public JPasswordField4j()
    {
        super();
        init();
    }

    public JPasswordField4j(String text)
    {
        super(text);
        init();
    }

    public JPasswordField4j(int columns)
    {
        super(columns);
        init();

        final int cols = columns;

        doc1 = (AbstractDocument) this.getDocument();
        tsf = new JFixedSizeFilter(columns);
        doc1.setDocumentFilter(tsf);

        addFocusListener(fl);

        doc1.addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                if (cols == e.getDocument().getLength())
                {
                    setForeground(Common.color_text_maxsize_color);
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                setForeground(Common.color_textfield_foreground_focus_color);
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                setForeground(Common.color_textfield_foreground_focus_color);
            }
        });
    }

    public JPasswordField4j(String text, int columns)
    {
        super(text, columns);
        init();
    }

    public JPasswordField4j(Document doc, String text, int columns)
    {
        super(doc, text, columns);
        init();
    }

}
