package com.commander4j.logorenderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import uk.org.okapibarcode.backend.Code128;
import uk.org.okapibarcode.backend.Code3Of9;
import uk.org.okapibarcode.backend.HumanReadableLocation;
import uk.org.okapibarcode.backend.Symbol;
import uk.org.okapibarcode.output.Java2DRenderer;

/**
 * Renders an {@link LlfLayout} to a {@link BufferedImage}.
 *
 * <h3>Coordinate system</h3> In the LLF physical print space:
 * <ul>
 * <li>x = 0 at left edge, increases rightward across the printhead</li>
 * <li>y = 0 at the <em>bottom</em> of the label (trailing edge as it exits the
 * printer), increases upward</li>
 * </ul>
 * Elements carrying the {@code U} (UPSIDEDOWN) option flag are printed
 * upside-down in physical space, which means they appear correctly oriented
 * when the label is viewed after rotating 180°. The viewing transform for U
 * elements is:
 *
 * <pre>
 *   viewX = canvasW - physicalX
 *   viewY = canvasH - physicalY
 * </pre>
 *
 * Scalable-text elements (S) additionally carry an angle parameter {@code A};
 * combined with U the effective drawing angle is {@code (A + 180) % 360}, so
 * {@code A180 + U = 0°} (normal left-to-right text).
 */
public class LabelRenderer
{

	private static final Logger LOG = Logger.getLogger(LabelRenderer.class.getName());

	/**
	 * Dots per millimetre — per-axis because the PowerLeap III has non-square
	 * dot geometry: X ≈ 11.8 dpmm (head), Y = 12 dpmm (paper advance).
	 */
	static final double DOTS_PER_MM_X = LlfParser.DOTS_PER_MM_X;
	static final double DOTS_PER_MM_Y = LlfParser.DOTS_PER_MM_Y;

	// POINTS_PER_MM, BASE_FONT_PT and SCALABLE_FONT_SCALE removed.
	// Font sizing is now fully dynamic: FontMapper.getScaledFont() derives an
	// AffineTransform-scaled font from the refHeight/refWidth stored per font
	// in fonts.xml, so no hand-tuned point-size constants are required.

	private final FontMapper fontMapper = new FontMapper();

	/**
	 * Dots per millimetre for S (scalable-text) elements. Derived from the
	 * NOTE,RESOLUTION height field: S_dpmm = NOTE_H / 100.0. Falls back to
	 * DPMM_X if the layout carries no NOTE line.
	 */
	private double sDpmm = DOTS_PER_MM_X;

	/** Optional UI logger; receives font-mapping diagnostics during render. */
	public void setLogger(ParseLogger logger)
	{
		fontMapper.setLogger(logger);
	}

	/** Directory searched for image files (PCX, PNG, JPG). */
	private Path imageSearchPath;

	private final Map<String, BufferedImage> imageCache = new HashMap<>();

	/**
	 * Per-element axis-aligned bounding boxes in image (dot) coordinates,
	 * populated during {@link #render(LlfLayout)}. Used by the UI for click-
	 * to-select hit testing on the canvas.
	 */
	private final Map<Integer, Rectangle> hitBoxes = new HashMap<>();

	public void setImageSearchPath(Path path)
	{
		this.imageSearchPath = path;
	}

	/** Hit boxes from the most recent {@link #render(LlfLayout)}, id → rect. */
	public Map<Integer, Rectangle> getHitBoxes()
	{
		return Collections.unmodifiableMap(hitBoxes);
	}

	private void recordHitBox(int id, int x, int y, int w, int h)
	{
		if (w < 6) { x -= 3; w = 6; }
		if (h < 6) { y -= 3; h = 6; }
		hitBoxes.put(id, new Rectangle(x, y, w, h));
	}

	// -------------------------------------------------------------------------
	// Public render entry point
	// -------------------------------------------------------------------------

