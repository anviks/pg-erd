package io.dbeavererd.style;

import java.util.EnumSet;
import java.util.Set;

/** Everything that influences how a diagram is drawn. Defaults mirror DBeaver's preferences. */
public final class RenderOptions {
    public Look look = Look.dbeaver();
    public Notation notation = Notation.IDEF1X;
    public Set<ViewStyle> styles = EnumSet.of(ViewStyle.ICONS);
    public AttributeVisibility visibility = AttributeVisibility.ALL;
    public Theme theme = Theme.light();
    public Fonts fonts;
    /** Integer upscale factor for the PNG. */
    public int scale = 1;
    /** Margin around the diagram content in the exported image (ERDExportRasterImage: 10). */
    public int margin = 10;
    public boolean embedSvgFont = false;

    public boolean has(ViewStyle s) {
        return styles.contains(s);
    }
}
