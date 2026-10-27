package io.dbeavererd.layout;

import io.dbeavererd.figure.EntityBox;
import io.dbeavererd.model.Association;
import io.dbeavererd.model.Diagram;
import io.dbeavererd.model.Entity;
import org.eclipse.draw2d.PositionConstants;
import org.eclipse.draw2d.geometry.Insets;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.graph.DirectedGraph;
import org.eclipse.draw2d.graph.DirectedGraphLayout;
import org.eclipse.draw2d.graph.Edge;
import org.eclipse.draw2d.graph.Node;
import org.eclipse.draw2d.graph.Subgraph;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DBeaver's "Arrange all": a port of {@code DirectedGraphLayoutVisitor} +
 * {@code NodeJoiningDirectedGraphLayout} + {@code StandaloneNodeConnector} on top of Draw2D's
 * {@link DirectedGraphLayout}. Sets {@link EntityBox#x}/{@link EntityBox#y} and returns the
 * bendpoints the layout assigns to long edges.
 */
public final class AutoLayout {
    /** ERDDecoratorDefault.getDefaultEntityInsets() for the shortest-path router. */
    static final Insets ENTITY_PADDING = new Insets(40, 40, 30, 30);
    static final int EDGE_PADDING = 10;
    static final int EDGE_WEIGHT = 2;

    private AutoLayout() {
    }

    /**
     * @param aspect viewport width/height ratio used to decide how many disconnected entities go in one row
     * @return bendpoints per association (only for edges spanning more than two ranks)
     */
    public static Map<Association, List<Point>> layout(Diagram diagram, List<EntityBox> boxes, double aspect) {
        Map<Entity, EntityBox> boxOf = new IdentityHashMap<>();
        for (EntityBox b : boxes) boxOf.put(b.entity, b);

        DirectedGraph graph = new DirectedGraph();
        graph.setDirection(PositionConstants.EAST);

        // nodes, in diagram order
        Map<EntityBox, Node> nodeOf = new IdentityHashMap<>();
        for (EntityBox box : boxes) {
            Node node;
            if (diagram.hasSelfReference(box.entity)) {
                node = new Subgraph(box);
            } else {
                node = new Node(box);
            }
            node.width = box.width;
            node.height = box.height;
            node.setPadding(ENTITY_PADDING);
            nodeOf.put(box, node);
            graph.nodes.add(node);
            if (node instanceof Subgraph sg) {
                // DBeaver's fake anchor nodes: members of the subgraph, but never added to graph.nodes
                Node sourceAnchor = new Node("Fake node for source links", sg);
                sourceAnchor.width = 0;
                sourceAnchor.height = 0;
                Node targetAnchor = new Node("Fake node for target links", sg);
                targetAnchor.width = 0;
                targetAnchor.height = 0;
            }
        }
        if (graph.nodes.isEmpty()) {
            return Map.of();
        }

        // edges: for each entity in order, its outgoing (FK child) associations in model order
        Map<Association, Edge> edgeOf = new LinkedHashMap<>();
        for (EntityBox box : boxes) {
            for (Association a : diagram.sourceAssociations(box.entity)) {
                Node source = nodeOf.get(boxOf.get(a.source()));
                Node target = nodeOf.get(boxOf.get(a.target()));
                if (source instanceof Subgraph ssg && target instanceof Subgraph tsg) {
                    source = ssg.members.get(0);
                    target = tsg.members.get(1);
                }
                Edge e = new Edge(a, source, target);
                e.setPadding(EDGE_PADDING);
                e.weight = EDGE_WEIGHT;
                graph.edges.add(e);
                edgeOf.put(a, e);
            }
        }

        connectStandaloneNodes(graph, aspect);
        new DirectedGraphLayout().visit(graph);

        for (EntityBox box : boxes) {
            Node n = nodeOf.get(box);
            box.x = n.x;
            box.y = n.y;
        }

        Map<Association, List<Point>> bends = new LinkedHashMap<>();
        for (Map.Entry<Association, Edge> en : edgeOf.entrySet()) {
            Edge e = en.getValue();
            if (e.vNodes != null && e.vNodes.size() > 1) {
                List<Point> list = new ArrayList<>();
                for (Node vn : e.vNodes) {
                    int x = vn.x;
                    int y = vn.y;
                    list.add(new Point(x, y));
                    if (e.isFeedback()) {
                        list.add(new Point(x, y + vn.height));
                        list.add(new Point(x, y));
                    } else {
                        list.add(new Point(x, y));
                        list.add(new Point(x, y + vn.height));
                    }
                }
                bends.put(en.getKey(), list);
            }
        }
        return bends;
    }

    /** StandaloneNodeConnector.setDummyEdges: chain disconnected entities into rows with invisible edges. */
    static void connectStandaloneNodes(DirectedGraph graph, double aspect) {
        if (graph.nodes.size() <= 1) return;
        graph.nodes.sort((o1, o2) -> {
            int connCount1 = o2.outgoing.size() + o2.incoming.size();
            int connCount2 = o1.outgoing.size() + o1.incoming.size();
            if (connCount1 == 0 && connCount1 == connCount2) {
                if (o1.data instanceof EntityBox b1 && o2.data instanceof EntityBox b2) {
                    return b1.height - b2.height;
                }
                return 0;
            }
            return connCount1 - connCount2;
        });
        List<Node> unconnected = new ArrayList<>();
        for (Node n : graph.nodes) {
            if (n.outgoing.size() + n.incoming.size() == 0) unconnected.add(n);
        }
        int nodeCount = unconnected.size();
        if (nodeCount > 1) {
            double middleRowSize = Math.sqrt(nodeCount);
            int nodesInLine = (int) (middleRowSize * aspect) + 1;
            for (int i = 0; i < nodeCount; i++) {
                for (int k = 0; k < nodesInLine - 1 && i < nodeCount - 1; k++, i++) {
                    Edge edge = new Edge(null, unconnected.get(i), unconnected.get(i + 1));
                    edge.weight = EDGE_WEIGHT;
                    graph.edges.add(edge);
                }
            }
        }
    }
}
