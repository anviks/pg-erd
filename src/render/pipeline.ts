import { Rect } from '../geom.js';
import { EntityBox } from '../figure/entity-box.js';
import { Icons } from '../figure/icons.js';
import { layoutWithElk, type RoutedConnection } from '../layout/elk-layout.js';
import type { Diagram } from '../model/index.js';
import { CrowsFootPainter } from '../notation/crowsfoot.js';
import { Idef1xPainter } from '../notation/idef1x.js';
import type { NotationPainter } from '../notation/painter.js';
import type { RenderOptions } from '../style/options.js';
import { SvgCanvas } from './canvas.js';

/** A laid-out, routed diagram ready to be painted. */
export class RenderedDiagram {
  constructor(
    readonly boxes: EntityBox[],
    readonly connections: RoutedConnection[],
    readonly notation: NotationPainter,
    readonly options: RenderOptions,
  ) {}

  /** Union of all tables and everything drawn for the connections. */
  extent(): Rect {
    let r: Rect | null = null;
    for (const b of this.boxes) {
      const e = b.bounds();
      r = r ? r.union(e) : e;
    }
    for (const c of this.connections) {
      const e = this.notation.bounds(c, this.options);
      if (e.isEmpty()) continue;
      r = r ? r.union(e) : e;
    }
    return r ?? new Rect();
  }

  /** Tables first, connections on top. */
  paint(canvas: SvgCanvas): void {
    for (const b of this.boxes) b.paint(canvas);
    for (const c of this.connections) this.notation.paint(canvas, c, this.options);
  }

  /** The diagram as an SVG document with the export margin around the content. */
  toSvg(): string {
    const extent = this.extent();
    if (extent.isEmpty()) throw new Error("Can't export empty diagram");
    const margin = this.options.margin;
    const w = extent.width + margin * 2;
    const h = extent.height + margin * 2;
    const canvas = new SvgCanvas(new Icons(), this.options.fonts, -extent.x + margin, -extent.y + margin);
    this.paint(canvas);
    return canvas.toSvg(w, h, this.options.theme.background, this.options.embedSvgFont);
  }
}

/** Model -> measured boxes -> layout and routing (ELK) -> paintable diagram. */
export async function render(diagram: Diagram, options: RenderOptions): Promise<RenderedDiagram> {
  const boxes = diagram.entities.map((e) => new EntityBox(e, options, options.fonts));
  const conns = await layoutWithElk(diagram, boxes, options);
  const painter: NotationPainter = options.notation === 'idef1x' ? new Idef1xPainter() : new CrowsFootPainter();
  return new RenderedDiagram(boxes, conns, painter, options);
}
