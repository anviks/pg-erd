import { Rect } from '../geom.js';
import type { Attribute, Entity } from '../model/index.js';
import type { Look } from '../style/look.js';
import type { RenderOptions } from '../style/options.js';
import type { Color, Theme } from '../style/theme.js';
import type { FontSet, FontStyle } from '../text/fonts.js';
import type { SvgCanvas } from '../render/canvas.js';
import { ICON_SIZE, TABLE_ICON, VIEW_ICON } from './icons.js';

/** DBeaver's frame width (ERDUIConstants.DEFAULT_ENTITY_BORDER_WIDTH). */
export const BORDER = 2;
const ICON_GAP = 3; // Draw2D Label iconTextGap
const ICON = ICON_SIZE;

/** One attribute row (name label + right panel) with its measured sizes. */
export class Row {
  readonly textH: number;
  readonly labelTextW: number;
  readonly labelW: number;
  readonly labelH: number;
  readonly itemW: number;
  readonly itemH: number;
  readonly rightW: number;

  constructor(
    readonly attribute: Attribute,
    readonly label: string,
    readonly right: string,
    readonly font: FontStyle,
    readonly icon: boolean,
    m: FontSet,
    look: Look,
  ) {
    this.textH = m.height(font);
    this.labelTextW = m.widthInt(label, font);
    const gap = icon && label ? ICON_GAP : 0;
    this.labelW = (icon ? ICON : 0) + gap + this.labelTextW;
    this.labelH = Math.max(icon ? ICON : 0, this.textH);
    this.itemW = 2 * look.itemMarginW + this.labelW;
    this.itemH = 2 * look.itemMarginH + this.labelH;
    this.rightW = m.widthInt(right, font);
  }
}

/** A key or attribute list section. */
export class Section {
  readonly col1W: number;
  readonly col2W: number;
  readonly prefW: number;
  readonly prefH: number;

  constructor(readonly rows: Row[], look: Look) {
    let c1 = 0;
    let c2 = 0;
    let rowsH = 0;
    for (const r of rows) {
      c1 = Math.max(c1, r.itemW);
      c2 = Math.max(c2, r.rightW);
      rowsH += r.itemH;
    }
    this.col1W = c1;
    this.col2W = c2;
    const empty = rows.length === 0 && look.sectionTopLine === 0;
    // GridLayout preferred size plus the (negative) border insets; DBeaver keeps an empty key section
    this.prefW = empty ? 0 : 2 * look.listMarginW + c1 + c2 + 2 * look.listInsetSide + look.rightPad;
    this.prefH = empty ? 0 : 2 * look.listMarginH + rowsH + look.listInsetBottom;
  }
}

/**
 * Measurement and painting of one entity, reproducing DBeaver's figure tree (EntityFigure with a
 * title label, optional description, key section and attribute section). The numbers are the
 * defaults of the DBeaver look; other looks change paddings, frame and separators.
 */
export class EntityBox {
  readonly title: string;
  readonly description: string | null;
  readonly keys: Section;
  readonly attrs: Section;
  readonly titleTextW: number;
  readonly titleTextH: number;
  readonly titleLabelH: number;
  readonly titleH: number;
  readonly descW: number;
  readonly descH: number;
  readonly width: number;
  readonly height: number;
  readonly background: Color;
  readonly borderColor: Color;
  readonly titleForeground: Color;
  private readonly look: Look;
  private readonly border: number;
  private readonly titleFont: FontStyle;
  private readonly descFont: FontStyle;

  /** Position assigned by the layout (top-left corner). */
  x = 0;
  y = 0;

