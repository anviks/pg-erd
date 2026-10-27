package io.dbeavererd.style;

import java.util.EnumSet;
import java.util.Set;

/** Everything that influences how a diagram is drawn. Defaults mirror DBeaver's preferences. */
public final class RenderOptions {
    /** Where connections attach to an entity. */
    public enum Anchor {
        /** DBeaver's behaviour: anywhere on the entity border, aimed at the entity centre. */
        ENTITY,
        /** Source end at the FK column's row; destination end on the border like DBeaver. */
        COLUMN,
        /** Both ends on their column rows (FK column and referenced column). */
        BOTH;

        public static Anchor parse(String s) {
            return valueOf(s.trim().toUpperCase());
        }
    }

    /** Layout + routing engine. */
    public enum Layout {
        /** Eclipse Layout Kernel: layered, parents left of children, ports at the FK/PK rows, orthogonal lines. */
        ELK,
        /** DBeaver's own arrangement and shortest-path (diagonal) routing. */
        DBEAVER;

        public static Layout parse(String s) {
            return valueOf(s.trim().toUpperCase());
        }
    }

    public Layout layout = Layout.ELK;
    public Notation notation = Notation.IDEF1X;
    public Anchor anchor = Anchor.COLUMN;
    /**
     * With column anchors, always leave the FK row through the table's left edge (next to the column
     * name) and arrange parents to the left of their children; false = leave through the nearest side
     * with DBeaver's left-to-right arrangement.
     */
    public boolean fkExitLeft = true;
    public Set<ViewStyle> styles = EnumSet.of(ViewStyle.ICONS);
    public AttributeVisibility visibility = AttributeVisibility.ALL;
    public Theme theme = Theme.light();
    public Fonts fonts;
    /** Integer upscale factor for the PNG. */
    public int scale = 1;
    /** Viewport aspect ratio (w/h) used when arranging disconnected entities into rows. */
    public double aspect = 1.6;
    /** Margin around the diagram content in the exported image (ERDExportRasterImage: 10). */
    public int margin = 10;
    public boolean embedSvgFont = false;

    public boolean has(ViewStyle s) {
        return styles.contains(s);
    }
}
