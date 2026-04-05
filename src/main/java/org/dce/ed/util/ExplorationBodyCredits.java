package org.dce.ed.util;

import java.util.Locale;

import org.dce.ed.state.BodyInfo;

/**
 * Estimated exploration credits for planetary bodies (FSS + DSS with Odyssey mapping rules and efficient mapping).
 * Based on the Sep 2022 community formula (MattG, Frontier Forums — exploration value thread): raw {@code k + k·q·m^0.2},
 * mapping multipliers, +30% Odyssey mapping bonus (or 555 Cr min), ×1.25 efficient mapping, optional ×2.6 first discoverer.
 * <p>
 * This matches the breakdown other tools show (base scan value, “surface scan” scaling, efficiency uplift on the mapped total).
 */
public final class ExplorationBodyCredits {

    /** q constant from community formula. */
    public static final double Q = 0.56591828;

    private static final double MAP_DISCOVERED_BEFORE = 3.3333333333;
    private static final double MAP_FIRST_DISCOVER_AND_MAP = 3.699622554;
    private static final double MAP_FIRST_MAP_ONLY = 8.0956;

    /**
     * Short UI form without a {@code Cr} suffix, e.g. {@code 228K}, {@code 1.7M}.
     */
    public static String formatAbbreviatedCredits(long credits) {
        if (credits <= 0) {
            return "0";
        }
        if (credits >= 1_000_000L) {
            double m = credits / 1_000_000.0;
            String s = String.format(Locale.US, "%.1fM", m);
            if (s.endsWith(".0M")) {
                return s.substring(0, s.length() - 3) + "M";
            }
            return s;
        }
        if (credits >= 1_000L) {
            double k = credits / 1000.0;
            if (k >= 100) {
                return String.format(Locale.US, "%.0fK", k);
            }
            String s = String.format(Locale.US, "%.1fK", k);
            if (s.endsWith(".0K")) {
                return s.substring(0, s.length() - 3) + "K";
            }
            return s;
        }
        return String.format(Locale.US, "%d", credits);
    }

    /**
     * Intermediate amounts for tooltips (same pipeline as {@link #achievableTotalCredits(double, boolean, boolean)}).
     */
    public static final class AchievableBreakdown {
        public final long baseScanCredits;
        /** Credits attributed to DSS mapping multiplier ({@code raw × mappingMult}). */
        public final long afterMappingMultiplierCredits;
        public final double mappingMultiplier;
        /** Extra mapping payout vs the “already discovered” tier (×3.33…), when first-map bonuses apply. */
        public final long firstMappingBonusCredits;
        public final long odysseyMappingBonusCredits;
        public final long efficientMappingBonusCredits;
        /** After +25% efficient map, before floor(500) and first-discoverer ×2.6. */
        public final long subtotalBeforeFirstDiscoverCredits;
        public final long firstDiscovererBonusCredits;
        public final long totalCredits;

        AchievableBreakdown(long baseScanCredits,
                long afterMappingMultiplierCredits,
                double mappingMultiplier,
                long firstMappingBonusCredits,
                long odysseyMappingBonusCredits,
                long efficientMappingBonusCredits,
                long subtotalBeforeFirstDiscoverCredits,
                long firstDiscovererBonusCredits,
                long totalCredits) {
            this.baseScanCredits = baseScanCredits;
            this.afterMappingMultiplierCredits = afterMappingMultiplierCredits;
            this.mappingMultiplier = mappingMultiplier;
            this.firstMappingBonusCredits = firstMappingBonusCredits;
            this.odysseyMappingBonusCredits = odysseyMappingBonusCredits;
            this.efficientMappingBonusCredits = efficientMappingBonusCredits;
            this.subtotalBeforeFirstDiscoverCredits = subtotalBeforeFirstDiscoverCredits;
            this.firstDiscovererBonusCredits = firstDiscovererBonusCredits;
            this.totalCredits = totalCredits;
        }
    }

    private ExplorationBodyCredits() {
    }

    static double mappingMultiplier(boolean firstDiscoverer, boolean firstMapper) {
        if (firstDiscoverer && firstMapper) {
            return MAP_FIRST_DISCOVER_AND_MAP;
        }
        if (firstMapper) {
            return MAP_FIRST_MAP_ONLY;
        }
        return MAP_DISCOVERED_BEFORE;
    }

