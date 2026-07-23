package org.dce.ed.route;

import java.util.List;
import java.util.Locale;

import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.LoadoutEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Predicts where along the plotted route the ship runs out of fuel, using the hyperspace
 * fuel equation {@code fuel = r * 1e-3 * (dist * mass / optimalMass)^p}.
 * <p>
 * FSD parameters come from the journal {@code Loadout} event: stock values from the tables
 * below, overridden by Engineering modifiers ({@code FSDOptimalMass} / {@code MaxFuelPerJump})
 * when present. A hop that exceeds {@code maxFuelPerJump} is reported as beyond FSD range
 * (not an empty tank). Elite often upgrades the FSD via {@code EngineerCraft} without emitting a
 * fresh Loadout — call {@link #applyFsdCraft} so those crafts refresh optimal mass / max fuel.
 * Ships with a fuel scoop are assumed to scoop to full at every scoopable (KGBFOAM) star on the
 * route, so tank-empty warnings only appear for unscoopable stretches longer than a tank, or when
 * current fuel can't reach the next scoopable star.
 */
public final class RouteFuelPrediction {

    public enum RowFuelState {
        /** Reachable on predicted fuel. */
        REACHABLE,
        /** Last system reachable before the route is blocked. */
        LAST_REACHABLE,
        /** Predicted out of main-tank fuel before arriving here. */
        UNREACHABLE,
        /**
         * Hop to this system needs more fuel than {@code maxFuelPerJump} allows (beyond FSD
         * range), even with a full tank.
         */
        BEYOND_JUMP_RANGE
    }

    /** Why the route is blocked after {@link RowFuelState#LAST_REACHABLE}, if it is. */
    public enum BlockReason {
        NONE,
        /** Remaining main-tank fuel is not enough for the next hop. */
        TANK_EMPTY,
        /** Next hop exceeds the FSD's max fuel per jump (range limit). */
        JUMP_TOO_FAR
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
        /**
         * Loadout {@code MaxJumpRange} when known (unladen, just enough fuel for one jump);
         * {@link Double#NaN} when the journal omitted it.
         */
        final double maxJumpRangeLy;

        ShipFuelProfile(double unladenMass, double fuelCapacityMain, double fuelCapacityReserve,
                double optimalMass, double maxFuelPerJump, double linearConstant, double powerConstant,
                boolean hasFuelScoop, double guardianBoostLy) {
            this(unladenMass, fuelCapacityMain, fuelCapacityReserve, optimalMass, maxFuelPerJump,
                    linearConstant, powerConstant, hasFuelScoop, guardianBoostLy, Double.NaN);
        }

        ShipFuelProfile(double unladenMass, double fuelCapacityMain, double fuelCapacityReserve,
                double optimalMass, double maxFuelPerJump, double linearConstant, double powerConstant,
                boolean hasFuelScoop, double guardianBoostLy, double maxJumpRangeLy) {
            this.unladenMass = unladenMass;
            this.fuelCapacityMain = fuelCapacityMain;
            this.fuelCapacityReserve = fuelCapacityReserve;
            this.optimalMass = optimalMass;
            this.maxFuelPerJump = maxFuelPerJump;
            this.linearConstant = linearConstant;
            this.powerConstant = powerConstant;
            this.hasFuelScoop = hasFuelScoop;
            this.guardianBoostLy = guardianBoostLy;
            this.maxJumpRangeLy = maxJumpRangeLy;
        }

        public boolean hasFuelScoop() {
            return hasFuelScoop;
        }

        public double fuelCapacityMain() {
            return fuelCapacityMain;
        }

        /** Loadout max jump range in Ly, or NaN when unknown. */
        public double maxJumpRangeLy() {
            return maxJumpRangeLy;
        }
    }

    /** Per-row prediction for one displayed route snapshot; indexes match the displayed entries. */
    public static final class Result {
        private final RowFuelState[] states;
        private final double[] fuelOnArrivalTons;
        private final double fuelCapacityMain;
        private final boolean assumesScooping;
        private final BlockReason blockReason;
        private final double maxFuelPerJump;
        private final double maxJumpRangeLy;

        Result(RowFuelState[] states, double[] fuelOnArrivalTons, double fuelCapacityMain, boolean assumesScooping,
                BlockReason blockReason, double maxFuelPerJump) {
            this(states, fuelOnArrivalTons, fuelCapacityMain, assumesScooping, blockReason, maxFuelPerJump,
                    Double.NaN);
        }

        Result(RowFuelState[] states, double[] fuelOnArrivalTons, double fuelCapacityMain, boolean assumesScooping,
                BlockReason blockReason, double maxFuelPerJump, double maxJumpRangeLy) {
            this.states = states;
            this.fuelOnArrivalTons = fuelOnArrivalTons;
            this.fuelCapacityMain = fuelCapacityMain;
            this.assumesScooping = assumesScooping;
            this.blockReason = blockReason != null ? blockReason : BlockReason.NONE;
            this.maxFuelPerJump = maxFuelPerJump;
            this.maxJumpRangeLy = maxJumpRangeLy;
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

        /** Why prediction stopped advancing past the last reachable system. */
        public BlockReason blockReason() {
            return blockReason;
        }

        /** FSD max fuel per jump (tons) used for this prediction. */
        public double maxFuelPerJump() {
            return maxFuelPerJump;
        }

        /** Loadout max jump range (Ly), or NaN when unknown. */
        public double maxJumpRangeLy() {
            return maxJumpRangeLy;
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
        double maxJump = loadout.getMaxJumpRange();
        if (maxJump <= 0) {
            maxJump = Double.NaN;
        }
        return new ShipFuelProfile(loadout.getUnladenMass(), capMain, capReserve,
                optMass, maxFuel, linear, power, scoop, boostLy, maxJump);
    }

    /**
     * Whether this craft updates the fitted FSD (slot and/or module id).
     * Elite frequently omits a follow-up {@code Loadout} after grade crafts at an engineer.
     */
    public static boolean isFsdCraft(EngineerCraftEvent craft) {
        if (craft == null) {
            return false;
        }
        String slot = craft.getSlot() != null ? craft.getSlot() : "";
        if ("FrameShiftDrive".equalsIgnoreCase(slot)) {
            return true;
        }
        String module = craft.getModule() != null ? craft.getModule().toLowerCase(Locale.ROOT) : "";
        return module.startsWith("int_hyperdrive");
    }

    /**
     * Applies {@code FSDOptimalMass} / {@code MaxFuelPerJump} from an {@code EngineerCraft} onto
     * the current profile. Clears stale Loadout {@code MaxJumpRange} (recomputes from the fuel
     * equation). Returns {@code current} unchanged when the craft is not an FSD update or has no
     * usable modifiers.
     */
    public static ShipFuelProfile applyFsdCraft(ShipFuelProfile current, EngineerCraftEvent craft) {
        if (current == null || craft == null || !isFsdCraft(craft)) {
            return current;
        }
        JsonObject raw = craft.getRawJson();
        if (raw == null || !raw.has("Modifiers") || !raw.get("Modifiers").isJsonArray()) {
            return current;
        }
        double optMass = current.optimalMass;
        double maxFuel = current.maxFuelPerJump;
        boolean touched = false;
        JsonArray mods = raw.getAsJsonArray("Modifiers");
        for (JsonElement el : mods) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject m = el.getAsJsonObject();
            String label = m.has("Label") && !m.get("Label").isJsonNull() ? m.get("Label").getAsString() : "";
            if (!m.has("Value") || m.get("Value").isJsonNull()) {
                continue;
            }
            double value = m.get("Value").getAsDouble();
            if ("FSDOptimalMass".equalsIgnoreCase(label) && value > 0) {
                optMass = value;
                touched = true;
            } else if ("MaxFuelPerJump".equalsIgnoreCase(label) && value > 0) {
                maxFuel = value;
                touched = true;
            }
        }
        if (!touched) {
            return current;
        }
        // Loadout MaxJumpRange is stale after crafts; recompute like the journal definition
        // (unladen + just enough fuel for one max jump). Unladen mass shifts slightly with FSD
        // module mass, but Loadout already baked that in — ignore Mass modifier deltas here.
        double maxJump = (optMass / (current.unladenMass + maxFuel))
                * Math.pow(1000.0 * maxFuel / current.linearConstant, 1.0 / current.powerConstant);
        return new ShipFuelProfile(current.unladenMass, current.fuelCapacityMain, current.fuelCapacityReserve,
                optMass, maxFuel, current.linearConstant, current.powerConstant,
                current.hasFuelScoop, current.guardianBoostLy, maxJump);
    }

    /** Fuel (tons) for one hyperspace jump of {@code distanceLy} at total ship mass {@code massTons}. */
    static double fuelForJump(ShipFuelProfile p, double distanceLy, double massTons) {
        return fuelForJump(p, distanceLy, massTons, 1.0);
    }

    /**
     * Fuel for one jump, optionally with a jet-cone range multiplier ({@code 4} neutron,
     * {@code 1.5} white dwarf). The multiplier stretches range for the same fuel by treating
     * the hop as shorter in the hyperspace equation.
     */
    static double fuelForJump(ShipFuelProfile p, double distanceLy, double massTons,
            double jetConeRangeMultiplier) {
        double mult = jetConeRangeMultiplier > 1.0 ? jetConeRangeMultiplier : 1.0;
        double dist = distanceLy / mult;
        if (p.guardianBoostLy > 0) {
            // Booster stretches range for the same fuel: scale distance by baseMax / (baseMax + boost).
            double baseMax = (p.optimalMass / massTons) * Math.pow(1000.0 * p.maxFuelPerJump / p.linearConstant,
                    1.0 / p.powerConstant);
            if (baseMax > 0) {
                dist = dist * baseMax / (baseMax + p.guardianBoostLy);
            }
        }
        return p.linearConstant * 1e-3 * Math.pow(dist * massTons / p.optimalMass, p.powerConstant);
    }

    /**
     * Simulates the remaining route from the CURRENT-marker row using live fuel.
     * Indexes in the result line up with {@code entries}.
     * <p>
     * Hops that leave a neutron ({@code N}) or white dwarf ({@code D}…) assume a jet-cone
     * supercharge (4× / 1.5×), matching the galaxy map plotter when jet-cone boost is enabled.
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
        boolean blocked = false;
        BlockReason blockReason = BlockReason.NONE;
        RowFuelState blockedState = RowFuelState.UNREACHABLE;
        for (int i = currentRow + 1; i < n; i++) {
            RouteEntry e = entries.get(i);
            if (e == null || e.isBodyRow) {
                continue;
            }
            if (blocked) {
                states[i] = blockedState;
                continue;
            }
            double dist = legDistanceLy(prev, e);
            if (Double.isNaN(dist)) {
                // Unknown geometry: stop predicting rather than guessing.
                break;
            }
            // Jet-cone charge is taken at the departure remnant before this hop.
            double jetCone = FsdJetConeBoost.multiplierLeaving(prev != null ? prev.starClass : null);
            double cost = fuelForJump(profile, dist, baseMass + fuel, jetCone);
            // FSD range limit (max fuel per jump) is distinct from an empty main tank.
            if (cost > profile.maxFuelPerJump + EPSILON) {
                blocked = true;
                blockReason = BlockReason.JUMP_TOO_FAR;
                blockedState = RowFuelState.BEYOND_JUMP_RANGE;
                states[lastReachable] = RowFuelState.LAST_REACHABLE;
                states[i] = blockedState;
                continue;
            }
            if (cost > fuel + EPSILON) {
                blocked = true;
                blockReason = BlockReason.TANK_EMPTY;
                blockedState = RowFuelState.UNREACHABLE;
                states[lastReachable] = RowFuelState.LAST_REACHABLE;
                states[i] = blockedState;
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
        return new Result(states, arrival, profile.fuelCapacityMain, profile.hasFuelScoop,
                blockReason, profile.maxFuelPerJump, profile.maxJumpRangeLy);
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
