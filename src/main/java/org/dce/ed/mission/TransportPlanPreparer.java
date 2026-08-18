package org.dce.ed.mission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.CargoMonitor;

import com.google.gson.JsonObject;

/** Validates live mission/cargo state and resolves it into planner inputs. */
public final class TransportPlanPreparer {
    private TransportPlanPreparer() { }

    public static TransportPlanPreparation prepare(List<MissionRecord> missions,
            String currentSystem, String currentStation, int cargoCapacity, JsonObject cargo,
            TransportCoordinateResolver resolver) {
        List<MissionRecord> active = missions == null ? List.of() : missions.stream()
                .filter(m -> m != null && m.getCategory().isTransport())
                .toList();
        List<TransportPlanProblem> problems = validate(active, currentSystem, cargoCapacity);
        if (!problems.isEmpty()) return new TransportPlanPreparation(null, problems);

        boolean hasInventory = cargo != null
                && ((cargo.has("Inventory") && cargo.get("Inventory").isJsonArray())
                        || (cargo.has("inventory") && cargo.get("inventory").isJsonArray()));
        if (!hasInventory) {
            return problem(TransportPlanProblem.Code.CARGO_REQUIRED, 0,
                    "Live cargo data is unavailable. Refresh Cargo.json before optimizing.");
        }

        int occupied = CargoMonitor.totalCargoTons(cargo);
        if (occupied > cargoCapacity) {
            return problem(TransportPlanProblem.Code.CAPACITY_UNKNOWN, 0,
                    "Cargo hold usage exceeds the latest known capacity.");
        }

        Map<String, double[]> coords = new HashMap<>();
        try {
            TransportLocation start = location(currentSystem,
                    blank(currentStation) ? "Current position" : currentStation, resolver, coords);
            List<TransportShipment> shipments = new ArrayList<>();
            List<TransportVisit> visits = new ArrayList<>();
            List<TransportPlanProblem> warnings = new ArrayList<>();
            Map<String, Integer> loose = looseCargoByCommodity(active, cargo);
            for (MissionRecord mission : active) {
                TransportLocation delivery = location(turnInSystem(mission),
                        turnInStation(mission), resolver, coords);
                if (mission.getCategory() != MissionCategory.COMMODITY) {
                    visits.add(new TransportVisit(mission.getMissionId(),
                            visitLabel(mission), delivery));
                    continue;
                }
                int remaining = remaining(mission);
                if (remaining <= 0) continue;
                int exact = Math.min(remaining,
                        CargoMonitor.countMissionCargoTons(cargo, mission.getMissionId()));
                String key = commodityKey(mission.getCommodityLocalised());
                int generic = mission.isManuallySourceableCommodityMission()
                        ? Math.min(remaining - exact, loose.getOrDefault(key, 0)) : 0;
                loose.put(key, loose.getOrDefault(key, 0) - generic);
                int aboard = exact + generic;
                TransportLocation pickup = null;
                if (aboard < remaining) {
                    if (mission.isManuallySourceableCommodityMission()
                            && (blank(mission.getSourcedFromSystem())
                                    || blank(mission.getSourcedFromStation()))) {
                        int omitted = remaining - aboard;
                        warnings.add(new TransportPlanProblem(TransportPlanProblem.Code.SOURCE_REQUIRED,
                                mission.getMissionId(), "Pickup not planned: " + omitted + " t "
                                        + mission.getCommodityLocalised() + " source has not been set."));
                        if (aboard > 0) {
                            shipments.add(TransportShipment.cargo(mission.getMissionId(),
                                    mission.getCommodityLocalised(), aboard, aboard, null, delivery));
                        }
                        continue;
                    }
                    pickup = mission.isManuallySourceableCommodityMission()
                            ? location(mission.getSourcedFromSystem(), mission.getSourcedFromStation(), resolver, coords)
                            : location(mission.getOriginSystem(), mission.getOriginStation(), resolver, coords);
                }
                shipments.add(TransportShipment.cargo(mission.getMissionId(),
                        mission.getCommodityLocalised(), remaining, aboard, pickup, delivery));
            }
            int missionCargoAboard = shipments.stream().mapToInt(TransportShipment::tonsAboard).sum();
            int unrelatedCargo = Math.max(0, occupied - missionCargoAboard);
            boolean hasPendingPickup = shipments.stream()
                    .anyMatch(shipment -> shipment.pickup() != null
                            && shipment.tonsRemaining() > shipment.tonsAboard());
            if (hasPendingPickup && unrelatedCargo >= cargoCapacity) {
                return problem(TransportPlanProblem.Code.CARGO_SPACE_REQUIRED, 0,
                        "No cargo space is available. Your hold contains "
                                + String.format(Locale.US, "%,d", unrelatedCargo)
                                + " t of cargo not assigned to these missions. "
                                + "Sell or discard some cargo, then optimize again.");
            }
            return new TransportPlanPreparation(
                    new TransportPlanRequest(start, cargoCapacity, occupied, shipments, visits),
                    List.of(), warnings);
        } catch (UnresolvedLocation ex) {
            return problem(TransportPlanProblem.Code.COORDINATES_UNAVAILABLE, ex.missionId, ex.getMessage());
        }
    }

