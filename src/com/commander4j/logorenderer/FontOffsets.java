package com.commander4j.logorenderer;

/**
 * Pixel offsets computed from tight GlyphVector ink bounds.
 *
 * <p>When drawing text so that the ink top-left aligns with a target point
 * {@code (x, y)}, pass {@code (x + offsetLeftPx, y + offsetTopPx)} as the
 * baseline origin to {@link java.awt.Graphics2D#drawString}.</p>
 *
 * <p>Computed by {@link FontMapper#computeTopLeftOffsets}.</p>
 */
public class FontOffsets
{
	/** Pixels to add to x so that the leftmost ink aligns with the target x. */
	public final int offsetLeftPx;

	/** Pixels to add to y so that the topmost ink aligns with the target y (= visual ascent). */
	public final int offsetTopPx;

	/** Maximum ink extent to the right of the baseline origin (informational). */
	public final int rightPadPx;

	/** Maximum ink extent below the baseline (informational). */
	public final int bottomPadPx;

	public FontOffsets(int left, int top, int right, int bottom)
	{
		this.offsetLeftPx  = left;
		this.offsetTopPx   = top;
		this.rightPadPx    = right;
		this.bottomPadPx   = bottom;
	}
}
