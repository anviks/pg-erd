package io.dbeavererd.layout;

import io.dbeavererd.figure.EntityBox;
import io.dbeavererd.model.Association;
import io.dbeavererd.model.Diagram;
import io.dbeavererd.model.Entity;
import io.dbeavererd.route.RoutedConnection;
import io.dbeavererd.style.RenderOptions;
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.data.LayoutMetaDataService;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.options.PortConstraints;
import org.eclipse.elk.core.options.PortSide;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkBendPoint;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkEdgeSection;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.ElkPort;
import org.eclipse.elk.graph.util.ElkGraphUtil;
import org.eclipse.draw2d.geometry.Rectangle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Layout and routing with the Eclipse Layout Kernel's layered algorithm:
 * <ul>
 *   <li>edges run from the FK child to the parent, laid out right-to-left so parents sit left of children;</li>
 *   <li>each FK leaves through a port on the child's west edge at its FK column's row;</li>
 *   <li>each FK arrives at a port on the parent's east edge; ports of one parent are spread around the
 *       referenced row ({@code fanStep} apart) in the order ELK chooses to avoid crossings;</li>
 *   <li>orthogonal edge routing with edge spacing, so lines never cross through tables.</li>
 * </ul>
 * Runs ELK twice: once with free port order to learn how it wants to order the parent-side ports, then
 * with all ports at fixed positions.
 */
public final class ElkLayout {
    static final double NODE_SPACING = 40;
    static final double LAYER_SPACING = 70;
    static final double EDGE_SPACING = 10;
    /** Extra straight run beyond the notation's end symbol before a line may bend. */
    static final double STRAIGHT_RUN_PADDING = 10;
    static final double COMPONENT_SPACING = 40;

    private static boolean registered;

    private ElkLayout() {
    }

    private static synchronized void register() {
        if (!registered) {
            // the service loader finds LayeredMetaDataProvider; register explicitly too in case it is shaded away
            LayoutMetaDataService service = LayoutMetaDataService.getInstance();
            if (service.getAlgorithmData(LayeredOptions.ALGORITHM_ID) == null) {
                service.registerLayoutMetaDataProviders(new LayeredMetaDataProvider());
            }
            registered = true;
        }
    }

    private record PortSpec(RoutedConnection conn, boolean source, EntityBox box, int rowCenterY) {
    }

    public static List<RoutedConnection> layout(Diagram diagram, List<EntityBox> boxes, RenderOptions options) {
        register();
        Map<Entity, EntityBox> boxOf = new IdentityHashMap<>();
        for (EntityBox b : boxes) boxOf.put(b.entity, b);

        List<RoutedConnection> conns = new ArrayList<>();
        for (Entity e : diagram.entities()) {
            for (Association a : diagram.sourceAssociations(e)) {
                conns.add(new RoutedConnection(a, boxOf.get(a.source()), boxOf.get(a.target())));
            }
        }

        // pass 1: fixed sides, free order -> learn the parent-side port order ELK prefers
        Map<RoutedConnection, Integer> targetOrder = run(boxes, conns, options, null);
        // pass 2: fixed positions
        run(boxes, conns, options, targetOrder);
        return conns;
    }

