package io.dbeavererd.render;

import io.dbeavererd.figure.Icons;
import io.dbeavererd.figure.SvgIcon;
import io.dbeavererd.style.Fonts;

import java.awt.Color;
import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Writes the same drawing calls as SVG. Geometry mirrors {@link Java2DCanvas}: half-pixel offsets for odd
 * stroke widths, text positioned by its line box, icons as reusable {@code <symbol>}s.
 */
public final class SvgCanvas implements Canvas {
    private final StringBuilder body = new StringBuilder();
    private final Map<String, String> symbols = new LinkedHashMap<>();
    private final Icons icons;
    private final TextMeasurer measurer;
    private final Fonts fonts;
    private final int offsetX;
    private final int offsetY;

    /** @param offsetX/offsetY translation applied to all coordinates (export margin minus extent origin) */
    public SvgCanvas(Icons icons, TextMeasurer measurer, Fonts fonts, int offsetX, int offsetY) {
        this.icons = icons;
        this.measurer = measurer;
        this.fonts = fonts;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    private static String hex(Color c) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String num(double v) {
        if (v == Math.rint(v)) return Integer.toString((int) v);
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static double off(int lineWidth) {
        return (lineWidth % 2 == 1) ? 0.5 : 0.0;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @Override
    public void fillRect(int x, int y, int w, int h, Color c) {
        body.append("<rect x=\"").append(x + offsetX).append("\" y=\"").append(y + offsetY)
            .append("\" width=\"").append(w).append("\" height=\"").append(h)
            .append("\" fill=\"").append(hex(c)).append("\"/>\n");
    }

    @Override
    public void drawRect(int x, int y, int w, int h, int lineWidth, Color c) {
        double o = off(lineWidth);
        body.append("<rect x=\"").append(num(x + offsetX + o)).append("\" y=\"").append(num(y + offsetY + o))
            .append("\" width=\"").append(w).append("\" height=\"").append(h)
            .append("\" fill=\"none\" stroke=\"").append(hex(c)).append("\" stroke-width=\"").append(lineWidth).append("\"/>\n");
    }

    @Override
    public void fillRoundRect(int x, int y, int w, int h, int radius, Color c) {
        body.append("<rect x=\"").append(x + offsetX).append("\" y=\"").append(y + offsetY)
            .append("\" width=\"").append(w).append("\" height=\"").append(h)
            .append("\" rx=\"").append(radius).append("\" fill=\"").append(hex(c)).append("\"/>\n");
    }

    @Override
    public void drawRoundRect(int x, int y, int w, int h, int radius, int lineWidth, Color c) {
        double o = off(lineWidth);
        body.append("<rect x=\"").append(num(x + offsetX + o)).append("\" y=\"").append(num(y + offsetY + o))
            .append("\" width=\"").append(w).append("\" height=\"").append(h).append("\" rx=\"").append(radius)
            .append("\" fill=\"none\" stroke=\"").append(hex(c)).append("\" stroke-width=\"").append(lineWidth).append("\"/>\n");
    }

    private String points(int[] xs, int[] ys, int n, double o) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(' ');
            sb.append(num(xs[i] + offsetX + o)).append(',').append(num(ys[i] + offsetY + o));
        }
        return sb.toString();
    }

    @Override
    public void drawPolyline(int[] xs, int[] ys, int n, int lineWidth, Color c, float[] dash) {
        if (n < 2) return;
        body.append("<polyline points=\"").append(points(xs, ys, n, off(lineWidth)))
            .append("\" fill=\"none\" stroke=\"").append(hex(c)).append("\" stroke-width=\"").append(lineWidth).append('"');
        if (dash != null) {
            StringBuilder d = new StringBuilder();
            for (int i = 0; i < dash.length; i++) {
                if (i > 0) d.append(' ');
                d.append(num(dash[i]));
            }
            body.append(" stroke-dasharray=\"").append(d).append('"');
        }
        body.append("/>\n");
    }

    @Override
    public void fillPolygon(int[] xs, int[] ys, int n, Color c) {
        body.append("<polygon points=\"").append(points(xs, ys, n, 0.5)).append("\" fill=\"").append(hex(c)).append("\"/>\n");
    }

    @Override
    public void drawPolygon(int[] xs, int[] ys, int n, int lineWidth, Color c) {
        body.append("<polygon points=\"").append(points(xs, ys, n, off(lineWidth)))
            .append("\" fill=\"none\" stroke=\"").append(hex(c)).append("\" stroke-width=\"").append(lineWidth).append("\"/>\n");
    }

    @Override
    public void fillOval(int x, int y, int w, int h, Color c) {
        body.append("<ellipse cx=\"").append(num(x + offsetX + 0.5 + w / 2.0)).append("\" cy=\"").append(num(y + offsetY + 0.5 + h / 2.0))
            .append("\" rx=\"").append(num(w / 2.0)).append("\" ry=\"").append(num(h / 2.0))
            .append("\" fill=\"").append(hex(c)).append("\"/>\n");
    }

    @Override
    public void drawOval(int x, int y, int w, int h, int lineWidth, Color c) {
        double o = off(lineWidth);
        body.append("<ellipse cx=\"").append(num(x + offsetX + o + w / 2.0)).append("\" cy=\"").append(num(y + offsetY + o + h / 2.0))
            .append("\" rx=\"").append(num(w / 2.0)).append("\" ry=\"").append(num(h / 2.0))
            .append("\" fill=\"none\" stroke=\"").append(hex(c)).append("\" stroke-width=\"").append(lineWidth).append("\"/>\n");
    }

    @Override
    public void drawText(String s, int x, int y, Font f, Color c) {
        if (s == null || s.isEmpty()) return;
        body.append("<text x=\"").append(x + offsetX).append("\" y=\"").append(y + offsetY + measurer.ascent(f))
            .append("\" font-size=\"").append(num(f.getSize2D())).append("px\"");
        if (f.isBold()) body.append(" font-weight=\"bold\"");
        body.append(" fill=\"").append(hex(c)).append("\" textLength=\"").append(measurer.width(s, f))
            .append("\" lengthAdjust=\"spacingAndGlyphs\" xml:space=\"preserve\">").append(esc(s)).append("</text>\n");
    }

    @Override
    public void drawIcon(String iconId, boolean keyOverlay, int x, int y) {
        String symbolId = symbol(iconId, keyOverlay);
        // a <use> of a plain <g> is translated by (x, y) in every SVG renderer; <symbol> sizing is not
        body.append("<use xlink:href=\"#").append(symbolId).append("\" href=\"#").append(symbolId)
            .append("\" x=\"").append(x + offsetX).append("\" y=\"").append(y + offsetY).append("\"/>\n");
    }

    private String symbol(String iconId, boolean keyOverlay) {
        String id = "icon-" + iconId.replace('/', '-') + (keyOverlay ? "-key" : "");
        if (!symbols.containsKey(id)) {
            SvgIcon icon = icons.shape(iconId);
            StringBuilder sb = new StringBuilder();
            sb.append("<g id=\"").append(id).append("\">\n");
            for (SvgIcon.Op op : icon.ops) {
                sb.append("  <path d=\"").append(esc(op.pathData())).append('"');
                if (op.stroke()) {
                    sb.append(" fill=\"none\" stroke=\"").append(hex(op.color())).append("\" stroke-width=\"1\"");
                } else {
                    sb.append(" fill=\"").append(hex(op.color())).append('"');
                }
                sb.append("/>\n");
            }
            if (keyOverlay) {
                String data = base64Resource("/icons/" + Icons.KEY_OVERLAY + ".png");
                sb.append("  <image x=\"8\" y=\"8\" width=\"8\" height=\"8\" xlink:href=\"data:image/png;base64,").append(data)
                    .append("\" href=\"data:image/png;base64,").append(data).append("\"/>\n");
            }
            sb.append("</g>\n");
            symbols.put(id, sb.toString());
        }
        return id;
    }

    private static String base64Resource(String resource) {
        try (InputStream in = SvgCanvas.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("missing resource " + resource);
            return Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Assembles the document. */
    public String toSvg(int width, int height, Color background, boolean embedFont) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"")
            .append(width).append("\" height=\"").append(height).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\"")
            .append(" font-family=\"").append(esc(fontFamilyList())).append("\" shape-rendering=\"geometricPrecision\">\n");
        if (embedFont && fonts.bundled) {
            sb.append("<style>\n");
            sb.append("@font-face{font-family:'Selawik';font-weight:normal;src:url(data:font/ttf;base64,")
                .append(base64Resource("/fonts/selawk.ttf")).append(") format('truetype');}\n");
            sb.append("@font-face{font-family:'Selawik';font-weight:bold;src:url(data:font/ttf;base64,")
                .append(base64Resource("/fonts/selawkb.ttf")).append(") format('truetype');}\n");
            sb.append("</style>\n");
        }
        sb.append("<defs>\n");
        for (String s : symbols.values()) sb.append(s);
        sb.append("</defs>\n");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"").append(hex(background)).append("\"/>\n");
        sb.append(body);
        sb.append("</svg>\n");
        return sb.toString();
    }

    private String fontFamilyList() {
        if (fonts.bundled) return "Selawik, 'Segoe UI', 'Open Sans', sans-serif";
        return switch (fonts.family) {
            case "Monospaced" -> "'Consolas', 'DejaVu Sans Mono', 'Liberation Mono', monospace";
            case "Serif" -> "serif";
            case "SansSerif", "Dialog" -> "sans-serif";
            default -> "'" + fonts.family + "', 'Segoe UI', sans-serif";
        };
    }

    static byte[] pngBytes(java.awt.image.BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
