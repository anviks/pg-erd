package io.dbeavererd.pg;

import io.dbeavererd.model.Association;
import io.dbeavererd.model.Attribute;
import io.dbeavererd.model.Diagram;
import io.dbeavererd.model.Entity;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads tables, columns, keys and foreign keys from pg_catalog and builds the diagram model the way
 * DBeaver's ERD does (ERDUtils.collectDatabaseTables, ERDEntity.addModelRelations,
 * ERDContentProviderDefault.fillEntityFromObject).
 */
public final class PgSchemaReader {

    public static final class Options {
        public boolean showViews = true;
        public boolean showPartitions = false;
        /** Table-name filter (applied to tables of the requested schema). */
        public Predicate<String> tableFilter = n -> true;
    }

    // ----- raw catalog rows -----

    record Table(long oid, String schema, String name, char relkind, String comment, boolean partition) {
        boolean isView() { return relkind == 'v' || relkind == 'm'; }
    }

    record Column(long relid, int attnum, String name, long typeOid, String typeName, String typeSchema, char typcategory,
                  int typmod, boolean notNull, int ndims, long elemOid, String elemName, String elemSchema, char elemCategory, String comment) {
    }

    record Constraint(long oid, String name, char type, long relid, long refRelid, int[] keys, int[] refKeys, String comment) {
    }

    record Index(long relid, String name, int[] keys) {
    }

    private final Connection conn;
    private final Options options;

    public PgSchemaReader(Connection conn, Options options) {
        this.conn = conn;
        this.options = options;
    }

    /** DBeaver "ER Diagram" tab on a schema: all tables (and views) directly in the schema. */
    public Diagram readSchema(String schema) throws SQLException {
        List<Table> tables = loadTables("n.nspname = ?", ps -> ps.setString(1, schema));
        tables.removeIf(t -> !options.tableFilter.test(t.name()));
        return build(schema, tables, null);
    }

