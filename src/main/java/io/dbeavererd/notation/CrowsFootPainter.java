package io.dbeavererd.notation;

import io.dbeavererd.figure.EntityBox;
import io.dbeavererd.render.Canvas;
import io.dbeavererd.render.TextMeasurer;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.style.Theme;
import io.dbeavererd.style.ViewStyle;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PointList;
import org.eclipse.draw2d.geometry.Rectangle;

import java.awt.Color;
import java.awt.Font;

/**
 * Crow's foot as drawn by DBeaver's CrowsFootDiagramNotation / CrowsFootPolylineDecoration, with the
 * cardinality labels positioned by Draw2D's ConnectionEndpointLocator(uDistance 3, vDistance -4).
 */
public final class CrowsFootPainter implements NotationPainter {
    enum Type { ZERO_OR_ONE, ONE_ONLY, ONE_OR_MANY, ZERO_OR_MANY }

    static final String LABEL_0_TO_1 = "0..1";
    static final String LABEL_1 = "1";
    static final String LABEL_1_TO_N = "1..n";
    static final int LBL_V_DISTANCE = -4;
    static final int LBL_U_DISTANCE = 3;
    static final int DIAMETER = 8;
    static final double SCALE = 5;
    static final PointList TEMPLATE = new PointList();
    private static final TextMeasurer MEASURER = new TextMeasurer();

    static {
        TEMPLATE.addPoint(0, -1);   // 0 crow's foot
        TEMPLATE.addPoint(-2, 0);   // 1
        TEMPLATE.addPoint(0, 1);    // 2
        TEMPLATE.addPoint(-2, -1);  // 3 dash 1
        TEMPLATE.addPoint(-2, 1);   // 4
        TEMPLATE.addPoint(-3, -1);  // 5 dash 2
        TEMPLATE.addPoint(-3, 1);   // 6
        TEMPLATE.addPoint(-3, 0);   // 7 circle
        TEMPLATE.addPoint(-6, 0);   // 8 top
    }

    private record End(Type type, String label) {
    }

    private static End sourceEnd(RoutedConnection c) {
        return c.association.oneToOne() ? new End(Type.ONE_ONLY, LABEL_1) : new End(Type.ONE_OR_MANY, LABEL_1_TO_N);
    }

    private static End targetEnd(RoutedConnection c) {
        return c.association.optional() ? new End(Type.ZERO_OR_ONE, LABEL_0_TO_1) : new End(Type.ONE_ONLY, LABEL_1);
    }

    @Override
    public void paint(Canvas c, RoutedConnection conn, RenderOptions options) {
        if (conn.size() < 2) return;
        Theme theme = options.theme;
        Color line = theme.linesForeground;
        c.drawPolyline(conn.xs, conn.ys, conn.size(), 1, line, null);

        int n = conn.size();
        Point p0 = new Point(conn.xs[0], conn.ys[0]);
        Point p1 = new Point(conn.xs[1], conn.ys[1]);
        Point pn = new Point(conn.xs[n - 1], conn.ys[n - 1]);
        Point pn1 = new Point(conn.xs[n - 2], conn.ys[n - 2]);

        End src = sourceEnd(conn);
        End trg = targetEnd(conn);
        paintDecoration(c, Decorations.transform(TEMPLATE, SCALE, p0, p1), src.type, theme);
        paintDecoration(c, Decorations.transform(TEMPLATE, SCALE, pn, pn1), trg.type, theme);

        Font font = options.fonts.notation;
        Color labelColor = theme.decorationColor();
        Rectangle lb = labelBounds(conn, false, src.label, font);
        c.drawText(src.label, lb.x, lb.y, font, labelColor);
        lb = labelBounds(conn, true, trg.label, font);
        c.drawText(trg.label, lb.x, lb.y, font, labelColor);

        if (options.has(ViewStyle.COMMENTS) && conn.association.comment() != null && !conn.association.comment().isEmpty()) {
            Labels.paintMidpointLabel(c, conn, conn.association.comment(), options.fonts.regular, theme.attrForeground, options);
        }
    }

    private static void paintDecoration(Canvas c, PointList pts, Type type, Theme theme) {
        Color fg = theme.linesForeground;
        Color bg = theme.diagramBackground;
        switch (type) {
            case ZERO_OR_ONE -> {
                line(c, pts, 3, 4, fg);
                circle(c, pts.getPoint(7), fg, bg);
            }
            case ONE_ONLY -> {
                line(c, pts, 3, 4, fg);
                line(c, pts, 5, 6, fg);
            }
            case ONE_OR_MANY -> {
                line(c, pts, 0, 1, fg);
                line(c, pts, 1, 2, fg);
                line(c, pts, 3, 4, fg);
            }
            case ZERO_OR_MANY -> {
                line(c, pts, 0, 1, fg);
                line(c, pts, 1, 2, fg);
                circle(c, pts.getPoint(7), fg, bg);
            }
        }
    }

