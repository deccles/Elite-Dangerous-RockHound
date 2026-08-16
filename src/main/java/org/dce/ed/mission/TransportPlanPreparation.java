package org.dce.ed.mission;

import java.util.List;

public record TransportPlanPreparation(TransportPlanRequest request,
        List<TransportPlanProblem> problems) {
    public TransportPlanPreparation {
        problems = List.copyOf(problems);
    }
}
