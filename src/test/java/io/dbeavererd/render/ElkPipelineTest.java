package io.dbeavererd.render;

import io.dbeavererd.figure.EntityBox;
import io.dbeavererd.model.Diagram;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.Notation;
import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.style.ViewStyle;
import io.dbeavererd.testutil.Fixtures;
import org.eclipse.draw2d.geometry.Rectangle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElkPipelineTest {

    @Test
    void shopFixtureIsOrthogonalAndAnchoredToColumns() throws IOException {
        for (Notation notation : Notation.values()) {
            RenderOptions o = Fixtures.elkOptions();
            o.notation = notation;
            RenderedDiagram d = Pipeline.render(Fixtures.shop(), o);
            check(d, notation.name());
            PngExporter.write(d, Path.of("build/test-output/elk-shop-" + notation.name().toLowerCase() + ".png"));
        }
        RenderOptions full = Fixtures.elkOptions();
        full.notation = Notation.CROWS_FOOT;
        full.styles = EnumSet.of(ViewStyle.ICONS, ViewStyle.TYPES, ViewStyle.NULLABILITY, ViewStyle.COMMENTS);
        RenderedDiagram d = Pipeline.render(Fixtures.shop(), full);
        PngExporter.write(d, Path.of("build/test-output/elk-shop-full.png"));
        Files.writeString(Path.of("build/test-output/elk-shop-full.svg"), SvgExporter.render(d));
    }

    @Test
    void hubAndMultiFkFixtures() throws IOException {
        for (Diagram fixture : new Diagram[] {Fixtures.hub(7), Fixtures.multiFk(6)}) {
            RenderOptions o = Fixtures.elkOptions();
            o.notation = Notation.CROWS_FOOT;
            RenderedDiagram d = Pipeline.render(fixture, o);
            check(d, fixture.name());
            PngExporter.write(d, Path.of("build/test-output/elk-" + fixture.name() + ".png"));
        }
    }

    static void check(RenderedDiagram d, String what) {
        for (int i = 0; i < d.boxes.size(); i++) {
            for (int j = i + 1; j < d.boxes.size(); j++) {
                EntityBox a = d.boxes.get(i);
                EntityBox b = d.boxes.get(j);
                assertFalse(new Rectangle(a.x, a.y, a.width, a.height).intersects(new Rectangle(b.x, b.y, b.width, b.height)),
                    what + ": " + a.entity + " overlaps " + b.entity);
            }
        }
        for (RoutedConnection c : d.connections) {
            String name = what + " " + c.association.name();
            assertTrue(c.size() >= 2, name);
            for (int i = 1; i < c.size(); i++) {
                assertTrue(c.xs[i] == c.xs[i - 1] || c.ys[i] == c.ys[i - 1], name + ": segment " + i + " not axis-aligned");
            }
            Rectangle fkRow = c.source.rowBounds(c.association.sourceColumns().get(0));
            assertEquals(c.source.x, c.xs[0], name + ": starts on the source's left edge");
            assertEquals(fkRow.getCenter().y, c.ys[0], name + ": starts at the FK row");
            int n = c.size();
            if (c.association.isSelfReference()) {
                assertEquals(c.target.x, c.xs[n - 1], name + ": self-loop ends on the left edge");
            } else {
                assertEquals(c.target.right(), c.xs[n - 1], name + ": ends on the target's right edge");
            }
            assertTrue(c.ys[n - 1] > c.target.y && c.ys[n - 1] < c.target.bottom(), name + ": end inside the target");
            // lines must not pass through other tables
            for (EntityBox b : d.boxes) {
                if (b == c.source || b == c.target) continue;
                Rectangle r = new Rectangle(b.x, b.y, b.width, b.height);
                for (int i = 1; i < n; i++) {
                    Rectangle seg = new Rectangle(Math.min(c.xs[i - 1], c.xs[i]), Math.min(c.ys[i - 1], c.ys[i]),
                        Math.abs(c.xs[i] - c.xs[i - 1]) + 1, Math.abs(c.ys[i] - c.ys[i - 1]) + 1);
                    assertFalse(r.intersects(seg), name + ": crosses " + b.entity);
                }
            }
        }
    }
}
