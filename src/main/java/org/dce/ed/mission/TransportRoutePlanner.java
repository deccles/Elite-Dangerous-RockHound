package org.dce.ed.mission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Computes capacity-safe pickup and delivery stops for Transport missions. */
public final class TransportRoutePlanner {
    private TransportRoutePlanner() { }

    public static TransportRoutePlan plan(TransportPlanRequest request) {
        Search search = new Search(request);
        search.run();
        if (search.best == null) {
            throw new IllegalArgumentException("No capacity-safe Transport plan exists");
        }
        return new TransportRoutePlan(search.best.stops, search.best.distance, true);
    }

    private static final class Search {
        final TransportPlanRequest request;
        final List<TransportShipment> shipments;
        final List<TransportVisit> visits;
        final int fixedCargo;
        final Map<String, Cost> seen = new HashMap<>();
        Result best;

        Search(TransportPlanRequest request) {
            this.request = request;
            this.shipments = request.shipments();
            this.visits = request.visits();
            int initiallyAboard = shipments.stream().mapToInt(TransportShipment::tonsAboard).sum();
            fixedCargo = request.occupiedCargo() - initiallyAboard;
            if (fixedCargo < 0) {
                throw new IllegalArgumentException("Mission cargo exceeds occupied cargo");
            }
        }

        void run() {
            int[] waiting = new int[shipments.size()];
            int[] aboard = new int[shipments.size()];
            boolean[] pendingVisits = new boolean[visits.size()];
            Arrays.fill(pendingVisits, true);
            for (int i = 0; i < shipments.size(); i++) {
                TransportShipment shipment = shipments.get(i);
                waiting[i] = shipment.tonsRemaining() - shipment.tonsAboard();
                aboard[i] = shipment.tonsAboard();
            }
            visit(new State(request.start(), waiting, aboard, pendingVisits), 0.0, new ArrayList<>());
        }

        void visit(State state, double distance, List<TransportPlanStop> path) {
            if (best != null && (distance > best.distance + 0.0000001
                    || (Math.abs(distance - best.distance) < 0.0000001 && path.size() > best.stops.size()))) {
                return;
            }
            String stateKey = state.key();
            Cost prior = seen.get(stateKey);
            if (prior != null && (prior.distance < distance - 0.0000001
                    || (Math.abs(prior.distance - distance) < 0.0000001 && prior.visits < path.size()))) {
                return;
            }
            seen.put(stateKey, new Cost(distance, path.size()));
            if (state.complete()) {
                Result candidate = new Result(List.copyOf(path), distance);
                if (best == null || candidate.betterThan(best)) {
                    best = candidate;
                }
                return;
            }

            List<TransportLocation> candidates = candidateLocations(state);
            candidates.sort(Comparator
                    .comparingDouble((TransportLocation location) -> state.location.distanceTo(location))
                    .thenComparing(TransportLocation::system, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(TransportLocation::station, String.CASE_INSENSITIVE_ORDER));
            for (TransportLocation location : candidates) {
                expandAt(state, distance, path, location);
            }
        }

        private List<TransportLocation> candidateLocations(State state) {
            Map<String, TransportLocation> unique = new LinkedHashMap<>();
            for (int i = 0; i < shipments.size(); i++) {
                TransportShipment shipment = shipments.get(i);
                if (state.aboard[i] > 0) {
                    unique.putIfAbsent(locationKey(shipment.delivery()), shipment.delivery());
                }
                if (state.waiting[i] > 0) {
                    unique.putIfAbsent(locationKey(shipment.pickup()), shipment.pickup());
                }
            }
            for (int i = 0; i < visits.size(); i++) {
                if (state.pendingVisits[i]) {
                    TransportLocation destination = visits.get(i).destination();
                    unique.putIfAbsent(locationKey(destination), destination);
                }
            }
            return new ArrayList<>(unique.values());
        }

        private void expandAt(State state, double distance, List<TransportPlanStop> path,
                TransportLocation location) {
            int[] waiting = state.waiting.clone();
            int[] aboard = state.aboard.clone();
            boolean[] pendingVisits = state.pendingVisits.clone();
            List<TransportPlanAction> deliveries = new ArrayList<>();
            for (int i = 0; i < shipments.size(); i++) {
                if (aboard[i] > 0 && sameLocation(shipments.get(i).delivery(), location)) {
                    int tons = aboard[i];
                    aboard[i] = 0;
                    TransportShipment shipment = shipments.get(i);
                    deliveries.add(new TransportPlanAction(TransportPlanAction.Kind.DELIVER,
                            shipment.missionId(), shipment.commodity(), tons));
                }
            }
            for (int i = 0; i < visits.size(); i++) {
                if (pendingVisits[i] && sameLocation(visits.get(i).destination(), location)) {
                    pendingVisits[i] = false;
                    TransportVisit visit = visits.get(i);
                    deliveries.add(new TransportPlanAction(TransportPlanAction.Kind.VISIT,
                            visit.missionId(), visit.label(), 0));
                }
            }

            int free = request.cargoCapacity() - fixedCargo - Arrays.stream(aboard).sum();
            List<Integer> eligible = new ArrayList<>();
            for (int i = 0; i < shipments.size(); i++) {
                if (waiting[i] > 0 && sameLocation(shipments.get(i).pickup(), location)) {
                    eligible.add(i);
                }
            }
            List<int[]> allocations = pickupAllocations(waiting, eligible, free);
            if (!deliveries.isEmpty()) {
                allocations.add(0, new int[shipments.size()]);
            }
            for (int[] allocation : allocations) {
                List<TransportPlanAction> actions = new ArrayList<>(deliveries);
                int[] nextWaiting = waiting.clone();
                int[] nextAboard = aboard.clone();
                for (int i = 0; i < allocation.length; i++) {
                    int tons = allocation[i];
                    if (tons <= 0) continue;
                    nextWaiting[i] -= tons;
                    nextAboard[i] += tons;
                    TransportShipment shipment = shipments.get(i);
                    actions.add(new TransportPlanAction(TransportPlanAction.Kind.PICK_UP,
                            shipment.missionId(), shipment.commodity(), tons));
                }
                if (actions.isEmpty()) continue;
                int holdAfter = fixedCargo + Arrays.stream(nextAboard).sum();
                List<TransportPlanStop> nextPath = new ArrayList<>(path);
                nextPath.add(new TransportPlanStop(location, actions, holdAfter));
                visit(new State(location, nextWaiting, nextAboard, pendingVisits.clone()),
                        distance + state.location.distanceTo(location), nextPath);
            }
        }

        private List<int[]> pickupAllocations(int[] waiting, List<Integer> eligible, int free) {
            if (free <= 0 || eligible.isEmpty()) return new ArrayList<>();
            if (Arrays.stream(waiting).sum() <= free) {
                int[] allocation = new int[shipments.size()];
                for (int index : eligible) allocation[index] = waiting[index];
                return new ArrayList<>(List.of(allocation));
            }
            // Missions collected here and delivered to the same place are route-equivalent.
            // Their loading order cannot affect distance or capacity, so choose one stable
            // allocation instead of exploring every permutation of mission IDs.
            TransportLocation sharedDelivery = shipments.get(eligible.get(0)).delivery();
            if (eligible.stream().allMatch(index -> sameLocation(sharedDelivery, shipments.get(index).delivery()))) {
                int[] allocation = new int[shipments.size()];
                int remainingFree = free;
                for (int index : eligible) {
                    allocation[index] = Math.min(waiting[index], remainingFree);
                    remainingFree -= allocation[index];
                    if (remainingFree == 0) break;
                }
                return new ArrayList<>(List.of(allocation));
            }
            Map<String, List<Integer>> byDelivery = new LinkedHashMap<>();
            for (int index : eligible) {
                byDelivery.computeIfAbsent(locationKey(shipments.get(index).delivery()), key -> new ArrayList<>())
                        .add(index);
            }
            if (byDelivery.size() < eligible.size()) {
                List<int[]> out = new ArrayList<>();
                Set<String> keys = new HashSet<>();
                List<List<Integer>> groups = new ArrayList<>(byDelivery.values());
                buildGroupAllocations(waiting, groups, free, new boolean[groups.size()],
                        new int[shipments.size()], out, keys);
                return out;
            }
            List<int[]> out = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            buildAllocations(waiting, eligible, free, new boolean[shipments.size()],
                    new int[shipments.size()], out, keys);
            return out;
        }

        private void buildGroupAllocations(int[] waiting, List<List<Integer>> groups, int free,
                boolean[] used, int[] allocation, List<int[]> out, Set<String> keys) {
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                if (used[groupIndex] || free <= 0) continue;
                List<Integer> group = groups.get(groupIndex);
                int remainingFree = free;
                int loaded = 0;
                for (int shipmentIndex : group) {
                    int amount = Math.min(waiting[shipmentIndex], remainingFree);
                    allocation[shipmentIndex] = amount;
                    loaded += amount;
                    remainingFree -= amount;
                    if (remainingFree == 0) break;
                }
                used[groupIndex] = true;
                String key = Arrays.toString(allocation);
                if (keys.add(key)) out.add(allocation.clone());
                buildGroupAllocations(waiting, groups, free - loaded, used, allocation, out, keys);
                for (int shipmentIndex : group) allocation[shipmentIndex] = 0;
                used[groupIndex] = false;
            }
        }

