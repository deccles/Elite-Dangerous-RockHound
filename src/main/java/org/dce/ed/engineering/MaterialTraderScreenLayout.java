package org.dce.ed.engineering;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * On-screen grid coordinates for Horizons material traders (row = subtype order, column = grade − 1).
 */
public final class MaterialTraderScreenLayout {

    private static final String RES = "/engineering/material_trader_layout.json";
    private static final Gson GSON = new Gson();

    private static volatile MaterialTraderScreenLayout INSTANCE;

    private final Map<String, TraderGrid> byTraderType;

    private MaterialTraderScreenLayout(Map<String, TraderGrid> byTraderType) {
        this.byTraderType = Collections.unmodifiableMap(byTraderType);
    }

    public static MaterialTraderScreenLayout getInstance() {
        MaterialTraderScreenLayout layout = INSTANCE;
        if (layout == null) {
            synchronized (MaterialTraderScreenLayout.class) {
                layout = INSTANCE;
                if (layout == null) {
                    INSTANCE = layout = load();
                }
            }
        }
        return layout;
    }

    /** Visible for tests. */
    static synchronized void resetForTests() {
        INSTANCE = null;
    }

    /** Visible for tests — inject a layout without touching the classpath resource. */
    static synchronized void setInstanceForTests(MaterialTraderScreenLayout layout) {
        INSTANCE = layout;
    }

    public Optional<GridPos> position(EngineeringMaterial material) {
        if (material == null) {
            return Optional.empty();
        }
        return position(material.getType(), material.getSubtype(), material.getGrade());
    }

    public Optional<GridPos> position(String traderType, String subtype, int grade) {
        if (traderType == null || subtype == null || traderType.isBlank() || subtype.isBlank()) {
            return Optional.empty();
        }
        TraderGrid grid = byTraderType.get(normalizeType(traderType));
        if (grid == null) {
            return Optional.empty();
        }
        Integer row = grid.rowIndexBySubtype.get(subtype.toLowerCase(Locale.ROOT));
        if (row == null) {
            return Optional.empty();
        }
        int col = grade - 1;
        if (col < 0 || col >= grid.grades) {
            return Optional.empty();
        }
        return Optional.of(new GridPos(row, col));
    }

    public List<String> rows(String traderType) {
        TraderGrid grid = byTraderType.get(normalizeType(traderType));
        return grid != null ? grid.rows : List.of();
    }

    public int grades(String traderType) {
        TraderGrid grid = byTraderType.get(normalizeType(traderType));
        return grid != null ? grid.grades : 0;
    }

    private static String normalizeType(String traderType) {
        if (traderType == null) {
            return "";
        }
        String t = traderType.trim();
        if (t.equalsIgnoreCase("raw")) {
            return "Raw";
        }
        if (t.equalsIgnoreCase("manufactured")) {
            return "Manufactured";
        }
        if (t.equalsIgnoreCase("encoded")) {
            return "Encoded";
        }
        return t;
    }

    private static MaterialTraderScreenLayout load() {
        try (InputStream in = MaterialTraderScreenLayout.class.getResourceAsStream(RES)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RES);
            }
            JsonObject root = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            Map<String, TraderGrid> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject obj = e.getValue().getAsJsonObject();
                int grades = obj.get("grades").getAsInt();
                List<String> rows = new java.util.ArrayList<>();
                Map<String, Integer> index = new HashMap<>();
                int i = 0;
                for (JsonElement rowEl : obj.getAsJsonArray("rows")) {
                    String subtype = rowEl.getAsString();
                    rows.add(subtype);
                    index.put(subtype.toLowerCase(Locale.ROOT), i++);
                }
                map.put(normalizeType(e.getKey()), new TraderGrid(grades, List.copyOf(rows), Map.copyOf(index)));
            }
            return new MaterialTraderScreenLayout(map);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load " + RES, ex);
        }
    }

    /** Zero-based row (down) and column (right) on the trader grid. */
    public record GridPos(int row, int col) {
        public GridPos {
            if (row < 0 || col < 0) {
                throw new IllegalArgumentException("row/col must be >= 0");
            }
        }
    }

    private record TraderGrid(int grades, List<String> rows, Map<String, Integer> rowIndexBySubtype) {
    }
}
