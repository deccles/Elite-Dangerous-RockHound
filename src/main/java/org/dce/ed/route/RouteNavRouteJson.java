package org.dce.ed.route;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Parses NavRoute.json, Spansh fleet-carrier JSON, and Spansh fleet-carrier CSV exports into {@link RouteEntry} lists.
 */
public final class RouteNavRouteJson {

    private RouteNavRouteJson() {
    }

    public static List<RouteEntry> parseNavRouteFromJson(JsonObject root) {
        List<RouteEntry> entries = new ArrayList<>();
        if (root == null || !root.has("Route") || !root.get("Route").isJsonArray()) {
            return entries;
        }
        JsonArray route = root.getAsJsonArray("Route");
        List<double[]> coords = new ArrayList<>();
        for (JsonElement elem : route) {
            if (!elem.isJsonObject()) {
                continue;
            }
            JsonObject obj = elem.getAsJsonObject();
            String systemName = safeString(obj, "StarSystem");
            long systemAddress = safeLong(obj, "SystemAddress");
            String starClass = safeString(obj, "StarClass");
            JsonArray pos = obj.getAsJsonArray("StarPos");
            RouteEntry entry = new RouteEntry();
            entry.index = entries.size();
            entry.systemName = systemName;
            entry.systemAddress = systemAddress;
            entry.starClass = starClass;
            entry.status = RouteScanStatus.UNKNOWN;
            entries.add(entry);
            if (pos != null && pos.size() == 3) {
                double x = pos.get(0).getAsDouble();
                double y = pos.get(1).getAsDouble();
                double z = pos.get(2).getAsDouble();
                entry.x = Double.valueOf(x);
                entry.y = Double.valueOf(y);
                entry.z = Double.valueOf(z);
                coords.add(new double[] { x, y, z });
            } else {
                entry.x = null;
                entry.y = null;
                entry.z = null;
                coords.add(null);
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            if (i == 0) {
                entries.get(i).distanceLy = null;
            } else {
                double[] prev = coords.get(i - 1);
                double[] cur = coords.get(i);
                if (prev == null || cur == null) {
                    entries.get(i).distanceLy = null;
                } else {
                    double dx = cur[0] - prev[0];
                    double dy = cur[1] - prev[1];
                    double dz = cur[2] - prev[2];
                    entries.get(i).distanceLy = Math.sqrt(dx * dx + dy * dy + dz * dz);
                }
            }
        }
        return entries;
    }

    public static List<RouteEntry> parseSpanshFleetCarrierRouteFromJson(JsonObject root) {
        List<RouteEntry> entries = new ArrayList<>();

        JsonArray jumps = null;
        if (root != null) {
            if (root.has("jumps") && root.get("jumps").isJsonArray()) {
                jumps = root.getAsJsonArray("jumps");
            } else if (root.has("result") && root.get("result").isJsonObject()) {
                JsonObject result = root.getAsJsonObject("result");
                if (result.has("jumps") && result.get("jumps").isJsonArray()) {
                    jumps = result.getAsJsonArray("jumps");
                }
            } else if (root.has("parameters") && root.get("parameters").isJsonObject()) {
                JsonObject parameters = root.getAsJsonObject("parameters");
                if (parameters.has("jumps") && parameters.get("jumps").isJsonArray()) {
                    jumps = parameters.getAsJsonArray("jumps");
                }
            }
        }

        if (jumps == null) {
            return entries;
        }
        List<double[]> coords = new ArrayList<>();

        for (JsonElement elem : jumps) {
            if (!elem.isJsonObject()) {
                continue;
            }
            JsonObject obj = elem.getAsJsonObject();

            String systemName = safeString(obj, "name");
            long systemAddress = safeLong(obj, "id64");

            Double x = safeDouble(obj, "x");
            Double y = safeDouble(obj, "y");
            Double z = safeDouble(obj, "z");

            RouteEntry entry = new RouteEntry();
            entry.index = entries.size();
            entry.systemName = systemName;
            entry.systemAddress = systemAddress;
            entry.starClass = "";
            entry.status = RouteScanStatus.UNKNOWN;

            entry.x = x;
            entry.y = y;
            entry.z = z;

            if (x != null && y != null && z != null) {
                coords.add(new double[] { x.doubleValue(), y.doubleValue(), z.doubleValue() });
            } else {
                coords.add(null);
            }
            entries.add(entry);
        }

        for (int i = 0; i < entries.size(); i++) {
            if (i == 0) {
                entries.get(i).distanceLy = null;
            } else {
                double[] prev = coords.get(i - 1);
                double[] cur = coords.get(i);
                if (prev == null || cur == null) {
                    entries.get(i).distanceLy = null;
                } else {
                    double dx = cur[0] - prev[0];
                    double dy = cur[1] - prev[1];
                    double dz = cur[2] - prev[2];
                    entries.get(i).distanceLy = Math.sqrt(dx * dx + dy * dy + dz * dz);
                }
            }
        }
        return entries;
    }

    /**
     * Spansh fleet-carrier CSV export: header row with {@code System Name} and optional {@code Distance}
     * (per-leg Ly from the previous system; first row’s distance is ignored like JSON).
     */
    public static List<RouteEntry> parseSpanshFleetCarrierRouteFromCsv(Reader reader) throws IOException {
        List<RouteEntry> entries = new ArrayList<>();
        if (reader == null) {
            return entries;
        }
        BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        String headerLine = br.readLine();
        if (headerLine == null) {
            return entries;
        }
        if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
            headerLine = headerLine.substring(1);
        }
        List<String> header = splitCsvLine(headerLine);
        int sysIdx = -1;
        int distIdx = -1;
        for (int i = 0; i < header.size(); i++) {
            String h = trimCsvField(header.get(i));
            if ("system name".equalsIgnoreCase(h)) {
                sysIdx = i;
            } else if ("distance".equalsIgnoreCase(h)) {
                distIdx = i;
            }
        }
        if (sysIdx < 0) {
            return entries;
        }

        String line;
        while ((line = br.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            List<String> cols = splitCsvLine(line);
            if (cols.size() <= sysIdx) {
                continue;
            }
            String name = trimCsvField(cols.get(sysIdx));
            if (name.isEmpty()) {
                continue;
            }
            RouteEntry entry = new RouteEntry();
            entry.index = entries.size();
            entry.systemName = name;
            entry.systemAddress = 0L;
            entry.starClass = "";
            entry.status = RouteScanStatus.UNKNOWN;
            entry.x = null;
            entry.y = null;
            entry.z = null;
            if (entries.isEmpty()) {
                entry.distanceLy = null;
            } else if (distIdx >= 0 && distIdx < cols.size()) {
                String d = trimCsvField(cols.get(distIdx));
                if (d.isEmpty()) {
                    entry.distanceLy = null;
                } else {
                    try {
                        entry.distanceLy = Double.parseDouble(d);
                    } catch (NumberFormatException e) {
                        entry.distanceLy = null;
                    }
                }
            } else {
                entry.distanceLy = null;
            }
            entries.add(entry);
        }
        return entries;
    }

    private static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String trimCsvField(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
            t = t.substring(1, t.length() - 1).replace("\"\"", "\"");
        }
        return t.trim();
    }

    public static String safeString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : "";
    }

    public static long safeLong(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        try {
            if (el == null || el.isJsonNull()) {
                return 0L;
            }
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String s = el.getAsString();
                if (s == null || s.isBlank()) {
                    return 0L;
                }
                return Long.parseLong(s.trim());
            }
            return el.getAsLong();
        } catch (Exception e) {
            return 0L;
        }
    }

    public static Double safeDouble(JsonObject obj, String key) {
        if (obj == null || key == null) {
            return null;
        }
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        try {
            return el.getAsDouble();
        } catch (Exception e) {
            return null;
        }
    }
}
