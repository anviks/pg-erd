# dbeaver-erd

A headless command-line tool that connects to PostgreSQL and renders the schema as an
entity-relationship diagram that looks like the PNG you get from DBeaver's ERD editor
("Save as image"), so a CI or deploy job can produce the diagram without a GUI.

It re-implements DBeaver's ERD rendering rules (entity box layout, colours, IDEF1X and
crow's-foot notation, auto-arrange, shortest-path connection routing) in plain Java2D,
using the same Draw2D graph layout and router classes DBeaver uses under the hood.

```
java -jar dbeaver-erd.jar --url jdbc:postgresql://db.example.com:5432/app \
    --user app --schema public --out schema.png --svg schema.svg
```

The password is read from `PGPASSWORD` by default (`--password-env NAME` for another variable,
`--password` to pass it directly).

## Requirements

* Java 21 or newer to run the jar (no display, fonts or native libraries needed).
* PostgreSQL 10 or newer.

## Build

```
./gradlew build          # unit tests + build/libs/dbeaver-erd.jar
./gradlew integrationTest   # end-to-end against PostgreSQL in Docker (Testcontainers)
```

The build uses a Java 21 toolchain; if no JDK is installed, Gradle downloads one automatically.

## Options

| Option | Default | Meaning |
|---|---|---|
| `--url` | required | JDBC URL, e.g. `jdbc:postgresql://host:5432/db` |
| `-U`, `--user` | `PGUSER` / OS user | database user |
| `--password`, `--password-env VAR` | `PGPASSWORD` | password, or the environment variable holding it |
| `-s`, `--schema` | `public` | schema to diagram (DBeaver's "ER Diagram" tab on a schema) |
| `-t`, `--table` | | diagram one table plus the tables it references / that reference it (DBeaver's table ERD tab; the root table gets the orange header) |
| `--include`, `--exclude GLOB,...` | | table-name filters |
| `--no-views`, `--partitions` | views on, partitions off | same as DBeaver's preferences |
| `-o`, `--out FILE.png` | | PNG output |
| `--svg FILE.svg` | | SVG output (`--svg-embed-font` makes it self-contained) |
| `--scale N` | 1 | integer upscale of the PNG for HiDPI |
| `--notation idef1x\|crowsfoot` | `idef1x` | connection notation |
| `--attributes all\|keys\|primary\|none` | `all` | which columns to show |
| `--show types,nullability,comments,fqn,alpha` | | DBeaver's view styles (icons are on; `--hide icons` turns them off) |
| `--theme light\|dark` | `light` | DBeaver's light or dark ERD colours |
| `--color "key=#rrggbb;key=r,g,b"` | | colour overrides (see `--help` for the keys) |
| `--font FAMILY`, `--font-size PT` | bundled Selawik, 9 pt | font; Selawik has Segoe UI's metrics, DBeaver's default UI font |
| `--aspect W/H` | 1.6 | viewport aspect ratio used to arrange tables that have no relations into rows |

Exit codes: 0 ok, 1 usage error, 2 database/render error, 3 no tables found.

## How faithful is it?

* **Entities**: same box model as DBeaver's `EntityFigure` (title with table icon, bold primary-key
  section with the key overlay, attribute section, 2 px blue frame and separators), same colours,
  same icons.
* **Relations**: IDEF1X by default (dot at the FK end, hollow diamond at the parent end for nullable
  FKs, dotted line for non-identifying relations) or crow's foot with `1`, `1..n`, `0..1` labels.
* **Layout**: DBeaver's "Arrange all" (Draw2D `DirectedGraphLayout`, left-to-right, same padding),
  including how tables without relations are packed into rows. DBeaver uses the editor window's
  aspect ratio for that; `--aspect` stands in for it.
* **Routing**: DBeaver's shortest-path router with the same end stubs and clipping.
* **Types**: shown like DBeaver, i.e. PostgreSQL's internal names: `int4`, `varchar(255)`,
  `numeric(10, 2)`, `timestamptz`, `_text` for arrays, `schema.type` for user-defined types.

Differences you may notice: text is rendered by Java2D instead of GDI/Cairo, so label widths can be
a pixel off; DBeaver's connection nudging depends on hash-map order, so lines that share an entity
side may be offset differently; and DBeaver takes a few colours from the OS theme (here the Windows
defaults are used, override with `--color`).

## CI example (GitHub Actions)

```yaml
- uses: actions/setup-java@v4
  with: { distribution: temurin, java-version: '21' }
- run: java -jar dbeaver-erd.jar --url "$DATABASE_JDBC_URL" --user "$DB_USER" --schema public --out docs/erd.png --svg docs/erd.svg
  env:
    PGPASSWORD: ${{ secrets.DB_PASSWORD }}
```

## Licenses

The tool is built on the Eclipse Draw2D graph layout (EPL-2.0), DBeaver's icons (Apache-2.0) and
the Selawik font (SIL OFL 1.1); see `THIRD_PARTY_NOTICES.md`.
