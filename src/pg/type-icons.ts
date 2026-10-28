/** Icon for a column type, by pg_type.typcategory and name. */
export function iconForType(typcategory: string, typeName: string): string {
  const lower = typeName.toLowerCase();
  if (lower === 'json' || lower === 'jsonb') return 'types/json';
  switch (typcategory) {
    case 'B': return 'types/boolean';
    case 'N': return 'types/number';
    case 'S': return 'types/string';
    case 'D': return 'types/datetime';
    case 'T': return 'types/datetime'; // interval
    case 'A': return 'types/array';
    case 'C': return 'types/struct';
    case 'E': return 'types/string'; // enum
    case 'U':
      switch (lower) {
        case 'bytea': return 'types/binary';
        case 'uuid': return 'types/uuid';
        case 'xml': return 'types/xml';
        case 'money': return 'types/number';
        default: return lower.includes('json') ? 'types/json' : 'types/object';
      }
    case 'V': return 'types/binary'; // bit strings
    case 'I': return 'types/string'; // inet, cidr, macaddr
    case 'G': return 'types/object'; // geometric
    case 'R': return 'types/object'; // ranges
    default: return 'types/unknown';
  }
}

/** Quotes an identifier the way psql shows it when it is not a plain lowercase name. */
export function quoteIdent(ident: string): string {
  return /^[a-z_][a-z0-9_]*$/.test(ident) ? ident : `"${ident.replace(/"/g, '""')}"`;
}

/**
 * PostgreSQL's own `format_type()` output, schema-qualified when the type is not a built-in and
 * format_type did not already qualify it (which depends on the session's search_path).
 */
export function typeLabel(formatted: string, typeSchema: string): string {
  if (typeSchema === 'pg_catalog' || formatted.includes('.')) return formatted;
  return `${quoteIdent(typeSchema)}.${formatted}`;
}