	public BufferedImage render(LlfLayout layout)
	{

		fontMapper.resetLogState();
		resolveData(layout);
		hitBoxes.clear();

		// S element resolution: NOTE,RESOLUTION=W,H encodes the S dpmm as H/100.
		// E.g. NOTE_H=800 → S_dpmm=8.0 (pal8010 labels); NOTE_H=1180 → 11.8.
		sDpmm = layout.getCanvasHeight() > 0 ? layout.getCanvasHeight() / 100.0 : DOTS_PER_MM_X;

		int canvasW = layout.getLayoutWidth();
		int canvasH = layout.getLayoutHeight();
		if (canvasW <= 0)
			canvasW = layout.getCanvasWidth();
		if (canvasH <= 0)
			canvasH = layout.getCanvasHeight();

		BufferedImage img = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		g.setColor(Color.WHITE);
		g.fillRect(0, 0, canvasW, canvasH);
		g.setColor(Color.BLACK);

		for (LlfElement element : layout.getElements())
		{
			if (element.isDisabled())
				continue;
			try
			{
				drawElement(g, element, canvasW, canvasH);
			}
			catch (Exception e)
			{
				LOG.warning("Element " + element.getId() + ": " + e.getMessage());
			}
		}

		g.dispose();
		return img;
	}

	// -------------------------------------------------------------------------
	// Data resolution
	// -------------------------------------------------------------------------

	private void resolveData(LlfLayout layout)
	{
		// Cascades nested VARs and stores each result in layout.fields[varId],
		// so %NF tokens referencing other VAR outputs resolve correctly.
		new UvarEvaluator().resolveAll(layout);

		for (LlfElement element : layout.getElements())
		{
			int sourceId = layout.getSourceForElement(element.getId());
			if (sourceId < 0)
			{
				element.setData(layout.getField(element.getId()));
			}
			else
			{
				element.setData(layout.getField(sourceId));
			}
		}
	}

	// -------------------------------------------------------------------------
	// Dispatch
	// -------------------------------------------------------------------------

	private void drawElement(Graphics2D g, LlfElement el, int cW, int cH)
	{
		switch (el.getType())
		{
		case TEXT -> drawText((TextElement) el, g, cW, cH);
		case SCALABLE_TEXT -> drawScalableText((ScalableTextElement) el, g, cW, cH);
		case IMAGE -> drawImage((ImageElement) el, g, cW, cH);
		case GRAPHIC -> drawGraphic((GraphicElement) el, g, cW, cH);
		case BARCODE -> drawBarcode((BarcodeElement) el, g, cW, cH);
		}
	}

	// -------------------------------------------------------------------------
	// Coordinate helpers
	// -------------------------------------------------------------------------

	/** Convert a physical x to viewing x (U-flagged elements only). */
	private int vx(int physX, int cW)
	{
		return cW - physX;
	}

	/** Convert a physical y to viewing y (U-flagged elements only). */
	private int vy(int physY, int cH)
	{
		return cH - physY;
	}

	// -------------------------------------------------------------------------
	// TEXT (T) — dot coordinates
	// -------------------------------------------------------------------------

