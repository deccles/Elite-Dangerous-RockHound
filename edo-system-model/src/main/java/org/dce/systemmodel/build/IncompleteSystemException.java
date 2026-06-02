package org.dce.systemmodel.build;

import java.util.List;

public final class IncompleteSystemException extends org.dce.systemmodel.exception.ModelBuildException {

    private final List<String> reasons;

    public IncompleteSystemException(List<String> reasons) {
        super("System model incomplete: " + String.join("; ", reasons), List.of());
        this.reasons = List.copyOf(reasons);
    }

    public List<String> reasons() {
        return reasons;
    }
}
