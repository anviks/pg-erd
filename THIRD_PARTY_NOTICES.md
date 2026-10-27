# Third-party notices

## Eclipse Draw2D (GEF Classic) — EPL-2.0
The packages `org.eclipse.draw2d.geometry` and `org.eclipse.draw2d.graph` under
`src/main/java/org/eclipse/draw2d` are copied from GEF Classic
(https://github.com/eclipse-gef/gef-classic, commit `911f03d2d268717f2847c0771907f62b6f6f0a23`),
with these local modifications:
- removed `@NoReference` annotations (PDE API tools dependency),
- removed the SWT-typed constructors/accessors in `Point`, `Rectangle` and `Dimension`,
- added a constants-only stub of `org.eclipse.draw2d.PositionConstants`.

License: Eclipse Public License 2.0, see `licenses/EPL-2.0.txt`.
They provide the layered graph layout (`DirectedGraphLayout`) and the
`ShortestPathRouter` that DBeaver's ERD editor uses.

## DBeaver icons — Apache-2.0
The SVG/PNG files under `src/main/resources/icons` come from DBeaver
(https://github.com/dbeaver/dbeaver, `plugins/org.jkiss.dbeaver.model/icons`,
commit `a79062e59e2f3c3b128acfa6e4e1b5ef0dd7f262`). License: Apache License 2.0, see `licenses/DBeaver-LICENSE.md`.

The rendering rules (box layout, colors, notation, routing) were re-implemented
from reading DBeaver's `org.jkiss.dbeaver.ui.editors.erd` plugin; no DBeaver
code is included.

## Selawik font — SIL OFL 1.1
`src/main/resources/fonts/selawk.ttf` and `selawkb.ttf` are Microsoft's Selawik
(https://github.com/microsoft/Selawik, release 1.01), a font metric-compatible
with Segoe UI (DBeaver's default UI font on Windows). License: SIL Open Font
License 1.1, see `src/main/resources/fonts/LICENSE-Selawik.txt`.

## Eclipse Layout Kernel — EPL-2.0
The default layout uses ELK (`org.eclipse.elk.core`, `org.eclipse.elk.alg.layered`, version 0.12.0) and
its runtime dependencies (Eclipse EMF, Xtext xbase.lib, Google Guava), bundled in the jar. ELK, EMF and
Xtext are licensed under the Eclipse Public License 2.0 (`licenses/EPL-2.0.txt`); Guava under the
Apache License 2.0.
