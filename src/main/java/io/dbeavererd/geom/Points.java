package io.dbeavererd.geom;

/** An immutable list of integer points stored as parallel x/y arrays (what {@code Canvas} consumes). */
public final class Points {
    public final int[] xs;
    public final int[] ys;

    public Points(int[] xs, int[] ys) {
        if (xs.length != ys.length) throw new IllegalArgumentException("xs and ys differ in length");
        this.xs = xs;
        this.ys = ys;
    }

    public int size() {
        return xs.length;
    }

    public int x(int i) {
        return xs[i];
    }

    public int y(int i) {
        return ys[i];
    }

    /** Smallest rectangle containing every point (zero-sized for a single point, empty for none). */
    public Rect bounds() {
        if (xs.length == 0) return new Rect();
        int minX = xs[0];
        int maxX = xs[0];
        int minY = ys[0];
        int maxY = ys[0];
        for (int i = 1; i < xs.length; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        return new Rect(minX, minY, maxX - minX, maxY - minY);
    }
}
