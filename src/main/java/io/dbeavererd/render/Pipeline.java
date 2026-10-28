package io.dbeavererd.render;

import io.dbeavererd.figure.EntityBox;
import io.dbeavererd.layout.ElkLayout;
import io.dbeavererd.model.Diagram;
import io.dbeavererd.model.Entity;
import io.dbeavererd.notation.CrowsFootPainter;
import io.dbeavererd.notation.Idef1xPainter;
import io.dbeavererd.notation.NotationPainter;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.RenderOptions;

import java.util.ArrayList;
import java.util.List;

/** Model -> measured boxes -> layout and routing (ELK) -> paintable diagram. */
public final class Pipeline {
    private Pipeline() {
    }

    public static RenderedDiagram render(Diagram diagram, RenderOptions options) {
        TextMeasurer measurer = new TextMeasurer();
        List<EntityBox> boxes = new ArrayList<>();
        for (Entity e : diagram.entities()) {
            boxes.add(new EntityBox(e, options, measurer));
        }
        List<RoutedConnection> conns = ElkLayout.layout(diagram, boxes, options);
        NotationPainter painter = switch (options.notation) {
            case IDEF1X -> new Idef1xPainter();
            case CROWS_FOOT -> new CrowsFootPainter();
        };
        return new RenderedDiagram(boxes, conns, painter, options);
    }
}
