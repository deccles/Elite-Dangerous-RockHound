package org.dce.ed.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.SupercruiseExitEvent;
import org.dce.ed.route.RouteEntry;
import org.dce.ed.route.RouteJournalApplyOutcome;
import org.dce.ed.route.RouteSession;
import org.junit.jupiter.api.Test;

/**
 * Replay of the 2026-08-04 Gliese↔Core loop window where live EDO stayed on Core
 * after arriving in Gliese (journal had FSDJump + SupercruiseExit + Docked).
 */
class MissedFsdJumpJournalReplayTest {

    private static final long CORE_ADDR = 22958210698080L;
    private static final long GLIESE_ADDR = 2557753660122L;

    @Test
    void supercruiseExit_recoversSystemState_whenFsdJumpMissed() throws Exception {
        SystemState state = new SystemState();
        SystemEventProcessor proc = new SystemEventProcessor("test", state);
        // Simulate: Core FSDJump applied, Gliese FSDJump dropped by live monitor.
        for (EliteLogEvent e : loadFixture()) {
            if (e instanceof FsdJumpEvent jump && "Gliese 868".equals(jump.getStarSystem())) {
                continue; // intentional miss
            }
            proc.handleEvent(e);
        }
        assertEquals("Gliese 868", state.getSystemName());
        assertEquals(GLIESE_ADDR, state.getSystemAddress());
        assertTrue(state.isDocked());
    }

    @Test
    void supercruiseExitAlone_recoversSystemState_beforeDocked() {
        SystemState state = new SystemState();
        state.setSystemName("Core Sys Sector CB-O a6-1");
        state.setSystemAddress(CORE_ADDR);
        SystemEventProcessor proc = new SystemEventProcessor("test", state);

        JsonFixture sc = JsonFixture.supercruiseExitGliese();
        proc.handleEvent(sc.event);

        assertEquals("Gliese 868", state.getSystemName());
        assertEquals(GLIESE_ADDR, state.getSystemAddress());
    }

    @Test
    void routeSession_advancesOnSupercruiseExit_whenFsdJumpMissed() throws Exception {
        RouteSession session = new RouteSession(null, j -> false);
        List<RouteEntry> loop = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            boolean gliese = (i % 2) == 0;
            loop.add(entry(i, gliese ? "Gliese 868" : "Core Sys Sector CB-O a6-1",
                    gliese ? GLIESE_ADDR : CORE_ADDR));
        }
        session.replaceBaseRouteEntries(loop);
        // Sit on Core #11 (same relative situation as the live miss).
        session.applyKnownCurrentSystem("Gliese 868", GLIESE_ADDR, null); // -> 0
        for (int hop = 0; hop < 5; hop++) {
            session.applyKnownCurrentSystem("Core Sys Sector CB-O a6-1", CORE_ADDR, null);
            session.applyKnownCurrentSystem("Gliese 868", GLIESE_ADDR, null);
        }
        session.applyKnownCurrentSystem("Core Sys Sector CB-O a6-1", CORE_ADDR, null);
        assertEquals(11, session.getCurrentBaseIndex());
        assertEquals("Core Sys Sector CB-O a6-1", session.getCurrentSystemName());

        EliteLogEvent sc = null;
        for (EliteLogEvent e : loadFixture()) {
            if (e instanceof SupercruiseExitEvent sex && "Gliese 868".equals(sex.getStarSystem())
                    && sex.getTimestamp().toString().startsWith("2026-08-04T16:27")) {
                sc = e;
                break;
            }
        }
        assertTrue(sc != null);
        RouteJournalApplyOutcome o = session.applySecondaryJournalEvent(sc);
        assertTrue(o.refreshDisplayedRows());
        assertEquals("Gliese 868", session.getCurrentSystemName());
        assertEquals(12, session.getCurrentBaseIndex());
    }

    private static List<EliteLogEvent> loadFixture() throws Exception {
        String path = "/journal/gliese_core_missed_fsdjump_window.ndjson";
        InputStream in = MissedFsdJumpJournalReplayTest.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("missing fixture " + path);
        }
        EliteLogParser parser = new EliteLogParser();
        List<EliteLogEvent> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                EliteLogEvent e = parser.parseRecord(line);
                if (e != null) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    private static RouteEntry entry(int index, String name, long addr) {
        RouteEntry e = new RouteEntry(index, name, addr, "K", 0.0, null);
        e.index = index;
        return e;
    }

    /** Tiny helper so the alone-recovery test does not depend on parser wiring. */
    private static final class JsonFixture {
        final EliteLogEvent event;

        private JsonFixture(EliteLogEvent event) {
            this.event = event;
        }

        static JsonFixture supercruiseExitGliese() {
            String line = "{\"timestamp\":\"2026-08-04T16:27:30Z\",\"event\":\"SupercruiseExit\","
                    + "\"Taxi\":false,\"Multicrew\":false,\"StarSystem\":\"Gliese 868\","
                    + "\"SystemAddress\":2557753660122,\"Body\":\"MacLean Terminal\","
                    + "\"BodyID\":67,\"BodyType\":\"Station\"}";
            return new JsonFixture(new EliteLogParser().parseRecord(line));
        }
    }
}
