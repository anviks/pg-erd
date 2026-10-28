import { existsSync, readdirSync, statSync } from 'node:fs';
import { homedir } from 'node:os';
import { basename, extname, join } from 'node:path';
import opentype from 'opentype.js';
import { resourcePath } from '../resources.js';

/** A font at a given pixel size and weight; the unit of all text measurement. */
export interface FontStyle {
  sizePx: number;
  bold: boolean;
}

interface Face {
  family: string;
  path: string;
  font: opentype.Font;
  bold: boolean;
}

export const BUNDLED_FAMILY = 'Inter';
export const DEFAULT_POINT_SIZE = 9;
export const NOTATION_POINT_SIZE = 8;

/** Points at 96 dpi to CSS pixels. */
export function pxFromPt(pt: number): number {
  return Math.round((pt * 96) / 72);
}

const FALLBACK_FAMILIES = ['DejaVu Sans', 'Noto Sans', 'Liberation Sans', 'Arial', 'Segoe UI', 'Helvetica', 'Cantarell', 'Noto Sans Arabic', 'Noto Sans Hebrew', 'Noto Sans Thai', 'Noto Sans Devanagari'];
const MONO_FAMILIES = ['DejaVu Sans Mono', 'Liberation Mono', 'Noto Sans Mono', 'Consolas', 'Menlo', 'Courier New'];

function fontDirs(): string[] {
  const home = homedir();
  const dirs = [
    '/usr/share/fonts', '/usr/local/share/fonts', join(home, '.fonts'), join(home, '.local/share/fonts'),
    '/Library/Fonts', '/System/Library/Fonts', join(home, 'Library/Fonts'),
    'C:\\Windows\\Fonts', join(home, 'AppData/Local/Microsoft/Windows/Fonts'),
  ];
  return dirs.filter((d) => existsSync(d));
}

let fontFileIndex: string[] | null = null;

/** Every .ttf/.otf under the usual font directories (scanned once). */
function allFontFiles(): string[] {
  if (fontFileIndex) return fontFileIndex;
  const out: string[] = [];
  const walk = (dir: string, depth: number) => {
    let names: string[];
    try {
      names = readdirSync(dir);
    } catch {
      return;
    }
    for (const n of names) {
      const p = join(dir, n);
      let st;
      try {
        st = statSync(p);
      } catch {
        continue;
      }
      if (st.isDirectory()) {
        if (depth < 4) walk(p, depth + 1);
      } else if (/\.(ttf|otf)$/i.test(n)) {
        out.push(p);
      }
    }
  };
  for (const d of fontDirs()) walk(d, 0);
  fontFileIndex = out;
  return out;
}

const norm = (s: string) => s.toLowerCase().replace(/[\s_-]/g, '');

type NameTable = Record<string, Record<string, string> | undefined>;

function nameEntry(font: opentype.Font, key: string): string {
  const n = font.names as unknown as NameTable;
  const entry = n[key];
  if (!entry) return '';
  return entry.en ?? entry[Object.keys(entry)[0] ?? ''] ?? '';
}

function subfamily(font: opentype.Font): string {
  return nameEntry(font, 'fontSubfamily').toLowerCase();
}

function familyName(font: opentype.Font): string {
  return nameEntry(font, 'fontFamily');
}

/** Finds the regular and bold files of an installed family by file name and the font's own name table. */
function findInstalled(family: string): { regular: Face; bold: Face | null } | null {
  const target = norm(family);
  const candidates = allFontFiles().filter((p) => norm(basename(p, extname(p))).startsWith(target));
  let regular: Face | null = null;
  let bold: Face | null = null;
  // static fonts before variable ones, shorter (plainer) names first
  candidates.sort((a, b) => Number(a.includes('[')) - Number(b.includes('[')) || a.length - b.length);
  for (const p of candidates) {
    let font: opentype.Font;
    try {
      font = opentype.loadSync(p);
    } catch {
      continue;
    }
    if (norm(familyName(font)) !== target) continue;
    const sub = subfamily(font);
    const face: Face = { family, path: p, font, bold: sub.includes('bold') };
    if ((sub === 'regular' || sub === 'book' || sub === 'normal' || sub === '') && !regular) regular = face;
    else if (sub === 'bold' && !bold) bold = face;
    if (regular && bold) break;
  }
  if (!regular) return null;
  return { regular, bold };
}

