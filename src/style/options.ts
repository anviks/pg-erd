import { Look } from './look.js';
import { Theme } from './theme.js';
import type { FontSet } from '../text/fonts.js';

/** Connection notation; DBeaver's default is IDEF1X. */
export type Notation = 'idef1x' | 'crowsfoot';

/** Length of the straight run at each end of a connection (ERDNotation.getIndentation). */
export function indentation(n: Notation): number {
  return n === 'idef1x' ? 20 : 30;
}
/** Vertical distance between line ends that share a table side. */
export function fanStep(n: Notation): number {
  return n === 'idef1x' ? 12 : 24;
}
export function parseNotation(s: string): Notation {
  switch (s.trim().toLowerCase().replace(/[_-]/g, '')) {
    case 'idef1x': return 'idef1x';
    case 'crowsfoot':
    case 'crow': return 'crowsfoot';
    default: throw new Error(`unknown notation: ${s}`);
  }
}

/** DBeaver's ERDViewStyle. */
export type ViewStyle = 'icons' | 'types' | 'nullability' | 'comments' | 'fqn' | 'alpha';
export const VIEW_STYLES: ViewStyle[] = ['icons', 'types', 'nullability', 'comments', 'fqn', 'alpha'];
export function parseViewStyle(s: string): ViewStyle {
  const k = s.trim().toLowerCase();
  const map: Record<string, ViewStyle> = {
    icons: 'icons', types: 'types', nullability: 'nullability', comments: 'comments',
    fqn: 'fqn', 'entity-fqn': 'fqn', alpha: 'alpha', alphabetical: 'alpha',
  };
  const v = map[k];
  if (!v) throw new Error(`unknown view style: ${s} (expected one of ${VIEW_STYLES.join(', ')})`);
  return v;
}

/** DBeaver's ERDAttributeVisibility. */
export type AttributeVisibility = 'all' | 'keys' | 'primary' | 'none';
export function parseVisibility(s: string): AttributeVisibility {
  const k = s.trim().toLowerCase();
  if (k === 'all' || k === 'keys' || k === 'primary' || k === 'none') return k;
  throw new Error(`unknown attribute visibility: ${s}`);
}

/** Everything that influences how a diagram is drawn. Defaults mirror DBeaver's preferences. */
export class RenderOptions {
  look: Look = Look.dbeaver();
  notation: Notation = 'idef1x';
  styles: Set<ViewStyle> = new Set(['icons']);
  visibility: AttributeVisibility = 'all';
  theme: Theme = Theme.light();
  fonts!: FontSet;
  /** Scale factor for the PNG (may be fractional). */
  scale = 1;
  /** Margin around the diagram content in the exported image (ERDExportRasterImage: 10). */
  margin = 10;
  embedSvgFont = false;

  has(s: ViewStyle): boolean {
    return this.styles.has(s);
  }
}
