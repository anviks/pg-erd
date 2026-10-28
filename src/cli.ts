import { Command, InvalidArgumentError } from 'commander';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import pg from 'pg';
import { parse as parseConnectionString } from 'pg-connection-string';
import { PgSchemaReader } from './pg/schema-reader.js';
import { toPng } from './render/png.js';
import { render } from './render/pipeline.js';
import { Look, LOOK_NAMES } from './style/look.js';
import { RenderOptions, parseNotation, parseViewStyle, parseVisibility, type ViewStyle } from './style/options.js';
import { Theme, parseColor } from './style/theme.js';
import { DEFAULT_POINT_SIZE, FontSet } from './text/fonts.js';

interface CliOptions {
  url?: string;
  user?: string;
  password?: string;
  passwordEnv: string;
  schema: string;
  table?: string;
  include: string[];
  exclude: string[];
  views: boolean;
  partitions: boolean;
  out?: string;
  svg?: string;
  svgEmbedFont: boolean;
  scale: number;
  notation: string;
  look: string;
  theme: string;
  attributes: string;
  show: string[];
  hide: string[];
  color: string[];
  font?: string;
  fontBold?: string;
  fontSize: number;
}

const list = (v: string, prev: string[] = []) => [...prev, ...v.split(',').map((s) => s.trim()).filter(Boolean)];
const semiList = (v: string, prev: string[] = []) => [...prev, ...v.split(';').map((s) => s.trim()).filter(Boolean)];
const number = (v: string) => {
  const n = Number(v);
  if (!Number.isFinite(n)) throw new InvalidArgumentError('not a number');
  return n;
};

