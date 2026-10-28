import { Points, Rect } from '../geom.js';
import type { SvgCanvas } from '../render/canvas.js';
import type { Color } from '../style/theme.js';

/**
 * Draw2D decoration transform: template scaled, rotated towards (lx, ly) as seen from (rx, ry),
 * translated to (lx, ly); every point rounded (Transform.getTransformed).
 */
export function transform(template: Points, scale: number, lx: number, ly: number, rx: number, ry: number): Points {
  const angle = Math.atan2(ly - ry, lx - rx);
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);
  const xs: number[] = [];
  const ys: number[] = [];
  for (let i = 0; i < template.size; i++) {
    const x = template.xs[i] * scale;
    const y = template.ys[i] * scale;
    xs.push(Math.round(x * cos - y * sin + lx));
    ys.push(Math.round(x * sin + y * cos + ly));
  }
  return new Points(xs, ys);
}

export function fillAndOutline(c: SvgCanvas, pts: Points, fill: Color, outline: Color): void {
  c.fillPolygon(pts.xs, pts.ys, fill);
  c.drawPolygon(pts.xs, pts.ys, 1, outline);
}

export function bounds(pts: Points): Rect {
  return pts.bounds().expand(1, 1);
}

/**
 * DBeaver's AssociationPart.CircleDecoration: an ellipse of the given radius centred `radius` px back
 * from the location towards the reference, with its bounds enlarged to 2.5r (a quirk kept for fidelity).
 */
export function circleBounds(radius: number, lx: number, ly: number, rx: number, ry: number): Rect {
  const d = Math.hypot(lx - rx, ly - ry);
  if (d < radius) return new Rect(lx - radius, ly - radius, radius * 2, radius * 2);
  const k = (d - radius) / d;
  const shortx = k * Math.abs(rx - lx);
  const shorty = k * Math.abs(ry - ly);
  const cx = lx < rx ? rx - Math.trunc(shortx) : rx + Math.trunc(shortx);
  const cy = ly > ry ? ry + Math.trunc(shorty) : ry - Math.trunc(shorty);
  return new Rect(cx - radius, cy - radius, Math.trunc(radius * 2.5), Math.trunc(radius * 2.5));
}

/** Draw2D Ellipse: fill the bounds, then outline the bounds shrunk by one pixel (line width 1). */
export function ellipse(c: SvgCanvas, r: Rect, fill: Color, outline: Color): void {
  c.fillOval(r.x, r.y, r.width, r.height, fill);
  c.drawOval(r.x, r.y, r.width - 1, r.height - 1, 1, outline);
}
