package com.commander4j.logorenderer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses an LLF label layout file into an {@link LlfLayout} model.
 *
 * Each line in the LLF is a comma-separated record beginning with a record
 * type. Lines beginning with NOTE are informational and are mostly ignored
 * (except RESOLUTION which sets the canvas size).
 */
public class LlfParser
{

	private ParseLogger logger;

	/** Set a logger to receive diagnostic messages during parsing. */
	public void setLogger(ParseLogger logger)
	{
		this.logger = logger;
	}

	private void logInfo(String msg)
	{
		if (logger != null) logger.info(msg);
	}

	private void logWarn(String msg)
	{
		if (logger != null) logger.warn(msg);
	}

	@SuppressWarnings("unused")
	private void logError(String msg)
	{
		if (logger != null) logger.error(msg);
	}

	/**
	 * Dots per millimetre for the PowerLeap III print engine — X axis (across
	 * the web, parallel to the print head).
	 *
	 * <p>The PL3 head is marketed as "300 DPI" but the actual physical X
	 * resolution is 11.8 dots/mm (≈ 299.72 DPI). Confirmed against the
	 * LeapExpress layout properties dialog, which derives mm dimensions from
	 * dot dimensions using exactly 11.8 (e.g. 1896 dots → 160.67796 mm).</p>
	 */
	public static final double DOTS_PER_MM_X = 11.8;

	/**
	 * Dots per millimetre for the PowerLeap III print engine — Y axis (along
	 * the web, driven by the paper-advance stepper).
	 *
	 * <p>The Y axis is a stepper-driven paper advance and moves at exactly
	 * 12 dots/mm (304.8 DPI). The print dot geometry is therefore NOT square:
	 * X ≈ 11.8 dpmm, Y = 12 dpmm. S-element mm coordinates must be converted
	 * per-axis or long-string fields drift horizontally (X=12 case) or
	 * vertical positions drift downward (Y=11.8 case).</p>
	 */
	public static final double DOTS_PER_MM_Y = 12.0;

	/**
	 * Legacy symmetric alias — retained for callers that need a single "label
	 * resolution" value (e.g. status-bar mm display when averaging makes no
	 * sense). New code should use {@link #DOTS_PER_MM_X} or
	 * {@link #DOTS_PER_MM_Y} explicitly.
	 */
	public static final double DOTS_PER_MM = DOTS_PER_MM_X;

	/**
	 * Detect the charset declared in a LLF file's NOTE,CHARSET= line. Defaults
	 * to ISO-8859-1 if no UTF-8 declaration is found.
	 */
	public static Charset detectCharset(Path llfPath) throws IOException
	{
		try (BufferedReader probe = Files.newBufferedReader(llfPath, StandardCharsets.ISO_8859_1))
		{
			for (int i = 0; i < 10; i++)
			{
				String line = probe.readLine();
				if (line == null)
					break;
				if (line.startsWith("NOTE,CHARSET=UTF-8") || line.startsWith("NOTE,CHARSET=utf-8"))
				{
					return StandardCharsets.UTF_8;
				}
			}
		}
		return StandardCharsets.ISO_8859_1;
	}

	/**
	 * Parse an LLF file from a Path.
	 */
	public LlfLayout parse(Path llfPath) throws IOException
	{
		try (InputStream in = Files.newInputStream(llfPath))
		{
			return parse(in);
		}
	}

	/**
	 * Parse an LLF file from an InputStream (ISO-8859-1).
	 */
	public LlfLayout parse(InputStream in) throws IOException
	{
		return parse(readLines(in));
	}