  constructor(
    readonly entity: Entity,
    private readonly options: RenderOptions,
    m: FontSet,
  ) {
    const look = options.look;
    this.look = look;
    this.border = look.borderWidth;
    const theme = options.theme;
    this.titleFont = look.titleBold ? options.fonts.bold : options.fonts.regular;
    this.descFont = options.fonts.regular;

    this.title = options.has('fqn') ? entity.fullName : entity.name;
    this.titleTextW = m.widthInt(this.title, this.titleFont);
    this.titleTextH = m.height(this.titleFont);
    this.titleLabelH = look.titleIcon ? Math.max(ICON, this.titleTextH) : this.titleTextH;
    this.titleH = this.titleLabelH + 2 * look.titleMarginV;
    const titlePrefW = this.titleContentW() + 2 * look.titleMarginH;

    this.description = options.has('comments') && entity.comment ? entity.comment : null;
    this.descW = this.description ? m.widthInt(this.description, this.descFont) : 0;
    this.descH = this.description ? m.height(this.descFont) : 0;

    const keyRows: Row[] = [];
    const attrRows: Row[] = [];
    for (const a of visibleAttributes(entity, options)) {
      const f = a.inPrimaryKey ? options.fonts.bold : options.fonts.regular;
      const r = new Row(a, attributeLabel(a, options), rightText(a, options), f, options.has('icons'), m, look);
      (a.inPrimaryKey ? keyRows : attrRows).push(r);
    }
    this.keys = new Section(keyRows, look);
    this.attrs = new Section(attrRows, look);

    const w = Math.max(titlePrefW, this.descW, this.keys.prefW, this.attrs.prefW);
    this.width = w + 2 * this.border;
    this.height = this.titleH + this.descH + this.keys.prefH + this.attrs.prefH + this.separatorsH() + 2 * this.border;

    this.background = look.headerColor(entity, theme);
    this.borderColor = look.linesByParent ? look.lineColor(this.background, theme) : theme.border();
    this.titleForeground = theme.headerFg(this.background);
  }

  private titleContentW(): number {
    return (this.look.titleIcon ? ICON + ICON_GAP : 0) + this.titleTextW;
  }

  /** Height taken by separator lines: one under the header, one more between key and attribute rows. */
  private separatorsH(): number {
    if (this.look.separator === 0) return 0;
    const both = this.keys.rows.length > 0 && this.attrs.rows.length > 0;
    return this.look.separator + (both ? this.look.separator : 0);
  }

  /** Full-width rectangle of the row showing `attributeName`, or null if that column is not displayed. */
  rowBounds(attributeName: string): Rect | null {
    let yy = this.y + this.border + this.titleH + this.descH + this.look.separator;
    for (const s of [this.keys, this.attrs]) {
      let ry = yy + this.look.listMarginH;
      for (const r of s.rows) {
        if (r.attribute.name === attributeName) return new Rect(this.x, ry, this.width, r.itemH);
        ry += r.itemH;
      }
      yy += s.prefH;
      if (s === this.keys && s.rows.length > 0 && this.attrs.rows.length > 0) yy += this.look.separator;
    }
    return null;
  }

  bounds(): Rect {
    return new Rect(this.x, this.y, this.width, this.height);
  }
  right(): number {
    return this.x + this.width;
  }
  bottom(): number {
    return this.y + this.height;
  }
  centerX(): number {
    return this.x + Math.trunc(this.width / 2);
  }
  centerY(): number {
    return this.y + Math.trunc(this.height / 2);
  }

  paint(c: SvgCanvas): void {
    const theme = this.options.theme;
    const look = this.look;
    const { x, y, width, height, border } = this;
    const cx = x + border;
    const cy = y + border;
    const cw = width - 2 * border;
    const radius = look.cornerRadius;

    // the whole figure is opaque in the header colour; the rows area is painted over it
    if (radius > 0) c.fillRoundRect(x, y, width, height, radius, this.background);
    else c.fillRect(x, y, width, height, this.background);

    // title label: content centred (DBeaver) or left-aligned
    const labelX = cx + look.titleMarginH;
    const labelY = cy + look.titleMarginV;
    const labelW = cw - 2 * look.titleMarginH;
    const offset = look.titleCentered ? Math.trunc((labelW - this.titleContentW()) / 2) : 0;
    let tx = labelX + offset;
    if (look.titleIcon) {
      c.drawIcon(this.entity.isView ? VIEW_ICON : TABLE_ICON, false, tx, labelY + Math.trunc((this.titleLabelH - ICON) / 2));
      tx += ICON + ICON_GAP;
    }
    c.drawText(this.title, tx, labelY + Math.trunc((this.titleLabelH - this.titleTextH) / 2), this.titleFont, this.titleForeground);
    let yy = cy + this.titleH;

    if (this.description) {
      c.drawText(this.description, cx + Math.trunc((cw - this.descW) / 2), yy, this.descFont, this.titleForeground);
      yy += this.descH;
    }

    // rows area: square at the top, following the frame's rounding at the bottom
    const bodyTop = yy;
    const bodyH = y + height - bodyTop;
    if (bodyH > 0) {
      if (radius > 0) {
        const r = Math.min(radius, Math.trunc(bodyH / 2));
        c.fillRect(x, bodyTop, width, bodyH - r, theme.attrBackground);
        c.fillRoundRect(x, y + height - 2 * r, width, 2 * r, r, theme.attrBackground);
      } else {
        c.fillRect(x, bodyTop, width, bodyH, theme.attrBackground);
      }
    }
    if (look.separator > 0) {
      this.separator(c, yy, theme);
      yy += look.separator;
    }

    yy = this.paintSection(c, this.keys, cx, yy, cw, theme);
    if (look.separator > 0 && this.keys.rows.length > 0 && this.attrs.rows.length > 0) {
      this.separator(c, yy, theme);
      yy += look.separator;
    }
    this.paintSection(c, this.attrs, cx, yy, cw, theme);

    // frame: a stroke of width w is inset by w/2 so it stays inside the figure (Draw2D LineBorder)
    const inset = Math.trunc(border / 2);
    const shrink = 2 * inset + (border % 2);
    if (radius > 0) {
      c.drawRoundRect(x + inset, y + inset, width - shrink, height - shrink, Math.max(0, radius - inset), border, this.borderColor);
    } else {
      c.drawRect(x + inset, y + inset, width - shrink, height - shrink, border, this.borderColor);
    }
  }

