package org.dce.ed.mining;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of the "leader" log row used for gather animation and leader tracking on the mining scatter plot.
 */
public record MiningLeaderSnapshot(
		int run,
		String asteroidId,
		Instant instant,
		double pct,
		double tons,
		String material
) {
	public MiningLeaderSnapshot(ProspectorLogRow r) {
		this(
			r.getRun(),
			r.getAsteroidId() != null ? r.getAsteroidId() : "",
			r.getTimestamp(),
			r.getPercent(),
			r.getDifference(),
			r.getMaterial() != null ? r.getMaterial() : "");
	}

	public boolean sameAsteroid(MiningLeaderSnapshot o) {
		return o != null && run == o.run && Objects.equals(asteroidId, o.asteroidId);
	}
}
