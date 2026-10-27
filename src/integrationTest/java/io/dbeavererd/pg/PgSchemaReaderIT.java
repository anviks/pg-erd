package io.dbeavererd.pg;

import io.dbeavererd.model.Association;
import io.dbeavererd.model.Attribute;
import io.dbeavererd.model.Diagram;
import io.dbeavererd.model.Entity;
import io.dbeavererd.render.Pipeline;
import io.dbeavererd.render.PngExporter;
import io.dbeavererd.render.RenderedDiagram;
import io.dbeavererd.render.SvgExporter;
import io.dbeavererd.style.Fonts;
import io.dbeavererd.style.RenderOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end against a real PostgreSQL (needs Docker). */
class PgSchemaReaderIT {
    static PostgreSQLContainer<?> pg;

    @BeforeAll
    static void start() throws Exception {
        pg = new PostgreSQLContainer<>("postgres:16-alpine");
        pg.start();
        String ddl = Files.readString(Path.of(PgSchemaReaderIT.class.getResource("/fixture.sql").toURI()), StandardCharsets.UTF_8);
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute(ddl);
        }
    }

    @AfterAll
    static void stop() {
        if (pg != null) pg.stop();
    }

    static Connection connect() throws Exception {
        return DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    }

    @Test
    void readsSchemaLikeDbeaver() throws Exception {
        Diagram d;
        try (Connection c = connect()) {
            d = new PgSchemaReader(c, new PgSchemaReader.Options()).readSchema("shop");
        }
        Map<String, Entity> e = d.entities().stream().collect(Collectors.toMap(Entity::name, Function.identity()));
        assertEquals(11, d.entities().size(), d.entities().toString());
        assertTrue(e.get("v_customer_orders").isView());
        assertTrue(e.get("mv_totals").isView());
        assertEquals("People who buy things", e.get("customers").comment());

        Map<String, Attribute> cust = attrs(e.get("customers"));
        assertEquals("int4", cust.get("id").fullTypeName());
        assertTrue(cust.get("id").inPrimaryKey());
        assertEquals("varchar(255)", cust.get("email").fullTypeName());
        assertTrue(cust.get("email").required());
        assertFalse(cust.get("email").inPrimaryKey());
        assertTrue(cust.get("email").inAnyConstraint());
        assertEquals("login name", cust.get("email").comment());
        assertEquals("timestamptz", cust.get("created_at").fullTypeName());
        assertEquals("bool", cust.get("active").fullTypeName());
        assertEquals("types/boolean", cust.get("active").iconId());
        assertEquals("shop.mood", cust.get("mood").fullTypeName());
        assertEquals("_text", cust.get("tags").fullTypeName());
        assertEquals("types/array", cust.get("tags").iconId());
        assertEquals("uuid", cust.get("ext_id").fullTypeName());
        assertEquals("types/uuid", cust.get("ext_id").iconId());

        Map<String, Attribute> ord = attrs(e.get("orders"));
        assertEquals("int8", ord.get("id").fullTypeName());
        assertEquals("timestamp(3)", ord.get("placed_at").fullTypeName());
        assertEquals("numeric(10, 2)", ord.get("total").fullTypeName());
        assertEquals("jsonb", ord.get("notes").fullTypeName());
        assertEquals("types/json", ord.get("notes").iconId());
        assertEquals("interval day to second(3)", ord.get("delivery").fullTypeName());
        assertEquals("timetz(0)", ord.get("opening").fullTypeName());
        assertTrue(ord.get("customer_id").inForeignKey());

        Map<String, Attribute> prod = attrs(e.get("products"));
        assertEquals("numeric(12, 2)", prod.get("price").fullTypeName());
        assertEquals("numeric(8)", prod.get("weight").fullTypeName());
        assertEquals("bytea", prod.get("picture").fullTypeName());
        assertEquals("types/binary", prod.get("picture").iconId());

        // best identifier: unique constraint when there is no PK; nothing for a table without keys
        assertTrue(attrs(e.get("settings")).get("key").inPrimaryKey());
        assertTrue(e.get("no_keys_at_all").keyAttributes().isEmpty());
        assertEquals("bit(8)", attrs(e.get("no_keys_at_all")).get("b").fullTypeName());
        assertEquals("varbit(3)", attrs(e.get("no_keys_at_all")).get("c").fullTypeName());

        Map<String, Association> a = d.associations().stream().collect(Collectors.toMap(Association::name, Function.identity()));
        assertEquals(6, a.size(), a.keySet().toString());
        Association oc = a.get("orders_customer_id_fkey");
        assertEquals("orders", oc.source().name());
        assertEquals("customers", oc.target().name());
        assertFalse(oc.identifying());
        assertFalse(oc.optional());
        assertEquals("who ordered", oc.comment());
        assertTrue(a.get("order_items_order_id_fkey").identifying());
        assertTrue(a.get("products_category_id_fkey").optional());
        assertTrue(a.get("categories_parent_id_fkey").isSelfReference());
        assertFalse(a.get("categories_parent_id_fkey").identifying());
        assertTrue(a.get("addresses_customer_id_fkey").oneToOne());
        assertFalse(a.get("orders_customer_id_fkey").oneToOne());

        RenderOptions o = new RenderOptions();
        o.fonts = Fonts.load(null, Fonts.DEFAULT_POINT_SIZE);
        RenderedDiagram r = Pipeline.render(d, o);
        PngExporter.write(r, Path.of("build/test-output/it-shop.png"));
        SvgExporter.write(r, Path.of("build/test-output/it-shop.svg"));
        assertTrue(Files.size(Path.of("build/test-output/it-shop.png")) > 0);
    }

    @Test
    void singleTableDiagram() throws Exception {
        Diagram d;
        try (Connection c = connect()) {
            d = new PgSchemaReader(c, new PgSchemaReader.Options()).readTable("shop", "orders");
        }
        Map<String, Entity> e = d.entities().stream().collect(Collectors.toMap(Entity::name, Function.identity()));
        assertEquals(3, d.entities().size(), e.keySet().toString());
        assertNotNull(e.get("customers"));
        assertNotNull(e.get("order_items"));
        assertTrue(e.get("orders").isPrimary());
        assertFalse(e.get("customers").isPrimary());
        assertEquals(2, d.associations().size());
    }

    private static Map<String, Attribute> attrs(Entity e) {
        return e.attributes().stream().collect(Collectors.toMap(Attribute::name, Function.identity()));
    }
}
