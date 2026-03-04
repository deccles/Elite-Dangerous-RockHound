package org.dce.ed;

import java.util.ArrayList;
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
import org.dce.ed.util.SpanshLandmark;
import org.dce.ed.util.SpanshLandmarkCache;

final class BioTableBuilder {

    private BioTableBuilder() {
        // utility
    }

    static List<Row> buildRows(java.util.Collection<BodyInfo> bodies) {
        return buildRows(bodies, false);
    }

    static List<Row> buildRows(java.util.Collection<BodyInfo> bodies, boolean shouldCollapse) {
        List<BodyInfo> sorted = new ArrayList<>(bodies);

        // Sorting priority:
        //   1) Any body that has an on-foot sampled biological (green rows)
        //      should float to the top.
        //   2) Otherwise, sort by maximum predicted (or confirmed) payout
        //      descending.
        //   3) Tie-breaker: distance from arrival ascending.
        sorted.sort((a, b) -> {
            boolean aObserved = hasObservedSample(a);
            boolean bObserved = hasObservedSample(b);

            if (aObserved != bObserved) {
                return aObserved ? -1 : 1;
            }

            long aVal = maxBioValue(a);
            long bVal = maxBioValue(b);

            int cmp = Long.compare(bVal, aVal);
            if (cmp != 0) {
                return cmp;
            }

            double aDist = Double.isNaN(a.getDistanceLs()) ? Double.MAX_VALUE : a.getDistanceLs();
            double bDist = Double.isNaN(b.getDistanceLs()) ? Double.MAX_VALUE : b.getDistanceLs();
            return Double.compare(aDist, bDist);
        });

        List<Row> rows = new ArrayList<>();

        for (BodyInfo b : sorted) {
            rows.add(Row.body(b));

            if (!b.hasBio()) {
                continue;
            }

            // 1) Start from whatever predictions we already have
            List<ExobiologyData.BioCandidate> preds = b.getPredictions();

            // If there are no predictions yet, try a one-shot calculation here
            if (preds == null || preds.isEmpty()) {
                BodyAttributes attrs = null;
                try {
                    attrs = b.buildBodyAttributes();
                } catch (RuntimeException ex) {
                    System.out.println("Bio attrs not ready for " + b.getShortName() + " (" + b.getBodyId() + "): " + ex);
                }
                // Intentionally not doing one-shot compute here (you were experimenting with this).
                // Predictions should normally already be present on BodyInfo via SystemEventProcessor.
            }

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
                List<SpanshLandmark> landmarks = SpanshLandmarkCache.getInstance().getOrFetch(b.getStarSystem(), b.getBodyName());
                if (landmarks != null) {
                    b.setSpanshLandmarks(landmarks);
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

                        rows.add(Row.bio(b.getBodyId(), label, valueText));
                    }

                    continue;
                }

                // Collapse by genus: "Genus (n)" with max CR
                class GenusSummary {
                    int count = 0;
                    Long maxCr = null;
                }

                Map<String, GenusSummary> byGenus = new LinkedHashMap<>();

                for (BioRowData br : bioRows) {
                    String genus = firstWord(br.name);
                    GenusSummary summary = byGenus.get(genus);
                    if (summary == null) {
                        summary = new GenusSummary();
                        byGenus.put(genus, summary);
                    }
                    summary.count++;
                    if (br.cr != null) {
                        if (summary.maxCr == null || br.cr > summary.maxCr) {
                            summary.maxCr = br.cr;
                        }
                    }
                }

                for (Map.Entry<String, GenusSummary> e : byGenus.entrySet()) {
                    String genus = e.getKey();
                    GenusSummary gs = e.getValue();

                    String label = genus + " (" + gs.count + ")";
                    String valueText = "";
                    if (gs.maxCr != null) {
                        long millions = Math.round(gs.maxCr / 1_000_000.0);
                        valueText = String.format(Locale.US, "%dM Cr", millions);
                    }

                    rows.add(Row.bio(b.getBodyId(), label, valueText));
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
                        Row bio = Row.bio(b.getBodyId(), sr.name, valueText, samples);

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
                        rows.add(Row.bio(b.getBodyId(), name, valueText));
                    }
                }
            }
        }

        return rows;
    }

    private static boolean hasObservedSample(BodyInfo b) {
        if (b == null) {
            return false;
        }
        Set<String> observed = b.getObservedBioDisplayNames();
        if (observed == null) {
            return false;
        }
        return !observed.isEmpty();
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
            List<SpanshLandmark> landmarks = SpanshLandmarkCache.getInstance().getOrFetch(b.getStarSystem(), b.getBodyName());
            if (landmarks != null) {
                b.setSpanshLandmarks(landmarks);
            }
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
}
