package io.dbeavererd.pg;

/**
 * DBeaver-style PostgreSQL type labels (PostgreAttribute.getFullTypeName + PostgreTypeHandler*):
 * the pg_type name (int4, varchar, timestamptz) plus modifiers decoded from atttypmod, schema-qualified
 * for types outside pg_catalog, with {@code []} per array dimension.
 */
public final class PgTypeNames {
    // pg_type OIDs (PostgreOid)
    public static final int BOOL = 16;
    public static final int BYTEA = 17;
    public static final int CHAR = 18;
    public static final int BPCHAR = 1042;
    public static final int VARCHAR = 1043;
    public static final int BIT = 1560;
    public static final int VARBIT = 1562;
    public static final int NUMERIC = 1700;
    public static final int FLOAT4 = 700;
    public static final int FLOAT8 = 701;
    public static final int TIME = 1083;
    public static final int TIMETZ = 1266;
    public static final int TIMESTAMP = 1114;
    public static final int TIMESTAMPTZ = 1184;
    public static final int INTERVAL = 1186;
    public static final int JSON = 114;
    public static final int JSONB = 3802;

    private static final int NUMERIC_MASK_PRECISION = 0xffff_0000;
    private static final int NUMERIC_MASK_SCALE = 0x0000_ffff;
    private static final int INTERVAL_TYPE_YEAR = 0x0004_0000;
    private static final int INTERVAL_TYPE_MONTH = 0x0002_0000;
    private static final int INTERVAL_TYPE_DAY = 0x0008_0000;
    private static final int INTERVAL_TYPE_HOUR = 0x0400_0000;
    private static final int INTERVAL_TYPE_MINUTE = 0x0800_0000;
    private static final int INTERVAL_TYPE_SECOND = 0x1000_0000;
    private static final int INTERVAL_TYPE_NONE = 0x7fff_0000;
    private static final int INTERVAL_MASK_TYPE = 0xffff_0000;
    private static final int INTERVAL_MASK_PRECISION = 0x0000_ffff;

    private PgTypeNames() {
    }

    /**
     * @param typeOid     OID of the (element) type
     * @param typeName    pg_type.typname of the (element) type
     * @param typeSchema  schema of the type ("pg_catalog" for built-ins)
     * @param typmod      atttypmod (-1 when absent)
     * @param isGis       PostGIS geometry/geography (no modifiers printed)
     * @param arrayDims   number of array dimensions (0 for scalars)
     */
    public static String fullTypeName(long typeOid, String typeName, String typeSchema, int typmod, boolean isGis, int arrayDims) {
        String base = qualified(typeName, typeSchema) + modifiers(typeOid, typeName, typmod, isGis);
        return base + "[]".repeat(Math.max(0, arrayDims));
    }

    /** PostgreDataType.getFullyQualifiedName(DDL): bare for pg_catalog, else schema.name (quoted if needed). */
    static String qualified(String typeName, String typeSchema) {
        if (typeSchema == null || typeSchema.isEmpty() || typeSchema.equals("pg_catalog")) {
            return quoteIfNeeded(typeName);
        }
        return quoteIfNeeded(typeSchema) + "." + quoteIfNeeded(typeName);
    }