	/**
	 * Parse from a pre-supplied list of raw lines. This is used by the
	 * LlfLinePanel to re-parse only the checked lines.
	 */
	public LlfLayout parse(List<String> lines)
	{
		LlfLayout layout = new LlfLayout();

		String pendingFontName = null; // FONT line sets this; next SPD line
										// consumes it
		int lineNum = 0;

		logInfo("--- Begin parsing (" + lines.size() + " lines) ---");

		for (String raw : lines)
		{
			lineNum++;
			String line = raw.stripLeading(); // preserve trailing spaces (they
												// can be part of FD/UD values)
			if (line.isEmpty())
				continue;

			String[] parts = splitLine(line);
			if (parts.length == 0)
				continue;
			String type = parts[0].toUpperCase();

			switch (type)
			{

			case "NOTE" -> {
				parseNote(layout, parts);
				if (parts.length >= 2 && parts[1].startsWith("RESOLUTION="))
					logInfo("NOTE: canvas resolution = " + parts[1].substring("RESOLUTION=".length())
							+ (parts.length >= 3 ? "x" + parts[2] : ""));
				else if (parts.length >= 2 && parts[1].startsWith("CHARSET="))
					logInfo("NOTE: charset = " + parts[1].substring("CHARSET=".length()));
				else if (parts.length >= 2 && parts[1].startsWith("LEAP_FILE_NAME="))
					logInfo("NOTE: file name = " + parts[1].substring("LEAP_FILE_NAME=".length()));
				else
					logInfo("NOTE: " + (parts.length >= 2 ? parts[1] : "(empty)"));
			}

			case "LAY" -> {
				parseLay(layout, parts);
				if (parts.length >= 6)
				{
					logInfo("LAY: layout bounds x=" + parts[2] + " y=" + parts[3]
							+ " w=" + parts[4] + " h=" + parts[5]
							+ (parts.length >= 7 ? " flags=" + parts[6] : ""));
					if (parts.length >= 7)
						logOptionFlags("LAY", parts[6]);
				}
			}

			case "FONT" -> {
				// FONT,fontname — registers a logical font name
				if (parts.length >= 2)
				{
					pendingFontName = parts[1];
					logInfo("FONT: declared font '" + parts[1] + "'");
				}
			}

			case "SPD" -> {
				// SPD,alias — physical alias for the most recent FONT
				if (parts.length >= 2 && pendingFontName != null)
				{
					layout.addFontAlias(pendingFontName, parts[1]);
					logInfo("SPD: font '" + pendingFontName + "' → alias '" + parts[1] + "'");
					pendingFontName = null;
				}
			}

			case "IMG" -> {
				// IMG,/c0/imagename.pcx
				if (parts.length >= 2)
				{
					String path = parts[1];
					String name = baseName(path);
					layout.addImagePath(name, path);
					logInfo("IMG: image '" + name + "' path=" + path);
				}
			}

			case "T" -> {
				parseTextElement(layout, parts);
				registerElementId(layout, parts);
				logTextElement(parts);
			}
			case "S" -> {
				parseScalableText(layout, parts);
				registerElementId(layout, parts);
				logScalableTextElement(parts);
			}
			case "L" -> {
				parseImageElement(layout, parts);
				registerElementId(layout, parts);
				logImageElement(parts);
			}
			case "G" -> {
				parseGraphicElement(layout, parts);
				registerElementId(layout, parts);
				logGraphicElement(parts);
			}
			case "C" -> {
				parseBarcodeElement(layout, parts);
				registerElementId(layout, parts);
				logBarcodeElement(parts);
			}

			case "FD", "FF", "QA" -> {
				// FD,id,value / FF,id,value / QA,id,value — FF skips FLP/FRP padding
				// (not implemented), so all three are identical for rendering
				if (parts.length >= 3)
				{
					layout.setField(parseInt(parts[1]), parts[2]);
					logInfo(type + ": field " + parts[1] + " = \"" + parts[2] + "\"");
				}
			}

			case "UD", "UF", "UQA" -> {
				// UD,id,value / UF,id,value / UQA,id,value — UF skips FLP/FRP padding
				// (not implemented), so all three are identical for rendering
				if (parts.length >= 3)
				{
					layout.setField(parseInt(parts[1]), parts[2]);
					logInfo(type + ": unicode field " + parts[1] + " = \"" + parts[2] + "\"");
				}
			}

			case "BD", "UBD" -> {
				// BD,id,value (ASCII) / UBD,id,value (Unicode) — multiline field
				// data. Storage is identical to FD/UD; word-wrap at render time
				// is driven by the target element's wmax + B-triple.
				if (parts.length >= 3)
				{
					layout.setField(parseInt(parts[1]), parts[2]);
					logInfo(type + ": block field " + parts[1] + " = \"" + parts[2] + "\"");
				}
			}

			case "UVAR", "VAR" -> {
				// UVAR/VAR,id,formula (LLF files use VAR; VAR and UVAR are
				// synonymous here)
				if (parts.length >= 3)
				{
					layout.addUvar(parseInt(parts[1]), parts[2]);
					logInfo(type + ": var " + parts[1] + " formula=\"" + parts[2] + "\"");
				}
			}

			case "FL" -> {
				// FL,sourceId,elementId
				if (parts.length >= 3)
				{
					layout.addFieldLink(parseInt(parts[1]), parseInt(parts[2]));
					logInfo("FL: field link source=" + parts[1] + " → element=" + parts[2]);
				}
			}

			case "QUE" -> {
				if (parts.length >= 2)
				{
					int id = parseInt(parts[1]);
					String promptText = parts.length >= 3 ? parts[2] : "";
					layout.addQuePrompt(id, promptText);
					logInfo("QUE: prompt id=" + id + " text=\"" + promptText + "\"");
				}
			}

			// ROTL, LAY-subcommands, counters etc. — ignore for rendering
			default -> {
				logWarn("Ignored: " + type + " (line " + lineNum + ": " + line + ")");
			}
			}
		}

		logInfo("--- Parse complete: " + layout.getElements().size() + " elements, "
				+ layout.getFields().size() + " fields, "
				+ layout.getUvars().size() + " vars, "
				+ layout.getFieldLinks().size() + " FL links ---");

		return layout;
	}

