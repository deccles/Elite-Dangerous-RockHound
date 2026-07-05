package org.dce.ed.exec;

import java.util.UUID;

import org.dce.ed.logreader.EliteEventType;

/** One external program launch rule bound to a trigger ({@code .jar} or {@code .exe}). */
public final class ExecBinding {

    private String id;
    private boolean enabled;
    private ExecTriggerId trigger;
    private int delayMs;
    private String jarPath;
    private String programArgs;
    /** When {@link ExecTriggerId#JOURNAL_EVENT}: journal {@code event} name (e.g. {@code Docked}). */
    private String journalEventType = EliteEventType.DOCKED.getJournalName();

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

    public String getJournalEventType() {
        return journalEventType;
    }

    public void setJournalEventType(String journalEventType) {
        if (journalEventType == null || journalEventType.isBlank()) {
            this.journalEventType = EliteEventType.DOCKED.getJournalName();
            return;
        }
        EliteEventType parsed = EliteEventType.fromJournalName(journalEventType.trim());
        if (parsed != EliteEventType.UNKNOWN) {
            this.journalEventType = parsed.getJournalName();
            return;
        }
        try {
            this.journalEventType = EliteEventType.valueOf(journalEventType.trim()).getJournalName();
        } catch (IllegalArgumentException ignored) {
            this.journalEventType = journalEventType.trim();
        }
    }

    public EliteEventType getJournalEventTypeEnum() {
        return resolveJournalEventType(journalEventType);
    }

    public void setJournalEventTypeEnum(EliteEventType type) {
        if (type == null || type == EliteEventType.UNKNOWN || type == EliteEventType.FILEHEADER) {
            this.journalEventType = EliteEventType.DOCKED.getJournalName();
            return;
        }
        this.journalEventType = type.getJournalName();
    }

    /** {@code true} when this binding listens for the given journal event type. */
    public boolean matchesJournalEvent(EliteEventType type) {
        if (type == null || type == EliteEventType.UNKNOWN) {
            return false;
        }
        EliteEventType configured = resolveJournalEventType(journalEventType);
        return configured != null && configured != EliteEventType.UNKNOWN && configured == type;
    }

    private static EliteEventType resolveJournalEventType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        EliteEventType byJournal = EliteEventType.fromJournalName(trimmed);
        if (byJournal != EliteEventType.UNKNOWN) {
            return byJournal;
        }
        try {
            return EliteEventType.valueOf(trimmed);
        } catch (IllegalArgumentException ignored) {
            return EliteEventType.UNKNOWN;
        }
    }
}
