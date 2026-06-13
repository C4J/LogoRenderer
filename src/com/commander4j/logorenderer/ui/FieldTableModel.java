package com.commander4j.logorenderer.ui;

import javax.swing.table.AbstractTableModel;

import com.commander4j.logorenderer.LabelField;
import com.commander4j.logorenderer.ServerMemory;

import java.util.function.BiConsumer;

/**
 * Table model that displays the contents of {@link ServerMemory#Fields}.
 * The Value column is editable; a callback is fired on commit so the caller
 * can apply the same logic as an incoming FD command.
 */
public class FieldTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    public static final int COL_TYPE   = 0;
    public static final int COL_ID     = 1;
    public static final int COL_VALUE  = 2;
    public static final int COL_SOURCE = 3;

    private static final String[] COLUMN_NAMES = { "Type", "ID", "Value", "Source" };

    /** Called with (fieldId, newValue) when the user commits an edit. */
    private BiConsumer<String, String> editCallback;

    public void setEditCallback(BiConsumer<String, String> callback) {
        this.editCallback = callback;
    }

    @Override
    public int getColumnCount() { return 4; }

    @Override
    public String getColumnName(int col) { return COLUMN_NAMES[col]; }

    @Override
    public int getRowCount() {
        try {
            return ServerMemory.Fields.size();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return col == COL_VALUE;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        if (col != COL_VALUE) return;
        String key = keyForRow(row);
        if (key == null) return;
        String newValue = value == null ? "" : value.toString();
        if (editCallback != null) {
            editCallback.accept(key, newValue);
        }
    }

    @Override
    public Object getValueAt(int row, int col) {
        String key = keyForRow(row);
        if (key == null) return "";
        try {
            LabelField f = ServerMemory.Fields.get(key);
            return switch (col) {
                case COL_TYPE   -> f.getFieldType();
                case COL_ID     -> f.getFieldID();
                case COL_VALUE  -> f.getFieldValue();
                case COL_SOURCE -> f.getFieldUpdateSource();
                default         -> "";
            };
        } catch (Exception e) {
            return "Error";
        }
    }

    private String keyForRow(int row) {
        int index = 0;
        for (String k : ServerMemory.Fields.keySet()) {
            if (index == row) return k;
            index++;
        }
        return null;
    }
}
