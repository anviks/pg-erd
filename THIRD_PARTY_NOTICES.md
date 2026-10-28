# Third-party notices

## DBeaver icons — Apache-2.0
The SVG/PNG files under `resources/icons` come from DBeaver
(https://github.com/dbeaver/dbeaver, `plugins/org.jkiss.dbeaver.model/icons`,
commit `a79062e59e2f3c3b128acfa6e4e1b5ef0dd7f262`). License: Apache License 2.0, see `licenses/DBeaver-LICENSE.md`.

The box model, colours and notation rules in `src/figure`, `src/style` and `src/notation` were derived
from reading DBeaver's `org.jkiss.dbeaver.ui.editors.erd` plugin; no DBeaver code is included.

## Selawik font — SIL OFL 1.1
`resources/fonts/selawk.ttf` and `selawkb.ttf` are Microsoft's Selawik, an open font metric-compatible
with Segoe UI (DBeaver's default UI font on Windows). License: SIL Open Font License 1.1, see
`resources/fonts/LICENSE-Selawik.txt`.

## Runtime dependencies (installed from npm, not bundled)
- elkjs (Eclipse Layout Kernel transpiled to JavaScript): Eclipse Public License 2.0, `licenses/EPL-2.0.txt`.
- @resvg/resvg-js: Mozilla Public License 2.0.
- opentype.js: MIT.
- pg, pg-connection-string, commander: MIT.
