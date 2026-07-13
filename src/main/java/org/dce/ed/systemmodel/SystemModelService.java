package org.dce.ed.systemmodel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.dce.ed.state.SystemState;
import org.dce.systemmodel.build.IncompleteSystemException;
import org.dce.systemmodel.build.SystemModelBuilder;
import org.dce.systemmodel.journal.JournalEventLogUtil;
import org.dce.systemmodel.journal.JournalRecord;
import org.dce.systemmodel.exception.ModelBuildException;
import org.dce.systemmodel.model.Position3d;
import org.dce.systemmodel.model.SystemModel;
import org.dce.systemmodel.snapshot.SystemSnapshot;

public final class SystemModelService {

    public enum ModelState {
        OK, INCOMPLETE, ERROR
    }

    public record ModelHandle(
            ModelState state,
            SystemModel model,
            String statusMessage,
            List<String> issues) {
    }

    private SystemModelService() {
    }

    public static ModelHandle rebuild(SystemState state, boolean strict) {
        try {
            List<JournalRecord> normalized = JournalEventLogUtil.normalizeForSystemBuild(
                    state.getSystemName(), state.getJournalEventLog());
            SystemModelBuilder builder = new SystemModelBuilder()
                    .systemName(state.getSystemName())
                    .systemAddress(state.getSystemAddress())
                    .addAll(normalized);
            SystemModel model = strict ? builder.build() : builder.buildPartial();
            List<String> incomplete = builder.incompleteReasons();
            if (!incomplete.isEmpty()) {
                // Keep INCOMPLETE for map/transcription logic; do not surface pending-parent chatter in the UI.
                return new ModelHandle(ModelState.INCOMPLETE, model, null, incomplete);
            }
            return new ModelHandle(ModelState.OK, model, null, List.of());
        } catch (IncompleteSystemException e) {
            return new ModelHandle(
                    ModelState.INCOMPLETE,
                    new SystemModelBuilder()
                            .systemName(state.getSystemName())
                            .systemAddress(state.getSystemAddress())
                            .addAll(JournalEventLogUtil.normalizeForSystemBuild(
                                    state.getSystemName(), state.getJournalEventLog()))
                            .buildPartial(),
                    e.getMessage(),
                    e.reasons());
        } catch (ModelBuildException e) {
            return new ModelHandle(ModelState.ERROR, null, e.getUserMessage(), List.of(e.getMessage()));
        } catch (RuntimeException e) {
            return new ModelHandle(ModelState.ERROR, null, "Cannot build system model: " + e.getMessage(), List.of());
        }
    }

    public static ModelHandle rebuildFromEventLog(
            String systemName, long systemAddress, List<JournalRecord> log, boolean strict) {
        SystemState stub = new SystemState();
        stub.setSystemName(systemName);
        stub.setSystemAddress(systemAddress);
        for (JournalRecord r : log) {
            stub.appendJournalEvent(r);
        }
        return rebuild(stub, strict);
    }

    public static SystemSnapshot snapshotFromState(SystemState state) {
        ModelHandle h = rebuild(state, false);
        return SystemSnapshot.fromModel(
                h.model() != null ? h.model() : emptyModel(state),
                state.getJournalEventLog());
    }

    public static String modelStateName(ModelHandle h) {
        return h.state().name();
    }

    public static Optional<Position3d> safePositionAt(ModelHandle h, int bodyId, Instant t) {
        if (h.state() == ModelState.ERROR || h.model() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(h.model().positionAt(bodyId, t));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static SystemModel emptyModel(SystemState state) {
        return new SystemModelBuilder()
                .systemName(state.getSystemName())
                .systemAddress(state.getSystemAddress())
                .buildPartial();
    }

    public static List<JournalRecord> mergeEventLog(List<JournalRecord> existing, JournalRecord next) {
        List<JournalRecord> merged = new ArrayList<>(existing != null ? existing : List.of());
        merged.add(next);
        return List.copyOf(merged);
    }
}
