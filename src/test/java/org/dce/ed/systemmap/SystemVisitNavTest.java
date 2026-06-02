package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SystemVisitNavTest {

    @Test
    void backForward_followJournalHistory() {
        SystemVisitNav nav = new SystemVisitNav();
        nav.setJournalHistory(List.of("Alpha", "Beta", "Gamma"), "Beta");

        assertTrue(nav.canBack());
        assertTrue(nav.canForward());
        assertEquals("Alpha", nav.back());
        assertFalse(nav.canBack());
        assertTrue(nav.canForward());
        assertEquals("Beta", nav.forward());
        assertEquals("Gamma", nav.forward());
        assertNull(nav.forward());
    }

    @Test
    void visit_repositionsIndexForManualLoad() {
        SystemVisitNav nav = new SystemVisitNav();
        nav.setJournalHistory(List.of("Alpha", "Beta", "Gamma"), "Gamma");

        nav.visit("Beta");
        assertEquals("Alpha", nav.back());
    }
}
