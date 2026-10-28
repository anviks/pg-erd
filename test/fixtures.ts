import { Diagram, Entity, type Attribute } from '../src/model/index.js';
import { Look } from '../src/style/look.js';
import { RenderOptions } from '../src/style/options.js';
import { FontSet } from '../src/text/fonts.js';

const fontCache = new Map<string, FontSet>();

/** Default options with the look's font loaded (fonts are cached across tests). */
export function options(look: Look = Look.clean(), dark = false): RenderOptions {
  const o = new RenderOptions();
  o.look = look;
  o.theme = look.theme(dark);
  const key = look.defaultFontFamily ?? '';
  let f = fontCache.get(key);
  if (!f) fontCache.set(key, (f = FontSet.load(look.defaultFontFamily)));
  o.fonts = f;
  return o;
}

export function col(name: string, type: string, icon: string, notNull: boolean, pk: boolean, fk: boolean, comment: string | null = null): Attribute {
  return { name, fullTypeName: type, iconId: icon, required: notNull, inPrimaryKey: pk, inForeignKey: fk, inAnyConstraint: pk || fk, comment };
}

export function customers(): Entity {
  const e = new Entity('public', 'customers', 'table', 'People who buy things');
  e.addAttribute(col('id', 'integer', 'types/number', true, true, false));
  e.addAttribute(col('email', 'character varying(255)', 'types/string', true, false, false));
  e.addAttribute(col('full_name', 'text', 'types/string', false, false, false));
  e.addAttribute(col('created_at', 'timestamp with time zone', 'types/datetime', true, false, false));
  e.addAttribute(col('active', 'boolean', 'types/boolean', true, false, false));
  return e;
}

export function orders(): Entity {
  const e = new Entity('public', 'orders', 'table');
  e.addAttribute(col('id', 'bigint', 'types/number', true, true, false));
  e.addAttribute(col('customer_id', 'integer', 'types/number', true, false, true));
  e.addAttribute(col('total', 'numeric(10,2)', 'types/number', true, false, false));
  e.addAttribute(col('notes', 'jsonb', 'types/json', false, false, false));
  return e;
}

/** A `users` table referenced by `n` other tables, to exercise fan-out on the PK row. */
export function hub(n: number): Diagram {
  const d = new Diagram('hub');
  const users = new Entity('public', 'users', 'table');
  users.addAttribute(col('id', 'integer', 'types/number', true, true, false));
  users.addAttribute(col('name', 'text', 'types/string', true, false, false));
  users.addAttribute(col('email', 'character varying(255)', 'types/string', true, false, false));
  d.addEntity(users);
  for (let i = 1; i <= n; i++) {
    const e = new Entity('public', `child_${i}`, 'table');
    e.addAttribute(col('id', 'integer', 'types/number', true, true, false));
    e.addAttribute(col('user_id', 'integer', 'types/number', i % 3 !== 0, false, true));
    e.addAttribute(col('payload', 'text', 'types/string', false, false, false));
    d.addEntity(e);
    d.addAssociation({
      name: `child_${i}_user_id_fkey`, source: e, target: users, sourceColumns: ['user_id'], targetColumns: ['id'],
      identifying: false, optional: i % 3 === 0, oneToOne: false,
    });
  }
  return d;
}

/** One `units` table with `n` FK columns to `n` different parents (many starts on one side). */
export function multiFk(n: number): Diagram {
  const d = new Diagram('multifk');
  const units = new Entity('public', 'units', 'table');
  units.addAttribute(col('id', 'integer', 'types/number', true, true, false));
  units.addAttribute(col('name', 'text', 'types/string', true, false, false));
  const parents: Entity[] = [];
  for (let i = 1; i <= n; i++) {
    units.addAttribute(col(`ref${i}_id`, 'integer', 'types/number', false, false, true));
    const p = new Entity('public', `parent_${i}`, 'table');
    p.addAttribute(col('id', 'integer', 'types/number', true, true, false));
    p.addAttribute(col('label', 'text', 'types/string', false, false, false));
    parents.push(p);
  }
  d.addEntity(units);
  for (const p of parents) d.addEntity(p);
  for (let i = 1; i <= n; i++) {
    d.addAssociation({
      name: `units_ref${i}_fkey`, source: units, target: parents[i - 1], sourceColumns: [`ref${i}_id`], targetColumns: ['id'],
      identifying: false, optional: true, oneToOne: false,
    });
  }
  return d;
}

