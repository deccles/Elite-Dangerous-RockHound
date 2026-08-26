package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RouteScanStatusMemoryTest {

    @Test
    void resolvedUnknownSurvivesFreshSnapshotByAddress() {
        RouteScanStatusMemory memory = new RouteScanStatusMemory();
        RouteEntry original = new RouteEntry(0, "Alpha", 42L, "K", 0.0, RouteScanStatus.UNKNOWN);
        RouteEntry rebuilt = new RouteEntry(0, "Alpha", 42L, "K", 0.0, RouteScanStatus.PENDING);

        memory.remember(original, RouteScanStatus.UNKNOWN);
        memory.applyTo(rebuilt);

        assertEquals(RouteScanStatus.UNKNOWN, rebuilt.status);
    }

    @Test
    void resolvedStatusSurvivesFreshSnapshotByNameWhenAddressIsMissing() {
        RouteScanStatusMemory memory = new RouteScanStatusMemory();
        RouteEntry original = new RouteEntry(0, "Addressless", 0L, "?", 0.0,
                RouteScanStatus.FULLY_DISCOVERED_NOT_VISITED);
        RouteEntry rebuilt = new RouteEntry(0, "addressless", 0L, "?", 0.0, RouteScanStatus.PENDING);

        memory.remember(original, original.status);
        memory.applyTo(rebuilt);

        assertEquals(RouteScanStatus.FULLY_DISCOVERED_NOT_VISITED, rebuilt.status);
    }
}
