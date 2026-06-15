package org.dce.ed.exec;

import java.util.UUID;

/** One JAR launch rule bound to a trigger. */
public final class ExecBinding {

    private String id;
    private boolean enabled;
    private ExecTriggerId trigger;
    private int delayMs;
    private String jarPath;
    private String programArgs;

    public ExecBinding() {
        this.id = UUID.randomUUID().toString();
        this.enabled = false;
        this.trigger = ExecTriggerId.FLEET_COOLDOWN_COMPLETE;
        this.delayMs = 0;
        this.jarPath = "";
        this.programArgs = "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ExecTriggerId getTrigger() {
        return trigger;
    }

    public void setTrigger(ExecTriggerId trigger) {
        this.trigger = trigger != null ? trigger : ExecTriggerId.FLEET_COOLDOWN_COMPLETE;
    }

    public int getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(int delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    public String getJarPath() {
        return jarPath;
    }

    public void setJarPath(String jarPath) {
        this.jarPath = jarPath != null ? jarPath.trim() : "";
    }

    public String getProgramArgs() {
        return programArgs;
    }

    public void setProgramArgs(String programArgs) {
        this.programArgs = programArgs != null ? programArgs.trim() : "";
    }
}
