package com.commander4j.logorenderer;

/**
 * L element — logo/image field. Coordinates are in dots. The image name refers
 * to a file declared via IMG.
 *
 * Syntax: L,id,x,y,Limagename,Ooptions,Rrotation,Zzoom,?
 */
public class ImageElement extends LlfElement
{

	private final int x;
	private final int y;
	private final String imageName; // logical image name (matches IMG
									// declaration)
	private final int rotation;
	private final int zoom;

	public ImageElement(int id, int x, int y, String imageName, String options, int rotation, int zoom)
	{
		super(id, Type.IMAGE, options);
		this.x = x;
		this.y = y;
		this.imageName = imageName;
		this.rotation = rotation;
		this.zoom = zoom;
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public String getImageName()
	{
		return imageName;
	}

	/** Rotation in degrees counter-clockwise. */
	public int getRotationDegrees()
	{
		return (rotation * 45) % 360;
	}

	public int getZoom()
	{
		return zoom;
	}
}
