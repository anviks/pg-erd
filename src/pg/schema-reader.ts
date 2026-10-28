import type { Client, ClientBase } from 'pg';
import { Diagram, Entity, type Association, type Attribute } from '../model/index.js';
import { iconForType, typeLabel } from './type-icons.js';

export interface ReaderOptions {
  showViews?: boolean;
  showPartitions?: boolean;
  /** Table-name filter (applied to tables of the requested schema). */
  tableFilter?: (name: string) => boolean;
}

interface Table {
  oid: number;
  schema: string;
  name: string;
  relkind: string;
  comment: string | null;
  partition: boolean;
}
const isView = (t: Table) => t.relkind === 'v' || t.relkind === 'm';

interface Column {
  relid: number;
  attnum: number;
  name: string;
  typeName: string;
  typeSchema: string;
  typcategory: string;
  /** PostgreSQL's format_type() output, e.g. `character varying(255)`, `numeric(10,2)`, `text[]`. */
  formatted: string;
  notNull: boolean;
  comment: string | null;
}

interface Constraint {
  oid: number;
  name: string;
  type: string;
  relid: number;
  refRelid: number;
  keys: number[];
  refKeys: number[];
  comment: string | null;
}

interface Index {
  relid: number;
  name: string;
  keys: number[];
}

type Row = Record<string, unknown>;
const num = (v: unknown) => Number(v);
const ints = (v: unknown): number[] => (Array.isArray(v) ? v.map(Number) : []);

/** Reads tables, columns, keys and foreign keys from pg_catalog and builds the diagram model. */
export class PgSchemaReader {
  private readonly options: Required<ReaderOptions>;

  constructor(
    private readonly client: ClientBase | Client,
    options: ReaderOptions = {},
  ) {
    this.options = {
      showViews: options.showViews ?? true,
      showPartitions: options.showPartitions ?? false,
      tableFilter: options.tableFilter ?? (() => true),
    };
  }

  /** All tables (and views) directly in the schema. */
  async readSchema(schema: string): Promise<Diagram> {
    const tables = (await this.loadTables('n.nspname = $1', [schema])).filter((t) => this.options.tableFilter(t.name));
    return this.build(schema, tables, null);
  }

  /** One table, the tables it references and the tables that reference it. */
  async readTable(schema: string, table: string): Promise<Diagram> {
    const root = await this.loadTables('n.nspname = $1 AND c.relname = $2', [schema, table]);
    if (root.length === 0) throw new Error(`table not found: ${schema}.${table}`);
    const rootOid = root[0].oid;
    const res = await this.client.query(
      'SELECT confrelid AS oid FROM pg_catalog.pg_constraint WHERE contype = \'f\' AND conrelid = $1 ' +
        'UNION ALL SELECT conrelid FROM pg_catalog.pg_constraint WHERE contype = \'f\' AND confrelid = $1',
      [rootOid],
    );
    const oids = [...new Set([rootOid, ...res.rows.map((r: Row) => num(r.oid))])];
    const tables = await this.loadTables('c.oid = ANY($1::oid[])', [oids]);
    return this.build(`${schema}.${table}`, tables, rootOid);
  }

  private async loadTables(where: string, params: unknown[]): Promise<Table[]> {
    const kinds = this.options.showViews ? "'r','p','f','v','m'" : "'r','p','f'";
    const sql =
      'SELECT c.oid, n.nspname, c.relname, c.relkind, obj_description(c.oid, \'pg_class\') AS comment, c.relispartition ' +
      'FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace ' +
      `WHERE c.relkind IN (${kinds}) AND ${where} ORDER BY c.oid`;
    const res = await this.client.query(sql, params);
    const out: Table[] = [];
    for (const r of res.rows as Row[]) {
      const t: Table = {
        oid: num(r.oid), schema: String(r.nspname), name: String(r.relname), relkind: String(r.relkind),
        comment: (r.comment as string | null) ?? null, partition: Boolean(r.relispartition),
      };
      if (t.partition && !this.options.showPartitions) continue;
      out.push(t);
    }
    return out;
  }

