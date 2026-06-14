package org.dce.ed.ardent;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Minimal HTTP client for the public {@link #BASE_URL} Ardent API (EDDN-backed market data).
 */
public final class ArdentClient {

    public static final String BASE_URL = "https://api.ardent-insight.com/v2";

    private volatile String lastUrl;
    private volatile String lastRawJson;

    public String getLastUrl() {
        return lastUrl;
    }

    public String getLastRawJson() {
        return lastRawJson;
    }

    public String get(String pathAndQuery) throws IOException {
        String path = pathAndQuery != null ? pathAndQuery.trim() : "";
        if (path.isEmpty()) {
            throw new IOException("Path is required");
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String urlString = BASE_URL + path;
        lastUrl = urlString;

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(in).trim();
            lastRawJson = body;

            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " from " + urlString
                        + (body.isBlank() ? "" : ": " + summarize(body)));
            }
            if (body.isEmpty()) {
                throw new IOException("Empty response from " + urlString);
            }
            if (!looksLikeJson(body)) {
                throw new IOException("Non-JSON response from " + urlString + ": " + summarize(body));
            }
            return body;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public String getVersion() throws IOException {
        return get("/version");
    }

    public String getStats() throws IOException {
        return get("/stats");
    }

    public String getCommoditySummary(String commodityName) throws IOException {
        return get("/commodity/name/" + ArdentQueryParams.encodePathSegment(commodityName));
    }

    public String getCommodityImports(String commodityName, ArdentQueryParams params) throws IOException {
        String q = params != null ? params.toQueryString() : "";
        return get("/commodity/name/" + ArdentQueryParams.encodePathSegment(commodityName) + "/imports" + q);
    }

    public String getCommodityExports(String commodityName, ArdentQueryParams params) throws IOException {
        String q = params != null ? params.toQueryString() : "";
        return get("/commodity/name/" + ArdentQueryParams.encodePathSegment(commodityName) + "/exports" + q);
    }

    public String getSystem(String systemName) throws IOException {
        return get("/system/name/" + ArdentQueryParams.encodePathSegment(systemName));
    }

    public String getSystemCommodities(String systemName) throws IOException {
        return get("/system/name/" + ArdentQueryParams.encodePathSegment(systemName) + "/commodities");
    }

    public String getSystemCommodity(String systemName, String commodityName, ArdentQueryParams params)
            throws IOException {
        String q = params != null ? params.toQueryString() : "";
        return get("/system/name/" + ArdentQueryParams.encodePathSegment(systemName)
                + "/commodity/name/" + ArdentQueryParams.encodePathSegment(commodityName) + q);
    }

    public String getNearbyImports(String systemName, String commodityName, ArdentQueryParams params)
            throws IOException {
        String q = params != null ? params.toQueryString() : "";
        return get("/system/name/" + ArdentQueryParams.encodePathSegment(systemName)
                + "/commodity/name/" + ArdentQueryParams.encodePathSegment(commodityName)
                + "/nearby/imports" + q);
    }

    public String getNearbyExports(String systemName, String commodityName, ArdentQueryParams params)
            throws IOException {
        String q = params != null ? params.toQueryString() : "";
        return get("/system/name/" + ArdentQueryParams.encodePathSegment(systemName)
                + "/commodity/name/" + ArdentQueryParams.encodePathSegment(commodityName)
                + "/nearby/exports" + q);
    }

    public String getMarketCommodity(long marketId, String commodityName) throws IOException {
        return get("/market/" + marketId + "/commodity/name/"
                + ArdentQueryParams.encodePathSegment(commodityName));
    }

    private static boolean looksLikeJson(String body) {
        if (body.isEmpty()) {
            return false;
        }
        char c = body.charAt(0);
        if (c == '{' || c == '[') {
            try {
                JsonElement el = JsonParser.parseString(body);
                return el != null;
            } catch (Exception ex) {
                return false;
            }
        }
        return false;
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String summarize(String body) {
        String oneLine = body.replace('\r', ' ').replace('\n', ' ').trim();
        if (oneLine.length() > 200) {
            return oneLine.substring(0, 200) + "…";
        }
        return oneLine;
    }
}
