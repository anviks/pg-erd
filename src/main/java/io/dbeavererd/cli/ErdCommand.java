package io.dbeavererd.cli;

import io.dbeavererd.model.Diagram;
import io.dbeavererd.pg.PgSchemaReader;
import io.dbeavererd.render.Pipeline;
import io.dbeavererd.render.PngExporter;
import io.dbeavererd.render.RenderedDiagram;
import io.dbeavererd.render.SvgExporter;
import io.dbeavererd.style.AttributeVisibility;
import io.dbeavererd.style.Fonts;
import io.dbeavererd.style.Notation;
import io.dbeavererd.style.RenderOptions;
import io.dbeavererd.style.Theme;
import io.dbeavererd.style.ViewStyle;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@Command(name = "dbeaver-erd", mixinStandardHelpOptions = true, version = "dbeaver-erd 0.1.0",
    sortOptions = false,
    description = "Renders a PostgreSQL schema as an entity-relationship diagram that looks like DBeaver's ERD export.",
    footer = {"", "Exit codes: 0 ok, 1 usage error, 2 database error, 3 empty diagram."})
public final class ErdCommand implements Callable<Integer> {

    @Option(names = "--url", required = true, paramLabel = "JDBC_URL", description = "e.g. jdbc:postgresql://localhost:5432/mydb")
    String url;
    @Option(names = {"-U", "--user"}, description = "Database user (default: PGUSER or the OS user).")
    String user;
    @Option(names = "--password", description = "Database password (prefer --password-env or PGPASSWORD).")
    String password;
    @Option(names = "--password-env", paramLabel = "VAR", defaultValue = "PGPASSWORD", description = "Environment variable holding the password (default: ${DEFAULT-VALUE}).")
    String passwordEnv;

    @Option(names = {"-s", "--schema"}, defaultValue = "public", description = "Schema to diagram (default: ${DEFAULT-VALUE}).")
    String schema;
    @Option(names = {"-t", "--table"}, description = "Diagram one table with the tables it references and that reference it (DBeaver's table ERD tab).")
    String table;
    @Option(names = "--include", split = ",", paramLabel = "GLOB", description = "Only tables matching these globs (comma separated).")
    List<String> include = new ArrayList<>();
    @Option(names = "--exclude", split = ",", paramLabel = "GLOB", description = "Skip tables matching these globs.")
    List<String> exclude = new ArrayList<>();
    @Option(names = "--no-views", negatable = false, description = "Leave views out (DBeaver default shows them).")
    boolean noViews;
    @Option(names = "--partitions", description = "Include partition child tables.")
    boolean partitions;

    @Option(names = {"-o", "--out"}, paramLabel = "FILE.png", description = "PNG output file.")
    Path png;
    @Option(names = "--svg", paramLabel = "FILE.svg", description = "SVG output file.")
    Path svg;
    @Option(names = "--svg-embed-font", description = "Embed the bundled font in the SVG so it renders identically everywhere.")
    boolean embedFont;
    @Option(names = "--scale", defaultValue = "1", description = "Integer upscale factor for the PNG (default: ${DEFAULT-VALUE}).")
    int scale;

    @Option(names = "--notation", defaultValue = "idef1x", paramLabel = "idef1x|crowsfoot", description = "Connection notation (default: ${DEFAULT-VALUE}).")
    String notation;
    @Option(names = "--attributes", defaultValue = "all", paramLabel = "all|keys|primary|none", description = "Which columns to show (default: ${DEFAULT-VALUE}).")
    String attributes;
    @Option(names = "--show", split = ",", paramLabel = "STYLE", description = "Extra view styles: types, nullability, comments, fqn, alpha (icons are on by default).")
    List<String> show = new ArrayList<>();
    @Option(names = "--hide", split = ",", paramLabel = "STYLE", description = "View styles to turn off, e.g. icons.")
    List<String> hide = new ArrayList<>();

