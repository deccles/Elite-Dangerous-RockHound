package org.dce.systemmodel.model;

import org.dce.systemmodel.journal.OrbitalElements;
import org.dce.systemmodel.journal.ParentRef;

import java.util.List;

public record BarycentreNode(
        int bodyId,
        String bodyName,
        ParentRef orbitParent,
        List<ParentRef> journalParents,
        List<Integer> childBodyIds,
        OrbitalElements orbit) {
}
