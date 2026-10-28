import { resourceBase64, resourceText } from '../resources.js';

export const ICON_SIZE = 16;
export const TABLE_ICON = 'tree/table';
export const VIEW_ICON = 'tree/view';
export const KEY_OVERLAY = 'over/key_ovr';

export interface IconShape {
  width: number;
  height: number;
  /** `<path .../>` elements as they appear in the icon file. */
  paths: string[];
}

const PATH_ELEMENT = /<path\b([^>]*?)\/?>(?:<\/path>)?/gs;
const ATTR = /([\w:-]+)\s*=\s*"([^"]*)"/g;

/**
 * Icon registry. Ids are resource paths without extension (`tree/table`, `types/number`). DBeaver's
 * icons are plain lists of `<path>` elements, which are copied into the SVG output verbatim.
 */
export class Icons {
  private readonly shapes = new Map<string, IconShape>();
  private overlay: string | null = null;

  shape(id: string): IconShape {
    let s = this.shapes.get(id);
    if (!s) {
      s = Icons.parse(resourceText(`icons/${id}.svg`));
      this.shapes.set(id, s);
    }
    return s;
  }

  static parse(svg: string): IconShape {
    const dim = (name: string) => {
      const m = new RegExp(`<svg\\b[^>]*\\b${name}="(\\d+)`).exec(svg);
      return m ? Number(m[1]) : ICON_SIZE;
    };
    const paths: string[] = [];
    for (const m of svg.matchAll(PATH_ELEMENT)) {
      const attrs: Record<string, string> = {};
      for (const a of m[1].matchAll(ATTR)) attrs[a[1]] = a[2];
      if (!attrs.d) continue;
      const fill = attrs.fill ?? (attrs.stroke ? 'none' : '#000000');
      let el = `<path d="${attrs.d}" fill="${fill}"`;
      if (attrs.stroke && attrs.stroke !== 'none') el += ` stroke="${attrs.stroke}" stroke-width="${attrs['stroke-width'] ?? '1'}"`;
      if (attrs['fill-rule']) el += ` fill-rule="${attrs['fill-rule']}"`;
      if (attrs['fill-opacity']) el += ` fill-opacity="${attrs['fill-opacity']}"`;
      paths.push(el + '/>');
    }
    if (paths.length === 0) throw new Error('SVG contains no <path> elements');
    return { width: dim('width'), height: dim('height'), paths };
  }

  keyOverlayBase64(): string {
    if (!this.overlay) this.overlay = resourceBase64(`icons/${KEY_OVERLAY}.png`);
    return this.overlay;
  }
}
