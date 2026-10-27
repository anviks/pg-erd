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

    /** Smallest spacing between fanned-out ends when a table is too short for the notation's step (decorations are 10 px tall). */
    static final int MIN_FAN_STEP = 12;

    /**
     * @param columnAnchors  true to attach the source end to the FK column's row, exiting horizontally;
     *                       false for DBeaver's entity-centre anchors at both ends
     * @param targetColumns  with columnAnchors, also attach the target end to the referenced column's row
     * @param exitLeft       with columnAnchors, always leave the FK row through the table's left edge
     * @param fanStep        vertical spacing between row-anchored ends that share a table side
     */
    public static List<RoutedConnection> route(
        List<EntityBox> boxes,
        List<RoutedConnection> connections,
        Map<Association, List<Point>> layoutBends,
        int indentation,
        boolean columnAnchors,
        boolean targetColumns,
        boolean exitLeft,
        int fanStep
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
        Map<RoutedConnection, Rectangle> srcAnchor = new IdentityHashMap<>();
        Map<RoutedConnection, Rectangle> trgAnchor = new IdentityHashMap<>();
        for (RoutedConnection c : connections) {
            Rectangle sa = null;
            Rectangle ta = null;
            if (columnAnchors) {
                sa = c.association.sourceColumns().isEmpty() ? null : c.source.rowBounds(c.association.sourceColumns().get(0));
                if (targetColumns) {
                    ta = c.association.targetColumns().isEmpty() ? null : c.target.rowBounds(c.association.targetColumns().get(0));
                }
            }
            srcAnchor.put(c, sa);
            trgAnchor.put(c, ta);
            Path path = new Path(c);
            if (sa != null && exitLeft) {
                // start the search at the end of the left stub, outside the table, so the router goes around it if needed
                path.setStartPoint(new Point(sa.x - 1 - indentation, sa.getCenter().y));
            } else {
                path.setStartPoint(sa != null ? sa.getCenter() : center(c.source));
            }
            path.setEndPoint(ta != null ? ta.getCenter() : center(c.target));
            List<Point> bends = layoutBends.get(c.association);
            if (bends == null && c.source == c.target) {
                bends = selfLoopBends(c.source, exitLeft && sa != null);
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
            Rectangle srcRow = srcAnchor.get(current);
            Rectangle trgRow = trgAnchor.get(current);
            Point start = srcRow != null ? (exitLeft ? leftExit(srcRow) : rowExit(srcRow, ref1)) : chopbox(srcBounds, ref1);
            Point end = trgRow != null ? rowExit(trgRow, ref2) : chopbox(trgBounds, ref2);
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
                    if (srcRow == null && dxStart == 0 && dyStart < POINT_DISTANCE) {
                        start = new Point(start.x + dxSrcTrg, start.y - dySrcTrg);
                        Point first = points.getPoint(0);
                        points.setPoint(new Point(first.x + dxSrcTrg, first.y - dySrcTrg), 0);
                    }
                    if (trgRow == null && dxEnd == 0 && dyEnd < POINT_DISTANCE) {
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
        if (columnAnchors) {
            fanOutSharedRowEnds(connections, srcAnchor, trgAnchor, fanStep);
        }
        return connections;
    }

    /**
     * Column anchors: the line leaves the row through its left or right edge at the row's vertical
     * centre, on the side facing the next path point (ChopboxAnchor geometry: left edge at x-1, right at x+w).
     */
    /** Column anchors with left exits: always the row's left edge (ChopboxAnchor puts it at x-1). */
    static Point leftExit(Rectangle row) {
        return new Point(row.x - 1, row.getCenter().y);
    }

    static Point rowExit(Rectangle row, Point reference) {
        Point c = row.getCenter();
        int x = reference.x < c.x ? row.x - 1 : row.x + row.width;
        return new Point(x, c.y);
    }

    /** Ends collide only when they hit the same row from the same side. */
    private record SideKey(EntityBox box, int x, int rowY) {
    }

    /** One line end in a fan-out group: which connection, which end, its row, and where it approaches from. */
    private record FanEnd(RoutedConnection conn, boolean isEnd, Rectangle row, int approachY, int approachX) {
    }

    /**
     * Lines attached to the same row on the same table side would meet at one point. Spread them
     * {@code fanStep} px apart along that side, centred on the row and spilling over neighbouring rows
     * when needed (but never outside the table), ordered by where each line comes from so they do not
     * cross at the edge. Ends on different rows are left exactly on their rows.
     */
    static void fanOutSharedRowEnds(List<RoutedConnection> connections, Map<RoutedConnection, Rectangle> srcAnchor,
                                    Map<RoutedConnection, Rectangle> trgAnchor, int fanStep) {
        Map<SideKey, List<FanEnd>> groups = new LinkedHashMap<>();
        for (RoutedConnection c : connections) {
            int n = c.size();
            if (n < 3) continue;
            Rectangle sr = srcAnchor.get(c);
            Rectangle tr = trgAnchor.get(c);
            if (sr != null) {
                // the point after the start stub shows where the line heads
                groups.computeIfAbsent(new SideKey(c.source, c.xs[0], sr.y), k -> new ArrayList<>())
                    .add(new FanEnd(c, false, sr, c.ys[2], c.xs[2]));
            }
            if (tr != null) {
                groups.computeIfAbsent(new SideKey(c.target, c.xs[n - 1], tr.y), k -> new ArrayList<>())
                    .add(new FanEnd(c, true, tr, c.ys[n - 3], c.xs[n - 3]));
            }
        }
        for (Map.Entry<SideKey, List<FanEnd>> g : groups.entrySet()) {
            List<FanEnd> ends = g.getValue();
            if (ends.size() < 2) continue;
            EntityBox box = g.getKey().box();
            long sum = 0;
            for (FanEnd e : ends) sum += e.row().getCenter().y;
            int centerY = (int) (sum / ends.size());
            // by target row first, then lines arriving from above land higher; ties by horizontal distance
            ends.sort((a, b) -> {
                if (a.row().y != b.row().y) return Integer.compare(a.row().y, b.row().y);
                if (a.approachY() != b.approachY()) return Integer.compare(a.approachY(), b.approachY());
                return Integer.compare(a.approachX(), b.approachX());
            });
            int n = ends.size();
            int minY = box.y + EntityBox.BORDER + 1;
            int maxY = box.bottom() - EntityBox.BORDER - 1;
            int step = fanStep;
            if ((n - 1) * step > maxY - minY) {
                step = Math.max(MIN_FAN_STEP, (maxY - minY) / (n - 1));
            }
            boolean crowded = step < fanStep;
            int span = (n - 1) * step;
            int top = centerY - span / 2;
            if (top + span > maxY) top = maxY - span;
            if (top < minY) top = minY;
            for (int k = 0; k < n; k++) {
                int y = top + k * step;
                FanEnd e = ends.get(k);
                RoutedConnection c = e.conn();
                if (e.isEnd()) {
                    int last = c.size() - 1;
                    c.ys[last] = y;
                    c.ys[last - 1] = y;
                    c.crowdedEnd = crowded;
                } else {
                    c.ys[0] = y;
                    c.ys[1] = y;
                    c.crowdedStart = crowded;
                }
            }
        }
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
    static List<Point> selfLoopBends(EntityBox b, boolean leftSide) {
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
        if (leftSide) {
            // mirror the loop to the left of the table, where the FK row exits
            for (Point p : bends) p.x = 2 * c.x - p.x;
        }
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
