package com.commander4j.logorenderer.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Alternating-row colour renderer for the field data table.
 */
public class FieldTableCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    private static final Color ALT_BG = new Color(245, 245, 245);

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {

        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (!isSelected) {
            setBackground(row % 2 == 0 ? table.getBackground() : ALT_BG);
            setForeground(table.getForeground());
        }

        setHorizontalAlignment(JLabel.LEFT);
        return this;
    }
}