/**
 * The fonts of a diagram: a primary family (regular + bold), the notation label size, and lazily
 * loaded fallback faces for characters the primary font lacks (e.g. CJK with the bundled Inter).
 * Measurement uses the same font files that the PNG rasteriser is given, so sizes agree.
 */
export class FontSet {
  readonly regular: FontStyle;
  readonly bold: FontStyle;
  readonly notation: FontStyle;
  private fallbacks: Face[] | null = null;
  private readonly cache = new Map<string, number>();

  private constructor(
    readonly family: string,
    readonly bundled: boolean,
    private readonly regularFace: Face,
    private readonly boldFace: Face,
    pointSize: number,
    readonly fallbackCandidates: string[],
  ) {
    const px = pxFromPt(pointSize);
    this.regular = { sizePx: px, bold: false };
    this.bold = { sizePx: px, bold: true };
    this.notation = { sizePx: pxFromPt(NOTATION_POINT_SIZE), bold: false };
  }

  /**
   * @param family null/`Selawik` = bundled font; `monospace` = first installed monospace family;
   *               a `.ttf`/`.otf` path (optionally `boldFile` too); or an installed family name
   */
  static load(family: string | null | undefined, pointSize = DEFAULT_POINT_SIZE, boldFile?: string): FontSet {
    if (!family || family.trim() === '' || family.toLowerCase() === BUNDLED_FAMILY.toLowerCase()) {
      return FontSet.bundled(pointSize);
    }
    if (/\.(ttf|otf)$/i.test(family)) {
      const r = opentype.loadSync(family);
      const b = boldFile ? opentype.loadSync(boldFile) : r;
      const name = familyName(r) || basename(family, extname(family));
      return new FontSet(
        name, false,
        { family: name, path: family, font: r, bold: false },
        { family: name, path: boldFile ?? family, font: b, bold: !!boldFile },
        pointSize, FALLBACK_FAMILIES,
      );
    }
    const mono = family.toLowerCase() === 'monospace';
    const families = mono ? MONO_FAMILIES : [family];
    for (const f of families) {
      const found = findInstalled(f);
      if (found) {
        return new FontSet(f, false, found.regular, found.bold ?? found.regular, pointSize, FALLBACK_FAMILIES);
      }
    }
    if (mono) {
      console.error(`warning: no monospace font installed (${MONO_FAMILIES.join(', ')}); using the bundled ${BUNDLED_FAMILY}`);
      return FontSet.bundled(pointSize);
    }
    throw new Error(`font family not installed: ${family}`);
  }

  private static bundled(pointSize: number): FontSet {
    const regular = resourcePath('fonts/Inter-Regular.ttf');
    const bold = resourcePath('fonts/Inter-Bold.ttf');
    return new FontSet(
      BUNDLED_FAMILY, true,
      { family: BUNDLED_FAMILY, path: regular, font: opentype.loadSync(regular), bold: false },
      { family: BUNDLED_FAMILY, path: bold, font: opentype.loadSync(bold), bold: true },
      pointSize, FALLBACK_FAMILIES,
    );
  }

  private face(style: FontStyle): Face {
    return style.bold ? this.boldFace : this.regularFace;
  }

  private loadFallbacks(): Face[] {
    if (!this.fallbacks) {
      this.fallbacks = [];
      for (const f of this.fallbackCandidates) {
        if (norm(f) === norm(this.family)) continue;
        const found = findInstalled(f);
        if (found) {
          this.fallbacks.push(found.regular);
          if (found.bold) this.fallbacks.push(found.bold);
        }
      }
    }
    return this.fallbacks;
  }

  private hasGlyph(face: Face, cp: number): boolean {
    return face.font.charToGlyphIndex(String.fromCodePoint(cp)) > 0;
  }

