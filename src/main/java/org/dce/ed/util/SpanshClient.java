package org.dce.ed.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Minimal client for Spansh (spansh.co.uk) API.
 * <ul>
 *   <li>Route: POST /api/route (form), then GET /api/results/{job} until 200.</li>
 *   <li>Search: GET /api/search?q=... (like top search bar). Returns systems and bodies; each result has "record" and "type".</li>
 *   <li>Body: GET /api/body/{id} (body detail page). Returns record with name, landmarks (exobiology), signals.</li>
 *   <li>Bodies search: POST /api/bodies/search (form-style filters); ref_system often ignored in practice.</li>
 * </ul>
 * API is undocumented. Use search() for "bodies in system X" by name; use getBody(id) for landmarks/exobiology.
 */
public class SpanshClient {

    private static final String BASE = "https://spansh.co.uk/api";
    private static final String USER_AGENT = "EDO-Spansh/1.0";
    private static final int POLL_MS = 1_000;
    private static final int POLL_ATTEMPTS = 30;

    private final HttpClient client;
    private final Gson gson = new Gson();

    public SpanshClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Bodies in system {@code refSystem} within {@code radiusLy}. Returns raw JSON or null.
     */
    public String queryBodies(String refSystem, double radiusLy) {
        return queryBodiesSearch(refSystem, radiusLy, 500);
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * Submit a job to the given path with form body. Returns job id or null.
     */
    private String submitJob(String path, String formBody, String label) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String respBody = resp.body();

        if (resp.statusCode() != 202) {
            System.err.println("Spansh " + label + " submit: HTTP " + resp.statusCode() + " " + respBody);
            return null;
        }

        JsonObject json = JsonParser.parseString(respBody).getAsJsonObject();
        if (json.has("job")) {
            return json.get("job").getAsString();
        }
        System.err.println("Spansh " + label + " submit: no job in " + respBody);
        return null;
    }

    private String pollResults(String job) throws Exception {
        URI resultsUri = URI.create(BASE + "/results/" + job);
        for (int i = 0; i < POLL_ATTEMPTS; i++) {
            TimeUnit.MILLISECONDS.sleep(POLL_MS);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(resultsUri)
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            String body = resp.body();

            if (code == 200) {
                return body;
            }
            if (code == 400) {
                System.err.println("Spansh results error: " + body);
                return null;
            }
            // 202 = still processing
        }
        System.err.println("Spansh results: timed out waiting for job " + job);
        return null;
    }

