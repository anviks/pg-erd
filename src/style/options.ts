import { Look } from './look.js';
import { Theme } from './theme.js';
import type { FontSet } from '../text/fonts.js';

/** Connection notation. */
export type Notation = 'idef1x' | 'crowsfoot';

/** Length a line must stay straight at each end so its end symbol (and label) fit before the first bend. */
export function straightRun(n: Notation): number {
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

export type ViewStyle = 'icons' | 'types' | 'nullability' | 'comments' | 'fqn' | 'alpha';
export const VIEW_STYLES: ViewStyle[] = ['icons', 'types', 'nullability', 'comments', 'fqn', 'alpha'];
export function parseViewStyle(s: string): ViewStyle {
  const k = s.trim().toLowerCase();
  const map: Record<string, ViewStyle> = {
    icons: 'icons', types: 'types', nullability: 'nullability', comments: 'comments',
    fqn: 'fqn', alpha: 'alpha', alphabetical: 'alpha',
  };
  const v = map[k];
  if (!v) throw new Error(`unknown view style: ${s} (expected one of ${VIEW_STYLES.join(', ')})`);
  return v;
}

export type AttributeVisibility = 'all' | 'keys' | 'primary' | 'none';
export function parseVisibility(s: string): AttributeVisibility {
  const k = s.trim().toLowerCase();
  if (k === 'all' || k === 'keys' || k === 'primary' || k === 'none') return k;
  throw new Error(`unknown attribute visibility: ${s}`);
}

/** Everything that influences how a diagram is drawn. */
export class RenderOptions {
  look: Look = Look.clean();
  notation: Notation = 'idef1x';
  styles: Set<ViewStyle> = new Set(['icons']);
  visibility: AttributeVisibility = 'all';
  theme: Theme = Theme.cleanLight();
  fonts!: FontSet;
  /** Scale factor for the PNG (may be fractional). */
  scale = 1;
  /** Margin around the diagram content in the exported image. */
  margin = 10;
  embedSvgFont = false;

  has(s: ViewStyle): boolean {
    return this.styles.has(s);
  }
}