/** ~9 tables: FK chain, nullable FK, self-reference, disconnected table, view, composite PK. */
export function shop(): Diagram {
  const d = new Diagram('shop');
  const cust = customers();
  const ord = orders();
  const items = new Entity('public', 'order_items', 'table');
  items.addAttribute(col('order_id', 'bigint', 'types/number', true, true, true));
  items.addAttribute(col('product_id', 'integer', 'types/number', true, true, true));
  items.addAttribute(col('quantity', 'integer', 'types/number', true, false, false));
  const products = new Entity('public', 'products', 'table');
  products.addAttribute(col('id', 'integer', 'types/number', true, true, false));
  products.addAttribute(col('sku', 'character varying(32)', 'types/string', true, false, false));
  products.addAttribute(col('name', 'character varying(200)', 'types/string', true, false, false));
  products.addAttribute(col('category_id', 'integer', 'types/number', false, false, true));
  products.addAttribute(col('price', 'numeric(12,2)', 'types/number', true, false, false));
  const categories = new Entity('public', 'categories', 'table');
  categories.addAttribute(col('id', 'integer', 'types/number', true, true, false));
  categories.addAttribute(col('parent_id', 'integer', 'types/number', false, false, true));
  categories.addAttribute(col('name', 'character varying(100)', 'types/string', true, false, false));
  const audit = new Entity('public', 'audit_log', 'table');
  audit.addAttribute(col('id', 'bigint', 'types/number', true, true, false));
  audit.addAttribute(col('payload', 'jsonb', 'types/json', true, false, false));
  audit.addAttribute(col('logged_at', 'timestamp with time zone', 'types/datetime', true, false, false));
  const settings = new Entity('public', 'settings', 'table');
  settings.addAttribute(col('key', 'character varying(64)', 'types/string', true, true, false));
  settings.addAttribute(col('value', 'text', 'types/string', false, false, false));
  const view = new Entity('public', 'v_customer_orders', 'view');
  view.addAttribute(col('customer_id', 'integer', 'types/number', false, false, false));
  view.addAttribute(col('order_count', 'bigint', 'types/number', false, false, false));
  const addresses = new Entity('public', 'addresses', 'table');
  addresses.addAttribute(col('id', 'integer', 'types/number', true, true, false));
  addresses.addAttribute(col('customer_id', 'integer', 'types/number', true, false, true));
  addresses.addAttribute(col('line1', 'character varying(255)', 'types/string', true, false, false));
  addresses.addAttribute(col('city', 'character varying(100)', 'types/string', true, false, false));
  addresses.addAttribute(col('geo', 'point', 'types/unknown', false, false, false));

  for (const e of [cust, ord, items, products, categories, audit, settings, view, addresses]) d.addEntity(e);
  const assoc = (name: string, source: Entity, target: Entity, sc: string, tc: string, identifying: boolean, optional: boolean, comment: string | null = null) =>
    d.addAssociation({ name, source, target, sourceColumns: [sc], targetColumns: [tc], identifying, optional, oneToOne: false, comment });
  assoc('orders_customer_id_fkey', ord, cust, 'customer_id', 'id', false, false, 'who ordered');
  assoc('order_items_order_id_fkey', items, ord, 'order_id', 'id', true, false);
  assoc('order_items_product_id_fkey', items, products, 'product_id', 'id', true, false);
  assoc('products_category_id_fkey', products, categories, 'category_id', 'id', false, true);
  assoc('categories_parent_id_fkey', categories, categories, 'parent_id', 'id', false, true);
  assoc('addresses_customer_id_fkey', addresses, cust, 'customer_id', 'id', false, false);
  return d;
}
