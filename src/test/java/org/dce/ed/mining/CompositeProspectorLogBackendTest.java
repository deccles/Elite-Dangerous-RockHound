package org.dce.ed.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CompositeProspectorLogBackend}: primary-first writes, mirror failure produces a warning but
 * keeps OK status, primary failure short-circuits, and reads always go to the primary only.
 */
class CompositeProspectorLogBackendTest {

    /** Test double: records calls and returns the configured result. */
    private static final class FakeBackend implements ProspectorLogBackend {
        final String name;
        final List<List<ProspectorLogRow>> appended = new ArrayList<>();
        final List<List<ProspectorLogRow>> upserted = new ArrayList<>();
        int updateRunEndCalls;
        int loadCalls;
        int loadForCommanderCalls;
        ProspectorWriteResult nextAppendResult = ProspectorWriteResult.ok();
        ProspectorWriteResult nextUpsertResult = ProspectorWriteResult.ok();
        ProspectorWriteResult nextUpdateRunEndResult = ProspectorWriteResult.ok();
        ProspectorLoadResult nextLoadResult =
                new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
        boolean preferDebounce;

        FakeBackend(String name) {
            this.name = name;
        }

        @Override
        public void appendRows(List<ProspectorLogRow> rows) {
            // legacy hook unused
        }

        @Override
        public List<ProspectorLogRow> loadRows() {
            return loadRowsWithStatus().getRows();
        }

        @Override
        public void updateRunEndTime(String commander, int run, Instant endTime) {
            // legacy hook unused
        }

        @Override
        public ProspectorWriteResult appendRowsResult(List<ProspectorLogRow> rows) {
            appended.add(new ArrayList<>(rows));
            return nextAppendResult;
        }

        @Override
        public ProspectorWriteResult upsertRowsResult(List<ProspectorLogRow> rows) {
            upserted.add(new ArrayList<>(rows));
            return nextUpsertResult;
        }

        @Override
        public ProspectorWriteResult updateRunEndTimeResult(String commander, int run, Instant endTime) {
            updateRunEndCalls++;
            return nextUpdateRunEndResult;
        }

        @Override
        public ProspectorLoadResult loadRowsWithStatus() {
            loadCalls++;
            return nextLoadResult;
        }

        @Override
        public ProspectorLoadResult loadRowsWithStatusForCommander(String commander) {
            loadForCommanderCalls++;
            return nextLoadResult;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public boolean prefersDebouncedRefresh() {
            return preferDebounce;
        }
    }

    private static List<ProspectorLogRow> oneRow() {
        return List.of(new ProspectorLogRow(
                1, "A", "Sol > Earth", Instant.parse("2026-02-16T14:30:00Z"),
                "Tritium", 10.0, 0.0, 1.0, 1.0,
                "C1", "anaconda", "", 0, null, null, ""));
    }

    @Test
    void appendRowsResult_primaryAndMirrorOk_returnsOkWithoutWarning() {
        FakeBackend primary = new FakeBackend("Primary");
        FakeBackend mirror = new FakeBackend("Mirror");
        CompositeProspectorLogBackend composite = new CompositeProspectorLogBackend(primary, mirror);

        ProspectorWriteResult r = composite.appendRowsResult(oneRow());
        assertTrue(r.isOk());
        assertFalse(r.hasMirrorWarning());
        assertEquals(1, primary.appended.size());
        assertEquals(1, mirror.appended.size());
    }

    @Test
    void appendRowsResult_primaryOk_mirrorFails_returnsOkWithMirrorWarning() {
        FakeBackend primary = new FakeBackend("Primary");
        FakeBackend mirror = new FakeBackend("Mirror");
        mirror.nextAppendResult = ProspectorWriteResult.failure("network timeout");
        CompositeProspectorLogBackend composite = new CompositeProspectorLogBackend(primary, mirror);

        ProspectorWriteResult r = composite.appendRowsResult(oneRow());
        assertTrue(r.isOk());
        assertTrue(r.hasMirrorWarning());
        assertNotNull(r.getMirrorWarning());
        assertTrue(r.getMirrorWarning().contains("Mirror"));
        assertTrue(r.getMirrorWarning().contains("network timeout"));
    }

