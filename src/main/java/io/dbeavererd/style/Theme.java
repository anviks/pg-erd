package io.dbeavererd.style;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Colour theme, mirroring DBeaver's ERD colour definitions
 * (org.jkiss.dbeaver.ui.editors.erd/plugin.xml and css/e4-dark-erd-editor.css).
 * SWT system colours are taken at their Windows defaults, which is where DBeaver's defaults
 * were designed (COLOR_LIST_BACKGROUND = white, COLOR_INFO_BACKGROUND = 255,255,225).
 */
public final class Theme {
    public Color diagramBackground;
    public Color entityRegularBackground;
    public Color entityPrimaryBackground;
    public Color entityAssociationBackground;
    public Color entityNameForeground;
    public Color linesForeground;
    public Color attrBackground;
    public Color attrForeground;
    public Color noteBackground;
    public Color noteForeground;
    /** Header colours for the 2nd..8th schema in a multi-schema diagram (index 0 uses the regular colour). */
    public Color[] headerColors;
    /** Border colours for the 2nd..8th data source (unused: a CLI diagram has one data source). */
    public Color[] borderColors;
    public boolean dark;
    /** Frame colour; null = the line colour (DBeaver). */
    public Color borderColor;
    /** Colour of the type / NOT NULL column; null = the attribute colour. */
    public Color typeForeground;
    /** Colour of separator lines inside a table; null = the frame colour. */
    public Color separatorColor;
    /** Title colour; null = black or white, whichever contrasts with the header. */
    public Color headerForeground;
    /** When set, a table's header colour is picked from this palette by its name (pastel look). */
    public Color[] tablePalette;

    public Color border() { return borderColor != null ? borderColor : linesForeground; }
    public Color typeForeground() { return typeForeground != null ? typeForeground : attrForeground; }
    public Color separator() { return separatorColor != null ? separatorColor : border(); }
    public Color headerForeground(Color header) { return headerForeground != null ? headerForeground : contrast(header); }

    public static Theme light() {
        Theme t = new Theme();
        t.dark = false;
        t.diagramBackground = Color.WHITE;                          // COLOR_LIST_BACKGROUND
        t.entityRegularBackground = new Color(255, 255, 225);       // COLOR_INFO_BACKGROUND
        t.entityPrimaryBackground = new Color(255, 128, 64);
        t.entityAssociationBackground = Color.WHITE;                // COLOR_LIST_BACKGROUND
        t.entityNameForeground = Color.BLACK;                       // COLOR_INFO_FOREGROUND
        t.linesForeground = new Color(0, 143, 207);
        t.attrBackground = Color.WHITE;                             // COLOR_LIST_BACKGROUND
        t.attrForeground = Color.BLACK;                             // COLOR_LIST_FOREGROUND
        t.noteBackground = new Color(255, 255, 225);
        t.noteForeground = Color.BLACK;
        t.headerColors = new Color[] {
            new Color(204, 192, 184), new Color(228, 255, 181), new Color(255, 255, 255), new Color(219, 229, 241),
            new Color(248, 214, 205), new Color(251, 216, 166), new Color(225, 221, 252)
        };
        t.borderColors = new Color[] {
            new Color(119, 206, 130), new Color(206, 63, 34), new Color(242, 132, 35), new Color(124, 38, 19),
            new Color(157, 214, 245), new Color(173, 140, 127), new Color(249, 214, 205)
        };
        return t;
    }

    public static Theme dark() {
        Theme t = light();
        t.dark = true;
        t.diagramBackground = new Color(0x2f2f2f);
        t.entityRegularBackground = new Color(0x202020);
        t.entityPrimaryBackground = new Color(0x804020);
        t.entityAssociationBackground = new Color(0x404040);
        t.entityNameForeground = new Color(0xc4c4c4);
        t.linesForeground = new Color(0x0064ae);
        t.attrBackground = new Color(0x404040);
        t.attrForeground = new Color(0xc4c4c4);
        t.noteBackground = new Color(0x404040);
        t.noteForeground = new Color(0xc4c4c4);
        return t;
    }

    // ---- looks other than DBeaver's

    private static Theme base(boolean dark, Color background, Color header, Color headerFg, Color body, Color text,
                              Color muted, Color border, Color lines) {
        Theme t = dark ? dark() : light();
        t.diagramBackground = background;
        t.entityRegularBackground = header;
        t.entityPrimaryBackground = dark ? new Color(0x9a3412) : new Color(0xf97316);
        t.entityAssociationBackground = body;
        t.entityNameForeground = headerFg;
        t.headerForeground = headerFg;
        t.linesForeground = lines;
        t.borderColor = border;
        t.separatorColor = border;
        t.attrBackground = body;
        t.attrForeground = text;
        t.typeForeground = muted;
        t.noteBackground = body;
        t.noteForeground = text;
        t.headerColors = new Color[] {header, header, header, header, header, header, header};
        return t;
    }

    public static Theme cleanLight() {
        return base(false, Color.WHITE, new Color(0x475569), Color.WHITE, Color.WHITE, new Color(0x1e293b),
            new Color(0x64748b), new Color(0xcbd5e1), new Color(0x64748b));
    }

    public static Theme cleanDark() {
        return base(true, new Color(0x0f172a), new Color(0x334155), new Color(0xf1f5f9), new Color(0x1e293b),
            new Color(0xe2e8f0), new Color(0x94a3b8), new Color(0x475569), new Color(0x94a3b8));
    }

