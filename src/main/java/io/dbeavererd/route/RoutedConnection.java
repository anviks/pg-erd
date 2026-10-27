package io.dbeavererd.route;

import io.dbeavererd.figure.EntityBox;
import io.dbeavererd.model.Association;

/** Final polyline of one association, from the source (FK child) box to the target (parent) box. */
public final class RoutedConnection {
    public final Association association;
    public final EntityBox source;
    public final EntityBox target;
    public int[] xs;
    public int[] ys;
    /** Set by the fan-out when ends on this side are packed tighter than the notation needs for labels. */
    public boolean crowdedStart;
    public boolean crowdedEnd;

    public RoutedConnection(Association association, EntityBox source, EntityBox target) {
        this.association = association;
        this.source = source;
        this.target = target;
        this.xs = new int[0];
        this.ys = new int[0];
    }

    public int size() { return xs.length; }
}
