/**
 * DBeaver-style PostgreSQL type labels (PostgreAttribute.getFullTypeName + PostgreTypeHandler*):
 * the pg_type name (int4, varchar, timestamptz) plus modifiers decoded from atttypmod, schema-qualified
 * for types outside pg_catalog, with `[]` per array dimension.
 */

// pg_type OIDs (PostgreOid)
export const OID = {
  BOOL: 16, BYTEA: 17, CHAR: 18, BPCHAR: 1042, VARCHAR: 1043, BIT: 1560, VARBIT: 1562, NUMERIC: 1700,
  FLOAT4: 700, FLOAT8: 701, TIME: 1083, TIMETZ: 1266, TIMESTAMP: 1114, TIMESTAMPTZ: 1184, INTERVAL: 1186,
  JSON: 114, JSONB: 3802,
} as const;

const NUMERIC_MASK_PRECISION = 0xffff0000;
const NUMERIC_MASK_SCALE = 0x0000ffff;
const INTERVAL_TYPE_YEAR = 0x00040000;
const INTERVAL_TYPE_MONTH = 0x00020000;
const INTERVAL_TYPE_DAY = 0x00080000;
const INTERVAL_TYPE_HOUR = 0x04000000;
const INTERVAL_TYPE_MINUTE = 0x08000000;
const INTERVAL_TYPE_SECOND = 0x10000000;
const INTERVAL_TYPE_NONE = 0x7fff0000;
const INTERVAL_MASK_TYPE = 0xffff0000;
const INTERVAL_MASK_PRECISION = 0x0000ffff;

/**
 * @param typeOid    OID of the (element) type
 * @param typeName   pg_type.typname of the (element) type
 * @param typeSchema schema of the type ("pg_catalog" for built-ins)
 * @param typmod     atttypmod (-1 when absent)
 * @param isGis      PostGIS geometry/geography (no modifiers printed)
 * @param arrayDims  number of array dimensions (0 for scalars)
 */
export function fullTypeName(typeOid: number, typeName: string, typeSchema: string | null, typmod: number, isGis: boolean, arrayDims: number): string {
  return qualified(typeName, typeSchema) + modifiers(typeOid, typeName, typmod, isGis) + '[]'.repeat(Math.max(0, arrayDims));
}

/** PostgreDataType.getFullyQualifiedName(DDL): bare for pg_catalog, else schema.name (quoted if needed). */
export function qualified(typeName: string, typeSchema: string | null): string {
  if (!typeSchema || typeSchema === 'pg_catalog') return quoteIfNeeded(typeName);
  return `${quoteIfNeeded(typeSchema)}.${quoteIfNeeded(typeName)}`;
}

export function quoteIfNeeded(ident: string): string {
  if (/^[a-z_][a-z0-9_]*$/.test(ident)) return ident;
  return `"${ident.replace(/"/g, '""')}"`;
}

/** PostgreTypeHandlerProvider.getTypeHandler(...).getTypeModifiersString(type, typmod). */
export function modifiers(oid: number, typeName: string, typmod: number, isGis: boolean): string {
  if (isGis) return '';
  switch (oid) {
    case OID.NUMERIC:
    case OID.FLOAT4:
    case OID.FLOAT8: {
      if (oid === OID.NUMERIC && typmod > 0) {
        const precision = (typmod & NUMERIC_MASK_PRECISION) >> 16;
        const scale = (typmod & NUMERIC_MASK_SCALE) - 4;
        if (precision > 0) return scale > 0 ? `(${precision}, ${scale})` : `(${precision})`;
      }
      return '';
    }
    case OID.CHAR:
    case OID.BPCHAR:
    case OID.VARCHAR:
    case OID.BIT:
    case OID.VARBIT: {
      if (typmod > 0) return `(${oid === OID.BIT || oid === OID.VARBIT ? typmod : typmod - 4})`;
      return '';
    }
    case OID.TIME:
    case OID.TIMETZ:
    case OID.TIMESTAMP:
    case OID.TIMESTAMPTZ:
      return typmod >= 0 ? `(${typmod})` : '';
    case OID.INTERVAL: {
      let s = '';
      if (typmod > 0) {
        if (typeName.endsWith('interval') && (typmod & INTERVAL_TYPE_NONE) !== INTERVAL_TYPE_NONE) s += ` ${intervalType(typmod)}`;
        if ((typmod & INTERVAL_TYPE_SECOND) > 0) {
          const precision = (typmod & INTERVAL_MASK_PRECISION) << 16 >> 16; // signed short
          if (precision > 0) s += `(${precision})`;
        }
      }
      return s;
    }
    default:
      return '';
  }
}

export function intervalType(typmod: number): string {
  switch ((typmod & INTERVAL_MASK_TYPE) >>> 0) {
    case INTERVAL_TYPE_YEAR: return 'year';
    case INTERVAL_TYPE_YEAR | INTERVAL_TYPE_MONTH: return 'year to month';
    case INTERVAL_TYPE_MONTH: return 'month';
    case INTERVAL_TYPE_DAY: return 'day';
    case INTERVAL_TYPE_DAY | INTERVAL_TYPE_HOUR: return 'day to hour';
    case INTERVAL_TYPE_DAY | INTERVAL_TYPE_HOUR | INTERVAL_TYPE_MINUTE: return 'day to minute';
    case INTERVAL_TYPE_DAY | INTERVAL_TYPE_HOUR | INTERVAL_TYPE_MINUTE | INTERVAL_TYPE_SECOND: return 'day to second';
    case INTERVAL_TYPE_HOUR: return 'hour';
    case INTERVAL_TYPE_HOUR | INTERVAL_TYPE_MINUTE: return 'hour to minute';
    case INTERVAL_TYPE_HOUR | INTERVAL_TYPE_MINUTE | INTERVAL_TYPE_SECOND: return 'hour to second';
    case INTERVAL_TYPE_MINUTE: return 'minute';
    case INTERVAL_TYPE_MINUTE | INTERVAL_TYPE_SECOND: return 'minute to second';
    case INTERVAL_TYPE_SECOND: return 'second';
    default: throw new Error(`unknown interval typmod 0x${typmod.toString(16)}`);
  }
}

/**
 * Icon for a column type (PostgreDataType.getObjectImage for json, else DBValueFormatting.getDefaultTypeImage
 * by the data kind that PostgreDataType derives from typcategory).
 */
export function iconId(typcategory: string, typeName: string, arrayDims: number): string {
  if (arrayDims > 0) return 'types/array';
  if (typeName === 'json' || typeName === 'jsonb') return 'types/json';
  const lower = typeName.toLowerCase();
  switch (typcategory) {
    case 'B': return 'types/boolean';
    case 'N': return 'types/number';
    case 'S': return 'types/string';
    case 'D': return 'types/datetime';
    case 'A': return 'types/array';
    case 'C': return 'types/struct';
    case 'E': return 'types/string'; // enums map to VARCHAR -> STRING
    case 'T': return 'types/datetime'; // timespan (interval)
    case 'U':
      switch (lower) {
        case 'bytea': return 'types/binary';
        case 'uuid': return 'types/uuid';
        case 'xml': return 'types/xml';
        case 'money': return 'types/number';
        default: return lower.includes('json') ? 'types/json' : 'types/object';
      }
    case 'V': return 'types/binary'; // bit strings resolve to BINARY in DBeaver
    case 'I': return 'types/string'; // inet/cidr/macaddr -> VARCHAR -> STRING
    case 'G': return 'types/object'; // geometric
    case 'R': return 'types/object'; // range
    case 'P': return 'types/unknown'; // pseudo
    default: return 'types/unknown';
  }
}