	// -------------------------------------------------------------------------
	// Record parsers
	// -------------------------------------------------------------------------

	private void parseNote(LlfLayout layout, String[] parts)
	{
		// NOTE,RESOLUTION=width,height
		if (parts.length >= 2 && parts[1].startsWith("RESOLUTION="))
		{
			String val = parts[1].substring("RESOLUTION=".length());
			// remaining parts may be split further
			// reconstruct: NOTE,RESOLUTION=1918,1180
			try
			{
				int w = Integer.parseInt(val);
				int h = (parts.length >= 3) ? parseInt(parts[2]) : 0;
				layout.setCanvasSize(w, h);
			}
			catch (NumberFormatException ignored)
			{
			}
		}
	}

	private void parseLay(LlfLayout layout, String[] parts)
	{
		// LAY,id,x,y,width,height,flags
		if (parts.length >= 6)
		{
			int x = parseInt(parts[2]);
			int y = parseInt(parts[3]);
			int w = parseInt(parts[4]);
			int h = parseInt(parts[5]);
			layout.setLayoutBounds(x, y, w, h);
		}
	}

	/**
	 * Register the element's ID as a runtime-targetable field, without
	 * disturbing any FD/UD value already loaded. Element lines come before
	 * FD lines in normal LQF files, so any later FD overwrites this stub.
	 */
	private void registerElementId(LlfLayout layout, String[] parts)
	{
		if (parts.length < 2) return;
		try { layout.registerField(Integer.parseInt(parts[1].trim())); }
		catch (NumberFormatException ignored) { /* malformed ID — parser already logged */ }
	}