    private static void line(Canvas c, PointList pts, int a, int b, Color color) {
        Point pa = pts.getPoint(a);
        Point pb = pts.getPoint(b);
        c.drawPolyline(new int[] {pa.x, pb.x}, new int[] {pa.y, pb.y}, 2, 1, color, null);
    }

    private static void circle(Canvas c, Point p, Color fg, Color bg) {
        int r = DIAMETER / 2;
        c.fillOval(p.x - r, p.y - r, DIAMETER, DIAMETER, bg);
        c.drawOval(p.x - r, p.y - r, DIAMETER, DIAMETER, 1, fg);
    }

    /** Draw2D ConnectionEndpointLocator.relocate for a label of the given text. */
    static Rectangle labelBounds(RoutedConnection conn, boolean end, String text, Font font) {
        int n = conn.size();
        int sp = end ? n - 1 : 0;
        int ep = end ? n - 2 : 1;
        Point startPoint = new Point(conn.xs[sp], conn.ys[sp]);
        Point endPoint = new Point(conn.xs[ep], conn.ys[ep]);
        EntityBox owner = end ? conn.target : conn.source;
        Rectangle ownerBounds = new Rectangle(owner.x, owner.y, owner.width, owner.height);
        int quadrant = connectionLocation(startPoint, ownerBounds.getTopLeft(), ownerBounds.getCenter());

        int cos = 1;
        boolean transpose = quadrant == 1 || quadrant == 3;
        if (quadrant == 3 || quadrant == 4) cos = -1;

        Dimension figureSize = new Dimension(MEASURER.width(text, font), MEASURER.height(font));
        Dimension size = transpose ? figureSize.getTransposed() : figureSize.getCopy();
        Point s = transpose ? startPoint.getTransposed() : startPoint.getCopy();
        Point e = transpose ? endPoint.getTransposed() : endPoint.getCopy();

        double tan;
        if (e.x == s.x) {
            tan = 1.0;
        } else {
            tan = (double) (e.y - s.y) / (double) (e.x - s.x);
        }
        if (tan > 1) tan = 1.0;
        else if (tan < -1) tan = -1.0;

        int figureWidth = size.width;
        int figureHeight = size.height;
        int yShift = 0;
        if (LBL_V_DISTANCE < 0) yShift = -figureHeight;
        else if (LBL_V_DISTANCE == 0) yShift = -figureHeight / 2;

        Point figurePoint = new Point(s.x + (LBL_U_DISTANCE * cos) + figureWidth * ((cos - 1) / 2),
            (int) (s.y + cos * LBL_U_DISTANCE * tan + LBL_V_DISTANCE + yShift));
        Point loc = transpose ? figurePoint.getTransposed() : figurePoint;
        return new Rectangle(loc.x, loc.y, figureSize.width, figureSize.height);
    }

    private static int connectionLocation(Point loc, Point topLeft, Point center) {
        if (loc.x == center.x) {
            return (loc.y < center.y) ? 3 : 1;
        }
        double m1 = (double) (topLeft.y - center.y) / (double) (topLeft.x - center.x);
        double m2 = (double) (loc.y - center.y) / (double) (loc.x - center.x);
        if (Math.abs(m2) <= Math.abs(m1)) {
            return (loc.x < center.x) ? 4 : 2;
        }
        return (loc.y < center.y) ? 3 : 1;
    }

    @Override
    public Rectangle bounds(RoutedConnection conn, RenderOptions options) {
        Rectangle r = null;
        for (int i = 0; i < conn.size(); i++) {
            Rectangle p = new Rectangle(conn.xs[i] - 1, conn.ys[i] - 1, 2, 2);
            r = r == null ? p : r.union(p);
        }
        if (r == null) return new Rectangle();
        if (conn.size() >= 2) {
            int n = conn.size();
            r.union(Decorations.bounds(Decorations.transform(TEMPLATE, SCALE, new Point(conn.xs[0], conn.ys[0]), new Point(conn.xs[1], conn.ys[1]))).expand(DIAMETER, DIAMETER));
            r.union(Decorations.bounds(Decorations.transform(TEMPLATE, SCALE, new Point(conn.xs[n - 1], conn.ys[n - 1]), new Point(conn.xs[n - 2], conn.ys[n - 2]))).expand(DIAMETER, DIAMETER));
            Font font = options.fonts.notation;
            r.union(labelBounds(conn, false, sourceEnd(conn).label, font));
            r.union(labelBounds(conn, true, targetEnd(conn).label, font));
        }
        return r;
    }
}
