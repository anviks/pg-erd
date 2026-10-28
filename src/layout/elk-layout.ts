import { createRequire } from 'node:module';
import type { ELK as ElkApi, ElkExtendedEdge, ElkNode, ElkPort } from 'elkjs/lib/elk-api.js';
import type { EntityBox } from '../figure/entity-box.js';
import type { Association, Diagram } from '../model/index.js';
import { fanStep, straightRun, type RenderOptions } from '../style/options.js';

/** Final polyline of one association, from the source (FK child) box to the target (parent) box. */
export class RoutedConnection {
  xs: number[] = [];
  ys: number[] = [];
  constructor(
    readonly association: Association,
    readonly source: EntityBox,
    readonly target: EntityBox,
  ) {}
  get size(): number {
    return this.xs.length;
  }
}

const NODE_SPACING = 40;
const LAYER_SPACING = 70;
const EDGE_SPACING = 10;
/** Extra straight run beyond the notation's end symbol before a line may bend. */
const STRAIGHT_RUN_PADDING = 10;
const COMPONENT_SPACING = 40;

interface PortSpec {
  conn: RoutedConnection;
  rowCenterY: number;
}

// elk.bundled.js is a CommonJS module whose `module.exports` is the constructor itself
const require = createRequire(import.meta.url);
const ElkCtor = require('elkjs/lib/elk.bundled.js') as new () => ElkApi;
const elk: ElkApi = new ElkCtor();

/**
 * Layout and routing with the Eclipse Layout Kernel's layered algorithm (elkjs):
 * edges run from the FK child to the parent, laid out right-to-left so parents sit left of children;
 * each FK leaves through a port on the child's west edge at its FK column's row and arrives at a port
 * on the parent's east edge spread around the referenced row; orthogonal routing keeps lines apart
 * from each other and from tables. ELK runs twice: once with free port order to learn how it wants to
 * order the parent-side ports, then with all ports at fixed positions.
 */
export async function layoutWithElk(diagram: Diagram, boxes: EntityBox[], options: RenderOptions): Promise<RoutedConnection[]> {
  const boxOf = new Map(boxes.map((b) => [b.entity, b]));
  const conns: RoutedConnection[] = [];
  for (const e of diagram.entities) {
    for (const a of diagram.sourceAssociations(e)) {
      conns.push(new RoutedConnection(a, boxOf.get(a.source)!, boxOf.get(a.target)!));
    }
  }
  const order = await run(boxes, conns, options, null);
  await run(boxes, conns, options, order);
  return conns;
}