  private async loadColumns(oids: number[]): Promise<Map<number, Column[]>> {
    const sql =
      'SELECT a.attrelid, a.attnum, a.attname, t.typname, tn.nspname AS typschema, t.typcategory, ' +
      'pg_catalog.format_type(a.atttypid, a.atttypmod) AS formatted, a.attnotnull, col_description(a.attrelid, a.attnum) AS comment ' +
      'FROM pg_catalog.pg_attribute a ' +
      'JOIN pg_catalog.pg_type t ON t.oid = a.atttypid ' +
      'JOIN pg_catalog.pg_namespace tn ON tn.oid = t.typnamespace ' +
      'WHERE a.attrelid = ANY($1::oid[]) AND a.attnum > 0 AND NOT a.attisdropped ' +
      'ORDER BY a.attrelid, a.attnum';
    const res = await this.client.query(sql, [oids]);
    const out = new Map<number, Column[]>();
    for (const r of res.rows as Row[]) {
      const c: Column = {
        relid: num(r.attrelid), attnum: num(r.attnum), name: String(r.attname),
        typeName: String(r.typname), typeSchema: String(r.typschema), typcategory: String(r.typcategory),
        formatted: String(r.formatted), notNull: Boolean(r.attnotnull), comment: (r.comment as string | null) ?? null,
      };
      let list = out.get(c.relid);
      if (!list) out.set(c.relid, (list = []));
      list.push(c);
    }
    return out;
  }

  private async loadConstraints(oids: number[]): Promise<Map<number, Constraint[]>> {
    const sql =
      'SELECT c.oid, c.conname, c.contype, c.conrelid, c.confrelid, c.conkey, c.confkey, obj_description(c.oid, \'pg_constraint\') AS comment ' +
      'FROM pg_catalog.pg_constraint c WHERE c.conrelid = ANY($1::oid[]) AND c.contype IN (\'p\',\'u\',\'f\') ORDER BY c.conrelid, c.oid';
    const res = await this.client.query(sql, [oids]);
    const out = new Map<number, Constraint[]>();
    for (const r of res.rows as Row[]) {
      const c: Constraint = {
        oid: num(r.oid), name: String(r.conname), type: String(r.contype), relid: num(r.conrelid), refRelid: num(r.confrelid),
        keys: ints(r.conkey), refKeys: ints(r.confkey), comment: (r.comment as string | null) ?? null,
      };
      let list = out.get(c.relid);
      if (!list) out.set(c.relid, (list = []));
      list.push(c);
    }
    return out;
  }

  /** Unique indexes on plain columns, by name. */
  private async loadUniqueIndexes(oids: number[]): Promise<Map<number, Index[]>> {
    const sql =
      'SELECT i.indrelid, ic.relname, i.indkey::int2[] AS keys ' +
      'FROM pg_catalog.pg_index i JOIN pg_catalog.pg_class ic ON ic.oid = i.indexrelid JOIN pg_catalog.pg_class tc ON tc.oid = i.indrelid ' +
      'WHERE i.indrelid = ANY($1::oid[]) AND i.indisunique ORDER BY tc.relname, ic.relname';
    const res = await this.client.query(sql, [oids]);
    const out = new Map<number, Index[]>();
    for (const r of res.rows as Row[]) {
      const keys = ints(r.keys);
      if (keys.length === 0 || keys.includes(0)) continue; // expression indexes have no plain columns
      const ix: Index = { relid: num(r.indrelid), name: String(r.relname), keys };
      let list = out.get(ix.relid);
      if (!list) out.set(ix.relid, (list = []));
      list.push(ix);
    }
    return out;
  }

