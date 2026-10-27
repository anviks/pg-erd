package io.dbeavererd.figure;

import java.awt.Color;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately tiny SVG reader for DBeaver's 16x16 icons: a list of {@code <path>} elements with a
 * solid fill or a 1px stroke. Path data is restricted to the absolute commands M, L, H, V, C and Z,
 * which is all those icons use. Anything else is an error so that an unsupported icon is noticed.
 */
public final class SvgIcon {

    /** One drawing operation: fill or stroke a path with a colour. */
    public record Op(Path2D.Float path, Color color, boolean stroke, String pathData) {
    }

    private static final Pattern PATH_ELEMENT = Pattern.compile("<path\\b([^>]*)/?>", Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile("([\\w:-]+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?");

    public final int width;
    public final int height;
    public final List<Op> ops;

    private SvgIcon(int width, int height, List<Op> ops) {
        this.width = width;
        this.height = height;
        this.ops = List.copyOf(ops);
    }

    public static SvgIcon parse(String svg) {
        int w = intAttr(svg, "width", 16);
        int h = intAttr(svg, "height", 16);
        List<Op> ops = new ArrayList<>();
        Matcher m = PATH_ELEMENT.matcher(svg);
        while (m.find()) {
            String attrs = m.group(1);
            String d = null;
            String fill = null;
            String stroke = null;
            Matcher a = ATTR.matcher(attrs);
            while (a.find()) {
                switch (a.group(1)) {
                    case "d" -> d = a.group(2);
                    case "fill" -> fill = a.group(2);
                    case "stroke" -> stroke = a.group(2);
                    default -> { }
                }
            }
            if (d == null) continue;
            Path2D.Float path = parsePathData(d);
            if (stroke != null && !stroke.equals("none")) {
                ops.add(new Op(path, parseColor(stroke), true, d));
            }
            if (fill != null && !fill.equals("none")) {
                ops.add(new Op(path, parseColor(fill), false, d));
            } else if (fill == null && stroke == null) {
                // SVG default fill is black
                ops.add(new Op(path, Color.BLACK, false, d));
            }
        }
        if (ops.isEmpty()) throw new IllegalArgumentException("SVG contains no <path> elements");
        return new SvgIcon(w, h, ops);
    }

    private static int intAttr(String svg, String name, int def) {
        Matcher m = Pattern.compile("<svg\\b[^>]*\\b" + name + "=\"(\\d+)").matcher(svg);
        return m.find() ? Integer.parseInt(m.group(1)) : def;
    }

    static Color parseColor(String s) {
        s = s.trim();
        if (s.startsWith("#") && s.length() == 7) {
            return new Color(Integer.parseInt(s.substring(1), 16));
        }
        if (s.startsWith("#") && s.length() == 4) {
            int r = Integer.parseInt(s.substring(1, 2), 16) * 17;
            int g = Integer.parseInt(s.substring(2, 3), 16) * 17;
            int b = Integer.parseInt(s.substring(3, 4), 16) * 17;
            return new Color(r, g, b);
        }
        return switch (s.toLowerCase()) {
            case "white" -> Color.WHITE;
            case "black" -> Color.BLACK;
            default -> throw new IllegalArgumentException("unsupported SVG colour: " + s);
        };
    }

    /** Parses absolute M/L/H/V/C/Z path data. */
    static Path2D.Float parsePathData(String d) {
        Path2D.Float p = new Path2D.Float(Path2D.WIND_NON_ZERO);
        int i = 0;
        int n = d.length();
        char cmd = 0;
        float cx = 0;
        float cy = 0;
        while (i < n) {
            char c = d.charAt(i);
            if (Character.isWhitespace(c) || c == ',') {
                i++;
                continue;
            }
            if (Character.isLetter(c)) {
                cmd = c;
                i++;
                if (cmd == 'Z' || cmd == 'z') {
                    p.closePath();
                    // current point becomes the subpath start
                    java.awt.geom.Point2D cp = p.getCurrentPoint();
                    if (cp != null) {
                        cx = (float) cp.getX();
                        cy = (float) cp.getY();
                    }
                }
                continue;
            }
            // number(s) for the current command
            switch (cmd) {
                case 'M' -> {
                    float[] v = numbers(d, i, 2);
                    i = nextIndex;
                    cx = v[0];
                    cy = v[1];
                    p.moveTo(cx, cy);
                    cmd = 'L'; // subsequent pairs are implicit lineto
                }
                case 'L' -> {
                    float[] v = numbers(d, i, 2);
                    i = nextIndex;
                    cx = v[0];
                    cy = v[1];
                    p.lineTo(cx, cy);
                }
                case 'H' -> {
                    float[] v = numbers(d, i, 1);
                    i = nextIndex;
                    cx = v[0];
                    p.lineTo(cx, cy);
                }
                case 'V' -> {
                    float[] v = numbers(d, i, 1);
                    i = nextIndex;
                    cy = v[0];
                    p.lineTo(cx, cy);
                }
                case 'C' -> {
                    float[] v = numbers(d, i, 6);
                    i = nextIndex;
                    p.curveTo(v[0], v[1], v[2], v[3], v[4], v[5]);
                    cx = v[4];
                    cy = v[5];
                }
                default -> throw new IllegalArgumentException("unsupported SVG path command '" + cmd + "' in: " + d);
            }
        }
        return p;
    }

    // crude single-threaded "out parameter" for numbers(); parsing is only done at startup
    private static int nextIndex;

    private static float[] numbers(String d, int from, int count) {
        float[] out = new float[count];
        Matcher m = NUMBER.matcher(d);
        int pos = from;
        for (int k = 0; k < count; k++) {
            m.region(pos, d.length());
            if (!m.lookingAt()) {
                // skip separators
                while (pos < d.length() && (Character.isWhitespace(d.charAt(pos)) || d.charAt(pos) == ',')) pos++;
                m.region(pos, d.length());
                if (!m.lookingAt()) throw new IllegalArgumentException("expected number at " + pos + " in: " + d);
            }
            out[k] = Float.parseFloat(m.group());
            pos = m.end();
        }
        nextIndex = pos;
        return out;
    }
}
