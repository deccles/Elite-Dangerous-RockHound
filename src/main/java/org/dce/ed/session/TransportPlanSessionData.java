package org.dce.ed.session;

import java.util.ArrayList;
import java.util.List;

import org.dce.ed.mission.TransportLocation;
import org.dce.ed.mission.TransportPlanAction;
import org.dce.ed.mission.TransportPlanProblem;
import org.dce.ed.mission.TransportPlanStop;
import org.dce.ed.mission.TransportRoutePlan;

/** Gson-friendly snapshot of the last Transport plan shown to the commander. */
public final class TransportPlanSessionData {
    private List<StopData> stops = new ArrayList<>();
    private double totalDistanceLy;
    private boolean optimal;
    private LocationData start;
    private int initialHoldTons;
    private int capacity;
    private List<ProblemData> warnings = new ArrayList<>();

    public static TransportPlanSessionData from(TransportRoutePlan plan, TransportLocation start,
            int initialHoldTons, int capacity, List<TransportPlanProblem> warnings) {
        if (plan == null) return null;
        TransportPlanSessionData data = new TransportPlanSessionData();
        data.totalDistanceLy = plan.totalDistanceLy();
        data.optimal = plan.optimal();
        data.start = LocationData.from(start);
        data.initialHoldTons = initialHoldTons;
        data.capacity = capacity;
        for (TransportPlanStop stop : plan.stops()) data.stops.add(StopData.from(stop));
        if (warnings != null) for (TransportPlanProblem warning : warnings) {
            data.warnings.add(ProblemData.from(warning));
        }
        return data;
    }

    public TransportRoutePlan toPlan() {
        List<TransportPlanStop> restored = new ArrayList<>();
        if (stops != null) for (StopData stop : stops) {
            TransportPlanStop converted = stop != null ? stop.toStop() : null;
            if (converted != null) restored.add(converted);
        }
        return new TransportRoutePlan(restored, totalDistanceLy, optimal);
    }

    public TransportLocation startLocation() {
        return start != null ? start.toLocation() : null;
    }

    public List<TransportPlanProblem> warningProblems() {
        List<TransportPlanProblem> restored = new ArrayList<>();
        if (warnings != null) for (ProblemData warning : warnings) {
            TransportPlanProblem converted = warning != null ? warning.toProblem() : null;
            if (converted != null) restored.add(converted);
        }
        return List.copyOf(restored);
    }

    public int getInitialHoldTons() { return initialHoldTons; }
    public int getCapacity() { return capacity; }

    private static final class LocationData {
        private String system;
        private String station;
        private double x;
        private double y;
        private double z;

        static LocationData from(TransportLocation location) {
            if (location == null) return null;
            LocationData data = new LocationData();
            data.system = location.system();
            data.station = location.station();
            data.x = location.x();
            data.y = location.y();
            data.z = location.z();
            return data;
        }

        TransportLocation toLocation() {
            try {
                return new TransportLocation(system, station, x, y, z);
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }

    private static final class StopData {
        private LocationData location;
        private List<ActionData> actions = new ArrayList<>();
        private int holdAfterTons;

        static StopData from(TransportPlanStop stop) {
            StopData data = new StopData();
            data.location = LocationData.from(stop.location());
            data.holdAfterTons = stop.holdAfterTons();
            for (TransportPlanAction action : stop.actions()) data.actions.add(ActionData.from(action));
            return data;
        }

        TransportPlanStop toStop() {
            TransportLocation restoredLocation = location != null ? location.toLocation() : null;
            if (restoredLocation == null) return null;
            List<TransportPlanAction> restoredActions = new ArrayList<>();
            if (actions != null) for (ActionData action : actions) {
                TransportPlanAction converted = action != null ? action.toAction() : null;
                if (converted != null) restoredActions.add(converted);
            }
            return new TransportPlanStop(restoredLocation, restoredActions, holdAfterTons);
        }
    }

    private static final class ActionData {
        private String kind;
        private long missionId;
        private String commodity;
        private int tons;

        static ActionData from(TransportPlanAction action) {
            ActionData data = new ActionData();
            data.kind = action.kind().name();
            data.missionId = action.missionId();
            data.commodity = action.commodity();
            data.tons = action.tons();
            return data;
        }

        TransportPlanAction toAction() {
            try {
                return new TransportPlanAction(TransportPlanAction.Kind.valueOf(kind),
                        missionId, commodity, tons);
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }

    private static final class ProblemData {
        private String code;
        private long missionId;
        private String message;

        static ProblemData from(TransportPlanProblem problem) {
            ProblemData data = new ProblemData();
            data.code = problem.code().name();
            data.missionId = problem.missionId();
            data.message = problem.message();
            return data;
        }

        TransportPlanProblem toProblem() {
            try {
                return new TransportPlanProblem(TransportPlanProblem.Code.valueOf(code),
                        missionId, message);
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }
}
