package org.dce.ed.exec;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Environment passed to external JAR processes. */
public final class ExecLaunchContext {

    private final ExecTriggerId trigger;
    private final int delayMs;
    private final Instant firedAt;
    private final String carrierSystemName;
    private final Integer carrierFuelLevel;
    private final Integer carrierFuelThreshold;
    private final String carrierCallsign;
    private final String carrierName;

    private ExecLaunchContext(Builder builder) {
        this.trigger = builder.trigger;
        this.delayMs = builder.delayMs;
        this.firedAt = builder.firedAt != null ? builder.firedAt : Instant.now();
        this.carrierSystemName = builder.carrierSystemName;
        this.carrierFuelLevel = builder.carrierFuelLevel;
        this.carrierFuelThreshold = builder.carrierFuelThreshold;
        this.carrierCallsign = builder.carrierCallsign;
        this.carrierName = builder.carrierName;
    }

    public ExecTriggerId getTrigger() {
        return trigger;
    }

    public int getDelayMs() {
        return delayMs;
    }

    public Instant getFiredAt() {
        return firedAt;
    }

    public String getCarrierSystemName() {
        return carrierSystemName;
    }

    public Integer getCarrierFuelLevel() {
        return carrierFuelLevel;
    }

    public Integer getCarrierFuelThreshold() {
        return carrierFuelThreshold;
    }

    public String getCarrierCallsign() {
        return carrierCallsign;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public Map<String, String> toEnvironment() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("EDO_TRIGGER", trigger.name().toLowerCase());
        env.put("EDO_TRIGGER_DELAY_MS", Integer.toString(delayMs));
        env.put("EDO_TIMESTAMP", firedAt.toString());
        putIfPresent(env, "EDO_CARRIER_SYSTEM", carrierSystemName);
        if (carrierFuelLevel != null) {
            env.put("EDO_CARRIER_FUEL_LEVEL", carrierFuelLevel.toString());
        }
        if (carrierFuelThreshold != null) {
            env.put("EDO_CARRIER_FUEL_THRESHOLD", carrierFuelThreshold.toString());
        }
        putIfPresent(env, "EDO_CARRIER_CALLSIGN", carrierCallsign);
        putIfPresent(env, "EDO_CARRIER_NAME", carrierName);
        return env;
    }

    private static void putIfPresent(Map<String, String> env, String key, String value) {
        if (value != null && !value.isBlank()) {
            env.put(key, value.trim());
        }
    }

    public static Builder builder(ExecTriggerId trigger) {
        return new Builder(trigger);
    }

    public static final class Builder {
        private final ExecTriggerId trigger;
        private int delayMs;
        private Instant firedAt;
        private String carrierSystemName;
        private Integer carrierFuelLevel;
        private Integer carrierFuelThreshold;
        private String carrierCallsign;
        private String carrierName;

        private Builder(ExecTriggerId trigger) {
            this.trigger = trigger;
        }

        public Builder delayMs(int delayMs) {
            this.delayMs = Math.max(0, delayMs);
            return this;
        }

        public Builder firedAt(Instant firedAt) {
            this.firedAt = firedAt;
            return this;
        }

        public Builder carrierSystemName(String carrierSystemName) {
            this.carrierSystemName = carrierSystemName;
            return this;
        }

        public Builder carrierFuelLevel(Integer carrierFuelLevel) {
            this.carrierFuelLevel = carrierFuelLevel;
            return this;
        }

        public Builder carrierFuelThreshold(Integer carrierFuelThreshold) {
            this.carrierFuelThreshold = carrierFuelThreshold;
            return this;
        }

        public Builder carrierCallsign(String carrierCallsign) {
            this.carrierCallsign = carrierCallsign;
            return this;
        }

        public Builder carrierName(String carrierName) {
            this.carrierName = carrierName;
            return this;
        }

        public ExecLaunchContext build() {
            return new ExecLaunchContext(this);
        }
    }
}
