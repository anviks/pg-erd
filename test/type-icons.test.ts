import { describe, expect, it } from 'vitest';
import { iconForType, typeLabel } from '../src/pg/type-icons.js';

describe('type icons and labels', () => {
  it('picks the type icon', () => {
    expect(iconForType('N', 'int4')).toBe('types/number');
    expect(iconForType('S', 'text')).toBe('types/string');
    expect(iconForType('B', 'bool')).toBe('types/boolean');
    expect(iconForType('D', 'timestamptz')).toBe('types/datetime');
    expect(iconForType('U', 'jsonb')).toBe('types/json');
    expect(iconForType('U', 'uuid')).toBe('types/uuid');
    expect(iconForType('U', 'bytea')).toBe('types/binary');
    expect(iconForType('A', '_int4')).toBe('types/array');
    expect(iconForType('E', 'mood')).toBe('types/string');
    expect(iconForType('X', 'whatever')).toBe('types/unknown');
  });

  it('qualifies user types unless format_type already did', () => {
    expect(typeLabel('integer', 'pg_catalog')).toBe('integer');
    expect(typeLabel('mood', 'shop')).toBe('shop.mood');
    expect(typeLabel('shop.mood', 'shop')).toBe('shop.mood');
    expect(typeLabel('"Odd-Type"', 'My Schema')).toBe('"My Schema"."Odd-Type"');
    expect(typeLabel('mood[]', 'shop')).toBe('shop.mood[]');
  });
});
