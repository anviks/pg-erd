import { Rect } from '../geom.js';
import type { Attribute, Entity } from '../model/index.js';
import type { Look } from '../style/look.js';
import type { RenderOptions } from '../style/options.js';
import type { Color, Theme } from '../style/theme.js';
import type { FontSet, FontStyle } from '../text/fonts.js';
import type { SvgCanvas } from '../render/canvas.js';
import { ICON_SIZE, TABLE_ICON, VIEW_ICON } from './icons.js';

const ICON_GAP = 3;
/** Minimum space between the column name and the type column. */
const COLUMN_GAP = 12;

/** One attribute row: icon + name on the left, type / NOT NULL on the right. */
export class Row {
  readonly textH: number;
  readonly labelW: number;
  readonly rightW: number;
  readonly height: number;

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
    const textW = m.widthInt(label, font);
    this.labelW = (icon ? ICON_SIZE + (label ? ICON_GAP : 0) : 0) + textW;
    this.rightW = m.widthInt(right, font);
    this.height = 2 * look.rowPadY + Math.max(icon ? ICON_SIZE : 0, this.textH);
  }
}

/** A key or attribute section: its rows and height (0 when empty). */
export class Section {
  readonly height: number;
  constructor(readonly rows: Row[], look: Look) {
    this.height = rows.length === 0 ? 0 : 2 * look.padY + rows.reduce((s, r) => s + r.height, 0);
  }
}

/** Measurement and painting of one table: title (with optional comment), key rows, other rows. */
export class EntityBox {
  readonly title: string;
  readonly description: string | null;
  readonly keys: Section;
  readonly attrs: Section;
  readonly titleTextW: number;
  readonly titleTextH: number;
  readonly titleH: number;
  readonly descW: number;
  readonly descH: number;
  /** Width of the name column (icon + widest name) and of the type column. */
  readonly col1W: number;
  readonly col2W: number;
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
    this.titleH = 2 * look.titlePadY + (look.titleIcon ? Math.max(ICON_SIZE, this.titleTextH) : this.titleTextH);

    this.description = options.has('comments') && entity.comment ? entity.comment : null;
    this.descW = this.description ? m.widthInt(this.description, this.descFont) : 0;
    this.descH = this.description ? m.height(this.descFont) + look.titlePadY : 0;

    const keyRows: Row[] = [];
    const attrRows: Row[] = [];
    for (const a of visibleAttributes(entity, options)) {
      const f = a.inPrimaryKey ? options.fonts.bold : options.fonts.regular;
      const r = new Row(a, attributeLabel(a, options), rightText(a, options), f, options.has('icons'), m, look);
      (a.inPrimaryKey ? keyRows : attrRows).push(r);
    }
    this.keys = new Section(keyRows, look);
    this.attrs = new Section(attrRows, look);
    const rows = [...keyRows, ...attrRows];
    this.col1W = Math.max(0, ...rows.map((r) => r.labelW));
    this.col2W = Math.max(0, ...rows.map((r) => r.rightW));

    const titleW = this.titleContentW() + 2 * look.titlePadX;
    const rowsW = rows.length === 0 ? 0 : 2 * look.padX + this.col1W + (this.col2W > 0 ? COLUMN_GAP + this.col2W : 0);
    const descW = this.descW > 0 ? this.descW + 2 * look.titlePadX : 0;
    this.width = Math.max(titleW, rowsW, descW) + 2 * this.border;
    this.height = 2 * this.border + this.titleH + this.descH + this.separatorsH() + this.keys.height + this.attrs.height;

