package org.dce.ed.exec;

import java.util.ArrayList;
import java.util.List;

/** Persisted Exec tab configuration. */
public final class ExecBindingsConfig {

    public static final int DEFAULT_TRITIUM_LOW_THRESHOLD = 100;
    public static final int DEFAULT_TRITIUM_LOW_HYSTERESIS = 20;

    private int fleetTritiumLowThreshold = DEFAULT_TRITIUM_LOW_THRESHOLD;
    private int fleetTritiumLowHysteresis = DEFAULT_TRITIUM_LOW_HYSTERESIS;
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

    public List<ExecBinding> getBindings() {
        return bindings;
    }

    public void setBindings(List<ExecBinding> bindings) {
        this.bindings = bindings != null ? new ArrayList<>(bindings) : new ArrayList<>();
    }
}
