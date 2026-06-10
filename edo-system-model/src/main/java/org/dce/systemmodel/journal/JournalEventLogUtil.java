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
        return dedupeScansByDesignation(
                systemName, latestPerBodyId(forSystem(systemName, stripDuplicateArrivalStarScans(systemName, log))));
    }

    /**
     * When journal {@code BodyID 0} is present for the arrival star, drop EDSM duplicate star scans that reused
     * the same body name with a non-zero id. Also drops a system-name arrival duplicate when a letter-branch
     * {@code A} star exists (OR-V / TV-A style primaries named {@code … A}, not the system name).
     */
    public static List<JournalRecord> stripDuplicateArrivalStarScans(String systemName, List<JournalRecord> log) {
        if (systemName == null || systemName.isBlank() || log == null || log.isEmpty()) {
            return log != null ? log : List.of();
        }
        String sys = systemName.trim();
        boolean hasArrivalAtZero = false;
        boolean hasLetterBranchA = false;
        for (JournalRecord r : log) {
            if (!(r instanceof ScanRecord s)) {
                continue;
            }
            if (s.bodyId() == 0 && isStellarScan(s) && s.bodyName() != null
                    && s.bodyName().trim().equalsIgnoreCase(sys)) {
                hasArrivalAtZero = true;
            }
            if (isStellarScan(s) && s.bodyName() != null && "a".equals(stellarBranchDesignationKey(systemName, s))) {
                hasLetterBranchA = true;
            }
        }
        if (!hasArrivalAtZero && !hasLetterBranchA) {
            return log;
        }
        List<JournalRecord> out = new ArrayList<>(log.size());
        for (JournalRecord r : log) {
            if (r instanceof ScanRecord s && isStellarScan(s) && s.bodyName() != null) {
                String name = s.bodyName().trim();
                if (hasArrivalAtZero && s.bodyId() != 0 && name.equalsIgnoreCase(sys)) {
                    continue;
                }
                if (hasLetterBranchA && name.equalsIgnoreCase(sys)) {
                    continue;
                }
            }
            out.add(r);
        }
        return List.copyOf(out);
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
                    String starKey = "star:" + stellarBranchDesignationKey(systemName, s);
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

    /**
     * Collapse key for stellar scans: branch letter ({@code a}, {@code b}, …) or the arrival star
     * (system name with no branch suffix).
     */
    static String stellarBranchDesignationKey(String systemName, ScanRecord s) {
        if (s == null || s.bodyName() == null || s.bodyName().isBlank()) {
            return "unknown";
        }
        String dk = designationKey(systemName, s.bodyName());
        if (dk.isEmpty()) {
            return "arrival";
        }
        String prefix = systemName != null ? systemName.trim().toLowerCase(Locale.ROOT) : "";
        if (!prefix.isEmpty() && dk.equals(prefix)) {
            return "arrival";
        }
        if (dk.length() == 1 && Character.isLetter(dk.charAt(0))) {
            return dk;
        }
        return dk;
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
