package org.dce.ed.engineering;



import java.util.Optional;



/**

 * Fixed material trader exchange math (Elite Dangerous).

 *

 * <p>Rates match the official exchange tables on the

 * <a href="https://elite-dangerous.fandom.com/wiki/Material_Trader">Material Trader wiki</a>:

 * same-row trades use 6:1 per grade up and 1:3 per grade down; cross-row trades use the

 * published 5×5 matrix (e.g. grade-4 → grade-3 across rows is 2:1, not 36:1).

 */

public final class MaterialTradeRateCalculator {



    /**

     * Cross-category table indexed by [outputGrade - 1][inputGrade - 1] = {inputs, outputs}.

     * Rows are output grade, columns are input grade (wiki orientation).

     */

    private static final int[][][] CROSS_CATEGORY = {

            { {6, 1}, {2, 1}, {2, 3}, {2, 9}, {2, 27} },

            { {36, 1}, {6, 1}, {2, 1}, {2, 3}, {2, 9} },

            { {216, 1}, {36, 1}, {6, 1}, {2, 1}, {2, 3} },

            { {1296, 1}, {216, 1}, {36, 1}, {6, 1}, {2, 1} },

            { {7776, 1}, {1296, 1}, {216, 1}, {36, 1}, {6, 1} },

    };



    private MaterialTradeRateCalculator() {

    }



    /**

     * @return units of {@code from} required to receive 1 unit of {@code to} when the trade is

     *         a whole-number linear rate; otherwise {@code 1} when batch ratios apply (see

     *         {@link #planExchange}).

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



        ExchangeRatio ratio = exchangeRatio(from, to);

        if (ratio.outputs() <= 0) {

            return Integer.MAX_VALUE;

        }

        return boundedRatio(ratio.inputs(), ratio.outputs());

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

        if (!MaterialTraderCatalog.isTradeableAtMaterialTrader(from)

                || !MaterialTraderCatalog.isTradeableAtMaterialTrader(to)) {

            return Optional.empty();

        }

        if (!from.getType().equalsIgnoreCase(to.getType())) {

            return Optional.empty();

        }

        if (from.getKey().equalsIgnoreCase(to.getKey()) && from.getGrade() == to.getGrade()) {

            int outputs = Math.min(availableFrom, outputsWanted);

            return outputs > 0 ? Optional.of(new Exchange(outputs, outputs)) : Optional.empty();

        }



        ExchangeRatio ratio = exchangeRatio(from, to);

        if (ratio.inputs() <= 0 || ratio.outputs() <= 0) {

            return Optional.empty();

        }

        int g = gcd(ratio.inputs(), ratio.outputs());

        return planBatchExchange(

                ratio.inputs() / g, ratio.outputs() / g, availableFrom, outputsWanted);

    }



    private static ExchangeRatio exchangeRatio(EngineeringMaterial from, EngineeringMaterial to) {

        int fromGrade = clampGrade(from.getGrade());

        int toGrade = clampGrade(to.getGrade());

        if (from.getSubtype().equalsIgnoreCase(to.getSubtype())) {

            return sameCategoryRatio(fromGrade, toGrade);

        }

        return crossCategoryRatio(fromGrade, toGrade);

    }



    /** Same trader row (wiki "Materials in the same category"). */

    private static ExchangeRatio sameCategoryRatio(int fromGrade, int toGrade) {

        if (fromGrade == toGrade) {

            return new ExchangeRatio(1, 1);

        }

        if (toGrade > fromGrade) {

            int steps = toGrade - fromGrade;

            return new ExchangeRatio(boundedPow(6, steps), 1);

        }

        int steps = fromGrade - toGrade;

        return new ExchangeRatio(1, boundedPow(3, steps));

    }



    /** Different trader row (wiki "Materials in different categories"). */

    private static ExchangeRatio crossCategoryRatio(int fromGrade, int toGrade) {

        int[] cell = CROSS_CATEGORY[toGrade - 1][fromGrade - 1];

        return new ExchangeRatio(cell[0], cell[1]);

    }



    private static int clampGrade(int grade) {

        return Math.max(1, Math.min(5, grade));

    }



    private static Optional<Exchange> planBatchExchange(int inputsPerBatch,

                                                        int outputsPerBatch,

                                                        int availableFrom,

                                                        int outputsWanted) {

        if (inputsPerBatch <= 0 || outputsPerBatch <= 0 || outputsWanted <= 0) {

            return Optional.empty();

        }

        int batchesForWant = boundedRatio(outputsWanted, outputsPerBatch);

        int batchesByStock = availableFrom / inputsPerBatch;

        int batches = Math.min(batchesForWant, batchesByStock);

        if (batches <= 0) {

            return Optional.empty();

        }

        // Traders always pay the full batch; partial receive counts are not possible.
        int outputs = batches * outputsPerBatch;

        int inputs = batches * inputsPerBatch;

        return Optional.of(new Exchange(inputs, outputs));

    }



    /** Pay {@code inputs} units of the source material to receive {@code outputs} of the target. */

    private record ExchangeRatio(int inputs, int outputs) {

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



    private static int gcd(int a, int b) {

        a = Math.abs(a);

        b = Math.abs(b);

        while (b != 0) {

            int t = a % b;

            a = b;

            b = t;

        }

        return Math.max(a, 1);

    }

}


