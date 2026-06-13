package com.commander4j.logorenderer;

/**
 * T element — fixed-size text field. Coordinates are in dots (integer). Font
 * size is implicit, driven by zoom.
 *
 * Syntax: T,id,x,y,0,Ffontname,Ooptions,Rrotation,Zzoomw,zoomh
 *
 * The Z token carries two comma-separated values: width zoom and height zoom.
 * When only one value is present, both width and height use the same value.
 */
public class TextElement extends LlfElement
{

	private final int x;
	private final int y;
	private final int maxWidthDots; // 0 = no wrap check
	private final String fontName;
	private final int rotation; // 0,2,4,6,8 = 0,90,180,270,360(=0) degrees CCW
	private final int zoomWidth;
	private final int zoomHeight;
	private final BlockParams blockParams; // null if field is not block-data capable

	public TextElement(int id, int x, int y, int maxWidthDots, String fontName, String options, int rotation, int zoomWidth, int zoomHeight, BlockParams blockParams)
	{
		super(id, Type.TEXT, options);
		this.x = x;
		this.y = y;
		this.maxWidthDots = maxWidthDots;
		this.fontName = fontName;
		this.rotation = rotation;
		this.zoomWidth  = zoomWidth;
		this.zoomHeight = zoomHeight;
		this.blockParams = blockParams;
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public String getFontName()
	{
		return fontName;
	}

	/** Rotation in degrees counter-clockwise (0, 90, 180, 270). */
	public int getRotationDegrees()
	{
		return (rotation * 45) % 360;
	}

	/** Horizontal (width) zoom factor. */
	public int getZoomWidth()
	{
		return zoomWidth;
	}

	/** Vertical (height) zoom factor. */
	public int getZoomHeight()
	{
		return zoomHeight;
	}

	/** Convenience: returns height zoom (kept for compatibility). */
	public int getZoom()
	{
		return zoomHeight;
	}

	/** Max width in dots. 0 means no wrap / no length check. */
	public int getMaxWidthDots()
	{
		return maxWidthDots;
	}

	/** Block-data parameters, or null if this field is not block-data capable. */
	public BlockParams getBlockParams()
	{
		return blockParams;
	}
}
