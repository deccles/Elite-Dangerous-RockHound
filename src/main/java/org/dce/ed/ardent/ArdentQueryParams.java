package org.dce.ed.ardent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional query parameters for Ardent trade endpoints.
 *
 * @see <a href="https://github.com/iaincollins/ardent-api">Ardent API</a>
 */
public final class ArdentQueryParams {

    private Integer minVolume;
    private Integer minPrice;
    private Integer maxPrice;
    private Integer maxDistance;
    private Integer maxDaysAgo;
    /** {@code true} = fleet carriers only, {@code false} = exclude, {@code null} = both. */
    private Boolean fleetCarriers;

    public ArdentQueryParams minVolume(int v) {
        minVolume = Integer.valueOf(v);
        return this;
    }

    public ArdentQueryParams minPrice(int v) {
        minPrice = Integer.valueOf(v);
        return this;
    }

    public ArdentQueryParams maxPrice(int v) {
        maxPrice = Integer.valueOf(v);
        return this;
    }

    public ArdentQueryParams maxDistance(int v) {
        maxDistance = Integer.valueOf(v);
        return this;
    }

    public ArdentQueryParams maxDaysAgo(int v) {
        maxDaysAgo = Integer.valueOf(v);
        return this;
    }

    public ArdentQueryParams fleetCarriers(Boolean v) {
        fleetCarriers = v;
        return this;
    }

    public String toQueryString() {
        List<String> parts = new ArrayList<>();
        append(parts, "minVolume", minVolume);
        append(parts, "minPrice", minPrice);
        append(parts, "maxPrice", maxPrice);
        append(parts, "maxDistance", maxDistance);
        append(parts, "maxDaysAgo", maxDaysAgo);
        if (fleetCarriers != null) {
            parts.add("fleetCarriers=" + (fleetCarriers.booleanValue() ? "1" : "0"));
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "?" + String.join("&", parts);
    }

    private static void append(List<String> parts, String key, Integer value) {
        if (value != null) {
            parts.add(key + "=" + value.intValue());
        }
    }

    public static String encodePathSegment(String s) {
        if (s == null) {
            return "";
        }
        return URLEncoder.encode(s.trim(), StandardCharsets.UTF_8).replace("+", "%20");
    }
}
