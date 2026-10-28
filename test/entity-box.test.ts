import { describe, expect, it } from 'vitest';
import { EntityBox } from '../src/figure/entity-box.js';
import { Icons } from '../src/figure/icons.js';
import { Entity } from '../src/model/index.js';
import { SvgCanvas } from '../src/render/canvas.js';
import { customers, options } from './fixtures.js';
import { readdirSync } from 'node:fs';
import { resourcePath } from '../src/resources.js';

describe('EntityBox', () => {
  it("follows DBeaver's box model", () => {
    const o = options();
    const m = o.fonts;
    const box = new EntityBox(customers(), o, m);
    const th = m.height(o.fonts.regular);
    const rowH = Math.max(16, th) + 4;
    expect(box.titleH).toBe(Math.max(16, th) + 6);
    expect(box.keys.rows.length).toBe(1);
    expect(box.attrs.rows.length).toBe(4);
    expect(box.keys.prefH).toBe(6 + rowH - 2);
    expect(box.attrs.prefH).toBe(6 + 4 * rowH - 2);
    expect(box.height).toBe(box.titleH + box.keys.prefH + box.attrs.prefH + 4);
    const widest = Math.max(...box.attrs.rows.map((r) => r.itemW));
    expect(box.attrs.prefW).toBe(10 + widest - 4);
    expect(box.width).toBeGreaterThanOrEqual(box.attrs.prefW + 4);
  });

  it('measures and draws text the bundled font lacks with a fallback face', () => {
    const o = options();
    o.styles = new Set(['icons', 'comments']);
    const e = new Entity('public', 'units', 'table', 'Организации');
    e.addAttribute({ name: 'inn', fullTypeName: 'varchar(12)', iconId: 'types/string', required: false, inPrimaryKey: false, inForeignKey: false, inAnyConstraint: false, comment: 'ИНН (Russian tax ID)' });
    const box = new EntityBox(e, o, o.fonts);
    const latinOnly = o.fonts.widthInt('inn - (Russian tax ID)', o.fonts.regular);
    expect(box.attrs.rows[0].labelTextW).toBeGreaterThan(latinOnly + 10);
    expect(box.descW).toBeGreaterThan(0);
    const canvas = new SvgCanvas(new Icons(), o.fonts, 0, 0);
    box.paint(canvas);
    const svg = canvas.toSvg(box.width, box.height, '#ffffff', false);
    expect(svg).toContain('Организации');
    expect(svg).toMatch(/<tspan font-family="[^"]+">ИНН/);
  });

  it('parses every bundled icon', () => {
    const icons = new Icons();
    for (const dir of ['tree', 'types']) {
      for (const f of readdirSync(resourcePath(`icons/${dir}`))) {
        if (!f.endsWith('.svg')) continue;
        const s = icons.shape(`${dir}/${f.replace(/\.svg$/, '')}`);
        expect(s.paths.length).toBeGreaterThan(0);
      }
    }
  });
});
