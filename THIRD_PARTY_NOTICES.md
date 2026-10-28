# Third-party notices

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
Layout and routing use ELK (`org.eclipse.elk.core`, `org.eclipse.elk.alg.layered`, version 0.12.0) and
its runtime dependencies (Eclipse EMF, Xtext xbase.lib, Google Guava), bundled in the jar. ELK, EMF and
Xtext are licensed under the Eclipse Public License 2.0 (`licenses/EPL-2.0.txt`); Guava under the
Apache License 2.0.
