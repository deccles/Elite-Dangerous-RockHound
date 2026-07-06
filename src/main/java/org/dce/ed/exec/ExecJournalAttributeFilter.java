package org.dce.ed.exec;

/** One top-level JSON field filter for journal event exec bindings. */
public final class ExecJournalAttributeFilter {

    public enum MatchMode {
        EQUALS,
        CONTAINS,
        EXISTS
    }

    private String field = "";
    private String expectedValue = "";
    private MatchMode matchMode = MatchMode.EQUALS;

    public ExecJournalAttributeFilter() {
    }

    public ExecJournalAttributeFilter(String field, String expectedValue, MatchMode matchMode) {
        this.field = field != null ? field.trim() : "";
        this.expectedValue = expectedValue != null ? expectedValue : "";
        this.matchMode = matchMode != null ? matchMode : MatchMode.EQUALS;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field != null ? field.trim() : "";
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue != null ? expectedValue : "";
    }

    public MatchMode getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(MatchMode matchMode) {
        this.matchMode = matchMode != null ? matchMode : MatchMode.EQUALS;
    }
}