    /**
     * @return null if {@code rawScan <= 0}
     */
    public static AchievableBreakdown computeAchievableBreakdown(double rawScan, boolean firstDiscoverer, boolean firstMapper) {
        if (rawScan <= 0) {
            return null;
        }
        double mult = mappingMultiplier(firstDiscoverer, firstMapper);
        double afterMap = rawScan * mult;
        long afterMapCr = Math.round(afterMap);
        long firstMapBonus = 0L;
        if (mult > MAP_DISCOVERED_BEFORE + 1e-9) {
            firstMapBonus = Math.round(rawScan * mult - rawScan * MAP_DISCOVERED_BEFORE);
        }
        double odyssey = Math.max(afterMap * 0.3, 555.0);
        long odysseyCr = Math.round(odyssey);
        double afterOdyssey = afterMap + odyssey;
        long effCr = Math.round(afterOdyssey * 0.25);
        double afterEff = afterOdyssey * 1.25;
        double preFd = Math.max(500.0, afterEff);
        long preFdCr = Math.round(preFd);
        long total = Math.round(firstDiscoverer ? preFd * 2.6 : preFd);
        long fdBonus = 0L;
        if (firstDiscoverer) {
            fdBonus = total - preFdCr;
        }
        return new AchievableBreakdown(
                Math.round(rawScan),
                afterMapCr,
                mult,
                firstMapBonus,
                odysseyCr,
                effCr,
                preFdCr,
                fdBonus,
                total);
    }

