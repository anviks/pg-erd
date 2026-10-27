package io.dbeavererd.render;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Text extents the way Draw2D measures them (GC.textExtent): width = sum of integer advances,
 * height = the font's line height. Uses the same rendering hints as {@link Java2DCanvas} so that
 * measured and painted sizes agree.
 */
public final class TextMeasurer {
    private final Graphics2D g;
    private final Map<Font, FontMetrics> metrics = new HashMap<>();

    public TextMeasurer() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        applyHints(g);
    }

    public static void applyHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private FontMetrics fm(Font f) {
        return metrics.computeIfAbsent(f, g::getFontMetrics);
    }

    public int width(String s, Font f) {
        if (s == null || s.isEmpty()) return 0;
        int w = 0;
        for (TextRuns.Run r : TextRuns.split(s, f)) {
            w += fm(r.font()).stringWidth(r.text());
        }
        return w;
    }

    public int height(Font f) {
        return fm(f).getHeight();
    }

    public int ascent(Font f) {
        return fm(f).getAscent();
    }
}
