package org.dce.ed.engineering;

import java.util.Optional;

/**
 * Fixed material trader exchange math (Elite Dangerous).
 *
 * <p>Cross-category rates follow the encoded-trader conversion table (6× per grade step,
 * with 2:1 or 36:1 for adjacent grades across categories). Same-line rates use 6:1 up
 * and 3:1 down per grade within a category row.
 */
public final class MaterialTradeRateCalculator {

    private MaterialTradeRateCalculator() {
    }

    /**
     * @return units of {@code from} required to receive 1 unit of {@code to}
     */
    public static int inputPerOneOutput(EngineeringMaterial from, EngineeringMaterial to) {
        if (from == null || to == null) {
            return Integer.MAX_VALUE;
        }
        if (!MaterialTraderCatalog.isTradeableAtMaterialTrader(from)
                || !MaterialTraderCatalog.isTradeableAtMaterialTrader(to)) {
            return Integer.MAX_VALUE;
        }
        if (!from.getType().equalsIgnoreCase(to.getType())) {
            return Integer.MAX_VALUE;
        }
        if (from.getKey().equalsIgnoreCase(to.getKey()) && from.getGrade() == to.getGrade()) {
            return 1;
        }

        int fromGrade = from.getGrade();
        int toGrade = to.getGrade();
        boolean sameGroup = from.getSubtype().equalsIgnoreCase(to.getSubtype());

        if (sameGroup) {
            return sameCategoryInputPerOneOutput(fromGrade, toGrade);
        }
        return crossCategoryInputPerOneOutput(fromGrade, toGrade);
    }

    /**
     * @return units of {@code to} received for 1 unit of {@code from} when the trader pays out
     *         multiple lower-grade units (same-row downgrade). Returns 1 when the linear
     *         {@link #inputPerOneOutput} model applies.
     */
    public static int outputPerOneInput(EngineeringMaterial from, EngineeringMaterial to) {
        if (from == null || to == null) {
            return 0;
        }
        if (!MaterialTraderCatalog.isTradeableAtMaterialTrader(from)
                || !MaterialTraderCatalog.isTradeableAtMaterialTrader(to)) {
            return 0;
        }
        if (!from.getType().equalsIgnoreCase(to.getType())) {
            return 0;
        }
        if (from.getKey().equalsIgnoreCase(to.getKey()) && from.getGrade() == to.getGrade()) {
            return 1;
        }

        int fromGrade = from.getGrade();
        int toGrade = to.getGrade();
        boolean sameGroup = from.getSubtype().equalsIgnoreCase(to.getSubtype());

        if (sameGroup && toGrade < fromGrade) {
            return boundedPow(3, fromGrade - toGrade);
        }
        return 1;
    }

    /**
     * Plans a single exchange using available {@code from} stock to cover up to {@code outputsWanted}
     * units of {@code to}.
     */
    public static Optional<Exchange> planExchange(EngineeringMaterial from,
                                                  EngineeringMaterial to,
                                                  int availableFrom,
                                                  int outputsWanted) {
        if (availableFrom <= 0 || outputsWanted <= 0) {
            return Optional.empty();
        }
        int yield = outputPerOneInput(from, to);
        if (yield > 1) {
            int inputs = Math.min(availableFrom, boundedRatio(outputsWanted, yield));
            if (inputs <= 0) {
                return Optional.empty();
            }
            int outputs = Math.min(outputsWanted, inputs * yield);
            return Optional.of(new Exchange(inputs, outputs));
        }

        int rate = inputPerOneOutput(from, to);
        if (rate <= 0 || rate == Integer.MAX_VALUE) {
            return Optional.empty();
        }
        int maxOut = availableFrom / rate;
        if (maxOut <= 0) {
            return Optional.empty();
        }
        int outputs = Math.min(maxOut, outputsWanted);
        return Optional.of(new Exchange(outputs * rate, outputs));
    }

    /** One material-trader swap: pay {@code fromCount}, receive {@code toCount}. */
    public static final class Exchange {
        private final int fromCount;
        private final int toCount;

        public Exchange(int fromCount, int toCount) {
            this.fromCount = Math.max(0, fromCount);
            this.toCount = Math.max(0, toCount);
        }

        public int getFromCount() {
            return fromCount;
        }

        public int getToCount() {
            return toCount;
        }
    }

    /** Same trader row (e.g. both Encoded Firmware). */
    private static int sameCategoryInputPerOneOutput(int fromGrade, int toGrade) {
        if (fromGrade == toGrade) {
            return 1;
        }
        if (toGrade > fromGrade) {
            return boundedPow(6, toGrade - fromGrade);
        }
        long numer = 1;
        long denom = pow(3, fromGrade - toGrade);
        return boundedRatio(numer, denom);
    }

    /**
     * Different trader row within the same material type (e.g. Wake Scans → Encoded Firmware).
     *
     * <p>Two-step model used in-game: cross at {@code fromGrade} (6:1), then adjust grade within
     * the target row. Upgrades multiply by 6 per grade; downgrades from G4+ use the same-row 6:1
     * table, while downgrades from G3 and below use the 3:1 yield split.
     */
    private static int crossCategoryInputPerOneOutput(int fromGrade, int toGrade) {
        if (fromGrade == toGrade) {
            return 6;
        }
        long rate = 6;
        if (toGrade > fromGrade) {
            rate *= pow(6, toGrade - fromGrade);
        } else {
            int steps = fromGrade - toGrade;
            if (fromGrade >= 4) {
                rate *= pow(6, steps);
            } else {
                rate = boundedRatio(rate, pow(3, steps));
            }
        }
        if (rate <= 0) {
            return 1;
        }
        if (rate > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) rate;
    }

    private static int boundedRatio(long numer, long denom) {
        if (denom <= 0) {
            return Integer.MAX_VALUE;
        }
        long result = (numer + denom - 1) / denom;
        if (result <= 0) {
            return 1;
        }
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) result;
    }

    private static int boundedPow(int base, int exp) {
        long result = pow(base, exp);
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) result;
    }

    private static long pow(int base, int exp) {
        long v = 1;
        for (int i = 0; i < exp; i++) {
            v *= base;
        }
        return v;
    }
}
