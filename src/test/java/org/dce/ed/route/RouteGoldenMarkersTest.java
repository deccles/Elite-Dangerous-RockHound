package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Characterization-style golden: expected marker kinds for a minimal two-hop route while "charging".
 */
class RouteGoldenMarkersTest {

    @Test
    void twoHopCharging_matchesGoldenFile() throws Exception {
        List<RouteEntry> base = new ArrayList<>();
        base.add(row("Sol", 1L, 0, 0, 0));
        base.add(row("Next", 2L, 1, 0, 0));
        RouteTargetState ts = new RouteTargetState();
        RouteSession session = new RouteSession(new ChargingFlash(), j -> false);
        session.applyNavRouteReloadParsed(base);
        session.setCurrentSystemName("Sol");
        List<RouteEntry> displayed = RouteLayoutEngine.buildDisplayedEntries(
                session.getBaseRouteEntries(),
                null,
                "Sol",
                1L,
                null,
                ts,
                null,
                0L,
                (n, a, p) -> null,
                true);
        List<String> actual = displayed.stream()
                .filter(e -> !e.isBodyRow)
                .map(e -> e.markerKind.name())
                .collect(Collectors.toList());
        List<String> expected;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                RouteGoldenMarkersTest.class.getResourceAsStream("/org/dce/ed/route/golden/two-hop-charging-markers.txt"),
                StandardCharsets.UTF_8))) {
            expected = r.lines().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        }
        assertEquals(expected, actual);
    }

    private static RouteEntry row(String name, long addr, double x, double y, double z) {
        RouteEntry e = new RouteEntry();
        e.systemName = name;
        e.systemAddress = addr;
        e.x = x;
        e.y = y;
        e.z = z;
        e.status = RouteScanStatus.UNKNOWN;
        return e;
    }

    private static final class ChargingFlash implements RouteJumpFlashHandle {
        @Override
        public boolean isTimerRunning() {
            return true;
        }

        @Override
        public void startTimer() {
        }

        @Override
        public void stopTimer() {
        }
    }
}
