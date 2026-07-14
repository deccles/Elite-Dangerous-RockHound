package org.dce.ed.exec;

import java.util.ArrayList;
import java.util.List;

/** Persisted Exec tab configuration. */
public final class ExecBindingsConfig {

    public static final int DEFAULT_TRITIUM_LOW_THRESHOLD = 100;
    public static final int DEFAULT_TRITIUM_LOW_HYSTERESIS = 20;

    private int fleetTritiumLowThreshold = DEFAULT_TRITIUM_LOW_THRESHOLD;
    private int fleetTritiumLowHysteresis = DEFAULT_TRITIUM_LOW_HYSTERESIS;
    /** Shared named programs (name → path) selectable in the Exec Program column. */
    private List<ExecProgram> programs = new ArrayList<>();
    private List<ExecBinding> bindings = new ArrayList<>();

    public int getFleetTritiumLowThreshold() {
        return fleetTritiumLowThreshold;
    }

    public void setFleetTritiumLowThreshold(int fleetTritiumLowThreshold) {
        this.fleetTritiumLowThreshold = Math.max(0, fleetTritiumLowThreshold);
    }

    public int getFleetTritiumLowHysteresis() {
        return fleetTritiumLowHysteresis;
    }

    public void setFleetTritiumLowHysteresis(int fleetTritiumLowHysteresis) {
        this.fleetTritiumLowHysteresis = Math.max(0, fleetTritiumLowHysteresis);
    }

    public List<ExecProgram> getPrograms() {
        return programs;
    }

    public void setPrograms(List<ExecProgram> programs) {
        this.programs = programs != null ? new ArrayList<>(programs) : new ArrayList<>();
    }

    public List<ExecBinding> getBindings() {
        return bindings;
    }

    public void setBindings(List<ExecBinding> bindings) {
        this.bindings = bindings != null ? new ArrayList<>(bindings) : new ArrayList<>();
    }
}
