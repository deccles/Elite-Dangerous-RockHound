package org.dce.ed.mission;

import java.time.Instant;

/** Outstanding requirement for one unassigned self-sourced mission. */
public record MultiCommodityMissionNeed(long missionId, String commodity, int tons, Instant expiry) { }