async function run(
  boxes: EntityBox[], conns: RoutedConnection[], options: RenderOptions, targetOrder: Map<RoutedConnection, number> | null,
): Promise<Map<RoutedConnection, number>> {
  const edgeNode = straightRun(options.notation) + STRAIGHT_RUN_PADDING;
  const step = fanStep(options.notation);
  const root: ElkNode = {
    id: 'root',
    layoutOptions: {
      'elk.algorithm': 'layered',
      'elk.direction': 'LEFT',
      'elk.edgeRouting': 'ORTHOGONAL',
      'elk.spacing.nodeNode': String(NODE_SPACING),
      'elk.layered.spacing.nodeNodeBetweenLayers': String(LAYER_SPACING),
      'elk.spacing.edgeEdge': String(EDGE_SPACING),
      'elk.layered.spacing.edgeEdgeBetweenLayers': String(EDGE_SPACING),
      // keep the first/last segment straight for at least the length of the end symbol (+ its label)
      'elk.spacing.edgeNode': String(edgeNode),
      'elk.layered.spacing.edgeNodeBetweenLayers': String(edgeNode),
      'elk.spacing.nodeSelfLoop': String(edgeNode),
      'elk.spacing.portPort': String(step),
      'elk.spacing.componentComponent': String(COMPONENT_SPACING),
      'elk.separateConnectedComponents': 'true',
      'elk.layered.compaction.connectedComponents': 'true',
      'elk.layered.thoroughness': '20',
    },
    children: [],
    edges: [],
  };

  const nodeOf = new Map<EntityBox, ElkNode>();
  boxes.forEach((b, i) => {
    const n: ElkNode = {
      id: `n${i}`,
      width: b.width,
      height: b.height,
      ports: [],
      layoutOptions: { 'elk.portConstraints': targetOrder ? 'FIXED_POS' : 'FIXED_SIDE' },
    };
    nodeOf.set(b, n);
    root.children!.push(n);
  });

  let portSeq = 0;
  const addPort = (box: EntityBox, side: 'WEST' | 'EAST', y: number): ElkPort => {
    const p: ElkPort = {
      id: `p${portSeq++}`,
      x: side === 'WEST' ? 0 : box.width,
      y,
      width: 0,
      height: 0,
      layoutOptions: { 'elk.port.side': side },
    };
    nodeOf.get(box)!.ports!.push(p);
    return p;
  };

  // parent-side ports per box, positioned around the referenced row
  const targetPorts = new Map<EntityBox, PortSpec[]>();
  const targetPortOf = new Map<RoutedConnection, ElkPort>();
  for (const c of conns) {
    const rowY = rowCenter(c.target, c.association.targetColumns);
    if (c.source === c.target) {
      // self-reference: both ports on the west side -> a compact loop beside the FK and key rows
      targetPortOf.set(c, addPort(c.target, 'WEST', rowY));
      continue;
    }
    let specs = targetPorts.get(c.target);
    if (!specs) targetPorts.set(c.target, (specs = []));
    specs.push({ conn: c, rowCenterY: rowY });
  }
  for (const [box, specs] of targetPorts) {
    if (targetOrder) specs.sort((a, b) => (targetOrder.get(a.conn) ?? 0) - (targetOrder.get(b.conn) ?? 0));
    const ys = spread(specs, box, step);
    specs.forEach((s, i) => targetPortOf.set(s.conn, addPort(box, 'EAST', ys[i])));
  }

  const edgeOf = new Map<RoutedConnection, ElkExtendedEdge>();
  conns.forEach((c, i) => {
    const sp = addPort(c.source, 'WEST', rowCenter(c.source, c.association.sourceColumns));
    const e: ElkExtendedEdge = { id: `e${i}`, sources: [sp.id], targets: [targetPortOf.get(c)!.id] };
    edgeOf.set(c, e);
    root.edges!.push(e);
  });

  const laidOut = await elk.layout(root);

  const nodeById = new Map(laidOut.children!.map((n: ElkNode) => [n.id, n]));
  const portById = new Map<string, ElkPort>();
  for (const n of laidOut.children!) for (const p of n.ports ?? []) portById.set(p.id, p);
  for (const b of boxes) {
    const n = nodeById.get(nodeOf.get(b)!.id)!;
    b.x = Math.round(n.x ?? 0);
    b.y = Math.round(n.y ?? 0);
  }
  const observed = new Map<RoutedConnection, number>();
  for (const specs of targetPorts.values()) {
    const sorted = [...specs].sort((a, b) => (portById.get(targetPortOf.get(a.conn)!.id)!.y ?? 0) - (portById.get(targetPortOf.get(b.conn)!.id)!.y ?? 0));
    sorted.forEach((s, i) => observed.set(s.conn, i));
  }
  const edgeById = new Map(laidOut.edges!.map((e: ElkExtendedEdge) => [e.id, e]));
  for (const c of conns) {
    const e = edgeById.get(edgeOf.get(c)!.id)!;
    const pts: [number, number][] = [];
    for (const s of e.sections ?? []) {
      pts.push([Math.round(s.startPoint.x), Math.round(s.startPoint.y)]);
      for (const bp of s.bendPoints ?? []) pts.push([Math.round(bp.x), Math.round(bp.y)]);
      pts.push([Math.round(s.endPoint.x), Math.round(s.endPoint.y)]);
    }
    // drop consecutive duplicates
    const clean: [number, number][] = [];
    for (const p of pts) {
      const last = clean[clean.length - 1];
      if (!last || last[0] !== p[0] || last[1] !== p[1]) clean.push(p);
    }
    c.xs = clean.map((p) => p[0]);
    c.ys = clean.map((p) => p[1]);
  }
  return observed;
}

/** Row centre (relative to the box top) of the first named column, or the box centre if hidden. */
export function rowCenter(box: EntityBox, columns: string[]): number {
  if (columns.length > 0) {
    const sx = box.x;
    const sy = box.y;
    box.x = 0;
    box.y = 0;
    const r = box.rowBounds(columns[0]);
    box.x = sx;
    box.y = sy;
    if (r) return r.centerY();
  }
  return Math.trunc(box.height / 2);
}

/** Port y positions spread `step` apart around the mean of the referenced rows, kept inside the box. */
export function spread(specs: PortSpec[], box: EntityBox, step: number): number[] {
  const n = specs.length;
  if (n === 1) return [specs[0].rowCenterY];
  const center = Math.trunc(specs.reduce((s, p) => s + p.rowCenterY, 0) / n);
  const minY = 4;
  const maxY = box.height - 4;
  let s = step;
  if ((n - 1) * s > maxY - minY) s = Math.max(4, Math.trunc((maxY - minY) / (n - 1)));
  const span = (n - 1) * s;
  let top = center - Math.trunc(span / 2);
  if (top + span > maxY) top = maxY - span;
  if (top < minY) top = minY;
  return Array.from({ length: n }, (_, i) => top + i * s);
}
