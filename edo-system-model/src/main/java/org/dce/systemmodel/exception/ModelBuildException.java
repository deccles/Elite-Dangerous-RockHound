package org.dce.systemmodel.exception;

import java.util.List;

public class ModelBuildException extends RuntimeException {

    private final List<ValidationIssue> issues;

    public ModelBuildException(String userMessage, List<ValidationIssue> issues) {
        super(userMessage);
        this.issues = issues != null ? List.copyOf(issues) : List.of();
    }

    public List<ValidationIssue> issues() {
        return issues;
    }

    public String getUserMessage() {
        return getMessage();
    }
}
