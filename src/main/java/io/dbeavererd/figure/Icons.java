package io.dbeavererd.figure;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Icon registry. Ids are resource paths without extension, e.g. {@code tree/table}, {@code types/number}.
 * Rasters are 16x16; a primary-key column gets DBeaver's {@code over/key_ovr.png} composited bottom-right
 * (JDBCAttribute.getOverlayImage + OverlayImageDescriptor.drawBottomRight).
 */
public final class Icons {
    public static final String TABLE = "tree/table";
    public static final String VIEW = "tree/view";
    public static final String KEY_OVERLAY = "over/key_ovr";
    public static final int SIZE = 16;

    private final Map<String, SvgIcon> shapes = new HashMap<>();
    private final Map<String, BufferedImage> rasters = new HashMap<>();
    private BufferedImage keyOverlay;

    public SvgIcon shape(String id) {
        return shapes.computeIfAbsent(id, k -> {
            try (InputStream in = Icons.class.getResourceAsStream("/icons/" + k + ".svg")) {
                if (in == null) throw new IllegalArgumentException("no such icon: " + k);
                return SvgIcon.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public BufferedImage keyOverlay() {
        if (keyOverlay == null) {
            try (InputStream in = Icons.class.getResourceAsStream("/icons/" + KEY_OVERLAY + ".png")) {
                if (in == null) throw new IllegalStateException("missing key overlay icon");
                keyOverlay = ImageIO.read(in);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return keyOverlay;
    }

    /** 16x16 raster of the icon, optionally with the PK key overlay in the bottom-right corner. */
    public BufferedImage raster(String id, boolean withKeyOverlay) {
        String key = id + (withKeyOverlay ? "+key" : "");
        return rasters.computeIfAbsent(key, k -> {
            BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                paint(g, shape(id));
                if (withKeyOverlay) {
                    BufferedImage ov = keyOverlay();
                    g.drawImage(ov, SIZE - ov.getWidth(), SIZE - ov.getHeight(), null);
                }
            } finally {
                g.dispose();
            }
            return img;
        });
    }

    public static void paint(Graphics2D g, SvgIcon icon) {
        for (SvgIcon.Op op : icon.ops) {
            g.setColor(op.color());
            if (op.stroke()) {
                g.setStroke(new BasicStroke(1f));
                g.draw(op.path());
            } else {
                g.fill(op.path());
            }
        }
    }
}
