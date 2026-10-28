package io.dbeavererd.style;

import io.dbeavererd.model.Entity;

import java.awt.Color;

/**
 * A visual style for the whole diagram: box geometry (frame, corners, padding, separators), which
 * colours go where, and how relation lines are coloured. Colours come from a {@link Theme} that each
 * look provides in a light and a dark variant.
 */
public final class Look {
    /** DBeaver's ERD editor look (the default). */
    public static final String DBEAVER = "dbeaver";
    /** Rounded cards with a solid header bar and thin grey frame. */
    public static final String CLEAN = "clean";
    /** Monochrome, square, monospaced: prints well and works in black and white. */
    public static final String PAPER = "paper";
    /** Each table gets a pastel header colour by name; lines take the colour of the table they point to. */
    public static final String PASTEL = "pastel";

    public static final String[] NAMES = {DBEAVER, CLEAN, PAPER, PASTEL};

    public final String name;
    /** Width of the frame around a table. */
    public int borderWidth = 2;
    /** Corner radius of the frame and header; 0 = square. */
    public int cornerRadius = 0;
    public boolean titleBold = false;
    public boolean titleCentered = true;
    /** Show the table/view icon next to the title. */
    public boolean titleIcon = true;
    public int titleMarginH = 3;
    public int titleMarginV = 3;
    /** Width of the line drawn across the top of each attribute section (DBeaver: 2, in the border colour). */
    public int sectionTopLine = 2;
    /** Width of a separator line between the header and the rows and between key rows and other rows. */
    public int separator = 0;
    public int listMarginW = 5;
    public int listMarginH = 3;
    /** DBeaver's ColumnFigureBorder insets (0,-2,-2,-2), which make a section narrower and shorter than its content. */
    public int listInsetSide = -2;
    public int listInsetBottom = -2;
    public int itemMarginW = 5;
    public int itemMarginH = 2;
    /** Space between the type column and the frame (DBeaver: none beyond the list margin). */
    public int rightPad = 0;
    /** Draw the type column at the top of its row (DBeaver) rather than vertically centred. */
    public boolean rightTextTop = true;
    /** Use the line colour for end decorations and labels instead of DBeaver's blended grey. */
    public boolean decorationsUseLineColor = false;
    /** Colour relation lines after the parent (referenced) table's header instead of the theme's line colour. */
    public boolean linesByParent = false;
    /** Font family to use when none is given on the command line; null = the bundled font. */
    public String defaultFontFamily = null;

    private Look(String name) {
        this.name = name;
    }

    public static Look parse(String s) {
        return switch (s.trim().toLowerCase()) {
            case DBEAVER -> dbeaver();
            case CLEAN -> clean();
            case PAPER -> paper();
            case PASTEL -> pastel();
            default -> throw new IllegalArgumentException("unknown look: " + s + " (expected one of " + String.join(", ", NAMES) + ")");
        };
    }

    public static Look dbeaver() {
        return new Look(DBEAVER);
    }

    public static Look clean() {
        Look l = new Look(CLEAN);
        l.borderWidth = 1;
        l.cornerRadius = 6;
        l.titleBold = true;
        l.titleCentered = false;
        l.titleIcon = true;
        l.titleMarginH = 8;
        l.titleMarginV = 5;
        l.sectionTopLine = 0;
        l.separator = 1;
        l.listMarginW = 3;
        l.listMarginH = 3;
        l.listInsetSide = 0;
        l.listInsetBottom = 0;
        l.itemMarginW = 5;
        l.itemMarginH = 3;
        l.rightPad = 5;
        l.rightTextTop = false;
        l.decorationsUseLineColor = true;
        return l;
    }

    public static Look paper() {
        Look l = new Look(PAPER);
        l.borderWidth = 1;
        l.cornerRadius = 0;
        l.titleBold = true;
        l.titleCentered = true;
        l.titleIcon = false;
        l.titleMarginH = 8;
        l.titleMarginV = 4;
        l.sectionTopLine = 0;
        l.separator = 1;
        l.listMarginW = 3;
        l.listMarginH = 2;
        l.listInsetSide = 0;
        l.listInsetBottom = 0;
        l.itemMarginW = 5;
        l.itemMarginH = 2;
        l.rightPad = 5;
        l.rightTextTop = false;
        l.decorationsUseLineColor = true;
        l.defaultFontFamily = "Monospaced";
        return l;
    }

    public static Look pastel() {
        Look l = clean();
        return copy(l, PASTEL, c -> {
            c.cornerRadius = 8;
            c.titleBold = true;
            c.linesByParent = true;
        });
    }

    private static Look copy(Look from, String name, java.util.function.Consumer<Look> tweak) {
        Look l = new Look(name);
        l.borderWidth = from.borderWidth;
        l.cornerRadius = from.cornerRadius;
        l.titleBold = from.titleBold;
        l.titleCentered = from.titleCentered;
        l.titleIcon = from.titleIcon;
        l.titleMarginH = from.titleMarginH;
        l.titleMarginV = from.titleMarginV;
        l.sectionTopLine = from.sectionTopLine;
        l.separator = from.separator;
        l.listMarginW = from.listMarginW;
        l.listMarginH = from.listMarginH;
        l.listInsetSide = from.listInsetSide;
        l.listInsetBottom = from.listInsetBottom;
        l.itemMarginW = from.itemMarginW;
        l.itemMarginH = from.itemMarginH;
        l.rightPad = from.rightPad;
        l.rightTextTop = from.rightTextTop;
        l.decorationsUseLineColor = from.decorationsUseLineColor;
        l.linesByParent = from.linesByParent;
        l.defaultFontFamily = from.defaultFontFamily;
        tweak.accept(l);
        return l;
    }

    /** The colours this look is designed with, in a light or dark variant. */
    public Theme theme(boolean dark) {
        return switch (name) {
            case CLEAN -> dark ? Theme.cleanDark() : Theme.cleanLight();
            case PAPER -> dark ? Theme.paperDark() : Theme.paperLight();
            case PASTEL -> dark ? Theme.pastelDark() : Theme.pastelLight();
            default -> dark ? Theme.dark() : Theme.light();
        };
    }

    /** Header (and frame fill) colour of an entity. */
    public Color headerColor(Entity e, Theme theme) {
        if (theme.tablePalette != null && theme.tablePalette.length > 0) {
            return theme.tablePalette[Math.floorMod(e.name().hashCode(), theme.tablePalette.length)];
        }
        if (e.isPrimary()) return theme.entityPrimaryBackground;
        return theme.headerColor(e.containerIndex());
    }

    /** Colour of a relation line whose parent (referenced) table has the given header colour. */
    public Color lineColor(Color parentHeader, Theme theme) {
        if (!linesByParent || parentHeader == null) return theme.linesForeground;
        // darken on light backgrounds, keep the pastel itself on dark ones
        return theme.dark ? Theme.blend(parentHeader, Color.WHITE, 70) : Theme.blend(parentHeader, Color.BLACK, 55);
    }

    /** Colour of end decorations (IDEF1X dot, crow's-foot labels) for a line of the given colour. */
    public Color decorationColor(Color lineColor, Theme theme) {
        return decorationsUseLineColor ? lineColor : theme.decorationColor();
    }
}
