import type { Entity } from '../model/index.js';
import { Theme, blend, type Color } from './theme.js';

export type LookName = 'dbeaver' | 'clean' | 'paper' | 'pastel';
export const LOOK_NAMES: LookName[] = ['dbeaver', 'clean', 'paper', 'pastel'];

/**
 * A visual style for the whole diagram: box geometry (frame, corners, padding, separators), which
 * colours go where, and how relation lines are coloured. Each look has a light and a dark theme.
 */
export class Look {
  /** Width of the frame around a table. */
  borderWidth = 2;
  /** Corner radius of the frame; 0 = square. */
  cornerRadius = 0;
  titleBold = false;
  titleCentered = true;
  /** Show the table/view icon next to the title. */
  titleIcon = true;
  titleMarginH = 3;
  titleMarginV = 3;
  /** Width of the line drawn across the top of each attribute section (DBeaver: 2, in the border colour). */
  sectionTopLine = 2;
  /** Width of a separator line between the header and the rows and between key rows and other rows. */
  separator = 0;
  listMarginW = 5;
  listMarginH = 3;
  /** DBeaver's ColumnFigureBorder insets (0,-2,-2,-2), which make a section narrower and shorter than its content. */
  listInsetSide = -2;
  listInsetBottom = -2;
  itemMarginW = 5;
  itemMarginH = 2;
  /** Space between the type column and the frame. */
  rightPad = 0;
  /** Draw the type column at the top of its row (DBeaver) rather than vertically centred. */
  rightTextTop = true;
  /** Use the line colour for end decorations and labels instead of DBeaver's blended grey. */
  decorationsUseLineColor = false;
  /** Colour relation lines after the parent (referenced) table's header instead of the theme's line colour. */
  linesByParent = false;
  /** Font family to use when none is given on the command line; null = the bundled font. */
  defaultFontFamily: string | null = null;

  private constructor(public readonly name: LookName) {}

  static parse(s: string): Look {
    switch (s.trim().toLowerCase()) {
      case 'dbeaver': return Look.dbeaver();
      case 'clean': return Look.clean();
      case 'paper': return Look.paper();
      case 'pastel': return Look.pastel();
      default: throw new Error(`unknown look: ${s} (expected one of ${LOOK_NAMES.join(', ')})`);
    }
  }

  /** DBeaver's ERD editor look (the default). */
  static dbeaver(): Look {
    return new Look('dbeaver');
  }

  /** Rounded cards with a solid header bar and thin grey frame. */
  static clean(): Look {
    const l = new Look('clean');
    l.borderWidth = 1;
    l.cornerRadius = 6;
    l.titleBold = true;
    l.titleCentered = false;
    l.titleMarginH = 8;
    l.titleMarginV = 5;
    l.sectionTopLine = 0;
    l.separator = 1;
    l.listMarginW = 3;
    l.listMarginH = 3;
    l.listInsetSide = 0;
    l.listInsetBottom = 0;
    l.itemMarginW = 5;
    l.itemMarginH = 3;
    l.rightPad = 5;
    l.rightTextTop = false;
    l.decorationsUseLineColor = true;
    return l;
  }

  /** Monochrome, square, monospaced: prints well and works in black and white. */
  static paper(): Look {
    const l = new Look('paper');
    l.borderWidth = 1;
    l.cornerRadius = 0;
    l.titleBold = true;
    l.titleCentered = true;
    l.titleIcon = false;
    l.titleMarginH = 8;
    l.titleMarginV = 4;
    l.sectionTopLine = 0;
    l.separator = 1;
    l.listMarginW = 3;
    l.listMarginH = 2;
    l.listInsetSide = 0;
    l.listInsetBottom = 0;
    l.itemMarginW = 5;
    l.itemMarginH = 2;
    l.rightPad = 5;
    l.rightTextTop = false;
    l.decorationsUseLineColor = true;
    l.defaultFontFamily = 'monospace';
    return l;
  }

  /** Each table gets a pastel header colour by name; lines take the colour of the table they point to. */
  static pastel(): Look {
    const l = Object.assign(new Look('pastel'), Look.clean(), { name: 'pastel' }) as Look;
    l.cornerRadius = 8;
    l.linesByParent = true;
    return l;
  }

  /** The colours this look is designed with, in a light or dark variant. */
  theme(dark: boolean): Theme {
    switch (this.name) {
      case 'clean': return dark ? Theme.cleanDark() : Theme.cleanLight();
      case 'paper': return dark ? Theme.paperDark() : Theme.paperLight();
      case 'pastel': return dark ? Theme.pastelDark() : Theme.pastelLight();
      default: return dark ? Theme.dark() : Theme.light();
    }
  }

  /** Header (and frame fill) colour of an entity. */
  headerColor(e: Entity, theme: Theme): Color {
    if (theme.tablePalette && theme.tablePalette.length > 0) {
      return theme.tablePalette[hash(e.name) % theme.tablePalette.length];
    }
    if (e.primary) return theme.entityPrimaryBackground;
    return theme.headerColor(e.containerIndex);
  }

  /** Colour of a relation line whose parent (referenced) table has the given header colour. */
  lineColor(parentHeader: Color | null, theme: Theme): Color {
    if (!this.linesByParent || !parentHeader) return theme.linesForeground;
    return theme.dark ? blend(parentHeader, '#ffffff', 70) : blend(parentHeader, '#000000', 55);
  }

  /** Colour of end decorations (IDEF1X dot, crow's-foot labels) for a line of the given colour. */
  decorationColor(lineColor: Color, theme: Theme): Color {
    return this.decorationsUseLineColor ? lineColor : theme.decorationColor();
  }
}

/** Java's String.hashCode, so a table keeps the same pastel colour as in the Java version. */
function hash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}
