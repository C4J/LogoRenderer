package com.commander4j.logorenderer.ui;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Describes a single property row in a {@link PropertyTablePanel}.
 * The getter extracts the current value from a String[] parts array;
 * the setter patches a new value into the array (possibly resizing it).
 */
public final class PropertyDef
{

	public enum EditorType
	{
		READONLY, TEXT, SPINNER, COMBO, FILE_PICKER, FONT_PICKER, IMAGE_PICKER, FLAGS
	}

	private final String     label;
	private final EditorType editorType;
	private final double     step;         // for SPINNER
	private final String[]   comboValues;  // for COMBO
	private final String[]   flagDefs;     // for FLAGS — each entry is "X=Description"
	private final Function<String[], String>           getter;
	private final BiFunction<String[], String, String[]> setter;

	private PropertyDef(String label, EditorType type, double step,
			String[] comboValues, String[] flagDefs,
			Function<String[], String> getter,
			BiFunction<String[], String, String[]> setter)
	{
		this.label       = label;
		this.editorType  = type;
		this.step        = step;
		this.comboValues = comboValues;
		this.flagDefs    = flagDefs;
		this.getter      = getter;
		this.setter      = setter;
	}

	public String     getLabel()       { return label; }
	public EditorType getEditorType()  { return editorType; }
	public double     getStep()        { return step; }
	public String[]   getComboValues() { return comboValues; }
	public String[]   getFlagDefs()    { return flagDefs; }

	public String   getValue(String[] parts)                { return getter.apply(parts); }
	public String[] setValue(String[] parts, String value)   { return setter.apply(parts, value); }

	// --- factory methods ---

	public static PropertyDef readOnly(String label,
			Function<String[], String> getter)
	{
		return new PropertyDef(label, EditorType.READONLY, 0, null, null, getter, (p, _) -> p);
	}

	public static PropertyDef text(String label,
			Function<String[], String> getter,
			BiFunction<String[], String, String[]> setter)
	{
		return new PropertyDef(label, EditorType.TEXT, 0, null, null, getter, setter);
	}

	public static PropertyDef spinner(String label, double step,
			Function<String[], String> getter,
			BiFunction<String[], String, String[]> setter)
	{
		return new PropertyDef(label, EditorType.SPINNER, step, null, null, getter, setter);
	}

	public static PropertyDef combo(String label, String[] values,
			Function<String[], String> getter,
			BiFunction<String[], String, String[]> setter)
	{
		return new PropertyDef(label, EditorType.COMBO, 0, values, null, getter, setter);
	}

	public static PropertyDef flags(String label, String[] flagDefs,
			Function<String[], String> getter,
			BiFunction<String[], String, String[]> setter)
	{
		return new PropertyDef(label, EditorType.FLAGS, 0, null, flagDefs, getter, setter);
	}

	public static PropertyDef imagePicker(String label,
			Function<String[], String> getter,
			BiFunction<String[], String, String[]> setter)
	{
		return new PropertyDef(label, EditorType.IMAGE_PICKER, 0, null, null, getter, setter);
	}

	public static PropertyDef filePicker(String label,
			Function<String[], String> getter,
			BiFunction<String[], String, String[]> setter)
	{
		return new PropertyDef(label, EditorType.FILE_PICKER, 0, null, null, getter, setter);
	}

	public static PropertyDef fontPicker(String label,
			Function<String[], String> getter,
			BiFunction<String[], String, String[]> setter)
	{
		return new PropertyDef(label, EditorType.FONT_PICKER, 0, null, null, getter, setter);
	}
}