  private async build(name: string, tables: Table[], primaryOid: number | null): Promise<Diagram> {
    const diagram = new Diagram(name);
    if (tables.length === 0) return diagram;
    const oids = tables.map((t) => t.oid);
    const columns = await this.loadColumns(oids);
    const constraints = await this.loadConstraints(oids);
    const indexes = await this.loadUniqueIndexes(oids);

    const entityOf = new Map<number, Entity>();
    const colByNum = new Map<number, Map<number, Column>>();

    for (const t of tables) {
      const cols = columns.get(t.oid) ?? [];
      const cons = constraints.get(t.oid) ?? [];
      const idx = indexes.get(t.oid) ?? [];
      const byNum = new Map(cols.map((c) => [c.attnum, c]));
      colByNum.set(t.oid, byNum);

      const identifier = bestIdentifier(t, cols, cons, idx, byNum);
      const inFk = new Set<number>();
      const inAny = new Set<number>();
      for (const c of cons) {
        for (const k of c.keys) inAny.add(k);
        if (c.type === 'f') for (const k of c.keys) inFk.add(k);
      }

      const e = new Entity(t.schema, t.name, isView(t) ? 'view' : 'table', t.comment);
      e.primary = primaryOid !== null && primaryOid === t.oid;
      for (const c of cols) {
        const attr: Attribute = {
          name: c.name,
          fullTypeName: typeLabel(c.formatted, c.typeSchema),
          iconId: iconForType(c.typcategory, c.typeName),
          required: c.notNull,
          inPrimaryKey: identifier.has(c.attnum),
          inForeignKey: inFk.has(c.attnum),
          inAnyConstraint: inAny.has(c.attnum),
          comment: c.comment,
        };
        e.addAttribute(attr);
      }
      entityOf.set(t.oid, e);
      diagram.addEntity(e);
    }

    // associations: FKs whose both ends are in the diagram
    for (const t of tables) {
      const source = entityOf.get(t.oid)!;
      const cons = constraints.get(t.oid) ?? [];
      for (const fk of cons) {
        if (fk.type !== 'f') continue;
        const target = entityOf.get(fk.refRelid);
        if (!target) continue;
        const srcCols = colByNum.get(t.oid)!;
        const trgCols = colByNum.get(fk.refRelid) ?? new Map<number, Column>();
        const sourceColumns: string[] = [];
        const targetColumns: string[] = [];
        let optional = false;
        fk.keys.forEach((k, i) => {
          const sc = srcCols.get(k);
          const tc = i < fk.refKeys.length ? trgCols.get(fk.refKeys[i]) : undefined;
          sourceColumns.push(sc?.name ?? '?');
          targetColumns.push(tc?.name ?? '?');
          if (sc && !sc.notNull) optional = true;
        });
        const a: Association = {
          name: fk.name, source, target, sourceColumns, targetColumns,
          identifying: isIdentifying(t, fk, cons),
          optional,
          oneToOne: isUniqueIndexFor(fk.keys, indexes.get(t.oid) ?? []),
          comment: fk.comment,
        };
        diagram.addAssociation(a);
      }
    }
    return diagram;
  }
}

/** The columns shown as the key: the PK, else the first unique constraint, else a unique index on NOT NULL columns. */
export function bestIdentifier(t: Table, cols: Column[], cons: Constraint[], idx: Index[], byNum: Map<number, Column>): Set<number> {
  const out = new Set<number>();
  if (isView(t) || cols.length === 0) return out;
  const key = cons.find((c) => c.type === 'p') ?? cons.find((c) => c.type === 'u');
  if (key) {
    for (const k of key.keys) out.add(k);
    return out;
  }
  for (const ix of idx) {
    if (ix.keys.every((k) => byNum.get(k)?.notNull)) {
      for (const k of ix.keys) out.add(k);
      return out;
    }
  }
  return out;
}

/** Identifying relation: every FK column is part of the child's PK (or of a unique constraint if it has no PK). */
export function isIdentifying(owner: Table, fk: Constraint, ownerConstraints: Constraint[]): boolean {
  if (fk.refRelid === owner.oid) return false;
  const hasPrimaryKey = ownerConstraints.some((c) => c.type === 'p');
  for (const c of ownerConstraints) {
    const candidate = hasPrimaryKey ? c.type === 'p' : c.type === 'u' || c.type === 'p';
    if (!candidate) continue;
    if (fk.keys.every((k) => c.keys.includes(k))) return true;
  }
  return false;
}

/** A unique index with exactly these columns in this order (the child side is then 0..1). */
export function isUniqueIndexFor(keys: number[], idx: Index[]): boolean {
  return idx.some((ix) => ix.keys.length === keys.length && ix.keys.every((k, i) => k === keys[i]));
}