  /**
   * Splits text into runs by the face that can draw them (whitespace sticks to the current run).
   * A run with `face: null` has no loaded font for its characters; it is measured at one em per
   * character and left to the rasteriser's own system-font fallback.
   */
  runs(text: string, style: FontStyle): { text: string; face: Face | null }[] {
    const primary = this.face(style);
    const out: { text: string; face: Face | null }[] = [];
    let cur: Face | null | undefined;
    let buf = '';
    for (const ch of text) {
      const cp = ch.codePointAt(0)!;
      let f: Face | null;
      if (/\s/.test(ch) && cur !== undefined) f = cur;
      else if (this.hasGlyph(primary, cp)) f = primary;
      else {
        f = this.loadFallbacks().find((fb) => fb.bold === style.bold && this.hasGlyph(fb, cp))
          ?? this.loadFallbacks().find((fb) => this.hasGlyph(fb, cp))
          ?? null;
      }
      if (cur !== undefined && f !== cur) {
        out.push({ text: buf, face: cur });
        buf = '';
      }
      cur = f;
      buf += ch;
    }
    if (cur !== undefined) out.push({ text: buf, face: cur });
    return out;
  }

  /** Advance width of `text` in CSS pixels (fractional). */
  width(text: string, style: FontStyle): number {
    if (!text) return 0;
    const key = `${style.sizePx}|${style.bold ? 'b' : 'r'}|${text}`;
    const hit = this.cache.get(key);
    if (hit !== undefined) return hit;
    let w = 0;
    for (const r of this.runs(text, style)) {
      if (r.face) w += r.face.font.getAdvanceWidth(r.text, style.sizePx, { kerning: true });
      else w += [...r.text].length * style.sizePx;
    }
    w = Math.round(w * 100) / 100;
    this.cache.set(key, w);
    return w;
  }

  /** Integer width for layout (never smaller than the drawn text). */
  widthInt(text: string, style: FontStyle): number {
    return Math.ceil(this.width(text, style) - 1e-6);
  }

  private metric(face: Face, v: number, sizePx: number): number {
    return Math.round((v * sizePx) / face.font.unitsPerEm);
  }

  ascent(style: FontStyle): number {
    const f = this.face(style);
    return this.metric(f, f.font.ascender, style.sizePx);
  }

  descent(style: FontStyle): number {
    const f = this.face(style);
    return this.metric(f, -f.font.descender, style.sizePx);
  }

  /** Line height: ascent + descent + line gap, like SWT/Draw2D text extents. */
  height(style: FontStyle): number {
    const f = this.face(style);
    const hhea = (f.font.tables as { hhea?: { lineGap?: number } }).hhea;
    const gap = hhea?.lineGap ? this.metric(f, hhea.lineGap, style.sizePx) : 0;
    return this.ascent(style) + this.descent(style) + gap;
  }

  /** Font files to hand to the PNG rasteriser: the primary faces plus any fallback faces used so far. */
  fontFiles(): string[] {
    const files = [this.regularFace.path, this.boldFace.path, ...(this.fallbacks ?? []).map((f) => f.path)];
    return [...new Set(files)];
  }

  /** CSS font-family list for the SVG. */
  cssFamily(): string {
    const q = (s: string) => (/^[A-Za-z0-9-]+$/.test(s) ? s : `'${s.replace(/'/g, "\\'")}'`);
    const names = [this.family, ...(this.fallbacks ?? []).map((f) => f.family)];
    if (this.bundled) names.push('Segoe UI', 'Roboto', 'Helvetica Neue');
    const generic = this.family.toLowerCase().includes('mono') || this.family.toLowerCase() === 'consolas' ? 'monospace' : 'sans-serif';
    return [...new Set(names)].map(q).join(', ') + ', ' + generic;
  }

  /** Paths of the primary regular and bold files (for embedding in the SVG). */
  primaryFiles(): { regular: string; bold: string } {
    return { regular: this.regularFace.path, bold: this.boldFace.path };
  }
}
