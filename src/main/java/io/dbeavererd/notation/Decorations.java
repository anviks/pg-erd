package io.dbeavererd.notation;

import io.dbeavererd.geom.Points;
import io.dbeavererd.geom.Rect;
import io.dbeavererd.render.Canvas;

import java.awt.Color;

/** Geometry helpers mirroring Draw2D's PolygonDecoration/PolylineDecoration and DBeaver's CircleDecoration. */
public final class Decorations {
    private Decorations() {
    }

    /**
     * Draw2D decoration transform: template scaled, rotated towards {@code (lx, ly)} as seen from
     * {@code (rx, ry)}, translated to {@code (lx, ly)}; every point rounded (Transform.getTransformed).
     */
    public static Points transform(Points template, double scale, int lx, int ly, int rx, int ry) {
        double angle = Math.atan2(ly - ry, lx - rx);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int n = template.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            double x = template.x(i) * scale;
            double y = template.y(i) * scale;
            xs[i] = (int) Math.round(x * cos - y * sin + lx);
            ys[i] = (int) Math.round(x * sin + y * cos + ly);
        }
        return new Points(xs, ys);
    }

    public static void fillAndOutline(Canvas c, Points pts, Color fill, Color outline) {
        c.fillPolygon(pts.xs, pts.ys, pts.size(), fill);
        c.drawPolygon(pts.xs, pts.ys, pts.size(), 1, outline);
    }

    public static Rect bounds(Points pts) {
        return pts.bounds().expand(1, 1);
    }

    /**
     * DBeaver's AssociationPart.CircleDecoration: an Ellipse of radius {@code radius} centred
     * {@code radius} px back from the location {@code (lx, ly)} towards the reference {@code (rx, ry)},
     * with its bounds enlarged to 2.5r (a quirk kept for fidelity). Returns the bounds used.
     */
    public static Rect circleBounds(int radius, int lx, int ly, int rx, int ry) {
        double d = Math.sqrt(Math.pow(lx - rx, 2) + Math.pow(ly - ry, 2));
        if (d < radius) {
            return new Rect(lx - radius, ly - radius, radius * 2, radius * 2);
        }
        double k = (d - radius) / d;
        double shortx = k * Math.abs(rx - lx);
        double shorty = k * Math.abs(ry - ly);
        int cx = lx < rx ? rx - (int) shortx : rx + (int) shortx;
        int cy = ly > ry ? ry + (int) shorty : ry - (int) shorty;
        return new Rect(cx - radius, cy - radius, (int) (radius * 2.5), (int) (radius * 2.5));
    }

    /** Draw2D Ellipse: fill the bounds, then outline the bounds shrunk by one pixel (line width 1). */
    public static void ellipse(Canvas c, Rect r, Color fill, Color outline) {
        c.fillOval(r.x, r.y, r.width, r.height, fill);
        c.drawOval(r.x, r.y, r.width - 1, r.height - 1, 1, outline);
    }
}
