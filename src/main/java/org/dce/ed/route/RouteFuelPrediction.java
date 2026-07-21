package org.dce.ed.route;

import java.util.List;
import java.util.Locale;

import org.dce.ed.logreader.event.LoadoutEvent;

/**
 * Predicts where along the plotted route the ship runs out of fuel, using the hyperspace
 * fuel equation {@code fuel = r * 1e-3 * (dist * mass / optimalMass)^p}.
 * <p>
 * FSD parameters come from the journal {@code Loadout} event: stock values from the tables
 * below, overridden by Engineering modifiers ({@code FSDOptimalMass} / {@code MaxFuelPerJump})
 * when present. Ships with a fuel scoop are assumed to scoop to full at every scoopable
 * (KGBFOAM) star on the route, so warnings only appear for unscoopable stretches longer than
 * a tank, or when current fuel can't reach the next scoopable star.
 */
public final class RouteFuelPrediction {

    public enum RowFuelState {
        /** Reachable on predicted fuel. */
        REACHABLE,
        /** Last system reachable before the tank runs dry. */
        LAST_REACHABLE,
        /** Predicted out of fuel before arriving here. */
        UNREACHABLE
    }

    /** Ship + FSD parameters needed by the fuel equation (immutable snapshot of a Loadout). */
    public static final class ShipFuelProfile {
        final double unladenMass;
        final double fuelCapacityMain;
        final double fuelCapacityReserve;
        final double optimalMass;
        final double maxFuelPerJump;
        /** Linear constant r (per-mille factor applied in the equation). */
        final double linearConstant;
        final double powerConstant;
        final boolean hasFuelScoop;
        /** Guardian FSD booster bonus in Ly (0 when absent). */
        final double guardianBoostLy;

        ShipFuelProfile(double unladenMass, double fuelCapacityMain, double fuelCapacityReserve,
                double optimalMass, double maxFuelPerJump, double linearConstant, double powerConstant,
                boolean hasFuelScoop, double guardianBoostLy) {
            this.unladenMass = unladenMass;
            this.fuelCapacityMain = fuelCapacityMain;
            this.fuelCapacityReserve = fuelCapacityReserve;
            this.optimalMass = optimalMass;
            this.maxFuelPerJump = maxFuelPerJump;
            this.linearConstant = linearConstant;
            this.powerConstant = powerConstant;
            this.hasFuelScoop = hasFuelScoop;
            this.guardianBoostLy = guardianBoostLy;
        }

        public boolean hasFuelScoop() {
            return hasFuelScoop;
        }

        public double fuelCapacityMain() {
            return fuelCapacityMain;
        }
    }

    /** Per-row prediction for one displayed route snapshot; indexes match the displayed entries. */
    public static final class Result {
        private final RowFuelState[] states;
        private final double[] fuelOnArrivalTons;
        private final double fuelCapacityMain;
        private final boolean assumesScooping;

        Result(RowFuelState[] states, double[] fuelOnArrivalTons, double fuelCapacityMain, boolean assumesScooping) {
            this.states = states;
            this.fuelOnArrivalTons = fuelOnArrivalTons;
            this.fuelCapacityMain = fuelCapacityMain;
            this.assumesScooping = assumesScooping;
        }

        /** @return state for the row, or null when no prediction applies (behind current, body row, unknown). */
        public RowFuelState stateAt(int row) {
            return states != null && row >= 0 && row < states.length ? states[row] : null;
        }

        /** @return predicted main-tank tons on arrival at the row, or NaN when unknown. */
        public double fuelOnArrivalAt(int row) {
            return fuelOnArrivalTons != null && row >= 0 && row < fuelOnArrivalTons.length
                    ? fuelOnArrivalTons[row]
                    : Double.NaN;
        }

        public double fuelCapacityMain() {
            return fuelCapacityMain;
        }

        /** True when the simulation refuels at scoopable stars (ship carries a fuel scoop). */
        public boolean assumesScooping() {
            return assumesScooping;
        }
    }

    private static final double EPSILON = 1e-6;

    /** Ratings indexed E, D, C, B, A (journal class digit 1..5). */
    private static final double[] LINEAR_CONSTANT_STANDARD = {11, 10, 8, 10, 12};
    private static final double[] LINEAR_CONSTANT_SCO = {8, 12, 12, 12, 13};