	private void parseTextElement(LlfLayout layout, String[] parts)
	{
		// T,id,x,y,wmax,Ffontname,Ooptions,Rrender,Zw,h
		//     [,Bmaxlines,spacing,indent]
		if (parts.length < 5)
			return;
		int id = parseInt(parts[1]);
		int x = parseInt(parts[2]);
		int y = parseInt(parts[3]);
		int maxWidthDots = parseInt(parts[4]); // 0 = no wrap / no check

		String fontName = "";
		String options = "";
		int rotation = 0;
		int zoomWidth = 1;
		int zoomHeight = 1;
		BlockParams blockParams = null;

		for (int i = 5; i < parts.length; i++)
		{
			String p = parts[i];
			if (p.startsWith("F"))
				fontName = p.substring(1);
			else if (p.startsWith("O"))
				options = p.substring(1);
			else if (p.startsWith("R"))
				rotation = parseInt(p.substring(1));
			else if (isBlockTriple(p) && i + 2 < parts.length)
			{
				int maxLines = parseInt(p.substring(1));
				int spacing  = (int) Math.round(parseDouble(parts[i + 1]));
				int indent   = (int) Math.round(parseDouble(parts[i + 2]));
				blockParams = new BlockParams(maxLines, spacing, indent);
				i += 2;
			}
			else if (p.startsWith("Z"))
			{
				// Z token may encode "Zw,h" (both in one token) or just "Zw"
				// followed by a plain integer height token as the next part.
				String[] zParts = p.substring(1).split(",");
				zoomWidth  = parseInt(zParts[0]);
				if (zParts.length > 1)
				{
					zoomHeight = parseInt(zParts[1]);
				}
				else
				{
					// Height zoom may be the next comma-separated token (plain integer)
					if (i + 1 < parts.length)
					{
						String next = parts[i + 1].trim();
						if (!next.isEmpty() && Character.isDigit(next.charAt(0)))
						{
							zoomHeight = parseInt(next);
							i++; // consume the height token
						}
						else
						{
							zoomHeight = zoomWidth;
						}
					}
					else
					{
						zoomHeight = zoomWidth;
					}
				}
			}
		}

		layout.addElement(new TextElement(id, x, y, maxWidthDots, fontName, options, rotation, zoomWidth, zoomHeight, blockParams));
	}

	private void parseScalableText(LlfLayout layout, String[] parts)
	{
		// S,id,x,y[,wmax],Ffontname,Hheight,Aangle,Wwidth,Ooptions
		//                [,Bmaxlines,spacing,indent]
		// wmax at parts[4] is a bare number (digit or dot start); flagged tokens
		// begin with a letter. If parts[4] starts with a letter, wmax is absent.
		if (parts.length < 4)
			return;
		int id = parseInt(parts[1]);
		double x = parseDouble(parts[2]);
		double y = parseDouble(parts[3]);

		double maxWidthMm = 0;
		int flagStart = 4;
		if (parts.length > 4 && startsWithNumeric(parts[4]))
		{
			maxWidthMm = parseDouble(parts[4]);
			flagStart = 5;
		}

		String fontName = "";
		String options = "";
		double heightMm = 0;
		double angleDeg = 0;
		double widthMm = 0;
		BlockParams blockParams = null;

		for (int i = flagStart; i < parts.length; i++)
		{
			String p = parts[i];
			if (p.startsWith("F"))
				fontName = p.substring(1);
			else if (p.startsWith("H"))
				heightMm = parseDouble(p.substring(1));
			else if (p.startsWith("A"))
				angleDeg = parseDouble(p.substring(1));
			else if (p.startsWith("W"))
				widthMm = parseDouble(p.substring(1));
			else if (p.startsWith("O"))
				options = p.substring(1);
			else if (isBlockTriple(p) && i + 2 < parts.length)
			{
				int maxLines = parseInt(p.substring(1));
				int spacing  = (int) Math.round(parseDouble(parts[i + 1]));
				int indent   = (int) Math.round(parseDouble(parts[i + 2]));
				blockParams = new BlockParams(maxLines, spacing, indent);
				i += 2;
			}
		}

		layout.addElement(new ScalableTextElement(id, x, y, maxWidthMm, fontName, heightMm, angleDeg, widthMm, options, blockParams));
	}

	/**
	 * True if a flag token encodes a block-data triple start ("B" followed by
	 * digits, e.g. "B4"). Distinguishes from other future B-prefixed flags by
	 * requiring all chars after B to be digits.
	 */
	private static boolean isBlockTriple(String p)
	{
		if (p == null || p.length() < 2 || p.charAt(0) != 'B')
			return false;
		for (int i = 1; i < p.length(); i++)
			if (!Character.isDigit(p.charAt(i)))
				return false;
		return true;
	}

	/** True if the string starts with a digit or a decimal point. */
	private static boolean startsWithNumeric(String s)
	{
		if (s == null || s.isEmpty())
			return false;
		char c = s.charAt(0);
		return Character.isDigit(c) || c == '.' || c == '-';
	}

