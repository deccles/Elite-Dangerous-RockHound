package org.dce.ed.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.edsm.CmdrCreditsResponse;
import org.dce.ed.edsm.CmdrLastPositionResponse;
import org.dce.ed.edsm.CmdrRanksResponse;
import org.dce.ed.edsm.DeathsResponse;
import org.dce.ed.edsm.LogsResponse;
import org.dce.ed.edsm.ShowSystemResponse;
import org.dce.ed.edsm.SphereSystemsResponse;
import org.dce.ed.edsm.SystemResponse;
import org.dce.ed.edsm.SystemStationsResponse;
import org.dce.ed.edsm.TrafficResponse;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class EdsmClient {

	private static final boolean DEBUG_SPHERE_SYSTEMS = true;
	
    private static final String BASE_URL = "https://www.edsm.net";

    private final HttpClient client;
    private final Gson gson;
    
    // Last raw JSON returned by the EDSM API (for debugging / query tool)
    private volatile String lastRawJson;
    /** Last HTTP status from getSphereSystems (so retry logic can see 503). */
    private volatile int lastSphereSystemsStatus = 0;
    public EdsmClient() {
        this.client = HttpClient.newHttpClient();
        this.gson = new GsonBuilder().create();
    }
    
    /**
     * Returns the raw JSON from the most recent API call made via this client.
     * May be null if no call has been made yet, or if the last call failed.
     */
    public String getLastRawJson() {
        return lastRawJson;
    }

    public static void main(String args[]) throws IOException, InterruptedException {
    	EdsmClient client = new EdsmClient();
    	
    	BodiesResponse showBodies = client.showBodies("Ploea Eurl PA-Z b45-0");
    	
    	System.out.println(showBodies);
    }

    public <T> T get(String urlString, Class<T> clazz) throws IOException {
        HttpURLConnection conn = null;
        String body = "";

        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            String contentType = conn.getContentType();

            InputStream in;
            if (code >= 200 && code < 300) {
                in = conn.getInputStream();
            } else {
                in = conn.getErrorStream();
                if (in == null) {
                    throw new IOException("EDSM HTTP " + code + " with empty error body: " + urlString);
                }
            }

            body = readAll(in).trim();
            // Save raw JSON for debugging / query tool
            lastRawJson = body;

            if (body.isEmpty()) {
                throw new IOException("EDSM returned empty response (HTTP " + code + "): " + urlString);
            }

            // If EDSM (or a proxy in front of it) is having a bad day, we can get an HTML error page.
            // Don't try to feed that into Gson, or you'll get MalformedJsonException spam.
            if (!looksLikeJson(body, contentType)) {
                throw new IOException("EDSM returned non-JSON response (HTTP " + code + ") from "
                        + urlString + ": " + summarize(body));
            }

            JsonElement el;
            try {
                el = gson.fromJson(body, JsonElement.class);
            } catch (JsonParseException ex) {
                throw new IOException("EDSM returned invalid JSON (HTTP " + code + "): " + summarize(body), ex);
            }

            // Top-level JSON string, e.g. "API call limit exceeded"
            if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String msg = el.getAsString();
                throw new IOException("EDSM returned JSON string instead of object (HTTP " + code + "): " + msg);
            }

            // Some endpoints sometimes return a top-level array.
            if (el != null && el.isJsonArray() && !clazz.isArray()) {
                if (clazz == BodiesResponse.class) {
                    BodiesResponse br = new BodiesResponse();
                    br.bodies = gson.fromJson(el, new TypeToken<List<BodiesResponse.Body>>() {}.getType());
                    if (br.bodies != null) {
                        br.bodyCount = br.bodies.size();
                    }
                    @SuppressWarnings("unchecked")
                    T t = (T) br;
                    return t;
                }

                throw new IOException("EDSM returned JSON array where an object was expected (HTTP " + code
                        + "): " + clazz.getSimpleName() + " from " + urlString + " => " + summarize(body));
            }

            return gson.fromJson(el, clazz);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static boolean looksLikeJson(String body, String contentType) {
        if (body == null) {
            return false;
        }

        String s = body.trim();
        if (s.isEmpty()) {
            return false;
        }

        // Quick body sniff (works even when Content-Type is wrong/missing)
        char c = s.charAt(0);
        if (c == '{' || c == '[' || c == '"') {
            return true;
        }

        // Common HTML starts
        if (c == '<') {
            return false;
        }

        // If content-type explicitly claims JSON, allow it.
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            // Examples: application/json; charset=utf-8
            if (ct.contains("application/json") || ct.contains("text/json") || ct.contains("+json")) {
                return true;
            }
        }

        return false;
    }
    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String summarize(String s) {
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > 240) {
            return s.substring(0, 240) + "...";
        }
        return s;
    }
    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ----------------- System-level -----------------

    public SystemResponse getSystem(String name) throws IOException, InterruptedException {
        String url = BASE_URL + "/api-v1/system"
                + "?showId=1&showCoordinates=1&showPermit=1"
        		+ "&showInformation=1"
                + "&systemName=" + encode(name);
        return get(url, SystemResponse.class);
    }

    public SystemResponse[] getSystems(String... names) throws IOException, InterruptedException {
        String joined = String.join(",", names);
        String url = BASE_URL + "/api-v1/systems"
                + "?showId=1&showCoordinates=1&showPermit=1"
                + "&systemName=" + encode(joined);
        return get(url, SystemResponse[].class);
    }

    /**
     * Richer single-system view (info + primary star, still no stations).
     */
    public ShowSystemResponse showSystem(String systemName) throws IOException, InterruptedException {
        String url = BASE_URL + "/api-v1/system"
                + "?systemName=" + encode(systemName)
                + "&showId=1"
                        		+ "&showInformation=1"
                + "&showCoordinates=1"
                + "&showPermit=1"
                + "&showInformation=1"
                + "&showPrimaryStar=1";

        return get(url, ShowSystemResponse.class);
    }

    /**
     * Sphere search by center system name. EDSM accepts either systemName or x/y/z;
     * the systemName form often works when the coordinate form returns 503.
     */
    public SphereSystemsResponse[] sphereSystemsByName(String centerSystemName, int radiusLy)
            throws IOException, InterruptedException {
        if (centerSystemName == null || centerSystemName.trim().isEmpty()) {
            return new SphereSystemsResponse[0];
        }
        String url = BASE_URL + "/api-v1/sphere-systems"
                + "?systemName=" + encode(centerSystemName.trim())
                + "&radius=" + Math.min(radiusLy, 100)
                + "&showCoordinates=1&showId=1&showInformation=1";
        return getSphereSystemsWithRetry(url);
    }

    public SphereSystemsResponse[] sphereSystems(double x, double y, double z, int radiusLy)
            throws IOException, InterruptedException {

        // First try the official EDSM endpoint (retry on 503 - EDSM is often temporarily unavailable)
        String url = BASE_URL + "/api-v1/sphere-systems"
                + "?x=" + x
                + "&y=" + y
                + "&z=" + z
                + "&radius=" + Math.min(radiusLy, 100)
                + "&showCoordinates=1&showId=1&showInformation=1";

        SphereSystemsResponse[] result = getSphereSystemsWithRetry(url);
        if (result == null || result.length == 0) {
            return new SphereSystemsResponse[0];
        }
        return result;
    }

    // ----------------- Bodies -----------------

    public BodiesResponse showBodies(String systemName) throws IOException, InterruptedException {
        String url = BASE_URL + "/api-system-v1/bodies?systemName=" + encode(systemName)
		+ "&showInformation=1";
        return get(url, BodiesResponse.class);
    }

    public BodiesResponse showBodies(long systemId) throws IOException, InterruptedException {
        String url = BASE_URL + "/api-system-v1/bodies?systemId=" + systemId
        		+ "&showInformation=1";
        return get(url, BodiesResponse.class);
    }

    // ----------------- Stations (new) -----------------

    /**
     * Get information about stations in a system (not including fleet carriers).
     * https://www.edsm.net/api-system-v1/stations
     */
    public SystemStationsResponse getSystemStations(String systemName)
            throws IOException, InterruptedException {

        String url = BASE_URL + "/api-system-v1/stations"
                + "?systemName=" + encode(systemName);
        return get(url, SystemStationsResponse.class);
    }

    // ----------------- Traffic / deaths -----------------

    public TrafficResponse showTraffic(String systemName) throws IOException, InterruptedException {
        String url = BASE_URL + "/api-system-v1/traffic?systemName=" + encode(systemName);
        return get(url, TrafficResponse.class);
    }

    public DeathsResponse showDeaths(String systemName) throws IOException, InterruptedException {
        String url = BASE_URL + "/api-system-v1/deaths?systemName=" + encode(systemName);
        return get(url, DeathsResponse.class);
    }

    // ----------------- Logs (system-level & commander-level) -----------------

    /**
     * System logs by system name (public, no API key).
     */
    public LogsResponse systemLogs(String apiKey, String commanderName, String systemName)
            throws IOException, InterruptedException {

        String url = BASE_URL + "/api-logs-v1/get-logs"
                + "?commanderName=" + encode(commanderName)
                + "&apiKey=" + encode(apiKey)
                + "&systemName=" + encode(systemName)
                + "&showId=1";

        return get(url, LogsResponse.class);
    }

    /**
     * Commander logs: requires BOTH commanderName and apiKey.
     */
    public LogsResponse getCmdrLogs(String apiKey, String commanderName)
            throws IOException, InterruptedException {

        String url = BASE_URL + "/api-logs-v1/get-logs"
                + "?commanderName=" + encode(commanderName)
                + "&apiKey=" + encode(apiKey)
                + "&showId=1";
        return get(url, LogsResponse.class);
    }

    /**
     * Commander last position.
     */
    public CmdrLastPositionResponse getCmdrLastPosition(String apiKey, String commanderName)
            throws IOException, InterruptedException {

        String url = BASE_URL + "/api-logs-v1/get-position"
                + "?commanderName=" + encode(commanderName)
                + "&apiKey=" + encode(apiKey)
                + "&showId=1&showCoordinates=1";
        return get(url, CmdrLastPositionResponse.class);
    }

    // ----------------- Commander-specific (ranks, credits) -----------------

    public CmdrRanksResponse getCmdrRanks(String apiKey, String commanderName)
            throws IOException, InterruptedException {

        String url = BASE_URL + "/api-commander-v1/get-ranks"
                + "?commanderName=" + encode(commanderName)
                + "&apiKey=" + encode(apiKey);
        return get(url, CmdrRanksResponse.class);
    }

    public CmdrCreditsResponse getCmdrCredits(String apiKey, String commanderName)
            throws IOException, InterruptedException {

        String url = BASE_URL + "/api-commander-v1/get-credits"
                + "?commanderName=" + encode(commanderName)
                + "&apiKey=" + encode(apiKey);
        return get(url, CmdrCreditsResponse.class);
    }

    /**
     * Sphere-systems endpoint is a bit inconsistent in practice:
     * sometimes it returns a JSON array, sometimes a single object,
     * and occasionally an error object. This helper normalizes that
     * into a SphereSystemsResponse[] so callers don't have to care.
     */
    private static final int SPHERE_RETRY_ATTEMPTS = 3;
    private static final int SPHERE_RETRY_DELAY_MS = 2000;

    private SphereSystemsResponse[] getSphereSystemsWithRetry(String url) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < SPHERE_RETRY_ATTEMPTS; attempt++) {
            SphereSystemsResponse[] result = getSphereSystems(url);
            if (lastSphereSystemsStatus != 503 || attempt == SPHERE_RETRY_ATTEMPTS - 1) {
                return result;
            }
            try {
                Thread.sleep(SPHERE_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during retry", e);
            }
        }
        return new SphereSystemsResponse[0];
    }

    private SphereSystemsResponse[] getSphereSystems(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "EDO-Tool")
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        lastSphereSystemsStatus = resp.statusCode();
        String body = resp.body();

        if (DEBUG_SPHERE_SYSTEMS) {
            System.out.println("[EDSM] sphere-systems URL: " + url);
            System.out.println("[EDSM] sphere-systems HTTP " + resp.statusCode());
            System.out.println("[EDSM] sphere-systems raw body: " + body);
        }

        if (body == null || body.isEmpty()) {
            return new SphereSystemsResponse[0];
        }

        body = body.trim();

        // Fast-path: looks like an array already
        if (!body.isEmpty() && body.charAt(0) == '[') {
            try {
                return gson.fromJson(body, SphereSystemsResponse[].class);
            } catch (Exception e) {
                if (DEBUG_SPHERE_SYSTEMS) {
                    e.printStackTrace();
                }
                throw new IOException("Failed to parse sphere-systems array: " + e.getMessage(), e);
            }
        }

        // Otherwise parse and normalize
        try {
            JsonElement root = JsonParser.parseString(body);

            if (root.isJsonArray()) {
                JsonArray arr = root.getAsJsonArray();
                return gson.fromJson(arr, SphereSystemsResponse[].class);
            }

            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();

                // If it has a "systems" array, prefer that.
                if (obj.has("systems") && obj.get("systems").isJsonArray()) {
                    JsonArray systems = obj.getAsJsonArray("systems");
                    return gson.fromJson(systems, SphereSystemsResponse[].class);
                }

                // If it looks like a single system (has at least name or coords or distance), wrap it.
                boolean looksLikeSystem =
                        obj.has("name") || obj.has("id") || obj.has("coords") || obj.has("distance");

                if (looksLikeSystem) {
                    SphereSystemsResponse single = gson.fromJson(obj, SphereSystemsResponse.class);
                    return new SphereSystemsResponse[]{single};
                }

                // Anything else (e.g. { "msg": "..."} ) => treat as "no systems"
                if (DEBUG_SPHERE_SYSTEMS) {
                    System.out.println("[EDSM] sphere-systems unexpected object, treating as empty: " + obj);
                }
                return new SphereSystemsResponse[0];
            }
        } catch (Exception e) {
            if (DEBUG_SPHERE_SYSTEMS) {
                e.printStackTrace();
            }
            throw new IOException("Unexpected EDSM sphere-systems response: " + body, e);
        }

        // Should not reach here, but just in case:
        if (DEBUG_SPHERE_SYSTEMS) {
            System.out.println("[EDSM] sphere-systems unknown structure, treating as empty: " + body);
        }
        return new SphereSystemsResponse[0];
    }

    /**
     * Merge EDSM bodies information into the current SystemState.
     *
     * We treat BodiesResponse as the single source of truth for what EDSM
     * knows about this system and enrich our local SystemState where we
     * have gaps (bodyCount, discovery commander, and physical attributes).
     *
     * This WILL create new BodyInfo entries for bodies that only exist in
     * EDSM. Those are given synthetic negative bodyIds to avoid clashing
     * with Journal BodyIDs.
     */
    public void mergeBodiesFromEdsm(SystemState state, BodiesResponse edsm) {
        if (state == null || edsm == null || edsm.bodies == null || edsm.bodies.isEmpty()) {
            return;
        }

        if (state.getTotalBodies() == null) {
            state.setTotalBodies(edsm.bodies.size());
        }

        Map<Integer, BodyInfo> local = state.getBodies();
        if (local == null) {
            return;
        }

        // EDSM "parents" uses {"Star": <bodyId>} where that id corresponds to the same numeric id in the body list.
        Map<Integer, String> starNameById = new HashMap<>();
        for (BodiesResponse.Body b : edsm.bodies) {
            if (b == null || b.type == null) {
                continue;
            }
            if ("Star".equalsIgnoreCase(b.type)) {
                Integer id = safeToInt(b.id);
                if (id != null && b.name != null && !b.name.isEmpty()) {
                    starNameById.put(id, b.name);
                }
            }
        }

        for (BodiesResponse.Body remote : edsm.bodies) {
            if (remote == null || remote.name == null || remote.name.isEmpty()) {
                continue;
            }

            Integer bodyId = safeToInt(remote.id);
            if (bodyId == null) {
                continue;
            }

            BodyInfo info = local.get(bodyId);
            if (info == null) {
                info = new BodyInfo();
                info.setBodyId(bodyId);
                local.put(bodyId, info);
            }

            // Identity / names
            if (info.getBodyName() == null || info.getBodyName().isEmpty()) {
                info.setBodyName(remote.name);
            }

            String sysName = state.getSystemName();
            if ((sysName == null || sysName.isEmpty()) && edsm.name != null && !edsm.name.isEmpty()) {
                sysName = edsm.name;
                state.setSystemName(sysName);
            }

            if (sysName != null && !sysName.isEmpty()) {
                info.setStarSystem(sysName);

                if (info.getShortName() == null || info.getShortName().isEmpty()) {
                    info.setBodyShortName(state.computeShortName(sysName, remote.name));
                }
            }

            // Best-effort: copy system starPos onto each body (EDSM doesn't provide it per body)
            if (info.getStarPos() == null && state.getStarPos() != null) {
                info.setStarPos(state.getStarPos());
            }

            // Fields you said were missing, but DO exist in the EDSM payload
            if (remote.gravity != null) {
                info.setGravityMS(remote.gravity);
            }
            if (remote.isLandable != null) {
                info.setLandable(remote.isLandable.booleanValue());
            }
            if (remote.radius != null) {
                info.setRadius(remote.radius);
            }
            if (remote.getSurfacePressure() != null) {
                info.setSurfacePressure(remote.getSurfacePressure());
            }

            // Planet class from EDSM subType (only makes sense for planets)
            if ("Planet".equalsIgnoreCase(remote.type)
                    && remote.subType != null
                    && !remote.subType.isEmpty()) {
                info.setPlanetClass(remote.subType);

                // If you want a display fallback similar to your ScanEvent logic
                if (info.getAtmoOrType() == null || info.getAtmoOrType().isEmpty()) {
                    info.setAtmoOrType(remote.subType);
                }
            }

            // Parent star: parents[].Star contains the star id (same numeric id as in the list)
            if ((info.getParentStar() == null || info.getParentStar().isEmpty())
                    && remote.parents != null
                    && !remote.parents.isEmpty()) {
                Integer parentStarId = null;
                for (BodiesResponse.ParentRef p : remote.parents) {
                    if (p != null && p.Star != null) {
                        parentStarId = p.Star;
                        break;
                    }
                }
                if (parentStarId != null) {
                    String parentStarName = starNameById.get(parentStarId);
                    if (parentStarName != null && !parentStarName.isEmpty()) {
                        info.setParentStar(parentStarName);
                    }
                }
            }

            // High-value heuristic (same as your ScanEvent-based logic)
            String pc = toLower(remote.subType);
            String tf = toLower(remote.terraformingState);
            boolean highValue =
                    pc.contains("earth-like")
                            || pc.contains("water world")
                            || pc.contains("ammonia world")
                            || tf.contains("terraformable");
            info.setHighValue(highValue);

            // NOTE:
            // hasBio / hasGeo are NOT in this EDSM body payload -> must come from journal events.
            // nebula is NOT in this payload -> must come from your own system classification.
        }
    }

    private static Integer safeToInt(long v) {
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            return null;
        }
        return (int) v;
    }

    private static String toLower(String s) {
        return (s == null) ? "" : s.toLowerCase(Locale.ROOT);
    }
    private static BodyInfo findBodyByName(Map<Integer, BodyInfo> bodies, String name) {
        for (BodyInfo b : bodies.values()) {
            if (b != null && name.equals(b.getBodyName())) {
                return b;
            }
        }
        return null;
    }

    /** Utility to normalize names */
    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }



}