    @Test
    void appendRowsResult_primaryFails_skipsMirrorAndReturnsFailure() {
        FakeBackend primary = new FakeBackend("Primary");
        FakeBackend mirror = new FakeBackend("Mirror");
        primary.nextAppendResult = ProspectorWriteResult.failure("auth error");
        CompositeProspectorLogBackend composite = new CompositeProspectorLogBackend(primary, mirror);

        ProspectorWriteResult r = composite.appendRowsResult(oneRow());
        assertFalse(r.isOk());
        assertEquals("auth error", r.getMessage());
        assertEquals(1, primary.appended.size());
        assertEquals(0, mirror.appended.size(), "primary failure must short-circuit before mirror");
    }

    @Test
    void upsertRowsResult_appliesSameSemantics() {
        FakeBackend primary = new FakeBackend("Primary");
        FakeBackend mirror = new FakeBackend("Mirror");
        mirror.nextUpsertResult = ProspectorWriteResult.failure("io");
        CompositeProspectorLogBackend composite = new CompositeProspectorLogBackend(primary, mirror);

        ProspectorWriteResult r = composite.upsertRowsResult(oneRow());
        assertTrue(r.isOk());
        assertTrue(r.hasMirrorWarning());
        assertEquals(1, primary.upserted.size());
        assertEquals(1, mirror.upserted.size());
    }

    @Test
    void updateRunEndTimeResult_primaryFails_doesNotCallMirror() {
        FakeBackend primary = new FakeBackend("Primary");
        FakeBackend mirror = new FakeBackend("Mirror");
        primary.nextUpdateRunEndResult = ProspectorWriteResult.failure("nope");
        CompositeProspectorLogBackend composite = new CompositeProspectorLogBackend(primary, mirror);

        ProspectorWriteResult r = composite.updateRunEndTimeResult("Cmdr", 1, Instant.now());
        assertFalse(r.isOk());
        assertEquals(1, primary.updateRunEndCalls);
        assertEquals(0, mirror.updateRunEndCalls);
    }

    @Test
    void loadRowsWithStatus_delegatesOnlyToPrimary() {
        FakeBackend primary = new FakeBackend("Primary");
        primary.nextLoadResult = new ProspectorLoadResult(
                ProspectorLoadResult.Status.OK, oneRow());
        FakeBackend mirror = new FakeBackend("Mirror");
        CompositeProspectorLogBackend composite = new CompositeProspectorLogBackend(primary, mirror);

        ProspectorLoadResult r = composite.loadRowsWithStatus();
        assertEquals(ProspectorLoadResult.Status.OK, r.getStatus());
        assertEquals(1, r.getRows().size());
        assertEquals(1, primary.loadCalls);
        assertEquals(0, mirror.loadCalls, "mirror is never read");
    }

    @Test
    void loadRowsWithStatusForCommander_delegatesOnlyToPrimary() {
        FakeBackend primary = new FakeBackend("Primary");
        FakeBackend mirror = new FakeBackend("Mirror");
        CompositeProspectorLogBackend composite = new CompositeProspectorLogBackend(primary, mirror);

        composite.loadRowsWithStatusForCommander("Cmdr");
        assertEquals(1, primary.loadForCommanderCalls);
        assertEquals(0, mirror.loadForCommanderCalls);
    }

    @Test
    void displayName_includesPrimary() {
        FakeBackend primary = new FakeBackend("Local CSV");
        FakeBackend mirror = new FakeBackend("Google Sheets");
        CompositeProspectorLogBackend composite = new CompositeProspectorLogBackend(primary, mirror);
        assertEquals("Both (primary: Local CSV)", composite.displayName());
    }

    @Test
    void prefersDebouncedRefresh_trueIfEitherSidePrefers() {
        FakeBackend primary = new FakeBackend("A");
        FakeBackend mirror = new FakeBackend("B");
        primary.preferDebounce = false;
        mirror.preferDebounce = true;
        CompositeProspectorLogBackend composite = new CompositeProspectorLogBackend(primary, mirror);
        assertTrue(composite.prefersDebouncedRefresh());
    }

    @Test
    void nullArgs_throw() {
        FakeBackend ok = new FakeBackend("OK");
        try {
            new CompositeProspectorLogBackend(null, ok);
            assertTrue(false, "should have thrown");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            new CompositeProspectorLogBackend(ok, null);
            assertTrue(false, "should have thrown");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    void nullLoadResult_isReturnedAsIs() {
        FakeBackend primary = new FakeBackend("Primary");
        primary.nextLoadResult = null;
        FakeBackend mirror = new FakeBackend("Mirror");
        CompositeProspectorLogBackend composite = new CompositeProspectorLogBackend(primary, mirror);
        assertNull(composite.loadRowsWithStatus());
    }
}
