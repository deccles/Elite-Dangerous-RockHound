package org.dce.ed.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

import org.dce.ed.cache.SystemCache;

/**
 * One-off CLI: list systems in the EDO SQLite cache whose body-count columns match a number (default {@code 2}).
 * <p>
 * <b>Database file</b> (same as the overlay): {@link SystemCache#getSqliteCacheDbPath()}, typically
 * {@code %USERPROFILE%\.edo\ed-overlay-systems-v1.db}. Override with {@code -Dedo.cacheDbFile=C:\path\to\file.db}.
 * <p>
 * <b>Columns</b> (see {@code SystemCache} DDL): {@code cached_body_count} is how many bodies are stored in
 * {@code payload_json}; {@code total_bodies} is the journal / FSS total when known (can differ if the cache is partial).
 * <p>
 * Examples:
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=org.dce.ed.tools.ListCachedSystemsByBodyCountMain -Dexec.args="2"
 *   mvn -q exec:java -Dexec.mainClass=org.dce.ed.tools.ListCachedSystemsByBodyCountMain -Dexec.args="--total 2"
 *   mvn -q exec:java -Dexec.mainClass=org.dce.ed.tools.ListCachedSystemsByBodyCountMain -Dexec.args="--either 2"
 * </pre>
 */
public final class ListCachedSystemsByBodyCountMain {

    private ListCachedSystemsByBodyCountMain() {
    }

    public static void main(String[] args) throws Exception {
        FilterMode mode = FilterMode.CACHED_COUNT;
        int n = 2;
        for (String a : args) {
            if ("--help".equalsIgnoreCase(a) || "-h".equalsIgnoreCase(a)) {
                usage();
                return;
            }
        }
        int i = 0;
        while (i < args.length) {
            String a = args[i];
            if ("--total".equalsIgnoreCase(a)) {
                mode = FilterMode.TOTAL_BODIES;
                i++;
            } else if ("--cached".equalsIgnoreCase(a)) {
                mode = FilterMode.CACHED_COUNT;
                i++;
            } else if ("--either".equalsIgnoreCase(a)) {
                mode = FilterMode.EITHER;
                i++;
            } else if (a.startsWith("-")) {
                System.err.println("Unknown option: " + a);
                usage();
                System.exit(2);
                return;
            } else {
                try {
                    n = Integer.parseInt(a.trim());
                } catch (NumberFormatException ex) {
                    System.err.println("Not an integer: " + a);
                    usage();
                    System.exit(2);
                    return;
                }
                i++;
            }
        }
        if (n < 0) {
            System.err.println("Body count must be non-negative.");
            System.exit(2);
            return;
        }

        Path db = SystemCache.getSqliteCacheDbPath();
        if (!Files.isRegularFile(db)) {
            System.err.println("Cache database not found: " + db.toAbsolutePath());
            System.err.println("Set -Dedo.cacheDbFile=... if the overlay uses a non-default path.");
            System.exit(1);
            return;
        }

        String url = "jdbc:sqlite:" + db.toAbsolutePath().toString().replace('\\', '/');
        String where = switch (mode) {
            case CACHED_COUNT -> "cached_body_count = ?";
            case TOTAL_BODIES -> "total_bodies = ?";
            case EITHER -> "(cached_body_count = ? OR total_bodies = ?)";
        };
        String sql = "SELECT system_address, system_name, canonical_name, total_bodies, cached_body_count, updated_at "
                + "FROM systems WHERE " + where + " ORDER BY LOWER(system_name)";

        try (Connection c = DriverManager.getConnection(url);
                PreparedStatement ps = c.prepareStatement(sql)) {
            if (mode == FilterMode.EITHER) {
                ps.setInt(1, n);
                ps.setInt(2, n);
            } else {
                ps.setInt(1, n);
            }
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("db\t" + db.toAbsolutePath());
                System.out.println("mode\t" + mode.name().toLowerCase(Locale.ROOT));
                System.out.println("match\t" + n);
                System.out.println("system_address\tsystem_name\tcanonical_name\ttotal_bodies\tcached_body_count\tupdated_at");
                int rows = 0;
                while (rs.next()) {
                    rows++;
                    System.out.println(rs.getLong("system_address") + "\t"
                            + nullToEmpty(rs.getString("system_name")) + "\t"
                            + nullToEmpty(rs.getString("canonical_name")) + "\t"
                            + intOrBlank(rs, "total_bodies") + "\t"
                            + intOrBlank(rs, "cached_body_count") + "\t"
                            + rs.getLong("updated_at"));
                }
                System.out.println("rows\t" + rows);
            }
        }
    }

    private enum FilterMode {
        CACHED_COUNT,
        TOTAL_BODIES,
        EITHER
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String intOrBlank(ResultSet rs, String col) throws java.sql.SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? "" : Integer.toString(v);
    }

    private static void usage() {
        System.err.println("Usage: ListCachedSystemsByBodyCountMain [--cached|--total|--either] [n]");
        System.err.println("  --cached   Match cached_body_count (default). Bodies present in cache JSON.");
        System.err.println("  --total    Match total_bodies. Journal/FSS total when stored.");
        System.err.println("  --either   Match if either column equals n.");
        System.err.println("  n          Integer body count (default 2).");
        System.err.println("DB path: SystemCache default or -Dedo.cacheDbFile=...");
    }
}
