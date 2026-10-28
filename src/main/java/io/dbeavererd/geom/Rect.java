package io.dbeavererd.geom;

/** Mutable integer rectangle (x, y, width, height), in the style of Draw2D's Rectangle. */
public final class Rect {
    public int x;
    public int y;
    public int width;
    public int height;

    public Rect() {
    }

    public Rect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    public boolean intersects(Rect r) {
        return !isEmpty() && !r.isEmpty()
            && r.x < right() && r.y < bottom() && r.right() > x && r.bottom() > y;
    }

    /** Grows this rectangle to also cover {@code r} (ignored when empty). Returns this. */
    public Rect union(Rect r) {
        if (r == null || r.isEmpty()) return this;
        int right = Math.max(right(), r.right());
        int bottom = Math.max(bottom(), r.bottom());
        x = Math.min(x, r.x);
        y = Math.min(y, r.y);
        width = right - x;
        height = bottom - y;
        return this;
    }

    /** Grows this rectangle by {@code h} on the left and right and {@code v} on the top and bottom. Returns this. */
    public Rect expand(int h, int v) {
        x -= h;
        y -= v;
        width += 2 * h;
        height += 2 * v;
        return this;
    }

    @Override
    public String toString() {
        return "Rect(" + x + ", " + y + ", " + width + ", " + height + ")";
    }
}
