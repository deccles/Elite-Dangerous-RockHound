package org.dce.ed.systemmap;

import java.time.Instant;

import org.dce.ed.state.SystemState;
import org.dce.ed.systemmodel.SystemModelService.ModelHandle;
import org.dce.systemmodel.model.SystemModel;

/**
 * One journal-authoritative {@link SystemModel} build for a loaded system, shared by the system tab,
 * hierarchy graph, and map pipeline.
 */
public final class SystemSession {

    private final SystemState state;
    private final ModelHandle handle;
    private final Instant builtAt;

    private SystemSession(SystemState state, ModelHandle handle, Instant builtAt) {
        this.state = state;
        this.handle = handle;
        this.builtAt = builtAt;
    }

    public static SystemSession of(SystemState state, ModelHandle handle) {
        return new SystemSession(state, handle, Instant.now());
    }

    public static SystemSession empty(SystemState state) {
        return new SystemSession(state, null, Instant.now());
    }

    public SystemState state() {
        return state;
    }

    public ModelHandle handle() {
        return handle;
    }

    public SystemModel model() {
        return handle != null ? handle.model() : null;
    }

    public Instant builtAt() {
        return builtAt;
    }

    public boolean hasModel() {
        return handle != null && handle.model() != null;
    }

    public String systemName() {
        return state != null && state.getSystemName() != null ? state.getSystemName().trim() : "";
    }

    public boolean matchesSystem(String systemName) {
        if (systemName == null || systemName.isBlank() || state == null) {
            return false;
        }
        String a = state.getSystemName();
        if (a == null || a.isBlank()) {
            return false;
        }
        return a.trim().equalsIgnoreCase(systemName.trim());
    }
}
