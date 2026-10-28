import { ICON_SIZE, Icons, KEY_OVERLAY } from '../figure/icons.js';
import type { Color } from '../style/theme.js';
import type { FontSet, FontStyle } from '../text/fonts.js';
import { readFileSync } from 'node:fs';

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const num = (v: number) => (Number.isInteger(v) ? String(v) : v.toFixed(2).replace(/\.?0+$/, ''));
/** Odd stroke widths are shifted by half a pixel so a 1px line lands on exactly one pixel row. */
const off = (lineWidth: number) => (lineWidth % 2 === 1 ? 0.5 : 0);

/**
 * The drawing surface: integer pixel coordinates in Draw2D/SWT conventions (strokes centred on the
 * geometric edge, text positioned by the top-left of its line box), written out as SVG. PNG output
 * is this SVG rasterised, so there is a single back end.
 */
export class SvgCanvas {
  private readonly body: string[] = [];
  private readonly symbols = new Map<string, string>();

  constructor(
    private readonly icons: Icons,
    private readonly fonts: FontSet,
    private readonly offsetX: number,
    private readonly offsetY: number,
  ) {}

  fillRect(x: number, y: number, w: number, h: number, c: Color): void {
    this.body.push(`<rect x="${x + this.offsetX}" y="${y + this.offsetY}" width="${w}" height="${h}" fill="${c}"/>`);
  }

  drawRect(x: number, y: number, w: number, h: number, lineWidth: number, c: Color): void {
    const o = off(lineWidth);
    this.body.push(
      `<rect x="${num(x + this.offsetX + o)}" y="${num(y + this.offsetY + o)}" width="${w}" height="${h}" fill="none" stroke="${c}" stroke-width="${lineWidth}"/>`,
    );
  }

  fillRoundRect(x: number, y: number, w: number, h: number, radius: number, c: Color): void {
    this.body.push(`<rect x="${x + this.offsetX}" y="${y + this.offsetY}" width="${w}" height="${h}" rx="${radius}" fill="${c}"/>`);
  }

  drawRoundRect(x: number, y: number, w: number, h: number, radius: number, lineWidth: number, c: Color): void {
    const o = off(lineWidth);
    this.body.push(
      `<rect x="${num(x + this.offsetX + o)}" y="${num(y + this.offsetY + o)}" width="${w}" height="${h}" rx="${radius}" fill="none" stroke="${c}" stroke-width="${lineWidth}"/>`,
    );
  }

  private points(xs: readonly number[], ys: readonly number[], o: number): string {
    const out: string[] = [];
    for (let i = 0; i < xs.length; i++) out.push(`${num(xs[i] + this.offsetX + o)},${num(ys[i] + this.offsetY + o)}`);
    return out.join(' ');
  }

  /** @param dash dash pattern in pixels, or null for a solid line */
  drawPolyline(xs: readonly number[], ys: readonly number[], lineWidth: number, c: Color, dash: number[] | null = null): void {
    if (xs.length < 2) return;
    let el = `<polyline points="${this.points(xs, ys, off(lineWidth))}" fill="none" stroke="${c}" stroke-width="${lineWidth}"`;
    if (dash) el += ` stroke-dasharray="${dash.map(num).join(' ')}"`;
    this.body.push(el + '/>');
  }

  fillPolygon(xs: readonly number[], ys: readonly number[], c: Color): void {
    this.body.push(`<polygon points="${this.points(xs, ys, 0.5)}" fill="${c}"/>`);
  }

  drawPolygon(xs: readonly number[], ys: readonly number[], lineWidth: number, c: Color): void {
    this.body.push(`<polygon points="${this.points(xs, ys, off(lineWidth))}" fill="none" stroke="${c}" stroke-width="${lineWidth}"/>`);
  }

  fillOval(x: number, y: number, w: number, h: number, c: Color): void {
    this.body.push(
      `<ellipse cx="${num(x + this.offsetX + 0.5 + w / 2)}" cy="${num(y + this.offsetY + 0.5 + h / 2)}" rx="${num(w / 2)}" ry="${num(h / 2)}" fill="${c}"/>`,
    );
  }

