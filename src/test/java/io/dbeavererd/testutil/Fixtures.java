package io.dbeavererd.testutil;

import io.dbeavererd.model.Association;
import io.dbeavererd.model.Attribute;
import io.dbeavererd.model.Diagram;
import io.dbeavererd.model.Entity;
import io.dbeavererd.style.Fonts;
import io.dbeavererd.style.RenderOptions;

import java.util.List;

/** Hand-written models used by the unit/pipeline tests (no database needed). */
public final class Fixtures {
    private Fixtures() {
    }

    /** Default options with the bundled font loaded. */
    public static RenderOptions options() {
        RenderOptions o = new RenderOptions();
        o.fonts = Fonts.load(null, Fonts.DEFAULT_POINT_SIZE);
        return o;
    }

    public static Attribute col(String name, String type, String icon, boolean notNull, boolean pk, boolean fk) {
        return new Attribute(name, type, icon, notNull, pk, fk, pk || fk, null);
    }

    public static Entity customers() {
        Entity e = new Entity("public", "customers", Entity.Kind.TABLE, "People who buy things");
        e.addAttribute(col("id", "int4", "types/number", true, true, false));
        e.addAttribute(col("email", "varchar(255)", "types/string", true, false, false));
        e.addAttribute(col("full_name", "text", "types/string", false, false, false));
        e.addAttribute(col("created_at", "timestamptz", "types/datetime", true, false, false));
        e.addAttribute(col("active", "bool", "types/boolean", true, false, false));
        return e;
    }

    public static Entity orders() {
        Entity e = new Entity("public", "orders", Entity.Kind.TABLE, null);
        e.addAttribute(col("id", "int8", "types/number", true, true, false));
        e.addAttribute(col("customer_id", "int4", "types/number", true, false, true));
        e.addAttribute(col("total", "numeric(10, 2)", "types/number", true, false, false));
        e.addAttribute(col("notes", "jsonb", "types/json", false, false, false));
        return e;
    }

    /** A {@code users} table referenced by {@code n} other tables, to exercise fan-out on the PK row. */
    public static Diagram hub(int n) {
        Diagram d = new Diagram("hub");
        Entity users = new Entity("public", "users", Entity.Kind.TABLE, null);
        users.addAttribute(col("id", "int4", "types/number", true, true, false));
        users.addAttribute(col("name", "text", "types/string", true, false, false));
        users.addAttribute(col("email", "varchar(255)", "types/string", true, false, false));
        d.addEntity(users);
        for (int i = 1; i <= n; i++) {
            Entity e = new Entity("public", "child_" + i, Entity.Kind.TABLE, null);
            e.addAttribute(col("id", "int4", "types/number", true, true, false));
            e.addAttribute(col("user_id", "int4", "types/number", i % 3 != 0, false, true));
            e.addAttribute(col("payload", "text", "types/string", false, false, false));
            d.addEntity(e);
            d.addAssociation(new Association("child_" + i + "_user_id_fkey", e, users, List.of("user_id"), List.of("id"), false, i % 3 == 0, false, null));
        }
        return d;
    }

