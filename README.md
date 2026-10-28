# pg-erd

Renders a PostgreSQL schema as an entity-relationship diagram, PNG and/or SVG, from the command
line. No GUI, browser or JVM: it reads `pg_catalog`, lays the tables out with the Eclipse Layout
Kernel ([elkjs](https://github.com/kieler/elkjs)) and rasterises with
[resvg](https://github.com/yisibl/resvg-js), so it runs in any CI job that has Node.js.

```
npx @anviks/pg-erd -h db.example.com -d app -U app --schema public --out schema.png --svg schema.svg
```

![clean look](docs/looks/clean.png)

* Every relation line starts at its foreign-key column and ends at the referenced key, with right
  angles, kept clear of other tables and lines. Parents are placed left of the tables that reference
  them. Self-references loop beside the table.
* IDEF1X (default) or crow's foot notation; nullable foreign keys, identifying relations and
  one-to-one links (unique index on the FK) are reflected in the symbols and `1`, `0..1`, `0..n` labels.
* Types as PostgreSQL prints them (`character varying(255)`, `numeric(10,2)`, `text[]`),
  NOT NULL markers, table and column comments, PK/FK icons.
* Four looks, each with a light and dark theme, and colour overrides.
* The bundled font (Inter) covers Latin, Cyrillic and Greek; other scripts use an installed font.

Connection flags follow psql: `-h` host, `-p` port, `-d` database, `-U` user, and anything left
out comes from `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER` or the usual defaults (localhost, 5432,
the OS user). The password comes from `PGPASSWORD` (`--password-env NAME` for another variable,
`--password` to pass it directly). `--url postgres://...` (or `jdbc:postgresql://...`) can replace
the flags if you already have a connection URL.

## Requirements

* Node.js 20 or newer. resvg ships prebuilt binaries for Linux (glibc and musl), macOS and Windows
  on x64 and arm64.
* PostgreSQL 10 or newer.
* Optional: an installed font for scripts Inter lacks (CJK, Arabic, Hebrew, Thai, Devanagari), for
  example `fonts-noto-cjk` on Debian/Ubuntu. The `paper` look uses an installed monospace font
  (DejaVu Sans Mono, Liberation Mono, Noto Sans Mono, Consolas or Menlo) and falls back to Inter.

## Install and build

The package is published under a scope because the bare `pg-erd` name on npm is held by an
unrelated placeholder; the installed command is still `pg-erd`.

```
npm install
npm run build        # compiles TypeScript into dist/
npm test             # unit tests (no database needed), images land in test-output/
npm run test:it      # end-to-end against PostgreSQL in Docker (Testcontainers)
node bin/pg-erd.js --help
```

`npm link` puts `pg-erd` on the PATH; `npm pack` produces a tarball for `npm install -g`.

## Options

| Option | Default | Meaning |
|---|---|---|
| `-h`, `--host` | `PGHOST` or localhost | database server host |
| `-p`, `--port` | `PGPORT` or 5432 | database server port |
| `-d`, `--dbname` | `PGDATABASE` or the user name | database name |
| `-U`, `--user` | `PGUSER` or the OS user | database user |
| `--password`, `--password-env VAR` | `PGPASSWORD` | password, or the environment variable holding it |
| `--url` | | `postgres://user:pass@host:5432/db` or `jdbc:postgresql://host:5432/db` instead of the flags above |
| `-s`, `--schema` | `public` | schema to diagram |
| `-t`, `--table` | | diagram one table plus the tables it references / that reference it (the root table gets the accent header) |
| `--include`, `--exclude GLOB,...` | | table-name filters |
| `--no-views`, `--partitions` | views on, partitions off | include views / partition child tables |
| `-o`, `--out FILE.png` | | PNG output |
| `--svg FILE.svg` | | SVG output (`--svg-embed-font` makes it self-contained) |
| `--scale N` | 1 | scale factor for the PNG, e.g. `2` for HiDPI; fractions like `1.5` work |
| `--notation idef1x\|crowsfoot` | `idef1x` | connection notation |
| `--look clean\|dbeaver\|paper\|pastel` | `clean` | visual style, see [Looks](#looks) |
| `--theme light\|dark` | `light` | light or dark variant of the look |
| `--attributes all\|keys\|primary\|none` | `all` | which columns to show |
| `--show types,nullability,comments,fqn,alpha` | | extra details: type column, NOT NULL, comments, schema-qualified names, alphabetical columns (icons are on; `--hide icons` turns them off) |
| `--color "key=#rrggbb;key=r,g,b"` | | colour overrides: background, header, header-accent, header-text, body, text, type-text, lines, border, separator |
| `--font FAMILY\|file.ttf`, `--font-bold file.ttf`, `--font-size PT` | bundled Inter, 9 pt | an installed family, or font files |

Help is `--help` (`-h` is the host, as in psql). Exit codes: 0 ok, 1 usage error, 2 database/render error, 3 no tables found.

## Looks

| | |
|---|---|
| `clean` (default): rounded cards, slate header bar, thin grey frame | `dbeaver`: the look of DBeaver's ERD editor, pale yellow headers and blue frames |
| ![clean](docs/looks/clean.png) | ![dbeaver](docs/looks/dbeaver.png) |
| `paper`: black and white, monospaced, prints well | `pastel`: a pastel header per table (by name), lines in the colour of the table they point to |
| ![paper](docs/looks/paper.png) | ![pastel](docs/looks/pastel.png) |

Dark variants (`--theme dark`):

| | |
|---|---|
| ![clean dark](docs/looks/clean-dark.png) | ![dbeaver dark](docs/looks/dbeaver-dark.png) |
| ![paper dark](docs/looks/paper-dark.png) | ![pastel dark](docs/looks/pastel-dark.png) |

## What the diagram shows

* **Key rows** (bold, above the separator): the primary key; for tables without one, the first
  unique constraint, else a unique index on NOT NULL columns. Views have no key rows.
* **Relations**: one line per foreign key whose both tables are in the diagram. The child end is
  `0..n`, or `0..1` when a unique index covers the FK columns; the parent end is `1`, or `0..1` when
  any FK column is nullable. In IDEF1X the line is dotted unless every FK column is part of the
  child's key (identifying relation), and the parent end carries a diamond when the FK is nullable.
* **`--table` mode** shows the table, every table it references and every table referencing it.

## Use as a library

```ts
import pg from 'pg';
import { FontSet, Look, PgSchemaReader, RenderOptions, render, toPng } from '@anviks/pg-erd';

const client = new pg.Client({ connectionString: process.env.DATABASE_URL });
await client.connect();
const diagram = await new PgSchemaReader(client).readSchema('public');
await client.end();

const options = new RenderOptions();
options.look = Look.pastel();
options.theme = options.look.theme(true);
options.fonts = FontSet.load(null);
const rendered = await render(diagram, options);
const svg = rendered.toSvg();
const png = toPng(rendered, svg, { scale: 2 });
```

## CI example (GitHub Actions)

```yaml
- uses: actions/setup-node@v4
  with: { node-version: 22 }
- run: npx @anviks/pg-erd -h "$DB_HOST" -d app -U app --schema public --out docs/erd.png --svg docs/erd.svg
  env:
    PGPASSWORD: ${{ secrets.DB_PASSWORD }}
```

## Licenses

pg-erd is MIT. It bundles the Inter font (SIL OFL 1.1) and type icons from DBeaver (Apache-2.0), and
depends on elkjs (EPL-2.0) and resvg-js (MPL-2.0); see `THIRD_PARTY_NOTICES.md`.