    private static String lc(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /**
     * Prefer journal {@code PlanetClass}; if missing (common on partial cache rows), fall back to display
     * {@link BodyInfo#getAtmoOrType()} which often still contains e.g. "Earth-like world".
     */
    public static String explorationTypeHint(BodyInfo b) {
        if (b == null) {
            return "";
        }
        String pc = b.getPlanetClass();
        if (pc != null && !pc.isBlank()) {
            return pc;
        }
        String at = b.getAtmoOrType();
        return at != null ? at.trim() : "";
    }

    /**
     * ELW / WW / ammonia world / terraformable tier — matches the bodies we show exploration estimates for.
     * Uses {@link #explorationTypeHint(BodyInfo)} so cached rows still work when {@code PlanetClass} was stored
     * only in {@link BodyInfo#getAtmoOrType()}.
     */
    public static boolean isJournalValuableExplorationTarget(BodyInfo b) {
        if (b == null) {
            return false;
        }
        return isJournalValuableExplorationTarget(explorationTypeHint(b), b.getTerraformState());
    }

    /**
     * @param typeHint planet class or atmosphere/type line (see {@link #explorationTypeHint(BodyInfo)})
     */
    public static boolean isJournalValuableExplorationTarget(String typeHint, String terraformState) {
        String pc = lc(typeHint);
        if (pc.contains("earth-like") || pc.contains("earthlike")) {
            return true;
        }
        if (pc.contains("water world") || pc.contains("ammonia world")) {
            return true;
        }
        return TerraformingUtil.isTerraformableExplorationTier(terraformState);
    }

    /**
     * Sets {@link BodyInfo#setHighValue(boolean)} from classifiers on the body (journal + merged cache/EDSM).
     */
    public static void syncHighValueExplorationFromClassifiers(BodyInfo b) {
        if (b == null) {
            return;
        }
        b.setHighValue(isJournalValuableExplorationTarget(b));
    }

    /**
     * Exploration coefficient k (combined base + terraformable tier when applicable).
     */
    public static int explorationK(String planetClass, String terraformState) {
        String pc = lc(planetClass);
        boolean terraformable = TerraformingUtil.isTerraformableExplorationTier(terraformState);
        if (pc.contains("earth-like") || pc.contains("earthlike")) {
            return 64831 + 116295;
        }
        if (pc.contains("ammonia world")) {
            return 96932;
        }
        if (pc.contains("water world")) {
            return terraformable ? 64831 + 116295 : 64831;
        }
        if (pc.contains("metal rich")) {
            return terraformable ? 21790 + 105678 : 21790;
        }
        if (pc.contains("high metal content")) {
            return terraformable ? 9654 + 100677 : 9654;
        }
        if (terraformable) {
            return 300 + 93328;
        }
        return 0;
    }

    /**
     * Default Earth-masses when the journal/EDSM has not provided {@link BodyInfo#getMassEm()}.
     */
    public static double defaultMassEm(String planetClass, String terraformState) {
        String pc = lc(planetClass);
        if (pc.contains("earth-like") || pc.contains("earthlike")) {
            return 1.0;
        }
        if (pc.contains("water world")) {
            return 3.0;
        }
        if (pc.contains("ammonia world")) {
            return 2.0;
        }
        if (pc.contains("high metal content") || pc.contains("metal rich")) {
            return 0.6;
        }
        if (TerraformingUtil.isTerraformableExplorationTier(terraformState)) {
            return 0.35;
        }
        return 0.5;
    }

    /**
     * Raw FSS-style scan value before mapping multipliers ({@code k + k·q·m^0.2}).
     */
    public static double rawScanValue(long k, double massEarth) {
        if (k <= 0 || massEarth <= 0 || Double.isNaN(massEarth) || Double.isInfinite(massEarth)) {
            return 0.0;
        }
        return k + k * Q * Math.pow(massEarth, 0.2);
    }

    /**
     * Full achievable credits assuming DSS with efficient mapping (and Odyssey +30% mapping rule).
     *
     * @param firstDiscoverer {@code true} when the body was not previously discovered ({@code WasDiscovered == false}).
     * @param firstMapper     {@code true} when the commander is expected to be first mapper (first discoverer and not yet mapped).
     */
    public static long achievableTotalCredits(double rawScan, boolean firstDiscoverer, boolean firstMapper) {
        AchievableBreakdown d = computeAchievableBreakdown(rawScan, firstDiscoverer, firstMapper);
        return d == null ? 0L : d.totalCredits;
    }

    /**
     * Convenience: total from k and mass (Earth masses).
     */
    public static long achievableTotalCredits(long k, double massEarth, boolean firstDiscoverer, boolean firstMapper) {
        return achievableTotalCredits(rawScanValue(k, massEarth), firstDiscoverer, firstMapper);
    }

    /**
     * Estimated achievable exploration payout for a high-value body loaded in the system table.
     */
    public static long achievableExplorationTotalCredits(BodyInfo b) {
        if (b == null || !b.isHighValue()) {
            return 0L;
        }
        String typeHint = explorationTypeHint(b);
        int k = explorationK(typeHint, b.getTerraformState());
        if (k <= 0) {
            return 0L;
        }
        Double massObj = b.getMassEm();
        double mass = massObj != null && massObj.doubleValue() > 0
                ? massObj.doubleValue()
                : defaultMassEm(typeHint, b.getTerraformState());
        boolean firstDiscoverer = Boolean.FALSE.equals(b.getWasDiscovered());
        boolean firstMapper = firstDiscoverer && !Boolean.TRUE.equals(b.getWasMapped());
        return achievableTotalCredits(k, mass, firstDiscoverer, firstMapper);
    }

    /**
     * Full breakdown for UI tooltips, or null if not a formula-backed high-value body.
     */
    public static AchievableBreakdown computeAchievableBreakdown(BodyInfo b) {
        if (b == null || !b.isHighValue()) {
            return null;
        }
        String typeHint = explorationTypeHint(b);
        int k = explorationK(typeHint, b.getTerraformState());
        if (k <= 0) {
            return null;
        }
        Double massObj = b.getMassEm();
        double mass = massObj != null && massObj.doubleValue() > 0
                ? massObj.doubleValue()
                : defaultMassEm(typeHint, b.getTerraformState());
        double raw = rawScanValue(k, mass);
        if (raw <= 0) {
            return null;
        }
        boolean firstDiscoverer = Boolean.FALSE.equals(b.getWasDiscovered());
        boolean firstMapper = firstDiscoverer && !Boolean.TRUE.equals(b.getWasMapped());
        return computeAchievableBreakdown(raw, firstDiscoverer, firstMapper);
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Multi-line HTML tooltip for the Value column, or null when no breakdown is available.
     */
    public static String formatExplorationTooltipHtml(BodyInfo b) {
        AchievableBreakdown d = computeAchievableBreakdown(b);
        if (d == null) {
            return null;
        }
        String name = b.getShortName();
        if (name == null || name.isBlank()) {
            name = b.getBodyName();
        }
        if (name == null) {
            name = "";
        }
        String multStr = String.format(Locale.US, "%.4g", Double.valueOf(d.mappingMultiplier));
        StringBuilder sb = new StringBuilder(512);
        sb.append("<html><body style='font-size:11px;text-align:left'>");
        sb.append("<b>").append(esc(name)).append("</b><br/>");
        sb.append("<span style='color:gray'>FSS + DSS estimate (community formula)</span><br/><br/>");
        sb.append("Base scan value: ").append(formatAbbreviatedCredits(d.baseScanCredits)).append(" Cr<br/>");
        sb.append("First discoverer bonus: ");
        sb.append(d.firstDiscovererBonusCredits > 0 ? formatAbbreviatedCredits(d.firstDiscovererBonusCredits) : "0");
        sb.append(" Cr<br/>");
        sb.append("Surface scan value (×").append(multStr).append("): ");
        sb.append(formatAbbreviatedCredits(d.afterMappingMultiplierCredits)).append(" Cr<br/>");
        if (d.firstMappingBonusCredits > 0) {
            sb.append("First surface scan bonus: ").append(formatAbbreviatedCredits(d.firstMappingBonusCredits)).append(" Cr<br/>");
        }
        sb.append("Odyssey mapping bonus (+30%): ").append(formatAbbreviatedCredits(d.odysseyMappingBonusCredits)).append(" Cr<br/>");
        sb.append("Efficiently scanned bonus (+25%): ").append(formatAbbreviatedCredits(d.efficientMappingBonusCredits)).append(" Cr<br/>");
        sb.append("<hr size='1'/>");
        sb.append("<b>Achievable total: ").append(formatAbbreviatedCredits(d.totalCredits)).append(" Cr</b>");
        sb.append("</body></html>");
        return sb.toString();
    }
}