	private void parseImageElement(LlfLayout layout, String[] parts)
	{
		// L,id,x,y,Limagename,Ooptions,Rrotation,Zzoom,?
		if (parts.length < 5)
			return;
		int id = parseInt(parts[1]);
		int x = parseInt(parts[2]);
		int y = parseInt(parts[3]);
		String imageName = "";
		String options = "";
		int rotation = 0;
		int zoom = 1;

		for (int i = 4; i < parts.length; i++)
		{
			String p = parts[i];
			if (p.startsWith("L"))
				imageName = p.substring(1);
			else if (p.startsWith("O"))
				options = p.substring(1);
			else if (p.startsWith("R"))
				rotation = parseInt(p.substring(1));
			else if (p.startsWith("Z"))
				zoom = parseInt(p.substring(1).split(",")[0]);
		}

		layout.addElement(new ImageElement(id, x, y, imageName, options, rotation, zoom));
	}

	private void parseGraphicElement(LlfLayout layout, String[] parts)
	{
		// G,id,x1,y1,x2,y2,shape[,H help][,L penw,penh][,O options][,P
		// priority][,R render]
		//
		// The meaning of x2,y2 differs by shape:
		// LINE: x2,y2 are ABSOLUTE endpoint coordinates.
		// Stored as signed deltas: width = x2-x1, height = y2-y1.
		// drawGraphic() reconstructs both endpoints as (x,y) and (x+w, y+h).
		//
		// BOX: x2 is the BOX WIDTH and y2 is the BOX HEIGHT (not a second
		// corner).
		// The box extends LEFTWARD from x1 by width, and upward by height:
		// physical corners are (x1-width, y1) → (x1, y1+height).
		// Stored directly: width = x2, height = y2.
		// drawGraphic() uses (x - width, y) → (x, y + height).
		//
		// L is split across two comma-separated tokens: "L4" and "4" →
		// penWidth=4, penHeight=4.
		if (parts.length < 7)
			return;
		int id = parseInt(parts[1]);
		int x = parseInt(parts[2]);
		int y = parseInt(parts[3]);
		int x2 = parseInt(parts[4]);
		int y2 = parseInt(parts[5]);
		String shapeStr = parts[6];
		// Determine width/height storage based on shape
		GraphicElement.Shape shape = GraphicElement.parseShape(shapeStr);
		int width = (shape == GraphicElement.Shape.BOX) ? x2 : x2 - x;
		int height = (shape == GraphicElement.Shape.BOX) ? y2 : y2 - y;
		String options = "";
		int penWidth = 1;
		int penHeight = 1;
		int renderOption = 0;

		for (int i = 7; i < parts.length; i++)
		{
			String p = parts[i];
			if (p.startsWith("O"))
				options = p.substring(1);
			else if (p.startsWith("L"))
			{
				penWidth = parseInt(p.substring(1));
				if (i + 1 < parts.length)
					penHeight = parseInt(parts[++i]);
			}
			else if (p.startsWith("R"))
				renderOption = parseInt(p.substring(1));
		}

		layout.addElement(new GraphicElement(id, x, y, width, height, shape, options, penWidth, penHeight, renderOption));
	}

	private void parseBarcodeElement(LlfLayout layout, String[] parts)
	{
		// C,id,x,y,0,height,Ccodetype,Ooptions,Zzoom,barWidth[,S1,...]
		if (parts.length < 7)
			return;
		int id = parseInt(parts[1]);
		int x = parseInt(parts[2]);
		int y = parseInt(parts[3]);
		int height = parseInt(parts[5]);
		String codeStr = "";
		String options = "";
		int zoom = 1;
		int barWidth = 1;

		for (int i = 6; i < parts.length; i++)
		{
			String p = parts[i];
			if (p.startsWith("C"))
				codeStr = p.substring(1);
			else if (p.startsWith("O"))
				options = p.substring(1);
			else if (p.startsWith("Z"))
				zoom = parseInt(p.substring(1).split(",")[0]);
			else if (p.startsWith("W"))
				barWidth = parseInt(p.substring(1).split(",")[0]);
		}

		layout.addElement(new BarcodeElement(id, x, y, height, BarcodeElement.parseCodeType(codeStr), options, zoom, barWidth));
	}

