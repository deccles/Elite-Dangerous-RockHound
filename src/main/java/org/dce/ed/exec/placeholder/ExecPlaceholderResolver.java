package org.dce.ed.exec.placeholder;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.exec.CarrierFuelTracker;
import org.dce.ed.exec.ExecLaunchContext;
import org.dce.ed.exec.ExecTriggerId;
import org.dce.ed.logreader.OwnedFleetCarrierTracker;
import org.dce.ed.route.RouteSession;
import org.dce.ed.route.RouteTargetState;
import org.dce.ed.state.SystemState;

/** Resolves {@link ExecPlaceholderId} values from overlay state at exec trigger time. */
public final class ExecPlaceholderResolver {

    public static final String UNKNOWN = "Unknown";

    private ExecPlaceholderResolver() {
    }

    public static Map<String, String> resolveAll(ExecPlaceholderContext ctx, ExecLaunchContext launch) {
        Map<String, String> out = new LinkedHashMap<>();
        if (ctx == null) {
            return out;
        }
        for (ExecPlaceholderId id : ExecPlaceholderId.values()) {
            out.put(id.name(), resolveOne(ctx, launch, id));
        }
        return out;
    }

    public static String resolveOne(ExecPlaceholderContext ctx, ExecLaunchContext launch, ExecPlaceholderId id) {
        if (id == null) {
            return UNKNOWN;
        }
        if (ctx == null) {
            return UNKNOWN;
        }
        return valueOrUnknown(switch (id) {
            case FLEET_CARRIER_DESTINATION, DESTINATION -> blankOrNull(ExecPlaceholderContext.fleetNextDestination(ctx));
            case ROUTE_NEXT_DESTINATION -> blankOrNull(ExecPlaceholderContext.shipNextDestination(ctx));
            case ROUTE_CURRENT_SYSTEM -> routeField(ctx.shipRoute(), RouteSession::getCurrentSystemName);
            case FLEET_ROUTE_CURRENT_SYSTEM -> routeField(ctx.fleetRoute(), RouteSession::getCurrentSystemName);
            case ROUTE_TARGET_SYSTEM -> targetField(ctx.shipRoute(), RouteTargetState::getTargetSystemName);
            case ROUTE_DEST_NAME -> targetField(ctx.shipRoute(), RouteTargetState::getDestinationName);
            case CARRIER_JUMP_TARGET -> blankOrNull(ctx.carrierJumpTarget());
            case PENDING_JUMP_SYSTEM -> routeField(ctx.fleetRoute(), RouteSession::getPendingJumpSystemName);
            case STATUS_DEST_SYSTEM -> blankOrNull(ctx.commanderSnapshot().getStatusDestSystem());
            case STATUS_DEST_NAME -> blankOrNull(ctx.commanderSnapshot().getStatusDestName());
            case FSD_TARGET -> blankOrNull(ctx.commanderSnapshot().getFsdTarget());
            case FSD_REMAINING_JUMPS -> {
                Integer n = ctx.commanderSnapshot().getFsdRemainingJumps();
                yield n != null ? Integer.toString(n) : null;
            }
            case CLIPBOARD -> launch != null ? blankOrNull(launch.getClipboard()) : null;
            case TRIGGER -> launch != null && launch.getTrigger() != null
                    ? launch.getTrigger().name().toLowerCase(Locale.ROOT) : null;
            case TIMESTAMP -> launch != null && launch.getFiredAt() != null
                    ? launch.getFiredAt().toString() : null;
            case CARRIER_SYSTEM -> {
                OwnedFleetCarrierTracker t = ctx.ownedTracker();
                yield t != null ? blankOrNull(t.getOwnedSystemName()) : null;
            }
            case CARRIER_NAME -> {
                if (launch != null && launch.getCarrierName() != null) {
                    String fromLaunch = blankOrNull(launch.getCarrierName());
                    if (fromLaunch != null) {
                        yield fromLaunch;
                    }
                }
                CarrierFuelTracker ft = ctx.fuelTracker();
                yield ft != null ? blankOrNull(ft.getLastKnownCarrierName()) : null;
            }
            case CARRIER_CALLSIGN -> {
                if (launch != null && launch.getCarrierCallsign() != null) {
                    String fromLaunch = blankOrNull(launch.getCarrierCallsign());
                    if (fromLaunch != null) {
                        yield fromLaunch;
                    }
                }
                CarrierFuelTracker ft = ctx.fuelTracker();
                yield ft != null ? blankOrNull(ft.getLastKnownCallsign()) : null;
            }
            case CARRIER_FUEL_LEVEL -> {
                CarrierFuelTracker ft = ctx.fuelTracker();
                int fuel = ft != null ? ft.getLastKnownFuelLevel() : -1;
                if (launch != null && launch.getCarrierFuelLevel() != null) {
                    fuel = launch.getCarrierFuelLevel().intValue();
                }
                yield fuel >= 0 ? Integer.toString(fuel) : null;
            }
            case CARRIER_FUEL_THRESHOLD -> {
                int threshold = ctx.tritiumThreshold();
                if (launch != null && launch.getCarrierFuelThreshold() != null) {
                    threshold = launch.getCarrierFuelThreshold().intValue();
                }
                yield Integer.toString(threshold);
            }
            case CARRIER_PARKED_BODY_ID -> systemField(ctx.systemState(), s -> {
                Integer id2 = s.getCarrierParkedBodyId();
                return id2 != null ? id2.toString() : null;
            });
            case COMMANDER_ABOARD_CARRIER -> systemField(ctx.systemState(), s ->
                    Boolean.toString(s.isCommanderAboardFleetCarrier()));
            case SYSTEM_NAME -> systemField(ctx.systemState(), SystemState::getSystemName);
            case SYSTEM_ADDRESS -> systemField(ctx.systemState(), s -> {
                long addr = s.getSystemAddress();
                return addr > 0L ? Long.toString(addr) : null;
            });
            case STAR_POS -> systemField(ctx.systemState(), s -> formatStarPos(s.getStarPos()));
            case TARGET_BODY_NAME -> blankOrNull(ctx.targetBodyName());
            case NEAR_BODY_NAME -> blankOrNull(ctx.nearBodyName());
            case BODY_NAME -> blankOrNull(ctx.commanderSnapshot().getBodyName());
            case FSS_PROGRESS -> systemField(ctx.systemState(), s -> {
                Double p = s.getFssProgress();
                return p != null ? String.format(Locale.ROOT, "%.1f", p) : null;
            });
            case TOTAL_BODIES -> systemField(ctx.systemState(), s -> {
                Integer n = s.getTotalBodies();
                return n != null ? n.toString() : null;
            });
            case DOCKED -> systemField(ctx.systemState(), s -> Boolean.toString(s.isDocked()));
            case VISITED_BY_ME -> systemField(ctx.systemState(), s -> Boolean.toString(s.isVisitedByMe()));
            case COMMANDER -> blankOrNull(ctx.commanderSnapshot().getCommander());
            case GAME_MODE -> blankOrNull(ctx.commanderSnapshot().getGameMode());
            case CREDITS -> {
                long bal = ctx.commanderSnapshot().getBalance();
                if (bal <= 0L) {
                    bal = ctx.commanderSnapshot().getCreditsFromLoadGame();
                }
                yield bal > 0L ? Long.toString(bal) : (bal == 0L ? "0" : null);
            }
            case EXOBIOLOGY_CREDITS -> Long.toString(ctx.exobiologyCredits());
            case GEO_SURVEY_CREDITS -> Long.toString(ctx.geoSurveyCredits());
            case BOUNTY_CREDITS -> Long.toString(ctx.bountyCredits());
            case CARGO -> String.format(Locale.ROOT, "%.2f", ctx.commanderSnapshot().getCargo());
            case SHIP_TYPE -> blankOrNull(ctx.commanderSnapshot().getShipType());
            case SHIP_NAME -> blankOrNull(ctx.commanderSnapshot().getShipName());
            case SHIP_IDENT -> blankOrNull(ctx.commanderSnapshot().getShipIdent());
            case SHIP_ID -> {
                int sid = ctx.commanderSnapshot().getShipId();
                yield sid >= 0 ? Integer.toString(sid) : null;
            }
            case SHIP_FUEL -> String.format(Locale.ROOT, "%.2f", ctx.commanderSnapshot().getShipFuel());
            case SHIP_FUEL_CAPACITY -> {
                double cap = ctx.commanderSnapshot().getShipFuelCapacity();
                yield cap > 0 ? String.format(Locale.ROOT, "%.2f", cap) : null;
            }
            case SHIP_FUEL_PERCENT -> {
                double cap = ctx.commanderSnapshot().getShipFuelCapacity();
                double fuel = ctx.commanderSnapshot().getShipFuel();
                if (cap <= 0) {
                    yield null;
                }
                yield Integer.toString((int) Math.round(100.0 * fuel / cap));
            }
            case LEGAL_STATE -> blankOrNull(ctx.commanderSnapshot().getLegalState());
        });
    }

