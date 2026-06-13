package com.commander4j.logorenderer.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Registry of property definitions for each LLF source-line type.
 * Each definition carries a label, editor type, and getter/setter lambdas
 * that operate on the comma-split parts array.
 */
public final class LinePropertyDefs
{

	private LinePropertyDefs() {}

	// --- Alignment combo values ---
	private static final String[] H_ALIGN = { "Left", "Centre", "Right" };
	private static final String[] V_ALIGN = { "Top", "Middle", "Bottom" };
	private static final String[] ROTATION_VALUES = { "0", "2", "4", "6", "8" };
	private static final String[] BARCODE_TYPES = {
		"ean128", "code128", "code39", "code93", "ean", "int2of5",
		"itf14", "codabar", "code11", "iata2of5", "ind2of5",
		"MSI", "Plessey", "Postnet", "upca", "upce"
	};

	// --- Flag definitions ---
	private static final String[] T_FLAGS = {
		"D=Disabled", "U=Upside Down", "V=Vertical", "B=Borders",
		"E=Redraw", "P=Protected", "Q=UTF-8"
	};
	private static final String[] S_FLAGS = {
		"D=Disabled", "U=Upside Down", "B=Borders", "E=Redraw", "P=Protected", "Q=UTF-8"
	};
	private static final String[] L_FLAGS = {
		"D=Disabled", "U=Upside Down", "B=Borders", "E=Redraw", "P=Protected"
	};
	private static final String[] G_FLAGS = {
		"D=Disabled", "B=Borders", "E=Redraw", "P=Protected"
	};
	private static final String[] C_FLAGS = {
		"D=Disabled", "U=Upside Down", "V=Vertical", "B=Borders",
		"E=Redraw", "P=Protected", "Q=UTF-8", "O=Outdata", "F=Forced Query"
	};

	/**
	 * Returns the property definitions for the given source-line command type,
	 * or an empty list if the type is not supported.
	 */
	public static List<PropertyDef> forLineType(String cmd)
	{
		if (cmd == null) return Collections.emptyList();
		return switch (cmd.toUpperCase()) {
			case "IMG"  -> imgProperties();
			case "T"    -> tProperties();
			case "S"    -> sProperties();
			case "L"    -> lProperties();
			case "G"    -> gProperties();
			case "C"    -> cProperties();
			case "FD", "FF" -> fdProperties();
			case "UD", "UF" -> udProperties();
			case "VAR"  -> varProperties();
			case "UVAR" -> uvarProperties();
			case "BD"   -> bdProperties();
			case "QUE"  -> queProperties();
			case "FL"   -> flProperties();
			default     -> Collections.emptyList();
		};
	}

	// =====================================================================
	// T — text element: T,id,x,y,wmax,Ffont,Ooptions,Rrot,Zzoomw,zoomh
	// =====================================================================

	private static List<PropertyDef> tProperties()
	{
		return List.of(
			PropertyDef.readOnly("Element ID", p -> get(p, 1)),
			PropertyDef.spinner("X Position", 1, p -> get(p, 2), (p, v) -> set(p, 2, v)),
			PropertyDef.spinner("Y Position", 1, p -> get(p, 3), (p, v) -> set(p, 3, v)),
			PropertyDef.spinner("Max Width",  1, p -> get(p, 4), (p, v) -> set(p, 4, v)),
			PropertyDef.readOnly("Font Name", p -> getToken(p, 5, 'F')),
			PropertyDef.combo("Rotation", ROTATION_VALUES,
				p -> getToken(p, 5, 'R'),
				(p, v) -> setToken(p, 5, 'R', v)),
			PropertyDef.spinner("Zoom Width", 1,
				p -> getZoomW(p, 5),
				(p, v) -> setZoomW(p, 5, v)),
			PropertyDef.spinner("Zoom Height", 1,
				p -> getZoomH(p, 5),
				(p, v) -> setZoomH(p, 5, v)),
			PropertyDef.combo("Horizontal Align", H_ALIGN,
				p -> getHAlign(p, 5),
				(p, v) -> setHAlign(p, 5, v)),
			PropertyDef.combo("Vertical Align", V_ALIGN,
				p -> getVAlign(p, 5),
				(p, v) -> setVAlign(p, 5, v)),
			PropertyDef.flags("Options", T_FLAGS,
				p -> getMiscFlags(p, 5, "CRLM"),
				(p, v) -> setMiscFlags(p, 5, "CRLM", v))
		);
	}

