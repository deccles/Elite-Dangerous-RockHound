package org.dce.ed.route;

/**
 * Route table marker column: current system, side-trip / body target, or pending FSD jump.
 */
public enum RouteMarkerKind {
    NONE,
    CURRENT,
    TARGET,
    PENDING_JUMP
}