    public static Theme paperLight() {
        return base(false, Color.WHITE, Color.WHITE, Color.BLACK, Color.WHITE, Color.BLACK,
            new Color(0x555555), Color.BLACK, Color.BLACK);
    }

    public static Theme paperDark() {
        Color ink = new Color(0xe8e8e8);
        Color paper = new Color(0x1c1c1c);
        return base(true, paper, paper, ink, paper, ink, new Color(0xa0a0a0), ink, ink);
    }

    private static final Color[] PASTELS = {
        new Color(0xffadad), new Color(0xffd6a5), new Color(0xfdffb6), new Color(0xcaffbf),
        new Color(0x9bf6ff), new Color(0xa0c4ff), new Color(0xbdb2ff), new Color(0xffc6ff)
    };

    public static Theme pastelLight() {
        Theme t = base(false, Color.WHITE, PASTELS[5], new Color(0x1f2937), Color.WHITE, new Color(0x1f2937),
            new Color(0x6b7280), new Color(0x9ca3af), new Color(0x6b7280));
        t.tablePalette = PASTELS.clone();
        return t;
    }

    public static Theme pastelDark() {
        Color[] muted = new Color[PASTELS.length];
        for (int i = 0; i < PASTELS.length; i++) muted[i] = blend(PASTELS[i], new Color(0x1f2937), 45);
        Theme t = base(true, new Color(0x111827), muted[5], new Color(0xf9fafb), new Color(0x1f2937),
            new Color(0xe5e7eb), new Color(0x9ca3af), new Color(0x4b5563), new Color(0x9ca3af));
        t.tablePalette = muted;
        return t;
    }

    /** Colour keys accepted by {@code --color key=r,g,b}. */
    public Map<String, Color> asMap() {
        Map<String, Color> m = new LinkedHashMap<>();
        m.put("background", diagramBackground);
        m.put("entity-background", entityRegularBackground);
        m.put("entity-primary-background", entityPrimaryBackground);
        m.put("entity-association-background", entityAssociationBackground);
        m.put("entity-name-foreground", entityNameForeground);
        m.put("lines", linesForeground);
        m.put("attr-background", attrBackground);
        m.put("attr-foreground", attrForeground);
        m.put("note-background", noteBackground);
        m.put("note-foreground", noteForeground);
        m.put("border", border());
        m.put("type-foreground", typeForeground());
        m.put("separator", separator());
        for (int i = 0; i < headerColors.length; i++) m.put("header-" + (i + 1), headerColors[i]);
        return m;
    }

    public void set(String key, Color c) {
        switch (key) {
            case "background" -> diagramBackground = c;
            case "entity-background" -> entityRegularBackground = c;
            case "entity-primary-background" -> entityPrimaryBackground = c;
            case "entity-association-background" -> entityAssociationBackground = c;
            case "entity-name-foreground" -> entityNameForeground = c;
            case "lines" -> linesForeground = c;
            case "attr-background" -> attrBackground = c;
            case "attr-foreground" -> attrForeground = c;
            case "note-background" -> noteBackground = c;
            case "note-foreground" -> noteForeground = c;
            case "border" -> borderColor = c;
            case "type-foreground" -> typeForeground = c;
            case "separator" -> separatorColor = c;
            case "header-foreground" -> headerForeground = c;
            default -> {
                if (key.startsWith("header-")) {
                    int i = Integer.parseInt(key.substring("header-".length())) - 1;
                    if (i < 0 || i >= headerColors.length) throw new IllegalArgumentException("unknown colour key: " + key);
                    headerColors[i] = c;
                } else {
                    throw new IllegalArgumentException("unknown colour key: " + key);
                }
            }
        }
    }

    public Color headerColor(int containerIndex) {
        if (containerIndex <= 0) return entityRegularBackground;
        return headerColors[(containerIndex - 1) % headerColors.length];
    }

    /** UIStyles.getContrastColor: black on light backgrounds, white on dark ones. */
    public Color contrast(Color c) {
        if (c == null) return Color.BLACK;
        double luminance = 1 - (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255;
        if (luminance > 0.5) {
            return dark ? new Color(0xc4c4c4) : Color.WHITE;
        }
        return Color.BLACK;
    }

    /** UIUtils.blend(c1, c2, ratio): ratio percent of c1 mixed with (100-ratio) percent of c2. */
    public static Color blend(Color c1, Color c2, int ratio) {
        int r = (ratio * c1.getRed() + (100 - ratio) * c2.getRed()) / 100;
        int g = (ratio * c1.getGreen() + (100 - ratio) * c2.getGreen()) / 100;
        int b = (ratio * c1.getBlue() + (100 - ratio) * c2.getBlue()) / 100;
        return new Color(r, g, b);
    }

    /** Colour used for IDEF1X circles and notation labels (AssociationPart: blend(attrFg, contrast(attrFg), 60)). */
    public Color decorationColor() {
        return blend(attrForeground, contrast(attrForeground), 60);
    }

    public static Color parseColor(String s) {
        s = s.trim();
        if (s.startsWith("#")) {
            return new Color(Integer.parseInt(s.substring(1), 16));
        }
        String[] p = s.split(",");
        if (p.length != 3) throw new IllegalArgumentException("colour must be r,g,b or #rrggbb: " + s);
        return new Color(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
    }
}
