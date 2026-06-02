package org.dce.systemmodel.exception;

public record ValidationIssue(IssueKind kind, int bodyId, String field, String message) {

    public enum IssueKind {
        MISSING_SCAN,
        MISSING_ORBITAL_ELEMENT,
        MISSING_BARYCENTRE_ROW,
        INVALID_PARENT_REF,
        DESIGNATION_HIERARCHY_MISMATCH,
        JOURNAL_PARENT_MISMATCH
    }
}
