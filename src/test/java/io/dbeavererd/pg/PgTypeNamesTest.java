package io.dbeavererd.pg;

import org.junit.jupiter.api.Test;

import static io.dbeavererd.pg.PgTypeNames.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PgTypeNamesTest {
    private static String t(long oid, String name, int typmod) {
        return fullTypeName(oid, name, "pg_catalog", typmod, false, 0);
    }

    @Test
    void stringTypes() {
        assertEquals("varchar(255)", t(VARCHAR, "varchar", 259));
        assertEquals("varchar", t(VARCHAR, "varchar", -1));
        assertEquals("bpchar(1)", t(BPCHAR, "bpchar", 5));
        assertEquals("text", t(25, "text", -1));
        assertEquals("bit(8)", t(BIT, "bit", 8));
        assertEquals("varbit(3)", t(VARBIT, "varbit", 3));
    }

    @Test
    void numericTypes() {
        assertEquals("numeric(10, 2)", t(NUMERIC, "numeric", (10 << 16) | (2 + 4)));
        assertEquals("numeric(12)", t(NUMERIC, "numeric", (12 << 16) | 4));
        assertEquals("numeric", t(NUMERIC, "numeric", -1));
        assertEquals("float8", t(FLOAT8, "float8", -1));
        assertEquals("int4", t(23, "int4", -1));
        assertEquals("int8", t(20, "int8", -1));
    }

    @Test
    void timeTypes() {
        assertEquals("timestamptz", t(TIMESTAMPTZ, "timestamptz", -1));
        assertEquals("timestamp(3)", t(TIMESTAMP, "timestamp", 3));
        assertEquals("timetz(0)", t(TIMETZ, "timetz", 0));
        assertEquals("interval", t(INTERVAL, "interval", -1));
        assertEquals("interval day to second(3)", t(INTERVAL, "interval", 0x0008_0000 | 0x0400_0000 | 0x0800_0000 | 0x1000_0000 | 3));
        assertEquals("interval year to month", t(INTERVAL, "interval", 0x0004_0000 | 0x0002_0000 | 0xffff));
    }

    @Test
    void arraysAndUserTypes() {
        assertEquals("int4[]", fullTypeName(23, "int4", "pg_catalog", -1, false, 1));
        assertEquals("varchar(10)[][]", fullTypeName(VARCHAR, "varchar", "pg_catalog", 14, false, 2));
        assertEquals("public.mood", fullTypeName(99999, "mood", "public", -1, false, 0));
        assertEquals("\"My Schema\".\"Odd-Type\"", fullTypeName(99999, "Odd-Type", "My Schema", -1, false, 0));
        assertEquals("public.geometry", fullTypeName(99998, "geometry", "public", 1234, true, 0));
    }

    @Test
    void icons() {
        assertEquals("types/number", iconId('N', "int4", 0));
        assertEquals("types/string", iconId('S', "text", 0));
        assertEquals("types/boolean", iconId('B', "bool", 0));
        assertEquals("types/datetime", iconId('D', "timestamptz", 0));
        assertEquals("types/json", iconId('U', "jsonb", 0));
        assertEquals("types/uuid", iconId('U', "uuid", 0));
        assertEquals("types/binary", iconId('U', "bytea", 0));
        assertEquals("types/array", iconId('N', "int4", 1));
        assertEquals("types/string", iconId('E', "mood", 0));
    }
}
