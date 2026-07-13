package org.dce.ed.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.dce.ed.logreader.EliteEventType;

import com.google.gson.JsonObject;

/** One external program launch rule bound to a trigger ({@code .jar} or {@code .exe}). */
public final class ExecBinding {

    private String id;
    /** User label for this binding (Control Panel button text, etc.). */
    private String name = "";
    private boolean enabled;
    /** When true, a button for this binding appears on the overlay Control Panel tab. */
    private boolean includeOnControlPanel;
    private ExecTriggerId trigger;
    private int delayMs;
    private String jarPath;
    private String programArgs;
    /** When {@link ExecTriggerId#JOURNAL_EVENT}: journal {@code event} name (e.g. {@code Docked}). */
    private String journalEventType = EliteEventType.DOCKED.getJournalName();
    private List<ExecJournalAttributeFilter> journalAttributeFilters = new ArrayList<>();
    /** When {@link ExecTriggerId#SHORTCUT_KEY}: JNativeHook {@code VC_F*} key code (F1–F12). */
    private int shortcutKeyCode = ExecShortcutKeys.DEFAULT_KEY_CODE;

    public ExecBinding() {
        this.id = UUID.randomUUID().toString();
        this.enabled = false;
        this.trigger = ExecTriggerId.NONE;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public boolean isIncludeOnControlPanel() {
        return includeOnControlPanel;
    }

    public void setIncludeOnControlPanel(boolean includeOnControlPanel) {
        this.includeOnControlPanel = includeOnControlPanel;
    }

    /** Label for Control Panel buttons: name, else program file name, else trigger label; appends shortcut key when set. */
    public String controlPanelLabel() {
        String base;
        if (name != null && !name.isBlank()) {
            base = name.trim();
        } else {
            String program = jarPath != null ? jarPath.trim() : "";
            if (!program.isEmpty()) {
                int slash = Math.max(program.lastIndexOf('/'), program.lastIndexOf('\\'));
                base = slash >= 0 ? program.substring(slash + 1) : program;
            } else {
                base = trigger != null ? trigger.getLabel() : "Action";
            }
        }
        if (trigger == ExecTriggerId.SHORTCUT_KEY) {
            return base + " (" + getShortcutKeyDisplay() + ")";
        }
        return base;
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
        this.trigger = trigger != null ? trigger : ExecTriggerId.NONE;
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

    public List<ExecJournalAttributeFilter> getJournalAttributeFilters() {
        if (journalAttributeFilters == null) {
            journalAttributeFilters = new ArrayList<>();
        }
        return journalAttributeFilters;
    }

    public void setJournalAttributeFilters(List<ExecJournalAttributeFilter> journalAttributeFilters) {
        this.journalAttributeFilters = journalAttributeFilters != null
                ? new ArrayList<>(journalAttributeFilters) : new ArrayList<>();
    }

    public int getShortcutKeyCode() {
        return shortcutKeyCode;
    }

    public void setShortcutKeyCode(int shortcutKeyCode) {
        this.shortcutKeyCode = ExecShortcutKeys.normalizeKeyCode(shortcutKeyCode);
    }

    public String getShortcutKeyDisplay() {
        return ExecShortcutKeys.toDisplayString(shortcutKeyCode);
    }

    public void setShortcutKeyDisplay(String display) {
        this.shortcutKeyCode = ExecShortcutKeys.fromDisplayString(display);
    }

    /** After event type matches, check optional top-level JSON field filters. */
    public boolean matchesJournalAttributes(JsonObject raw, Map<String, String> placeholders) {
        if (journalAttributeFilters == null || journalAttributeFilters.isEmpty()) {
            return true;
        }
        return ExecJournalJsonMatcher.matches(raw, journalEventType, journalAttributeFilters, placeholders);
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