	// -------------------------------------------------------------------------
	// Diagnostic logging helpers
	// -------------------------------------------------------------------------

	private void logOptionFlags(String context, String flagToken)
	{
		if (logger == null) return;
		String flags = flagToken.startsWith("O") ? flagToken.substring(1) : flagToken;
		if (flags.isEmpty()) return;
		StringBuilder sb = new StringBuilder("  Flags: ");
		for (char c : flags.toCharArray())
		{
			switch (c)
			{
			case 'D' -> sb.append("D=DISABLED ");
			case 'U' -> sb.append("U=UPSIDEDOWN ");
			case 'Q' -> sb.append("Q=UNICODE ");
			case 'C' -> sb.append("C=CENTRED ");
			case 'R' -> sb.append("R=RIGHT_ADJUST ");
			case 'L' -> sb.append("L=LOWER ");
			case 'M' -> sb.append("M=MIDDLE ");
			case 'O' -> sb.append("O=OUTDATA ");
			case 'F' -> sb.append("F=FORMULA ");
			default  -> sb.append(c + "=? ");
			}
		}
		logInfo(sb.toString().trim());
	}

	private void logTextElement(String[] parts)
	{
		if (logger == null || parts.length < 5) return;
		StringBuilder sb = new StringBuilder("T: text id=" + parts[1]
				+ " x=" + parts[2] + " y=" + parts[3]);
		for (int i = 5; i < parts.length; i++)
		{
			String p = parts[i];
			if (p.startsWith("F")) sb.append(" font=").append(p.substring(1));
			else if (p.startsWith("O")) sb.append(" opts=").append(p.substring(1));
			else if (p.startsWith("R")) sb.append(" rot=").append(p.substring(1));
			else if (p.startsWith("Z")) sb.append(" zoom=").append(p.substring(1));
			else if (p.startsWith("N")) sb.append(" maxlen=").append(p.substring(1));
			else if (p.startsWith("M")) sb.append(" minlen=").append(p.substring(1));
		}
		logInfo(sb.toString());
		for (int i = 5; i < parts.length; i++)
			if (parts[i].startsWith("O")) logOptionFlags("T", parts[i]);
	}

	private void logScalableTextElement(String[] parts)
	{
		if (logger == null || parts.length < 4) return;
		StringBuilder sb = new StringBuilder("S: scalable text id=" + parts[1]
				+ " x=" + parts[2] + "mm y=" + parts[3] + "mm");
		for (int i = 4; i < parts.length; i++)
		{
			String p = parts[i];
			if (p.startsWith("F")) sb.append(" font=").append(p.substring(1));
			else if (p.startsWith("H")) sb.append(" height=").append(p.substring(1)).append("mm");
			else if (p.startsWith("A")) sb.append(" angle=").append(p.substring(1)).append("°");
			else if (p.startsWith("W")) sb.append(" width=").append(p.substring(1)).append("%");
			else if (p.startsWith("O")) sb.append(" opts=").append(p.substring(1));
			else if (p.startsWith("N")) sb.append(" maxlen=").append(p.substring(1));
			else if (p.startsWith("M")) sb.append(" minlen=").append(p.substring(1));
		}
		logInfo(sb.toString());
		for (int i = 4; i < parts.length; i++)
			if (parts[i].startsWith("O")) logOptionFlags("S", parts[i]);
	}

	private void logImageElement(String[] parts)
	{
		if (logger == null || parts.length < 5) return;
		StringBuilder sb = new StringBuilder("L: image id=" + parts[1]
				+ " x=" + parts[2] + " y=" + parts[3]);
		for (int i = 4; i < parts.length; i++)
		{
			String p = parts[i];
			if (p.startsWith("L")) sb.append(" name=").append(p.substring(1));
			else if (p.startsWith("O")) sb.append(" opts=").append(p.substring(1));
			else if (p.startsWith("R")) sb.append(" rot=").append(p.substring(1));
			else if (p.startsWith("Z")) sb.append(" zoom=").append(p.substring(1));
		}
		logInfo(sb.toString());
	}