    /**
     * Try route API (documented pattern from EDMC_SpanshRouter) to confirm base URL and results.
     */
    public String queryRoute(String from, String to, double rangeLy) {
        try {
            String form = "from=" + urlEncode(from) + "&to=" + urlEncode(to)
                    + "&range=" + rangeLy + "&efficiency=60&supercharge_multiplier=4";
            String job = submitJob(BASE + "/route", form, "route");
            if (job == null) return null;
            return pollResults(job);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Body search: POST to /api/bodies/search. Returns raw JSON or null.
     * Spansh may return 200 (immediate) or 202 (job) + poll results.
     * Use refSystem as reference system (bodies within radius of it).
     * Optionally filter to only bodies in that system by setting radius to 0 and using filters.
     */
    public String queryBodiesSearch(String refSystem) {
        return queryBodiesSearch(refSystem, 0, 500);
    }

    /**
     * Body search with radius (ly) and max size. Reference system = center for radius.
     */
    public String queryBodiesSearch(String refSystem, double radiusLy, int size) {
        return queryBodiesSearch(refSystem, radiusLy, size, null);
    }

    /**
     * Body search with optional system_name filter (only bodies in that system).
     */
    public String queryBodiesSearch(String refSystem, double radiusLy, int size, String filterSystemName) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("ref_system", refSystem);
            body.addProperty("radius", radiusLy);
            body.addProperty("size", size);
            if (filterSystemName != null && !filterSystemName.isEmpty()) {
                JsonObject filters = new JsonObject();
                filters.addProperty("system_name", filterSystemName);
                body.add("filters", filters);
            }
            return postBodiesSearch(body.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Body search using only system_name (no ref_system). Tries to get bodies in the given system.
     */
    public String queryBodiesInSystem(String systemName, int size) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("system_name", systemName);
            body.addProperty("size", size);
            return postBodiesSearch(body.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Try GET /api/systems?name=... (undocumented).
     */
    public String getSystemBodies(String systemName) {
        try {
            String path = BASE + "/systems?name=" + urlEncode(systemName);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(path))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) return resp.body();
            System.err.println("Spansh GET systems: " + resp.statusCode() + " " + resp.body());
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Global search (like the top search bar: spansh.co.uk/search/kinesi).
     * Tries GET /api/search?q=... to get systems and bodies by name.
     */
    public String search(String query) {
        try {
            String path = BASE + "/search?q=" + urlEncode(query);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(path))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) return resp.body();
            System.err.println("Spansh search: " + resp.statusCode() + " " + (resp.body() != null ? resp.body().substring(0, Math.min(200, resp.body().length())) : ""));
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Body details by id (like spansh.co.uk/body/1873536417410142499 - landmarks, signals, exobiology).
     * Tries GET /api/body/{id}.
     */
    public String getBody(String bodyId) {
        try {
            String path = BASE + "/body/" + bodyId;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(path))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) return resp.body();
            System.err.println("Spansh body: " + resp.statusCode() + " " + (resp.body() != null ? resp.body().substring(0, Math.min(200, resp.body().length())) : ""));
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Resolves (systemName, bodyName) to the Spansh body id used by GET /api/body/{id}.
     * Uses global search for systemName, then finds a result record whose name matches bodyName
     * (or systemName + " " + bodyName) and system_name matches systemName.
     * Returns the id string for getBody(), or null if not found.
     */
    public String resolveBodyId(String systemName, String bodyName) {
        if (systemName == null || systemName.isBlank() || bodyName == null || bodyName.isBlank()) {
            return null;
        }
        String searchJson = search(systemName);
        if (searchJson == null || searchJson.isBlank()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(searchJson).getAsJsonObject();
            if (!root.has("results")) {
                return null;
            }
            JsonArray results = root.getAsJsonArray("results");
            String sysLower = systemName.trim().toLowerCase(Locale.ROOT);
            String bodyLower = bodyName.trim().toLowerCase(Locale.ROOT);
            String fullNameLower = (systemName.trim() + " " + bodyName.trim()).toLowerCase(Locale.ROOT);

            for (JsonElement el : results) {
                if (!el.isJsonObject()) continue;
                JsonObject r = el.getAsJsonObject();
                JsonObject rec = r.has("record") ? r.getAsJsonObject("record") : r;
                String recSys = rec.has("system_name") ? rec.get("system_name").getAsString() : null;
                if (recSys == null || !recSys.trim().toLowerCase(Locale.ROOT).equals(sysLower)) {
                    continue;
                }
                String recName = rec.has("name") ? rec.get("name").getAsString() : null;
                if (recName == null) continue;
                String recNameLower = recName.trim().toLowerCase(Locale.ROOT);
                if (!recNameLower.equals(bodyLower) && !recNameLower.equals(fullNameLower) && !recNameLower.endsWith(" " + bodyLower)) {
                    continue;
                }
                // Prefer long id (body detail page uses long). Spansh may expose "id" as number or string.
                if (rec.has("id64")) {
                    return String.valueOf(rec.get("id64").getAsLong());
                }
                if (rec.has("id")) {
                    JsonElement idEl = rec.get("id");
                    if (idEl.isJsonPrimitive()) {
                        return idEl.getAsString();
                    }
                }
                return null;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetches exobiology landmarks for a body from Spansh. Uses landmarks only (not signals).
     * Returns empty list when the body exists but has no landmarks; returns null on API/search failure.
     */
    public List<SpanshLandmark> getBodyLandmarks(String systemName, String bodyName) {
        String bodyId = resolveBodyId(systemName, bodyName);
        if (bodyId == null) {
            return null;
        }
        String bodyJson = getBody(bodyId);
        if (bodyJson == null || bodyJson.isBlank()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(bodyJson).getAsJsonObject();
            JsonObject rec = root.has("record") ? root.getAsJsonObject("record") : root;
            if (!rec.has("landmarks") || !rec.get("landmarks").isJsonArray()) {
                return Collections.emptyList();
            }
            JsonArray arr = rec.getAsJsonArray("landmarks");
            List<SpanshLandmark> list = new ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject lm = el.getAsJsonObject();
                String type = lm.has("type") ? lm.get("type").getAsString() : "";
                String subtype = lm.has("subtype") ? lm.get("subtype").getAsString() : "";
                double lat = lm.has("latitude") ? lm.get("latitude").getAsDouble() : 0.0;
                double lon = lm.has("longitude") ? lm.get("longitude").getAsDouble() : 0.0;
                list.add(new SpanshLandmark(type, subtype, lat, lon));
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * POST to /api/bodies/search. Handles 200 (immediate result) or 202 (job then poll).
     */
    private String postBodiesSearch(String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/bodies/search"))
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String respBody = resp.body();
        int code = resp.statusCode();

        if (code == 200) {
            return respBody;
        }
        if (code == 202) {
            JsonObject json = JsonParser.parseString(respBody).getAsJsonObject();
            if (json.has("job")) {
                return pollResults(json.get("job").getAsString());
            }
        }
        System.err.println("Spansh bodies/search: HTTP " + code + " " + (respBody != null && respBody.length() > 200 ? respBody.substring(0, 200) + "..." : respBody));
        return null;
    }

    /**
     * Quick test: route Sol->Tir (confirms API), then bodies in Tir (sample: Tir A 5).
     */
    public static void main(String[] args) {
        SpanshClient c = new SpanshClient();

        System.out.println("1) Route API (Sol -> Tir, 30 ly)...");
        String routeJson = c.queryRoute("Sol", "Tir", 30);
        if (routeJson != null) {
            System.out.println("   OK, length=" + routeJson.length());
        } else {
            System.out.println("   Failed.");
        }

        System.out.println("\n2a) Global search (like top search bar) GET /api/search?q=Kinesi ...");
        String searchJson = c.search("Kinesi");
        if (searchJson != null) {
            System.out.println("   Length: " + searchJson.length());
            JsonObject root = JsonParser.parseString(searchJson).getAsJsonObject();
            if (root.has("results")) {
                var results = root.getAsJsonArray("results");
                System.out.println("   Results: " + results.size());
                for (int i = 0; i < Math.min(12, results.size()); i++) {
                    var r = results.get(i).getAsJsonObject();
                    var rec = r.has("record") ? r.getAsJsonObject("record") : r;
                    String name = rec.has("name") ? rec.get("name").getAsString() : "?";
                    String type = r.has("type") ? r.get("type").getAsString() : "?";
                    String sys = rec.has("system_name") ? rec.get("system_name").getAsString() : (rec.has("systemName") ? rec.get("systemName").getAsString() : "?");
                    String id = rec.has("id") ? rec.get("id").getAsString() : (rec.has("body_id") ? String.valueOf(rec.get("body_id")) : "");
                    System.out.println("     " + name + " | " + type + " | " + sys + (id.isEmpty() ? "" : " | id=" + id));
                }
            } else {
                System.out.println("   Keys: " + (root.isJsonObject() ? root.getAsJsonObject().keySet() : ""));
                System.out.println("   " + searchJson.substring(0, Math.min(500, searchJson.length())) + "...");
            }
        } else {
            System.out.println("   No result.");
        }

        System.out.println("\n2b) Body details GET /api/body/1873536417410142499 (Kinesi 6 f - landmarks/exobiology)...");
        String bodyJson = c.getBody("1873536417410142499");
        if (bodyJson != null) {
            JsonObject b = JsonParser.parseString(bodyJson).getAsJsonObject();
            JsonObject rec = b.has("record") ? b.getAsJsonObject("record") : b;
            System.out.println("   Name: " + (rec.has("name") ? rec.get("name").getAsString() : "?"));
            if (rec.has("landmarks")) System.out.println("   Landmarks: " + rec.getAsJsonArray("landmarks").size());
            if (rec.has("signals")) System.out.println("   Signals: " + rec.get("signals"));
            System.out.println("   (raw length " + bodyJson.length() + ")");
        } else {
            System.out.println("   No result.");
        }

        System.out.println("\n2c) Bodies search (ref_system=Kinesi, radius=0, size=100)...");
        String bodiesJson = c.queryBodiesSearch("Kinesi", 0, 100);
        if (bodiesJson != null) {
            JsonObject root = JsonParser.parseString(bodiesJson).getAsJsonObject();
            System.out.println("   Reference: " + root.get("reference"));
            if (root.has("results")) {
                var results = root.getAsJsonArray("results");
                int kinesiCount = 0;
                for (int i = 0; i < results.size(); i++) {
                    var b = results.get(i).getAsJsonObject();
                    String sys = b.has("system_name") ? b.get("system_name").getAsString() : "";
                    if (!"Kinesi".equalsIgnoreCase(sys)) continue;
                    kinesiCount++;
                    String name = b.has("name") ? b.get("name").getAsString() : "?";
                    boolean landable = b.has("is_landable") && b.get("is_landable").getAsBoolean();
                    Object signals = b.has("signals") ? b.get("signals") : null;
                    String sigStr = signals != null ? " signals=" + signals : "";
                    System.out.println("     " + name + (landable ? " [landable]" : "") + sigStr);
                }
                System.out.println("   Bodies in Kinesi: " + kinesiCount + " (total in response: " + results.size() + ")");
            }
        } else {
            System.out.println("   No result.");
        }

    }
}
