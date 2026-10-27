package io.dbeavererd.render;

import io.dbeavererd.figure.Icons;
import org.eclipse.draw2d.geometry.Rectangle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Same extent/margin rules as the PNG export, written as SVG. */
public final class SvgExporter {
    private SvgExporter() {
    }

    public static String render(RenderedDiagram diagram) {
        Rectangle extent = diagram.extent();
        if (extent.isEmpty()) {
            throw new IllegalStateException("Can't export empty diagram");
        }
        int margin = diagram.options.margin;
        int w = extent.width + margin * 2;
        int h = extent.height + margin * 2;
        SvgCanvas canvas = new SvgCanvas(new Icons(), new TextMeasurer(), diagram.options.fonts, -extent.x + margin, -extent.y + margin);
        diagram.paint(canvas);
        return canvas.toSvg(w, h, diagram.options.theme.diagramBackground, diagram.options.embedSvgFont);
    }

    public static void write(RenderedDiagram diagram, Path file) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, render(diagram), StandardCharsets.UTF_8);
    }
}
