package org.dce.ed.mission;

/**
 * Derives objective vs turn-in destinations from mission category and journal fields.
 */
public final class MissionDestinationResolver {

    private MissionDestinationResolver() {
    }

    public static MissionDestination objectiveFor(MissionRecord r) {
        if (r == null) {
            return empty();
        }
        return switch (r.getCategory()) {
            case COMMODITY -> {
                String commodity = r.getCommodityLocalised();
                if (commodity == null || commodity.isBlank()) {
                    yield new MissionDestination(null, null, null);
                }
                int count = r.getCountRequired() > 0 ? r.getCountRequired() : r.getTotalItemsToDeliver();
                String label = count > 0
                        ? count + " " + commodity
                        : commodity;
                yield new MissionDestination(null, null, label);
            }
            case DONATION -> {
                long d = r.getDonation();
                yield new MissionDestination(null, null,
                        d > 0 ? "Donate " + formatCredits(d) : "Donate credits");
            }
            case COMBAT -> combatObjective(r);
            case COURIER, PASSENGER, UNKNOWN -> turnInFromRecord(r);
        };
    }

    public static MissionDestination turnInFor(MissionRecord r) {
        if (r == null) {
            return empty();
        }
        if (r.getCategory() == MissionCategory.COMMODITY) {
            return turnInFromRecord(r);
        }
        if (r.getCategory() == MissionCategory.DONATION) {
            return turnInFromRecord(r);
        }
        MissionDestination dest = turnInFromRecord(r);
        if (!dest.isEmpty()) {
            return dest;
        }
        return new MissionDestination(null, null, "Claim at destination");
    }

    /**
     * Assassination → named target; massacre → {@code done/required} (e.g. {@code 23/48 pirates}).
     * Kill progress comes from {@code Bounty} events gated by hunt {@code DestinationSystem}
     * and {@code TargetFaction} (see {@link MissionTracker}).
     * When redirected (objective done), show {@code 48/48 pirates}.
     */
    static MissionDestination combatObjective(MissionRecord r) {
        String namedTarget = firstNonBlank(r.getTarget());
        if (namedTarget != null && r.getKillCount() <= 1) {
            // Assassinate / single-target combat: show the individual.
            return new MissionDestination(null, null, namedTarget);
        }
        if (r.getKillCount() > 0) {
            String noun = combatKillNoun(r);
            if (r.isRedirected()) {
                return new MissionDestination(null, null,
                        r.getKillCount() + "/" + r.getKillCount() + " " + noun);
            }
            int done = Math.min(r.getKillsCompleted(), r.getKillCount());
            return new MissionDestination(null, null, done + "/" + r.getKillCount() + " " + noun);
        }
        if (namedTarget != null) {
            return new MissionDestination(null, null, namedTarget);
        }
        String faction = firstNonBlank(r.getTargetFaction());
        if (faction != null) {
            return new MissionDestination(null, null, faction);
        }
        return turnInFromRecord(r);
    }

    /** Plural noun for massacre progress, e.g. {@code pirates}. */
    static String combatKillNoun(MissionRecord r) {
        String type = firstNonBlank(r.getTargetTypeLocalised());
        if (type != null) {
            return pluralizeKillNoun(type);
        }
        String faction = firstNonBlank(r.getTargetFaction());
        if (faction != null) {
            return faction;
        }
        return "kills";
    }

    static String pluralizeKillNoun(String localisedType) {
        String t = localisedType.trim();
        if (t.isEmpty()) {
            return "kills";
        }
        String lower = Character.toLowerCase(t.charAt(0)) + (t.length() > 1 ? t.substring(1) : "");
        if (lower.endsWith("s") || lower.endsWith("S")) {
            return lower;
        }
        if (lower.endsWith("y") && lower.length() > 1
                && !isVowel(lower.charAt(lower.length() - 2))) {
            return lower.substring(0, lower.length() - 1) + "ies";
        }
        return lower + "s";
    }

    private static boolean isVowel(char c) {
        return "aeiouAEIOU".indexOf(c) >= 0;
    }

    private static String firstNonBlank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static MissionDestination turnInFromRecord(MissionRecord r) {
        return new MissionDestination(
                r.getDestinationSystem(),
                r.getDestinationStation(),
                r.getDestinationSettlement());
    }

    private static MissionDestination empty() {
        return new MissionDestination(null, null, null);
    }

    private static String formatCredits(long amount) {
        if (amount >= 1_000_000) {
            return String.format("%,d Cr", amount);
        }
        return amount + " Cr";
    }
}
