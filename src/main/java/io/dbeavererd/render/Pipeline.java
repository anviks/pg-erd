package io.dbeavererd.render;

import io.dbeavererd.figure.EntityBox;
import io.dbeavererd.layout.AutoLayout;
import io.dbeavererd.model.Association;
import io.dbeavererd.model.Diagram;
import io.dbeavererd.model.Entity;
import io.dbeavererd.notation.CrowsFootPainter;
import io.dbeavererd.notation.Idef1xPainter;
import io.dbeavererd.notation.NotationPainter;
import io.dbeavererd.route.ConnectionRouter;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.RenderOptions;
import org.eclipse.draw2d.geometry.Point;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Model -> measured boxes -> auto layout -> routing. */
public final class Pipeline {
    private Pipeline() {
    }

    public static RenderedDiagram render(Diagram diagram, RenderOptions options) {
        TextMeasurer measurer = new TextMeasurer();
        List<EntityBox> boxes = new ArrayList<>();
        Map<Entity, EntityBox> boxOf = new IdentityHashMap<>();
        for (Entity e : diagram.entities()) {
            EntityBox b = new EntityBox(e, options, measurer);
            boxes.add(b);
            boxOf.put(e, b);
        }

        Map<Association, List<Point>> bends = AutoLayout.layout(diagram, boxes, options.aspect);

        // connections in the same order the layout saw them: per entity, its outgoing associations
        List<RoutedConnection> conns = new ArrayList<>();
        for (Entity e : diagram.entities()) {
            for (Association a : diagram.sourceAssociations(e)) {
                conns.add(new RoutedConnection(a, boxOf.get(a.source()), boxOf.get(a.target())));
            }
        }
        ConnectionRouter.route(boxes, conns, bends, options.notation.indentation);

        NotationPainter painter = switch (options.notation) {
            case IDEF1X -> new Idef1xPainter();
            case CROWS_FOOT -> new CrowsFootPainter();
        };
        return new RenderedDiagram(boxes, conns, painter, options);
    }
}
