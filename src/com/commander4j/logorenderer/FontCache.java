package com.commander4j.logorenderer;

import java.awt.Font;

/**
 * Thin wrapper caching a scaled {@link Font} that has been derived via
 * {@link java.awt.geom.AffineTransform} to fill a specific pixel bounding box.
 */
public class FontCache
{
	public final Font font;

	public FontCache(Font font)
	{
		this.font = font;
	}
}
