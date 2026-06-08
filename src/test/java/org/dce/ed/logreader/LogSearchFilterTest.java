package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class LogSearchFilterTest {

    @Test
    void splitAndTerms_unescapedAmpersand() {
        assertEquals(List.of("ScanBaryCentre", "13"), LogSearchFilter.splitAndTerms("ScanBaryCentre&13"));
        assertEquals(List.of("A|B", "C"), LogSearchFilter.splitAndTerms("A|B&C"));
    }

    @Test
    void splitAndTerms_escapedAmpersandStaysInOneTerm() {
        assertEquals(List.of("A\\&B"), LogSearchFilter.splitAndTerms("A\\&B"));
    }

    @Test
    void andMode_requiresAllTermsAcrossColumns() throws Exception {
        LogSearchFilter filter = LogSearchFilter.compile("ScanBaryCentre&13");
        assertTrue(filter.isAndMode());
        assertTrue(filter.matchesRow("09:57:45", "ScanBaryCentre", "BodyID=13 StarSystem=Foo"));
        assertFalse(filter.matchesRow("09:57:45", "Scan", "BodyID=13"));
        assertFalse(filter.matchesRow("09:57:45", "ScanBaryCentre", "BodyID=12"));
    }

    @Test
    void singleTerm_anyColumnMatches() throws Exception {
        LogSearchFilter filter = LogSearchFilter.compile("ScanBaryCentre");
        assertFalse(filter.isAndMode());
        assertTrue(filter.matchesRow("09:57:45", "ScanBaryCentre", ""));
        assertFalse(filter.matchesRow("09:57:45", "Scan", "BodyID=1"));
    }

    @Test
    void singleTerm_pipeIsOrWithinRegex() throws Exception {
        LogSearchFilter filter = LogSearchFilter.compile("ScanBaryCentre|FSDJump");
        assertTrue(filter.matchesRow("", "FSDJump", ""));
        assertTrue(filter.matchesRow("", "ScanBaryCentre", ""));
    }
}
