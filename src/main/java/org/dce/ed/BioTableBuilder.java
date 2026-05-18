package org.dce.ed;

import java.awt.Color;
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
import org.dce.ed.ui.EdoUi;
import org.dce.ed.util.FirstBonusHelper;
import org.dce.ed.util.RingSummaryFormatter;
import org.dce.ed.util.SpanshBodyExobiologyInfo;
import org.dce.ed.util.SpanshLandmark;
import org.dce.ed.util.SpanshLandmarkCache;

final class BioTableBuilder {

    /** Match Dist column display ({@code %.0f Ls}) so sub-ls jitter does not reshuffle rows. */
    private static final double DIST_LS_SORT_QUANTUM = 1.0;

    private BioTableBuilder() {
        // utility
    }

    static double distanceSortKeyForTable(double rawLs) {
        if (!Double.isFinite(rawLs)) {
            return Double.MAX_VALUE;
        }
        return Math.round(rawLs / DIST_LS_SORT_QUANTUM) * DIST_LS_SORT_QUANTUM;
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
        BioColumnHeaderParts parts = buildBioColumnHeaderParts(b);
        return parts == null ? null : parts.toPlainString();
    }

    /**
     * HTML for the body-row bio column: subdued gray for {@code (n signals)} and {@code (Xm scanned)};
     * green only for the million-scale remaining range/value when its max credits meet or exceed
     * {@code valuableThresholdCredits}.
     */
    static String formatBodyBioColumnHtml(BodyInfo b, long valuableThresholdCredits) {
        BioColumnHeaderParts parts = buildBioColumnHeaderParts(b);
        if (parts != null && !parts.isEmpty()) {
            return parts.toHtml(valuableThresholdCredits);
        }
        String summary = computeBioHeaderSummary(b);
        if (summary == null || summary.isBlank()) {
            return null;
        }
        return singleMillionSummaryHtml(summary, valuableThresholdCredits);
    }

    private static BioColumnHeaderParts buildBioColumnHeaderParts(BodyInfo b) {
        if (b == null) {
            return null;
        }
        RemainingClaimedCredits split = collectRemainingClaimedPayoutCredits(b);
        Integer fss = b.getNumberOfBioSignals();
        int fssN = (fss != null && fss.intValue() > 0) ? fss.intValue() : 0;

        if (split == null) {
            if (fssN <= 0) {
                return null;
            }
            BioColumnHeaderParts onlySignals = new BioColumnHeaderParts();
            onlySignals.signalsParenthetical = parentheticalSignals(fssN);
            return onlySignals;
        }

        long claimedSum = 0L;
        for (Long c : split.claimed) {
            if (c != null) {
                claimedSum += c.longValue();
            }
        }

        String remainingStr = null;
        Long maxRemainingCr = null;
        if (!split.remainingPerSpecies.isEmpty()) {
            long[] payoutRange = bioPayoutRangeFromRemainingCredits(
                    split.remainingMin, split.remainingMax, b.getNumberOfBioSignals());
            if (payoutRange != null) {
                remainingStr = formatRemainingMillionSummaryForHeader(
                        payoutRange[0], payoutRange[1], split.remainingPerSpecies, (int) payoutRange[2]);
                maxRemainingCr = Long.valueOf(payoutRange[1]);
            }
        }

        BioColumnHeaderParts p = new BioColumnHeaderParts();
        p.remainingMillionText = remainingStr;
        p.maxRemainingCredits = maxRemainingCr;
        if (claimedSum > 0L) {
            p.scannedParenthetical = formatScannedCreditsParenthetical(claimedSum);
        }
        if (claimedSum <= 0L && fssN > 0) {
            p.signalsParenthetical = parentheticalSignals(fssN);
        }
        return p.isEmpty() ? null : p;
    }

    private static final class BioColumnHeaderParts {
        String remainingMillionText;
        Long maxRemainingCredits;
        String scannedParenthetical;
        String signalsParenthetical;

        boolean isEmpty() {
            return (remainingMillionText == null || remainingMillionText.isEmpty())
                    && scannedParenthetical == null
                    && signalsParenthetical == null;
        }

        String toPlainString() {
            StringBuilder sb = new StringBuilder();
            if (remainingMillionText != null && !remainingMillionText.isEmpty()) {
                sb.append(remainingMillionText);
            }
            if (scannedParenthetical != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(scannedParenthetical);
            }
            if (signalsParenthetical != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(signalsParenthetical);
            }
            return sb.length() == 0 ? null : sb.toString();
        }