    /** One {@code units} table with {@code n} FK columns to {@code n} different parents (many starts on one side). */
    public static Diagram multiFk(int n) {
        Diagram d = new Diagram("multifk");
        Entity units = new Entity("public", "units", Entity.Kind.TABLE, null);
        units.addAttribute(col("id", "int4", "types/number", true, true, false));
        units.addAttribute(col("name", "text", "types/string", true, false, false));
        List<Entity> parents = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            units.addAttribute(col("ref" + i + "_id", "int4", "types/number", false, false, true));
            Entity p = new Entity("public", "parent_" + i, Entity.Kind.TABLE, null);
            p.addAttribute(col("id", "int4", "types/number", true, true, false));
            p.addAttribute(col("label", "text", "types/string", false, false, false));
            parents.add(p);
        }
        d.addEntity(units);
        for (Entity p : parents) d.addEntity(p);
        for (int i = 1; i <= n; i++) {
            d.addAssociation(new Association("units_ref" + i + "_fkey", units, parents.get(i - 1), List.of("ref" + i + "_id"), List.of("id"), false, true, false, null));
        }
        return d;
    }

    /** ~8 tables: FK chain, nullable FK, self-reference, disconnected table, view, composite PK. */
    public static Diagram shop() {
        Diagram d = new Diagram("shop");
        Entity customers = customers();
        Entity orders = orders();
        Entity items = new Entity("public", "order_items", Entity.Kind.TABLE, null);
        items.addAttribute(col("order_id", "int8", "types/number", true, true, true));
        items.addAttribute(col("product_id", "int4", "types/number", true, true, true));
        items.addAttribute(col("quantity", "int4", "types/number", true, false, false));
        Entity products = new Entity("public", "products", Entity.Kind.TABLE, null);
        products.addAttribute(col("id", "int4", "types/number", true, true, false));
        products.addAttribute(col("sku", "varchar(32)", "types/string", true, false, false));
        products.addAttribute(col("name", "varchar(200)", "types/string", true, false, false));
        products.addAttribute(col("category_id", "int4", "types/number", false, false, true));
        products.addAttribute(col("price", "numeric(12, 2)", "types/number", true, false, false));
        Entity categories = new Entity("public", "categories", Entity.Kind.TABLE, null);
        categories.addAttribute(col("id", "int4", "types/number", true, true, false));
        categories.addAttribute(col("parent_id", "int4", "types/number", false, false, true));
        categories.addAttribute(col("name", "varchar(100)", "types/string", true, false, false));
        Entity audit = new Entity("public", "audit_log", Entity.Kind.TABLE, null);
        audit.addAttribute(col("id", "int8", "types/number", true, true, false));
        audit.addAttribute(col("payload", "jsonb", "types/json", true, false, false));
        audit.addAttribute(col("logged_at", "timestamptz", "types/datetime", true, false, false));
        Entity settings = new Entity("public", "settings", Entity.Kind.TABLE, null);
        settings.addAttribute(col("key", "varchar(64)", "types/string", true, true, false));
        settings.addAttribute(col("value", "text", "types/string", false, false, false));
        Entity view = new Entity("public", "v_customer_orders", Entity.Kind.VIEW, null);
        view.addAttribute(col("customer_id", "int4", "types/number", false, false, false));
        view.addAttribute(col("order_count", "int8", "types/number", false, false, false));
        Entity addresses = new Entity("public", "addresses", Entity.Kind.TABLE, null);
        addresses.addAttribute(col("id", "int4", "types/number", true, true, false));
        addresses.addAttribute(col("customer_id", "int4", "types/number", true, false, true));
        addresses.addAttribute(col("line1", "varchar(255)", "types/string", true, false, false));
        addresses.addAttribute(col("city", "varchar(100)", "types/string", true, false, false));
        addresses.addAttribute(col("geo", "point", "types/unknown", false, false, false));

        for (Entity e : List.of(customers, orders, items, products, categories, audit, settings, view, addresses)) {
            d.addEntity(e);
        }
        d.addAssociation(new Association("orders_customer_id_fkey", orders, customers, List.of("customer_id"), List.of("id"), false, false, false, null));
        d.addAssociation(new Association("order_items_order_id_fkey", items, orders, List.of("order_id"), List.of("id"), true, false, false, null));
        d.addAssociation(new Association("order_items_product_id_fkey", items, products, List.of("product_id"), List.of("id"), true, false, false, null));
        d.addAssociation(new Association("products_category_id_fkey", products, categories, List.of("category_id"), List.of("id"), false, true, false, null));
        d.addAssociation(new Association("categories_parent_id_fkey", categories, categories, List.of("parent_id"), List.of("id"), false, true, false, null));
        d.addAssociation(new Association("addresses_customer_id_fkey", addresses, customers, List.of("customer_id"), List.of("id"), false, false, false, null));
        return d;
    }
}