    static String quoteIfNeeded(String ident) {
        if (ident.matches("[a-z_][a-z0-9_]*")) return ident;
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    /** PostgreTypeHandlerProvider.getTypeHandler(...).getTypeModifiersString(type, typmod). */
    static String modifiers(long oid, String typeName, int typmod, boolean isGis) {
        if (isGis) return "";
        switch ((int) oid) {
            case NUMERIC, FLOAT4, FLOAT8 -> {
                if (oid == NUMERIC && typmod > 0) {
                    int precision = (typmod & NUMERIC_MASK_PRECISION) >> 16;
                    int scale = (typmod & NUMERIC_MASK_SCALE) - 4;
                    if (precision > 0) {
                        StringBuilder sb = new StringBuilder("(").append(precision);
                        if (scale > 0) sb.append(", ").append(scale);
                        return sb.append(')').toString();
                    }
                }
                return "";
            }
            case CHAR, BPCHAR, VARCHAR, BIT, VARBIT -> {
                if (typmod > 0) {
                    int length = (oid == BIT || oid == VARBIT) ? typmod : typmod - 4;
                    return "(" + length + ")";
                }
                return "";
            }
            case TIME, TIMETZ, TIMESTAMP, TIMESTAMPTZ -> {
                if (typmod >= 0) return "(" + typmod + ")";
                return "";
            }
            case INTERVAL -> {
                StringBuilder sb = new StringBuilder();
                if (typmod > 0) {
                    if (typeName.endsWith("interval") && (typmod & INTERVAL_TYPE_NONE) != INTERVAL_TYPE_NONE) {
                        sb.append(' ').append(intervalType(typmod));
                    }
                    if ((typmod & INTERVAL_TYPE_SECOND) > 0) {
                        int precision = (short) (typmod & INTERVAL_MASK_PRECISION);
                        if (precision > 0) sb.append('(').append(precision).append(')');
                    }
                }
                return sb.toString();
            }
            default -> {
                return "";
            }
        }
    }

    static String intervalType(int typmod) {
        return switch (typmod & INTERVAL_MASK_TYPE) {
            case INTERVAL_TYPE_YEAR -> "year";
            case INTERVAL_TYPE_YEAR | INTERVAL_TYPE_MONTH -> "year to month";
            case INTERVAL_TYPE_MONTH -> "month";
            case INTERVAL_TYPE_DAY -> "day";
            case INTERVAL_TYPE_DAY | INTERVAL_TYPE_HOUR -> "day to hour";
            case INTERVAL_TYPE_DAY | INTERVAL_TYPE_HOUR | INTERVAL_TYPE_MINUTE -> "day to minute";
            case INTERVAL_TYPE_DAY | INTERVAL_TYPE_HOUR | INTERVAL_TYPE_MINUTE | INTERVAL_TYPE_SECOND -> "day to second";
            case INTERVAL_TYPE_HOUR -> "hour";
            case INTERVAL_TYPE_HOUR | INTERVAL_TYPE_MINUTE -> "hour to minute";
            case INTERVAL_TYPE_HOUR | INTERVAL_TYPE_MINUTE | INTERVAL_TYPE_SECOND -> "hour to second";
            case INTERVAL_TYPE_MINUTE -> "minute";
            case INTERVAL_TYPE_MINUTE | INTERVAL_TYPE_SECOND -> "minute to second";
            case INTERVAL_TYPE_SECOND -> "second";
            default -> throw new IllegalArgumentException("unknown interval typmod " + Integer.toHexString(typmod));
        };
    }

    /**
     * Icon for a column type: PostgreDataType.getObjectImage (json), else DBValueFormatting.getDefaultTypeImage
     * by data kind, where the kind follows PostgreDataType's typcategory switch.
     *
     * @param typcategory pg_type.typcategory of the (element) type
     * @param typeName    pg_type.typname of the (element) type
     * @param arrayDims   array dimensions (arrays use the ARRAY icon)
     */
    public static String iconId(char typcategory, String typeName, int arrayDims) {
        if (arrayDims > 0) return "types/array";
        if (typeName.equals("json") || typeName.equals("jsonb")) return "types/json";
        String lower = typeName.toLowerCase();
        return switch (typcategory) {
            case 'B' -> "types/boolean";
            case 'N' -> "types/number";
            case 'S' -> "types/string";
            case 'D' -> "types/datetime";
            case 'A' -> "types/array";
            case 'C' -> "types/struct";
            case 'E' -> "types/string";       // enums map to VARCHAR -> STRING
            case 'T' -> "types/datetime";     // timespan (interval)
            case 'U' -> switch (lower) {
                case "bytea" -> "types/binary";
                case "uuid" -> "types/uuid";
                case "xml" -> "types/xml";
                case "money" -> "types/number";
                default -> lower.contains("json") ? "types/json" : "types/object";
            };
            case 'V' -> "types/binary";       // bit strings resolve to BINARY in DBeaver
            case 'I' -> "types/string";       // inet/cidr/macaddr -> VARCHAR -> STRING
            case 'G' -> "types/object";       // geometric
            case 'R' -> "types/object";       // range
            case 'P' -> "types/unknown";      // pseudo
            default -> "types/unknown";
        };
    }
}
