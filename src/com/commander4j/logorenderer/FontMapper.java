package com.commander4j.logorenderer;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.font.FontRenderContext;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Maps LLF font names to Java AWT {@link Font} objects using dynamic
 * AffineTransform-based scaling so that each font fills its declared
 * reference pixel dimensions exactly.
 *
 * <h3>fonts.xml schema</h3>
 * Each {@code <font>} element contains:
 * <ul>
 *   <li>{@code <labelName>}   — font name as it appears in the LLF file</li>
 *   <li>{@code <family>}      — Java font family (system font) or the family
 *       name of the registered TTF</li>
 *   <li>{@code <style>}       — PLAIN | BOLD | ITALIC | BOLD_ITALIC</li>
 *   <li>{@code <refHeight>}   — reference character-cell height in printer dots</li>
 *   <li>{@code <refWidth>}    — reference character-cell width in printer dots</li>
 *   <li>{@code <filename>}    — optional TTF file name in the {@code fonts/} directory</li>
 *   <li>{@code <renderer>}    — SCALEABLE (tight glyph bounds) | BITMAP (loose FontMetrics)</li>
 *   <li>{@code <spacing>}     — PROPORTIONAL | MONOSPACED</li>
 *   <li>{@code <description>} — optional human-readable description</li>
 * </ul>
 *
 * <h3>Font scaling</h3>
 * When a caller requests a font at a given {@code heightPx × widthPx},
 * {@link #getScaledFont} derives the AWT font via {@link AffineTransform#getScaleInstance}
 * so that the rendered glyphs exactly fill the requested cell, using either
 * tight ink bounds (SCALEABLE) or loose FontMetrics bounds (BITMAP).
 * Scaled fonts are cached by {@code (fontName, heightPx, widthPx, whitespace)}.
 */
public class FontMapper
{

	private static final Logger LOG = Logger.getLogger(FontMapper.class.getName());

	/** Path to the font mapping file, relative to the working directory. */
	public static final String FONTS_XML_PATH = "xml/config/fonts.xml";

	/** Directory searched for TTF files, relative to the working directory. */
	public static final String FONTS_DIR = "fonts";

	/**
	 * Character set used when computing per-glyph ink-bound offsets.
	 * Covers the printable ASCII range plus common Latin-1 punctuation.
	 */
	private static final String DEFAULT_CHARSET =
		"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
		" !?.,;:'\"-_/\\()[]{}@#*$%&+^=<>";

	/** Parsed font entries keyed by labelName. */
	private final Map<String, FontProperties> entries   = new HashMap<>();

	/** Cached scaled fonts keyed by (fontName|heightPx|widthPx|whitespace). */
	private final Map<String, FontCache>      fontCache = new HashMap<>();

	/** Optional UI logger that mirrors font-mapping diagnostics into the LogPanel. */
	private ParseLogger uiLogger;

	/** Tracks font names already reported to the UI logger this render pass. */
	private final Set<String> loggedFonts = new HashSet<>();

	/** Buffer of XML-load diagnostics emitted before {@link #setLogger} is called. */
	private final List<String[]> bufferedMessages = new ArrayList<>();

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	public FontMapper()
	{
		loadFromXml(new File(FONTS_XML_PATH));
		if (entries.isEmpty())
		{
			logWarn("fonts.xml not loaded — using built-in defaults");
			loadDefaults();
		}
	}

	/** Alternative constructor for testing or non-standard working directories. */
	public FontMapper(File fontsXml)
	{
		loadFromXml(fontsXml);
		if (entries.isEmpty())
		{
			logWarn("fonts.xml not loaded from " + fontsXml + " — using built-in defaults");
			loadDefaults();
		}
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Returns the {@link FontProperties} for the given LLF font name.
	 * Falls back to the "default" entry, then to a minimal built-in fallback.
	 */
	public FontProperties getFontProperties(String fontName)
	{
		FontProperties fp = entries.get(fontName);
		boolean fallback = false;
		boolean builtIn  = false;
		if (fp == null)
		{
			fp = entries.get("default");
			fallback = true;
		}
		if (fp == null)
		{
			fp = new FontProperties();         // bare minimum fallback
			fp.family    = "SansSerif";
			fp.refHeight = "30";
			fp.refWidth  = "18";
			builtIn = true;
		}
		logFontResolution(fontName, fp, fallback, builtIn);
		return fp;
	}

	/**
	 * Emit a one-line resolution summary the first time a given font name is
	 * looked up during the current render pass. Subsequent lookups are silent.
	 */
	private void logFontResolution(String requested, FontProperties fp,
	                               boolean fallback, boolean builtIn)
	{
		if (!loggedFonts.add(requested))
			return;

		StringBuilder sb = new StringBuilder();
		sb.append("Font '").append(requested).append("' → ");
		if (builtIn)
			sb.append("(no fonts.xml entry, no 'default') ");
		else if (fallback)
			sb.append("(unmapped, using 'default') ");
		sb.append("family=").append(fp.family)
		  .append(" style=").append(fp.style.isEmpty() ? "PLAIN" : fp.style)
		  .append(" refH=").append(fp.refHeight)
		  .append(" refW=").append(fp.refWidth)
		  .append(" renderer=").append(fp.renderer.isEmpty() ? "SCALEABLE" : fp.renderer)
		  .append(" spacing=").append(fp.spacing.isEmpty() ? "PROPORTIONAL" : fp.spacing);
		if (fp.filename != null && !fp.filename.isEmpty())
			sb.append(" ttf=").append(fp.filename);

		if (fallback || builtIn)
			logWarn(sb.toString());
		else
			logInfo(sb.toString());
	}

	/**
	 * Attaches a UI logger that receives font-mapping diagnostics (XML load
	 * summary, missing TTFs, per-font resolution lines, fallback warnings).
	 * Any messages buffered before this call are replayed immediately.
	 */
	public void setLogger(ParseLogger logger)
	{
		this.uiLogger = logger;
		if (logger == null)
			return;
		for (String[] m : bufferedMessages)
		{
			switch (m[0])
			{
				case "INFO" -> logger.info(m[1]);
				case "WARN" -> logger.warn(m[1]);
				case "ERROR" -> logger.error(m[1]);
			}
		}
		bufferedMessages.clear();
	}

	/** Clears the per-render dedup set so the next pass re-reports each font. */
	public void resetLogState()
	{
		loggedFonts.clear();
	}

	private void logInfo(String msg)
	{
		LOG.info(msg);
		if (uiLogger != null) uiLogger.info(msg);
		else                  bufferedMessages.add(new String[] { "INFO", msg });
	}

	private void logWarn(String msg)
	{
		LOG.warning(msg);
		if (uiLogger != null) uiLogger.warn(msg);
		else                  bufferedMessages.add(new String[] { "WARN", msg });
	}

	/**
	 * Returns a {@link Font} scaled to fill exactly {@code heightPx × widthPx}
	 * pixels, using an {@link AffineTransform} applied to the base font.
	 *
	 * <p>Results are cached by {@code (fontName|heightPx|widthPx|whitespace|capHeight)}.</p>
	 *
	 * @param g         Graphics2D used for glyph measurement
	 * @param fontName  LLF font name
	 * @param heightPx  target cell height in pixels
	 * @param widthPx   target cell width in pixels (0 = maintain aspect ratio)
	 * @param whitespace true = loose FontMetrics (BITMAP mode);
	 *                   false = tight GlyphVector ink bounds (SCALEABLE mode)
	 * @param capHeight  true  = scale so the cap height of "H" equals heightPx
	 *                           (Logopak S element convention: H parameter = cap height);
	 *                   false = scale so the full "Hg" ink height equals heightPx
	 *                           (T element convention)
	 */
	public Font getScaledFont(Graphics2D g, String fontName,
	                          float heightPx, float widthPx, boolean whitespace,
	                          boolean capHeight)
	{
		String key = fontName + "|" + heightPx + "|" + widthPx + "|" + whitespace + "|" + capHeight;
		FontCache cached = fontCache.get(key);
		if (cached != null)
			return cached.font;

		FontProperties fp = getFontProperties(fontName);
		String family = fp.family.isEmpty() ? "SansSerif" : fp.family;
		int    style  = parseStyle(fp.style);

		Font scaled = createScaledFont(g, family, style, heightPx, widthPx, whitespace, capHeight);
		fontCache.put(key, new FontCache(scaled));
		return scaled;
	}

	/** Convenience overload — preserves existing T-element behaviour (capHeight = false). */
	public Font getScaledFont(Graphics2D g, String fontName,
	                          float heightPx, float widthPx, boolean whitespace)
	{
		return getScaledFont(g, fontName, heightPx, widthPx, whitespace, false);
	}

	/**
	 * Computes the pixel offsets needed so that
	 * {@code drawString(text, x + offsetLeftPx, y + offsetTopPx)} places the
	 * ink top-left at {@code (x, y)}.
	 *
	 * <p>Uses tight {@link GlyphVector#getVisualBounds()} across every character
	 * in {@code charset} (defaults to printable ASCII + common punctuation).</p>
	 */
	public FontOffsets computeTopLeftOffsets(Graphics2D g, Font font, String charset)
	{
		if (charset == null || charset.isEmpty())
			charset = DEFAULT_CHARSET;

		FontRenderContext frc = g.getFontRenderContext();
		double maxLeft = 0, maxTop = 0, maxRight = 0, maxBottom = 0;

		for (int i = 0; i < charset.length(); i++)
		{
			GlyphVector  gv  = font.createGlyphVector(frc, String.valueOf(charset.charAt(i)));
			Rectangle2D  vb  = gv.getVisualBounds();

			if (-vb.getMinX() > maxLeft)   maxLeft   = -vb.getMinX();
			if (-vb.getMinY() > maxTop)    maxTop    = -vb.getMinY();
			if ( vb.getMaxX() > maxRight)  maxRight  =  vb.getMaxX();
			if ( vb.getMaxY() > maxBottom) maxBottom =  vb.getMaxY();
		}

		return new FontOffsets(
			(int) Math.ceil(maxLeft),
			(int) Math.ceil(maxTop),
			(int) Math.ceil(maxRight),
			(int) Math.ceil(maxBottom)
		);
	}

	// -------------------------------------------------------------------------
	// XML loading
	// -------------------------------------------------------------------------

	private void loadFromXml(File file)
	{
		if (!file.exists())
		{
			logWarn("fonts.xml not found at: " + file.getAbsolutePath());
			return;
		}
		try
		{
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			List<String> available = Arrays.asList(ge.getAvailableFontFamilyNames());

			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(file);
			doc.getDocumentElement().normalize();

			NodeList nodes = doc.getElementsByTagName("font");
			for (int i = 0; i < nodes.getLength(); i++)
			{
				Element el = (Element) nodes.item(i);

				FontProperties fp = new FontProperties();
				fp.labelName   = text(el, "labelName");
				fp.family      = text(el, "family");
				fp.style       = text(el, "style");
				fp.refHeight   = text(el, "refHeight");
				fp.refWidth    = text(el, "refWidth");
				fp.filename    = text(el, "filename");
				fp.renderer    = text(el, "renderer");
				fp.spacing     = text(el, "spacing");
				fp.ascentFactor  = text(el, "ascentFactor");
				fp.descentFactor = text(el, "descentFactor");
				fp.description = text(el, "description");

				if (fp.labelName.isEmpty() || fp.family.isEmpty())
					continue;

				// Apply defaults for missing optional fields
				if (fp.style.isEmpty())     fp.style     = "PLAIN";
				if (fp.refHeight.isEmpty()) fp.refHeight = "30";
				if (fp.refWidth.isEmpty())  fp.refWidth  = "18";
				if (fp.renderer.isEmpty())  fp.renderer  = "SCALEABLE";
				if (fp.spacing.isEmpty())   fp.spacing   = "PROPORTIONAL";
				if (fp.ascentFactor.isEmpty())  fp.ascentFactor  = "1.0";
				if (fp.descentFactor.isEmpty()) fp.descentFactor = "0.25";

				// Load and register TTF if specified
				if (!fp.filename.isEmpty())
				{
					File ttf = new File(FONTS_DIR + File.separator + fp.filename);
					if (ttf.exists())
					{
						try
						{
							Font loaded = Font.createFont(Font.TRUETYPE_FONT, ttf);
							if (!available.contains(loaded.getFamily()))
								ge.registerFont(loaded);
							// Update family to the name AWT now knows
							fp.family = loaded.getFamily();
							logInfo("Registered TTF: " + fp.filename + " → " + fp.family);
						}
						catch (Exception ex)
						{
							logWarn("Could not load TTF " + fp.filename + ": " + ex.getMessage());
						}
					}
					else
					{
						logWarn("TTF file not found: " + ttf.getAbsolutePath());
					}
				}

				entries.put(fp.labelName, fp);
			}
			logInfo("Loaded " + entries.size() + " font mappings from " + file);
		}
		catch (Exception e)
		{
			logWarn("Failed to parse fonts.xml: " + e.getMessage());
		}
	}

	private void loadDefaults()
	{
		addDefault("swis721b", "Arial Narrow",  "BOLD",  "36", "21",
		           "ATTriumvirate-CondensedBold.ttf", "SCALEABLE", "PROPORTIONAL",
		           "1.29", "0.80",
		           "Swiss 721 Bold — primary scalable text font");
		addDefault("swis721r", "Arial",         "PLAIN", "36", "21",
		           "ATTriumvirate.ttf",                  "SCALEABLE", "PROPORTIONAL",
		           "1.29", "0.80",
		           "Swiss 721 Normal");
		addDefault("sw020bsn", "Arial",         "BOLD",  "20", "12",
		           "", "SCALEABLE", "PROPORTIONAL",
		           "1.29", "0.80",
		           "Swiss 020 Bold Proportional roman-8");
		addDefault("sw030rsn", "Arial",         "PLAIN", "30", "18",
		           "", "SCALEABLE", "PROPORTIONAL",
		           "1.29", "0.80",
		           "Swiss 030 Normal Proportional roman-8");
		addDefault("sw050bsn", "Arial",         "BOLD",  "50", "30",
		           "", "SCALEABLE", "PROPORTIONAL",
		           "1.29", "0.80",
		           "Swiss 050 Bold Proportional roman-8");
		addDefault("sw050rsn", "Arial",         "PLAIN", "50", "30",
		           "", "SCALEABLE", "PROPORTIONAL",
		           "1.29", "0.80",
		           "Swiss 050 Normal Proportional roman-8");
		addDefault("default",  "SansSerif",     "PLAIN", "30", "18",
		           "", "SCALEABLE", "PROPORTIONAL",
		           "1.0", "0.25",
		           "Fallback for any unmapped font name");
	}

	private void addDefault(String labelName, String family, String style,
	                        String refH, String refW, String filename,
	                        String renderer, String spacing,
	                        String ascentFactor, String descentFactor,
	                        String description)
	{
		FontProperties fp = new FontProperties();
		fp.labelName     = labelName;
		fp.family        = family;
		fp.style         = style;
		fp.refHeight     = refH;
		fp.refWidth      = refW;
		fp.filename      = filename;
		fp.renderer      = renderer;
		fp.spacing       = spacing;
		fp.ascentFactor  = ascentFactor;
		fp.descentFactor = descentFactor;
		fp.description   = description;
		entries.put(labelName, fp);
	}

	// -------------------------------------------------------------------------
	// Scaled font creation (approach ported from ZPLFont.createScaledFont)
	// -------------------------------------------------------------------------

	private Font createScaledFont(Graphics2D g, String fontName, int style,
	                              float heightPx, float widthPx, boolean whitespace,
	                              boolean capHeight)
	{
		if (whitespace)
		{
			// BITMAP / loose mode: use FontMetrics bounds (includes whitespace).
			// Binary-search downward from 72pt to find the largest size that fits
			// within the bounding box, then apply AffineTransform to reach exactly
			// the target dimensions.
			Font testFont = new Font(fontName, style, 72);
			int  w = 1, h = 1;
			FontMetrics fm;

			for (int size = 72; size >= 6; size--)
			{
				testFont = new Font(fontName, style, size);
				fm = g.getFontMetrics(testFont);
				w  = fm.getStringBounds("M", g).getBounds().width;
				h  = fm.getStringBounds("M", g).getBounds().height;
				if (w <= widthPx && h <= heightPx)
					break;
			}

			double scaleX = (widthPx  > 0) ? (double) widthPx  / Math.max(w, 1) : 1.0;
			double scaleY = (heightPx > 0) ? (double) heightPx / Math.max(h, 1) : 1.0;
			return testFont.deriveFont(AffineTransform.getScaleInstance(scaleX, scaleY));
		}
		else
		{
			Font             baseFont = new Font(fontName, style, Math.max(6, Math.round(heightPx)));
			FontRenderContext frc     = g.getFontRenderContext();

			double baseHeight;
			if (capHeight)
			{
				// S-element convention: H parameter = cap height (baseline → top of "H").
				// Scale so the ascending ink of a capital "H" equals heightPx.
				// Descenders are left at their natural proportion below the baseline.
				GlyphVector gvH = baseFont.createGlyphVector(frc, "H");
				Rectangle2D visH = gvH.getVisualBounds();
				baseHeight = Math.max(1e-6, -visH.getMinY()); // ascent above baseline
			}
			else
			{
				// T-element convention: scale so full "Hg" ink extent equals heightPx.
				GlyphVector gvHg  = baseFont.createGlyphVector(frc, "Hg");
				Rectangle2D visHg = gvHg.getVisualBounds();
				baseHeight = Math.max(1e-6, visHg.getHeight());
			}

			GlyphVector gvHg  = baseFont.createGlyphVector(frc, "Hg");
			Rectangle2D visHg = gvHg.getVisualBounds();
			double baseWidth  = Math.max(1e-6, visHg.getWidth());

			double scaleY = (heightPx > 0) ? heightPx / baseHeight : 1.0;
			double scaleX = (widthPx  > 0) ? widthPx  / baseWidth  : scaleY;

			return baseFont.deriveFont(AffineTransform.getScaleInstance(scaleX, scaleY));
		}
	}

	/** Backwards-compatible overload used by existing T-element calls. */
	@SuppressWarnings("unused")
	private Font createScaledFont(Graphics2D g, String fontName, int style,
	                              float heightPx, float widthPx, boolean whitespace)
	{
		return createScaledFont(g, fontName, style, heightPx, widthPx, whitespace, false);
	}

	// -------------------------------------------------------------------------
	// Dialog / persistence support
	// -------------------------------------------------------------------------

	/**
	 * Returns all entries sorted alphabetically, with "default" last.
	 * Each element is {@code String[11]}:
	 * {@code {labelName, family, style, refHeight, refWidth, filename,
	 *         renderer, spacing, ascentFactor, descentFactor, description}}.
	 */
	public List<String[]> getAllEntries()
	{
		List<String[]> result = new ArrayList<>();
		List<String>   keys   = new ArrayList<>(entries.keySet());
		keys.sort(Comparator.comparing(k -> k.equals("default") ? "\uFFFF" : k));
		for (String key : keys)
		{
			FontProperties fp = entries.get(key);
			result.add(new String[] {
				fp.labelName, fp.family, fp.style,
				fp.refHeight, fp.refWidth,
				fp.filename, fp.renderer, fp.spacing,
				fp.ascentFactor, fp.descentFactor,
				fp.description
			});
		}
		return result;
	}

	/**
	 * Saves the given rows to fonts.xml and clears the font cache.
	 * Each row is {@code String[11]}:
	 * {@code {labelName, family, style, refHeight, refWidth, filename,
	 *         renderer, spacing, ascentFactor, descentFactor, description}}.
	 */
	public void saveToXml(List<String[]> rows)
	{
		try
		{
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.newDocument();
			Element root = doc.createElement("fonts");
			doc.appendChild(root);

			for (String[] row : rows)
			{
				String name = get(row, 0, "").trim();
				if (name.isEmpty())
					continue;
				Element font = doc.createElement("font");
				addChild(doc, font, "labelName",     name);
				addChild(doc, font, "family",        get(row, 1, "SansSerif"));
				addChild(doc, font, "style",         get(row, 2, "PLAIN"));
				addChild(doc, font, "refHeight",     get(row, 3, "30"));
				addChild(doc, font, "refWidth",      get(row, 4, "18"));
				addChild(doc, font, "filename",      get(row, 5, ""));
				addChild(doc, font, "renderer",      get(row, 6, "SCALEABLE"));
				addChild(doc, font, "spacing",       get(row, 7, "PROPORTIONAL"));
				addChild(doc, font, "ascentFactor",  get(row, 8, "1.0"));
				addChild(doc, font, "descentFactor", get(row, 9, "0.25"));
				String desc = get(row, 10, "");
				if (!desc.isEmpty())
					addChild(doc, font, "description", desc);
				root.appendChild(font);
			}

			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer t = tf.newTransformer();
			t.setOutputProperty(OutputKeys.INDENT, "yes");
			t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			t.transform(new DOMSource(doc), new StreamResult(new File(FONTS_XML_PATH)));
			LOG.info("Saved " + rows.size() + " font mappings to " + FONTS_XML_PATH);
		}
		catch (Exception e)
		{
			LOG.warning("Failed to save fonts.xml: " + e.getMessage());
		}
	}

	/** Reloads font mappings from fonts.xml and clears the font cache. */
	public void reload()
	{
		entries.clear();
		fontCache.clear();
		loadFromXml(new File(FONTS_XML_PATH));
		if (entries.isEmpty())
			loadDefaults();
	}

	/**
	 * Ensures {@code fonts.xml} contains an entry for every name in
	 * {@code fontNames}. Any missing name is added with SansSerif family and
	 * metric-derived {@code ascentFactor} / {@code descentFactor} so text will
	 * render in the ballpark before the user fine-tunes against printed output.
	 *
	 * <p>If any entries were added, the on-disk {@code fonts.xml} is rewritten
	 * via {@link #saveToXml} so the seeds persist for the next session.</p>
	 *
	 * @return the set of font names that were newly added (empty if nothing
	 *         changed).
	 */
	public Set<String> addMissingFonts(Set<String> fontNames)
	{
		Set<String> added = new HashSet<>();
		if (fontNames == null || fontNames.isEmpty())
			return added;

		for (String name : fontNames)
		{
			if (name == null || name.isBlank())
				continue;
			if (entries.containsKey(name))
				continue;

			FontProperties fp = new FontProperties();
			fp.labelName     = name;
			fp.family        = "SansSerif";
			fp.style         = "PLAIN";
			fp.refHeight     = "30";
			fp.refWidth      = "18";
			fp.filename      = "";
			fp.renderer      = "SCALEABLE";
			fp.spacing       = "PROPORTIONAL";
			float[] factors  = deriveFactors(fp.family, Font.PLAIN, 30);
			fp.ascentFactor  = formatFactor(factors[0]);
			fp.descentFactor = formatFactor(factors[1]);
			fp.description   = "Auto-added for label — calibrate Asc/Desc against printed output";

			entries.put(name, fp);
			added.add(name);
			logInfo("Font mapping auto-added: " + name
					+ " (ascentFactor=" + fp.ascentFactor
					+ ", descentFactor=" + fp.descentFactor + ")");
		}

		if (!added.isEmpty())
			saveToXml(toRows());

		return added;
	}

	/** Build the 11-column row list used by {@link #saveToXml}. */
	private List<String[]> toRows()
	{
		return getAllEntries();
	}

	/**
	 * Derive a reasonable {@code (ascentFactor, descentFactor)} from Java
	 * {@link java.awt.font.LineMetrics} for the given family at the given size.
	 * Falls back to {@code (1.0, 0.25)} if anything goes wrong.
	 *
	 * <p>The factors are expressed as multiples of {@code heightPx} so they
	 * stay meaningful when the user edits {@code refHeight} later.</p>
	 */
	private float[] deriveFactors(String family, int style, int sizePx)
	{
		try
		{
			Font probe = new Font(family == null || family.isEmpty() ? "SansSerif" : family,
					style, Math.max(6, sizePx));
			Graphics2D g2 = scratchG2D();
			try
			{
				java.awt.font.LineMetrics lm = probe.getLineMetrics("Hgpj", g2.getFontRenderContext());
				float ascent  = lm.getAscent();
				float descent = lm.getDescent();
				float asc = ascent  / (float) sizePx;
				float dsc = descent / (float) sizePx;
				// Clamp to sensible bounds so a pathological font can't produce zero or huge cells.
				asc = Math.max(0.5f, Math.min(asc, 2.0f));
				dsc = Math.max(0.1f, Math.min(dsc, 1.5f));
				return new float[] { asc, dsc };
			}
			finally
			{
				g2.dispose();
			}
		}
		catch (Exception ignored)
		{
			return new float[] { 1.0f, 0.25f };
		}
	}

	private static String formatFactor(float f)
	{
		return String.format(java.util.Locale.ROOT, "%.3f", f);
	}

	/**
	 * Returns a minimal scratch {@link Graphics2D} suitable for off-screen font
	 * measurement when no real graphics context is available.
	 * Caller is responsible for disposing the returned context.
	 */
	public static Graphics2D scratchG2D()
	{
		BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		return img.createGraphics();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static String get(String[] row, int idx, String def)
	{
		return (row.length > idx && !row[idx].trim().isEmpty()) ? row[idx].trim() : def;
	}

	private static void addChild(Document doc, Element parent, String tag, String value)
	{
		Element el = doc.createElement(tag);
		el.setTextContent(value);
		parent.appendChild(el);
	}

	private static String text(Element parent, String tagName)
	{
		NodeList nl = parent.getElementsByTagName(tagName);
		if (nl.getLength() == 0)
			return "";
		return nl.item(0).getTextContent().trim();
	}

	private static int parseStyle(String s)
	{
		return switch (s.toUpperCase())
		{
		case "BOLD"        -> Font.BOLD;
		case "ITALIC"      -> Font.ITALIC;
		case "BOLD_ITALIC" -> Font.BOLD | Font.ITALIC;
		default            -> Font.PLAIN;
		};
	}
}
