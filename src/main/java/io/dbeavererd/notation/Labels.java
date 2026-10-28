package io.dbeavererd.notation;

import io.dbeavererd.geom.Rect;
import io.dbeavererd.render.Canvas;
import io.dbeavererd.render.TextMeasurer;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.RenderOptions;

import java.awt.Color;
import java.awt.Font;

/** Connection labels (Draw2D ConnectionLocator.MIDDLE) and shared bounds helpers. */
final class Labels {
    private static final TextMeasurer MEASURER = new TextMeasurer();

    private Labels() {
    }

    /** ConnectionLocator.MIDDLE: the midpoint of the middle segment, label centred on it. */
    static void paintMidpointLabel(Canvas c, RoutedConnection conn, String text, Font font, Color color, RenderOptions options) {
        int n = conn.size();
        if (n < 2) return;
        int mx;
        int my;
        if (n % 2 == 0) {
            int i = n / 2 - 1;
            mx = (conn.xs[i] + conn.xs[i + 1]) / 2;
            my = (conn.ys[i] + conn.ys[i + 1]) / 2;
        } else {
            mx = conn.xs[n / 2];
            my = conn.ys[n / 2];
        }
        int w = MEASURER.width(text, font);
        int h = MEASURER.height(font);
        c.drawText(text, mx - w / 2, my - h / 2, font, color);
    }

    /** Bounds of the 1 px polyline itself: every point grown by one pixel on each side. */
    static Rect polylineBounds(RoutedConnection conn) {
        Rect r = null;
        for (int i = 0; i < conn.size(); i++) {
            Rect p = new Rect(conn.xs[i] - 1, conn.ys[i] - 1, 2, 2);
            r = r == null ? p : r.union(p);
        }
        return r == null ? new Rect() : r;
    }
}
