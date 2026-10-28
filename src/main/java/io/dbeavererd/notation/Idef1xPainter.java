package io.dbeavererd.notation;

import io.dbeavererd.geom.Points;
import io.dbeavererd.geom.Rect;
import io.dbeavererd.render.Canvas;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.style.Theme;
import io.dbeavererd.style.ViewStyle;

import java.awt.Color;

/**
 * IDEF1X as drawn by DBeaver's IDEF1XDiagramNotation + AssociationPart: a filled circle at the FK (child)
 * end, a hollow rhombus at the parent end for optional (nullable) FKs, and a dotted line when the
 * association is non-identifying.
 */
public final class Idef1xPainter implements NotationPainter {
    static final int CIRCLE_RADIUS = 4;                    // ERDNotationBase.CIRCLE_RADIUS
    static final float[] DOTTED = {1f, 1f};               // SWT.LINE_DOT at width 1
    static final double RHOMBUS_SCALE = 5;
    static final Points RHOMBUS = new Points(new int[] {0, -1, -2, -1}, new int[] {0, 1, 0, -1});

    @Override
    public void paint(Canvas c, RoutedConnection conn, RenderOptions options) {
        if (conn.size() < 2) return;
        Theme theme = options.theme;
        Color line = theme.linesForeground;
        boolean dotted = !conn.association.identifying();
        c.drawPolyline(conn.xs, conn.ys, conn.size(), 1, line, dotted ? DOTTED : null);

        // source decoration: CircleDecoration located at point 0, reference point 1
        Decorations.ellipse(c, sourceCircle(conn), theme.decorationColor(), line);

        // target decoration: rhombus for optional associations, filled with the diagram background
        if (conn.association.optional()) {
            Decorations.fillAndOutline(c, targetRhombus(conn), theme.diagramBackground, line);
        }

        if (options.has(ViewStyle.COMMENTS) && conn.association.comment() != null && !conn.association.comment().isEmpty()) {
            Labels.paintMidpointLabel(c, conn, conn.association.comment(), options.fonts.regular, theme.attrForeground, options);
        }
    }

    private static Rect sourceCircle(RoutedConnection conn) {
        return Decorations.circleBounds(CIRCLE_RADIUS, conn.xs[0], conn.ys[0], conn.xs[1], conn.ys[1]);
    }

    private static Points targetRhombus(RoutedConnection conn) {
        int n = conn.size();
        return Decorations.transform(RHOMBUS, RHOMBUS_SCALE, conn.xs[n - 1], conn.ys[n - 1], conn.xs[n - 2], conn.ys[n - 2]);
    }

    @Override
    public Rect bounds(RoutedConnection conn, RenderOptions options) {
        Rect r = Labels.polylineBounds(conn);
        if (conn.size() >= 2) {
            r.union(sourceCircle(conn).expand(1, 1));
            if (conn.association.optional()) {
                r.union(Decorations.bounds(targetRhombus(conn)));
            }
        }
        return r;
    }
}
