package org.dce.ed.exec;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional {@code edo} block on a dropped script JSON. Generic convention for any tool that
 * wants EDO to install a file and create an Exec binding.
 */
public final class EdoScriptMetadata {

    private String bindingName = "";
    private boolean includeOnControlPanel;
    private String buttonTab = "";
    private String trigger = "";
    private int delaySeconds;
    private String programName = "";
    private String programArgs = "";
    private String installArgs = "";
    private String journalEventType = "";
    private List<ExecJournalAttributeFilter> journalAttributeFilters = new ArrayList<>();
    private String shortcutKey = "";

    public String getBindingName() {
        return bindingName;
    }

    public void setBindingName(String bindingName) {
        this.bindingName = bindingName != null ? bindingName.trim() : "";
    }

    public boolean isIncludeOnControlPanel() {
        return includeOnControlPanel;
    }

    public void setIncludeOnControlPanel(boolean includeOnControlPanel) {
        this.includeOnControlPanel = includeOnControlPanel;
    }

    public String getButtonTab() {
        return buttonTab;
    }

    public void setButtonTab(String buttonTab) {
        this.buttonTab = buttonTab != null ? buttonTab.trim() : "";
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger != null ? trigger.trim() : "";
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(int delaySeconds) {
        this.delaySeconds = Math.max(0, delaySeconds);
    }

    public String getProgramName() {
        return programName;
    }

    public void setProgramName(String programName) {
        this.programName = programName != null ? programName.trim() : "";
    }

    public String getProgramArgs() {
        return programArgs;
    }

    public void setProgramArgs(String programArgs) {
        this.programArgs = programArgs != null ? programArgs.trim() : "";
    }

    public String getInstallArgs() {
        return installArgs;
    }

    public void setInstallArgs(String installArgs) {
        this.installArgs = installArgs != null ? installArgs.trim() : "";
    }

    public String getJournalEventType() {
        return journalEventType;
    }

    public void setJournalEventType(String journalEventType) {
        this.journalEventType = journalEventType != null ? journalEventType.trim() : "";
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

    public String getShortcutKey() {
        return shortcutKey;
    }

    public void setShortcutKey(String shortcutKey) {
        this.shortcutKey = shortcutKey != null ? shortcutKey.trim() : "";
    }

    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (programName.isBlank()) {
            errors.add("edo.programName is required");
        }
        if (programArgs.isBlank()) {
            errors.add("edo.programArgs is required");
        }
        if (installArgs.isBlank()) {
            errors.add("edo.installArgs is required");
        } else if (!installArgs.contains("$FILE")) {
            errors.add("edo.installArgs must include $FILE");
        }
        return errors;
    }
}
