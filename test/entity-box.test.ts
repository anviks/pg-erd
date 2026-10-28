import { describe, expect, it } from 'vitest';
import { EntityBox } from '../src/figure/entity-box.js';
import { Icons } from '../src/figure/icons.js';
import { Entity } from '../src/model/index.js';
import { SvgCanvas } from '../src/render/canvas.js';
import { customers, options } from './fixtures.js';
import { Look } from '../src/style/look.js';
import { readdirSync } from 'node:fs';
import { resourcePath } from '../src/resources.js';

describe('EntityBox', () => {
  it('sizes a table from its rows and padding', () => {
    for (const look of [Look.clean(), Look.dbeaver(), Look.paper()]) {
      const o = options(look);
      const m = o.fonts;
      const box = new EntityBox(customers(), o, m);
      const th = m.height(o.fonts.regular);
      const rowH = 2 * look.rowPadY + Math.max(16, th);
      expect(box.keys.rows.length).toBe(1);
      expect(box.attrs.rows.length).toBe(4);
      expect(box.keys.height).toBe(2 * look.padY + rowH);
      expect(box.attrs.height).toBe(2 * look.padY + 4 * rowH);
      expect(box.height).toBe(2 * look.borderWidth + box.titleH + 2 * look.separator + box.keys.height + box.attrs.height);
      const titleW = (look.titleIcon ? 19 : 0) + box.titleTextW + 2 * look.titlePadX;
      expect(box.width, look.name).toBe(Math.max(titleW, 2 * look.padX + box.col1W) + 2 * look.borderWidth);
      expect(box.rowBounds('email')!.y).toBe(box.y + look.borderWidth + box.titleH + 2 * look.separator + box.keys.height + look.padY);
    }
  });

  it('keeps the type column when types are shown', () => {
    const o = options();
    o.styles = new Set(['icons', 'types', 'nullability']);
    const box = new EntityBox(customers(), o, o.fonts);
    expect(box.col2W).toBeGreaterThan(0);
    expect(box.width).toBe(2 + 16 + box.col1W + 12 + box.col2W);
  });

  it('draws Cyrillic with the bundled font, other scripts with a fallback face', () => {
    const o = options();
    o.styles = new Set(['icons', 'comments']);
    const e = new Entity('public', 'units', 'table', 'Организации 単位');
    e.addAttribute({ name: 'inn', fullTypeName: 'varchar(12)', iconId: 'types/string', required: false, inPrimaryKey: false, inForeignKey: false, inAnyConstraint: false, comment: 'ИНН (Russian tax ID)' });
    const box = new EntityBox(e, o, o.fonts);
    const latinOnly = o.fonts.widthInt('inn - (Russian tax ID)', o.fonts.regular);
    expect(box.attrs.rows[0].labelW).toBeGreaterThan(latinOnly + 10);
    const canvas = new SvgCanvas(new Icons(), o.fonts, 0, 0);
    box.paint(canvas);
    const svg = canvas.toSvg(box.width, box.height, '#ffffff', false);
    expect(svg).toContain('>inn - ИНН (Russian tax ID)<'); // one run: Inter covers Cyrillic
    expect(svg).toMatch(/<tspan font-family="[^"]+">単位/); // CJK: an installed font, or generic sans-serif
    expect(o.fonts.width('単位', o.fonts.regular)).toBeGreaterThanOrEqual(20); // full-width glyphs, even unmeasured
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
