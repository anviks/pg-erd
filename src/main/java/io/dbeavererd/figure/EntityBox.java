package io.dbeavererd.figure;

import io.dbeavererd.model.Attribute;
import io.dbeavererd.model.Entity;
import io.dbeavererd.render.Canvas;
import io.dbeavererd.render.TextMeasurer;
import io.dbeavererd.style.AttributeVisibility;
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
 * All numbers are from org.jkiss.dbeaver.ui.editors.erd.figures.*.
 */
public final class EntityBox {
    public static final int BORDER = 2;        // ERDUIConstants.DEFAULT_ENTITY_BORDER_WIDTH
    static final int TITLE_MARGIN = 3;         // nameLabel.setBorder(new MarginBorder(3))
    static final int LIST_MARGIN_W = 5;        // AttributeListFigure GridLayout marginWidth
    static final int LIST_MARGIN_H = 3;        // AttributeListFigure GridLayout marginHeight
    static final int LIST_INSET_SIDE = -2;     // ColumnFigureBorder insets (0,-2,-2,-2)
    static final int LIST_INSET_BOTTOM = -2;
    static final int ITEM_MARGIN_W = 5;        // AttributeItemFigure GridLayout marginWidth
    static final int ITEM_MARGIN_H = 2;        // AttributeItemFigure GridLayout marginHeight
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

        Row(Attribute attribute, String label, String right, Font font, boolean icon, TextMeasurer m) {
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
            this.itemW = 2 * ITEM_MARGIN_W + labelW;
            this.itemH = 2 * ITEM_MARGIN_H + labelH;
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

        Section(List<Row> rows) {
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
            this.prefW = 2 * LIST_MARGIN_W + c1 + c2 + 2 * LIST_INSET_SIDE;
            this.prefH = 2 * LIST_MARGIN_H + rowsH + LIST_INSET_BOTTOM;
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
    private final Font titleFont;
    private final Font descFont;

    /** Position assigned by the layout (top-left corner). */
    public int x;
    public int y;

    public EntityBox(Entity entity, RenderOptions options, TextMeasurer m) {
        this.entity = entity;
        this.options = options;
        Theme theme = options.theme;
        titleFont = options.fonts.regular;
        descFont = options.fonts.regular;

        this.title = options.has(ViewStyle.ENTITY_FQN) ? entity.fullName() : entity.name();
        this.titleTextW = m.width(title, titleFont);
        this.titleTextH = m.height(titleFont);
        this.titleLabelH = Math.max(ICON, titleTextH);
        this.titleH = titleLabelH + 2 * TITLE_MARGIN;
        int titlePrefW = ICON + ICON_GAP + titleTextW + 2 * TITLE_MARGIN;

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
            Row r = new Row(a, attributeLabel(a, options), rightText(a, options), f, options.has(ViewStyle.ICONS), m);
            (a.inPrimaryKey() ? keyRows : attrRows).add(r);
        }
        this.keys = new Section(keyRows);
        this.attrs = new Section(attrRows);

        int w = Math.max(Math.max(titlePrefW, descW), Math.max(keys.prefW, attrs.prefW));
        this.width = w + 2 * BORDER;
        this.height = titleH + descH + keys.prefH + attrs.prefH + 2 * BORDER;

        // EntityFigure.refreshColors / getBorderColor (single data source -> index 0 -> lines colour)
        if (entity.isPrimary()) {
            background = theme.entityPrimaryBackground;
        } else {
            background = theme.headerColor(entity.containerIndex());
        }
        borderColor = theme.linesForeground;
        titleForeground = theme.contrast(background);
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

    public int right() { return x + width; }
    public int bottom() { return y + height; }
    public int centerX() { return x + width / 2; }
    public int centerY() { return y + height / 2; }

    public void paint(Canvas c) {
        Theme theme = options.theme;
        int cx = x + BORDER;                 // client area (inside the LineBorder)
        int cy = y + BORDER;
        int cw = width - 2 * BORDER;

        // EntityFigure is opaque: header colour under everything
        c.fillRect(x, y, width, height, background);

        // title label: MarginBorder(3), content centred horizontally (Label alignment CENTER)
        int labelX = cx + TITLE_MARGIN;
        int labelY = cy + TITLE_MARGIN;
        int labelW = cw - 2 * TITLE_MARGIN;
        int prefW = ICON + ICON_GAP + titleTextW;
        int offset = (labelW - prefW) / 2;
        int iconX = labelX + offset;
        int iconY = labelY + (titleLabelH - ICON) / 2;
        c.drawIcon(entity.isView() ? Icons.VIEW : Icons.TABLE, false, iconX, iconY);
        c.drawText(title, iconX + ICON + ICON_GAP, labelY + (titleLabelH - titleTextH) / 2, titleFont, titleForeground);
        int yy = cy + titleH;

        if (description != null) {
            c.drawText(description, cx + (cw - descW) / 2, yy, descFont, titleForeground);
            yy += descH;
        }

        yy = paintSection(c, keys, cx, yy, cw, theme);
        paintSection(c, attrs, cx, yy, cw, theme);

        // LineBorder(2): even width -> shrink by 1 and stroke the outline
        c.drawRect(x + 1, y + 1, width - 2, height - 2, BORDER, borderColor);
    }

    private int paintSection(Canvas c, Section s, int listX, int listY, int listW, Theme theme) {
        int listH = s.prefH;
        c.fillRect(listX, listY, listW, listH, theme.attrBackground);
        // ColumnFigureBorder: 2px line across the top in the border colour
        c.drawPolyline(new int[] {listX, listX + listW}, new int[] {listY, listY}, 2, 2, borderColor, null);

        // client area is the bounds expanded by the negative insets, then the GridLayout margins
        int x0 = listX + LIST_INSET_SIDE + LIST_MARGIN_W;
        int y0 = listY + LIST_MARGIN_H;
        int available = listW - 2 * LIST_INSET_SIDE - 2 * LIST_MARGIN_W;
        int col1 = available - s.col2W;   // column 1 grabs the excess
        int rightEdge = x0 + col1 + s.col2W;

        int ry = y0;
        for (Row r : s.rows) {
            int lx = x0 + ITEM_MARGIN_W;
            int ly = ry + ITEM_MARGIN_H;
            int tx = lx;
            if (r.icon) {
                c.drawIcon(r.attribute.iconId(), r.attribute.inPrimaryKey(), lx, ly + (r.labelH - ICON) / 2);
                tx += ICON + ICON_GAP;
            }
            c.drawText(r.label, tx, ly + (r.labelH - r.textH) / 2, r.font, theme.attrForeground);
            if (!r.right.isEmpty()) {
                // right panel: HORIZONTAL_ALIGN_END | VERTICAL_ALIGN_BEGINNING, preferred size
                c.drawText(r.right, rightEdge - r.rightW, ry, r.font, theme.attrForeground);
            }
            ry += r.itemH;
        }
        return listY + listH;
    }
}
