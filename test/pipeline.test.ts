import { mkdirSync, writeFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import type { Diagram } from '../src/model/index.js';
import { isSelfReference } from '../src/model/index.js';
import { toPng } from '../src/render/png.js';
import { render, type RenderedDiagram } from '../src/render/pipeline.js';
import { LOOK_NAMES, Look } from '../src/style/look.js';
import { straightRun, type Notation } from '../src/style/options.js';
import { hub, multiFk, options, shop } from './fixtures.js';

const OUT = 'test-output';
mkdirSync(OUT, { recursive: true });

const NOTATIONS: Notation[] = ['idef1x', 'crowsfoot'];

function check(d: RenderedDiagram, what: string): void {
  for (let i = 0; i < d.boxes.length; i++) {
    for (let j = i + 1; j < d.boxes.length; j++) {
      const a = d.boxes[i];
      const b = d.boxes[j];
      expect(a.bounds().intersects(b.bounds()), `${what}: ${a.entity} overlaps ${b.entity}`).toBe(false);
    }
  }
  for (const c of d.connections) {
    const name = `${what} ${c.association.name}`;
    expect(c.size, name).toBeGreaterThanOrEqual(2);
    for (let i = 1; i < c.size; i++) {
      expect(c.xs[i] === c.xs[i - 1] || c.ys[i] === c.ys[i - 1], `${name}: segment ${i} not axis-aligned`).toBe(true);
    }
    if (c.size >= 3) {
      const straight = straightRun(d.options.notation);
      expect(Math.abs(c.xs[1] - c.xs[0]), `${name}: first segment shorter than the end symbol`).toBeGreaterThanOrEqual(straight);
      expect(Math.abs(c.xs[c.size - 1] - c.xs[c.size - 2]), `${name}: last segment shorter than the end symbol`).toBeGreaterThanOrEqual(straight);
    }
    const fkRow = c.source.rowBounds(c.association.sourceColumns[0])!;
    expect(c.xs[0], `${name}: starts on the source's left edge`).toBe(c.source.x);
    expect(c.ys[0], `${name}: starts at the FK row`).toBe(fkRow.centerY());
    const n = c.size;
    if (isSelfReference(c.association)) expect(c.xs[n - 1], `${name}: self-loop ends on the left edge`).toBe(c.target.x);
    else expect(c.xs[n - 1], `${name}: ends on the target's right edge`).toBe(c.target.right());
    expect(c.ys[n - 1] > c.target.y && c.ys[n - 1] < c.target.bottom(), `${name}: end inside the target`).toBe(true);
    for (const b of d.boxes) {
      if (b === c.source || b === c.target) continue;
      const r = b.bounds();
      for (let i = 1; i < n; i++) {
        const seg = { x: Math.min(c.xs[i - 1], c.xs[i]), y: Math.min(c.ys[i - 1], c.ys[i]), w: Math.abs(c.xs[i] - c.xs[i - 1]) + 1, h: Math.abs(c.ys[i] - c.ys[i - 1]) + 1 };
        const hit = seg.x < r.right() && seg.y < r.bottom() && seg.x + seg.w > r.x && seg.y + seg.h > r.y;
        expect(hit, `${name}: crosses ${b.entity}`).toBe(false);
      }
    }
  }
}

describe('pipeline', () => {
  it('lays the shop fixture out orthogonally, anchored to columns', async () => {
    for (const notation of NOTATIONS) {
      const o = options();
      o.notation = notation;
      const d = await render(shop(), o);
      check(d, notation);
      writeFileSync(`${OUT}/shop-${notation}.png`, toPng(d));
    }
    const full = options();
    full.notation = 'crowsfoot';
    full.styles = new Set(['icons', 'types', 'nullability', 'comments']);
    const d = await render(shop(), full);
    writeFileSync(`${OUT}/shop-full.png`, toPng(d));
    writeFileSync(`${OUT}/shop-full.svg`, d.toSvg());
  });

  it('exports match the extent', async () => {
    const o = options();
    const d = await render(shop(), o);
    const extent = d.extent();
    const svg = d.toSvg();
    expect(svg.startsWith('<?xml')).toBe(true);
    expect(svg).toContain(`width="${extent.width + 20}"`);
    expect(svg).toContain('<use xlink:href="#icon-tree-table"');
    expect(svg).toContain('stroke-dasharray="1 1"');
    const png = toPng(d, svg);
    // PNG IHDR: width at byte 16, height at byte 20
    expect(png.readUInt32BE(16)).toBe(extent.width + 20);
    expect(png.readUInt32BE(20)).toBe(extent.height + 20);
    expect(toPng(d, svg, { scale: 2 }).readUInt32BE(16)).toBe((extent.width + 20) * 2);
    expect(toPng(d, svg, { scale: 1.5 }).readUInt32BE(16)).toBe(Math.round((extent.width + 20) * 1.5));
  });

  it('renders every look in both themes', async () => {
    for (const name of LOOK_NAMES) {
      for (const dark of [false, true]) {
        for (const notation of NOTATIONS) {
          const o = options(Look.parse(name), dark);
          o.notation = notation;
          o.styles = new Set(['icons', 'types', 'nullability']);
          const d = await render(shop(), o);
          check(d, `${name}${dark ? '-dark' : ''}-${notation}`);
          const file = `${OUT}/look-${name}${dark ? '-dark' : ''}-${notation}`;
          writeFileSync(`${file}.png`, toPng(d));
          writeFileSync(`${file}.svg`, d.toSvg());
        }
      }
    }
  });

  it('handles fan-in and fan-out fixtures', async () => {
    const fixtures: Diagram[] = [hub(7), multiFk(6)];
    for (const f of fixtures) {
      const o = options();
      o.notation = 'crowsfoot';
      const d = await render(f, o);
      check(d, f.name);
      writeFileSync(`${OUT}/${f.name}.png`, toPng(d));
    }
  });

  it('embeds the font on request', async () => {
    const o = options();
    o.embedSvgFont = true;
    const d = await render(shop(), o);
    expect(d.toSvg()).toContain('@font-face');
  });
});