        String toHtml(long valuableThresholdCredits) {
            Color subdued = EdoUi.Internal.GRAY_180;
            Color valuable = EdoUi.User.PRIMARY_HIGHLIGHT;
            String sub = htmlRgb(subdued);
            String val = htmlRgb(valuable);
            StringBuilder sb = new StringBuilder("<html><body style='margin:0'>");
            boolean needSpace = false;
            if (remainingMillionText != null && !remainingMillionText.isEmpty()) {
                boolean hi = maxRemainingCredits != null
                        && maxRemainingCredits.longValue() >= valuableThresholdCredits;
                sb.append("<font color='").append(hi ? val : sub).append("'>")
                        .append(escapeHtml(remainingMillionText)).append("</font>");
                needSpace = true;
            }
            if (scannedParenthetical != null) {
                if (needSpace) {
                    sb.append(' ');
                }
                sb.append("<font color='").append(sub).append("'>")
                        .append(escapeHtml(scannedParenthetical)).append("</font>");
                needSpace = true;
            }
            if (signalsParenthetical != null) {
                if (needSpace) {
                    sb.append(' ');
                }
                sb.append("<font color='").append(sub).append("'>")
                        .append(escapeHtml(signalsParenthetical)).append("</font>");
            }
            sb.append("</body></html>");
            return sb.toString();
        }
    }

    private static String singleMillionSummaryHtml(String summary, long valuableThresholdCredits) {
        Long maxCr = parseMaxCreditsFromMillionSummaryLabel(summary);
        Color subdued = EdoUi.Internal.GRAY_180;
        Color valuable = EdoUi.User.PRIMARY_HIGHLIGHT;
        String sub = htmlRgb(subdued);
        String val = htmlRgb(valuable);
        boolean hi = maxCr != null && maxCr.longValue() >= valuableThresholdCredits;
        return "<html><body style='margin:0'><font color='" + (hi ? val : sub) + "'>"
                + escapeHtml(summary.trim()) + "</font></body></html>";
    }