	private void drawText(TextElement e, Graphics2D g, int cW, int cH)
	{
		if (e.getData().isEmpty())
			return;

		FontProperties fp     = fontMapper.getFontProperties(e.getFontName());
		int            refH   = parseIntSafe(fp.refHeight, 30);
		int            refW   = parseIntSafe(fp.refWidth,  18);
		float          heightPx = refH * e.getZoomHeight();
		float          widthPx;
		if ("MONOSPACED".equals(fp.spacing))
			widthPx = refW * e.getZoomWidth();
		else
			widthPx = (refH > 0) ? (refH * e.getZoomWidth()) * refW / (float) refH : heightPx * 0.6f;

		boolean whitespace = "BITMAP".equals(fp.renderer);
		Font font = fontMapper.getScaledFont(g, e.getFontName(), heightPx, widthPx, whitespace);

		// FontOffsets (tight visual bounds) used only for horizontal ink alignment.
		FontOffsets offsets = fontMapper.computeTopLeftOffsets(g, font, null);
		FontMetrics fm      = g.getFontMetrics(font);

		// Cell dimensions per Leap Express design convention: the element's y
		// coordinate is the TOP of a typographic cell whose ascent and descent
		// are each a per-font multiple of refHeight (calibrated in fonts.xml).
		float ascentFactor  = parseFloatSafe(fp.ascentFactor,  1.0f);
		float descentFactor = parseFloatSafe(fp.descentFactor, 0.25f);
		int   cellAscent    = Math.round(heightPx * ascentFactor);
		int   cellDescent   = Math.round(heightPx * descentFactor);

		int drawX = e.isUpsideDown() ? vx(e.getX(), cW) : e.getX();
		int drawY = e.isUpsideDown() ? vy(e.getY(), cH) : e.getY();

		// Vertical anchor reference points (Leap Express design convention):
		//   default (no flag) = TOP of character cell (y + cellAscent = baseline)
		//   M (middle)        = vertical midpoint of cell
		//   L (lower)         = BASELINE (descenders extend below y)
		if (e.isLower())
			drawY -= cellAscent;
		else if (e.isMiddle())
			drawY -= (cellAscent + cellDescent) / 2;

		AffineTransform saved = g.getTransform();
		g.translate(drawX, drawY);

		int deg = e.getRotationDegrees();
		if (deg != 0)
			g.rotate(Math.toRadians(-deg)); // negative = CW in screen space

		g.setFont(font);
		g.setColor(Color.BLACK);

		BlockParams bp = e.getBlockParams();
		int wmax = e.getMaxWidthDots();
		if (bp != null && wmax > 0 && fm.stringWidth(e.getData()) > wmax)
		{
			List<String> lines = wrapWords(e.getData(), fm, wmax, bp.maxLines());
			// Baseline-to-baseline step: the design-grid line height (refH × zoom)
			// plus the B-triple lineSpacing offset. Using cellAscent+cellDescent
			// here would double-space because the Leap Express typographic cell
			// is ~2× the grid row.
			int lineStepPx = Math.round(heightPx) + bp.lineSpacingDots();
			for (int i = 0; i < lines.size(); i++)
			{
				String line = lines.get(i);
				int lineW = fm.stringWidth(line);
				int alignX = e.isCentered()    ? -lineW / 2
				           : e.isRightAdjust() ? -lineW
				           : 0;
				int indentX = (i > 0) ? bp.indentDots() : 0;
				int y = cellAscent + i * lineStepPx;
				g.drawString(line, alignX + offsets.offsetLeftPx + indentX, y);
			}
		}
		else
		{
			int textW = fm.stringWidth(e.getData());
			int alignX = e.isCentered()    ? -textW / 2
			           : e.isRightAdjust() ? -textW
			           : 0;
			g.drawString(e.getData(), alignX + offsets.offsetLeftPx, cellAscent);
			recordTextBounds(e.getId(), drawX + alignX, drawY, textW, cellAscent + cellDescent, deg);
		}
		g.setTransform(saved);
		if (bp != null && wmax > 0 && fm.stringWidth(e.getData()) > wmax)
		{
			// Block-wrap branch: approximate bounds are the max-width box.
			recordTextBounds(e.getId(), drawX, drawY, wmax, cellAscent + cellDescent, deg);
		}
	}

	/**
	 * Record a hit box for a rotated text element. At 0°/180° the box is the
	 * natural (w × h) rect anchored at (x, y). At 90°/270° we swap w/h. Other
	 * angles fall back to an enclosing square of side max(w, h) — good enough
	 * for mouse hit testing without full rotated-rect math.
	 */
	private void recordTextBounds(int id, int x, int y, int w, int h, int rotDeg)
	{
		int norm = ((rotDeg % 360) + 360) % 360;
		if (norm == 0 || norm == 180)
			recordHitBox(id, x, y, w, h);
		else if (norm == 90 || norm == 270)
			recordHitBox(id, x - h / 2, y - w / 2 + h / 2, h, w);
		else
		{
			int side = Math.max(w, h);
			recordHitBox(id, x - (side - w) / 2, y - (side - h) / 2, side, side);
		}
	}

