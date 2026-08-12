package org.dce.ed.mission;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        if (nearSystem == null || nearSystem.isBlank() || commodity == null || commodity.isBlank()) {
            throw new IOException("System and commodity are required");
        }
        ArdentQueryParams params = queryParams(minSupply);
        return parse(client.getNearbyExports(nearSystem.trim(), commodity.trim(), params));
    }

    static ArdentQueryParams queryParams(int missionRequirement) {
        return new ArdentQueryParams()
                .minVolume(1)
                .maxDaysAgo(7)
                .fleetCarriers(Boolean.FALSE);
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
                        string(o, "updatedAt", "timestamp")));
            }
            out.sort(Comparator.comparing(CommoditySourceChoice::systemDistanceLy,
                    Comparator.nullsLast(Double::compareTo))
                    .thenComparing(CommoditySourceChoice::arrivalDistanceLs,
                            Comparator.nullsLast(Double::compareTo)));
            return out.size() > 25 ? List.copyOf(out.subList(0, 25)) : List.copyOf(out);
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
}