    this.background = look.headerColor(entity, theme);
    this.borderColor = look.linesByParent ? look.lineColor(this.background, theme) : theme.border;
    this.titleForeground = theme.headerTextOn(this.background);
  }

  private titleContentW(): number {
    return (this.look.titleIcon ? ICON_SIZE + ICON_GAP : 0) + this.titleTextW;
  }

  private hasRows(): boolean {
    return this.keys.rows.length + this.attrs.rows.length > 0;
  }

  private bothSections(): boolean {
    return this.keys.rows.length > 0 && this.attrs.rows.length > 0;
  }

  /** Height of the separator lines: one under the header (if there are rows), one between the sections. */
  private separatorsH(): number {
    return (this.hasRows() ? this.look.separator : 0) + (this.bothSections() ? this.look.separator : 0);
  }

  /** Full-width rectangle of the row showing `attributeName`, or null if that column is not displayed. */
  rowBounds(attributeName: string): Rect | null {
    let yy = this.y + this.border + this.titleH + this.descH + (this.hasRows() ? this.look.separator : 0);
    for (const s of [this.keys, this.attrs]) {
      if (s.rows.length === 0) continue;
      let ry = yy + this.look.padY;
      for (const r of s.rows) {
        if (r.attribute.name === attributeName) return new Rect(this.x, ry, this.width, r.height);
        ry += r.height;
      }
      yy += s.height + this.look.separator;
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

    // header colour under everything; the rows area is painted over it
    if (radius > 0) c.fillRoundRect(x, y, width, height, radius, this.background);
    else c.fillRect(x, y, width, height, this.background);

    // title
    const innerH = this.titleH - 2 * look.titlePadY;
    const labelY = cy + look.titlePadY;
    const labelW = cw - 2 * look.titlePadX;
    let tx = cx + look.titlePadX + (look.titleCentered ? Math.trunc((labelW - this.titleContentW()) / 2) : 0);
    if (look.titleIcon) {
      c.drawIcon(this.entity.isView ? VIEW_ICON : TABLE_ICON, false, tx, labelY + Math.trunc((innerH - ICON_SIZE) / 2));
      tx += ICON_SIZE + ICON_GAP;
    }
    c.drawText(this.title, tx, labelY + Math.trunc((innerH - this.titleTextH) / 2), this.titleFont, this.titleForeground);
    let yy = cy + this.titleH;
    if (this.description) {
      const dx = look.titleCentered ? cx + Math.trunc((cw - this.descW) / 2) : cx + look.titlePadX;
      c.drawText(this.description, dx, yy, this.descFont, this.titleForeground);
      yy += this.descH;
    }

    if (this.hasRows()) {
      // rows area: square at the top, following the frame's rounding at the bottom
      const bodyTop = yy;
      const bodyH = y + height - bodyTop;
      if (radius > 0) {
        const r = Math.min(radius, Math.trunc(bodyH / 2));
        c.fillRect(x, bodyTop, width, bodyH - r, theme.body);
        c.fillRoundRect(x, y + height - 2 * r, width, 2 * r, r, theme.body);
      } else {
        c.fillRect(x, bodyTop, width, bodyH, theme.body);
      }
      this.separator(c, yy, theme);
      yy += look.separator;
      yy = this.paintSection(c, this.keys, cx, yy, cw, theme);
      if (this.bothSections()) {
        this.separator(c, yy, theme);
        yy += look.separator;
      }
      this.paintSection(c, this.attrs, cx, yy, cw, theme);
    }

    // frame: a stroke of width w is inset by w/2 so it stays inside the figure
    const inset = Math.trunc(border / 2);
    const shrink = 2 * inset + (border % 2);
    if (radius > 0) {
      c.drawRoundRect(x + inset, y + inset, width - shrink, height - shrink, Math.max(0, radius - inset), border, this.borderColor);
    } else {
      c.drawRect(x + inset, y + inset, width - shrink, height - shrink, border, this.borderColor);
    }
  }

  private separator(c: SvgCanvas, yy: number, theme: Theme): void {
    const w = this.look.separator;
    if (w === 0) return;
    const half = Math.trunc(w / 2);
    const color = this.look.separatorInBorderColor ? this.borderColor : theme.separator;
    c.drawPolyline([this.x, this.x + this.width], [yy + half, yy + half], w, color);
  }

  private paintSection(c: SvgCanvas, s: Section, cx: number, top: number, cw: number, theme: Theme): number {
    if (s.rows.length === 0) return top;
    const look = this.look;
    const left = cx + look.padX;
    const rightEdge = cx + cw - look.padX;
    let ry = top + look.padY;
    for (const r of s.rows) {
      const inner = r.height - 2 * look.rowPadY;
      const ly = ry + look.rowPadY;
      let tx = left;
      if (r.icon) {
        c.drawIcon(r.attribute.iconId, r.attribute.inPrimaryKey, tx, ly + Math.trunc((inner - ICON_SIZE) / 2));
        tx += ICON_SIZE + ICON_GAP;
      }
      const ty = ly + Math.trunc((inner - r.textH) / 2);
      c.drawText(r.label, tx, ty, r.font, theme.text);
      if (r.right) c.drawText(r.right, rightEdge - r.rightW, ty, r.font, theme.typeText);
      ry += r.height;
    }
    return top + s.height;
  }
}

/** Visibility filter and optional alphabetical sort. */
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
  if (options.has('alpha')) list.sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  return list;
}

export function attributeLabel(a: Attribute, options: RenderOptions): string {
  return options.has('comments') && a.comment ? `${a.name} - ${a.comment}` : a.name;
}

export function rightText(a: Attribute, options: RenderOptions): string {
  let s = options.has('types') ? a.fullTypeName : '';
  if (options.has('nullability') && a.required) s += ' NOT NULL';
  return s.trim();
}
