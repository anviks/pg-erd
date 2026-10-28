import type { Rect } from '../geom.js';
import type { RoutedConnection } from '../layout/elk-layout.js';
import type { SvgCanvas } from '../render/canvas.js';
import type { RenderOptions } from '../style/options.js';
import { commentOf, lineEnd, paintMidpointLabel, polylineBounds, symbolBounds, type LineEnd, type NotationPainter } from './painter.js';

const DOT_RADIUS = 4;
const DIAMOND = 5;
const DOTTED = [1, 1];

/**
 * IDEF1X: a filled dot at the child (FK) end, a hollow diamond at the parent end when the FK is
 * nullable, and a dotted line for non-identifying relations.
 */
export class Idef1xPainter implements NotationPainter {
  paint(c: SvgCanvas, conn: RoutedConnection, options: RenderOptions): void {
    if (conn.size < 2) return;
    const theme = options.theme;
    const line = options.look.lineColor(conn.target.background, theme);
    c.drawPolyline(conn.xs, conn.ys, 1, line, conn.association.identifying ? null : DOTTED);

    const s = lineEnd(conn, 'source');
    const cx = s.x - (DOT_RADIUS + 1) * s.dir;
    c.fillOval(cx - DOT_RADIUS, s.y - DOT_RADIUS, 2 * DOT_RADIUS, 2 * DOT_RADIUS, line);
    c.drawOval(cx - DOT_RADIUS, s.y - DOT_RADIUS, 2 * DOT_RADIUS, 2 * DOT_RADIUS, 1, line);

    if (conn.association.optional) {
      const t = lineEnd(conn, 'target');
      const xs = [t.x, t.x - DIAMOND * t.dir, t.x - 2 * DIAMOND * t.dir, t.x - DIAMOND * t.dir];
      const ys = [t.y, t.y - DIAMOND, t.y, t.y + DIAMOND];
      c.fillPolygon(xs, ys, theme.background);
      c.drawPolygon(xs, ys, 1, line);
    }

    const comment = commentOf(conn, options);
    if (comment) paintMidpointLabel(c, conn, comment, options.fonts.regular, theme.text, options);
  }

  bounds(conn: RoutedConnection, options: RenderOptions): Rect {
    void options;
    const r = polylineBounds(conn);
    if (conn.size >= 2) {
      const s: LineEnd = lineEnd(conn, 'source');
      r.union(symbolBounds(s, 2 * DOT_RADIUS + 1, DOT_RADIUS));
      if (conn.association.optional) r.union(symbolBounds(lineEnd(conn, 'target'), 2 * DIAMOND, DIAMOND));
    }
    return r;
  }
}
