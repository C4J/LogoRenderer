package com.commander4j.logorenderer.ui;

import javax.swing.*;

import com.commander4j.logorenderer.LabelRenderer;
import com.commander4j.logorenderer.LlfLayout;
import com.commander4j.logorenderer.ParseLogger;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Swing panel that displays a rendered label image.
 *
 * Rendering is delegated entirely to {@link LabelRenderer}, which produces a
 * 1:1 dot-per-pixel {@link BufferedImage}.  This panel scales that image to
 * the current zoom level for display.
 */
public class LabelCanvas extends JPanel {

    private static final long serialVersionUID = 1L;

	private static final int MARGIN = 20;

    private BufferedImage rendered   = null;
    private float         zoom       = 1.0f;
    private LlfLayout     layout     = null;
    private boolean       flipped    = false;
    private ParseLogger   logger     = null;

    /** Hit boxes in rendered-image coordinates (before zoom / margin / flip). */
    private Map<Integer, Rectangle> hitBoxes = new HashMap<>();

    /** Fired with the element id when the user left-clicks a visual element. */
    private IntConsumer elementClickListener;

    public LabelCanvas() {
        setBackground(Color.LIGHT_GRAY);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int id = hitTestAt(e.getPoint());
                if (elementClickListener != null)
                    elementClickListener.accept(id);
            }
        });
    }

    public void setElementClickListener(IntConsumer listener) {
        this.elementClickListener = listener;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void setLayout(LlfLayout layout, Path imageSearchPath) {
        this.layout = layout;

        LabelRenderer renderer = new LabelRenderer();
        if (imageSearchPath != null) renderer.setImageSearchPath(imageSearchPath);
        if (logger != null) renderer.setLogger(logger);

        rendered = renderer.render(layout);
        hitBoxes = new HashMap<>(renderer.getHitBoxes());
        updatePreferredSize();
        revalidate();
        repaint();
    }

    /** Reset to the empty placeholder state. */
    public void clear() {
        this.layout   = null;
        this.rendered = null;
        this.hitBoxes = new HashMap<>();
        updatePreferredSize();
        revalidate();
        repaint();
    }

    /** Attach a logger that receives font-mapping diagnostics during render. */
    public void setLogger(ParseLogger logger) {
        this.logger = logger;
    }

    public LlfLayout getLlfLayout() { return layout; }

    public float getZoom()          { return zoom; }

    public boolean isFlipped()      { return flipped; }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        repaint();
    }

    public void setZoom(float zoom) {
        this.zoom = Math.max(0.05f, Math.min(zoom, 8.0f));
        updatePreferredSize();
        revalidate();
        repaint();
    }

    // -----------------------------------------------------------------------
    // Painting
    // -----------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (rendered == null) {
            drawPlaceholder(g);
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                                RenderingHints.VALUE_RENDER_QUALITY);

            int scaledW = (int)(rendered.getWidth()  * zoom);
            int scaledH = (int)(rendered.getHeight() * zoom);

            // White label background + thin border
            g2.setColor(Color.WHITE);
            g2.fillRect(MARGIN, MARGIN, scaledW, scaledH);
            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(1));
            g2.drawRect(MARGIN, MARGIN, scaledW, scaledH);

            // Draw scaled label image (optionally rotated 180°)
            if (flipped) {
                g2.translate(MARGIN + scaledW, MARGIN + scaledH);
                g2.rotate(Math.PI);
                g2.drawImage(rendered, 0, 0, scaledW, scaledH, null);
            } else {
                g2.drawImage(rendered, MARGIN, MARGIN, scaledW, scaledH, null);
            }
        } finally {
            g2.dispose();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void updatePreferredSize() {
        if (rendered == null) {
            setPreferredSize(new Dimension(400 + MARGIN * 2, 200 + MARGIN * 2));
            return;
        }
        int w = (int)(rendered.getWidth()  * zoom) + MARGIN * 2;
        int h = (int)(rendered.getHeight() * zoom) + MARGIN * 2;
        setPreferredSize(new Dimension(w, h));
    }

    /**
     * Translate a mouse point in canvas coordinates back to rendered-image
     * (dot) coordinates and return the element id whose hit box contains it,
     * or -1 if the click missed every element. If multiple hit boxes overlap
     * the point, returns the smallest (tightest) — best approximates "the
     * thing on top".
     */
    private int hitTestAt(Point p) {
        if (rendered == null || hitBoxes.isEmpty()) return -1;

        int scaledW = (int)(rendered.getWidth()  * zoom);
        int scaledH = (int)(rendered.getHeight() * zoom);

        // Unwind margin + flip + zoom to get dot coordinates.
        float dx, dy;
        if (flipped) {
            dx = (MARGIN + scaledW - p.x) / zoom;
            dy = (MARGIN + scaledH - p.y) / zoom;
        } else {
            dx = (p.x - MARGIN) / zoom;
            dy = (p.y - MARGIN) / zoom;
        }
        if (dx < 0 || dy < 0 || dx >= rendered.getWidth() || dy >= rendered.getHeight())
            return -1;

        int bestId = -1;
        long bestArea = Long.MAX_VALUE;
        for (Map.Entry<Integer, Rectangle> entry : hitBoxes.entrySet()) {
            Rectangle r = entry.getValue();
            if (r.contains(dx, dy)) {
                long area = (long) r.width * r.height;
                if (area < bestArea) {
                    bestArea = area;
                    bestId   = entry.getKey();
                }
            }
        }
        return bestId;
    }

    private void drawPlaceholder(Graphics g) {
        g.setColor(Color.GRAY);
        g.setFont(g.getFont().deriveFont(Font.ITALIC, 14f));
        FontMetrics fm = g.getFontMetrics();
        String msg = "Open a .llf file to view the label";
        g.drawString(msg,
                (getWidth()  - fm.stringWidth(msg)) / 2,
                (getHeight() + fm.getAscent())       / 2);
    }
}