    /**
     * @param targetOrder null for the first pass (FIXED_SIDE); otherwise the rank of each connection's
     *                    target port among the ports on that parent's east side
     * @return the target port order observed after this pass
     */
    private static Map<RoutedConnection, Integer> run(List<EntityBox> boxes, List<RoutedConnection> conns,
                                                      RenderOptions options, Map<RoutedConnection, Integer> targetOrder) {
        ElkNode root = ElkGraphUtil.createGraph();
        root.setProperty(CoreOptions.ALGORITHM, LayeredOptions.ALGORITHM_ID);
        root.setProperty(CoreOptions.DIRECTION, Direction.LEFT);
        root.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);
        root.setProperty(CoreOptions.SPACING_NODE_NODE, NODE_SPACING);
        root.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, LAYER_SPACING);
        root.setProperty(CoreOptions.SPACING_EDGE_EDGE, EDGE_SPACING);
        root.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, EDGE_SPACING);
        // keep the first/last segment straight for at least the length of the end symbol (+ its label)
        double edgeNode = options.notation.indentation + STRAIGHT_RUN_PADDING;
        root.setProperty(CoreOptions.SPACING_EDGE_NODE, edgeNode);
        root.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, edgeNode);
        root.setProperty(LayeredOptions.SPACING_NODE_SELF_LOOP, edgeNode);
        root.setProperty(CoreOptions.SPACING_PORT_PORT, (double) options.notation.fanStep);
        root.setProperty(CoreOptions.SPACING_COMPONENT_COMPONENT, COMPONENT_SPACING);
        root.setProperty(CoreOptions.SEPARATE_CONNECTED_COMPONENTS, true);
        root.setProperty(LayeredOptions.COMPACTION_CONNECTED_COMPONENTS, true);
        root.setProperty(LayeredOptions.THOROUGHNESS, 20);

        Map<EntityBox, ElkNode> nodeOf = new IdentityHashMap<>();
        for (EntityBox b : boxes) {
            ElkNode n = ElkGraphUtil.createNode(root);
            n.setWidth(b.width);
            n.setHeight(b.height);
            n.setProperty(CoreOptions.PORT_CONSTRAINTS, targetOrder == null ? PortConstraints.FIXED_SIDE : PortConstraints.FIXED_POS);
            nodeOf.put(b, n);
        }

        // parent-side ports per box, positioned around the referenced row
        Map<EntityBox, List<PortSpec>> targetPorts = new IdentityHashMap<>();
        Map<RoutedConnection, ElkPort> targetPortOf = new IdentityHashMap<>();
        for (RoutedConnection c : conns) {
            int rowY = rowCenter(c.target, c.association.targetColumns());
            if (c.source == c.target) {
                // self-reference: both ports on the west side -> a compact loop beside the FK and key rows
                ElkPort p = ElkGraphUtil.createPort(nodeOf.get(c.target));
                p.setProperty(CoreOptions.PORT_SIDE, PortSide.WEST);
                p.setWidth(0);
                p.setHeight(0);
                p.setX(0);
                p.setY(rowY);
                targetPortOf.put(c, p);
                continue;
            }
            targetPorts.computeIfAbsent(c.target, k -> new ArrayList<>()).add(new PortSpec(c, false, c.target, rowY));
        }
        for (Map.Entry<EntityBox, List<PortSpec>> en : targetPorts.entrySet()) {
            EntityBox box = en.getKey();
            List<PortSpec> specs = en.getValue();
            if (targetOrder != null) {
                specs.sort(Comparator.comparingInt(s -> targetOrder.getOrDefault(s.conn(), 0)));
            }
            int[] ys = spread(specs, box, options.notation.fanStep);
            for (int i = 0; i < specs.size(); i++) {
                ElkPort p = ElkGraphUtil.createPort(nodeOf.get(box));
                p.setProperty(CoreOptions.PORT_SIDE, PortSide.EAST);
                p.setWidth(0);
                p.setHeight(0);
                p.setX(box.width);
                p.setY(ys[i]);
                targetPortOf.put(specs.get(i).conn(), p);
            }
        }

        Map<RoutedConnection, ElkEdge> edgeOf = new IdentityHashMap<>();
        for (RoutedConnection c : conns) {
            ElkPort sp = ElkGraphUtil.createPort(nodeOf.get(c.source));
            sp.setProperty(CoreOptions.PORT_SIDE, PortSide.WEST);
            sp.setWidth(0);
            sp.setHeight(0);
            sp.setX(0);
            sp.setY(rowCenter(c.source, c.association.sourceColumns()));
            ElkEdge e = ElkGraphUtil.createSimpleEdge(sp, targetPortOf.get(c));
            edgeOf.put(c, e);
        }

        new RecursiveGraphLayoutEngine().layout(root, new BasicProgressMonitor());

        for (EntityBox b : boxes) {
            ElkNode n = nodeOf.get(b);
            b.x = (int) Math.round(n.getX());
            b.y = (int) Math.round(n.getY());
        }
        Map<RoutedConnection, Integer> observedOrder = new IdentityHashMap<>();
        for (Map.Entry<EntityBox, List<PortSpec>> en : targetPorts.entrySet()) {
            List<PortSpec> specs = new ArrayList<>(en.getValue());
            specs.sort(Comparator.comparingDouble(s -> targetPortOf.get(s.conn()).getY()));
            for (int i = 0; i < specs.size(); i++) observedOrder.put(specs.get(i).conn(), i);
        }
        for (RoutedConnection c : conns) {
            ElkEdge e = edgeOf.get(c);
            List<int[]> pts = new ArrayList<>();
            for (ElkEdgeSection s : e.getSections()) {
                pts.add(new int[] {(int) Math.round(s.getStartX()), (int) Math.round(s.getStartY())});
                for (ElkBendPoint bp : s.getBendPoints()) {
                    pts.add(new int[] {(int) Math.round(bp.getX()), (int) Math.round(bp.getY())});
                }
                pts.add(new int[] {(int) Math.round(s.getEndX()), (int) Math.round(s.getEndY())});
            }
            // drop consecutive duplicates
            List<int[]> clean = new ArrayList<>();
            for (int[] p : pts) {
                if (clean.isEmpty() || clean.get(clean.size() - 1)[0] != p[0] || clean.get(clean.size() - 1)[1] != p[1]) clean.add(p);
            }
            c.xs = new int[clean.size()];
            c.ys = new int[clean.size()];
            for (int i = 0; i < clean.size(); i++) {
                c.xs[i] = clean.get(i)[0];
                c.ys[i] = clean.get(i)[1];
            }
        }
        return observedOrder;
    }

    /** Row centre (relative to the box top) of the first named column, or the box centre if hidden. */
    static int rowCenter(EntityBox box, List<String> columns) {
        if (!columns.isEmpty()) {
            int savedX = box.x;
            int savedY = box.y;
            box.x = 0;
            box.y = 0;
            Rectangle r = box.rowBounds(columns.get(0));
            box.x = savedX;
            box.y = savedY;
            if (r != null) return r.getCenter().y;
        }
        return box.height / 2;
    }

    /** Port y positions spread {@code step} apart around the mean of the referenced rows, kept inside the box. */
    static int[] spread(List<PortSpec> specs, EntityBox box, int step) {
        int n = specs.size();
        int[] ys = new int[n];
        if (n == 1) {
            ys[0] = specs.get(0).rowCenterY();
            return ys;
        }
        long sum = 0;
        for (PortSpec s : specs) sum += s.rowCenterY();
        int center = (int) (sum / n);
        int minY = EntityBox.BORDER + 2;
        int maxY = box.height - EntityBox.BORDER - 2;
        int s = step;
        if ((n - 1) * s > maxY - minY) s = Math.max(4, (maxY - minY) / (n - 1));
        int span = (n - 1) * s;
        int top = center - span / 2;
        if (top + span > maxY) top = maxY - span;
        if (top < minY) top = minY;
        for (int i = 0; i < n; i++) ys[i] = top + i * s;
        return ys;
    }
}
