/** Colours as `#rrggbb` strings. */
export type Color = string;

export function rgb(r: number, g: number, b: number): Color {
  const h = (v: number) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0');
  return `#${h(r)}${h(g)}${h(b)}`;
}

export function parseColor(s: string): Color {
  s = s.trim();
  if (/^#[0-9a-fA-F]{6}$/.test(s)) return s.toLowerCase();
  if (/^#[0-9a-fA-F]{3}$/.test(s)) {
    return `#${s[1]}${s[1]}${s[2]}${s[2]}${s[3]}${s[3]}`.toLowerCase();
  }
  const p = s.split(',');
  if (p.length !== 3) throw new Error(`colour must be r,g,b or #rrggbb: ${s}`);
  return rgb(Number(p[0]), Number(p[1]), Number(p[2]));
}

export function channels(c: Color): [number, number, number] {
  return [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
}

/** UIUtils.blend(c1, c2, ratio): ratio percent of c1 mixed with (100-ratio) percent of c2. */
export function blend(c1: Color, c2: Color, ratio: number): Color {
  const a = channels(c1);
  const b = channels(c2);
  return rgb(
    Math.trunc((ratio * a[0] + (100 - ratio) * b[0]) / 100),
    Math.trunc((ratio * a[1] + (100 - ratio) * b[1]) / 100),
    Math.trunc((ratio * a[2] + (100 - ratio) * b[2]) / 100),
  );
}

const WHITE = '#ffffff';
const BLACK = '#000000';

/**
 * Colour theme. The DBeaver variants mirror org.jkiss.dbeaver.ui.editors.erd/plugin.xml and
 * css/e4-dark-erd-editor.css, with SWT system colours at their Windows defaults.
 */
export class Theme {
  dark = false;
  diagramBackground: Color = WHITE;
  entityRegularBackground: Color = rgb(255, 255, 225);
  entityPrimaryBackground: Color = rgb(255, 128, 64);
  entityAssociationBackground: Color = WHITE;
  entityNameForeground: Color = BLACK;
  linesForeground: Color = rgb(0, 143, 207);
  attrBackground: Color = WHITE;
  attrForeground: Color = BLACK;
  noteBackground: Color = rgb(255, 255, 225);
  noteForeground: Color = BLACK;
  /** Header colours for the 2nd..8th schema in a multi-schema diagram. */
  headerColors: Color[] = [
    rgb(204, 192, 184), rgb(228, 255, 181), rgb(255, 255, 255), rgb(219, 229, 241),
    rgb(248, 214, 205), rgb(251, 216, 166), rgb(225, 221, 252),
  ];
  /** Frame colour; null = the line colour (DBeaver). */
  borderColor: Color | null = null;
  /** Colour of the type / NOT NULL column; null = the attribute colour. */
  typeForeground: Color | null = null;
  /** Colour of separator lines inside a table; null = the frame colour. */
  separatorColor: Color | null = null;
  /** Title colour; null = black or white, whichever contrasts with the header. */
  headerForeground: Color | null = null;
  /** When set, a table's header colour is picked from this palette by its name (pastel look). */
  tablePalette: Color[] | null = null;

  border(): Color {
    return this.borderColor ?? this.linesForeground;
  }
  typeFg(): Color {
    return this.typeForeground ?? this.attrForeground;
  }
  separator(): Color {
    return this.separatorColor ?? this.border();
  }
  headerFg(header: Color): Color {
    return this.headerForeground ?? this.contrast(header);
  }

  static light(): Theme {
    return new Theme();
  }

  static dark(): Theme {
    const t = new Theme();
    t.dark = true;
    t.diagramBackground = '#2f2f2f';
    t.entityRegularBackground = '#202020';
    t.entityPrimaryBackground = '#804020';
    t.entityAssociationBackground = '#404040';
    t.entityNameForeground = '#c4c4c4';
    t.linesForeground = '#0064ae';
    t.attrBackground = '#404040';
    t.attrForeground = '#c4c4c4';
    t.noteBackground = '#404040';
    t.noteForeground = '#c4c4c4';
    return t;
  }

  private static base(
    dark: boolean, background: Color, header: Color, headerFg: Color, body: Color, text: Color,
    muted: Color, border: Color, lines: Color,
  ): Theme {
    const t = dark ? Theme.dark() : Theme.light();
    t.diagramBackground = background;
    t.entityRegularBackground = header;
    t.entityPrimaryBackground = dark ? '#9a3412' : '#f97316';
    t.entityAssociationBackground = body;
    t.entityNameForeground = headerFg;
    t.headerForeground = headerFg;
    t.linesForeground = lines;
    t.borderColor = border;
    t.separatorColor = border;
    t.attrBackground = body;
    t.attrForeground = text;
    t.typeForeground = muted;
    t.noteBackground = body;
    t.noteForeground = text;
    t.headerColors = Array(7).fill(header);
    return t;
  }

  static cleanLight(): Theme {
    return Theme.base(false, WHITE, '#475569', WHITE, WHITE, '#1e293b', '#64748b', '#cbd5e1', '#64748b');
  }
  static cleanDark(): Theme {
    return Theme.base(true, '#0f172a', '#334155', '#f1f5f9', '#1e293b', '#e2e8f0', '#94a3b8', '#475569', '#94a3b8');
  }
  static paperLight(): Theme {
    return Theme.base(false, WHITE, WHITE, BLACK, WHITE, BLACK, '#555555', BLACK, BLACK);
  }
  static paperDark(): Theme {
    const ink = '#e8e8e8';
    const paper = '#1c1c1c';
    return Theme.base(true, paper, paper, ink, paper, ink, '#a0a0a0', ink, ink);
  }

  static readonly PASTELS: Color[] = ['#ffadad', '#ffd6a5', '#fdffb6', '#caffbf', '#9bf6ff', '#a0c4ff', '#bdb2ff', '#ffc6ff'];

  static pastelLight(): Theme {
    const t = Theme.base(false, WHITE, Theme.PASTELS[5], '#1f2937', WHITE, '#1f2937', '#6b7280', '#9ca3af', '#6b7280');
    t.tablePalette = [...Theme.PASTELS];
    return t;
  }
  static pastelDark(): Theme {
    const muted = Theme.PASTELS.map((c) => blend(c, '#1f2937', 45));
    const t = Theme.base(true, '#111827', muted[5], '#f9fafb', '#1f2937', '#e5e7eb', '#9ca3af', '#4b5563', '#9ca3af');
    t.tablePalette = muted;
    return t;
  }

  /** Colour keys accepted by `--color key=value`. */
  static readonly KEYS = [
    'background', 'entity-background', 'entity-primary-background', 'entity-association-background',
    'entity-name-foreground', 'header-foreground', 'lines', 'border', 'separator', 'attr-background',
    'attr-foreground', 'type-foreground', 'note-background', 'note-foreground', 'header-1..7',
  ];

  set(key: string, c: Color): void {
    switch (key) {
      case 'background': this.diagramBackground = c; break;
      case 'entity-background': this.entityRegularBackground = c; break;
      case 'entity-primary-background': this.entityPrimaryBackground = c; break;
      case 'entity-association-background': this.entityAssociationBackground = c; break;
      case 'entity-name-foreground': this.entityNameForeground = c; break;
      case 'header-foreground': this.headerForeground = c; break;
      case 'lines': this.linesForeground = c; break;
      case 'border': this.borderColor = c; break;
      case 'separator': this.separatorColor = c; break;
      case 'attr-background': this.attrBackground = c; break;
      case 'attr-foreground': this.attrForeground = c; break;
      case 'type-foreground': this.typeForeground = c; break;
      case 'note-background': this.noteBackground = c; break;
      case 'note-foreground': this.noteForeground = c; break;
      default: {
        const m = /^header-(\d+)$/.exec(key);
        if (!m) throw new Error(`unknown colour key: ${key}`);
        const i = Number(m[1]) - 1;
        if (i < 0 || i >= this.headerColors.length) throw new Error(`unknown colour key: ${key}`);
        this.headerColors[i] = c;
      }
    }
  }

  headerColor(containerIndex: number): Color {
    if (containerIndex <= 0) return this.entityRegularBackground;
    return this.headerColors[(containerIndex - 1) % this.headerColors.length];
  }

  /** UIStyles.getContrastColor: black on light backgrounds, white on dark ones. */
  contrast(c: Color): Color {
    const [r, g, b] = channels(c);
    const luminance = 1 - (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    if (luminance > 0.5) return this.dark ? '#c4c4c4' : WHITE;
    return BLACK;
  }

  /** Colour of IDEF1X circles and notation labels (AssociationPart: blend(attrFg, contrast(attrFg), 60)). */
  decorationColor(): Color {
    return blend(this.attrForeground, this.contrast(this.attrForeground), 60);
  }
}
