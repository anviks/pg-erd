import type { Rect } from '../geom.js';
import type { RoutedConnection } from '../layout/elk-layout.js';
import type { SvgCanvas } from '../render/canvas.js';
import type { RenderOptions } from '../style/options.js';
import type { Color } from '../style/theme.js';
import { commentOf, labelBounds, lineEnd, paintLabel, paintMidpointLabel, polylineBounds, symbolBounds, type LineEnd, type NotationPainter } from './painter.js';

type Cardinality = 'ZERO_OR_ONE' | 'ONE' | 'ZERO_OR_MANY';

const HALF = 5; // half-height of the foot and bars
const FOOT = 10; // length of the foot / position of the first bar
const SECOND = 15; // position of the second bar or the circle centre
const CIRCLE_R = 4;
const SYMBOL_LENGTH = SECOND + CIRCLE_R;

const LABELS: Record<Cardinality, string> = { ZERO_OR_ONE: '0..1', ONE: '1', ZERO_OR_MANY: '0..n' };

/** Child end: at most one child when a unique index covers the FK, any number otherwise. */
const childEnd = (c: RoutedConnection): Cardinality => (c.association.oneToOne ? 'ZERO_OR_ONE' : 'ZERO_OR_MANY');
/** Parent end: exactly one parent, or none when the FK is nullable. */
const parentEnd = (c: RoutedConnection): Cardinality => (c.association.optional ? 'ZERO_OR_ONE' : 'ONE');

/** Crow's foot with `1`, `0..1` and `0..n` labels. Symbols are drawn along the line's horizontal end. */
export class CrowsFootPainter implements NotationPainter {
  paint(c: SvgCanvas, conn: RoutedConnection, options: RenderOptions): void {
    if (conn.size < 2) return;
    const theme = options.theme;
    const line = options.look.lineColor(conn.target.background, theme);
    c.drawPolyline(conn.xs, conn.ys, 1, line);

    const s = lineEnd(conn, 'source');
    const t = lineEnd(conn, 'target');
    symbol(c, s, childEnd(conn), line, theme.background);
    symbol(c, t, parentEnd(conn), line, theme.background);

    const font = options.fonts.notation;
    paintLabel(c, s, LABELS[childEnd(conn)], font, line, options);
    paintLabel(c, t, LABELS[parentEnd(conn)], font, line, options);

    const comment = commentOf(conn, options);
    if (comment) paintMidpointLabel(c, conn, comment, options.fonts.regular, theme.text, options);
  }

  bounds(conn: RoutedConnection, options: RenderOptions): Rect {
    const r = polylineBounds(conn);
    if (conn.size >= 2) {
      const font = options.fonts.notation;
      for (const [e, card] of [[lineEnd(conn, 'source'), childEnd(conn)], [lineEnd(conn, 'target'), parentEnd(conn)]] as const) {
        r.union(symbolBounds(e, SYMBOL_LENGTH, HALF));
        r.union(labelBounds(e, LABELS[card], font, options));
      }
    }
    return r;
  }
}

function symbol(c: SvgCanvas, e: LineEnd, card: Cardinality, fg: Color, bg: Color): void {
  const { x, y, dir } = e;
  const bar = (k: number) => c.drawPolyline([x - k * dir, x - k * dir], [y - HALF, y + HALF], 1, fg);
  const foot = () => {
    c.drawPolyline([x, x - FOOT * dir], [y - HALF, y], 1, fg);
    c.drawPolyline([x, x - FOOT * dir], [y + HALF, y], 1, fg);
  };
  const circle = () => {
    const cx = x - SECOND * dir;
    c.fillOval(cx - CIRCLE_R, y - CIRCLE_R, 2 * CIRCLE_R, 2 * CIRCLE_R, bg);
    c.drawOval(cx - CIRCLE_R, y - CIRCLE_R, 2 * CIRCLE_R, 2 * CIRCLE_R, 1, fg);
  };
  switch (card) {
    case 'ONE': bar(FOOT); bar(SECOND); break;
    case 'ZERO_OR_ONE': bar(FOOT); circle(); break;
    case 'ZERO_OR_MANY': foot(); circle(); break;
  }
}
