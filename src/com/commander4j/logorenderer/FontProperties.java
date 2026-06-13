package com.commander4j.logorenderer;

/**
 * Holds the mapping properties for one LLF font name entry.
 * Stored in {@code xml/config/fonts.xml} and managed by {@link FontMapper}.
 */
public class FontProperties
{
	/** Font name as it appears in the LLF file (e.g. {@code sw050bsn}). */
	public String labelName   = "";

	/** Java AWT font family name (e.g. {@code Arial}, {@code Courier New}). */
	public String family      = "";

	/** AWT style string: PLAIN | BOLD | ITALIC | BOLD_ITALIC. */
	public String style       = "PLAIN";

	/** Reference character-cell height in printer dots (encoded in font name for fixed fonts). */
	public String refHeight   = "30";

	/** Reference character-cell width in printer dots. */
	public String refWidth    = "18";

	/**
	 * Optional TTF filename (relative to the {@code fonts/} directory).
	 * When non-empty, the font is loaded from disk and registered with the
	 * {@link java.awt.GraphicsEnvironment} at startup; {@link #family} is
	 * then updated to the registered family name.
	 * Leave empty to use {@link #family} as a system-installed font name.
	 */
	public String filename    = "";

	/**
	 * Renderer type: {@code SCALEABLE} (vector — uses tight GlyphVector ink bounds)
	 * or {@code BITMAP} (uses loose FontMetrics bounds).
	 */
	public String renderer    = "SCALEABLE";

	/** Spacing model: {@code PROPORTIONAL} or {@code MONOSPACED}. */
	public String spacing     = "PROPORTIONAL";

	/**
	 * Cell ascent as a multiple of {@link #refHeight}: the distance from the
	 * element's (x, y) top-left to the baseline, in refHeight units. Calibrated
	 * per-font against Leap Express design-mode reference renders. A typical
	 * value is ~1.3 (cell top sits above the cap by a typographic leading
	 * margin). Default 1.0 approximates "y is cap top".
	 */
	public String ascentFactor  = "1.0";

	/**
	 * Cell descent as a multiple of {@link #refHeight}: the distance from the
	 * baseline to the bottom of the cell, in refHeight units. A typical value
	 * is ~0.8. Default 0.25 approximates a conventional typographic descent.
	 */
	public String descentFactor = "0.25";

	/** Optional human-readable description for the mapping editor. */
	public String description = "";
}
