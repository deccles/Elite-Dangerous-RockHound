package org.dce.ed.exec.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.dce.ed.exec.CarrierFuelTracker;
import org.dce.ed.exec.ExecLaunchContext;
import org.dce.ed.exec.ExecTriggerId;
import org.junit.jupiter.api.Test;

class ExecPlaceholderSubstitutorTest {

    @Test
    void expandProgramArgs_resolvesSymbolAsSingleToken() {
        ExecPlaceholderContext ctx = new ExecPlaceholderContext();
        ctx.setCarrierJumpTargetSupplier(() -> "Col 285 Sector IX-T b3-3");
        ExecLaunchContext launch = ExecLaunchContext.builder(ExecTriggerId.MANUAL).build();
        Map<String, String> resolved = ExecPlaceholderResolver.resolveAll(ctx, launch);

        List<String> args = ExecPlaceholderSubstitutor.expandProgramArgs(
                "--play fleet-map $CARRIER_JUMP_TARGET",
                ctx, launch, resolved);

        assertEquals(3, args.size());
        assertEquals("--play", args.get(0));
        assertEquals("fleet-map", args.get(1));
        assertEquals("Col 285 Sector IX-T b3-3", args.get(2));
    }

    @Test
    void clipboard_fromLaunchContext() {
        ExecPlaceholderContext ctx = new ExecPlaceholderContext();
        ExecLaunchContext launch = ExecLaunchContext.builder(ExecTriggerId.MANUAL)
                .clipboard("Eol Prou LH-K c9-96")
                .build();
        assertEquals("Eol Prou LH-K c9-96",
                ExecPlaceholderResolver.resolveOne(ctx, launch, ExecPlaceholderId.CLIPBOARD));

        List<String> args = ExecPlaceholderSubstitutor.expandProgramArgs(
                "--play paste-dest $CLIPBOARD",
                ctx, launch, ExecPlaceholderResolver.resolveAll(ctx, launch));
        assertEquals(List.of("--play", "paste-dest", "Eol Prou LH-K c9-96"), args);
    }

    @Test
    void clipboard_unknownWhenClipboardCleared() {
        ExecPlaceholderContext ctx = new ExecPlaceholderContext();
        ExecLaunchContext launch = ExecLaunchContext.builder(ExecTriggerId.FLEET_COOLDOWN_COMPLETE)
                .clipboardCleared(true)
                .build();
        assertEquals("Unknown",
                ExecPlaceholderResolver.resolveOne(ctx, launch, ExecPlaceholderId.CLIPBOARD));
    }

    @Test
    void tokenizer_respectsQuotedLiterals() {
        List<String> tokens = ExecArgsTokenizer.tokenize("--play \"fleet map\" $SYSTEM_NAME");
        assertEquals(3, tokens.size());
        assertEquals("--play", tokens.get(0));
        assertEquals("fleet map", tokens.get(1));
        assertEquals("$SYSTEM_NAME", tokens.get(2));
    }

    @Test
    void destinationAlias_matchesFleetCarrierDestination() {
        ExecPlaceholderContext ctx = new ExecPlaceholderContext();
        ctx.setFleetRouteSessionSupplier(() -> null);
        String fleet = ExecPlaceholderResolver.resolveOne(ctx, null, ExecPlaceholderId.FLEET_CARRIER_DESTINATION);
        String dest = ExecPlaceholderResolver.resolveOne(ctx, null, ExecPlaceholderId.DESTINATION);
        assertEquals("Unknown", fleet);
        assertEquals("Unknown", dest);
    }

    @Test
    void carrierFuelLevel_unknownWhenNotLoaded() {
        ExecPlaceholderContext ctx = new ExecPlaceholderContext();
        assertEquals("Unknown",
                ExecPlaceholderResolver.resolveOne(ctx, null, ExecPlaceholderId.CARRIER_FUEL_LEVEL));
    }

    @Test
    void carrierName_fromFuelTrackerWhenNoLaunchContext() {
        CarrierFuelTracker tracker = new CarrierFuelTracker();
        tracker.ingestCarrierStats(com.google.gson.JsonParser.parseString(
                "{ \"CarrierID\": 1, \"Name\": \"My Carrier\", \"Callsign\": \"ABC-12X\" }").getAsJsonObject(), 0L);
        ExecPlaceholderContext ctx = new ExecPlaceholderContext();
        ctx.setCarrierFuelTrackerSupplier(() -> tracker);
        assertEquals("My Carrier",
                ExecPlaceholderResolver.resolveOne(ctx, null, ExecPlaceholderId.CARRIER_NAME));
        assertEquals("ABC-12X",
                ExecPlaceholderResolver.resolveOne(ctx, null, ExecPlaceholderId.CARRIER_CALLSIGN));
    }

    @Test
    void fleetRouteCurrentSystem_readsFleetCarrierRouteSession() {
        org.dce.ed.route.RouteSession fleet = new org.dce.ed.route.RouteSession(
                new org.dce.ed.route.RouteJumpFlashHandle() {
                    @Override
                    public boolean isTimerRunning() {
                        return false;
                    }

                    @Override
                    public void startTimer() {
                    }

                    @Override
                    public void stopTimer() {
                    }
                },
                j -> true);
        fleet.applyKnownCurrentSystem("Traikee UP-O d6-33", 200L, null);

        ExecPlaceholderContext ctx = new ExecPlaceholderContext();
        ctx.setFleetRouteSessionSupplier(() -> fleet);
        ctx.setShipRouteSessionSupplier(() -> {
            org.dce.ed.route.RouteSession ship = new org.dce.ed.route.RouteSession(
                    new org.dce.ed.route.RouteJumpFlashHandle() {
                        @Override
                        public boolean isTimerRunning() {
                            return false;
                        }

                        @Override
                        public void startTimer() {
                        }

                        @Override
                        public void stopTimer() {
                        }
                    },
                    j -> false);
            ship.applyKnownCurrentSystem("Sol", 100L, null);
            return ship;
        });

        assertEquals("Traikee UP-O d6-33",
                ExecPlaceholderResolver.resolveOne(ctx, null, ExecPlaceholderId.FLEET_ROUTE_CURRENT_SYSTEM));
        assertEquals("Sol",
                ExecPlaceholderResolver.resolveOne(ctx, null, ExecPlaceholderId.ROUTE_CURRENT_SYSTEM));
    }
}