    /** Parses e.g. {@code 182M} or {@code 5–182M} (en dash) to max credits. */
    private static Long parseMaxCreditsFromMillionSummaryLabel(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (!s.endsWith("M")) {
            return null;
        }
        String core = s.substring(0, s.length() - 1).trim();
        int dash = -1;
        for (int i = 0; i < core.length(); i++) {
            char ch = core.charAt(i);
            if (ch == '\u2013' || ch == '-') {
                dash = i;
                break;
            }
        }
        try {
            if (dash >= 0) {
                long a = Long.parseLong(core.substring(0, dash).trim());
                long b = Long.parseLong(core.substring(dash + 1).trim());
                return Long.valueOf(Math.max(a, b) * 1_000_000L);
            }
            long n = Long.parseLong(core);
            return Long.valueOf(n * 1_000_000L);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String htmlRgb(Color c) {
        return String.format("#%06x", c.getRGB() & 0xffffff);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String parentheticalSignals(int n) {
        return "(" + n + (n == 1 ? " signal)" : " signals)");
    }

    /**
     * Million-scale header for remaining payouts: for a single total, prefer the sum of per-species
     * {@code round(cr/1M)} so the headline matches the rounded detail lines (aggregate round can differ by 1M).
     */
    private static String formatRemainingMillionSummaryForHeader(long minCr, long maxCr, List<Long> remainingCredits,
            int signalCountUsed) {
        String aggregate = formatMillionSummary(minCr, maxCr);
        if (aggregate == null || remainingCredits == null || remainingCredits.isEmpty() || minCr != maxCr
                || signalCountUsed != remainingCredits.size()) {
            return aggregate;
        }
        long sumRoundedM = 0L;
        for (Long c : remainingCredits) {
            if (c != null) {
                sumRoundedM += Math.round(c.longValue() / 1_000_000.0);
            }
        }
        long aggregateM = Math.round(minCr / 1_000_000.0);
        if (sumRoundedM != aggregateM) {
            return sumRoundedM + "M";
        }
        return aggregate;
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
        return buildRows(bodies, shouldCollapse, hideBioDetailRowsForBodyIds, false, null, false);
    }

    /**
     * @param distanceFromShipMode when {@code true}, sort by {@code shipCentricDistLs} when that map is non-empty;
     *                             otherwise fall back to journal {@link BodyInfo#getDistanceLs()}.
     * @param shipCentricDistLs approximate distance from commander (Ls) per body id; may be sparse
     */
    static List<Row> buildRows(java.util.Collection<BodyInfo> bodies, boolean shouldCollapse,
            Set<Integer> hideBioDetailRowsForBodyIds,
            boolean distanceFromShipMode,
            java.util.Map<Integer, Double> shipCentricDistLs) {
        return buildRows(bodies, shouldCollapse, hideBioDetailRowsForBodyIds, distanceFromShipMode,
                shipCentricDistLs, false);
    }

    /**
     * @param shipCentricAnchorMissing when {@code true} with {@code distanceFromShipMode}, commander anchor was not
     *                                 resolved — sort and Dist use arrival data except Dist shows an em dash per body.
     */
    static List<Row> buildRows(java.util.Collection<BodyInfo> bodies, boolean shouldCollapse,
            Set<Integer> hideBioDetailRowsForBodyIds,
            boolean distanceFromShipMode,
            java.util.Map<Integer, Double> shipCentricDistLs,
            boolean shipCentricAnchorMissing) {
        return buildRows(bodyMapFromCollection(bodies), shouldCollapse, hideBioDetailRowsForBodyIds,
                distanceFromShipMode, shipCentricDistLs, shipCentricAnchorMissing, null);
    }

    private static LinkedHashMap<Integer, BodyInfo> bodyMapFromCollection(java.util.Collection<BodyInfo> bodies) {
        LinkedHashMap<Integer, BodyInfo> m = new LinkedHashMap<>();
        if (bodies != null) {
            for (BodyInfo b : bodies) {
                if (b != null) {
                    m.put(Integer.valueOf(b.getBodyId()), b);
                }
            }
        }
        return m;
    }

    /**
     * @param bodiesByMapKey journal / state map keys (may differ from {@link BodyInfo#getBodyId()} on some paths)
     * @param geometryFallbackDistLs when non-null and not in ship mode, fills Dist / sort for bodies whose journal
     *                               {@code DistanceFromArrivalLS} is NaN (approximate distance from primary, Ls)
     */
    static List<Row> buildRows(java.util.Map<Integer, BodyInfo> bodiesByMapKey, boolean shouldCollapse,
            Set<Integer> hideBioDetailRowsForBodyIds,
            boolean distanceFromShipMode,
            java.util.Map<Integer, Double> shipCentricDistLs,
            boolean shipCentricAnchorMissing,
            java.util.Map<Integer, Double> geometryFallbackDistLs) {
        if (bodiesByMapKey == null || bodiesByMapKey.isEmpty()) {
            return new ArrayList<>();
        }
        List<java.util.Map.Entry<Integer, BodyInfo>> sorted = new ArrayList<>(bodiesByMapKey.entrySet());
        for (java.util.Map.Entry<Integer, BodyInfo> ent : sorted) {
            BodyInfo b = ent.getValue();
            if (b != null && b.hasBio() && !spanshExobiologyExclusionActive(b)) {
                ensureBioPredictionsPopulated(b);
            }
        }

        final boolean sortByShip = distanceFromShipMode && !shipCentricAnchorMissing
                && shipCentricDistLs != null && !shipCentricDistLs.isEmpty();

        // System tab: default order — closest to arrival / primary first (journal {@code DistanceFromArrivalLS}).
        // Ship mode — closest to commander first when {@code shipCentricDistLs} is populated.
        sorted.sort((ea, eb) -> {
            BodyInfo a = ea.getValue();
            BodyInfo b = eb.getValue();
            if (a == null || b == null) {
                return 0;
            }
            double aDist;
            double bDist;
            if (sortByShip) {
                aDist = shipCentricDistanceSortKey(ea.getKey(), a, shipCentricDistLs);
                bDist = shipCentricDistanceSortKey(eb.getKey(), b, shipCentricDistLs);
            } else {
                aDist = arrivalOrGeometrySortKey(a, ea.getKey(), geometryFallbackDistLs);
                bDist = arrivalOrGeometrySortKey(b, eb.getKey(), geometryFallbackDistLs);
            }
            int cmp = Double.compare(distanceSortKeyForTable(aDist), distanceSortKeyForTable(bDist));
            if (cmp != 0) {
                return cmp;
            }
            int keyCmp = Integer.compare(ea.getKey().intValue(), eb.getKey().intValue());
            if (keyCmp != 0) {
                return keyCmp;
            }
            return Integer.compare(a.getBodyId(), b.getBodyId());
        });

        List<Row> rows = new ArrayList<>();

        for (java.util.Map.Entry<Integer, BodyInfo> ent : sorted) {
            Integer mapKey = ent.getKey();
            BodyInfo b = ent.getValue();
            if (b == null) {
                continue;
            }
            String bioHeader = null;
            if (b.hasBio() && !spanshExobiologyExclusionActive(b)) {
                bioHeader = computeBioHeaderSummary(b);
            }
            Double distCol = null;
            if (distanceFromShipMode && !shipCentricAnchorMissing && shipCentricDistLs != null) {
                distCol = shipCentricDistLs.get(mapKey);
                if (distCol == null && b.getBodyId() >= 0) {
                    distCol = shipCentricDistLs.get(Integer.valueOf(b.getBodyId()));
                }
            } else if (!distanceFromShipMode && geometryFallbackDistLs != null) {
                double j = b.getDistanceLs();
                if (Double.isNaN(j) || j <= 0.0) {
                    Double g = geometryDistanceLookup(mapKey, b, geometryFallbackDistLs);
                    if (g != null && Double.isFinite(g.doubleValue())) {
                        distCol = g;
                    }
                }
            }
            rows.add(Row.body(b, bioHeader, distCol, shipCentricAnchorMissing));

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
                        long minG = Long.MAX_VALUE;
                        long maxG = Long.MIN_VALUE;
                        for (Long c : gs.remaining) {
                            if (c != null) {
                                long v = c.longValue();
                                minG = Math.min(minG, v);
                                maxG = Math.max(maxG, v);
                            }
                        }
                        if (minG != Long.MAX_VALUE) {
                            long[] rng = bioPayoutRangeFromRemainingCredits(
                                    Collections.singletonList(Long.valueOf(minG)),
                                    Collections.singletonList(Long.valueOf(maxG)),
                                    Integer.valueOf(1));
                            if (rng != null) {
                                remainingStr = formatMillionSummary(rng[0], rng[1]);
                            }
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
     * Min/max sum of {@code k} biological signal slots (k = FSS bio count when known, else one slot per
     * remaining genus). {@code minCredits[i]} / {@code maxCredits[i]} bound Vista payout for genus {@code i}
     * when multiple species are predicted (only one species per genus exists on the body).
     */
    static long[] bioPayoutRangeFromMinMaxLists(List<Long> minCredits, List<Long> maxCredits,
            Integer fssBioSignalCount) {
        return bioPayoutRangeFromRemainingCredits(minCredits, maxCredits, fssBioSignalCount);
    }

    private static long[] bioPayoutRangeFromRemainingCredits(List<Long> minCredits, List<Long> maxCredits,
            Integer fssBioSignalCount) {
        if (minCredits == null || maxCredits == null || minCredits.isEmpty() || minCredits.size() != maxCredits.size()) {
            return null;
        }
        List<Long> sortedMin = new ArrayList<>(minCredits);
        List<Long> sortedMax = new ArrayList<>(maxCredits);
        Collections.sort(sortedMin);
        Collections.sort(sortedMax);
        int n = sortedMin.size();
        int signalCount = (fssBioSignalCount != null && fssBioSignalCount.intValue() > 0)
                ? fssBioSignalCount.intValue()
                : Math.max(1, n);
        signalCount = Math.min(signalCount, n);
        if (signalCount <= 0) {
            return null;
        }
        long minTotal = 0L;
        for (int i = 0; i < signalCount; i++) {
            minTotal += sortedMin.get(i).longValue();
        }
        long maxTotal = 0L;
        for (int i = sortedMax.size() - signalCount; i < sortedMax.size(); i++) {
            maxTotal += sortedMax.get(i).longValue();
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
        /** One entry per predicted species still in play (matches expanded table rows / rounding). */
        final List<Long> remainingPerSpecies = new ArrayList<>();
        /** One entry per genus with remaining species: min Vista payout among predicted species in that genus. */
        final List<Long> remainingMin = new ArrayList<>();
        /** One entry per genus with remaining species: max Vista payout among predicted species in that genus. */
        final List<Long> remainingMax = new ArrayList<>();
        final List<Long> claimed = new ArrayList<>();
    }

    private static void appendCollapsedMinMax(RemainingClaimedCredits out, List<Long> pendingRemaining) {
        if (pendingRemaining == null || pendingRemaining.isEmpty()) {
            return;
        }
        if (pendingRemaining.size() == 1) {
            long c = pendingRemaining.get(0).longValue();
            out.remainingMin.add(Long.valueOf(c));
            out.remainingMax.add(Long.valueOf(c));
            return;
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (Long c : pendingRemaining) {
            if (c == null) {
                continue;
            }
            long v = c.longValue();
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (min != Long.MAX_VALUE) {
            out.remainingMin.add(Long.valueOf(min));
            out.remainingMax.add(Long.valueOf(max));
        }
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
            Map<String, List<ExobiologyData.BioCandidate>> predictedByGenusCaseA = new LinkedHashMap<>();
            for (ExobiologyData.BioCandidate cand : preds) {
                String canon = canonicalBioName(cand.getDisplayName());
                String genus = firstWord(canon).toLowerCase(Locale.ROOT);
                predictedByGenusCaseA.computeIfAbsent(genus, k -> new ArrayList<>()).add(cand);
            }
            for (Map.Entry<String, List<ExobiologyData.BioCandidate>> ge : predictedByGenusCaseA.entrySet()) {
                List<Long> pendingRemaining = new ArrayList<>();
                for (ExobiologyData.BioCandidate cand : ge.getValue()) {
                    String name = canonicalBioName(cand.getDisplayName());
                    long cr = cand.getEstimatedPayout(firstBonus);
                    if (speciesFullySampled(b, name)) {
                        out.claimed.add(Long.valueOf(cr));
                    } else {
                        out.remainingPerSpecies.add(Long.valueOf(cr));
                        pendingRemaining.add(Long.valueOf(cr));
                    }
                }
                appendCollapsedMinMax(out, pendingRemaining);
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

                List<Long> pendingRemaining = new ArrayList<>();

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
                            out.remainingPerSpecies.add(Long.valueOf(cr));
                            pendingRemaining.add(Long.valueOf(cr));
                        }
                    }
                } else if (predictedForGenus != null && !predictedForGenus.isEmpty()) {
                    for (ExobiologyData.BioCandidate cand : predictedForGenus) {
                        String name = canonicalBioName(cand.getDisplayName());
                        long cr = cand.getEstimatedPayout(firstBonus);
                        if (speciesFullySampled(b, name)) {
                            out.claimed.add(Long.valueOf(cr));
                        } else {
                            out.remainingPerSpecies.add(Long.valueOf(cr));
                            pendingRemaining.add(Long.valueOf(cr));
                        }
                    }
                }
                appendCollapsedMinMax(out, pendingRemaining);
            }
        }

        if (out.remainingPerSpecies.isEmpty() && out.claimed.isEmpty()) {
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
        if (split == null || split.remainingPerSpecies.isEmpty()) {
            return null;
        }
        long[] range = bioPayoutRangeFromRemainingCredits(
                split.remainingMin, split.remainingMax, b.getNumberOfBioSignals());
        if (range == null) {
            return null;
        }
        return formatRemainingMillionSummaryForHeader(range[0], range[1], split.remainingPerSpecies, (int) range[2]);
    }

    private static Double geometryDistanceLookup(Integer mapKey, BodyInfo b, Map<Integer, Double> geometryFallbackDistLs) {
        if (geometryFallbackDistLs == null || b == null) {
            return null;
        }
        Double d = mapKey != null ? geometryFallbackDistLs.get(mapKey) : null;
        if (d == null && b.getBodyId() >= 0) {
            d = geometryFallbackDistLs.get(Integer.valueOf(b.getBodyId()));
        }
        return d;
    }

    private static double arrivalOrGeometrySortKey(BodyInfo b, Integer mapKey, Map<Integer, Double> geometryFallbackDistLs) {
        if (b == null) {
            return Double.MAX_VALUE;
        }
        double j = b.getDistanceLs();
        if (!Double.isNaN(j) && j > 0.0) {
            return j;
        }
        Double g = geometryDistanceLookup(mapKey, b, geometryFallbackDistLs);
        if (g != null && Double.isFinite(g.doubleValue())) {
            return g.doubleValue();
        }
        if (!Double.isNaN(j)) {
            return j;
        }
        return Double.MAX_VALUE;
    }

    private static double shipCentricDistanceSortKey(Integer mapKey, BodyInfo b,
            java.util.Map<Integer, Double> shipCentricDistLs) {
        if (shipCentricDistLs == null || b == null) {
            return Double.MAX_VALUE;
        }
        Double d = mapKey != null ? shipCentricDistLs.get(mapKey) : null;
        if (d == null && b.getBodyId() >= 0) {
            d = shipCentricDistLs.get(Integer.valueOf(b.getBodyId()));
        }
        if (d != null && Double.isFinite(d.doubleValue())) {
            return d.doubleValue();
        }
        return Double.MAX_VALUE;
    }

}
