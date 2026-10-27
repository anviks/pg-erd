package io.dbeavererd.render;

import io.dbeavererd.figure.Icons;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** Paints onto a Graphics2D (a BufferedImage for PNG output). */
public final class Java2DCanvas implements Canvas {
    private final Graphics2D g;
    private final Icons icons;
    private final TextMeasurer measurer;

    public Java2DCanvas(Graphics2D g, Icons icons, TextMeasurer measurer) {
        this.g = g;
        this.icons = icons;
        this.measurer = measurer;
        TextMeasurer.applyHints(g);
    }

    /** Odd stroke widths are shifted by half a pixel so a 1px line lands on exactly one pixel row. */
    private static double off(int lineWidth) {
        return (lineWidth % 2 == 1) ? 0.5 : 0.0;
    }

    private void stroke(int lineWidth, float[] dash) {
        if (dash == null) {
            g.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        } else {
            g.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
        }
    }

    @Override
    public void fillRect(int x, int y, int w, int h, Color c) {
        g.setColor(c);
        g.fillRect(x, y, w, h);
    }

    @Override
    public void drawRect(int x, int y, int w, int h, int lineWidth, Color c) {
        g.setColor(c);
        stroke(lineWidth, null);
        double o = off(lineWidth);
        g.draw(new Rectangle2D.Double(x + o, y + o, w, h));
    }

    @Override
    public void drawPolyline(int[] xs, int[] ys, int n, int lineWidth, Color c, float[] dash) {
        if (n < 2) return;
        g.setColor(c);
        stroke(lineWidth, dash);
        double o = off(lineWidth);
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xs[0] + o, ys[0] + o);
        for (int i = 1; i < n; i++) p.lineTo(xs[i] + o, ys[i] + o);
        g.draw(p);
    }

    private static Path2D.Double polygon(int[] xs, int[] ys, int n, double o) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xs[0] + o, ys[0] + o);
        for (int i = 1; i < n; i++) p.lineTo(xs[i] + o, ys[i] + o);
        p.closePath();
        return p;
    }

    @Override
    public void fillPolygon(int[] xs, int[] ys, int n, Color c) {
        g.setColor(c);
        g.fill(polygon(xs, ys, n, 0.5));
    }

    @Override
    public void drawPolygon(int[] xs, int[] ys, int n, int lineWidth, Color c) {
        g.setColor(c);
        stroke(lineWidth, null);
        g.draw(polygon(xs, ys, n, off(lineWidth)));
    }

    @Override
    public void fillOval(int x, int y, int w, int h, Color c) {
        g.setColor(c);
        g.fill(new Ellipse2D.Double(x + 0.5, y + 0.5, w, h));
    }

    @Override
    public void drawOval(int x, int y, int w, int h, int lineWidth, Color c) {
        g.setColor(c);
        stroke(lineWidth, null);
        double o = off(lineWidth);
        g.draw(new Ellipse2D.Double(x + o, y + o, w, h));
    }

    @Override
    public void drawText(String s, int x, int y, Font f, Color c) {
        if (s == null || s.isEmpty()) return;
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, x, y + measurer.ascent(f));
    }

    @Override
    public void drawIcon(String iconId, boolean keyOverlay, int x, int y) {
        g.drawImage(icons.raster(iconId, keyOverlay), x, y, null);
    }
}