    public static String resolveToken(ExecPlaceholderContext ctx, ExecLaunchContext launch, String token) {
        return ExecPlaceholderId.fromToken(token)
                .map(id -> resolveOne(ctx, launch, id))
                .orElse(UNKNOWN);
    }

    /** Returns {@link #UNKNOWN} when the resolved value is null or blank. */
    public static String valueOrUnknown(String s) {
        return s == null || s.isBlank() ? UNKNOWN : s.trim();
    }

    private interface RouteStringFn {
        String apply(RouteSession session);
    }

    private interface TargetStringFn {
        String apply(RouteTargetState target);
    }

    private interface SystemStringFn {
        String apply(SystemState state);
    }

    private static String routeField(RouteSession session, RouteStringFn fn) {
        if (session == null) {
            return null;
        }
        return blankOrNull(fn.apply(session));
    }

    private static String targetField(RouteSession session, TargetStringFn fn) {
        if (session == null || session.getTargetState() == null) {
            return null;
        }
        return blankOrNull(fn.apply(session.getTargetState()));
    }

    private static String systemField(SystemState state, SystemStringFn fn) {
        if (state == null) {
            return null;
        }
        return blankOrNull(fn.apply(state));
    }

    private static String formatStarPos(double[] pos) {
        if (pos == null || pos.length < 3) {
            return null;
        }
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", pos[0], pos[1], pos[2]);
    }

    private static String blankOrNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