  private separator(c: SvgCanvas, yy: number, theme: Theme): void {
    const half = Math.trunc(this.look.separator / 2);
    c.drawPolyline([this.x, this.x + this.width], [yy + half, yy + half], this.look.separator, theme.separator());
  }

  private paintSection(c: SvgCanvas, s: Section, listX: number, listY: number, listW: number, theme: Theme): number {
    const look = this.look;
    const listH = s.prefH;
    if (listH === 0) return listY;
    if (look.sectionTopLine > 0) {
      // ColumnFigureBorder: 2px line across the top in the border colour
      c.drawPolyline([listX, listX + listW], [listY, listY], look.sectionTopLine, this.borderColor);
    }
    // client area is the bounds expanded by the negative insets, then the GridLayout margins
    const x0 = listX + look.listInsetSide + look.listMarginW;
    const y0 = listY + look.listMarginH;
    const available = listW - 2 * look.listInsetSide - 2 * look.listMarginW;
    const col1 = available - s.col2W - look.rightPad; // column 1 grabs the excess
    const rightEdge = x0 + col1 + s.col2W;

    let ry = y0;
    for (const r of s.rows) {
      const lx = x0 + look.itemMarginW;
      const ly = ry + look.itemMarginH;
      let tx = lx;
      if (r.icon) {
        c.drawIcon(r.attribute.iconId, r.attribute.inPrimaryKey, lx, ly + Math.trunc((r.labelH - ICON) / 2));
        tx += ICON + ICON_GAP;
      }
      c.drawText(r.label, tx, ly + Math.trunc((r.labelH - r.textH) / 2), r.font, theme.attrForeground);
      if (r.right) {
        // right panel: HORIZONTAL_ALIGN_END, at the row top (DBeaver) or vertically centred
        const ty = look.rightTextTop ? ry : ly + Math.trunc((r.labelH - r.textH) / 2);
        c.drawText(r.right, rightEdge - r.rightW, ty, r.font, theme.typeFg());
      }
      ry += r.itemH;
    }
    return listY + listH;
  }
}

/** ERDContentProviderDefault.fillEntityFromObject: visibility filter and optional alphabetical sort. */
export function visibleAttributes(entity: Entity, options: RenderOptions): Attribute[] {
  if (options.visibility === 'none') return [];
  const list = entity.attributes.filter((a) => {
    switch (options.visibility) {
      case 'all': return true;
      case 'primary': return a.inPrimaryKey;
      case 'keys': return a.inPrimaryKey || a.inForeignKey || a.inAnyConstraint;
      default: return false;
    }
  });
  if (options.has('alpha')) {
    list.sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  }
  return list;
}

/** ERDUIUtils.getFullAttributeLabel(includeType=false). */
export function attributeLabel(a: Attribute, options: RenderOptions): string {
  let s = a.name;
  if (options.has('comments') && a.comment) s += ` - ${a.comment}`;
  return s;
}

/** AttributeItemFigure.updateLabels right panel text. */
export function rightText(a: Attribute, options: RenderOptions): string {
  let s = '';
  if (options.has('types')) s = a.fullTypeName;
  if (options.has('nullability') && a.required) s += ' NOT NULL';
  return s;
}