    /** Stock optimal mass (tons), [size 2..7][rating E..A]. */
    private static final double[][] OPTIMAL_MASS_STANDARD = {
            {48, 54, 60, 75, 90},
            {80, 90, 100, 125, 150},
            {280, 315, 350, 438, 525},
            {560, 630, 700, 875, 1050},
            {960, 1080, 1200, 1500, 1800},
            {1440, 1620, 1800, 2250, 2700},
    };
    private static final double[][] MAX_FUEL_STANDARD = {
            {0.60, 0.60, 0.60, 0.80, 0.90},
            {1.20, 1.20, 1.20, 1.50, 1.80},
            {2.00, 2.00, 2.00, 2.50, 3.00},
            {3.30, 3.30, 3.30, 4.10, 5.00},
            {5.30, 5.30, 5.30, 6.60, 8.00},
            {8.50, 8.50, 8.50, 10.60, 12.80},
    };
    /** SCO drives ({@code int_hyperdrive_overcharge_*}), same indexing. */
    private static final double[][] OPTIMAL_MASS_SCO = {
            {60, 90, 90, 90, 100},
            {100, 150, 150, 150, 167},
            {350, 525, 525, 525, 585},
            {700, 1050, 1050, 1050, 1175},
            {1200, 1800, 1800, 1800, 2000},
            {1800, 2700, 2700, 2700, 3000},
    };
    private static final double[][] MAX_FUEL_SCO = {
            {0.60, 0.90, 0.90, 0.90, 1.00},
            {1.20, 1.80, 1.80, 1.80, 1.90},
            {2.00, 3.00, 3.00, 3.00, 3.20},
            {3.30, 5.00, 5.00, 5.00, 5.20},
            {5.30, 8.00, 8.00, 8.00, 8.30},
            {8.50, 12.80, 12.80, 12.80, 13.10},
    };

    /** Guardian FSD booster jump-range bonus (Ly) by module size 1..5. */
    private static final double[] GUARDIAN_BOOST_LY = {4.00, 6.00, 7.75, 9.25, 10.50};

    private RouteFuelPrediction() {
    }

    /**
     * Builds the fuel profile from a Loadout event.
     *
     * @return profile, or null when the FSD (or its constants) can't be identified
     */
    public static ShipFuelProfile profileFromLoadout(LoadoutEvent loadout) {
        if (loadout == null) {
            return null;
        }
        LoadoutEvent.Module fsd = null;
        boolean scoop = false;
        double boostLy = 0;
        for (LoadoutEvent.Module m : loadout.getModules()) {
            String item = m.getItem() != null ? m.getItem().toLowerCase(Locale.ROOT) : "";
            if (item.startsWith("int_hyperdrive")) {
                fsd = m;
            } else if (item.startsWith("int_fuelscoop")) {
                scoop = true;
            } else if (item.startsWith("int_guardianfsdbooster_size")) {
                int size = parseTrailingDigit(item);
                if (size >= 1 && size <= GUARDIAN_BOOST_LY.length) {
                    boostLy = GUARDIAN_BOOST_LY[size - 1];
                }
            }
        }
        if (fsd == null) {
            return null;
        }
        String item = fsd.getItem().toLowerCase(Locale.ROOT);
        boolean sco = item.contains("overcharge");
        int size = extractIntAfter(item, "size");
        int classDigit = extractIntAfter(item, "class");
        if (size < 2 || size > 7 || classDigit < 1 || classDigit > 5) {
            return null;
        }
        int ratingIdx = classDigit - 1; // 1=E .. 5=A
        double optMass = (sco ? OPTIMAL_MASS_SCO : OPTIMAL_MASS_STANDARD)[size - 2][ratingIdx];
        double maxFuel = (sco ? MAX_FUEL_SCO : MAX_FUEL_STANDARD)[size - 2][ratingIdx];
        double linear = (sco ? LINEAR_CONSTANT_SCO : LINEAR_CONSTANT_STANDARD)[ratingIdx];
        double power = 2.0 + 0.15 * (size - 2);

        LoadoutEvent.Engineering eng = fsd.getEngineering();
        if (eng != null) {
            for (LoadoutEvent.Modifier mod : eng.getModifiers()) {
                String label = mod.getLabel() != null ? mod.getLabel() : "";
                if ("FSDOptimalMass".equalsIgnoreCase(label) && mod.getValue() > 0) {
                    optMass = mod.getValue();
                } else if ("MaxFuelPerJump".equalsIgnoreCase(label) && mod.getValue() > 0) {
                    maxFuel = mod.getValue();
                }
            }
        }

        double capMain = loadout.getFuelCapacity() != null ? loadout.getFuelCapacity().getMain() : 0;
        double capReserve = loadout.getFuelCapacity() != null ? loadout.getFuelCapacity().getReserve() : 0;
        if (capMain <= 0 || optMass <= 0 || maxFuel <= 0) {
            return null;
        }
        return new ShipFuelProfile(loadout.getUnladenMass(), capMain, capReserve,
                optMass, maxFuel, linear, power, scoop, boostLy);
    }

