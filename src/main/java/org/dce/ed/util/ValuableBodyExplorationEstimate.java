package org.dce.ed.util;

import java.util.Locale;

import org.dce.ed.state.BodyInfo;

/**
 * Fallback tier credits when mass/terraform data is missing, plus display formatting.
 * Primary estimates use {@link ExplorationBodyCredits} (FSS + DSS + Odyssey mapping + efficient map).
 */
public final class ValuableBodyExplorationEstimate {

    private static final long ELW_TYPICAL = 1_200_000L;
    private static final long WW_AW_TYPICAL = 500_000L;

    /** Used when a body is high-value but class/terraform parsing yields no tier (should be rare). */
    public static final long TERRAFORMABLE_FALLBACK = 300_000L;

    private ValuableBodyExplorationEstimate() {
    }

    private static String toLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /**
     * @return credits to display, or null if the body is not a valuable exploration target by class/terraform
     */
    public static Long estimateCredits(String planetClass, String terraformState) {
        String pc = toLower(planetClass);
        boolean terraformable = TerraformingUtil.isTerraformableExplorationTier(terraformState);
        if (pc.contains("earth-like") || pc.contains("earthlike")) {
            return Long.valueOf(ELW_TYPICAL);
        }
        if (pc.contains("water world")) {
            return Long.valueOf(WW_AW_TYPICAL);
        }
        if (pc.contains("ammonia world")) {
            return Long.valueOf(WW_AW_TYPICAL);
        }
        if (terraformable) {
            return Long.valueOf(TERRAFORMABLE_FALLBACK);
        }
        return null;
    }

    /**
     * Credits for UI: {@link ExplorationBodyCredits} when possible, else persisted/cache tier fallback.
     */
    public static long resolveCreditsForDisplay(BodyInfo b) {
        if (b == null || !b.isHighValue()) {
            return 0L;
        }
        long fromFormula = ExplorationBodyCredits.achievableExplorationTotalCredits(b);
        if (fromFormula > 0) {
            return fromFormula;
        }
        Long stored = b.getValuableBodyExplorationCredits();
        if (stored != null && stored.longValue() > 0) {
            return stored.longValue();
        }
        Long est = estimateCredits(ExplorationBodyCredits.explorationTypeHint(b), b.getTerraformState());
        return est != null ? est.longValue() : TERRAFORMABLE_FALLBACK;
    }

    /**
     * When {@link ExplorationBodyCredits#formatExplorationTooltipHtml} has no line breakdown, explain the cell total.
     */
    public static String formatHighValueFallbackTooltipHtml(BodyInfo b) {
        if (b == null || !b.isHighValue()) {
            return null;
        }
        long cr = resolveCreditsForDisplay(b);
        if (cr <= 0) {
            return null;
        }
        boolean hasMass = b.getMassEm() != null && b.getMassEm().doubleValue() > 0;
        boolean hasK = ExplorationBodyCredits.explorationK(
                ExplorationBodyCredits.explorationTypeHint(b), b.getTerraformState()) > 0;
        StringBuilder sb = new StringBuilder(400);
        sb.append("<html><body style='font-size:11px;text-align:left'>");
        sb.append("<b>Total shown: ").append(formatCredits(cr)).append("</b><br/><br/>");
        if (!hasK) {
            sb.append("Line-by-line exploration math needs a recognized world type (e.g. Earth-like / water world / terraformable). ");
            sb.append("This row may be missing planet class in cache; jump or re-scan can refresh it.<br/><br/>");
        }
        if (!hasMass) {
            sb.append("<b>MassEM</b> in the journal is Earth masses. Elite usually adds it on a <b>detailed surface scan</b>, ");
            sb.append("not on FSS discovery alone. The overlay keeps MassEM after the first scan that includes it.<br/><br/>");
        } else {
            sb.append("Earth masses (MassEM) are stored for this body.<br/><br/>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    public static String formatCredits(long credits) {
        if (credits <= 0) {
            return "";
        }
        return ExplorationBodyCredits.formatAbbreviatedCredits(credits) + " Cr";
    }
}
