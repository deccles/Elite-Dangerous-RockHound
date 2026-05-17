package org.dce.ed.systemmap;

/**
 * High-level schematic layout strategy for the system plan map. Rules that pick among these are documented on
 * {@link SystemMapRules} and tested via {@code src/test/resources/systemmap/*.json} fixtures.
 */
public enum SystemLayoutKind {
    /** One barycentric star with multiple orbiting bodies — concentric rings at the central star. */
    SINGLE_STAR_SCHEMATIC,
    /** Two (or more) stars orbiting the journal barycentre ({@code Parents: Null}) — mutual ring + per-branch rings. */
    WIDE_BINARY,
    /** Fallback: raw Kepler / journal placement without branch flatten or single-star schematic circles. */
    GENERIC
}
