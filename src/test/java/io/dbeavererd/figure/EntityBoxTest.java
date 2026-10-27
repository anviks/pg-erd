package io.dbeavererd.figure;

import io.dbeavererd.model.Entity;
import io.dbeavererd.render.Java2DCanvas;
import io.dbeavererd.render.TextMeasurer;
import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.style.ViewStyle;
import io.dbeavererd.testutil.Fixtures;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityBoxTest {

    @Test
    void sizesFollowDbeaverBoxModel() {
        RenderOptions o = Fixtures.options();
        TextMeasurer m = new TextMeasurer();
        Entity e = Fixtures.customers();
        EntityBox box = new EntityBox(e, o, m);

        int th = m.height(o.fonts.regular);
        int rowH = Math.max(16, th) + 4;
        assertEquals(Math.max(16, th) + 6, box.titleH);
        assertEquals(1, box.keys.rows.size());
        assertEquals(4, box.attrs.rows.size());
        assertEquals(6 + rowH - 2, box.keys.prefH);
        assertEquals(6 + 4 * rowH - 2, box.attrs.prefH);
        assertEquals(box.titleH + box.keys.prefH + box.attrs.prefH + 4, box.height);

        int widest = 0;
        for (EntityBox.Row r : box.attrs.rows) widest = Math.max(widest, r.itemW);
        assertEquals(10 + widest - 4, box.attrs.prefW);
        assertTrue(box.width >= box.attrs.prefW + 4);
    }

    @Test
    void rendersSampleEntitiesToPng() throws Exception {
        RenderOptions o = Fixtures.options();
        TextMeasurer m = new TextMeasurer();
        EntityBox plain = new EntityBox(Fixtures.customers(), o, m);

        RenderOptions full = Fixtures.options();
        full.styles = EnumSet.of(ViewStyle.ICONS, ViewStyle.TYPES, ViewStyle.NULLABILITY, ViewStyle.COMMENTS);
        EntityBox detailed = new EntityBox(Fixtures.customers(), full, m);

        RenderOptions noIcons = Fixtures.options();
        noIcons.styles = EnumSet.of(ViewStyle.TYPES);
        EntityBox bare = new EntityBox(Fixtures.orders(), noIcons, m);

        plain.x = 10; plain.y = 10;
        detailed.x = plain.right() + 20; detailed.y = 10;
        bare.x = detailed.right() + 20; bare.y = 10;

        int w = bare.right() + 10;
        int h = Math.max(Math.max(plain.bottom(), detailed.bottom()), bare.bottom()) + 10;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(o.theme.diagramBackground);
        g.fillRect(0, 0, w, h);
        Java2DCanvas c = new Java2DCanvas(g, new Icons(), m);
        plain.paint(c);
        detailed.paint(c);
        bare.paint(c);
        g.dispose();

        File out = new File("build/test-output/entity.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        assertTrue(out.length() > 0);
    }
}
