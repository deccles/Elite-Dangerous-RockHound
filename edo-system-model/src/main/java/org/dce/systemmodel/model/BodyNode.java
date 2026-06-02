package org.dce.systemmodel.model;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;

import java.util.List;

public record BodyNode(
        int bodyId,
        String bodyName,
        BodyKind kind,
        String bodyType,
        String subType,
        double distanceFromArrivalLs,
        ParentRef orbitParent,
        List<ParentRef> journalParents,
        OrbitalElements orbit,
        boolean definitive) {
}
