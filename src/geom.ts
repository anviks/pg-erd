/** Mutable integer rectangle (x, y, width, height). */
export class Rect {
  constructor(public x = 0, public y = 0, public width = 0, public height = 0) {}

  isEmpty(): boolean {
    return this.width <= 0 || this.height <= 0;
  }
  right(): number {
    return this.x + this.width;
  }
  bottom(): number {
    return this.y + this.height;
  }
  centerX(): number {
    return this.x + Math.trunc(this.width / 2);
  }
  centerY(): number {
    return this.y + Math.trunc(this.height / 2);
  }
  intersects(r: Rect): boolean {
    return (
      !this.isEmpty() && !r.isEmpty() && r.x < this.right() && r.y < this.bottom() && r.right() > this.x && r.bottom() > this.y
    );
  }
  /** Grows this rectangle to also cover `r` (ignored when empty). Returns this. */
  union(r: Rect | null | undefined): Rect {
    if (!r || r.isEmpty()) return this;
    const right = Math.max(this.right(), r.right());
    const bottom = Math.max(this.bottom(), r.bottom());
    this.x = Math.min(this.x, r.x);
    this.y = Math.min(this.y, r.y);
    this.width = right - this.x;
    this.height = bottom - this.y;
    return this;
  }
  /** Grows by `h` on the left and right and `v` on the top and bottom. Returns this. */
  expand(h: number, v: number): Rect {
    this.x -= h;
    this.y -= v;
    this.width += 2 * h;
    this.height += 2 * v;
    return this;
  }
  toString(): string {
    return `Rect(${this.x}, ${this.y}, ${this.width}, ${this.height})`;
  }
}

/** An immutable list of integer points as parallel x/y arrays. */
export class Points {
  constructor(
    public readonly xs: readonly number[],
    public readonly ys: readonly number[],
  ) {
    if (xs.length !== ys.length) throw new Error('xs and ys differ in length');
  }
  get size(): number {
    return this.xs.length;
  }
  bounds(): Rect {
    if (this.xs.length === 0) return new Rect();
    const minX = Math.min(...this.xs);
    const maxX = Math.max(...this.xs);
    const minY = Math.min(...this.ys);
    const maxY = Math.max(...this.ys);
    return new Rect(minX, minY, maxX - minX, maxY - minY);
  }
}
