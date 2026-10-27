package io.dbeavererd.style;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;

/**
 * Diagram fonts. DBeaver's default UI font is "Segoe UI" 9pt (12px at 96 dpi); notation labels use 8pt.
 * Because Segoe UI is rarely installed on Linux build agents, the bundled Selawik (metric-compatible
 * with Segoe UI) is used unless another family is requested.
 */
public final class Fonts {
    public static final String BUNDLED_FAMILY = "Selawik";
    public static final float DEFAULT_POINT_SIZE = 9f;
    public static final float NOTATION_POINT_SIZE = 8f;

    public final Font regular;
    public final Font bold;
    public final Font notation;
    public final String family;
    public final boolean bundled;

    private Fonts(Font regular, Font bold, Font notation, String family, boolean bundled) {
        this.regular = regular;
        this.bold = bold;
        this.notation = notation;
        this.family = family;
        this.bundled = bundled;
    }

    /** Points at 96 dpi to Java2D user-space pixels. */
    public static float pxFromPt(float pt) {
        return Math.round(pt * 96f / 72f);
    }

    public static Fonts load(String family, float pointSize) {
        float px = pxFromPt(pointSize);
        float notationPx = pxFromPt(NOTATION_POINT_SIZE);
        if (family == null || family.isBlank() || family.equalsIgnoreCase(BUNDLED_FAMILY)) {
            try {
                Font r = createFont("/fonts/selawk.ttf");
                Font b = createFont("/fonts/selawkb.ttf");
                return new Fonts(r.deriveFont(px), b.deriveFont(px), r.deriveFont(notationPx), BUNDLED_FAMILY, true);
            } catch (IOException e) {
                throw new IllegalStateException("bundled font missing", e);
            }
        }
        boolean installed = false;
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (f.equalsIgnoreCase(family)) {
                installed = true;
                break;
            }
        }
        if (!installed) {
            throw new IllegalArgumentException("font family not installed: " + family);
        }
        Font r = new Font(family, Font.PLAIN, 1).deriveFont(px);
        Font b = new Font(family, Font.BOLD, 1).deriveFont(px);
        return new Fonts(r, b, r.deriveFont(notationPx), family, false);
    }

    private static Font createFont(String resource) throws IOException {
        try (InputStream in = Fonts.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("resource not found: " + resource);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (java.awt.FontFormatException e) {
            throw new IOException(e);
        }
    }
}
