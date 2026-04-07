package org.dce.ed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.dce.ed.SystemTabPanel.Row;
import org.dce.ed.exobiology.BodyAttributes;
import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.FirstBonusHelper;
import org.dce.ed.util.RingSummaryFormatter;
import org.dce.ed.util.SpanshBodyExobiologyInfo;
import org.dce.ed.util.SpanshLandmark;
import org.dce.ed.util.SpanshLandmarkCache;

final class BioTableBuilder {

    private BioTableBuilder() {
        // utility
    }

    /**
     * FSS / journal shows real exobiology on this body (contradicts Spansh “no biological signals” heuristics).
     */
    static boolean hasLocalBioEvidence(BodyInfo b) {
        if (b == null) {
            return false;
        }
        Integer sig = b.getNumberOfBioSignals();
        if (sig != null && sig.intValue() > 0) {
            return true;
        }
        if (b.getObservedBioDisplayNames() != null && !b.getObservedBioDisplayNames().isEmpty()) {
            return true;
        }
        if (b.getObservedGenusPrefixes() != null && !b.getObservedGenusPrefixes().isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * When true, hide exobiology-only UI that depends on Spansh’s exclude flag (no local contradiction).
     */
    static boolean spanshExobiologyExclusionActive(BodyInfo b) {
        if (b == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(b.getSpanshExcludeFromExobiology())) {
            return false;
        }
        return !hasLocalBioEvidence(b);
    }

    /**
     * Full system-tab body-row bio column text: remaining payout range (same rule as
     * {@link #computeBioHeaderSummary}) when any species are left, then {@code (Xm scanned)} for fully
     * sampled species; when nothing is scanned yet, appends FSS {@code (n signals)} if known.
     */
    static String formatBodyBioColumnText(BodyInfo b) {
        if (b == null) {
            return null;
        }
        RemainingClaimedCredits split = collectRemainingClaimedPayoutCredits(b);
        Integer fss = b.getNumberOfBioSignals();
        int fssN = (fss != null && fss.intValue() > 0) ? fss.intValue() : 0;

        if (split == null) {
            return fssN > 0 ? parentheticalSignals(fssN) : null;
        }

        long claimedSum = 0L;
        for (Long c : split.claimed) {
            if (c != null) {
                claimedSum += c.longValue();
            }
        }

        String remainingStr = null;
        if (!split.remaining.isEmpty()) {
            long[] range = bioPayoutRangeFromRemainingCredits(split.remaining, b.getNumberOfBioSignals());
            if (range != null) {
                remainingStr = formatMillionSummary(range[0], range[1]);
            }
        }

        StringBuilder sb = new StringBuilder();
        if (remainingStr != null && !remainingStr.isEmpty()) {
            sb.append(remainingStr);
        }
        if (claimedSum > 0L) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            String scanned = formatScannedCreditsParenthetical(claimedSum);
            if (scanned != null) {
                sb.append(scanned);
            }
        }
        if (claimedSum <= 0L && fssN > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(parentheticalSignals(fssN));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String parentheticalSignals(int n) {
        return "(" + n + (n == 1 ? " signal)" : " signals)");
    }

    /** e.g. {@code "(137M scanned)"} from summed credits at million scale. */
    private static String formatScannedCreditsParenthetical(long creditsSum) {
        String m = formatMillionSummary(creditsSum, creditsSum);
        return (m == null) ? null : "(" + m + " scanned)";
    }

    static List<Row> buildRows(java.util.Collection<BodyInfo> bodies) {
        return buildRows(bodies, false, null);
    }

    static List<Row> buildRows(java.util.Collection<BodyInfo> bodies, boolean shouldCollapse) {
        return buildRows(bodies, shouldCollapse, null);
    }

    /**
     * @param hideBioDetailRowsForBodyIds when non-null and containing a body id, exobiology detail rows under
     *                                    that body are omitted (body + ring lines are still emitted).
     */
    static List<Row> buildRows(java.util.Collection<BodyInfo> bodies, boolean shouldCollapse,
            Set<Integer> hideBioDetailRowsForBodyIds) {
        List<BodyInfo> sorted = new ArrayList<>(bodies);
        for (BodyInfo b : sorted) {
            if (b.hasBio() && !spanshExobiologyExclusionActive(b)) {
                ensureBioPredictionsPopulated(b);
            }
        }

        // System tab: orbital order — closest to the primary star first (journal {@code DistanceFromArrivalLS}),
        // furthest last. Missing distance sorts last; stable tie-break on body id.
        sorted.sort((a, b) -> {
            double aDist = Double.isNaN(a.getDistanceLs()) ? Double.MAX_VALUE : a.getDistanceLs();
            double bDist = Double.isNaN(b.getDistanceLs()) ? Double.MAX_VALUE : b.getDistanceLs();
            int cmp = Double.compare(aDist, bDist);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(a.getBodyId(), b.getBodyId());
        });

        List<Row> rows = new ArrayList<>();

        for (BodyInfo b : sorted) {
            String bioHeader = null;
            if (b.hasBio() && !spanshExobiologyExclusionActive(b)) {
                bioHeader = computeBioHeaderSummary(b);
            }
            rows.add(Row.body(b, bioHeader));

            if (b.isPlanetaryBodyForRingDisplay()) {
                List<String> ringLines = RingSummaryFormatter.finalizeAndEnrichRingLines(
                        b.getRingSummaryLines(),
                        b.getRingReserveHumanized());
                for (String line : ringLines) {
                    if (line != null && !line.trim().isEmpty()) {
                        rows.add(Row.ring(b.getBodyId(), line.trim()));
                    }
                }
            }

            if (!b.hasBio()) {
                continue;
            }

            if (hideBioDetailRowsForBodyIds != null
                    && hideBioDetailRowsForBodyIds.contains(Integer.valueOf(b.getBodyId()))) {
                continue;
            }

            // 1) Start from whatever predictions we already have
            List<ExobiologyData.BioCandidate> preds = b.getPredictions();

            Set<String> genusPrefixes = b.getObservedGenusPrefixes();
            Set<String> observedNamesRaw = b.getObservedBioDisplayNames();
            Set<String> observedGenusLower = new HashSet<>();
            if (genusPrefixes != null) {
                for (String gp : genusPrefixes) {
                    if (gp != null && !gp.isEmpty()) {
                        observedGenusLower.add(firstWord(gp).toLowerCase(Locale.ROOT));
                    }
                }
            }

            boolean hasGenusPrefixes = genusPrefixes != null && !genusPrefixes.isEmpty();
            boolean hasObservedNames = observedNamesRaw != null && !observedNamesRaw.isEmpty();
            boolean hasPreds = preds != null && !preds.isEmpty();

            if (!Boolean.TRUE.equals(b.getWasFootfalled()) && b.getSpanshLandmarks() == null) {
                SpanshBodyExobiologyInfo info = SpanshLandmarkCache.getInstance().getIfPresent(b.getStarSystem(), b.getBodyName());
                if (info != null) {
                    b.setSpanshLandmarks(info.getLandmarks());
                    b.setSpanshExcludeFromExobiology(info.isExcludeFromExobiology());
                }
            }
            boolean firstBonus = FirstBonusHelper.firstBonusApplies(b);

            // If literally nothing but "hasBio", show a generic message
            if (!hasGenusPrefixes && !hasObservedNames && !hasPreds) {
                rows.add(Row.bio(b.getBodyId(),
                        "Biological signals detected",
                        ""));
                continue;
            }

            //
            // CASE A: Predictions only, no genus info from scan yet.
            //
            if (!hasGenusPrefixes && !hasObservedNames) {

                class BioRowData {
                    final String name;
                    final Long cr;

                    BioRowData(String name, Long cr) {
                        this.name = name;
                        this.cr = cr;
                    }
                }

                List<BioRowData> bioRows = new ArrayList<>();

                if (preds != null) {
                    for (ExobiologyData.BioCandidate cand : preds) {
                        String name = canonicalBioName(cand.getDisplayName());
                        Long cr = cand.getEstimatedPayout(firstBonus);
                        bioRows.add(new BioRowData(name, cr));
                    }
                }

                if (bioRows.isEmpty()) {
                    rows.add(Row.bio(b.getBodyId(),
                            "Biological signals detected",
                            ""));
                    continue;
                }

                // Sort by value desc, then genus, then full name
                bioRows.sort((a, bRow) -> {
                    String aName = (a.name != null) ? a.name : "";
                    String bName = (bRow.name != null) ? bRow.name : "";

                    String aGenus = firstWord(aName);
                    String bGenus = firstWord(bName);

                    long aVal = (a.cr != null) ? a.cr : Long.MIN_VALUE;
                    long bVal = (bRow.cr != null) ? bRow.cr : Long.MIN_VALUE;

                    int cmp = Long.compare(bVal, aVal);
                    if (cmp != 0) {
                        return cmp;
                    }

                    cmp = aGenus.compareToIgnoreCase(bGenus);
                    if (cmp != 0) {
                        return cmp;
                    }

                    return aName.compareToIgnoreCase(bName);
                });

                if (!shouldCollapse) {
                    for (BioRowData br : bioRows) {
                        String label = br.name;

                        String valueText = "";
                        if (br.cr != null) {
                            long millions = Math.round(br.cr / 1_000_000.0);
                            valueText = String.format(Locale.US, "%dM Cr", millions);
                        }

                        rows.add(Row.bio(b.getBodyId(), label, valueText,
                                br.cr != null ? Long.valueOf(br.cr.longValue()) : null));
                    }

                    continue;
                }

                // Collapse by genus: label "Genus (n signals)"; value = remaining payout range then (Xm scanned).
                class GenusSummary {
                    final List<Long> remaining = new ArrayList<>();
                    final List<Long> claimed = new ArrayList<>();
                    int rowCount = 0;
                }

                Map<String, GenusSummary> byGenus = new LinkedHashMap<>();

                for (BioRowData br : bioRows) {
                    String genus = firstWord(br.name);
                    GenusSummary summary = byGenus.get(genus);
                    if (summary == null) {
                        summary = new GenusSummary();
                        byGenus.put(genus, summary);
                    }
                    summary.rowCount++;
                    if (br.cr != null) {
                        long cr = br.cr.longValue();
                        if (speciesFullySampled(b, br.name)) {
                            summary.claimed.add(Long.valueOf(cr));
                        } else {
                            summary.remaining.add(Long.valueOf(cr));
                        }
                    }
                }

                for (Map.Entry<String, GenusSummary> e : byGenus.entrySet()) {
                    String genus = e.getKey();
                    GenusSummary gs = e.getValue();

                    int total = gs.rowCount;
                    String label = genus + " (" + total + (total == 1 ? " signal)" : " signals)");

                    String remainingStr = null;
                    if (!gs.remaining.isEmpty()) {
                        long[] rng = bioPayoutRangeFromRemainingCredits(
                                new ArrayList<>(gs.remaining), Integer.valueOf(1));
                        if (rng != null) {
                            remainingStr = formatMillionSummary(rng[0], rng[1]);
                        }
                    }
                    long claimedSum = 0L;
                    for (Long c : gs.claimed) {
                        if (c != null) {
                            claimedSum += c.longValue();
                        }
                    }

                    StringBuilder vb = new StringBuilder();
                    if (remainingStr != null && !remainingStr.isEmpty()) {
                        vb.append(remainingStr);
                    }
                    if (claimedSum > 0L) {
                        if (vb.length() > 0) {
                            vb.append(' ');
                        }
                        String scanned = formatScannedCreditsParenthetical(claimedSum);
                        if (scanned != null) {
                            vb.append(scanned);
                        }
                    }
                    String valueText = vb.toString();

                    Long maxRem = null;
                    for (Long cr : gs.remaining) {
                        if (cr != null && (maxRem == null || cr.longValue() > maxRem.longValue())) {
                            maxRem = cr;
                        }
                    }

                    rows.add(Row.bio(b.getBodyId(), label, valueText,
                            maxRem != null ? Long.valueOf(maxRem.longValue()) : null));
                }

                continue;
            }

            //
            // CASE B: We have some genus / confirmed info (either genus prefixes OR concrete observed names).
            // We display genus headers (green) for observed genus, and rows for confirmed species.
            // If confirmed species exists for a genus, it REPLACES predictions for that genus.
            //

            // Build:
            //   predictedByGenus: genus -> list of predicted candidates
            //   predictedByCanonName: canonical name -> candidate
            //   confirmedByGenus: genus -> list of canonical confirmed names
            Map<String, List<ExobiologyData.BioCandidate>> predictedByGenus = new LinkedHashMap<>();
            Map<String, ExobiologyData.BioCandidate> predictedByCanonName = new LinkedHashMap<>();
            Map<String, List<String>> confirmedByGenus = new LinkedHashMap<>();

            if (preds != null) {
                for (ExobiologyData.BioCandidate cand : preds) {
                    String canon = canonicalBioName(cand.getDisplayName());
                    predictedByCanonName.put(canon, cand);

                    String genus = firstWord(canon).toLowerCase(Locale.ROOT);
                    predictedByGenus.computeIfAbsent(genus, k -> new ArrayList<>()).add(cand);
                }
            }

            if (observedNamesRaw != null) {
                for (String raw : observedNamesRaw) {
                    String canon = canonicalBioName(raw);
                    String genus = firstWord(canon).toLowerCase(Locale.ROOT);
                    confirmedByGenus.computeIfAbsent(genus, k -> new ArrayList<>()).add(canon);
                }
            }

            // Genus ordering: observed genus first, then remaining predicted genus.
            List<String> genusOrder = new ArrayList<>();

            if (genusPrefixes != null) {
                for (String gp : genusPrefixes) {
                    if (gp == null || gp.isBlank()) {
                        continue;
                    }
                    String g = firstWord(gp).trim().toLowerCase(Locale.ROOT);
                    if (!genusOrder.contains(g)) {
                        genusOrder.add(g);
                    }
                }
            }

            for (String g : predictedByGenus.keySet()) {
                if (!genusOrder.contains(g)) {
                    genusOrder.add(g);
                }
            }

            // Sort genusOrder: observed genus (green) first, then by max value desc, then name
            genusOrder.sort((g1, g2) -> {
                boolean g1Observed = observedGenusLower.contains(g1);
                boolean g2Observed = observedGenusLower.contains(g2);
                if (g1Observed != g2Observed) {
                    return g1Observed ? -1 : 1;
                }

                long g1Val = genusMaxValue(g1, predictedByGenus, predictedByCanonName, confirmedByGenus, firstBonus);
                long g2Val = genusMaxValue(g2, predictedByGenus, predictedByCanonName, confirmedByGenus, firstBonus);
                int cmp = Long.compare(g2Val, g1Val);
                if (cmp != 0) {
                    return cmp;
                }

                return g1.compareToIgnoreCase(g2);
            });

            if (genusOrder.isEmpty()) {
                rows.add(Row.bio(b.getBodyId(),
                        "Biological signals detected",
                        ""));
                continue;
            }

            for (String genusKey : genusOrder) {
                List<ExobiologyData.BioCandidate> predictedForGenus = predictedByGenus.get(genusKey);
                List<String> confirmedForGenus = confirmedByGenus.get(genusKey);

                boolean hasAnySpecies =
                        (confirmedForGenus != null && !confirmedForGenus.isEmpty()) ||
                        (predictedForGenus != null && !predictedForGenus.isEmpty());

                if (!hasAnySpecies) {
                    String displayGenus;
                    if (genusKey.isEmpty()) {
                        displayGenus = genusKey;
                    } else {
                        displayGenus = Character.toUpperCase(genusKey.charAt(0))
                                + genusKey.substring(1);
                    }
                    rows.add(Row.bio(b.getBodyId(), displayGenus, ""));
                    continue;
                }

                // If we have confirmed species for this genus, they REPLACE predictions.
                if (confirmedForGenus != null && !confirmedForGenus.isEmpty()) {
                    class SpeciesRow {
                        final String name;
                        final Long cr;

                        SpeciesRow(String name, Long cr) {
                            this.name = name;
                            this.cr = cr;
                        }
                    }

                    List<SpeciesRow> speciesRows = new ArrayList<>();
                    for (String canonName : confirmedForGenus) {
                        ExobiologyData.BioCandidate cand = predictedByCanonName.get(canonName);
                        Long cr = (cand != null) ? cand.getEstimatedPayout(firstBonus) : null;
                        speciesRows.add(new SpeciesRow(canonName, cr));
                    }

                    speciesRows.sort((a, bRow) -> {
                        long aVal = (a.cr != null) ? a.cr : Long.MIN_VALUE;
                        long bVal = (bRow.cr != null) ? bRow.cr : Long.MIN_VALUE;
                        int cmp = Long.compare(bVal, aVal);
                        if (cmp != 0) {
                            return cmp;
                        }
                        return a.name.compareToIgnoreCase(bRow.name);
                    });

                    for (SpeciesRow sr : speciesRows) {
                        String valueText = "";
                        if (sr.cr != null) {
                            long millions = Math.round(sr.cr / 1_000_000.0);
                            valueText = String.format(Locale.US, "%dM Cr", millions);
                        }
                        int samples = b.getBioSampleCount(sr.name);
                        Row bio = Row.bio(b.getBodyId(), sr.name, valueText, samples,
                                sr.cr != null ? Long.valueOf(sr.cr.longValue()) : null);

                        bio.setObservedGenusHeader(true); // green styling
                        rows.add(bio);
                    }
                } else if (predictedForGenus != null && !predictedForGenus.isEmpty()) {
                    predictedForGenus.sort((c1, c2) -> {
                        long v1 = c1.getEstimatedPayout(firstBonus);
                        long v2 = c2.getEstimatedPayout(firstBonus);
                        int cmp = Long.compare(v2, v1);
                        if (cmp != 0) {
                            return cmp;
                        }
                        String n1 = canonicalBioName(c1.getDisplayName());
                        String n2 = canonicalBioName(c2.getDisplayName());
                        return n1.compareToIgnoreCase(n2);
                    });

                    for (ExobiologyData.BioCandidate cand : predictedForGenus) {
                        String name = canonicalBioName(cand.getDisplayName());
                        long cr = cand.getEstimatedPayout(firstBonus);
                        long millions = Math.round(cr / 1_000_000.0);
                        String valueText = String.format(Locale.US, "%dM Cr", millions);
                        rows.add(Row.bio(b.getBodyId(), name, valueText, Long.valueOf(cr)));
                    }
                }
            }
        }

        return rows;
    }

    /**
     * True when the system tab could list one or more exobiology lines under this body (excluding Spansh-only
     * exclusion with no local journal evidence).
     */
    static boolean hasExpandableBioDetails(BodyInfo b) {
        if (b == null || !b.hasBio()) {
            return false;
        }
        ensureBioPredictionsPopulated(b);
        if (!Boolean.TRUE.equals(b.getWasFootfalled()) && b.getSpanshLandmarks() == null) {
            SpanshBodyExobiologyInfo info =
                    SpanshLandmarkCache.getInstance().getIfPresent(b.getStarSystem(), b.getBodyName());
            if (info != null) {
                b.setSpanshLandmarks(info.getLandmarks());
                b.setSpanshExcludeFromExobiology(info.isExcludeFromExobiology());
            }
        }
        return !spanshExobiologyExclusionActive(b);
    }

    /**
     * Fills {@link BodyInfo#getPredictions()} when the journal already marked the body as having bio
     * but the processor has not attached candidates yet (common when scans precede full prediction events).
     */
    private static void ensureBioPredictionsPopulated(BodyInfo b) {
        if (b == null) {
            return;
        }
        List<ExobiologyData.BioCandidate> preds = b.getPredictions();
        if (preds != null && !preds.isEmpty()) {
            return;
        }
        BodyAttributes attrs = null;
        try {
            attrs = b.buildBodyAttributes();
        } catch (RuntimeException ex) {
            System.out.println("Bio attrs not ready for " + b.getShortName() + " (" + b.getBodyId() + "): " + ex);
            return;
        }
        List<ExobiologyData.BioCandidate> computed = ExobiologyData.predict(attrs);
        if (computed != null && !computed.isEmpty()) {
            b.setPredictions(computed);
        }
    }

    /**
     * Highest single-species estimated Vista Genomics payout used for UI (money-bag threshold), or
     * {@link Long#MIN_VALUE} if none.
     */
    static long getMaxBioEstimatedCredits(BodyInfo b) {
        return maxBioValue(b);
    }

    private static long maxBioValue(BodyInfo b) {
        if (b == null) {
            return Long.MIN_VALUE;
        }

        List<ExobiologyData.BioCandidate> preds = b.getPredictions();
        if (preds == null || preds.isEmpty()) {
            return Long.MIN_VALUE;
        }

        if (!Boolean.TRUE.equals(b.getWasFootfalled()) && b.getSpanshLandmarks() == null) {
            SpanshBodyExobiologyInfo info = SpanshLandmarkCache.getInstance().getIfPresent(b.getStarSystem(), b.getBodyName());
            if (info != null) {
                b.setSpanshLandmarks(info.getLandmarks());
                b.setSpanshExcludeFromExobiology(info.isExcludeFromExobiology());
            }
        }
        if (spanshExobiologyExclusionActive(b)) {
            return Long.MIN_VALUE;
        }
        boolean firstBonus = FirstBonusHelper.firstBonusApplies(b);

        long max = Long.MIN_VALUE;
        for (ExobiologyData.BioCandidate c : preds) {
            if (c == null) {
                continue;
            }
            long v = c.getEstimatedPayout(firstBonus);
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    private static boolean isGenusObserved(List<String> confirmedForGenus) {
        if (confirmedForGenus == null) {
            return false;
        }
        return !confirmedForGenus.isEmpty();
    }

    private static long genusMaxValue(String genusKey,
                                      Map<String, List<ExobiologyData.BioCandidate>> predictedByGenus,
                                      Map<String, ExobiologyData.BioCandidate> predictedByCanonName,
                                      Map<String, List<String>> confirmedByGenus,
                                      boolean firstBonus) {
        if (genusKey == null) {
            return Long.MIN_VALUE;
        }

        List<String> confirmed = confirmedByGenus.get(genusKey);
        if (confirmed != null && !confirmed.isEmpty()) {
            long max = Long.MIN_VALUE;
            for (String canon : confirmed) {
                ExobiologyData.BioCandidate cand = predictedByCanonName.get(canon);
                if (cand == null) {
                    continue;
                }
                long v = cand.getEstimatedPayout(firstBonus);
                if (v > max) {
                    max = v;
                }
            }
            return max;
        }

        List<ExobiologyData.BioCandidate> predicted = predictedByGenus.get(genusKey);
        if (predicted == null || predicted.isEmpty()) {
            return Long.MIN_VALUE;
        }

        long max = Long.MIN_VALUE;
        for (ExobiologyData.BioCandidate cand : predicted) {
            if (cand == null) {
                continue;
            }
            long v = cand.getEstimatedPayout(firstBonus);
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    private static String canonicalBioName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return s;
        }

        String[] parts = s.split("\\s+");
        if (parts.length >= 3 && parts[0].equalsIgnoreCase(parts[1])) {
            StringBuilder sb = new StringBuilder(parts[0]);
            for (int i = 2; i < parts.length; i++) {
                sb.append(' ').append(parts[i]);
            }
            return sb.toString();
        }

        return s;
    }

    private static String firstWord(String s) {
        if (s == null) {
            return "";
        }
        String[] parts = s.trim().split("\\s+");
        return parts.length > 0 ? parts[0] : "";
    }

    private static boolean speciesFullySampled(BodyInfo b, String canonDisplayName) {
        if (b == null || canonDisplayName == null || canonDisplayName.isBlank()) {
            return false;
        }
        return b.getBioSampleCount(canonDisplayName) >= 3;
    }

    /**
     * Min/max sum of {@code k} payouts (k = FSS bio count when known, else all remaining species),
     * same rule as INITIAL bio TTS, over credits already filtered to “still in play”.
     */
    private static long[] bioPayoutRangeFromRemainingCredits(List<Long> payoutCredits, Integer fssBioSignalCount) {
        if (payoutCredits == null || payoutCredits.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(payoutCredits);
        Collections.sort(sorted);
        int signalCount = (fssBioSignalCount != null && fssBioSignalCount.intValue() > 0)
                ? fssBioSignalCount.intValue()
                : Math.max(1, sorted.size());
        signalCount = Math.min(signalCount, sorted.size());
        if (signalCount <= 0) {
            return null;
        }
        long minTotal = 0L;
        for (int i = 0; i < signalCount; i++) {
            minTotal += sorted.get(i).longValue();
        }
        long maxTotal = 0L;
        for (int i = sorted.size() - signalCount; i < sorted.size(); i++) {
            maxTotal += sorted.get(i).longValue();
        }
        return new long[] { minTotal, maxTotal, signalCount };
    }

    private static String formatMillionSummary(long minTotal, long maxTotal) {
        if (maxTotal <= 0L) {
            return null;
        }
        long minM = Math.round(minTotal / 1_000_000.0);
        long maxM = Math.round(maxTotal / 1_000_000.0);
        if (minM == maxM) {
            return minM + "M";
        }
        return minM + "\u2013" + maxM + "M";
    }

    private static final class RemainingClaimedCredits {
        final List<Long> remaining = new ArrayList<>();
        final List<Long> claimed = new ArrayList<>();
    }

    /**
     * Vista Genomics credits still in play vs already fully sampled (3/3), using the same CASE A/B
     * species set as the bio table. {@code null} when exobiology payouts cannot be computed from state.
     */
    private static RemainingClaimedCredits collectRemainingClaimedPayoutCredits(BodyInfo b) {
        if (b == null) {
            return null;
        }
        if (!b.hasBio()) {
            return null;
        }
        if (spanshExobiologyExclusionActive(b)) {
            return null;
        }
        ensureBioPredictionsPopulated(b);

        if (!Boolean.TRUE.equals(b.getWasFootfalled()) && b.getSpanshLandmarks() == null) {
            SpanshBodyExobiologyInfo sinfo =
                    SpanshLandmarkCache.getInstance().getIfPresent(b.getStarSystem(), b.getBodyName());
            if (sinfo != null) {
                b.setSpanshLandmarks(sinfo.getLandmarks());
                b.setSpanshExcludeFromExobiology(sinfo.isExcludeFromExobiology());
            }
        }
        if (spanshExobiologyExclusionActive(b)) {
            return null;
        }

        List<ExobiologyData.BioCandidate> preds = b.getPredictions();
        Set<String> genusPrefixes = b.getObservedGenusPrefixes();
        Set<String> observedNamesRaw = b.getObservedBioDisplayNames();

        boolean hasGenusPrefixes = genusPrefixes != null && !genusPrefixes.isEmpty();
        boolean hasObservedNames = observedNamesRaw != null && !observedNamesRaw.isEmpty();
        boolean hasPreds = preds != null && !preds.isEmpty();

        if (!hasGenusPrefixes && !hasObservedNames && !hasPreds) {
            return null;
        }

        boolean firstBonus = FirstBonusHelper.firstBonusApplies(b);
        RemainingClaimedCredits out = new RemainingClaimedCredits();
        boolean caseA = !hasGenusPrefixes && !hasObservedNames;

        if (caseA) {
            for (ExobiologyData.BioCandidate cand : preds) {
                String name = canonicalBioName(cand.getDisplayName());
                long cr = cand.getEstimatedPayout(firstBonus);
                if (speciesFullySampled(b, name)) {
                    out.claimed.add(Long.valueOf(cr));
                } else {
                    out.remaining.add(Long.valueOf(cr));
                }
            }
        } else {
            Map<String, List<ExobiologyData.BioCandidate>> predictedByGenus = new LinkedHashMap<>();
            Map<String, ExobiologyData.BioCandidate> predictedByCanonName = new LinkedHashMap<>();
            Map<String, List<String>> confirmedByGenus = new LinkedHashMap<>();

            if (preds != null) {
                for (ExobiologyData.BioCandidate cand : preds) {
                    String canon = canonicalBioName(cand.getDisplayName());
                    predictedByCanonName.put(canon, cand);
                    String genus = firstWord(canon).toLowerCase(Locale.ROOT);
                    predictedByGenus.computeIfAbsent(genus, k -> new ArrayList<>()).add(cand);
                }
            }

            if (observedNamesRaw != null) {
                for (String raw : observedNamesRaw) {
                    String canon = canonicalBioName(raw);
                    String genus = firstWord(canon).toLowerCase(Locale.ROOT);
                    confirmedByGenus.computeIfAbsent(genus, k -> new ArrayList<>()).add(canon);
                }
            }

            Set<String> observedGenusLower = new HashSet<>();
            if (genusPrefixes != null) {
                for (String gp : genusPrefixes) {
                    if (gp != null && !gp.isEmpty()) {
                        observedGenusLower.add(firstWord(gp).toLowerCase(Locale.ROOT));
                    }
                }
            }

            List<String> genusOrder = new ArrayList<>();
            if (genusPrefixes != null) {
                for (String gp : genusPrefixes) {
                    if (gp == null || gp.isBlank()) {
                        continue;
                    }
                    String g = firstWord(gp).trim().toLowerCase(Locale.ROOT);
                    if (!genusOrder.contains(g)) {
                        genusOrder.add(g);
                    }
                }
            }
            for (String g : predictedByGenus.keySet()) {
                if (!genusOrder.contains(g)) {
                    genusOrder.add(g);
                }
            }

            genusOrder.sort((g1, g2) -> {
                boolean g1Observed = observedGenusLower.contains(g1);
                boolean g2Observed = observedGenusLower.contains(g2);
                if (g1Observed != g2Observed) {
                    return g1Observed ? -1 : 1;
                }

                long g1Val = genusMaxValue(g1, predictedByGenus, predictedByCanonName, confirmedByGenus, firstBonus);
                long g2Val = genusMaxValue(g2, predictedByGenus, predictedByCanonName, confirmedByGenus, firstBonus);
                int cmp = Long.compare(g2Val, g1Val);
                if (cmp != 0) {
                    return cmp;
                }

                return g1.compareToIgnoreCase(g2);
            });

            for (String genusKey : genusOrder) {
                List<ExobiologyData.BioCandidate> predictedForGenus = predictedByGenus.get(genusKey);
                List<String> confirmedForGenus = confirmedByGenus.get(genusKey);

                boolean hasAnySpecies =
                        (confirmedForGenus != null && !confirmedForGenus.isEmpty())
                        || (predictedForGenus != null && !predictedForGenus.isEmpty());
                if (!hasAnySpecies) {
                    continue;
                }

                if (confirmedForGenus != null && !confirmedForGenus.isEmpty()) {
                    for (String canonName : confirmedForGenus) {
                        ExobiologyData.BioCandidate cand = predictedByCanonName.get(canonName);
                        if (cand == null) {
                            continue;
                        }
                        long cr = cand.getEstimatedPayout(firstBonus);
                        if (speciesFullySampled(b, canonName)) {
                            out.claimed.add(Long.valueOf(cr));
                        } else {
                            out.remaining.add(Long.valueOf(cr));
                        }
                    }
                } else if (predictedForGenus != null && !predictedForGenus.isEmpty()) {
                    for (ExobiologyData.BioCandidate cand : predictedForGenus) {
                        String name = canonicalBioName(cand.getDisplayName());
                        long cr = cand.getEstimatedPayout(firstBonus);
                        if (speciesFullySampled(b, name)) {
                            out.claimed.add(Long.valueOf(cr));
                        } else {
                            out.remaining.add(Long.valueOf(cr));
                        }
                    }
                }
            }
        }

        if (out.remaining.isEmpty() && out.claimed.isEmpty()) {
            return null;
        }
        return out;
    }

    /**
     * System tab body-row bio summary: same species set as the table (CASE A/B), excluding species
     * already fully analysed (3/3). Recomputes every table rebuild as scans progress.
     */
    static String computeBioHeaderSummary(BodyInfo b) {
        RemainingClaimedCredits split = collectRemainingClaimedPayoutCredits(b);
        if (split == null || split.remaining.isEmpty()) {
            return null;
        }
        long[] range = bioPayoutRangeFromRemainingCredits(split.remaining, b.getNumberOfBioSignals());
        if (range == null) {
            return null;
        }
        return formatMillionSummary(range[0], range[1]);
    }

}