	/**
	 * Greedy word-wrap on whitespace, capped at {@code maxLines} lines.
	 * Oversized single words (longer than maxWidthPx) are broken at character
	 * boundaries so they never bleed past the field edge. Lines beyond
	 * maxLines are silently dropped — matching the PL3 block-data behaviour
	 * where the B-triple's maxLines is an upper bound on the visible block.
	 */
	private static List<String> wrapWords(String text, FontMetrics fm, int maxWidthPx, int maxLines)
	{
		List<String> out = new ArrayList<>();
		if (text == null || text.isEmpty())
		{
			out.add("");
			return out;
		}
		if (maxWidthPx <= 0)
		{
			out.add(text);
			return out;
		}

		String[] words = text.split(" ", -1);
		StringBuilder current = new StringBuilder();

		for (String word : words)
		{
			if (maxLines > 0 && out.size() >= maxLines)
				return out;

			String candidate = current.length() == 0 ? word : current + " " + word;
			if (fm.stringWidth(candidate) <= maxWidthPx)
			{
				current.setLength(0);
				current.append(candidate);
				continue;
			}

			// Candidate overflows. Flush what we have.
			if (current.length() > 0)
			{
				out.add(current.toString());
				current.setLength(0);
				if (maxLines > 0 && out.size() >= maxLines)
					return out;
			}

			// Does the word itself fit on an empty line?
			if (fm.stringWidth(word) <= maxWidthPx)
			{
				current.append(word);
			}
			else
			{
				// Single word exceeds wmax — break at character boundary so it
				// stays within the field. Emit completed fragments as their own
				// lines; the trailing fragment becomes the new current line.
				StringBuilder frag = new StringBuilder();
				for (int ci = 0; ci < word.length(); ci++)
				{
					char c = word.charAt(ci);
					frag.append(c);
					if (fm.stringWidth(frag.toString()) > maxWidthPx)
					{
						frag.setLength(frag.length() - 1);
						if (frag.length() > 0)
						{
							out.add(frag.toString());
							if (maxLines > 0 && out.size() >= maxLines)
								return out;
						}
						frag.setLength(0);
						frag.append(c);
					}
				}
				current.append(frag);
			}
		}

		if (current.length() > 0 && (maxLines <= 0 || out.size() < maxLines))
			out.add(current.toString());

		return out;
	}

	private static float parseFloatSafe(String s, float def)
	{
		if (s == null || s.isBlank())
			return def;
		try
		{
			return Float.parseFloat(s.trim());
		}
		catch (NumberFormatException ignored)
		{
			return def;
		}
	}

	// -------------------------------------------------------------------------
	// SCALABLE TEXT (S) — mm coordinates, angle in degrees CCW
	// -------------------------------------------------------------------------

