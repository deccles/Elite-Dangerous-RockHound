package org.dce.ed.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves journal/cargo material names to the best matching entry in {@link GalacticAveragePrices}.
 *
 * This intentionally avoids commodity-specific special casing. Instead it uses:
 *  1) direct normalized-key match
 *  2) normalized-key match with common abbreviation folding ("temperature" <-> "temp")
 *  3) conservative fuzzy match (Levenshtein) against known normalized keys
 *
 * Results are cached per input string.
 */
public class MaterialNameMatcher {

    private final GalacticAveragePrices prices;

    /**
     * Maps a "variant normalized key" (after abbreviation folding) to a real normalized key
     * present in the loaded price list.
     */
    private final Map<String, String> variantToRealKey = new HashMap<>();

    /**
     * Real normalized keys from the loaded price list.
     */
    private final List<String> realKeys = new ArrayList<>();

    /**
     * Cache input string -> resolved real key (or "" if none).
     */
    private final Map<String, String> resolvedCache = new HashMap<>();

    public MaterialNameMatcher(GalacticAveragePrices prices) {
        this.prices = prices;

        Set<String> keys = new HashSet<>(prices.getAllNormalizedKeys());
        realKeys.addAll(keys);

        // Build variant map so common abbreviation differences still resolve via exact lookup.
        for (String k : keys) {
            // The key itself.
            variantToRealKey.put(k, k);

            // Fold "temperature" -> "temp"
            String folded = foldTemperatureToTemp(k);
            variantToRealKey.putIfAbsent(folded, k);

            // Fold "temp" -> "temperature"
            String expanded = foldTempToTemperature(k);
            variantToRealKey.putIfAbsent(expanded, k);
        }
    }

    public int lookupAvgSell(String rawName, String localizedName) {
        String resolvedKey = resolveKey(rawName, localizedName);
        if (resolvedKey.isEmpty()) {
            return 0;
        }
        return prices.getAvgSellCrPerTon(resolvedKey).orElse(0);
    }

    public String resolveKey(String rawName, String localizedName) {
        String bestInput = pickBestInput(rawName, localizedName);
        if (bestInput.isBlank()) {
            return "";
        }

        String cached = resolvedCache.get(bestInput);
        if (cached != null) {
            return cached;
        }

        // 1) Direct normalized-key match(s)
        String norm = GalacticAveragePrices.normalizeMaterialKey(bestInput);
        String direct = variantToRealKey.get(norm);
        if (direct != null) {
            resolvedCache.put(bestInput, direct);
            return direct;
        }

        // 2) Abbreviation folding on the input
        direct = variantToRealKey.get(foldTemperatureToTemp(norm));
        if (direct != null) {
            resolvedCache.put(bestInput, direct);
            return direct;
        }
        direct = variantToRealKey.get(foldTempToTemperature(norm));
        if (direct != null) {
            resolvedCache.put(bestInput, direct);
            return direct;
        }

        // 3) Conservative fuzzy match against known real keys
        String fuzzy = bestFuzzyMatch(norm);
        resolvedCache.put(bestInput, fuzzy);
        return fuzzy;
    }

    private static String pickBestInput(String rawName, String localizedName) {
        if (localizedName != null && !localizedName.isBlank()) {
            return localizedName;
        }
        if (rawName != null) {
            return rawName;
        }
        return "";
    }

    private String bestFuzzyMatch(String norm) {
        if (norm.isBlank()) {
            return "";
        }

        // Compare on a folded representation to reduce distance when only abbrev differs.
        String normFolded = foldTemperatureToTemp(norm);

        int bestDist = Integer.MAX_VALUE;
        String bestKey = "";

        for (String candidate : realKeys) {
            // Use folded comparison form for both sides
            String candFolded = foldTemperatureToTemp(candidate);

            if (!roughCompatible(normFolded, candFolded)) {
                continue;
            }

            int d = levenshtein(normFolded, candFolded);
            if (d < bestDist) {
                bestDist = d;
                bestKey = candidate;
            }
        }

        int len = normFolded.length();
        int allowed = Math.max(2, len / 6); // ~16% edits, at least 2
        if (bestDist <= allowed) {
            return bestKey;
        }
        return "";
    }

    private static boolean roughCompatible(String a, String b) {
        int min = Math.min(a.length(), b.length());
        if (min < 5) {
            return false;
        }
        int sharedPrefix = 0;
        for (int i = 0; i < min; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                break;
            }
            sharedPrefix++;
        }
        return sharedPrefix >= (min / 3);
    }

    private static String foldTemperatureToTemp(String normalizedKey) {
        if (normalizedKey == null) {
            return "";
        }
        return normalizedKey.replace("temperature", "temp");
    }

    private static String foldTempToTemperature(String normalizedKey) {
        if (normalizedKey == null) {
            return "";
        }
        // Only expand "temp" when it appears as a word fragment that typically came from "temperature".
        // On normalized keys we don't have separators, so this is necessarily heuristic.
        // This still helps for cases like "lowtempdiamonds" vs "lowtemperaturediamonds".
        return normalizedKey.replace("temp", "temperature");
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();

        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];

        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                cur[j] = Math.min(
                        Math.min(cur[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }

            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }

        return prev[m];
    }
}
