package com.commander4j.logorenderer.ui;

import java.util.List;

/**
 * Property definitions for the font-properties table. These read from and
 * write to a String[] row matching the {@code fonts.xml} column layout:
 * [0]=labelName, [1]=family, [2]=style, [3]=refH, [4]=refW, [5]=filename,
 * [6]=renderer, [7]=spacing, [8]=ascentFactor, [9]=descentFactor, [10]=description
 */
public final class FontPropertyDefs
{

	private FontPropertyDefs() {}

	private static final String[] STYLES    = { "PLAIN", "BOLD", "ITALIC", "BOLD_ITALIC" };
	private static final String[] RENDERERS = { "SCALEABLE", "BITMAP" };
	private static final String[] SPACINGS  = { "PROPORTIONAL", "MONOSPACED" };

	public static List<PropertyDef> defs()
	{
		return List.of(
			PropertyDef.readOnly("Font Name",
				p -> safeGet(p, 0)),
			PropertyDef.fontPicker("Java Family",
				p -> safeGet(p, 1),
				(p, v) -> safeSet(p, 1, v)),
			PropertyDef.combo("Style", STYLES,
				p -> safeGet(p, 2, "PLAIN"),
				(p, v) -> safeSet(p, 2, v)),
			PropertyDef.spinner("Ref Height", 1,
				p -> safeGet(p, 3),
				(p, v) -> safeSet(p, 3, v)),
			PropertyDef.spinner("Ref Width", 1,
				p -> safeGet(p, 4),
				(p, v) -> safeSet(p, 4, v)),
			PropertyDef.filePicker("TTF Filename",
				p -> safeGet(p, 5),
				(p, v) -> safeSet(p, 5, v)),
			PropertyDef.combo("Renderer", RENDERERS,
				p -> safeGet(p, 6, "SCALEABLE"),
				(p, v) -> safeSet(p, 6, v)),
			PropertyDef.combo("Spacing", SPACINGS,
				p -> safeGet(p, 7, "PROPORTIONAL"),
				(p, v) -> safeSet(p, 7, v)),
			PropertyDef.spinner("Ascent Factor", 0.05,
				p -> safeGet(p, 8, "1.0"),
				(p, v) -> safeSet(p, 8, v)),
			PropertyDef.spinner("Descent Factor", 0.05,
				p -> safeGet(p, 9, "0.25"),
				(p, v) -> safeSet(p, 9, v))
		);
	}

	private static String safeGet(String[] p, int i)
	{
		return i < p.length ? p[i] : "";
	}

	private static String safeGet(String[] p, int i, String fallback)
	{
		String v = safeGet(p, i);
		return v.isEmpty() ? fallback : v;
	}

	private static String[] safeSet(String[] p, int i, String v)
	{
		if (i >= p.length) {
			String[] expanded = new String[i + 1];
			System.arraycopy(p, 0, expanded, 0, p.length);
			for (int j = p.length; j < expanded.length; j++) expanded[j] = "";
			p = expanded;
		}
		p[i] = v;
		return p;
	}
}