    private static String visitLabel(MissionRecord mission) {
        if (mission.getCategory() == MissionCategory.DONATION) {
            return mission.getDonation() > 0
                    ? "Donate " + String.format(Locale.US, "%,d", mission.getDonation()) + " Cr"
                    : "Donate credits";
        }
        return mission.getCategory().displayLabel();
    }

    private static List<TransportPlanProblem> validate(List<MissionRecord> missions,
            String currentSystem, int capacity) {
        List<TransportPlanProblem> out = new ArrayList<>();
        if (capacity <= 0) out.add(new TransportPlanProblem(TransportPlanProblem.Code.CAPACITY_UNKNOWN, 0,
                "Load or refresh the current ship to determine cargo capacity."));
        if (blank(currentSystem)) out.add(new TransportPlanProblem(TransportPlanProblem.Code.LOCATION_REQUIRED, 0,
                "Current system is unknown."));
        for (MissionRecord mission : missions) {
            if (blank(turnInSystem(mission)) || blank(turnInStation(mission))) {
                out.add(new TransportPlanProblem(TransportPlanProblem.Code.LOCATION_REQUIRED,
                        mission.getMissionId(), mission.summaryLine() + " has no complete turn-in location."));
            }
        }
        return out;
    }

    private static TransportLocation location(String system, String station,
            TransportCoordinateResolver resolver, Map<String, double[]> cache) {
        if (blank(system) || blank(station)) throw new UnresolvedLocation(0, "A required stop is incomplete.");
        String normalized = system.trim();
        double[] xyz = cache.get(normalized.toLowerCase(Locale.ROOT));
        if (xyz == null) {
            try { xyz = resolver.resolve(normalized); }
            catch (Exception ex) { xyz = null; }
            if (xyz == null || xyz.length < 3) {
                throw new UnresolvedLocation(0, "Could not resolve coordinates for " + normalized + ".");
            }
            cache.put(normalized.toLowerCase(Locale.ROOT), xyz.clone());
        }
        return new TransportLocation(normalized, station.trim(), xyz[0], xyz[1], xyz[2]);
    }

    private static Map<String, Integer> looseCargoByCommodity(List<MissionRecord> missions, JsonObject cargo) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (MissionRecord mission : missions) {
            if (mission.getCategory() != MissionCategory.COMMODITY) continue;
            String commodity = mission.getCommodityLocalised();
            String key = commodityKey(commodity);
            out.putIfAbsent(key, CargoMonitor.countUnassignedCommodityTons(cargo, commodity));
        }
        return out;
    }

    private static int remaining(MissionRecord mission) {
        int required = mission.getCountRequired() > 0
                ? mission.getCountRequired() : mission.getTotalItemsToDeliver();
        return Math.max(0, required - mission.getItemsDelivered());
    }

    private static String destinationStation(MissionRecord mission) {
        return blank(mission.getDestinationStation())
                ? mission.getDestinationSettlement() : mission.getDestinationStation();
    }

    private static String turnInSystem(MissionRecord mission) {
        return mission.getCategory() == MissionCategory.DONATION
                ? mission.getOriginSystem() : mission.getDestinationSystem();
    }

    private static String turnInStation(MissionRecord mission) {
        return mission.getCategory() == MissionCategory.DONATION
                ? mission.getOriginStation() : destinationStation(mission);
    }

    private static String commodityKey(String commodity) {
        return commodity == null ? "" : commodity.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static TransportPlanPreparation problem(TransportPlanProblem.Code code, long missionId,
            String message) {
        return new TransportPlanPreparation(null, List.of(new TransportPlanProblem(code, missionId, message)));
    }

    private static final class UnresolvedLocation extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final long missionId;
        UnresolvedLocation(long missionId, String message) { super(message); this.missionId = missionId; }
    }
}
