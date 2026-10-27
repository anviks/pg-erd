package io.dbeavererd.notation;

import io.dbeavererd.render.Canvas;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.style.Theme;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PointList;
import org.eclipse.draw2d.geometry.Rectangle;

import java.awt.Color;

/**
 * IDEF1X as drawn by DBeaver's IDEF1XDiagramNotation + AssociationPart: a filled circle at the FK (child)
 * end, a hollow rhombus at the parent end for optional (nullable) FKs, and a dotted line when the
 * association is non-identifying.
 */
public final class Idef1xPainter implements NotationPainter {
    static final int CIRCLE_RADIUS = 4;                    // ERDNotationBase.CIRCLE_RADIUS
    static final float[] DOTTED = {1f, 1f};               // SWT.LINE_DOT at width 1
    static final PointList RHOMBUS = new PointList();

    static {
        RHOMBUS.addPoint(0, 0);
        RHOMBUS.addPoint(-1, 1);
        RHOMBUS.addPoint(-2, 0);
        RHOMBUS.addPoint(-1, -1);
    }

    @Override
    public void paint(Canvas c, RoutedConnection conn, RenderOptions options) {
        if (conn.size() < 2) return;
        Theme theme = options.theme;
        Color line = theme.linesForeground;
        boolean dotted = !conn.association.identifying();
        c.drawPolyline(conn.xs, conn.ys, conn.size(), 1, line, dotted ? DOTTED : null);

        // source decoration: CircleDecoration located at point 0, reference point 1
        Point p0 = new Point(conn.xs[0], conn.ys[0]);
        Point p1 = new Point(conn.xs[1], conn.ys[1]);
        Rectangle circle = Decorations.circleBounds(CIRCLE_RADIUS, p0, p1);
        Decorations.ellipse(c, circle, theme.decorationColor(), line);

        // target decoration: rhombus for optional associations, filled with the diagram background
        if (conn.association.optional()) {
            int n = conn.size();
            Point end = new Point(conn.xs[n - 1], conn.ys[n - 1]);
            Point ref = new Point(conn.xs[n - 2], conn.ys[n - 2]);
            PointList pts = Decorations.transform(RHOMBUS, 5, end, ref);
            Decorations.fillAndOutline(c, pts, theme.diagramBackground, line);
        }

        if (options.has(io.dbeavererd.style.ViewStyle.COMMENTS) && conn.association.comment() != null
            && !conn.association.comment().isEmpty()) {
            Labels.paintMidpointLabel(c, conn, conn.association.comment(), options.fonts.regular, theme.attrForeground, options);
        }
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
            r.union(Decorations.circleBounds(CIRCLE_RADIUS, new Point(conn.xs[0], conn.ys[0]), new Point(conn.xs[1], conn.ys[1])).expand(1, 1));
            if (conn.association.optional()) {
                int n = conn.size();
                r.union(Decorations.bounds(Decorations.transform(RHOMBUS, 5,
                    new Point(conn.xs[n - 1], conn.ys[n - 1]), new Point(conn.xs[n - 2], conn.ys[n - 2]))));
            }
        }
        return r;
    }
}
