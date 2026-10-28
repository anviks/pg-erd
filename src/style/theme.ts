/** Colours as `#rrggbb` strings. */
export type Color = string;

export function rgb(r: number, g: number, b: number): Color {
  const h = (v: number) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0');
  return `#${h(r)}${h(g)}${h(b)}`;
}

export function parseColor(s: string): Color {
  s = s.trim();
  if (/^#[0-9a-fA-F]{6}$/.test(s)) return s.toLowerCase();
  if (/^#[0-9a-fA-F]{3}$/.test(s)) return `#${s[1]}${s[1]}${s[2]}${s[2]}${s[3]}${s[3]}`.toLowerCase();
  const p = s.split(',');
  if (p.length !== 3) throw new Error(`colour must be r,g,b or #rrggbb: ${s}`);
  return rgb(Number(p[0]), Number(p[1]), Number(p[2]));
}

export function channels(c: Color): [number, number, number] {
  return [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
}

/** `ratio` percent of c1 mixed with the rest of c2. */
export function blend(c1: Color, c2: Color, ratio: number): Color {
  const a = channels(c1);
  const b = channels(c2);
  return rgb((ratio * a[0] + (100 - ratio) * b[0]) / 100, (ratio * a[1] + (100 - ratio) * b[1]) / 100, (ratio * a[2] + (100 - ratio) * b[2]) / 100);
}

/** Black or white, whichever reads better on the given colour. */
export function contrast(c: Color): Color {
  const [r, g, b] = channels(c);
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255 > 0.5 ? '#000000' : '#ffffff';
}

const WHITE = '#ffffff';
const BLACK = '#000000';

/** The colours of a diagram. Each look provides a light and a dark theme; `--color` overrides single keys. */
export class Theme {
  /** Colour keys accepted by `--color key=value`. */
  static readonly KEYS = ['background', 'header', 'header-accent', 'header-text', 'body', 'text', 'type-text', 'lines', 'border', 'separator'];

  constructor(
    public dark: boolean,
    /** Page background. */
    public background: Color,
    /** Table header. */
    public header: Color,
    /** Header of the root table in single-table mode. */
    public headerAccent: Color,
    /** Rows area. */
    public body: Color,
    /** Column names. */
    public text: Color,
    /** Type / NOT NULL column. */
    public typeText: Color,
    /** Relation lines. */
    public lines: Color,
    /** Table frame. */
    public border: Color,
    /** Lines inside a table. */
    public separator: Color,
    /** Title colour; null = black or white, whichever contrasts with the header. */
    public headerText: Color | null = null,
    /** When set, a table's header colour is picked from this palette by its name. */
    public tablePalette: Color[] | null = null,
  ) {}

  headerTextOn(header: Color): Color {
    return this.headerText ?? contrast(header);
  }

  set(key: string, c: Color): void {
    switch (key) {
      case 'background': this.background = c; break;
      case 'header': this.header = c; break;
      case 'header-accent': this.headerAccent = c; break;
      case 'header-text': this.headerText = c; break;
      case 'body': this.body = c; break;
      case 'text': this.text = c; break;
      case 'type-text': this.typeText = c; break;
      case 'lines': this.lines = c; break;
      case 'border': this.border = c; break;
      case 'separator': this.separator = c; break;
      default: throw new Error(`unknown colour key: ${key} (expected one of ${Theme.KEYS.join(', ')})`);
    }
  }

  // ---- clean
  static cleanLight(): Theme {
    return new Theme(false, WHITE, '#475569', '#ea580c', WHITE, '#1e293b', '#64748b', '#64748b', '#cbd5e1', '#e2e8f0', WHITE);
  }
  static cleanDark(): Theme {
    return new Theme(true, '#0f172a', '#334155', '#9a3412', '#1e293b', '#e2e8f0', '#94a3b8', '#94a3b8', '#475569', '#334155', '#f1f5f9');
  }

  // ---- dbeaver: the colours of DBeaver's ERD editor
  static dbeaverLight(): Theme {
    const blue = rgb(0, 143, 207);
    return new Theme(false, WHITE, rgb(255, 255, 225), rgb(255, 128, 64), WHITE, BLACK, BLACK, blue, blue, blue);
  }
  static dbeaverDark(): Theme {
    return new Theme(true, '#2f2f2f', '#202020', '#804020', '#404040', '#c4c4c4', '#c4c4c4', '#0064ae', '#0064ae', '#0064ae', '#c4c4c4');
  }

  // ---- paper
  static paperLight(): Theme {
    return new Theme(false, WHITE, WHITE, '#e5e5e5', WHITE, BLACK, '#555555', BLACK, BLACK, BLACK, BLACK);
  }
  static paperDark(): Theme {
    const ink = '#e8e8e8';
    const paper = '#1c1c1c';
    return new Theme(true, paper, paper, '#3a3a3a', paper, ink, '#a0a0a0', ink, ink, ink, ink);
  }

  // ---- pastel
  static readonly PASTELS: Color[] = ['#ffadad', '#ffd6a5', '#fdffb6', '#caffbf', '#9bf6ff', '#a0c4ff', '#bdb2ff', '#ffc6ff'];

  static pastelLight(): Theme {
    const t = new Theme(false, WHITE, Theme.PASTELS[5], '#fdba74', WHITE, '#1f2937', '#6b7280', '#6b7280', '#9ca3af', '#e5e7eb', '#1f2937');
    t.tablePalette = [...Theme.PASTELS];
    return t;
  }
  static pastelDark(): Theme {
    const muted = Theme.PASTELS.map((c) => blend(c, '#1f2937', 45));
    const t = new Theme(true, '#111827', muted[5], '#9a3412', '#1f2937', '#e5e7eb', '#9ca3af', '#9ca3af', '#4b5563', '#374151', '#f9fafb');
    t.tablePalette = muted;
    return t;
  }
}
