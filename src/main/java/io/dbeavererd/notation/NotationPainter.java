package io.dbeavererd.notation;

import io.dbeavererd.render.Canvas;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.geom.Rect;

/** Draws one routed connection (line + end decorations + labels) in a given notation. */
public interface NotationPainter {
    void paint(Canvas canvas, RoutedConnection conn, RenderOptions options);

    /** Bounding box of everything {@link #paint} draws, for the export extent. */
    Rect bounds(RoutedConnection conn, RenderOptions options);
}