	private void drawScalableText(ScalableTextElement e, Graphics2D g, int cW, int cH)
	{
		if (e.getData().isEmpty())
			return;

		int xDots = (int) Math.round(e.getX() * sDpmm);
		int yDots = (int) Math.round(e.getY() * sDpmm);

		// Determine effective draw position and angle.
		// A180 (with or without U): both axes mirrored, effective angle = 0°.
		// Any other angle: raw coordinates, raw angle.
		int    drawX;
		int    drawY;
		double angleDeg;
		if (Math.abs(e.getAngleDeg() - 180.0) < 0.01)
		{
			drawX    = cW - xDots;
			drawY    = cH - yDots;
			angleDeg = 0.0;
		}
		else
		{
			drawX    = xDots;
			drawY    = yDots;
			angleDeg = e.getAngleDeg();
		}

		// Derive target pixel height from the S element's H parameter (mm).
		// The width ratio (W parameter) is baked into widthPx so that the
		// AffineTransform handles the horizontal squeeze — no separate g.scale()
		// step is needed.
		FontProperties fp      = fontMapper.getFontProperties(e.getFontName());
		int            refH    = parseIntSafe(fp.refHeight, 30);
		int            refW    = parseIntSafe(fp.refWidth,  18);
		// Height in mm → dots using the S-element resolution (NOTE_H / 100).
		float          heightPx = (float) (e.getHeightMm() * sDpmm);
		float          aspectRatio = (refH > 0) ? (float) refW / refH : 0.6f;
		float          widthPx    = heightPx * aspectRatio * (float) e.getWidthRatio();

		// Vertical anchor: L = y is bottom of field, M = y is vertical midpoint.
		// The S-element "cell" spans heightPx * (ascentFactor + descentFactor)
		// so per-font ascent/descent factors in fonts.xml let the user tune
		// anchor position without editing the source LLF. For swis721b with
		// ascentFactor=1, descentFactor=0, these reduce to heightPx / heightPx/2
		// (matching the prior hard-coded behaviour).
		float sAscentFactor  = parseFloatSafe(fp.ascentFactor,  1.0f);
		float sDescentFactor = parseFloatSafe(fp.descentFactor, 0.0f);
		int   sCellAscent    = Math.round(heightPx * sAscentFactor);
		int   sCellDescent   = Math.round(heightPx * sDescentFactor);
		if (e.isLower())
			drawY -= sCellAscent;
		else if (e.isMiddle())
			drawY -= (sCellAscent + sCellDescent) / 2;

		boolean whitespace = "BITMAP".equals(fp.renderer);
		// S elements: the H parameter is the FIELD (em-box) height in mm, not the
		// cap height. The design-mode editor shows the text anchored to the top-left
		// of a box H mm tall — cap letters fill ~70% of that, leaving the ascent gap
		// above and the descender room below. Using full-Hg ink scaling (capHeight=false)
		// reproduces this: the font is sized so its baseline-to-top + descender extent
		// equals H mm, and cap height naturally comes out at the font's intrinsic ratio.
		Font font = fontMapper.getScaledFont(g, e.getFontName(), heightPx, widthPx, whitespace, false);

		// Compute tight ink offsets for horizontal anchoring (offsetLeftPx).
		// Vertical anchor is the line-box top: per the LLF spec the (x,y) of an
		// S element is the upper-left corner of the FIELD (full em/line box),
		// not the cap top. Using FontMetrics.getAscent() places the baseline
		// (ascent) below drawY, so the line-box top sits exactly at drawY.
		FontOffsets offsets = fontMapper.computeTopLeftOffsets(g, font, null);

		// Horizontal alignment: C = centred around drawX, R = right-edge at drawX.
		// FontMetrics.stringWidth() accounts for the AffineTransform-scaled advance.
		g.setFont(font);
		FontMetrics fm    = g.getFontMetrics(font);
		int         textW = fm.stringWidth(e.getData());
		int startX = e.isCentered()    ? drawX - textW / 2
		           : e.isRightAdjust() ? drawX - textW
		           : drawX;

		AffineTransform saved = g.getTransform();
		g.translate(startX, drawY);

		if (angleDeg != 0.0)
			g.rotate(Math.toRadians(-angleDeg)); // positive CCW physical = CW screen (y-down)

		// Baseline positioning: use GlyphVector visual ascent so the visible
		// ink top aligns with drawY (the field's top-left in screen space).
		// LineMetrics.getAscent() includes font-internal leading above the
		// visible caps, which pushes text down by an amount proportional to
		// font height — causing progressive misalignment on tightly-packed
		// labels.
		java.awt.font.GlyphVector gvHg = font.createGlyphVector(g.getFontRenderContext(), "Hg");
		java.awt.geom.Rectangle2D visHg = gvHg.getVisualBounds();
		int baselineY = (int) Math.round(-visHg.getMinY());

		g.setColor(Color.BLACK);

		BlockParams bp = e.getBlockParams();
		int wmaxPx = (int) Math.round(e.getMaxWidthMm() * sDpmm);
		if (bp != null && wmaxPx > 0 && fm.stringWidth(e.getData()) > wmaxPx)
		{
			// For S elements the "cell height" is the H parameter expressed in
			// Y-axis dots (paper-advance). Line spacing from the B-triple is
			// added on top.
			List<String> lines = wrapWords(e.getData(), fm, wmaxPx, bp.maxLines());
			int lineStepPx = (int) Math.round(e.getHeightMm() * sDpmm) + bp.lineSpacingDots();

			// startX was computed for the full-width single-line case and
			// applied via translate(startX, drawY). For wrapped lines we need
			// per-line horizontal alignment, so undo that single-line shift
			// by subtracting (startX - drawX) from each line's x offset.
			int singleLineShift = startX - drawX;

			for (int i = 0; i < lines.size(); i++)
			{
				String line = lines.get(i);
				int lineW = fm.stringWidth(line);
				int lineAlignShift = e.isCentered()    ? -lineW / 2
				                   : e.isRightAdjust() ? -lineW
				                   : 0;
				int indentX = (i > 0) ? bp.indentDots() : 0;
				int x = offsets.offsetLeftPx + (lineAlignShift - singleLineShift) + indentX;
				int y = baselineY + i * lineStepPx;
				g.drawString(line, x, y);
			}
		}
		else
		{
			g.drawString(e.getData(), offsets.offsetLeftPx, baselineY);
		}
		g.setTransform(saved);

		int boundsW = (bp != null && wmaxPx > 0 && fm.stringWidth(e.getData()) > wmaxPx) ? wmaxPx : textW;
		int boundsH = sCellAscent + sCellDescent;
		recordTextBounds(e.getId(), startX, drawY, boundsW, boundsH, (int) Math.round(angleDeg));
	}

