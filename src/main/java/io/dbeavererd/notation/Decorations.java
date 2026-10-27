package io.dbeavererd.notation;

import io.dbeavererd.render.Canvas;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PointList;
import org.eclipse.draw2d.geometry.Rectangle;

import java.awt.Color;

/** Geometry helpers mirroring Draw2D's PolygonDecoration/PolylineDecoration and DBeaver's CircleDecoration. */
public final class Decorations {
    private Decorations() {
    }

    /**
     * Draw2D decoration transform: template scaled, rotated towards {@code location} as seen from
     * {@code reference}, translated to {@code location}; every point rounded (Transform.getTransformed).
     */
    public static PointList transform(PointList template, double scale, Point location, Point reference) {
        double angle = Math.atan2(location.y - reference.y, location.x - reference.x);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        PointList out = new PointList(template.size());
        for (int i = 0; i < template.size(); i++) {
            Point t = template.getPoint(i);
            double x = t.x * scale;
            double y = t.y * scale;
            double rx = x * cos - y * sin;
            double ry = x * sin + y * cos;
            out.addPoint(new Point((int) Math.round(rx + location.x), (int) Math.round(ry + location.y)));
        }
        return out;
    }

    public static void fillAndOutline(Canvas c, PointList pts, Color fill, Color outline) {
        int n = pts.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            Point p = pts.getPoint(i);
            xs[i] = p.x;
            ys[i] = p.y;
        }
        c.fillPolygon(xs, ys, n, fill);
        c.drawPolygon(xs, ys, n, 1, outline);
    }

    public static Rectangle bounds(PointList pts) {
        return pts.getBounds().getCopy().expand(1, 1);
    }

    /**
     * DBeaver's AssociationPart.CircleDecoration: an Ellipse of radius {@code radius} centred
     * {@code radius} px back from {@code location} towards {@code reference}, with its bounds enlarged
     * to 2.5r (a quirk kept for fidelity). Returns the bounds used.
     */
    public static Rectangle circleBounds(int radius, Point location, Point reference) {
        double d = Math.sqrt(Math.pow(location.x - reference.x, 2) + Math.pow(location.y - reference.y, 2));
        if (d < radius) {
            return new Rectangle(location.x - radius, location.y - radius, radius * 2, radius * 2);
        }
        double k = (d - radius) / d;
        double longx = Math.abs(reference.x - location.x);
        double longy = Math.abs(reference.y - location.y);
        double shortx = k * longx;
        double shorty = k * longy;
        int rx;
        int ry;
        if (location.x < reference.x) {
            rx = reference.x - (int) shortx;
        } else {
            rx = reference.x + (int) shortx;
        }
        if (location.y > reference.y) {
            ry = reference.y + (int) shorty;
        } else {
            ry = reference.y - (int) shorty;
        }
        return new Rectangle(rx - radius, ry - radius, (int) (radius * 2.5), (int) (radius * 2.5));
    }

    /** Draw2D Ellipse: fill the bounds, then outline the bounds shrunk by one pixel (line width 1). */
    public static void ellipse(Canvas c, Rectangle r, Color fill, Color outline) {
        c.fillOval(r.x, r.y, r.width, r.height, fill);
        c.drawOval(r.x, r.y, r.width - 1, r.height - 1, 1, outline);
    }
}
