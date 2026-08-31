package org.dce.ed.route.pacing;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Direct EDSM {@code showBodies} GET. Does not read or write EDO's query cache or request gate.
 * Each call uses a unique URL so HTTP/CDN caches cannot reuse a previous response.
 */
public final class EdsmPacingBodiesHttp implements EdsmPacingExperiment.BodiesQuery {
    private static final String BODIES_URL = "https://www.edsm.net/api-system-v1/bodies?systemName=";
    private static final AtomicLong NONCE = new AtomicLong();

    @Override
    public EdsmPacingExperiment.QueryResult query(String systemName) {
        String name = systemName != null ? systemName.trim() : "";
        long started = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            String url = bodiesUrl(name, NONCE.incrementAndGet());
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("Pragma", "no-cache");
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = in != null ? new String(in.readAllBytes(), StandardCharsets.UTF_8) : "";
            EdsmPacingExperiment.Outcome outcome = EdsmPacingExperiment.classify(code, body);
            String detail = outcome == EdsmPacingExperiment.Outcome.SUCCESS
                    ? null
                    : summarize(body);
            return new EdsmPacingExperiment.QueryResult(name, outcome, code,
                    System.currentTimeMillis() - started, detail);
        } catch (IOException ex) {
            int code = 0;
            try {
                if (conn != null) {
                    code = conn.getResponseCode();
                }
            } catch (IOException ignored) {
                // keep 0
            }
            String message = ex.getMessage() != null ? ex.getMessage() : "";
            EdsmPacingExperiment.Outcome outcome = EdsmPacingExperiment.classify(code, message);
            if (outcome == EdsmPacingExperiment.Outcome.SUCCESS) {
                outcome = EdsmPacingExperiment.Outcome.ERROR;
            }
            return new EdsmPacingExperiment.QueryResult(name, outcome, code,
                    System.currentTimeMillis() - started, ex.getClass().getSimpleName() + " — " + message);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    static String bodiesUrl(String systemName, long nonce) {
        String encoded = URLEncoder.encode(systemName != null ? systemName : "", StandardCharsets.UTF_8);
        return BODIES_URL + encoded + "&showInformation=1&edoPacing=" + nonce;
    }

    private static String summarize(String body) {
        if (body == null) {
            return "";
        }
        String compact = body.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > 180 ? compact.substring(0, 180) + "..." : compact;
    }
}