	private static int parseIntSafe(String s, int def)
	{
		if (s == null || s.isBlank())
			return def;
		try
		{
			return Integer.parseInt(s.trim());
		}
		catch (NumberFormatException ignored)
		{
			return def;
		}
	}

	// -------------------------------------------------------------------------
	// IMAGE (L) — dot coordinates
	// -------------------------------------------------------------------------

	private void drawImage(ImageElement e, Graphics2D g, int cW, int cH)
	{
		BufferedImage img = loadImage(e.getImageName());
		if (img == null)
		{
			LOG.warning("Image not found: " + e.getImageName());
			return;
		}

		int drawX = e.isUpsideDown() ? vx(e.getX(), cW) : e.getX();
		int drawY = e.isUpsideDown() ? vy(e.getY(), cH) : e.getY();

		// Vertical anchor: L = y is bottom edge, M = y is vertical midpoint,
		// default = y is top edge.
		if (e.isLower())
			drawY -= img.getHeight();
		else if (e.isMiddle())
			drawY -= img.getHeight() / 2;

		// Horizontal anchor: C = x is horizontal midpoint, R = x is right edge,
		// default = x is left edge.
		if (e.isCentered())
			drawX -= img.getWidth() / 2;
		else if (e.isRightAdjust())
			drawX -= img.getWidth();

		AffineTransform saved = g.getTransform();
		int deg = e.getRotationDegrees();
		if (deg != 0)
		{
			g.translate(drawX + img.getWidth() / 2.0, drawY + img.getHeight() / 2.0);
			g.rotate(Math.toRadians(-deg));
			g.translate(-img.getWidth() / 2.0, -img.getHeight() / 2.0);
			g.drawImage(img, 0, 0, null);
		}
		else
		{
			g.drawImage(img, drawX, drawY, null);
		}
		g.setTransform(saved);
		recordHitBox(e.getId(), drawX, drawY, img.getWidth(), img.getHeight());
	}

	// -------------------------------------------------------------------------
	// GRAPHIC (G) — dot coordinates
	// -------------------------------------------------------------------------

	private void drawGraphic(GraphicElement e, Graphics2D g, int cW, int cH)
	{
		// Reconstruct both physical corners/endpoints.
		//
		// LINE: width/height are signed deltas (x2-x1, y2-y1).
		// physical endpoint = (x + width, y + height)
		//
		// BOX: width/height are the box dimensions.
		// The box extends LEFTWARD from x by width, and upward by height.
		// physical corners: (x - width, y) and (x, y + height)
		int x1phys, y1phys, x2phys, y2phys;
		if (e.getShape() == GraphicElement.Shape.BOX)
		{
			x1phys = e.getX() - e.getWidth();
			y1phys = e.getY();
			x2phys = e.getX();
			y2phys = e.getY() + e.getHeight();
		}
		else
		{
			x1phys = e.getX();
			y1phys = e.getY();
			x2phys = e.getX() + e.getWidth();
			y2phys = e.getY() + e.getHeight();
		}

		// Apply the U (upside-down) transform to EACH endpoint independently.
		// This correctly reverses the line direction in view space.
		int drawX1 = e.isUpsideDown() ? vx(x1phys, cW) : x1phys;
		int drawY1 = e.isUpsideDown() ? vy(y1phys, cH) : y1phys;
		int drawX2 = e.isUpsideDown() ? vx(x2phys, cW) : x2phys;
		int drawY2 = e.isUpsideDown() ? vy(y2phys, cH) : y2phys;

		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(Math.max(1, e.getPenWidth())));

