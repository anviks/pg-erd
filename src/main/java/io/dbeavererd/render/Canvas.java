package io.dbeavererd.render;

import java.awt.Color;
import java.awt.Font;

/**
 * Minimal drawing surface shared by the PNG and SVG back ends. Coordinates follow Draw2D/SWT
 * conventions: integer pixels, strokes centred on the geometric edge, text positioned by the
 * top-left corner of its line box.
 */
public interface Canvas {
    void fillRect(int x, int y, int w, int h, Color c);

    /** Rectangle outline from (x,y) to (x+w, y+h), stroke centred on the edges (SWT drawRectangle). */
    void drawRect(int x, int y, int w, int h, int lineWidth, Color c);

    /** Filled rectangle with rounded corners of the given radius. */
    void fillRoundRect(int x, int y, int w, int h, int radius, Color c);

    /** Like {@link #drawRect} with rounded corners. */
    void drawRoundRect(int x, int y, int w, int h, int radius, int lineWidth, Color c);

    /** @param dash dash pattern in pixels, or null for a solid line */
    void drawPolyline(int[] xs, int[] ys, int n, int lineWidth, Color c, float[] dash);

    void fillPolygon(int[] xs, int[] ys, int n, Color c);

    void drawPolygon(int[] xs, int[] ys, int n, int lineWidth, Color c);

    void fillOval(int x, int y, int w, int h, Color c);

    void drawOval(int x, int y, int w, int h, int lineWidth, Color c);

    /** Draws text with its line box's top-left corner at (x, y). */
    void drawText(String s, int x, int y, Font f, Color c);

    /** Draws a 16x16 icon with its top-left corner at (x, y). */
    void drawIcon(String iconId, boolean keyOverlay, int x, int y);
}
