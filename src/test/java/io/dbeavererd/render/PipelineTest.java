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
import java.io.IOException;
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
    void defaultAnchorsStartOnFkRowAndEndOnTheBorder() throws IOException {
        RenderOptions o = Fixtures.options();
        RenderedDiagram d = Pipeline.render(Fixtures.shop(), o);
        assertStructure(d);
        for (RoutedConnection c : d.connections) {
            Rectangle src = c.source.rowBounds(c.association.sourceColumns().get(0));
            assertTrue(c.ys[0] >= src.y && c.ys[0] < src.bottom(), c.association.name() + " start y " + c.ys[0] + " not in FK row " + src);
            assertEquals(c.source.x - 1, c.xs[0], "start leaves through the left edge");
            assertEquals(c.ys[0], c.ys[1], "start stub is horizontal");
            assertEquals(c.xs[0] - o.notation.indentation, c.xs[1], "left stub");
        }
        // parents are arranged left of their FK children
        RoutedConnection oc = find(d, "orders_customer_id_fkey");
        assertTrue(oc.target.right() <= oc.source.x, "customers left of orders");
        PngExporter.write(d, Path.of("build/test-output/shop-column-anchors.png"));

        RenderOptions nearest = Fixtures.options();
        nearest.fkExitLeft = false;
        RenderedDiagram dn = Pipeline.render(Fixtures.shop(), nearest);
        assertStructure(dn);
        RoutedConnection on = find(dn, "orders_customer_id_fkey");
        assertTrue(on.source.right() <= on.target.x, "DBeaver arrangement: orders left of customers");
        PngExporter.write(dn, Path.of("build/test-output/shop-fk-nearest.png"));
    }

    @Test
    void bothAnchorsStartAndEndOnTheRelevantRows() throws IOException {
        RenderOptions o = Fixtures.options();
        o.anchor = RenderOptions.Anchor.BOTH;
        RenderedDiagram d = Pipeline.render(Fixtures.shop(), o);
        assertStructure(d);
        for (RoutedConnection c : d.connections) {
            Rectangle src = c.source.rowBounds(c.association.sourceColumns().get(0));
            Rectangle trg = c.target.rowBounds(c.association.targetColumns().get(0));
            int n = c.size();
            assertTrue(c.ys[0] >= src.y && c.ys[0] < src.bottom(), c.association.name() + " start y " + c.ys[0] + " not in FK row " + src);
            assertTrue(c.ys[n - 1] >= trg.y && c.ys[n - 1] < trg.bottom(), c.association.name() + " end y " + c.ys[n - 1] + " not in PK row " + trg);
            assertTrue(c.xs[0] == c.source.x - 1 || c.xs[0] == c.source.right(), "start leaves horizontally");
            assertTrue(c.xs[n - 1] == c.target.x - 1 || c.xs[n - 1] == c.target.right(), "end arrives horizontally");
            assertEquals(c.ys[0], c.ys[1], "start stub is horizontal");
            assertEquals(c.ys[n - 1], c.ys[n - 2], "end stub is horizontal");
        }
        // customers.id is referenced by orders and addresses: the two ends must not coincide
        RoutedConnection a = find(d, "orders_customer_id_fkey");
        RoutedConnection b = find(d, "addresses_customer_id_fkey");
        boolean sameSide = a.xs[a.size() - 1] == b.xs[b.size() - 1];
        if (sameSide) {
            assertTrue(a.ys[a.size() - 1] != b.ys[b.size() - 1], "shared parent row ends are fanned out");
        }
        PngExporter.write(d, Path.of("build/test-output/shop-both-anchors.png"));
    }

    @Test
    void manyLinesIntoOnePkFanOutAlongTheSide() throws IOException {
        RenderOptions o = Fixtures.options();
        o.anchor = RenderOptions.Anchor.BOTH;
        RenderedDiagram d = Pipeline.render(Fixtures.hub(7), o);
        assertStructure(d);
        EntityBox users = d.boxes.get(0);
        Rectangle idRow = users.rowBounds("id");
        java.util.Map<Integer, java.util.List<RoutedConnection>> bySide = new java.util.TreeMap<>();
        for (RoutedConnection c : d.connections) {
            bySide.computeIfAbsent(c.xs[c.size() - 1], k -> new java.util.ArrayList<>()).add(c);
        }
        for (java.util.List<RoutedConnection> side : bySide.values()) {
            if (side.size() < 2) continue;
            side.sort(java.util.Comparator.comparingInt(c -> c.ys[c.size() - 1]));
            for (int i = 0; i < side.size(); i++) {
                RoutedConnection c = side.get(i);
                int n = c.size();
                assertTrue(c.ys[n - 1] > users.y && c.ys[n - 1] < users.bottom(), "end inside the table");
                assertEquals(c.ys[n - 1], c.ys[n - 2], "stub horizontal");
                if (i > 0) {
                    RoutedConnection prev = side.get(i - 1);
                    assertEquals(o.notation.fanStep, c.ys[n - 1] - prev.ys[prev.size() - 1], "ends fanStep px apart");
                    assertTrue(prev.ys[prev.size() - 3] <= c.ys[n - 3], "ordered by approach direction, no crossing at the edge");
                }
            }
            int first = side.get(0).ys[side.get(0).size() - 1];
            int last = side.get(side.size() - 1).ys[side.get(side.size() - 1).size() - 1];
            int mid = (first + last) / 2;
            assertTrue(Math.abs(mid - idRow.getCenter().y) <= 4, "fan centred on the id row");
        }
        PngExporter.write(d, Path.of("build/test-output/hub.png"));

        RenderOptions crow = Fixtures.options();
        crow.anchor = RenderOptions.Anchor.BOTH;
        crow.notation = Notation.CROWS_FOOT;
        RenderedDiagram dc = Pipeline.render(Fixtures.hub(7), crow);
        assertStructure(dc);
        // 7 ends do not fit at 24 px on a 3-row table: packed at >= 12 px and flagged so labels are dropped
        java.util.List<RoutedConnection> sorted = new java.util.ArrayList<>(dc.connections);
        sorted.sort(java.util.Comparator.comparingInt(c -> c.ys[c.size() - 1]));
        for (int i = 1; i < sorted.size(); i++) {
            int gap = sorted.get(i).ys[sorted.get(i).size() - 1] - sorted.get(i - 1).ys[sorted.get(i - 1).size() - 1];
            assertTrue(gap >= 12, "ends at least 12 px apart, got " + gap);
        }
        assertTrue(dc.connections.stream().allMatch(c -> c.crowdedEnd), "crowded ends flagged");
        assertTrue(dc.connections.stream().noneMatch(c -> c.crowdedStart), "child ends not crowded");
        PngExporter.write(dc, Path.of("build/test-output/hub-crowsfoot.png"));
        RenderedDiagram dc4 = Pipeline.render(Fixtures.hub(4), crow);
        PngExporter.write(dc4, Path.of("build/test-output/hub4-crowsfoot.png"));

        RenderOptions def = Fixtures.options();
        def.notation = Notation.CROWS_FOOT;
        RenderedDiagram dd = Pipeline.render(Fixtures.hub(7), def);
        assertStructure(dd);
        assertTrue(dd.connections.stream().noneMatch(c -> c.crowdedEnd), "default mode never crowds destination ends");
        PngExporter.write(dd, Path.of("build/test-output/hub-crowsfoot-default.png"));
    }

    @Test
    void manyFkColumnsEachStartOnTheirOwnRow() throws IOException {
        for (Notation notation : Notation.values()) {
            RenderOptions o = Fixtures.options();
            o.notation = notation;
            RenderedDiagram d = Pipeline.render(Fixtures.multiFk(6), o);
            assertStructure(d);
            for (RoutedConnection c : d.connections) {
                Rectangle row = c.source.rowBounds(c.association.sourceColumns().get(0));
                assertEquals(row.getCenter().y, c.ys[0], notation + " " + c.association.name() + " must start at its FK row centre");
                assertEquals(c.source.x - 1, c.xs[0]);
            }
            PngExporter.write(d, Path.of("build/test-output/multifk-" + notation.name().toLowerCase() + ".png"));
        }
    }

    @Test
    void entityAnchorsKeepDbeaverBehaviour() throws IOException {
        RenderOptions o = Fixtures.options();
        o.anchor = RenderOptions.Anchor.ENTITY;
        RenderedDiagram d = Pipeline.render(Fixtures.shop(), o);
        assertStructure(d);
        PngExporter.write(d, Path.of("build/test-output/shop-entity-anchors.png"));
    }

    private static RoutedConnection find(RenderedDiagram d, String name) {
        return d.connections.stream().filter(c -> c.association.name().equals(name)).findFirst().orElseThrow();
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
