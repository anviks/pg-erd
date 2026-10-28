import { Points, Rect } from '../geom.js';
import type { RoutedConnection } from '../layout/elk-layout.js';
import type { SvgCanvas } from '../render/canvas.js';
import type { RenderOptions } from '../style/options.js';
import type { Color } from '../style/theme.js';
import type { FontSet, FontStyle } from '../text/fonts.js';
import { bounds, transform } from './decorations.js';
import { commentOf, paintMidpointLabel, polylineBounds, type NotationPainter } from './painter.js';

type EndType = 'ZERO_OR_ONE' | 'ONE_ONLY' | 'ONE_OR_MANY' | 'ZERO_OR_MANY';

const LABEL_0_TO_1 = '0..1';
const LABEL_1 = '1';
const LABEL_1_TO_N = '1..n';
const LBL_V_DISTANCE: number = -4;
const LBL_U_DISTANCE = 3;
const DIAMETER = 8;
const SCALE = 5;
/** Template points: 0-2 crow's foot, 3-4 first dash, 5-6 second dash, 7 circle centre, 8 top. */
const TEMPLATE = new Points([0, -2, 0, -2, -2, -3, -3, -3, -6], [-1, 0, 1, -1, 1, -1, 1, 0, 0]);

interface End {
  type: EndType;
  label: string;
}

const sourceEnd = (c: RoutedConnection): End =>
  c.association.oneToOne ? { type: 'ONE_ONLY', label: LABEL_1 } : { type: 'ONE_OR_MANY', label: LABEL_1_TO_N };
const targetEnd = (c: RoutedConnection): End =>
  c.association.optional ? { type: 'ZERO_OR_ONE', label: LABEL_0_TO_1 } : { type: 'ONE_ONLY', label: LABEL_1 };

function decoration(conn: RoutedConnection, end: boolean): Points {
  const n = conn.size;
  return end
    ? transform(TEMPLATE, SCALE, conn.xs[n - 1], conn.ys[n - 1], conn.xs[n - 2], conn.ys[n - 2])
    : transform(TEMPLATE, SCALE, conn.xs[0], conn.ys[0], conn.xs[1], conn.ys[1]);
}

/**
 * Crow's foot as drawn by DBeaver's CrowsFootDiagramNotation / CrowsFootPolylineDecoration, with the
 * cardinality labels positioned by Draw2D's ConnectionEndpointLocator(uDistance 3, vDistance -4).
 */
export class CrowsFootPainter implements NotationPainter {
  paint(c: SvgCanvas, conn: RoutedConnection, options: RenderOptions): void {
    if (conn.size < 2) return;
    const theme = options.theme;
    const line = options.look.lineColor(conn.target.background, theme);
    c.drawPolyline(conn.xs, conn.ys, 1, line);

    const src = sourceEnd(conn);
    const trg = targetEnd(conn);
    paintDecoration(c, decoration(conn, false), src.type, line, theme.diagramBackground);
    paintDecoration(c, decoration(conn, true), trg.type, line, theme.diagramBackground);

    const font = options.fonts.notation;
    const labelColor = options.look.decorationColor(line, theme);
    let lb = labelBounds(conn, false, src.label, font, options.fonts);
    c.drawText(src.label, lb.x, lb.y, font, labelColor);
    lb = labelBounds(conn, true, trg.label, font, options.fonts);
    c.drawText(trg.label, lb.x, lb.y, font, labelColor);

    const comment = commentOf(conn, options);
    if (comment) paintMidpointLabel(c, conn, comment, options.fonts.regular, theme.attrForeground, options);
  }

  bounds(conn: RoutedConnection, options: RenderOptions): Rect {
    const r = polylineBounds(conn);
    if (conn.size >= 2) {
      r.union(bounds(decoration(conn, false)).expand(DIAMETER, DIAMETER));
      r.union(bounds(decoration(conn, true)).expand(DIAMETER, DIAMETER));
      const font = options.fonts.notation;
      r.union(labelBounds(conn, false, sourceEnd(conn).label, font, options.fonts));
      r.union(labelBounds(conn, true, targetEnd(conn).label, font, options.fonts));
    }
    return r;
  }
}

function paintDecoration(c: SvgCanvas, pts: Points, type: EndType, fg: Color, bg: Color): void {
  const line = (a: number, b: number) => c.drawPolyline([pts.xs[a], pts.xs[b]], [pts.ys[a], pts.ys[b]], 1, fg);
  const circle = () => {
    const r = DIAMETER / 2;
    c.fillOval(pts.xs[7] - r, pts.ys[7] - r, DIAMETER, DIAMETER, bg);
    c.drawOval(pts.xs[7] - r, pts.ys[7] - r, DIAMETER, DIAMETER, 1, fg);
  };
  switch (type) {
    case 'ZERO_OR_ONE': line(3, 4); circle(); break;
    case 'ONE_ONLY': line(3, 4); line(5, 6); break;
    case 'ONE_OR_MANY': line(0, 1); line(1, 2); line(3, 4); break;
    case 'ZERO_OR_MANY': line(0, 1); line(1, 2); circle(); break;
  }
}

/** Draw2D ConnectionEndpointLocator.relocate for a label of the given text. */
export function labelBounds(conn: RoutedConnection, end: boolean, text: string, font: FontStyle, fonts: FontSet): Rect {
  const n = conn.size;
  const sp = end ? n - 1 : 0;
  const ep = end ? n - 2 : 1;
  const startX = conn.xs[sp];
  const startY = conn.ys[sp];
  const endX = conn.xs[ep];
  const endY = conn.ys[ep];
  const owner = end ? conn.target : conn.source;
  const quadrant = connectionLocation(startX, startY, owner.x, owner.y, owner.centerX(), owner.centerY());

  let cos = 1;
  const transpose = quadrant === 1 || quadrant === 3;
  if (quadrant === 3 || quadrant === 4) cos = -1;

  const textW = fonts.widthInt(text, font);
  const textH = fonts.height(font);
  // in the transposed frame x and y swap roles
  const figureWidth = transpose ? textH : textW;
  const figureHeight = transpose ? textW : textH;
  const sx = transpose ? startY : startX;
  const sy = transpose ? startX : startY;
  const ex = transpose ? endY : endX;
  const ey = transpose ? endX : endY;

  let tan = ex === sx ? 1 : (ey - sy) / (ex - sx);
  if (tan > 1) tan = 1;
  else if (tan < -1) tan = -1;

  let yShift = 0;
  if (LBL_V_DISTANCE < 0) yShift = -figureHeight;
  else if (LBL_V_DISTANCE === 0) yShift = -Math.trunc(figureHeight / 2);

  const fx = sx + LBL_U_DISTANCE * cos + figureWidth * Math.trunc((cos - 1) / 2);
  const fy = Math.trunc(sy + cos * LBL_U_DISTANCE * tan + LBL_V_DISTANCE + yShift);
  return transpose ? new Rect(fy, fx, textW, textH) : new Rect(fx, fy, textW, textH);
}

function connectionLocation(locX: number, locY: number, topLeftX: number, topLeftY: number, centerX: number, centerY: number): number {
  if (locX === centerX) return locY < centerY ? 3 : 1;
  const m1 = (topLeftY - centerY) / (topLeftX - centerX);
  const m2 = (locY - centerY) / (locX - centerX);
  if (Math.abs(m2) <= Math.abs(m1)) return locX < centerX ? 4 : 2;
  return locY < centerY ? 3 : 1;
}
