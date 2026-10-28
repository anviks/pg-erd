package io.dbeavererd.figure;

import io.dbeavererd.geom.Rect;
import io.dbeavererd.model.Attribute;
import io.dbeavererd.model.Entity;
import io.dbeavererd.render.Canvas;
import io.dbeavererd.render.TextMeasurer;
import io.dbeavererd.style.AttributeVisibility;
import io.dbeavererd.style.Look;
import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.style.Theme;
import io.dbeavererd.style.ViewStyle;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * Measurement and painting of one entity, reproducing DBeaver's figure tree:
 * <pre>
 * EntityFigure            GridLayout(1), margins 0, LineBorder(2)
 *   title  EditableLabel  icon + text, MarginBorder(3), content centred
 *   [desc  Label]         only with the COMMENTS style
 *   keys   AttributeListFigure   GridLayout(2) marginW 5 marginH 3, insets (0,-2,-2,-2), 2px top line
 *     row  AttributeItemFigure  GridLayout(3) marginW 5 marginH 2 hSpacing 3 -> Label(icon, name)
 *          right panel   Label(type [NOT NULL]) aligned END / BEGINNING
 *   attrs  AttributeListFigure   same as keys
 * </pre>
 * All numbers are from org.jkiss.dbeaver.ui.editors.erd.figures.* and are the defaults of
 * {@link Look#dbeaver()}; other looks change the paddings, frame and separators.
 */
public final class EntityBox {
    /** DBeaver's frame width (ERDUIConstants.DEFAULT_ENTITY_BORDER_WIDTH). */
    public static final int BORDER = 2;
    static final int ICON_GAP = 3;             // Draw2D Label iconTextGap
    static final int ICON = Icons.SIZE;

    /** One attribute row (name label + right panel) with its measured sizes. */
    public static final class Row {
        public final Attribute attribute;
        public final String label;
        public final String right;
        public final Font font;
        public final boolean icon;
        public final int labelTextW;
        public final int labelW;
        public final int labelH;
        public final int itemW;
        public final int itemH;
        public final int rightW;
        public final int textH;

        Row(Attribute attribute, String label, String right, Font font, boolean icon, TextMeasurer m, Look look) {
            this.attribute = attribute;
            this.label = label;
            this.right = right;
            this.font = font;
            this.icon = icon;
            this.textH = m.height(font);
            this.labelTextW = m.width(label, font);
            int gap = icon && !label.isEmpty() ? ICON_GAP : 0;
            this.labelW = (icon ? ICON : 0) + gap + labelTextW;
            this.labelH = Math.max(icon ? ICON : 0, textH);
            this.itemW = 2 * look.itemMarginW + labelW;
            this.itemH = 2 * look.itemMarginH + labelH;
            this.rightW = m.width(right, font);
        }
    }

    /** A key or attribute list section. */
    public static final class Section {
        public final List<Row> rows;
        public final int col1W;
        public final int col2W;
        public final int prefW;
        public final int prefH;

        Section(List<Row> rows, Look look) {
            this.rows = rows;
            int c1 = 0;
            int c2 = 0;
            int rowsH = 0;
            for (Row r : rows) {
                c1 = Math.max(c1, r.itemW);
                c2 = Math.max(c2, r.rightW);
                rowsH += r.itemH;
            }
            this.col1W = c1;
            this.col2W = c2;
            // GridLayout preferred size plus the (negative) border insets
            this.prefW = rows.isEmpty() && look.sectionTopLine == 0 ? 0 : 2 * look.listMarginW + c1 + c2 + 2 * look.listInsetSide + look.rightPad;
            // DBeaver keeps an empty key section (4 px with its top line); other looks drop it
            this.prefH = rows.isEmpty() && look.sectionTopLine == 0 ? 0 : 2 * look.listMarginH + rowsH + look.listInsetBottom;
        }
    }

    public final Entity entity;
    public final String title;
    public final String description;
    public final Section keys;
    public final Section attrs;
    public final int titleTextW;
    public final int titleTextH;
    public final int titleLabelH;
    public final int titleH;
    public final int descW;
    public final int descH;
    public final int width;
    public final int height;
    public final Color background;
    public final Color borderColor;
    public final Color titleForeground;
    private final RenderOptions options;
    private final Look look;
    private final int border;
    private final Font titleFont;
    private final Font descFont;

    /** Position assigned by the layout (top-left corner). */
    public int x;
    public int y;

    public EntityBox(Entity entity, RenderOptions options, TextMeasurer m) {
        this.entity = entity;
        this.options = options;
        this.look = options.look;
        this.border = look.borderWidth;
        Theme theme = options.theme;
        titleFont = look.titleBold ? options.fonts.bold : options.fonts.regular;
        descFont = options.fonts.regular;

        this.title = options.has(ViewStyle.ENTITY_FQN) ? entity.fullName() : entity.name();
        this.titleTextW = m.width(title, titleFont);
        this.titleTextH = m.height(titleFont);
        this.titleLabelH = look.titleIcon ? Math.max(ICON, titleTextH) : titleTextH;
        this.titleH = titleLabelH + 2 * look.titleMarginV;
        int titlePrefW = titleContentW() + 2 * look.titleMarginH;

        String desc = null;
        if (options.has(ViewStyle.COMMENTS) && entity.comment() != null && !entity.comment().isEmpty()) {
            desc = entity.comment();
        }
        this.description = desc;
        this.descW = desc == null ? 0 : m.width(desc, descFont);
        this.descH = desc == null ? 0 : m.height(descFont);

        List<Row> keyRows = new ArrayList<>();
        List<Row> attrRows = new ArrayList<>();
        for (Attribute a : visibleAttributes(entity, options)) {
            Font f = a.inPrimaryKey() ? options.fonts.bold : options.fonts.regular;
            Row r = new Row(a, attributeLabel(a, options), rightText(a, options), f, options.has(ViewStyle.ICONS), m, look);
            (a.inPrimaryKey() ? keyRows : attrRows).add(r);
        }
        this.keys = new Section(keyRows, look);
        this.attrs = new Section(attrRows, look);

        int w = Math.max(Math.max(titlePrefW, descW), Math.max(keys.prefW, attrs.prefW));
        this.width = w + 2 * border;
        this.height = titleH + descH + keys.prefH + attrs.prefH + separatorsH() + 2 * border;

        // EntityFigure.refreshColors / getBorderColor (single data source -> index 0 -> lines colour)
        background = look.headerColor(entity, theme);
        borderColor = look.linesByParent ? look.lineColor(background, theme) : theme.border();
        titleForeground = theme.headerForeground(background);
    }

    private int titleContentW() {
        return (look.titleIcon ? ICON + ICON_GAP : 0) + titleTextW;
    }

    /** Height taken by separator lines: one under the header, one more between the key and attribute rows. */
    private int separatorsH() {
        if (look.separator == 0) return 0;
        return look.separator + (keys.rows.isEmpty() || attrs.rows.isEmpty() ? 0 : look.separator);
    }

    /** ERDContentProviderDefault.fillEntityFromObject: visibility filter and optional alphabetical sort. */
    public static List<Attribute> visibleAttributes(Entity entity, RenderOptions options) {
        List<Attribute> list = new ArrayList<>();
        AttributeVisibility v = options.visibility;
        if (v == AttributeVisibility.NONE) return list;
        for (Attribute a : entity.attributes()) {
            boolean show = switch (v) {
                case ALL -> true;
                case PRIMARY -> a.inPrimaryKey();
                case KEYS -> a.inPrimaryKey() || a.inForeignKey() || a.inAnyConstraint();
                case NONE -> false;
            };
            if (show) list.add(a);
        }
        if (options.has(ViewStyle.ALPHABETICAL_ORDER)) {
            list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        }
        return list;
    }

    /** ERDUIUtils.getFullAttributeLabel(includeType=false). */
    static String attributeLabel(Attribute a, RenderOptions options) {
        String s = a.name();
        if (options.has(ViewStyle.COMMENTS) && a.comment() != null && !a.comment().isEmpty()) {
            s += " - " + a.comment();
        }
        return s;
    }

    /** AttributeItemFigure.updateLabels right panel text. */
    static String rightText(Attribute a, RenderOptions options) {
        String s = "";
        if (options.has(ViewStyle.TYPES)) s = a.fullTypeName();
        if (options.has(ViewStyle.NULLABILITY) && a.required()) s += " NOT NULL";
        return s;
    }

    /**
     * Full-width rectangle of the row showing {@code attributeName}, or null if that column is not
     * displayed (filtered by visibility or unknown).
     */
    public Rect rowBounds(String attributeName) {
        int yy = y + border + titleH + descH + look.separator;
        for (Section s : new Section[] {keys, attrs}) {
            int ry = yy + look.listMarginH;
            for (Row r : s.rows) {
                if (r.attribute.name().equals(attributeName)) {
                    return new Rect(x, ry, width, r.itemH);
                }
                ry += r.itemH;
            }
            yy += s.prefH;
            if (!s.rows.isEmpty() && s == keys && !attrs.rows.isEmpty()) yy += look.separator;
        }
        return null;
    }

    public Rect bounds() { return new Rect(x, y, width, height); }

    public int right() { return x + width; }
    public int bottom() { return y + height; }
    public int centerX() { return x + width / 2; }
    public int centerY() { return y + height / 2; }

    public void paint(Canvas c) {
        Theme theme = options.theme;
        int cx = x + border;                 // client area (inside the frame)
        int cy = y + border;
        int cw = width - 2 * border;
        int radius = look.cornerRadius;

        // the whole figure is opaque in the header colour; the rows area is painted over it
        if (radius > 0) {
            c.fillRoundRect(x, y, width, height, radius, background);
        } else {
            c.fillRect(x, y, width, height, background);
        }

        // title label: content centred (DBeaver) or left-aligned
        int labelX = cx + look.titleMarginH;
        int labelY = cy + look.titleMarginV;
        int labelW = cw - 2 * look.titleMarginH;
        int offset = look.titleCentered ? (labelW - titleContentW()) / 2 : 0;
        int tx = labelX + offset;
        if (look.titleIcon) {
            c.drawIcon(entity.isView() ? Icons.VIEW : Icons.TABLE, false, tx, labelY + (titleLabelH - ICON) / 2);
            tx += ICON + ICON_GAP;
        }
        c.drawText(title, tx, labelY + (titleLabelH - titleTextH) / 2, titleFont, titleForeground);
        int yy = cy + titleH;

        if (description != null) {
            c.drawText(description, cx + (cw - descW) / 2, yy, descFont, titleForeground);
            yy += descH;
        }

        // rows area: square at the top, following the frame's rounding at the bottom
        int bodyTop = yy;
        int bodyH = y + height - bodyTop;
        if (bodyH > 0) {
            if (radius > 0) {
                int r = Math.min(radius, bodyH / 2);
                c.fillRect(x, bodyTop, width, bodyH - r, theme.attrBackground);
                c.fillRoundRect(x, y + height - 2 * r, width, 2 * r, r, theme.attrBackground);
            } else {
                c.fillRect(x, bodyTop, width, bodyH, theme.attrBackground);
            }
        }
        if (look.separator > 0) {
            separator(c, yy, theme);
            yy += look.separator;
        }

        yy = paintSection(c, keys, cx, yy, cw, theme);
        if (look.separator > 0 && !keys.rows.isEmpty() && !attrs.rows.isEmpty()) {
            separator(c, yy, theme);
            yy += look.separator;
        }
        paintSection(c, attrs, cx, yy, cw, theme);

        // frame: a stroke of width w is inset by w/2 so it stays inside the figure (Draw2D LineBorder)
        int in = border / 2;
        int shrink = 2 * in + border % 2;
        if (radius > 0) {
            c.drawRoundRect(x + in, y + in, width - shrink, height - shrink, Math.max(0, radius - in), border, borderColor);
        } else {
            c.drawRect(x + in, y + in, width - shrink, height - shrink, border, borderColor);
        }
    }

    private void separator(Canvas c, int yy, Theme theme) {
        int half = look.separator / 2;
        c.drawPolyline(new int[] {x, x + width}, new int[] {yy + half, yy + half}, 2, look.separator, theme.separator(), null);
    }

    private int paintSection(Canvas c, Section s, int listX, int listY, int listW, Theme theme) {
        int listH = s.prefH;
        if (listH == 0) return listY;
        if (look.sectionTopLine > 0) {
            // ColumnFigureBorder: 2px line across the top in the border colour
            c.drawPolyline(new int[] {listX, listX + listW}, new int[] {listY, listY}, 2, look.sectionTopLine, borderColor, null);
        }

        // client area is the bounds expanded by the negative insets, then the GridLayout margins
        int x0 = listX + look.listInsetSide + look.listMarginW;
        int y0 = listY + look.listMarginH;
        int available = listW - 2 * look.listInsetSide - 2 * look.listMarginW;
        int col1 = available - s.col2W - look.rightPad;   // column 1 grabs the excess
        int rightEdge = x0 + col1 + s.col2W;

        int ry = y0;
        for (Row r : s.rows) {
            int lx = x0 + look.itemMarginW;
            int ly = ry + look.itemMarginH;
            int tx = lx;
            if (r.icon) {
                c.drawIcon(r.attribute.iconId(), r.attribute.inPrimaryKey(), lx, ly + (r.labelH - ICON) / 2);
                tx += ICON + ICON_GAP;
            }
            c.drawText(r.label, tx, ly + (r.labelH - r.textH) / 2, r.font, theme.attrForeground);
            if (!r.right.isEmpty()) {
                // right panel: HORIZONTAL_ALIGN_END, at the row top (DBeaver) or vertically centred
                int ty = look.rightTextTop ? ry : ly + (r.labelH - r.textH) / 2;
                c.drawText(r.right, rightEdge - r.rightW, ty, r.font, theme.typeForeground());
            }
            ry += r.itemH;
        }
        return listY + listH;
    }
}
