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
            case COURIER, COMBAT, PASSENGER, UNKNOWN -> turnInFromRecord(r);
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
