package com.commander4j.logorenderer;

/**
 * C element — barcode field. Coordinates are in dots.
 *
 * Syntax: C,id,x,y,0,height,Ccodetype,Ooptions,Zzoom,barWidth[,S startChar,...]
 */
public class BarcodeElement extends LlfElement
{

	public enum CodeType
	{
		EAN128, CODE128, CODE39, UNKNOWN
	}

	private final int x;
	private final int y;
	private final int height; // in dots
	private final CodeType codeType;
	private final int zoom;
	private final int barWidth; // W parameter (narrow bar width multiplier)

	public BarcodeElement(int id, int x, int y, int height, CodeType codeType, String options, int zoom, int barWidth)
	{
		super(id, Type.BARCODE, options);
		this.x = x;
		this.y = y;
		this.height = height;
		this.codeType = codeType;
		this.zoom = zoom;
		this.barWidth = barWidth;
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public int getHeight()
	{
		return height;
	}

	public CodeType getCodeType()
	{
		return codeType;
	}

	public int getZoom()
	{
		return zoom;
	}

	public int getBarWidth()
	{
		return barWidth;
	}

	public static CodeType parseCodeType(String s)
	{
		if (s == null)
			return CodeType.UNKNOWN;
		return switch (s.toLowerCase())
		{
		case "ean128" -> CodeType.EAN128;
		case "code128" -> CodeType.CODE128;
		case "code39" -> CodeType.CODE39;
		default -> CodeType.UNKNOWN;
		};
	}
}
