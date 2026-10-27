package io.dbeavererd.route;

import io.dbeavererd.figure.EntityBox;
import io.dbeavererd.model.Association;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PointList;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.draw2d.graph.Path;
import org.eclipse.draw2d.graph.ShortestPathRouter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of DBeaver's {@code ShortPathRouting} (its only connection router): Draw2D's
 * {@link ShortestPathRouter} between entity centres, bendpoints inside entities dropped, endpoints
 * clipped to the entity border (ChopboxAnchor), a small nudge when two lines would meet an entity
 * at the same spot, and a perpendicular stub of {@code indentation} px at both ends.
 */
public final class ConnectionRouter {
    private static final int POINT_DISTANCE = 7;
    private static final int RIGHT = 180;
    private static final int LEFT = 0;
    private static final int UP = 90;
    private static final int DOWN = -90;

    private ConnectionRouter() {
    }

    public static List<RoutedConnection> route(
        List<EntityBox> boxes,
        List<RoutedConnection> connections,
        Map<Association, List<Point>> layoutBends,
        int indentation
    ) {
        ShortestPathRouter algorithm = new ShortestPathRouter();
        List<Rectangle> obstacles = new ArrayList<>();
        for (EntityBox b : boxes) {
            Rectangle r = new Rectangle(b.x, b.y, b.width, b.height);
            obstacles.add(r);
            algorithm.addObstacle(r);
        }

        Map<Path, RoutedConnection> connOf = new IdentityHashMap<>();
        List<Path> paths = new ArrayList<>();
        for (RoutedConnection c : connections) {
            Path path = new Path(c);
            path.setStartPoint(center(c.source));
            path.setEndPoint(center(c.target));
            List<Point> bends = layoutBends.get(c.association);
            if (bends == null && c.source == c.target) {
                bends = selfLoopBends(c.source);
            }
            if (bends != null && !bends.isEmpty()) {
                PointList pl = new PointList(bends.size());
                for (Point p : bends) pl.addPoint(p);
                path.setBendPoints(pl);
            } else {
                path.setBendPoints(null);
            }
            algorithm.addPath(path);
            paths.add(path);
            connOf.put(path, c);
        }

        // computePaths(): solve, drop bendpoints that fall inside an entity, solve again
        List<Path> solved = algorithm.solve();
        for (Path path : solved) {
            removeOverlappingBendPoints(path, obstacles);
        }
        solved = algorithm.solve();

        // route(): DBeaver's HashMap iteration order is replaced by the deterministic path order
        Map<RoutedConnection, PointList> routed = new LinkedHashMap<>();
        for (RoutedConnection c : connections) routed.put(c, new PointList());

        for (Path path : solved) {
            RoutedConnection current = connOf.get(path);
            PointList points = path.getPoints().getCopy();
            Point ref1 = points.getPoint(1);
            Point ref2 = points.getPoint(points.size() - 2);
            Rectangle srcBounds = bounds(current.source);
            Rectangle trgBounds = bounds(current.target);
            Point start = chopbox(srcBounds, ref1);
            Point end = chopbox(trgBounds, ref2);
            points.setPoint(start, 0);
            points.setPoint(end, points.size() - 1);

            int srcTrgAngle = 90 - getDirection(srcBounds, points.getPoint(0));
            int trgSrcAngle = -90 + getDirection(trgBounds, points.getPoint(points.size() - 1));
            int dxSrcTrg = (int) (Math.cos(Math.toRadians(srcTrgAngle)) * indentation);
            int dySrcTrg = (int) (Math.sin(Math.toRadians(srcTrgAngle)) * indentation);
            int dxTrgSrc = (int) (Math.cos(Math.toRadians(trgSrcAngle)) * indentation);
            int dyTrgSrc = (int) (Math.sin(Math.toRadians(trgSrcAngle)) * indentation);

            for (Map.Entry<RoutedConnection, PointList> entry : routed.entrySet()) {
                if (entry.getKey() == current) continue;
                PointList other = entry.getValue();
                for (int i = 0; i < other.size(); i++) {
                    Point p = other.getPoint(i);
                    int dxStart = Math.abs(start.x - p.x);
                    int dyStart = Math.abs(start.y - p.y);
                    int dxEnd = Math.abs(end.x - p.x);
                    int dyEnd = Math.abs(end.y - p.y);
                    if (dxStart == 0 && dyStart < POINT_DISTANCE) {
                        start = new Point(start.x + dxSrcTrg, start.y - dySrcTrg);
                        Point first = points.getPoint(0);
                        points.setPoint(new Point(first.x + dxSrcTrg, first.y - dySrcTrg), 0);
                    }
                    if (dxEnd == 0 && dyEnd < POINT_DISTANCE) {
                        end = new Point(end.x - dxTrgSrc, end.y - dyTrgSrc);
                        Point last = points.getPoint(points.size() - 1);
                        points.setPoint(new Point(last.x - dxTrgSrc, last.y - dyTrgSrc), points.size() - 1);
                    }
                }
            }

            PointList modified = new PointList();
            modified.addPoint(points.getFirstPoint());
            int direction1 = 180 - getDirection(srcBounds, points.getPoint(0).getCopy());
            int direction2 = getDirection(trgBounds, points.getPoint(points.size() - 1).getCopy());
            int dx1 = (int) (Math.cos(Math.toRadians(direction1)) * indentation);
            int dy1 = (int) (Math.sin(Math.toRadians(direction1)) * indentation);
            int dx2 = (int) (Math.cos(Math.toRadians(direction2)) * indentation);
            int dy2 = (int) (Math.sin(Math.toRadians(direction2)) * indentation);
            modified.addPoint(new Point(start.x + dx1, start.y - dy1));
            for (int i = 1; i < points.size() - 1; i++) {
                modified.addPoint(points.getPoint(i));
            }
            modified.addPoint(new Point(end.x - dx2, end.y - dy2));
            modified.addPoint(points.getLastPoint());

            routed.put(current, modified);
            current.xs = new int[modified.size()];
            current.ys = new int[modified.size()];
            for (int i = 0; i < modified.size(); i++) {
                Point p = modified.getPoint(i);
                current.xs[i] = p.x;
                current.ys[i] = p.y;
            }
        }
        return connections;
    }