        private void buildAllocations(int[] waiting, List<Integer> eligible, int free,
                boolean[] used, int[] allocation, List<int[]> out, Set<String> keys) {
            for (int index : eligible) {
                if (used[index] || free <= 0) continue;
                int amount = Math.min(waiting[index], free);
                used[index] = true;
                allocation[index] = amount;
                String key = Arrays.toString(allocation);
                if (keys.add(key)) out.add(allocation.clone());
                buildAllocations(waiting, eligible, free - amount, used, allocation, out, keys);
                allocation[index] = 0;
                used[index] = false;
            }
        }
    }

    private static boolean sameLocation(TransportLocation a, TransportLocation b) {
        return a != null && b != null
                && a.system().equalsIgnoreCase(b.system())
                && a.station().equalsIgnoreCase(b.station());
    }

    private static String locationKey(TransportLocation location) {
        return location.system().toLowerCase() + "\n" + location.station().toLowerCase();
    }

    private record State(TransportLocation location, int[] waiting, int[] aboard,
            boolean[] pendingVisits) {
        boolean complete() {
            if (Arrays.stream(waiting).sum() != 0 || Arrays.stream(aboard).sum() != 0) return false;
            for (boolean pending : pendingVisits) if (pending) return false;
            return true;
        }

        String key() {
            return locationKey(location) + '|' + Arrays.toString(waiting) + '|'
                    + Arrays.toString(aboard) + '|' + Arrays.toString(pendingVisits);
        }
    }

    private record Cost(double distance, int visits) { }

    private record Result(List<TransportPlanStop> stops, double distance) {
        boolean betterThan(Result other) {
            if (distance < other.distance - 0.0000001) return true;
            if (Math.abs(distance - other.distance) >= 0.0000001) return false;
            if (stops.size() != other.stops.size()) return stops.size() < other.stops.size();
            for (int i = 0; i < stops.size(); i++) {
                int compared = Integer.compare(stops.get(i).holdAfterTons(), other.stops.get(i).holdAfterTons());
                if (compared != 0) return compared > 0;
            }
            return false;
        }
    }
}
