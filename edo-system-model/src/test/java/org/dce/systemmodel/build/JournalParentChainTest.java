package org.dce.systemmodel.build;

import org.dce.systemmodel.journal.ParentRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalParentChainTest {

    @Test
    void immediateOrbitParent_returnsFirst() {
        ParentRef p = JournalParentChain.immediateOrbitParent(List.of(
                new ParentRef(ParentRef.ParentType.PLANET, 8),
                new ParentRef(ParentRef.ParentType.NULL, 7),
                new ParentRef(ParentRef.ParentType.STAR, 0)));
        assertEquals(ParentRef.ParentType.PLANET, p.type());
        assertEquals(8, p.bodyId());
    }

    @Test
    void emptyParents_returnsNull() {
        assertNull(JournalParentChain.immediateOrbitParent(List.of()));
        assertNull(JournalParentChain.immediateOrbitParent(null));
    }

    @Test
    void directParentIsNull() {
        assertTrue(JournalParentChain.directParentIsNull(
                List.of(new ParentRef(ParentRef.ParentType.NULL, 32)), 32));
        assertFalse(JournalParentChain.directParentIsNull(
                List.of(new ParentRef(ParentRef.ParentType.PLANET, 28)), 32));
    }
}