	// =====================================================================
	// S — scalable text: S,id,x,y[,wmax],Ffont,Hh,Aa,Ww,Ooptions
	// =====================================================================

	private static List<PropertyDef> sProperties()
	{
		return List.of(
			PropertyDef.readOnly("Element ID", p -> get(p, 1)),
			PropertyDef.spinner("X Position (mm)", 0.1, p -> get(p, 2), (p, v) -> set(p, 2, v)),
			PropertyDef.spinner("Y Position (mm)", 0.1, p -> get(p, 3), (p, v) -> set(p, 3, v)),
			PropertyDef.spinner("Max Width (mm)",  0.1,
				p -> getSMaxWidth(p),
				(p, v) -> setSMaxWidth(p, v)),
			PropertyDef.readOnly("Font Name", p -> getToken(p, sFlagStart(p), 'F')),
			PropertyDef.spinner("Height (mm)", 0.5,
				p -> getToken(p, sFlagStart(p), 'H'),
				(p, v) -> setToken(p, sFlagStart(p), 'H', v)),
			PropertyDef.spinner("Angle (\u00B0)", 1,
				p -> getToken(p, sFlagStart(p), 'A'),
				(p, v) -> setToken(p, sFlagStart(p), 'A', v)),
			PropertyDef.spinner("Char Width %", 1,
				p -> getSToken(p, 'W'),
				(p, v) -> setSToken(p, 'W', v)),
			PropertyDef.combo("Horizontal Align", H_ALIGN,
				p -> getHAlign(p, sFlagStart(p)),
				(p, v) -> setHAlign(p, sFlagStart(p), v)),
			PropertyDef.combo("Vertical Align", V_ALIGN,
				p -> getVAlign(p, sFlagStart(p)),
				(p, v) -> setVAlign(p, sFlagStart(p), v)),
			PropertyDef.flags("Options", S_FLAGS,
				p -> getMiscFlags(p, sFlagStart(p), "CRLM"),
				(p, v) -> setMiscFlags(p, sFlagStart(p), "CRLM", v))
		);
	}

	// =====================================================================
	// L — logo/image: L,id,x,y,Limagename,Ooptions,Rrot,Zzoom
	// =====================================================================

	private static List<PropertyDef> lProperties()
	{
		return List.of(
			PropertyDef.readOnly("Element ID", p -> get(p, 1)),
			PropertyDef.spinner("X Position", 1, p -> get(p, 2), (p, v) -> set(p, 2, v)),
			PropertyDef.spinner("Y Position", 1, p -> get(p, 3), (p, v) -> set(p, 3, v)),
			PropertyDef.readOnly("Image Name", p -> getToken(p, 4, 'L')),
			PropertyDef.combo("Rotation", ROTATION_VALUES,
				p -> getToken(p, 4, 'R'),
				(p, v) -> setToken(p, 4, 'R', v)),
			PropertyDef.spinner("Zoom", 1,
				p -> getToken(p, 4, 'Z'),
				(p, v) -> setToken(p, 4, 'Z', v)),
			PropertyDef.combo("Horizontal Align", H_ALIGN,
				p -> getHAlign(p, 4),
				(p, v) -> setHAlign(p, 4, v)),
			PropertyDef.combo("Vertical Align", V_ALIGN,
				p -> getVAlign(p, 4),
				(p, v) -> setVAlign(p, 4, v)),
			PropertyDef.flags("Options", L_FLAGS,
				p -> getMiscFlags(p, 4, "CRLM"),
				(p, v) -> setMiscFlags(p, 4, "CRLM", v))
		);
	}

	// =====================================================================
	// G — graphic: G,id,x1,y1,x2,y2,shape[,Lpenw,penh][,Ooptions][,Rrender]
	// =====================================================================

