import { PostgreSqlContainer, type StartedPostgreSqlContainer } from '@testcontainers/postgresql';
import { readFileSync } from 'node:fs';
import pg from 'pg';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { PgSchemaReader } from '../../src/pg/schema-reader.js';
import { render } from '../../src/render/pipeline.js';
import { toPng } from '../../src/render/png.js';
import { options } from '../fixtures.js';

let container: StartedPostgreSqlContainer;
let client: pg.Client;

beforeAll(async () => {
  container = await new PostgreSqlContainer('postgres:16-alpine').start();
  client = new pg.Client({ connectionString: container.getConnectionUri() });
  await client.connect();
  await client.query(readFileSync('test/fixture.sql', 'utf8'));
});

afterAll(async () => {
  await client?.end();
  await container?.stop();
});

describe('PgSchemaReader', () => {
  it('reads the schema', async () => {
    const d = await new PgSchemaReader(client).readSchema('shop');
    const e = new Map(d.entities.map((x) => [x.name, x]));
    expect(d.entities.length, [...e.keys()].join(',')).toBe(11);
    expect(e.get('v_customer_orders')!.isView).toBe(true);
    expect(e.get('mv_totals')!.isView).toBe(true);
    expect(e.get('customers')!.comment).toBe('People who buy things');

    const attrs = (name: string) => new Map(e.get(name)!.attributes.map((a) => [a.name, a]));
    const cust = attrs('customers');
    expect(cust.get('id')!.fullTypeName).toBe('integer');
    expect(cust.get('id')!.inPrimaryKey).toBe(true);
    expect(cust.get('email')!.fullTypeName).toBe('character varying(255)');
    expect(cust.get('email')!.required).toBe(true);
    expect(cust.get('email')!.inPrimaryKey).toBe(false);
    expect(cust.get('email')!.inAnyConstraint).toBe(true);
    expect(cust.get('email')!.comment).toBe('login name');
    expect(cust.get('created_at')!.fullTypeName).toBe('timestamp with time zone');
    expect(cust.get('active')!.fullTypeName).toBe('boolean');
    expect(cust.get('active')!.iconId).toBe('types/boolean');
    expect(cust.get('mood')!.fullTypeName).toBe('shop.mood');
    expect(cust.get('tags')!.fullTypeName).toBe('text[]');
    expect(cust.get('tags')!.iconId).toBe('types/array');
    expect(cust.get('ext_id')!.fullTypeName).toBe('uuid');
    expect(cust.get('ext_id')!.iconId).toBe('types/uuid');

    const ord = attrs('orders');
    expect(ord.get('id')!.fullTypeName).toBe('bigint');
    expect(ord.get('placed_at')!.fullTypeName).toBe('timestamp(3) without time zone');
    expect(ord.get('total')!.fullTypeName).toBe('numeric(10,2)');
    expect(ord.get('notes')!.iconId).toBe('types/json');
    expect(ord.get('delivery')!.fullTypeName).toBe('interval day to second(3)');
    expect(ord.get('opening')!.fullTypeName).toBe('time(0) with time zone');
    expect(ord.get('customer_id')!.inForeignKey).toBe(true);

    const prod = attrs('products');
    expect(prod.get('price')!.fullTypeName).toBe('numeric(12,2)');
    expect(prod.get('weight')!.fullTypeName).toBe('numeric(8,0)');
    expect(prod.get('picture')!.iconId).toBe('types/binary');

    expect(attrs('settings').get('key')!.inPrimaryKey).toBe(true);
    expect(e.get('no_keys_at_all')!.keyAttributes()).toHaveLength(0);
    expect(attrs('no_keys_at_all').get('b')!.fullTypeName).toBe('bit(8)');
    expect(attrs('no_keys_at_all').get('c')!.fullTypeName).toBe('bit varying(3)');

    const a = new Map(d.associations.map((x) => [x.name, x]));
    expect(a.size, [...a.keys()].join(',')).toBe(6);
    const oc = a.get('orders_customer_id_fkey')!;
    expect(oc.source.name).toBe('orders');
    expect(oc.target.name).toBe('customers');
    expect(oc.identifying).toBe(false);
    expect(oc.optional).toBe(false);
    expect(oc.comment).toBe('who ordered');
    expect(a.get('order_items_order_id_fkey')!.identifying).toBe(true);
    expect(a.get('products_category_id_fkey')!.optional).toBe(true);
    expect(a.get('categories_parent_id_fkey')!.identifying).toBe(false);
    expect(a.get('addresses_customer_id_fkey')!.oneToOne).toBe(true);

    const png = toPng(await render(d, options()));
    expect(png.readUInt32BE(16)).toBeGreaterThan(500);
  });

  it('reads a single table with its neighbours', async () => {
    const d = await new PgSchemaReader(client).readTable('shop', 'orders');
    expect(d.entities.map((x) => x.name).sort()).toEqual(['customers', 'order_items', 'orders']);
    expect(d.entities.find((x) => x.name === 'orders')!.primary).toBe(true);
    expect(d.associations).toHaveLength(2);
  });
});
