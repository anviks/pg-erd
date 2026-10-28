import type { Entity } from '../model/index.js';
import { Theme, blend, type Color } from './theme.js';

export type LookName = 'clean' | 'dbeaver' | 'paper' | 'pastel';
export const LOOK_NAMES: LookName[] = ['clean', 'dbeaver', 'paper', 'pastel'];

/**
 * A visual style: box geometry (frame, corners, padding, separators) and how relation lines are
 * coloured. Each look comes with a light and a dark {@link Theme}.
 */
export class Look {
  /** Width of the frame around a table. */
  borderWidth = 1;
  /** Corner radius of the frame; 0 = square. */
  cornerRadius = 6;
  titleBold = true;
  titleCentered = false;
  /** Show the table/view icon next to the title. */
  titleIcon = true;
  titlePadX = 8;
  titlePadY = 5;
  /** Width of the lines under the header and between key rows and other rows. */
  separator = 1;
  /** Draw separators in the frame colour instead of the theme's separator colour. */
  separatorInBorderColor = false;
  /** Horizontal padding of the rows area. */
  padX = 8;
  /** Vertical padding of each rows section. */
  padY = 3;
  /** Vertical padding of each row (added above and below the icon/text). */
  rowPadY = 3;
  /** Colour relation lines after the parent (referenced) table's header instead of the theme's line colour. */
  linesByParent = false;
  /** Font family when none is given on the command line; null = the bundled font. */
  defaultFontFamily: string | null = null;

  private constructor(public readonly name: LookName) {}

  static parse(s: string): Look {
    switch (s.trim().toLowerCase()) {
      case 'clean': return Look.clean();
      case 'dbeaver': return Look.dbeaver();
      case 'paper': return Look.paper();
      case 'pastel': return Look.pastel();
      default: throw new Error(`unknown look: ${s} (expected one of ${LOOK_NAMES.join(', ')})`);
    }
  }

  /** Rounded cards with a solid header bar and a thin grey frame (the default). */
  static clean(): Look {
    return new Look('clean');
  }

  /** The look of DBeaver's ERD editor: pale yellow headers, 2 px blue frames and separators, centred titles. */
  static dbeaver(): Look {
    const l = new Look('dbeaver');
    l.borderWidth = 2;
    l.cornerRadius = 0;
    l.titleBold = false;
    l.titleCentered = true;
    l.titlePadX = 3;
    l.titlePadY = 3;
    l.separator = 2;
    l.separatorInBorderColor = true;
    l.padY = 3;
    l.rowPadY = 2;
    return l;
  }

  /** Monochrome, square, monospaced: prints well and works in black and white. */
  static paper(): Look {
    const l = new Look('paper');
    l.cornerRadius = 0;
    l.titleCentered = true;
    l.titleIcon = false;
    l.titlePadY = 4;
    l.padY = 2;
    l.rowPadY = 2;
    l.defaultFontFamily = 'monospace';
    return l;
  }

  /** Each table gets a pastel header colour by name; lines take the colour of the table they point to. */
  static pastel(): Look {
    const l = new Look('pastel');
    l.cornerRadius = 8;
    l.linesByParent = true;
    return l;
  }

  /** The colours this look is designed with, in a light or dark variant. */
  theme(dark: boolean): Theme {
    switch (this.name) {
      case 'dbeaver': return dark ? Theme.dbeaverDark() : Theme.dbeaverLight();
      case 'paper': return dark ? Theme.paperDark() : Theme.paperLight();
      case 'pastel': return dark ? Theme.pastelDark() : Theme.pastelLight();
      default: return dark ? Theme.cleanDark() : Theme.cleanLight();
    }
  }

  /** Header colour of an entity. */
  headerColor(e: Entity, theme: Theme): Color {
    if (theme.tablePalette && theme.tablePalette.length > 0) {
      return theme.tablePalette[hash(e.name) % theme.tablePalette.length];
    }
    return e.primary ? theme.headerAccent : theme.header;
  }

  /** Colour of a relation line whose parent (referenced) table has the given header colour. */
  lineColor(parentHeader: Color, theme: Theme): Color {
    if (!this.linesByParent) return theme.lines;
    return theme.dark ? blend(parentHeader, '#ffffff', 70) : blend(parentHeader, '#000000', 55);
  }
}

function hash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}
