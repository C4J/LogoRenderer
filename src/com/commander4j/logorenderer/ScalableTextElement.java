package com.commander4j.logorenderer;

/**
 * S element — scalable text field. Coordinates and dimensions are in
 * millimetres (float). The angle (A) is in degrees counter-clockwise.
 *
 * Syntax: S,id,x,y,Ffontname,Hheight,Aangle,Wwidth,Ooptions
 */
public class ScalableTextElement extends LlfElement
{

	private final double x; // mm
	private final double y; // mm
	private final double maxWidthMm; // 0 = no wrap check
	private final String fontName;
	private final double heightMm; // H parameter — rendered font height in mm
	private final double angleDeg; // A parameter — CCW degrees
	private final double widthRatio; // W parameter — character width as % of
										// normal (e.g. 78 = 78%)
	private final BlockParams blockParams; // null if field is not block-data capable

	public ScalableTextElement(int id, double x, double y, double maxWidthMm, String fontName, double heightMm, double angleDeg, double widthMm, String options, BlockParams blockParams)
	{
		super(id, Type.SCALABLE_TEXT, options);
		this.x = x;
		this.y = y;
		this.maxWidthMm = maxWidthMm;
		this.fontName = fontName;
		this.heightMm = heightMm;
		this.angleDeg = angleDeg;
		this.widthRatio = widthMm;
		this.blockParams = blockParams;
	}

	public double getX()
	{
		return x;
	}

	public double getY()
	{
		return y;
	}

	public String getFontName()
	{
		return fontName;
	}

	public double getHeightMm()
	{
		return heightMm;
	}

	public double getAngleDeg()
	{
		return angleDeg;
	}

	/** Width ratio as a fraction (e.g. 78 → 0.78). Returns 1.0 if not set. */
	public double getWidthRatio()
	{
		return widthRatio > 0 ? widthRatio / 100.0 : 1.0;
	}

	/** Max width in mm. 0 means no wrap / no length check. */
	public double getMaxWidthMm()
	{
		return maxWidthMm;
	}

	/** Block-data parameters, or null if this field is not block-data capable. */
	public BlockParams getBlockParams()
	{
		return blockParams;
	}
}