    /** DBeaver "ER Diagram" tab on one table: the table, what it references and what references it. */
    public Diagram readTable(String schema, String table) throws SQLException {
        List<Table> root = loadTables("n.nspname = ? AND c.relname = ?", ps -> {
            ps.setString(1, schema);
            ps.setString(2, table);
        });
        if (root.isEmpty()) {
            throw new SQLException("table not found: " + schema + "." + table);
        }
        long rootOid = root.get(0).oid();
        Set<Long> oids = new LinkedHashSet<>();
        oids.add(rootOid);
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT confrelid FROM pg_catalog.pg_constraint WHERE contype = 'f' AND conrelid = ? "
                + "UNION ALL SELECT conrelid FROM pg_catalog.pg_constraint WHERE contype = 'f' AND confrelid = ?")) {
            ps.setLong(1, rootOid);
            ps.setLong(2, rootOid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) oids.add(rs.getLong(1));
            }
        }
        List<Table> tables = loadTables("c.oid = ANY(?)", ps -> ps.setArray(1, longArray(oids)));
        return build(schema + "." + table, tables, rootOid);
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private Array longArray(Collection<Long> values) throws SQLException {
        return conn.createArrayOf("bigint", values.toArray(new Long[0]));
    }

    private List<Table> loadTables(String where, Binder binder) throws SQLException {
        String kinds = options.showViews ? "'r','p','f','v','m'" : "'r','p','f'";
        String sql = "SELECT c.oid, n.nspname, c.relname, c.relkind, obj_description(c.oid, 'pg_class') AS comment, c.relispartition "
            + "FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
            + "WHERE c.relkind IN (" + kinds + ") AND " + where + " ORDER BY c.oid";
        List<Table> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Table t = new Table(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4).charAt(0),
                        rs.getString(5), rs.getBoolean(6));
                    if (t.partition() && !options.showPartitions) continue;
                    out.add(t);
                }
            }
        }
        return out;
    }

    private Map<Long, List<Column>> loadColumns(Collection<Long> oids) throws SQLException {
        String sql = "SELECT a.attrelid, a.attnum, a.attname, a.atttypid, t.typname, tn.nspname, t.typcategory, a.atttypmod, a.attnotnull, a.attndims, "
            + "et.oid, et.typname, etn.nspname, et.typcategory, col_description(a.attrelid, a.attnum) "
            + "FROM pg_catalog.pg_attribute a "
            + "JOIN pg_catalog.pg_type t ON t.oid = a.atttypid "
            + "JOIN pg_catalog.pg_namespace tn ON tn.oid = t.typnamespace "
            + "LEFT JOIN pg_catalog.pg_type et ON t.typcategory = 'A' AND et.oid = t.typelem "
            + "LEFT JOIN pg_catalog.pg_namespace etn ON etn.oid = et.typnamespace "
            + "WHERE a.attrelid = ANY(?) AND a.attnum > 0 AND NOT a.attisdropped "
            + "ORDER BY a.attrelid, a.attnum";
        Map<Long, List<Column>> out = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setArray(1, longArray(oids));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Column c = new Column(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getString(6),
                        rs.getString(7).charAt(0), rs.getInt(8), rs.getBoolean(9), rs.getInt(10),
                        rs.getLong(11), rs.getString(12), rs.getString(13), rs.getString(14) == null ? 0 : rs.getString(14).charAt(0),
                        rs.getString(15));
                    out.computeIfAbsent(c.relid(), k -> new ArrayList<>()).add(c);
                }
            }
        }
        return out;
    }

    private static int[] toInts(Array a) throws SQLException {
        if (a == null) return new int[0];
        Object raw = a.getArray();
        if (raw instanceof Integer[] ints) {
            int[] out = new int[ints.length];
            for (int i = 0; i < ints.length; i++) out[i] = ints[i];
            return out;
        }
        if (raw instanceof Short[] shorts) {
            int[] out = new int[shorts.length];
            for (int i = 0; i < shorts.length; i++) out[i] = shorts[i];
            return out;
        }
        if (raw instanceof int[] ints) return ints;
        if (raw instanceof Number[] nums) {
            int[] out = new int[nums.length];
            for (int i = 0; i < nums.length; i++) out[i] = nums[i].intValue();
            return out;
        }
        throw new SQLException("unexpected array type " + raw.getClass());
    }

    private Map<Long, List<Constraint>> loadConstraints(Collection<Long> oids) throws SQLException {
        String sql = "SELECT c.oid, c.conname, c.contype, c.conrelid, c.confrelid, c.conkey, c.confkey, obj_description(c.oid, 'pg_constraint') "
            + "FROM pg_catalog.pg_constraint c WHERE c.conrelid = ANY(?) AND c.contype IN ('p','u','f') ORDER BY c.conrelid, c.oid";
        Map<Long, List<Constraint>> out = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setArray(1, longArray(oids));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Constraint c = new Constraint(rs.getLong(1), rs.getString(2), rs.getString(3).charAt(0), rs.getLong(4), rs.getLong(5),
                        toInts(rs.getArray(6)), toInts(rs.getArray(7)), rs.getString(8));
                    out.computeIfAbsent(c.relid(), k -> new ArrayList<>()).add(c);
                }
            }
        }
        return out;
    }

    /** Unique indexes in DBeaver's index-cache order (ORDER BY table name, index name). */
    private Map<Long, List<Index>> loadUniqueIndexes(Collection<Long> oids) throws SQLException {
        String sql = "SELECT i.indrelid, ic.relname, i.indkey::int2[] "
            + "FROM pg_catalog.pg_index i JOIN pg_catalog.pg_class ic ON ic.oid = i.indexrelid JOIN pg_catalog.pg_class tc ON tc.oid = i.indrelid "
            + "WHERE i.indrelid = ANY(?) AND i.indisunique ORDER BY tc.relname, ic.relname";
        Map<Long, List<Index>> out = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setArray(1, longArray(oids));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int[] keys = toInts(rs.getArray(3));
                    boolean expression = false;
                    for (int k : keys) if (k == 0) expression = true;
                    if (expression || keys.length == 0) continue;   // expression indexes have no plain columns
                    Index ix = new Index(rs.getLong(1), rs.getString(2), keys);
                    out.computeIfAbsent(ix.relid(), k -> new ArrayList<>()).add(ix);
                }
            }
        }
        return out;
    }

    // ----- model building -----

    private Diagram build(String name, List<Table> tables, Long primaryOid) throws SQLException {
        Diagram diagram = new Diagram(name);
        if (tables.isEmpty()) return diagram;
        List<Long> oids = tables.stream().map(Table::oid).toList();
        Map<Long, List<Column>> columns = loadColumns(oids);
        Map<Long, List<Constraint>> constraints = loadConstraints(oids);
        Map<Long, List<Index>> indexes = loadUniqueIndexes(oids);

        Map<Long, Entity> entityOf = new LinkedHashMap<>();
        Map<Long, Map<Integer, Column>> colByNum = new HashMap<>();
        Map<Long, Set<Integer>> identifierOf = new HashMap<>();
        Map<String, Integer> containerIndex = new LinkedHashMap<>();

        for (Table t : tables) {
            List<Column> cols = columns.getOrDefault(t.oid(), List.of());
            List<Constraint> cons = constraints.getOrDefault(t.oid(), List.of());
            List<Index> idx = indexes.getOrDefault(t.oid(), List.of());
            Map<Integer, Column> byNum = new HashMap<>();
            for (Column c : cols) byNum.put(c.attnum(), c);
            colByNum.put(t.oid(), byNum);

            Set<Integer> identifier = bestIdentifier(t, cols, cons, idx, byNum);
            identifierOf.put(t.oid(), identifier);
            Set<Integer> inFk = new LinkedHashSet<>();
            Set<Integer> inAnyConstraint = new LinkedHashSet<>();
            for (Constraint c : cons) {
                for (int k : c.keys()) inAnyConstraint.add(k);
                if (c.type() == 'f') for (int k : c.keys()) inFk.add(k);
            }

            Entity e = new Entity(t.schema(), t.name(), t.isView() ? Entity.Kind.VIEW : Entity.Kind.TABLE, t.comment());
            e.setPrimary(primaryOid != null && primaryOid == t.oid());
            e.setContainerIndex(containerIndex.computeIfAbsent(t.schema(), k -> containerIndex.size()));
            for (Column c : cols) {
                e.addAttribute(new Attribute(
                    c.name(),
                    typeLabel(c),
                    PgTypeNames.iconId(c.typcategory(), c.typeName(), 0),
                    c.notNull(),
                    identifier.contains(c.attnum()),
                    inFk.contains(c.attnum()),
                    inAnyConstraint.contains(c.attnum()),
                    c.comment()));
            }
            entityOf.put(t.oid(), e);
            diagram.addEntity(e);
        }

        // associations: FKs whose both ends are in the diagram (others are silently unresolved, like DBeaver)
        for (Table t : tables) {
            Entity source = entityOf.get(t.oid());
            for (Constraint fk : constraints.getOrDefault(t.oid(), List.of())) {
                if (fk.type() != 'f') continue;
                Entity target = entityOf.get(fk.refRelid());
                if (target == null) continue;
                Map<Integer, Column> srcCols = colByNum.get(t.oid());
                Map<Integer, Column> trgCols = colByNum.getOrDefault(fk.refRelid(), Map.of());
                List<String> srcNames = new ArrayList<>();
                List<String> trgNames = new ArrayList<>();
                boolean optional = false;
                for (int i = 0; i < fk.keys().length; i++) {
                    Column sc = srcCols.get(fk.keys()[i]);
                    Column tc = i < fk.refKeys().length ? trgCols.get(fk.refKeys()[i]) : null;
                    srcNames.add(sc == null ? "?" : sc.name());
                    trgNames.add(tc == null ? "?" : tc.name());
                    if (sc != null && !sc.notNull()) optional = true;
                }
                boolean identifying = isIdentifying(t, fk, constraints.getOrDefault(t.oid(), List.of()));
                boolean oneToOne = isUniqueIndexFor(fk.keys(), indexes.getOrDefault(t.oid(), List.of()));
                diagram.addAssociation(new Association(fk.name(), source, target, srcNames, trgNames, identifying, optional, oneToOne, fk.comment()));
            }
        }
        return diagram;
    }

    /** PostgreAttribute.getFullTypeName: array columns show the array type's own name (e.g. {@code _int4}). */
    static String typeLabel(Column c) {
        boolean gis = c.typeName().equalsIgnoreCase("geometry") || c.typeName().equalsIgnoreCase("geography");
        return PgTypeNames.fullTypeName(c.typeOid(), c.typeName(), c.typeSchema(), c.typmod(), gis, 0);
    }

    /** ERDUtils.getBestTableIdentifier. */
    static Set<Integer> bestIdentifier(Table t, List<Column> cols, List<Constraint> cons, List<Index> idx, Map<Integer, Column> byNum) {
        Set<Integer> out = new LinkedHashSet<>();
        if (t.isView() || cols.isEmpty()) return out;
        Constraint unique = null;
        for (Constraint c : cons) {
            if (c.type() == 'p') {
                for (int k : c.keys()) out.add(k);
                return out;
            } else if (c.type() == 'u') {
                unique = c;
            }
        }
        if (unique != null) {
            for (int k : unique.keys()) out.add(k);
            return out;
        }
        for (Index ix : idx) {
            boolean allRequired = true;
            for (int k : ix.keys()) {
                Column c = byNum.get(k);
                if (c == null || !c.notNull()) {
                    allRequired = false;
                    break;
                }
            }
            if (allRequired) {
                for (int k : ix.keys()) out.add(k);
                return out;
            }
        }
        return out;
    }

    /** DBUtils.isIdentifyingAssociation. */
    static boolean isIdentifying(Table owner, Constraint fk, List<Constraint> ownerConstraints) {
        if (fk.refRelid() == owner.oid()) return false;
        boolean hasPrimaryKey = ownerConstraints.stream().anyMatch(c -> c.type() == 'p');
        for (Constraint c : ownerConstraints) {
            boolean candidate = hasPrimaryKey ? c.type() == 'p' : (c.type() == 'u' || c.type() == 'p');
            if (!candidate) continue;
            boolean included = true;
            for (int k : fk.keys()) {
                boolean found = false;
                for (int ck : c.keys()) if (ck == k) found = true;
                if (!found) {
                    included = false;
                    break;
                }
            }
            if (included) return true;
        }
        return false;
    }

    /** DBUtils.isUniqueIndexForAttributes: a unique index with exactly these columns in this order. */
    static boolean isUniqueIndexFor(int[] keys, List<Index> idx) {
        for (Index ix : idx) {
            if (java.util.Arrays.equals(ix.keys(), keys)) return true;
        }
        return false;
    }
}