		switch (e.getShape())
		{
		case LINE -> g.drawLine(drawX1, drawY1, drawX2, drawY2);
		case BOX -> g.drawRect(Math.min(drawX1, drawX2), Math.min(drawY1, drawY2), Math.abs(drawX2 - drawX1), Math.abs(drawY2 - drawY1));
		default -> g.drawRect(Math.min(drawX1, drawX2), Math.min(drawY1, drawY2), Math.abs(drawX2 - drawX1), Math.abs(drawY2 - drawY1));
		}
		recordHitBox(e.getId(),
				Math.min(drawX1, drawX2), Math.min(drawY1, drawY2),
				Math.abs(drawX2 - drawX1), Math.abs(drawY2 - drawY1));
	}

	// -------------------------------------------------------------------------
	// BARCODE (C) — dot coordinates
	// -------------------------------------------------------------------------

	private void drawBarcode(BarcodeElement e, Graphics2D g, int cW, int cH)
	{
		if (e.getData().isEmpty())
			return;

		// Zoom = pixel scale factor; bar height in okapibarcode units = dots /
		// zoom
		double scale = Math.max(1, e.getZoom());
		int barHeightU = (int) Math.round(e.getHeight() / scale);

		Symbol symbol;
		try
		{
			symbol = buildSymbol(e, barHeightU);
		}
		catch (Exception ex)
		{
			LOG.warning("Barcode build error [" + e.getId() + "]: " + ex.getMessage());
			return;
		}
		if (symbol == null)
			return;

		// Render barcode into a temporary image to get its pixel dimensions.
		// The expected bar height in pixels equals e.getHeight() (dots at 1:1
		// scale
		// before zoom is applied by OkapiBarcode). If the rendered image is
		// taller
		// than that, OkapiBarcode has included HRT rows despite the NONE
		// setting —
		// clip them off so the label layout is not disturbed.
		BufferedImage tmp = renderSymbolToImage(symbol, scale);
		if (tmp == null)
			return;

		int expectedBarH = e.getHeight();
		if (tmp.getHeight() > expectedBarH)
		{
			tmp = tmp.getSubimage(0, 0, tmp.getWidth(), expectedBarH);
		}

		int bW = tmp.getWidth();
		int bH = tmp.getHeight();

		// Bearer bars: the O (outdata) flag in the stored options indicates bearer bars.
		// Note: the parser strips the leading "O" prefix from the options token, so
		// "OCLO" in the LLF is stored as "CLO" — the trailing O is what isOutdata() sees.
		// Code39 bearer bars are not rendered.
		boolean wantBearerBars = e.isOutdata()
				&& e.getCodeType() != BarcodeElement.CodeType.CODE39;
		if (wantBearerBars)
		{
			Graphics2D bg = tmp.createGraphics();
			bg.setColor(Color.BLACK);
			int thick = Math.max(2, (int) Math.round(scale * 3));
			bg.fillRect(0, 0, bW, thick);
			bg.fillRect(0, bH - thick, bW, thick);
			bg.dispose();
		}

		// The L (lower) option flag controls the anchor point of the y
		// coordinate:
		// L present: y is the BOTTOM edge of the barcode (e.g. EAN-128 anchored
		// low on label)
		// L absent: y is the TOP edge of the barcode (e.g. Code39 anchored near
		// top of label)
		// After the U-flip, vy(y) gives the canvas position of that anchor
		// point.
		int viewCentreX = e.isUpsideDown() ? vx(e.getX(), cW) : e.getX();
		int viewTopY;
		if (e.isUpsideDown())
		{
			viewTopY = e.isLower() ? vy(e.getY(), cH) - e.getHeight() : vy(e.getY(), cH);
		}
		else
		{
			// L flag: y is the bottom anchor in screen space; subtract height to get top.
			viewTopY = e.isLower() ? e.getY() - e.getHeight() : e.getY();
		}

		// Horizontal anchor: C = centred, R = right-edge, default = left-edge
		int drawX = e.isCentered() ? viewCentreX - bW / 2 : e.isRightAdjust() ? viewCentreX - bW : viewCentreX;
		int drawY = viewTopY;

		g.drawImage(tmp, drawX, drawY, null);
		recordHitBox(e.getId(), drawX, drawY, bW, bH);
	}

	private Symbol buildSymbol(BarcodeElement e, int barHeightUnits) throws Exception
	{
		// Label templates pair each barcode (C element) with an explicit T element
		// that renders the human-readable text via an FL link to a formatted UVAR.
		// OkapiBarcode must therefore never draw its own HRT — doing so produces
		// a duplicate line under the barcode.
		// The O option flag means "outdata" (this element's value feeds an FL link),
		// not "draw HRT on the barcode itself".
		//
		// IMPORTANT: setHumanReadableLocation(NONE) must be called BEFORE
		// setContent(). OkapiBarcode calls encode() internally inside
		// setContent(),
		// and Code128's encode() reinitialises HRT state; a NONE call made
		// after
		// setContent() is ineffective for that barcode type.

		return switch (e.getCodeType())
		{
		case EAN128, CODE128 -> {
			Code128 bc = new Code128();
			bc.setBarHeight(barHeightUnits);
			bc.setHumanReadableLocation(HumanReadableLocation.NONE);
			bc.setContent(e.getData());
			yield bc;
		}
		case CODE39 -> {
			Code3Of9 bc = new Code3Of9();
			bc.setBarHeight(barHeightUnits);
			bc.setHumanReadableLocation(HumanReadableLocation.NONE);
			bc.setContent(e.getData());
			yield bc;
		}
		default -> {
			LOG.warning("Unsupported barcode type for element " + e.getId());
			yield null;
		}
		};
	}

	/** Render a Symbol to a BufferedImage at the given pixel scale. */
	private BufferedImage renderSymbolToImage(Symbol symbol, double scale)
	{
		// Estimate symbol pixel size from its vector data
		int estW = Math.max(100, symbol.getWidth() * (int) scale + 10);
		int estH = Math.max(50, symbol.getHeight() * (int) scale + 10);

		BufferedImage tmp = new BufferedImage(estW, estH, BufferedImage.TYPE_INT_RGB);
		Graphics2D tg = tmp.createGraphics();
		tg.setColor(Color.WHITE);
		tg.fillRect(0, 0, estW, estH);

		Java2DRenderer renderer = new Java2DRenderer(tg, scale, uk.org.okapibarcode.graphics.Color.WHITE, uk.org.okapibarcode.graphics.Color.BLACK);
		renderer.render(symbol);
		tg.dispose();

		// Crop to actual content (trim white border)
		return cropToContent(tmp);
	}

	/** Trim surrounding white pixels from a barcode image. */
	private BufferedImage cropToContent(BufferedImage src)
	{
		int minX = src.getWidth(), maxX = 0, minY = src.getHeight(), maxY = 0;
		for (int y = 0; y < src.getHeight(); y++)
		{
			for (int x = 0; x < src.getWidth(); x++)
			{
				if ((src.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF)
				{
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (minX > maxX || minY > maxY)
			return src;
		return src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	// -------------------------------------------------------------------------
	// Image loading (PCX via PcxReader; PNG/JPG via ImageIO)
	// -------------------------------------------------------------------------

	private BufferedImage loadImage(String logicalName)
	{
		if (imageCache.containsKey(logicalName))
			return imageCache.get(logicalName);
		if (imageSearchPath == null)
			return null;

		for (String ext : new String[]
		{ "pcx", "PCX", "png", "jpg" })
		{
			File f = imageSearchPath.resolve(logicalName + "." + ext).toFile();
			if (f.exists())
			{
				try
				{
					BufferedImage img = ext.equalsIgnoreCase("pcx") ? PcxReader.read(f) : javax.imageio.ImageIO.read(f);
					if (img != null)
					{
						imageCache.put(logicalName, img);
						return img;
					}
				}
				catch (Exception ex)
				{
					LOG.warning("Failed to load image " + f + ": " + ex.getMessage());
				}
			}
		}
		return null;
	}
}