    static Rectangle bounds(EntityBox b) {
        return new Rectangle(b.x, b.y, b.width, b.height);
    }

    /** ChopboxAnchor.getReferencePoint(): the box centre. */
    static Point center(EntityBox b) {
        return bounds(b).getCenter();
    }

    /**
     * AssociationPart.setConnectionRouting: four RelativeBendpoints for a self-reference, which with
     * both anchors at the box centre and weight 0.5 evaluate to centre + (d1 + d2) / 2.
     */
    static List<Point> selfLoopBends(EntityBox b) {
        int w = b.width;
        int h = b.height;
        int w2 = w / 2;
        int h2 = h / 2;
        Point c = center(b);
        List<Point> bends = new ArrayList<>();
        bends.add(relative(c, w, h2, w / 2, -h2 + w2));
        bends.add(relative(c, w, h2, w, -h2 + w2 / 2));
        bends.add(relative(c, w, h2, w, -h2 - w2 / 2));
        bends.add(relative(c, w, h2, w / 2, -h2 - w2));
        return bends;
    }

    private static Point relative(Point c, int d1w, int d1h, int d2w, int d2h) {
        double weight = 0.5;
        double x = (c.x + d1w) * (1.0 - weight) + weight * (c.x + d2w);
        double y = (c.y + d1h) * (1.0 - weight) + weight * (c.y + d2h);
        return new Point((int) Math.floor(x + 0.000000001), (int) Math.floor(y + 0.000000001));
    }

    static void removeOverlappingBendPoints(Path path, List<Rectangle> obstacles) {
        PointList bendPoints = path.getBendPoints();
        if (bendPoints == null) return;
        PointList actual = new PointList(bendPoints.size());
        for (int i = 0; i < bendPoints.size(); i++) {
            Point bp = bendPoints.getPoint(i);
            boolean skip = false;
            for (Rectangle r : obstacles) {
                if (r.contains(bp)) {
                    skip = true;
                    break;
                }
            }
            if (!skip) actual.addPoint(bp);
        }
        path.setBendPoints(actual);
    }

    /** ChopboxAnchor.getLocation(reference). */
    static Point chopbox(Rectangle box, Point reference) {
        Rectangle r = box.getCopy();
        r.translate(-1, -1);
        r.resize(1, 1);
        float centerX = r.x + 0.5f * r.width;
        float centerY = r.y + 0.5f * r.height;
        if (r.isEmpty() || (reference.x == (int) centerX && reference.y == (int) centerY)) {
            return new Point((int) centerX, (int) centerY);
        }
        float dx = reference.x - centerX;
        float dy = reference.y - centerY;
        float scale = 0.5f / Math.max(Math.abs(dx) / r.width, Math.abs(dy) / r.height);
        dx *= scale;
        dy *= scale;
        centerX += dx;
        centerY += dy;
        return new Point(Math.round(centerX), Math.round(centerY));
    }

    /** ShortPathRouting.getDirection: the side of the rectangle closest to the point. */
    static int getDirection(Rectangle r, Point p) {
        int direction = LEFT;
        int distance = Math.abs(r.x - p.x);
        int i = Math.abs(r.y - p.y);
        if (i <= distance) {
            distance = i;
            direction = UP;
        }
        i = Math.abs(r.bottom() - p.y);
        if (i <= distance) {
            distance = i;
            direction = DOWN;
        }
        i = Math.abs(r.right() - p.x);
        if (i < distance) {
            direction = RIGHT;
        }
        return direction;
    }
}