	private static List<PropertyDef> gProperties()
	{
		return List.of(
			PropertyDef.readOnly("Element ID", p -> get(p, 1)),
			PropertyDef.spinner("X1", 1, p -> get(p, 2), (p, v) -> set(p, 2, v)),
			PropertyDef.spinner("Y1", 1, p -> get(p, 3), (p, v) -> set(p, 3, v)),
			PropertyDef.spinner("X2", 1, p -> get(p, 4), (p, v) -> set(p, 4, v)),
			PropertyDef.spinner("Y2", 1, p -> get(p, 5), (p, v) -> set(p, 5, v)),
			PropertyDef.readOnly("Shape", p -> get(p, 6)),
			PropertyDef.spinner("Pen Width", 1,
				p -> getGPenW(p),
				(p, v) -> setGPenW(p, v)),
			PropertyDef.spinner("Pen Height", 1,
				p -> getGPenH(p),
				(p, v) -> setGPenH(p, v)),
			PropertyDef.spinner("Render", 1,
				p -> getToken(p, 7, 'R'),
				(p, v) -> setToken(p, 7, 'R', v)),
			PropertyDef.flags("Options", G_FLAGS,
				p -> getMiscFlags(p, 7, "CRLM"),
				(p, v) -> setMiscFlags(p, 7, "CRLM", v))
		);
	}

	// =====================================================================
	// C — barcode: C,id,x,y,0,height,Ccodetype,Ooptions,Zzoom,Wbarwidth
	// =====================================================================

	private static List<PropertyDef> cProperties()
	{
		return List.of(
			PropertyDef.readOnly("Element ID", p -> get(p, 1)),
			PropertyDef.spinner("X Position", 1, p -> get(p, 2), (p, v) -> set(p, 2, v)),
			PropertyDef.spinner("Y Position", 1, p -> get(p, 3), (p, v) -> set(p, 3, v)),
			PropertyDef.spinner("Height", 1, p -> get(p, 5), (p, v) -> set(p, 5, v)),
			PropertyDef.combo("Code Type", BARCODE_TYPES,
			p -> getToken(p, 6, 'C'),
			(p, v) -> setToken(p, 6, 'C', v)),
			PropertyDef.spinner("Zoom", 1,
				p -> getToken(p, 6, 'Z'),
				(p, v) -> setToken(p, 6, 'Z', v)),
			PropertyDef.spinner("Bar Width", 1,
				p -> getCToken(p, 'W'),
				(p, v) -> setCToken(p, 'W', v)),
			PropertyDef.combo("Horizontal Align", H_ALIGN,
				p -> getHAlign(p, 6),
				(p, v) -> setHAlign(p, 6, v)),
			PropertyDef.combo("Vertical Align", V_ALIGN,
				p -> getVAlign(p, 6),
				(p, v) -> setVAlign(p, 6, v)),
			PropertyDef.flags("Options", C_FLAGS,
				p -> getMiscFlags(p, 6, "CRLM"),
				(p, v) -> setMiscFlags(p, 6, "CRLM", v))
		);
	}

	// =====================================================================
	// Data commands: FD, FF, UD, UF, VAR, UVAR, BD, QUE, FL
	// =====================================================================

	private static List<PropertyDef> fdProperties()
	{
		return List.of(
			PropertyDef.readOnly("Command", _ -> "Field Data"),
			PropertyDef.spinner("Field ID", 1, p -> get(p, 1), (p, v) -> set(p, 1, v)),
			PropertyDef.text("Value", p -> getFrom(p, 2), (p, v) -> setFrom(p, 2, v))
		);
	}

	private static List<PropertyDef> udProperties()
	{
		return List.of(
			PropertyDef.readOnly("Command", _ -> "User Data"),
			PropertyDef.spinner("Field ID", 1, p -> get(p, 1), (p, v) -> set(p, 1, v)),
			PropertyDef.text("Value", p -> getFrom(p, 2), (p, v) -> setFrom(p, 2, v))
		);
	}

	private static List<PropertyDef> varProperties()
	{
		return List.of(
			PropertyDef.readOnly("Command", _ -> "Variable Formula"),
			PropertyDef.spinner("Variable ID", 1, p -> get(p, 1), (p, v) -> set(p, 1, v)),
			PropertyDef.text("Expression", p -> getFrom(p, 2), (p, v) -> setFrom(p, 2, v))
		);
	}

	private static List<PropertyDef> uvarProperties()
	{
		return List.of(
			PropertyDef.readOnly("Command", _ -> "User Variable"),
			PropertyDef.spinner("Variable ID", 1, p -> get(p, 1), (p, v) -> set(p, 1, v)),
			PropertyDef.text("Expression", p -> getFrom(p, 2), (p, v) -> setFrom(p, 2, v))
		);
	}

