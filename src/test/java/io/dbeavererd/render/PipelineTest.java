package io.dbeavererd.render;

import io.dbeavererd.figure.EntityBox;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.Notation;
import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.style.Theme;
import io.dbeavererd.style.ViewStyle;
import io.dbeavererd.testutil.Fixtures;
import org.eclipse.draw2d.geometry.Rectangle;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineTest {

    @Test
    void layoutRouteAndExportFixture() throws Exception {
        RenderOptions o = Fixtures.options();
        RenderedDiagram d = Pipeline.render(Fixtures.shop(), o);
        assertStructure(d);

        Rectangle extent = d.extent();
        BufferedImage img = PngExporter.render(d);
        assertEquals(extent.width + 20, img.getWidth());
        assertEquals(extent.height + 20, img.getHeight());
        PngExporter.write(d, Path.of("build/test-output/shop.png"));

        String svg = SvgExporter.render(d);
        assertTrue(svg.startsWith("<?xml"));
        assertTrue(svg.contains("width=\"" + (extent.width + 20) + "\""));
        assertTrue(svg.contains("<use xlink:href=\"#icon-tree-table\""));
        assertTrue(svg.contains("stroke-dasharray=\"1 1\""));
        Files.createDirectories(Path.of("build/test-output"));
        Files.writeString(Path.of("build/test-output/shop.svg"), svg);
    }

    @Test
    void crowsFootDarkWithTypes() throws Exception {
        RenderOptions o = Fixtures.options();
        o.notation = Notation.CROWS_FOOT;
        o.theme = Theme.dark();
        o.styles = EnumSet.of(ViewStyle.ICONS, ViewStyle.TYPES, ViewStyle.NULLABILITY);
        RenderedDiagram d = Pipeline.render(Fixtures.shop(), o);
        assertStructure(d);
        PngExporter.write(d, Path.of("build/test-output/shop-crowsfoot-dark.png"));
        Files.writeString(Path.of("build/test-output/shop-crowsfoot-dark.svg"), SvgExporter.render(d));
    }

    @Test
    void scaleDoublesPixelSize() {
        RenderOptions o = Fixtures.options();
        o.scale = 2;
        RenderedDiagram d = Pipeline.render(Fixtures.shop(), o);
        BufferedImage img = PngExporter.render(d);
        assertEquals((d.extent().width + 20) * 2, img.getWidth());
    }

    static void assertStructure(RenderedDiagram d) {
        for (int i = 0; i < d.boxes.size(); i++) {
            for (int j = i + 1; j < d.boxes.size(); j++) {
                EntityBox a = d.boxes.get(i);
                EntityBox b = d.boxes.get(j);
                Rectangle ra = new Rectangle(a.x, a.y, a.width, a.height);
                Rectangle rb = new Rectangle(b.x, b.y, b.width, b.height);
                assertFalse(ra.intersects(rb), a.entity + " overlaps " + b.entity);
            }
        }
        for (RoutedConnection c : d.connections) {
            assertTrue(c.size() >= 4, c.association.name());
            assertOnBorder(c.source, c.xs[0], c.ys[0], c.association.name() + " start");
            assertOnBorder(c.target, c.xs[c.size() - 1], c.ys[c.size() - 1], c.association.name() + " end");
        }
    }

    private static void assertOnBorder(EntityBox b, int x, int y, String what) {
        // ChopboxAnchor works on the box translated by (-1,-1) and grown by (1,1)
        boolean onVertical = (x == b.x - 1 || x == b.right()) && y >= b.y - 1 && y <= b.bottom();
        boolean onHorizontal = (y == b.y - 1 || y == b.bottom()) && x >= b.x - 1 && x <= b.right();
        assertTrue(onVertical || onHorizontal, what + " (" + x + "," + y + ") not on border of " + b.entity
            + " [" + b.x + "," + b.y + " " + b.width + "x" + b.height + "]");
    }
}
