package org.dce.ed.mission;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.ardent.ArdentClient;
import org.dce.ed.ardent.ArdentQueryParams;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Ardent-backed lookup and tolerant response parser for commodity purchase stations. */
public final class CommoditySourceSearch {
    private final ArdentClient client;

    public CommoditySourceSearch() {
        this(new ArdentClient());
    }

    CommoditySourceSearch(ArdentClient client) {
        this.client = client;
    }

    public List<CommoditySourceChoice> search(String nearSystem, String commodity, int minSupply) throws IOException {
        return search(nearSystem, commodity, null, minSupply, 50);
    }

    public List<CommoditySourceChoice> search(String nearSystem, String commodity, int minSupply, int radiusLy)
            throws IOException {
        return search(nearSystem, commodity, null, minSupply, radiusLy);
    }

    public List<CommoditySourceChoice> search(String nearSystem, String commodity, String canonicalCommodity,
            int minSupply, int radiusLy) throws IOException {
        if (nearSystem == null || nearSystem.isBlank() || commodity == null || commodity.isBlank()) {
            throw new IOException("System and commodity are required");
        }
        ArdentQueryParams params = queryParams(minSupply, radiusLy);
        String system = nearSystem.trim();
        String commodityName = commodityApiName(commodity, canonicalCommodity);
        List<CommoditySourceChoice> nearby = parse(client.getNearbyExports(system, commodityName, params));
        try {
            List<CommoditySourceChoice> origin = parse(client.getSystemCommodity(system, commodityName, params));
            return mergeNearbyAndOrigin(nearby, origin);
        } catch (IOException ex) {
            return nearby;
        }
    }

    static List<CommoditySourceChoice> mergeNearbyAndOrigin(List<CommoditySourceChoice> nearby,
            List<CommoditySourceChoice> origin) {
        Map<String, CommoditySourceChoice> merged = new LinkedHashMap<>();
        if (origin != null) for (CommoditySourceChoice choice : origin) addChoice(merged, choice, true);
        if (nearby != null) for (CommoditySourceChoice choice : nearby) addChoice(merged, choice, false);
        List<CommoditySourceChoice> out = new ArrayList<>(merged.values());
        out.sort(Comparator.comparing(CommoditySourceChoice::systemDistanceLy,
                Comparator.nullsLast(Double::compareTo)).thenComparing(CommoditySourceChoice::arrivalDistanceLs,
                        Comparator.nullsLast(Double::compareTo)));
        return List.copyOf(out);
    }

    private static void addChoice(Map<String, CommoditySourceChoice> merged, CommoditySourceChoice choice,
            boolean origin) {
        if (choice == null) return;
        CommoditySourceChoice normalized = origin && choice.systemDistanceLy() == null
                ? new CommoditySourceChoice(choice.system(), choice.station(), 0.0, choice.arrivalDistanceLs(),
                        choice.price(), choice.supply(), choice.updatedAt(), choice.stationType(),
                        choice.maxLandingPadSize(), choice.bodyId())
                : choice;
        merged.putIfAbsent((choice.system() + "\n" + choice.station()).toLowerCase(Locale.ROOT), normalized);
    }

    static String commodityApiName(String displayName) {
        return commodityApiName(displayName, null);
    }

    static String commodityApiName(String displayName, String canonicalName) {
        if (canonicalName != null && !canonicalName.isBlank()) {
            String canonical = canonicalName.trim()
                    .replaceFirst("^\\$", "")
                    .replaceFirst("(?i)_Name;$", "")
                    .replaceAll("[^A-Za-z0-9]", "")
                    .toLowerCase(Locale.ROOT);
            if (!canonical.isBlank()) return canonical;
        }
        String normalized = displayName.trim().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        // Compatibility for missions saved before their canonical journal token was retained.
        if ("hesuits".equals(normalized)) return "hazardousenvironmentsuits";
        return normalized;
    }

    static ArdentQueryParams queryParams(int missionRequirement) {
        return queryParams(missionRequirement, 50);
    }

    static ArdentQueryParams queryParams(int missionRequirement, int radiusLy) {
        return new ArdentQueryParams()
                .minVolume(1)
                .maxDistance(Math.max(1, Math.min(500, radiusLy)))
                .maxDaysAgo(7)
                .fleetCarriers(null);
    }

    public static List<CommoditySourceChoice> parse(String json) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray rows = root.isJsonArray() ? root.getAsJsonArray()
                    : root.isJsonObject() && root.getAsJsonObject().has("data")
                            && root.getAsJsonObject().get("data").isJsonArray()
                                    ? root.getAsJsonObject().getAsJsonArray("data") : new JsonArray();
            List<CommoditySourceChoice> out = new ArrayList<>();
            for (JsonElement element : rows) {
                if (!element.isJsonObject()) continue;
                JsonObject o = element.getAsJsonObject();
                String system = string(o, "systemName", "system");
                String station = string(o, "stationName", "marketName", "station");
                if (system == null || station == null) continue;
                out.add(new CommoditySourceChoice(system, station,
                        decimal(o, "distance", "systemDistance"),
                        decimal(o, "distanceToArrival", "distanceToArrivalLs"),
                        integer(o, "buyPrice", "price"), integer(o, "stock", "supply"),
                        string(o, "updatedAt", "timestamp"), string(o, "stationType"),
                        integer(o, "maxLandingPadSize"), longInteger(o, "bodyId")));
            }
            out.sort(Comparator.comparing(CommoditySourceChoice::systemDistanceLy,
                    Comparator.nullsLast(Double::compareTo))
                    .thenComparing(CommoditySourceChoice::arrivalDistanceLs,
                            Comparator.nullsLast(Double::compareTo)));
            return List.copyOf(out);
        } catch (Exception ex) {
            throw new IOException("Invalid Ardent market response", ex);
        }
    }

    private static String string(JsonObject o, String... keys) {
        for (String key : keys) if (o.has(key) && !o.get(key).isJsonNull()) {
            String value = o.get(key).getAsString().trim();
            if (!value.isBlank()) return value;
        }
        return null;
    }

    private static Double decimal(JsonObject o, String... keys) {
        for (String key : keys) if (o.has(key) && !o.get(key).isJsonNull()) return o.get(key).getAsDouble();
        return null;
    }

    private static Integer integer(JsonObject o, String... keys) {
        for (String key : keys) if (o.has(key) && !o.get(key).isJsonNull()) return o.get(key).getAsInt();
        return null;
    }

    private static Long longInteger(JsonObject o, String... keys) {
        for (String key : keys) if (o.has(key) && !o.get(key).isJsonNull()) return o.get(key).getAsLong();
        return null;
    }
}
