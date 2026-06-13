package com.commander4j.logorenderer;

/**
 * G element — graphic (line or box). Coordinates and dimensions are in dots.
 *
 * Syntax: G,id,x,y,w,h,what[,H help][,L penw,penh][,O options][,P priority][,R
 * render] For LINE shapes w,h are the absolute x2,y2 endpoint coordinates.
 */
public class GraphicElement extends LlfElement
{

	public enum Shape
	{
		BOX, LINE, UNKNOWN
	}

	private final int x;
	private final int y;
	private final int width;
	private final int height;
	private final Shape shape;
	private final int penWidth; // L parameter (first value)
	private final int penHeight; // L parameter (second value)
	private final int renderOption; // R parameter

	public GraphicElement(int id, int x, int y, int width, int height, Shape shape, String options, int penWidth, int penHeight, int renderOption)
	{
		super(id, Type.GRAPHIC, options);
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.shape = shape;
		this.penWidth = penWidth;
		this.penHeight = penHeight;
		this.renderOption = renderOption;
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public int getWidth()
	{
		return width;
	}

	public int getHeight()
	{
		return height;
	}

	public Shape getShape()
	{
		return shape;
	}

	public int getPenWidth()
	{
		return penWidth;
	}

	public int getPenHeight()
	{
		return penHeight;
	}

	public int getRenderOption()
	{
		return renderOption;
	}

	public static Shape parseShape(String s)
	{
		if (s == null)
			return Shape.UNKNOWN;
		return switch (s.toLowerCase())
		{
		case "box" -> Shape.BOX;
		case "line" -> Shape.LINE;
		default -> Shape.UNKNOWN;
		};
	}
}
