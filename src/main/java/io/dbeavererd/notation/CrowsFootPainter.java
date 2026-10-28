package io.dbeavererd.notation;

import io.dbeavererd.figure.EntityBox;
import io.dbeavererd.geom.Points;
import io.dbeavererd.geom.Rect;
import io.dbeavererd.render.Canvas;
import io.dbeavererd.render.TextMeasurer;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.style.Theme;
import io.dbeavererd.style.ViewStyle;

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
    /**
     * Template points: 0-2 crow's foot, 3-4 first dash, 5-6 second dash, 7 circle centre, 8 top.
     */
    static final Points TEMPLATE = new Points(
        new int[] {0, -2, 0, -2, -2, -3, -3, -3, -6},
        new int[] {-1, 0, 1, -1, 1, -1, 1, 0, 0});
    private static final TextMeasurer MEASURER = new TextMeasurer();

    private record End(Type type, String label) {
    }

    private static End sourceEnd(RoutedConnection c) {
        return c.association.oneToOne() ? new End(Type.ONE_ONLY, LABEL_1) : new End(Type.ONE_OR_MANY, LABEL_1_TO_N);
    }

    private static End targetEnd(RoutedConnection c) {
        return c.association.optional() ? new End(Type.ZERO_OR_ONE, LABEL_0_TO_1) : new End(Type.ONE_ONLY, LABEL_1);
    }

    private static Points decoration(RoutedConnection conn, boolean end) {
        int n = conn.size();
        return end
            ? Decorations.transform(TEMPLATE, SCALE, conn.xs[n - 1], conn.ys[n - 1], conn.xs[n - 2], conn.ys[n - 2])
            : Decorations.transform(TEMPLATE, SCALE, conn.xs[0], conn.ys[0], conn.xs[1], conn.ys[1]);
    }

    @Override
    public void paint(Canvas c, RoutedConnection conn, RenderOptions options) {
        if (conn.size() < 2) return;
        Theme theme = options.theme;
        Color line = theme.linesForeground;
        c.drawPolyline(conn.xs, conn.ys, conn.size(), 1, line, null);

        End src = sourceEnd(conn);
        End trg = targetEnd(conn);
        paintDecoration(c, decoration(conn, false), src.type, theme);
        paintDecoration(c, decoration(conn, true), trg.type, theme);

        Font font = options.fonts.notation;
        Color labelColor = theme.decorationColor();
        Rect lb = labelBounds(conn, false, src.label, font);
        c.drawText(src.label, lb.x, lb.y, font, labelColor);
        lb = labelBounds(conn, true, trg.label, font);
        c.drawText(trg.label, lb.x, lb.y, font, labelColor);

        if (options.has(ViewStyle.COMMENTS) && conn.association.comment() != null && !conn.association.comment().isEmpty()) {
            Labels.paintMidpointLabel(c, conn, conn.association.comment(), options.fonts.regular, theme.attrForeground, options);
        }
    }

    private static void paintDecoration(Canvas c, Points pts, Type type, Theme theme) {
        Color fg = theme.linesForeground;
        Color bg = theme.diagramBackground;
        switch (type) {
            case ZERO_OR_ONE -> {
                line(c, pts, 3, 4, fg);
                circle(c, pts.x(7), pts.y(7), fg, bg);
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
                circle(c, pts.x(7), pts.y(7), fg, bg);
            }
        }
    }

    private static void line(Canvas c, Points pts, int a, int b, Color color) {
        c.drawPolyline(new int[] {pts.x(a), pts.x(b)}, new int[] {pts.y(a), pts.y(b)}, 2, 1, color, null);
    }

    private static void circle(Canvas c, int x, int y, Color fg, Color bg) {
        int r = DIAMETER / 2;
        c.fillOval(x - r, y - r, DIAMETER, DIAMETER, bg);
        c.drawOval(x - r, y - r, DIAMETER, DIAMETER, 1, fg);
    }

    /** Draw2D ConnectionEndpointLocator.relocate for a label of the given text. */
    static Rect labelBounds(RoutedConnection conn, boolean end, String text, Font font) {
        int n = conn.size();
        int sp = end ? n - 1 : 0;
        int ep = end ? n - 2 : 1;
        int startX = conn.xs[sp];
        int startY = conn.ys[sp];
        int endX = conn.xs[ep];
        int endY = conn.ys[ep];
        EntityBox owner = end ? conn.target : conn.source;
        int quadrant = connectionLocation(startX, startY, owner.x, owner.y, owner.centerX(), owner.centerY());

        int cos = 1;
        boolean transpose = quadrant == 1 || quadrant == 3;
        if (quadrant == 3 || quadrant == 4) cos = -1;

        int textW = MEASURER.width(text, font);
        int textH = MEASURER.height(font);
        // in the transposed frame x and y swap roles
        int figureWidth = transpose ? textH : textW;
        int figureHeight = transpose ? textW : textH;
        int sx = transpose ? startY : startX;
        int sy = transpose ? startX : startY;
        int ex = transpose ? endY : endX;
        int ey = transpose ? endX : endY;

        double tan;
        if (ex == sx) {
            tan = 1.0;
        } else {
            tan = (double) (ey - sy) / (double) (ex - sx);
        }
        if (tan > 1) tan = 1.0;
        else if (tan < -1) tan = -1.0;

        int yShift = 0;
        if (LBL_V_DISTANCE < 0) yShift = -figureHeight;
        else if (LBL_V_DISTANCE == 0) yShift = -figureHeight / 2;

        int fx = sx + (LBL_U_DISTANCE * cos) + figureWidth * ((cos - 1) / 2);
        int fy = (int) (sy + cos * LBL_U_DISTANCE * tan + LBL_V_DISTANCE + yShift);
        return transpose ? new Rect(fy, fx, textW, textH) : new Rect(fx, fy, textW, textH);
    }

    private static int connectionLocation(int locX, int locY, int topLeftX, int topLeftY, int centerX, int centerY) {
        if (locX == centerX) {
            return (locY < centerY) ? 3 : 1;
        }
        double m1 = (double) (topLeftY - centerY) / (double) (topLeftX - centerX);
        double m2 = (double) (locY - centerY) / (double) (locX - centerX);
        if (Math.abs(m2) <= Math.abs(m1)) {
            return (locX < centerX) ? 4 : 2;
        }
        return (locY < centerY) ? 3 : 1;
    }

    @Override
    public Rect bounds(RoutedConnection conn, RenderOptions options) {
        Rect r = Labels.polylineBounds(conn);
        if (conn.size() >= 2) {
            r.union(Decorations.bounds(decoration(conn, false)).expand(DIAMETER, DIAMETER));
            r.union(Decorations.bounds(decoration(conn, true)).expand(DIAMETER, DIAMETER));
            Font font = options.fonts.notation;
            r.union(labelBounds(conn, false, sourceEnd(conn).label, font));
            r.union(labelBounds(conn, true, targetEnd(conn).label, font));
        }
        return r;
    }
}