    @Option(names = "--theme", defaultValue = "light", paramLabel = "light|dark", description = "Colour theme (default: ${DEFAULT-VALUE}).")
    String theme;
    @Option(names = "--color", split = ";", paramLabel = "KEY=VALUE", description = "Colour overrides separated by ';', values as #rrggbb or r,g,b, e.g. \"lines=#008fcf;entity-background=255,255,225\". Keys: background, entity-background, entity-primary-background, entity-association-background, entity-name-foreground, lines, attr-background, attr-foreground, header-1..7.")
    List<String> colors = new ArrayList<>();
    @Option(names = "--font", description = "Installed font family to use instead of the bundled Selawik (Segoe UI metrics).")
    String font;
    @Option(names = "--font-size", defaultValue = "9", description = "Font size in points (default: ${DEFAULT-VALUE}, DBeaver's default).")
    float fontSize;
    @Option(names = "--aspect", defaultValue = "1.6", description = "Viewport width/height ratio used to arrange tables without relations into rows (default: ${DEFAULT-VALUE}).")
    double aspect;

    @Override
    public Integer call() {
        if (png == null && svg == null) {
            System.err.println("error: at least one of --out or --svg is required");
            return 1;
        }
        RenderOptions options;
        try {
            options = buildOptions();
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        }

        Diagram diagram;
        try (Connection conn = connect()) {
            PgSchemaReader.Options ro = new PgSchemaReader.Options();
            ro.showViews = !noViews;
            ro.showPartitions = partitions;
            ro.tableFilter = tableFilter();
            PgSchemaReader reader = new PgSchemaReader(conn, ro);
            diagram = table == null ? reader.readSchema(schema) : reader.readTable(schema, table);
        } catch (SQLException e) {
            System.err.println("database error: " + e.getMessage());
            return 2;
        }
        if (diagram.isEmpty()) {
            System.err.println("error: no tables found in schema '" + schema + "'");
            return 3;
        }

        try {
            RenderedDiagram rendered = Pipeline.render(diagram, options);
            if (png != null) {
                PngExporter.write(rendered, png);
                System.out.println("wrote " + png);
            }
            if (svg != null) {
                SvgExporter.write(rendered, svg);
                System.out.println("wrote " + svg);
            }
        } catch (Exception e) {
            System.err.println("render error: " + e.getMessage());
            return 2;
        }
        return 0;
    }

    private Connection connect() throws SQLException {
        Properties props = new Properties();
        String u = user != null ? user : System.getenv("PGUSER");
        if (u == null || u.isEmpty()) u = System.getProperty("user.name");
        props.setProperty("user", u);
        String pw = password;
        if (pw == null && passwordEnv != null) pw = System.getenv(passwordEnv);
        if (pw != null) props.setProperty("password", pw);
        props.setProperty("ApplicationName", "dbeaver-erd");
        return DriverManager.getConnection(url, props);
    }

    private java.util.function.Predicate<String> tableFilter() {
        List<Pattern> inc = include.stream().map(ErdCommand::glob).toList();
        List<Pattern> exc = exclude.stream().map(ErdCommand::glob).toList();
        return name -> (inc.isEmpty() || inc.stream().anyMatch(p -> p.matcher(name).matches()))
            && exc.stream().noneMatch(p -> p.matcher(name).matches());
    }

    static Pattern glob(String g) {
        StringBuilder sb = new StringBuilder();
        for (char c : g.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                default -> sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(sb.toString());
    }

    RenderOptions buildOptions() {
        RenderOptions o = new RenderOptions();
        o.notation = Notation.parse(notation);
        o.visibility = AttributeVisibility.parse(attributes);
        EnumSet<ViewStyle> styles = EnumSet.of(ViewStyle.ICONS);
        for (String s : show) styles.add(ViewStyle.parse(s));
        for (String s : hide) styles.remove(ViewStyle.parse(s));
        o.styles = styles;
        o.theme = switch (theme.toLowerCase()) {
            case "light" -> Theme.light();
            case "dark" -> Theme.dark();
            default -> throw new IllegalArgumentException("unknown theme: " + theme);
        };
        for (String kv : colors) {
            int eq = kv.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("colour override must be KEY=VALUE: " + kv);
            o.theme.set(kv.substring(0, eq).trim(), Theme.parseColor(kv.substring(eq + 1)));
        }
        o.fonts = Fonts.load(font, fontSize);
        if (scale < 1 || scale > 8) throw new IllegalArgumentException("--scale must be between 1 and 8");
        o.scale = scale;
        if (aspect <= 0) throw new IllegalArgumentException("--aspect must be positive");
        o.aspect = aspect;
        o.embedSvgFont = embedFont;
        return o;
    }
}
