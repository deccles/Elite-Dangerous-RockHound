package org.dce.ed.systemmap;

/**
 * High-level layout strategy for the true-scale system plan map. Rules that pick among these are documented on
 * {@link SystemMapRules} and tested via {@code src/test/resources/systemmap/*.json} fixtures.
 */
public enum SystemLayoutKind {
    /** One map star with orbiting bodies — central star marker and branch parenting. */
    SINGLE_STAR,
    /** Two (or more) stars orbiting the journal barycentre ({@code Parents: Null}) — mutual ring + per-branch orbits. */
    WIDE_BINARY,
    /** Fallback: raw Kepler / journal placement without wide-binary flatten. */
    GENERIC
}
