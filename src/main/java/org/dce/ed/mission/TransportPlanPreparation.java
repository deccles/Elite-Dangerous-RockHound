package org.dce.ed.mission;

import java.util.List;

public record TransportPlanPreparation(TransportPlanRequest request,
        List<TransportPlanProblem> problems, List<TransportPlanProblem> warnings) {
    public TransportPlanPreparation(TransportPlanRequest request,
            List<TransportPlanProblem> problems) {
        this(request, problems, List.of());
    }

    public TransportPlanPreparation {
        problems = List.copyOf(problems);
        warnings = List.copyOf(warnings);
    }
}
