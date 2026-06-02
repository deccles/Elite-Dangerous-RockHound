package org.dce.systemmodel.journal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Normalizes append-only journal logs before {@link org.dce.systemmodel.build.SystemModelBuilder} runs.
 */
public final class JournalEventLogUtil {

    private JournalEventLogUtil() {
    }

    /** Keeps rows whose {@code bodyName} belongs to this system (Scan + ScanBaryCentre). */
    public static List<JournalRecord> forSystem(String systemName, List<JournalRecord> log) {
        if (log == null || log.isEmpty()) {
            return List.of();
        }
        if (systemName == null || systemName.isBlank()) {
            return List.copyOf(log);
        }
        String prefix = systemName.trim().toLowerCase(Locale.ROOT);
        List<JournalRecord> out = new ArrayList<>();
        for (JournalRecord r : log) {
            String name = bodyName(r);
            if (name == null || name.isBlank()) {
                continue;
            }
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(r);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Latest row per journal id. {@code Scan} and {@code ScanBaryCentre} use separate key spaces — planet
     * body id 28 and {@code Null:28} barycentre must not overwrite each other.
     */
    public static List<JournalRecord> latestPerBodyId(List<JournalRecord> log) {
        if (log == null || log.isEmpty()) {
            return List.of();
        }
        Map<String, JournalRecord> latest = new LinkedHashMap<>();
        for (JournalRecord r : log) {
            String key = recordKey(r);
            if (key == null) {
                continue;
            }
            JournalRecord existing = latest.get(key);
            if (existing == null || isNewer(r, existing)) {
                latest.put(key, r);
            }
        }
        return List.copyOf(latest.values());
    }

    private static String recordKey(JournalRecord r) {
        if (r instanceof ScanRecord s) {
            return s.bodyId() < 0 ? null : "scan:" + s.bodyId();
        }
        if (r instanceof ScanBaryCentreRecord b) {
            return b.bodyId() < 0 ? null : "bary:" + b.bodyId();
        }
        return null;
    }

    public static List<JournalRecord> normalizeForSystemBuild(String systemName, List<JournalRecord> log) {
        return dedupeScansByDesignation(systemName, latestPerBodyId(forSystem(systemName, log)));
    }

    /**
     * Collapses duplicate {@link ScanRecord} rows for the same body designation (e.g. journal log body id 21 and
     * cache body id 55 both named {@code … 1}) while keeping separate {@link ScanBaryCentreRecord} rows.
     */
    public static List<JournalRecord> dedupeScansByDesignation(String systemName, List<JournalRecord> log) {
        if (log == null || log.isEmpty()) {
            return List.of();
        }
        Map<String, ScanBaryCentreRecord> baryById = new LinkedHashMap<>();
        Map<String, ScanRecord> scanByDesignation = new LinkedHashMap<>();
        List<JournalRecord> passthrough = new ArrayList<>();
        for (JournalRecord r : log) {
            if (r instanceof ScanBaryCentreRecord b) {
                if (b.bodyId() < 0) {
                    continue;
                }
                String key = "bary:" + b.bodyId();
                ScanBaryCentreRecord existing = baryById.get(key);
                if (existing == null || isNewer(r, existing)) {
                    baryById.put(key, b);
                }
                continue;
            }
            if (r instanceof ScanRecord s) {
                if (s.bodyId() < 0) {
                    continue;
                }
                if (isStellarScan(s)) {
                    String starKey = "star:" + s.bodyId();
                    ScanRecord existing = scanByDesignation.get(starKey);
                    if (existing == null || preferScan(s, existing)) {
                        scanByDesignation.put(starKey, s);
                    }
                    continue;
                }
                String dk = designationKey(systemName, s.bodyName());
                if (dk.isEmpty()) {
                    passthrough.add(s);
                    continue;
                }
                ScanRecord existing = scanByDesignation.get(dk);
                if (existing == null || preferScan(s, existing)) {
                    scanByDesignation.put(dk, s);
                }
                continue;
            }
            passthrough.add(r);
        }
        List<JournalRecord> out = new ArrayList<>(passthrough.size() + baryById.size() + scanByDesignation.size());
        out.addAll(passthrough);
        out.addAll(baryById.values());
        out.addAll(scanByDesignation.values());
        return List.copyOf(out);
    }

    private static boolean isStellarScan(ScanRecord s) {
        return "Star".equalsIgnoreCase(s.bodyType()) || s.bodyId() == 0;
    }

    private static boolean preferScan(ScanRecord candidate, ScanRecord incumbent) {
        int cmp = Integer.compare(scanRecordQuality(candidate), scanRecordQuality(incumbent));
        if (cmp != 0) {
            return cmp > 0;
        }
        return isNewer(candidate, incumbent);
    }

    private static int scanRecordQuality(ScanRecord s) {
        int score = 0;
        if (s.subType() != null && !s.subType().isBlank()) {
            score += 4;
            if (s.subType().toLowerCase(Locale.ROOT).contains("sudarsky")) {
                score += 2;
            }
        }
        if (s.parents() != null) {
            score += 2 * s.parents().size();
        }
        if (s.orbit() != null) {
            score += 2;
        }
        if (s.bodyType() != null && !s.bodyType().isBlank()) {
            score += 1;
        }
        return score;
    }

    static String designationKey(String systemName, String bodyName) {
        if (bodyName == null || bodyName.isBlank()) {
            return "";
        }
        String trimmed = bodyName.trim();
        String prefix = systemName != null ? systemName.trim() : "";
        if (!prefix.isEmpty() && trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
            String rest = trimmed.substring(prefix.length()).trim();
            if (!rest.isEmpty()) {
                return rest.toLowerCase(Locale.ROOT);
            }
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static boolean isNewer(JournalRecord a, JournalRecord b) {
        Instant ta = timestamp(a);
        Instant tb = timestamp(b);
        if (ta == null) {
            return false;
        }
        if (tb == null) {
            return true;
        }
        return ta.isAfter(tb);
    }

    private static Instant timestamp(JournalRecord r) {
        if (r instanceof ScanRecord s) {
            return s.timestamp();
        }
        if (r instanceof ScanBaryCentreRecord b) {
            return b.timestamp();
        }
        return null;
    }

    private static int bodyId(JournalRecord r) {
        if (r instanceof ScanRecord s) {
            return s.bodyId();
        }
        if (r instanceof ScanBaryCentreRecord b) {
            return b.bodyId();
        }
        return -1;
    }

    private static String bodyName(JournalRecord r) {
        if (r instanceof ScanRecord s) {
            return s.bodyName();
        }
        if (r instanceof ScanBaryCentreRecord b) {
            return b.bodyName();
        }
        return null;
    }
}
