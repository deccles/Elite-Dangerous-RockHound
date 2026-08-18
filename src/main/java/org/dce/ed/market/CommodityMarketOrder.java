package org.dce.ed.market;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Orders commodity labels the same way Elite's commodities market does. */
public final class CommodityMarketOrder {
    private static final int UNKNOWN_CATEGORY = Integer.MAX_VALUE;

    private final Map<String, Integer> categoryByCommodity;

    private CommodityMarketOrder(Map<String, Integer> categoryByCommodity) {
        this.categoryByCommodity = Map.copyOf(categoryByCommodity);
    }

    public static CommodityMarketOrder load(Path marketJson) {
        if (marketJson == null || !Files.isRegularFile(marketJson)) {
            return new CommodityMarketOrder(Map.of());
        }
        try (Reader reader = Files.newBufferedReader(marketJson, StandardCharsets.UTF_8)) {
            return fromMarketSnapshot(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception ignored) {
            return new CommodityMarketOrder(Map.of());
        }
    }

    public static CommodityMarketOrder fromMarketSnapshot(JsonObject snapshot) {
        Map<String, Integer> categories = new HashMap<>();
        if (snapshot == null || !snapshot.has("Items") || !snapshot.get("Items").isJsonArray()) {
            return new CommodityMarketOrder(categories);
        }
        for (JsonElement element : snapshot.getAsJsonArray("Items")) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            int category = categoryRank(string(item, "Category"));
            if (category == UNKNOWN_CATEGORY) continue;
            put(categories, string(item, "Name_Localised"), category);
            put(categories, string(item, "Name"), category);
        }
        return new CommodityMarketOrder(categories);
    }

    public Comparator<String> comparator() {
        return (left, right) -> {
            int leftCategory = categoryByCommodity.getOrDefault(normalize(left), UNKNOWN_CATEGORY);
            int rightCategory = categoryByCommodity.getOrDefault(normalize(right), UNKNOWN_CATEGORY);
            int categoryComparison = Integer.compare(leftCategory, rightCategory);
            return categoryComparison != 0
                    ? categoryComparison
                    : String.CASE_INSENSITIVE_ORDER.compare(left, right);
        };
    }

    private static void put(Map<String, Integer> categories, String name, int category) {
        String key = normalize(name);
        if (!key.isBlank()) categories.putIfAbsent(key, category);
    }

    private static String string(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static String normalize(String value) {
        return GalacticAveragePrices.normalizeMaterialKey(value);
    }

    private static int categoryRank(String category) {
        return switch (normalize(category)) {
            case "marketcategorychemicals" -> 0;
            case "marketcategoryconsumeritems" -> 1;
            case "marketcategoryfoods" -> 2;
            case "marketcategoryindustrialmaterials" -> 3;
            case "marketcategorydrugs" -> 4;
            case "marketcategorymachinery" -> 5;
            case "marketcategorymedicines" -> 6;
            case "marketcategorymetals" -> 7;
            case "marketcategoryminerals" -> 8;
            case "marketcategorysalvage" -> 9;
            case "marketcategoryslavery", "marketcategoryslaves" -> 10;
            case "marketcategorytechnology" -> 11;
            case "marketcategorytextiles" -> 12;
            case "marketcategorywaste" -> 13;
            case "marketcategoryweapons" -> 14;
            default -> UNKNOWN_CATEGORY;
        };
    }
}
