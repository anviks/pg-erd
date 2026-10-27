package io.dbeavererd.render;

import io.dbeavererd.figure.Icons;
import org.eclipse.draw2d.geometry.Rectangle;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** ERDExportRasterImage: content extent + margin, background filled, figures painted, no zoom. */
public final class PngExporter {
    private PngExporter() {
    }

    public static BufferedImage render(RenderedDiagram diagram) {
        Rectangle extent = diagram.extent();
        if (extent.isEmpty()) {
            throw new IllegalStateException("Can't export empty diagram");
        }
        int margin = diagram.options.margin;
        int scale = Math.max(1, diagram.options.scale);
        int w = extent.width + margin * 2;
        int h = extent.height + margin * 2;
        BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(diagram.options.theme.diagramBackground);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.scale(scale, scale);
            g.translate(-extent.x + margin, -extent.y + margin);
            Java2DCanvas canvas = new Java2DCanvas(g, new Icons(), new TextMeasurer());
            diagram.paint(canvas);
        } finally {
            g.dispose();
        }
        return img;
    }

    public static void write(RenderedDiagram diagram, Path file) throws IOException {
        BufferedImage img = render(diagram);
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        if (!ImageIO.write(img, "png", file.toFile())) {
            throw new IOException("no PNG writer available");
        }
    }
}
