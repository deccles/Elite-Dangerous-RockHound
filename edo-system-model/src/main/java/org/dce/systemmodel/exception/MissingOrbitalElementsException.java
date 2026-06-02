package org.dce.systemmodel.exception;

import java.util.List;

public final class MissingOrbitalElementsException extends RuntimeException {

    private final int bodyId;
    private final List<String> missingFields;

    public MissingOrbitalElementsException(int bodyId, List<String> missingFields) {
        super(formatMessage(bodyId, missingFields));
        this.bodyId = bodyId;
        this.missingFields = List.copyOf(missingFields);
    }

    public int bodyId() {
        return bodyId;
    }

    public List<String> missingFields() {
        return missingFields;
    }

    private static String formatMessage(int bodyId, List<String> missingFields) {
        return bodyId + ": missing " + String.join(", ", missingFields) + " for position at sim time";
    }
}
