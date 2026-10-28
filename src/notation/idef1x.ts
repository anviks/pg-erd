import { Points, Rect } from '../geom.js';
import type { RoutedConnection } from '../layout/elk-layout.js';
import type { SvgCanvas } from '../render/canvas.js';
import type { RenderOptions } from '../style/options.js';
import { bounds, circleBounds, ellipse, fillAndOutline, transform } from './decorations.js';
import { commentOf, paintMidpointLabel, polylineBounds, type NotationPainter } from './painter.js';

const CIRCLE_RADIUS = 4; // ERDNotationBase.CIRCLE_RADIUS
const DOTTED = [1, 1]; // SWT.LINE_DOT at width 1
const RHOMBUS_SCALE = 5;
const RHOMBUS = new Points([0, -1, -2, -1], [0, 1, 0, -1]);

/**
 * IDEF1X as drawn by DBeaver's IDEF1XDiagramNotation + AssociationPart: a filled circle at the FK (child)
 * end, a hollow rhombus at the parent end for optional (nullable) FKs, and a dotted line when the
 * association is non-identifying.
 */
export class Idef1xPainter implements NotationPainter {
  paint(c: SvgCanvas, conn: RoutedConnection, options: RenderOptions): void {
    if (conn.size < 2) return;
    const theme = options.theme;
    const line = options.look.lineColor(conn.target.background, theme);
    const dotted = !conn.association.identifying;
    c.drawPolyline(conn.xs, conn.ys, 1, line, dotted ? DOTTED : null);

    // source decoration: CircleDecoration located at point 0, reference point 1
    ellipse(c, sourceCircle(conn), options.look.decorationColor(line, theme), line);

    // target decoration: rhombus for optional associations, filled with the diagram background
    if (conn.association.optional) fillAndOutline(c, targetRhombus(conn), theme.diagramBackground, line);

    const comment = commentOf(conn, options);
    if (comment) paintMidpointLabel(c, conn, comment, options.fonts.regular, theme.attrForeground, options);
  }

  bounds(conn: RoutedConnection, options: RenderOptions): Rect {
    void options;
    const r = polylineBounds(conn);
    if (conn.size >= 2) {
      r.union(sourceCircle(conn).expand(1, 1));
      if (conn.association.optional) r.union(bounds(targetRhombus(conn)));
    }
    return r;
  }
}

function sourceCircle(conn: RoutedConnection): Rect {
  return circleBounds(CIRCLE_RADIUS, conn.xs[0], conn.ys[0], conn.xs[1], conn.ys[1]);
}

function targetRhombus(conn: RoutedConnection): Points {
  const n = conn.size;
  return transform(RHOMBUS, RHOMBUS_SCALE, conn.xs[n - 1], conn.ys[n - 1], conn.xs[n - 2], conn.ys[n - 2]);
}
