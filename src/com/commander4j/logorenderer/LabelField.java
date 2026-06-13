package com.commander4j.logorenderer;

public class LabelField {

    private String fieldID = "";
    private String fieldType = "";
    private String fieldValue = "";
    private String fieldUpdateSource = "";

    public LabelField() {
        setFieldID("");
        setFieldValue("");
    }

    public String getFieldID() { return fieldID; }
    public void setFieldID(String fieldID) { this.fieldID = fieldID; }

    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }

    public String getFieldValue() { return fieldValue; }
    public void setFieldValue(String fieldValue) { this.fieldValue = fieldValue; }

    public String getFieldUpdateSource() { return fieldUpdateSource; }
    public void setFieldUpdateSource(String fieldUpdateSource) { this.fieldUpdateSource = fieldUpdateSource; }

    @Override
    public String toString() {
        return fieldType + " " + fieldID + " | " + fieldValue + " | " + fieldUpdateSource;
    }
}