  drawOval(x: number, y: number, w: number, h: number, lineWidth: number, c: Color): void {
    const o = off(lineWidth);
    this.body.push(
      `<ellipse cx="${num(x + this.offsetX + o + w / 2)}" cy="${num(y + this.offsetY + o + h / 2)}" rx="${num(w / 2)}" ry="${num(h / 2)}" fill="none" stroke="${c}" stroke-width="${lineWidth}"/>`,
    );
  }

  /** Draws text with its line box's top-left corner at (x, y). */
  drawText(s: string, x: number, y: number, f: FontStyle, c: Color): void {
    if (!s) return;
    const runs = this.fonts.runs(s, f);
    const width = this.fonts.width(s, f);
    let el = `<text x="${x + this.offsetX}" y="${y + this.offsetY + this.fonts.ascent(f)}" font-size="${f.sizePx}px"`;
    if (f.bold) el += ' font-weight="bold"';
    el += ` fill="${c}" textLength="${num(width)}" lengthAdjust="spacingAndGlyphs" xml:space="preserve">`;
    if (runs.length === 1) {
      el += esc(s);
    } else {
      for (const r of runs) {
        const fam = r.face.family === this.fonts.family ? '' : ` font-family="${esc(r.face.family)}"`;
        el += `<tspan${fam}>${esc(r.text)}</tspan>`;
      }
    }
    this.body.push(el + '</text>');
  }

  /** Draws a 16x16 icon with its top-left corner at (x, y). */
  drawIcon(iconId: string, keyOverlay: boolean, x: number, y: number): void {
    const id = this.symbol(iconId, keyOverlay);
    this.body.push(`<use xlink:href="#${id}" href="#${id}" x="${x + this.offsetX}" y="${y + this.offsetY}"/>`);
  }

  private symbol(iconId: string, keyOverlay: boolean): string {
    const id = `icon-${iconId.replace(/\//g, '-')}${keyOverlay ? '-key' : ''}`;
    if (!this.symbols.has(id)) {
      const icon = this.icons.shape(iconId);
      const lines = [`<g id="${id}">`];
      if (icon.width !== ICON_SIZE || icon.height !== ICON_SIZE) {
        lines[0] = `<g id="${id}" transform="scale(${num(ICON_SIZE / icon.width)} ${num(ICON_SIZE / icon.height)})">`;
      }
      lines.push(...icon.paths.map((p) => '  ' + p));
      if (keyOverlay) {
        const data = `data:image/png;base64,${this.icons.keyOverlayBase64()}`;
        lines.push(`  <image x="8" y="8" width="8" height="8" xlink:href="${data}" href="${data}"/>`);
      }
      lines.push('</g>');
      this.symbols.set(id, lines.join('\n'));
    }
    return id;
  }

  /** Assembles the document. */
  toSvg(width: number, height: number, background: Color, embedFont: boolean): string {
    const out: string[] = [];
    out.push('<?xml version="1.0" encoding="UTF-8"?>');
    out.push(
      `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" font-family="${esc(this.fonts.cssFamily())}" shape-rendering="geometricPrecision">`,
    );
    if (embedFont) {
      const files = this.fonts.primaryFiles();
      const b64 = (p: string) => readFileSync(p).toString('base64');
      out.push('<style>');
      out.push(`@font-face{font-family:'${this.fonts.family}';font-weight:normal;src:url(data:font/ttf;base64,${b64(files.regular)}) format('truetype');}`);
      out.push(`@font-face{font-family:'${this.fonts.family}';font-weight:bold;src:url(data:font/ttf;base64,${b64(files.bold)}) format('truetype');}`);
      out.push('</style>');
    }
    out.push('<defs>');
    out.push(...this.symbols.values());
    out.push('</defs>');
    out.push(`<rect width="100%" height="100%" fill="${background}"/>`);
    out.push(...this.body);
    out.push('</svg>');
    return out.join('\n') + '\n';
  }
}

export { KEY_OVERLAY };
