# dbeaver-erd

A headless command-line tool that connects to PostgreSQL and renders the schema as an
entity-relationship diagram in the style of DBeaver's ERD editor, as PNG and/or SVG, so a CI or
deploy job can produce the diagram without a GUI.

It is a Node.js/TypeScript program: DBeaver's drawing rules (entity boxes, colours, icons, IDEF1X
and crow's-foot notation) are re-implemented as SVG, the tables are arranged by the Eclipse Layout
Kernel ([elkjs](https://github.com/kieler/elkjs)) with every relation line attached to its foreign-key
column and routed with right angles, and the PNG is rasterised with
[resvg](https://github.com/yisibl/resvg-js). No browser, JVM or native image library is needed.

```
npx dbeaver-erd --url postgres://app@db.example.com:5432/app \
    --schema public --out schema.png --svg schema.svg
```

The password is read from `PGPASSWORD` by default (`--password-env NAME` for another variable,
`--password` to pass it directly, or put it in the URL). JDBC-style URLs
(`jdbc:postgresql://host/db`) are accepted too.

## Requirements

* Node.js 20 or newer. The only native dependency, resvg, ships prebuilt binaries for Linux
  (glibc and musl), macOS and Windows on x64 and arm64.
* PostgreSQL 10 or newer.
* The bundled font covers Latin only. Names and comments in other scripts (Cyrillic, Greek, CJK, ...)
  are measured and drawn with an installed fallback font (DejaVu Sans, Noto Sans, Liberation Sans,
  Arial or Segoe UI), so a CI image needs one of those (for example `fonts-dejavu` on Debian/Ubuntu,
  `dejavu-sans-fonts` on Fedora, `ttf-dejavu` on Alpine). The `paper` look needs an installed
  monospace font (DejaVu Sans Mono, Liberation Mono, Noto Sans Mono, Consolas or Menlo).

## Install and build

```
npm install
npm run build        # compiles TypeScript into dist/
npm test             # unit tests (no database needed), images land in test-output/
npm run test:it      # end-to-end against PostgreSQL in Docker (Testcontainers)
node bin/dbeaver-erd.js --help
```

`npm link` makes `dbeaver-erd` available on the PATH; `npm pack` produces a tarball you can
`npm install -g`.

## Options

| Option | Default | Meaning |
|---|---|---|
| `--url` | required | `postgres://user:pass@host:5432/db` or `jdbc:postgresql://host:5432/db` |
| `-U`, `--user` | URL user, `PGUSER` or the OS user | database user |
| `--password`, `--password-env VAR` | `PGPASSWORD` | password, or the environment variable holding it |
| `-s`, `--schema` | `public` | schema to diagram (DBeaver's "ER Diagram" tab on a schema) |
| `-t`, `--table` | | diagram one table plus the tables it references / that reference it (DBeaver's table ERD tab; the root table gets the orange header) |
| `--include`, `--exclude GLOB,...` | | table-name filters |
| `--no-views`, `--partitions` | views on, partitions off | same as DBeaver's preferences |
| `-o`, `--out FILE.png` | | PNG output |
| `--svg FILE.svg` | | SVG output (`--svg-embed-font` makes it self-contained) |
| `--scale N` | 1 | scale factor for the PNG, e.g. `2` for HiDPI; fractions like `1.5` work |
| `--notation idef1x\|crowsfoot` | `idef1x` | connection notation |
| `--look dbeaver\|clean\|paper\|pastel` | `dbeaver` | visual style, see [Looks](#looks) |
| `--theme light\|dark` | `light` | light or dark variant of the look |
| `--attributes all\|keys\|primary\|none` | `all` | which columns to show |
| `--show types,nullability,comments,fqn,alpha` | | DBeaver's view styles (icons are on; `--hide icons` turns them off) |
| `--color "key=#rrggbb;key=r,g,b"` | | colour overrides (see `--help` for the keys) |
| `--font FAMILY\|file.ttf`, `--font-bold file.ttf`, `--font-size PT` | bundled Selawik, 9 pt | an installed family, or font files; Selawik has Segoe UI's metrics, DBeaver's default UI font |

Exit codes: 0 ok, 1 usage error, 2 database/render error, 3 no tables found.

## Looks

`--look` picks the visual style; `--theme dark` gives each one a dark variant and `--color`
still overrides single colours (`border`, `separator`, `type-foreground`, `header-foreground`
are the extra keys the non-DBeaver looks use).

| | |
|---|---|
| `dbeaver` (default): DBeaver's ERD editor, yellow headers, blue frames | `clean`: rounded cards, slate header bar, thin grey frame |
| ![dbeaver](docs/looks/dbeaver.png) | ![clean](docs/looks/clean.png) |
| `paper`: black and white, monospaced, prints well | `pastel`: a pastel header per table (by name), lines in the colour of the table they point to |
| ![paper](docs/looks/paper.png) | ![pastel](docs/looks/pastel.png) |

Dark variants of the same looks (`--theme dark`):

| | |
|---|---|
| ![dbeaver dark](docs/looks/dbeaver-dark.png) | ![clean dark](docs/looks/clean-dark.png) |
| ![paper dark](docs/looks/paper-dark.png) | ![pastel dark](docs/looks/pastel-dark.png) |

## How faithful is it?

* **Entities**: same box model as DBeaver's `EntityFigure` (title with table icon, bold primary-key
  section with the key overlay, attribute section, 2 px blue frame and separators), same colours,
  same icons.
* **Relations**: IDEF1X by default (dot at the FK end, hollow diamond at the parent end for nullable
  FKs, dotted line for non-identifying relations) or crow's foot with `1`, `1..n`, `0..1` labels.
* **Layout and routing**: the Eclipse Layout Kernel's layered algorithm, not DBeaver's arrangement.
  Parents are placed left of the tables that reference them; every line leaves the child through its
  FK column's row on the left edge and arrives at the parent's right edge next to the referenced key
  (several arrivals are spread 12 px / 24 px apart and ordered to avoid crossings); lines are routed with
  right angles, kept apart from each other and from tables. Self-references loop beside the table.
* **Types**: shown like DBeaver, i.e. PostgreSQL's internal names: `int4`, `varchar(255)`,
  `numeric(10, 2)`, `timestamptz`, `_text` for arrays, `schema.type` for user-defined types.

Differences you may notice: tables are arranged by ELK rather than DBeaver's "Arrange all", and the
lines are orthogonal instead of DBeaver's diagonal shortest paths; text is measured from the font
files directly, so label widths can be a pixel off from DBeaver's GDI/Cairo metrics; and DBeaver takes
a few colours from the OS theme (here the Windows defaults are used, override with `--color`).

## Use as a library

```ts
import pg from 'pg';
import { FontSet, Look, PgSchemaReader, RenderOptions, render, toPng } from 'dbeaver-erd';

const client = new pg.Client({ connectionString: process.env.DATABASE_URL });
await client.connect();
const diagram = await new PgSchemaReader(client).readSchema('public');
await client.end();

const options = new RenderOptions();
options.look = Look.clean();
options.theme = options.look.theme(false);
options.fonts = FontSet.load(null);
const rendered = await render(diagram, options);
const svg = rendered.toSvg();
const png = toPng(rendered, svg, { scale: 2 });
```

## CI example (GitHub Actions)

```yaml
- uses: actions/setup-node@v4
  with: { node-version: 22 }
- run: sudo apt-get install -y fonts-dejavu-core   # only needed for non-Latin names/comments
- run: npx dbeaver-erd --url "$DATABASE_URL" --schema public --out docs/erd.png --svg docs/erd.svg
  env:
    PGPASSWORD: ${{ secrets.DB_PASSWORD }}
```

## Licenses

The tool itself is MIT. It bundles DBeaver's icons (Apache-2.0) and the Selawik font (SIL OFL 1.1)
and depends on elkjs (EPL-2.0) and resvg-js (MPL-2.0); see `THIRD_PARTY_NOTICES.md`.