    /** Fuel (tons) for one hyperspace jump of {@code distanceLy} at total ship mass {@code massTons}. */
    static double fuelForJump(ShipFuelProfile p, double distanceLy, double massTons) {
        double dist = distanceLy;
        if (p.guardianBoostLy > 0) {
            // Booster stretches range for the same fuel: scale distance by baseMax / (baseMax + boost).
            double baseMax = (p.optimalMass / massTons) * Math.pow(1000.0 * p.maxFuelPerJump / p.linearConstant,
                    1.0 / p.powerConstant);
            if (baseMax > 0) {
                dist = distanceLy * baseMax / (baseMax + p.guardianBoostLy);
            }
        }
        return p.linearConstant * 1e-3 * Math.pow(dist * massTons / p.optimalMass, p.powerConstant);
    }

    /**
     * Simulates the remaining route from the CURRENT-marker row using live fuel.
     * Indexes in the result line up with {@code entries}.
     *
     * @return result, or null when there's nothing to predict (no current row / no fuel reading)
     */
    public static Result simulate(List<RouteEntry> entries, ShipFuelProfile profile,
            double fuelMainTons, double cargoTons) {
        if (entries == null || entries.isEmpty() || profile == null || Double.isNaN(fuelMainTons)) {
            return null;
        }
        int currentRow = -1;
        for (int i = 0; i < entries.size(); i++) {
            RouteEntry e = entries.get(i);
            if (e != null && !e.isBodyRow && e.markerKind == RouteMarkerKind.CURRENT) {
                currentRow = i;
                break;
            }
        }
        if (currentRow < 0) {
            return null;
        }
        int n = entries.size();
        RowFuelState[] states = new RowFuelState[n];
        double[] arrival = new double[n];
        java.util.Arrays.fill(arrival, Double.NaN);

        double fuel = Math.max(0, Math.min(fuelMainTons, profile.fuelCapacityMain));
        // Reserve tank refills from main during normal flight; count its capacity as carried mass.
        double baseMass = profile.unladenMass + Math.max(0, cargoTons) + profile.fuelCapacityReserve;
        arrival[currentRow] = fuel;

        RouteEntry prev = entries.get(currentRow);
        int lastReachable = currentRow;
        boolean ranDry = false;
        for (int i = currentRow + 1; i < n; i++) {
            RouteEntry e = entries.get(i);
            if (e == null || e.isBodyRow) {
                continue;
            }
            if (ranDry) {
                states[i] = RowFuelState.UNREACHABLE;
                continue;
            }
            double dist = legDistanceLy(prev, e);
            if (Double.isNaN(dist)) {
                // Unknown geometry: stop predicting rather than guessing.
                break;
            }
            double cost = fuelForJump(profile, dist, baseMass + fuel);
            if (cost > profile.maxFuelPerJump + EPSILON || cost > fuel + EPSILON) {
                ranDry = true;
                states[lastReachable] = RowFuelState.LAST_REACHABLE;
                states[i] = RowFuelState.UNREACHABLE;
                continue;
            }
            fuel -= cost;
            arrival[i] = fuel;
            states[i] = RowFuelState.REACHABLE;
            lastReachable = i;
            if (profile.hasFuelScoop && FuelScoopStarClass.isFuelScoopable(e.starClass)) {
                fuel = profile.fuelCapacityMain;
            }
            prev = e;
        }
        return new Result(states, arrival, profile.fuelCapacityMain, profile.hasFuelScoop);
    }

    /** Distance between two route rows: coordinates when both known, else the stored per-leg value. */
    private static double legDistanceLy(RouteEntry from, RouteEntry to) {
        if (from != null && to != null
                && from.x != null && from.y != null && from.z != null
                && to.x != null && to.y != null && to.z != null) {
            double dx = to.x - from.x;
            double dy = to.y - from.y;
            double dz = to.z - from.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        if (to != null && to.distanceLy != null) {
            return to.distanceLy;
        }
        return Double.NaN;
    }

    private static int extractIntAfter(String s, String marker) {
        int at = s.indexOf(marker);
        if (at < 0) {
            return -1;
        }
        int i = at + marker.length();
        int val = 0;
        boolean any = false;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            val = val * 10 + (s.charAt(i) - '0');
            any = true;
            i++;
        }
        return any ? val : -1;
    }

    private static int parseTrailingDigit(String s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                return c - '0';
            }
        }
        return -1;
    }
}
