import { Rect } from '../geom.js';
import type { RoutedConnection } from '../layout/elk-layout.js';
import type { SvgCanvas } from '../render/canvas.js';
import type { RenderOptions } from '../style/options.js';
import type { Color } from '../style/theme.js';
import type { FontStyle } from '../text/fonts.js';

/** Draws one routed connection (line + end symbols + labels) in a given notation. */
export interface NotationPainter {
  paint(canvas: SvgCanvas, conn: RoutedConnection, options: RenderOptions): void;
  /** Bounding box of everything `paint` draws, for the export extent. */
  bounds(conn: RoutedConnection, options: RenderOptions): Rect;
}

/**
 * One end of a connection. Lines always meet a table horizontally, so an end is described by the
 * point on the table's edge and `dir`: +1 when the table lies to the right of the line, -1 when it
 * lies to the left. Symbols are drawn along the line, i.e. at `x - k * dir`.
 */
export interface LineEnd {
  x: number;
  y: number;
  dir: 1 | -1;
}

export function lineEnd(conn: RoutedConnection, end: 'source' | 'target'): LineEnd {
  const i = end === 'source' ? 0 : conn.size - 1;
  const box = end === 'source' ? conn.source : conn.target;
  const x = conn.xs[i];
  return { x, y: conn.ys[i], dir: box.centerX() >= x ? 1 : -1 };
}

/** Bounds of the 1 px polyline itself: every point grown by one pixel on each side. */
export function polylineBounds(conn: RoutedConnection): Rect {
  let r: Rect | null = null;
  for (let i = 0; i < conn.size; i++) {
    const p = new Rect(conn.xs[i] - 1, conn.ys[i] - 1, 2, 2);
    r = r ? r.union(p) : p;
  }
  return r ?? new Rect();
}

/** The area a symbol of the given length and half-height occupies at an end. */
export function symbolBounds(e: LineEnd, length: number, halfHeight: number): Rect {
  return new Rect(Math.min(e.x, e.x - length * e.dir), e.y - halfHeight, length, 2 * halfHeight + 1).expand(1, 1);
}

const LABEL_GAP_X = 3;
const LABEL_GAP_Y = 5;

/** A cardinality label sits above the line, just outside the table, reading away from it. */
export function labelBounds(e: LineEnd, text: string, font: FontStyle, options: RenderOptions): Rect {
  const w = options.fonts.widthInt(text, font);
  const h = options.fonts.height(font);
  const x = e.dir > 0 ? e.x - LABEL_GAP_X - w : e.x + LABEL_GAP_X;
  return new Rect(x, e.y - LABEL_GAP_Y - h, w, h);
}

export function paintLabel(c: SvgCanvas, e: LineEnd, text: string, font: FontStyle, color: Color, options: RenderOptions): void {
  const r = labelBounds(e, text, font, options);
  c.drawText(text, r.x, r.y, font, color);
}

/** The FK comment, centred on the middle segment of the line. */
export function paintMidpointLabel(c: SvgCanvas, conn: RoutedConnection, text: string, font: FontStyle, color: Color, options: RenderOptions): void {
  const n = conn.size;
  if (n < 2) return;
  let mx: number;
  let my: number;
  if (n % 2 === 0) {
    const i = n / 2 - 1;
    mx = Math.trunc((conn.xs[i] + conn.xs[i + 1]) / 2);
    my = Math.trunc((conn.ys[i] + conn.ys[i + 1]) / 2);
  } else {
    mx = conn.xs[Math.trunc(n / 2)];
    my = conn.ys[Math.trunc(n / 2)];
  }
  const w = options.fonts.widthInt(text, font);
  const h = options.fonts.height(font);
  c.drawText(text, mx - Math.trunc(w / 2), my - Math.trunc(h / 2), font, color);
}

export function commentOf(conn: RoutedConnection, options: RenderOptions): string | null {
  return options.has('comments') && conn.association.comment ? conn.association.comment : null;
}