/** Converts `--include a*,b?` globs to a name filter. */
export function globFilter(include: string[], exclude: string[]): (name: string) => boolean {
  const re = (g: string) => new RegExp('^' + g.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*').replace(/\?/g, '.') + '$');
  const inc = include.map(re);
  const exc = exclude.map(re);
  return (name) => (inc.length === 0 || inc.some((p) => p.test(name))) && !exc.some((p) => p.test(name));
}

/** Accepts postgres:// and postgresql:// URLs as well as JDBC-style jdbc:postgresql://host/db. */
export function normaliseUrl(url: string): string {
  if (url.startsWith('jdbc:')) url = url.slice('jdbc:'.length);
  return url;
}

/** The URL's own user/password win; otherwise the flags, then PGUSER / the password variable. */
export function connectionConfig(o: Pick<CliOptions, 'url' | 'user' | 'password' | 'passwordEnv'>): pg.ClientConfig {
  const parsed = parseConnectionString(normaliseUrl(o.url!)) as Record<string, unknown>;
  const cfg: Record<string, unknown> = { ...parsed, application_name: 'dbeaver-erd' };
  if (cfg.port != null) cfg.port = Number(cfg.port);
  if (!cfg.user) cfg.user = o.user ?? process.env.PGUSER ?? undefined;
  const password = o.password ?? (o.passwordEnv ? process.env[o.passwordEnv] : undefined);
  if (!cfg.password && password !== undefined) cfg.password = password;
  for (const k of Object.keys(cfg)) if (cfg[k] === undefined || cfg[k] === null) delete cfg[k];
  return cfg as pg.ClientConfig;
}

export function buildOptions(o: CliOptions): RenderOptions {
  const r = new RenderOptions();
  r.look = Look.parse(o.look);
  r.notation = parseNotation(o.notation);
  r.visibility = parseVisibility(o.attributes);
  const styles = new Set<ViewStyle>(['icons']);
  for (const s of o.show) styles.add(parseViewStyle(s));
  for (const s of o.hide) styles.delete(parseViewStyle(s));
  r.styles = styles;
  switch (o.theme.toLowerCase()) {
    case 'light': r.theme = r.look.theme(false); break;
    case 'dark': r.theme = r.look.theme(true); break;
    default: throw new Error(`unknown theme: ${o.theme}`);
  }
  for (const kv of o.color) {
    const eq = kv.indexOf('=');
    if (eq < 0) throw new Error(`colour override must be KEY=VALUE: ${kv}`);
    r.theme.set(kv.slice(0, eq).trim(), parseColor(kv.slice(eq + 1)));
  }
  r.fonts = FontSet.load(o.font ?? r.look.defaultFontFamily, o.fontSize, o.fontBold);
  if (!(o.scale >= 0.1 && o.scale <= 16)) throw new Error('--scale must be between 0.1 and 16');
  r.scale = o.scale;
  r.embedSvgFont = o.svgEmbedFont;
  return r;
}

export function program(): Command {
  const cmd = new Command('dbeaver-erd')
    .description("Renders a PostgreSQL schema as an entity-relationship diagram in the style of DBeaver's ERD export.")
    .version('0.2.0')
    .requiredOption('--url <url>', 'postgres://user@host:5432/db (jdbc:postgresql://... is accepted too)')
    .option('-U, --user <user>', 'database user (default: PGUSER or the OS user)')
    .option('--password <password>', 'database password (prefer --password-env or PGPASSWORD)')
    .option('--password-env <var>', 'environment variable holding the password', 'PGPASSWORD')
    .option('-s, --schema <schema>', 'schema to diagram', 'public')
    .option('-t, --table <table>', "diagram one table with the tables it references and that reference it (DBeaver's table ERD tab)")
    .option('--include <globs>', 'only tables matching these globs (comma separated)', list, [])
    .option('--exclude <globs>', 'skip tables matching these globs', list, [])
    .option('--no-views', 'leave views out (DBeaver default shows them)')
    .option('--partitions', 'include partition child tables', false)
    .option('-o, --out <file.png>', 'PNG output file')
    .option('--svg <file.svg>', 'SVG output file')
    .option('--svg-embed-font', 'embed the font in the SVG so it renders identically everywhere', false)
    .option('--scale <n>', 'scale factor for the PNG, e.g. 2 for HiDPI (fractions allowed)', number, 1)
    .option('--notation <idef1x|crowsfoot>', 'connection notation', 'idef1x')
    .option('--look <name>', `visual style: ${LOOK_NAMES.join(', ')}`, 'dbeaver')
    .option('--theme <light|dark>', 'light or dark variant of the look', 'light')
    .option('--attributes <all|keys|primary|none>', 'which columns to show', 'all')
    .option('--show <styles>', 'extra view styles: types, nullability, comments, fqn, alpha (icons are on by default)', list, [])
    .option('--hide <styles>', 'view styles to turn off, e.g. icons', list, [])
    .option('--color <overrides>', `colour overrides separated by ';', values as #rrggbb or r,g,b, e.g. "lines=#008fcf;entity-background=255,255,225". Keys: ${Theme.KEYS.join(', ')}`, semiList, [])
    .option('--font <family|file.ttf>', "installed font family or a .ttf/.otf file instead of the look's default (bundled Selawik with Segoe UI metrics; monospace for --look paper)")
    .option('--font-bold <file.ttf>', 'bold face when --font is a file')
    .option('--font-size <pt>', "font size in points (DBeaver's default: 9)", number, DEFAULT_POINT_SIZE)
    .addHelpText('after', '\nExit codes: 0 ok, 1 usage error, 2 database/render error, 3 no tables found.')
    .showHelpAfterError();
  return cmd;
}

export async function main(argv: string[]): Promise<number> {
  const cmd = program();
  cmd.exitOverride();
  let o: CliOptions;
  try {
    cmd.parse(argv, { from: 'user' });
    o = cmd.opts<CliOptions>();
  } catch (e) {
    const err = e as { exitCode?: number; code?: string };
    if (err.code === 'commander.helpDisplayed' || err.code === 'commander.version') return 0;
    return 1;
  }
  if (!o.out && !o.svg) {
    console.error('error: at least one of --out or --svg is required');
    return 1;
  }
  let options: RenderOptions;
  try {
    options = buildOptions(o);
  } catch (e) {
    console.error(`error: ${(e as Error).message}`);
    return 1;
  }

  const client = new pg.Client(connectionConfig(o));
  let diagram;
  try {
    await client.connect();
    const reader = new PgSchemaReader(client, {
      showViews: o.views,
      showPartitions: o.partitions,
      tableFilter: globFilter(o.include, o.exclude),
    });
    diagram = o.table ? await reader.readTable(o.schema, o.table) : await reader.readSchema(o.schema);
  } catch (e) {
    let msg = (e as Error).message;
    if (/password must be a string/.test(msg)) msg = `no password given: set ${o.passwordEnv || 'PGPASSWORD'}, use --password-env or --password`;
    console.error(`database error: ${msg}`);
    return 2;
  } finally {
    await client.end().catch(() => undefined);
  }
  if (diagram.isEmpty) {
    console.error(`error: no tables found in schema '${o.schema}'`);
    return 3;
  }

  try {
    const rendered = await render(diagram, options);
    const svg = rendered.toSvg();
    if (o.out) {
      mkdirSync(dirname(o.out), { recursive: true });
      writeFileSync(o.out, toPng(rendered, svg));
      console.log(`wrote ${o.out}`);
    }
    if (o.svg) {
      mkdirSync(dirname(o.svg), { recursive: true });
      writeFileSync(o.svg, svg);
      console.log(`wrote ${o.svg}`);
    }
  } catch (e) {
    console.error(`render error: ${(e as Error).message}`);
    return 2;
  }
  return 0;
}
