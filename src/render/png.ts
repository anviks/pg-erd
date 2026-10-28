import { Resvg } from '@resvg/resvg-js';
import type { RenderedDiagram } from './pipeline.js';

export interface PngOptions {
  /** Scale factor (default: the render options' scale). */
  scale?: number;
}

/** Rasterises the diagram's SVG with resvg, using the very font files the layout was measured with. */
export function toPng(diagram: RenderedDiagram, svg?: string, opts: PngOptions = {}): Buffer {
  const scale = opts.scale ?? diagram.options.scale;
  const r = new Resvg(svg ?? diagram.toSvg(), {
    fitTo: { mode: 'zoom', value: scale },
    background: diagram.options.theme.background,
    font: {
      fontFiles: diagram.options.fonts.fontFiles(),
      loadSystemFonts: true,
      defaultFontFamily: diagram.options.fonts.family,
    },
    shapeRendering: 2, // geometricPrecision
    textRendering: 1, // optimizeLegibility
  });
  return r.render().asPng();
}
