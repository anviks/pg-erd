package io.dbeavererd.render;

import io.dbeavererd.figure.EntityBox;
import io.dbeavererd.notation.NotationPainter;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.geom.Rect;

import java.util.List;

/** A laid-out, routed diagram ready to be painted on any {@link Canvas}. */
public final class RenderedDiagram {
    public final List<EntityBox> boxes;
    public final List<RoutedConnection> connections;
    public final NotationPainter notation;
    public final RenderOptions options;

    public RenderedDiagram(List<EntityBox> boxes, List<RoutedConnection> connections, NotationPainter notation, RenderOptions options) {
        this.boxes = boxes;
        this.connections = connections;
        this.notation = notation;
        this.options = options;
    }

    /** FreeformLayeredPane.getFreeformExtent(): union of the entity and connection layers. */
    public Rect extent() {
        Rect r = null;
        for (EntityBox b : boxes) {
            Rect e = b.bounds();
            r = r == null ? e : r.union(e);
        }
        for (RoutedConnection c : connections) {
            Rect e = notation.bounds(c, options);
            if (e.isEmpty()) continue;
            r = r == null ? e : r.union(e);
        }
        return r == null ? new Rect() : r;
    }

    /** Paint order of GEF's printable layers: primary layer (entities) first, connection layer on top. */
    public void paint(Canvas canvas) {
        for (EntityBox b : boxes) b.paint(canvas);
        for (RoutedConnection c : connections) notation.paint(canvas, c, options);
    }
}