	private static List<PropertyDef> bdProperties()
	{
		return List.of(
			PropertyDef.readOnly("Command", _ -> "Block Data"),
			PropertyDef.spinner("Field ID", 1, p -> get(p, 1), (p, v) -> set(p, 1, v)),
			PropertyDef.text("Block Text", p -> getFrom(p, 2), (p, v) -> setFrom(p, 2, v))
		);
	}

	private static List<PropertyDef> queProperties()
	{
		return List.of(
			PropertyDef.readOnly("Command", _ -> "Operator Prompt"),
			PropertyDef.spinner("Field ID", 1, p -> get(p, 1), (p, v) -> set(p, 1, v)),
			PropertyDef.text("Prompt Text", p -> getFrom(p, 2), (p, v) -> setFrom(p, 2, v))
		);
	}

	private static List<PropertyDef> flProperties()
	{
		return List.of(
			PropertyDef.readOnly("Command", _ -> "Field Link"),
			PropertyDef.spinner("Source ID", 1, p -> get(p, 1), (p, v) -> set(p, 1, v)),
			PropertyDef.spinner("Element ID", 1, p -> get(p, 2), (p, v) -> set(p, 2, v))
		);
	}

	// =====================================================================
	// IMG — image path declaration: IMG,/c0/filename.ext
	// =====================================================================

	private static List<PropertyDef> imgProperties()
	{
		return List.of(
			PropertyDef.readOnly("Command", _ -> "Image Declaration"),
			PropertyDef.imagePicker("Image Path", p -> get(p, 1), (p, v) -> set(p, 1, v))
		);
	}

	// =====================================================================
	// Accessor helpers
	// =====================================================================

	/** Safe indexed get. */
	private static String get(String[] p, int i)
	{
		return i < p.length ? p[i].trim() : "";
	}

	/** Set a positional part, extending the array if needed. */
	private static String[] set(String[] p, int i, String v)
	{
		if (i >= p.length) {
			p = Arrays.copyOf(p, i + 1);
			Arrays.fill(p, p.length - (i + 1 - p.length), p.length, "");
		}
		p[i] = v;
		return p;
	}

