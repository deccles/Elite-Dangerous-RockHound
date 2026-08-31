package org.dce.ed.route.pacing;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** Last-used EDSM pacing experiment batches and system list. */
public final class EdsmPacingExperimentSettings {
    private static final String PREF_BATCHES = "batches";
    private static final String PREF_SYSTEMS = "systems";

    public record BatchSpec(int count, int concurrent, int restSeconds, int delayMs, int repeats) {
        public BatchSpec {
            count = clamp(count, 1, 200);
            concurrent = clamp(concurrent, 1, 18);
            restSeconds = clamp(restSeconds, 0, 120);
            delayMs = clamp(delayMs, 0, 5_000);
            repeats = clamp(repeats, 1, 99);
        }

        public BatchSpec(int count, int concurrent, int restSeconds, int delayMs) {
            this(count, concurrent, restSeconds, delayMs, 1);
        }

        public String toCsv() {
            return count + "," + concurrent + "," + restSeconds + "," + delayMs + "," + repeats;
        }

        public static BatchSpec parse(String csv) {
            if (csv == null || csv.isBlank()) {
                return null;
            }
            String[] parts = csv.split(",", -1);
            if (parts.length != 4 && parts.length != 5) {
                return null;
            }
            try {
                int parsedRepeats = parts.length == 5 ? Integer.parseInt(parts[4].trim()) : 1;
                return new BatchSpec(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()),
                        Integer.parseInt(parts[3].trim()),
                        parsedRepeats);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private EdsmPacingExperimentSettings() {
    }

    public static String encodeBatches(List<BatchSpec> batches) {
        if (batches == null || batches.isEmpty()) {
            return "";
        }
        StringBuilder encoded = new StringBuilder();
        for (BatchSpec batch : batches) {
            if (batch == null) {
                continue;
            }
            if (encoded.length() > 0) {
                encoded.append(';');
            }
            encoded.append(batch.toCsv());
        }
        return encoded.toString();
    }

    public static List<BatchSpec> decodeBatches(String encoded) {
        List<BatchSpec> batches = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return batches;
        }
        for (String token : encoded.split(";")) {
            BatchSpec parsed = BatchSpec.parse(token);
            if (parsed != null) {
                batches.add(parsed);
            }
        }
        return batches;
    }

    public static void save(List<BatchSpec> batches, String systemsText) {
        Preferences prefs = prefs();
        prefs.put(PREF_BATCHES, encodeBatches(batches));
        if (systemsText != null) {
            prefs.put(PREF_SYSTEMS, systemsText);
        }
    }

    public static List<BatchSpec> loadBatches() {
        return decodeBatches(prefs().get(PREF_BATCHES, ""));
    }

    public static String loadSystemsText() {
        String text = prefs().get(PREF_SYSTEMS, "");
        return text == null || text.isBlank() ? null : text;
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(EdsmPacingExperimentSettings.class);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
