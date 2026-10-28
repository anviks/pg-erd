import { describe, expect, it } from 'vitest';
import { OID, fullTypeName, iconId } from '../src/pg/type-names.js';

const t = (oid: number, name: string, typmod: number) => fullTypeName(oid, name, 'pg_catalog', typmod, false, 0);

describe('PostgreSQL type labels', () => {
  it('formats character and bit types like DBeaver', () => {
    expect(t(OID.VARCHAR, 'varchar', 259)).toBe('varchar(255)');
    expect(t(OID.VARCHAR, 'varchar', -1)).toBe('varchar');
    expect(t(OID.BPCHAR, 'bpchar', 5)).toBe('bpchar(1)');
    expect(t(25, 'text', -1)).toBe('text');
    expect(t(OID.BIT, 'bit', 8)).toBe('bit(8)');
    expect(t(OID.VARBIT, 'varbit', 3)).toBe('varbit(3)');
  });

  it('formats numeric types', () => {
    expect(t(OID.NUMERIC, 'numeric', (10 << 16) | (2 + 4))).toBe('numeric(10, 2)');
    expect(t(OID.NUMERIC, 'numeric', (12 << 16) | 4)).toBe('numeric(12)');
    expect(t(OID.NUMERIC, 'numeric', -1)).toBe('numeric');
    expect(t(OID.FLOAT8, 'float8', -1)).toBe('float8');
    expect(t(23, 'int4', -1)).toBe('int4');
    expect(t(20, 'int8', -1)).toBe('int8');
  });

  it('formats date/time and interval types', () => {
    expect(t(OID.TIMESTAMPTZ, 'timestamptz', -1)).toBe('timestamptz');
    expect(t(OID.TIMESTAMP, 'timestamp', 3)).toBe('timestamp(3)');
    expect(t(OID.TIMETZ, 'timetz', 0)).toBe('timetz(0)');
    expect(t(OID.INTERVAL, 'interval', -1)).toBe('interval');
    expect(t(OID.INTERVAL, 'interval', 0x00080000 | 0x04000000 | 0x08000000 | 0x10000000 | 3)).toBe('interval day to second(3)');
    expect(t(OID.INTERVAL, 'interval', 0x00040000 | 0x00020000 | 0xffff)).toBe('interval year to month');
  });

  it('qualifies and quotes user types and appends array dimensions', () => {
    expect(fullTypeName(23, 'int4', 'pg_catalog', -1, false, 1)).toBe('int4[]');
    expect(fullTypeName(OID.VARCHAR, 'varchar', 'pg_catalog', 14, false, 2)).toBe('varchar(10)[][]');
    expect(fullTypeName(99999, 'mood', 'public', -1, false, 0)).toBe('public.mood');
    expect(fullTypeName(99999, 'Odd-Type', 'My Schema', -1, false, 0)).toBe('"My Schema"."Odd-Type"');
    expect(fullTypeName(99998, 'geometry', 'public', 1234, true, 0)).toBe('public.geometry');
  });

  it('picks the type icon', () => {
    expect(iconId('N', 'int4', 0)).toBe('types/number');
    expect(iconId('S', 'text', 0)).toBe('types/string');
    expect(iconId('B', 'bool', 0)).toBe('types/boolean');
    expect(iconId('D', 'timestamptz', 0)).toBe('types/datetime');
    expect(iconId('U', 'jsonb', 0)).toBe('types/json');
    expect(iconId('U', 'uuid', 0)).toBe('types/uuid');
    expect(iconId('U', 'bytea', 0)).toBe('types/binary');
    expect(iconId('N', 'int4', 1)).toBe('types/array');
    expect(iconId('E', 'mood', 0)).toBe('types/string');
  });
});