	/**
	 * Get everything from index {@code from} onward, rejoined with commas.
	 * Used for FD/VAR values that may contain commas.
	 */
	private static String getFrom(String[] p, int from)
	{
		if (from >= p.length) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < p.length; i++) {
			if (i > from) sb.append(',');
			sb.append(p[i]);
		}
		return sb.toString();
	}

	/** Set everything from index {@code from} onward. The value may contain commas. */
	private static String[] setFrom(String[] p, int from, String v)
	{
		// Rebuild: keep parts[0..from-1], then append the new value as a single segment
		String[] head = Arrays.copyOf(p, from);
		String[] result = Arrays.copyOf(head, from + 1);
		result[from] = v;
		return result;
	}

	// --- Flagged-token helpers ---

	/**
	 * Find a token starting with the given prefix letter, searching from
	 * {@code flagStart} onward. Returns the value after the prefix, or "".
	 */
	private static String getToken(String[] p, int flagStart, char prefix)
	{
		for (int i = flagStart; i < p.length; i++) {
			String t = p[i].trim();
			if (t.length() > 0 && t.charAt(0) == prefix) {
				// For R and Z tokens, also check that the second char is a digit
				// to avoid matching "R" in other contexts
				if (prefix == 'R' || prefix == 'Z' || prefix == 'W' || prefix == 'H' || prefix == 'A') {
					if (t.length() > 1 && (Character.isDigit(t.charAt(1)) || t.charAt(1) == '-' || t.charAt(1) == '.'))
						return t.substring(1).split(",")[0]; // take first value for Z
				} else {
					return t.substring(1);
				}
			}
		}
		return "";
	}

	/** Patch or insert a flagged token. */
	private static String[] setToken(String[] p, int flagStart, char prefix, String value)
	{
		for (int i = flagStart; i < p.length; i++) {
			String t = p[i].trim();
			if (t.length() > 0 && t.charAt(0) == prefix) {
				if (prefix == 'R' || prefix == 'Z' || prefix == 'W' || prefix == 'H' || prefix == 'A') {
					if (t.length() > 1 && (Character.isDigit(t.charAt(1)) || t.charAt(1) == '-' || t.charAt(1) == '.')) {
						p[i] = String.valueOf(prefix) + value;
						return p;
					}
				} else {
					p[i] = String.valueOf(prefix) + value;
					return p;
				}
			}
		}
		// Not found — insert at flagStart
		List<String> list = new ArrayList<>(Arrays.asList(p));
		list.add(flagStart, String.valueOf(prefix) + value);
		return list.toArray(new String[0]);
	}

	// --- Zoom (T) — stored as "Zw,h" or "Zw" + bare "h" ---

	private static String getZoomW(String[] p, int flagStart)
	{
		for (int i = flagStart; i < p.length; i++) {
			String t = p[i].trim();
			if (t.startsWith("Z") && t.length() > 1 && Character.isDigit(t.charAt(1))) {
				return t.substring(1).split(",")[0];
			}
		}
		return "1";
	}

	private static String getZoomH(String[] p, int flagStart)
	{
		for (int i = flagStart; i < p.length; i++) {
			String t = p[i].trim();
			if (t.startsWith("Z") && t.length() > 1 && Character.isDigit(t.charAt(1))) {
				String[] zParts = t.substring(1).split(",");
				if (zParts.length > 1) return zParts[1];
				// Check next token for bare integer
				if (i + 1 < p.length) {
					String next = p[i + 1].trim();
					if (!next.isEmpty() && Character.isDigit(next.charAt(0)))
						return next;
				}
				return zParts[0]; // default: same as width
			}
		}
		return "1";
	}

	private static String[] setZoomW(String[] p, int flagStart, String v)
	{
		String h = getZoomH(p, flagStart);
		return setZoomBoth(p, flagStart, v, h);
	}

	private static String[] setZoomH(String[] p, int flagStart, String v)
	{
		String w = getZoomW(p, flagStart);
		return setZoomBoth(p, flagStart, w, v);
	}

	private static String[] setZoomBoth(String[] p, int flagStart, String w, String h)
	{
		for (int i = flagStart; i < p.length; i++) {
			String t = p[i].trim();
			if (t.startsWith("Z") && t.length() > 1 && Character.isDigit(t.charAt(1))) {
				p[i] = "Z" + w + "," + h;
				// Remove orphan bare-integer height if present
				if (!t.substring(1).contains(",") && i + 1 < p.length) {
					String next = p[i + 1].trim();
					if (!next.isEmpty() && Character.isDigit(next.charAt(0))) {
						List<String> list = new ArrayList<>(Arrays.asList(p));
						list.remove(i + 1);
						return list.toArray(new String[0]);
					}
				}
				return p;
			}
		}
		// Not found — insert
		List<String> list = new ArrayList<>(Arrays.asList(p));
		list.add(flagStart, "Z" + w + "," + h);
		return list.toArray(new String[0]);
	}

	// --- S element: variable flag start due to optional wmax ---

	private static int sFlagStart(String[] p)
	{
		if (p.length > 4 && startsWithNumeric(p[4].trim())) return 5;
		return 4;
	}

	private static String getSMaxWidth(String[] p)
	{
		if (p.length > 4 && startsWithNumeric(p[4].trim())) return p[4].trim();
		return "";
	}

	private static String[] setSMaxWidth(String[] p, String v)
	{
		if (p.length > 4 && startsWithNumeric(p[4].trim())) {
			p[4] = v.isEmpty() ? "0" : v;
		}
		return p;
	}

	/** Get a flagged token for S, searching from the correct flag start. */
	private static String getSToken(String[] p, char prefix)
	{
		return getToken(p, sFlagStart(p), prefix);
	}

	private static String[] setSToken(String[] p, char prefix, String value)
	{
		return setToken(p, sFlagStart(p), prefix, value);
	}

	/** Get/set for C element tokens starting at index 6. */
	private static String getCToken(String[] p, char prefix)
	{
		return getToken(p, 6, prefix);
	}

	private static String[] setCToken(String[] p, char prefix, String value)
	{
		return setToken(p, 6, prefix, value);
	}

	// --- G pen width/height (L token spans two comma-separated parts) ---

	private static String getGPenW(String[] p)
	{
		for (int i = 7; i < p.length; i++) {
			String t = p[i].trim();
			if (t.startsWith("L") && t.length() > 1 && Character.isDigit(t.charAt(1)))
				return t.substring(1);
		}
		return "1";
	}

	private static String getGPenH(String[] p)
	{
		for (int i = 7; i < p.length; i++) {
			String t = p[i].trim();
			if (t.startsWith("L") && t.length() > 1 && Character.isDigit(t.charAt(1))) {
				if (i + 1 < p.length) return p[i + 1].trim();
			}
		}
		return "1";
	}

	private static String[] setGPenW(String[] p, String v)
	{
		for (int i = 7; i < p.length; i++) {
			String t = p[i].trim();
			if (t.startsWith("L") && t.length() > 1 && Character.isDigit(t.charAt(1))) {
				p[i] = "L" + v;
				return p;
			}
		}
		return p;
	}

	private static String[] setGPenH(String[] p, String v)
	{
		for (int i = 7; i < p.length; i++) {
			String t = p[i].trim();
			if (t.startsWith("L") && t.length() > 1 && Character.isDigit(t.charAt(1))) {
				if (i + 1 < p.length) { p[i + 1] = v; return p; }
			}
		}
		return p;
	}

	// --- Alignment: read/write from the O-token flags ---

	private static String getHAlign(String[] p, int flagStart)
	{
		String opts = getToken(p, flagStart, 'O');
		if (opts.contains("C")) return "Centre";
		if (opts.contains("R")) return "Right";
		return "Left";
	}

	private static String[] setHAlign(String[] p, int flagStart, String v)
	{
		String opts = getToken(p, flagStart, 'O');
		// Remove old H-align flags
		opts = opts.replace("C", "").replace("R", "");
		// Add new
		if ("Centre".equals(v)) opts = "C" + opts;
		else if ("Right".equals(v)) opts = "R" + opts;
		return setOrInsertO(p, flagStart, opts);
	}

	private static String getVAlign(String[] p, int flagStart)
	{
		String opts = getToken(p, flagStart, 'O');
		if (opts.contains("M")) return "Middle";
		if (opts.contains("L")) return "Bottom";
		return "Top";
	}

	private static String[] setVAlign(String[] p, int flagStart, String v)
	{
		String opts = getToken(p, flagStart, 'O');
		opts = opts.replace("M", "").replace("L", "");
		if ("Middle".equals(v)) opts = opts + "M";
		else if ("Bottom".equals(v)) opts = opts + "L";
		return setOrInsertO(p, flagStart, opts);
	}

	/**
	 * Get misc flags (everything in the O-token EXCEPT alignment flags CRLM).
	 * Returns raw flag letters like "DU". Non-letter characters (e.g. numeric
	 * barcode rendering suffixes like the "0" in "OCL0") are excluded.
	 */
	private static String getMiscFlags(String[] p, int flagStart, String excludeChars)
	{
		String opts = getToken(p, flagStart, 'O');
		StringBuilder sb = new StringBuilder();
		for (char c : opts.toCharArray()) {
			if (Character.isLetter(c) && excludeChars.indexOf(c) < 0) sb.append(c);
		}
		return sb.toString();
	}

	/**
	 * Set misc flags, preserving alignment flags and non-letter characters
	 * (e.g. numeric barcode suffixes) already in the O-token.
	 */
	private static String[] setMiscFlags(String[] p, int flagStart, String alignChars, String newFlags)
	{
		String opts = getToken(p, flagStart, 'O');
		// Keep alignment chars and non-letter characters from the existing options
		StringBuilder sb = new StringBuilder();
		for (char c : opts.toCharArray()) {
			if (alignChars.indexOf(c) >= 0 || !Character.isLetter(c)) sb.append(c);
		}
		// Append new misc flags
		sb.append(newFlags);
		return setOrInsertO(p, flagStart, sb.toString());
	}

	/** Patch or insert the O-token. Remove it if empty. */
	private static String[] setOrInsertO(String[] p, int flagStart, String opts)
	{
		for (int i = flagStart; i < p.length; i++) {
			String t = p[i].trim();
			if (t.startsWith("O")) {
				if (opts.isEmpty()) {
					// Remove empty O token
					List<String> list = new ArrayList<>(Arrays.asList(p));
					list.remove(i);
					return list.toArray(new String[0]);
				}
				p[i] = "O" + opts;
				return p;
			}
		}
		// Not found — insert if non-empty
		if (!opts.isEmpty()) {
			List<String> list = new ArrayList<>(Arrays.asList(p));
			list.add(Math.min(flagStart, list.size()), "O" + opts);
			return list.toArray(new String[0]);
		}
		return p;
	}

	private static boolean startsWithNumeric(String s)
	{
		if (s == null || s.isEmpty()) return false;
		char c = s.charAt(0);
		return Character.isDigit(c) || c == '.' || c == '-';
	}
}