	private void logGraphicElement(String[] parts)
	{
		if (logger == null || parts.length < 7) return;
		logInfo("G: graphic id=" + parts[1] + " x1=" + parts[2] + " y1=" + parts[3]
				+ " x2=" + parts[4] + " y2=" + parts[5] + " shape=" + parts[6]);
	}

	private void logBarcodeElement(String[] parts)
	{
		if (logger == null || parts.length < 7) return;
		StringBuilder sb = new StringBuilder("C: barcode id=" + parts[1]
				+ " x=" + parts[2] + " y=" + parts[3] + " height=" + parts[5]);
		for (int i = 6; i < parts.length; i++)
		{
			String p = parts[i];
			if (p.startsWith("C")) sb.append(" type=").append(p.substring(1));
			else if (p.startsWith("O")) sb.append(" opts=").append(p.substring(1));
			else if (p.startsWith("Z")) sb.append(" zoom=").append(p.substring(1));
			else if (p.startsWith("W")) sb.append(" barWidth=").append(p.substring(1));
		}
		logInfo(sb.toString());
		for (int i = 6; i < parts.length; i++)
			if (parts[i].startsWith("O")) logOptionFlags("C", parts[i]);
	}

	// -------------------------------------------------------------------------
	// Utility
	// -------------------------------------------------------------------------

	private List<String> readLines(InputStream in) throws IOException
	{
		byte[] bytes = in.readAllBytes();

		// Detect charset from NOTE,CHARSET= declaration (always ASCII-safe to
		// scan as Latin-1).
		Charset charset = StandardCharsets.ISO_8859_1;
		try (BufferedReader probe = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.ISO_8859_1)))
		{
			for (int i = 0; i < 10; i++)
			{ // only scan the first 10 lines
				String line = probe.readLine();
				if (line == null)
					break;
				if (line.startsWith("NOTE,CHARSET=UTF-8") || line.startsWith("NOTE,CHARSET=utf-8"))
				{
					charset = StandardCharsets.UTF_8;
					break;
				}
			}
		}

		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), charset)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				lines.add(line);
			}
		}
		return lines;
	}

	/**
	 * Split a line on commas, but only for the first 2 tokens — the remainder
	 * is kept whole. This handles FD/UD values that may contain commas. For
	 * most records full splitting is fine; for FD/UD we need to be careful.
	 */
	private String[] splitLine(String line)
	{
		// For FD/UD lines the value may contain commas, so take only the first
		// two splits and treat the rest as the value.
		// FD, UD, and UVAR: only split on the first two commas; the remainder
		// is the value/formula
		// (UVAR formulas contain commas inside %(N,len,X)$ tokens)
		String upper = line.toUpperCase();
		if (upper.startsWith("FD,") || upper.startsWith("UD,") || upper.startsWith("BD,") || upper.startsWith("UBD,") || upper.startsWith("QA,") || upper.startsWith("UQA,") || upper.startsWith("UVAR,") || upper.startsWith("VAR,") || upper.startsWith("QUE,"))
		{
			int first = line.indexOf(',');
			int second = line.indexOf(',', first + 1);
			if (first >= 0 && second >= 0)
			{
				return new String[]
				{ line.substring(0, first), line.substring(first + 1, second), line.substring(second + 1) };
			}
		}
		// For NOTE,RESOLUTION=1918,1180 we need all parts
		return line.split(",");
	}

	private int parseInt(String s)
	{
		try
		{
			return Integer.parseInt(s.trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private double parseDouble(String s)
	{
		try
		{
			return Double.parseDouble(s.trim());
		}
		catch (NumberFormatException e)
		{
			return 0.0;
		}
	}

	/** Extract the base filename without extension from a path string. */
	private String baseName(String path)
	{
		String name = path;
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (slash >= 0)
			name = name.substring(slash + 1);
		int dot = name.lastIndexOf('.');
		if (dot > 0)
			name = name.substring(0, dot);
		return name;
	}
}
