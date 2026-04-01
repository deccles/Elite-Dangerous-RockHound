package org.dce.ed.mining;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.dce.ed.market.GalacticAveragePrices;

/**
 * Data for the mining scatter plot: prospector readout (per new limpet), leader rows per commander for gather
 * animation, and which log rows should get line-art rocks (one rock per material on the current asteroid).
 */
public final class MiningScatterAsteroidModel {

	public record LeaderSnapshotUpdate(
			Map<String, MiningLeaderSnapshot> previous,
			Map<String, MiningLeaderSnapshot> next) {
	}

	private int prospectorGeneration;
	private Map<String, Double> prospectorPercentByNormalizedKey = Map.of();
	private final Map<String, MiningLeaderSnapshot> leadersByCommander = new HashMap<>();
	/** Per-commander+material gather snapshots (keys {@code commander + "\t" + normalizedMaterial}); drives laser queue for each rock. */
	private final Map<String, MiningLeaderSnapshot> leadersByRockKey = new HashMap<>();

	public int getProspectorGeneration() {
		return prospectorGeneration;
	}

	public Map<String, Double> getProspectorPercentByNormalizedKey() {
		return prospectorPercentByNormalizedKey;
	}

	/**
	 * New prospector limpet hit: replace reported percentages, bump generation, and clear leader snapshots so
	 * animation does not bridge from the previous asteroid.
	 */
	public void onProspectorLimpet(Map<String, Double> uiNameToPercent) {
		prospectorGeneration++;
		if (uiNameToPercent == null || uiNameToPercent.isEmpty()) {
			prospectorPercentByNormalizedKey = Map.of();
		} else {
			Map<String, Double> m = new LinkedHashMap<>();
			for (Map.Entry<String, Double> e : uiNameToPercent.entrySet()) {
				if (e.getKey() == null || e.getKey().isBlank()) {
					continue;
				}
				String nk = GalacticAveragePrices.normalizeMaterialKey(e.getKey());
				m.put(nk, e.getValue());
			}
			prospectorPercentByNormalizedKey = Map.copyOf(m);
		}
		leadersByCommander.clear();
		leadersByRockKey.clear();
	}

	/** Dock / scatter disabled: drop cached prospector snapshot and leader state. */
	public void clearForMiningSessionEnd() {
		prospectorPercentByNormalizedKey = Map.of();
		leadersByCommander.clear();
		leadersByRockKey.clear();
	}

	/**
	 * Recomputes leader snapshots from plotted rows and returns the previous map (for gather transitions) and the new map.
	 */
	public LeaderSnapshotUpdate updateLeadersFromPlotRows(List<ProspectorLogRow> toPlot) {
		Map<String, MiningLeaderSnapshot> previous = Map.copyOf(leadersByCommander);
		Map<String, MiningLeaderSnapshot> next = computeLeaderSnapshots(toPlot);
		leadersByCommander.clear();
		leadersByCommander.putAll(next);
		return new LeaderSnapshotUpdate(previous, next);
	}

	/**
	 * Snapshots for each line-art rock (one per commander + prospector material on current asteroid). Used to
	 * enqueue gather/laser animations per material; the single "leader" row misses secondary materials (e.g. Bromelite).
	 */
	public LeaderSnapshotUpdate updateRockSnapshotsForGather(
			List<ProspectorLogRow> toPlot,
			Set<String> prospectorMaterialKeysNormalized,
			int sessionActiveRun) {
		if (sessionActiveRun <= 0 || toPlot == null || toPlot.isEmpty()
			|| prospectorMaterialKeysNormalized == null || prospectorMaterialKeysNormalized.isEmpty()) {
			Map<String, MiningLeaderSnapshot> prev = Map.copyOf(leadersByRockKey);
			leadersByRockKey.clear();
			return new LeaderSnapshotUpdate(prev, Map.of());
		}
		List<ProspectorLogRow> inSession = toPlot.stream()
			.filter(r -> r.getRun() == sessionActiveRun)
			.toList();
		List<ProspectorLogRow> rocks = computeRockMarkerRows(inSession, prospectorMaterialKeysNormalized);
		Map<String, MiningLeaderSnapshot> next = new HashMap<>();
		for (ProspectorLogRow r : rocks) {
			String cmdr = r.getCommanderName() != null ? r.getCommanderName() : "";
			String nk = GalacticAveragePrices.normalizeMaterialKey(r.getMaterial());
			next.put(cmdr + "\t" + nk, new MiningLeaderSnapshot(r));
		}
		Map<String, MiningLeaderSnapshot> previous = Map.copyOf(leadersByRockKey);
		leadersByRockKey.clear();
		leadersByRockKey.putAll(next);
		return new LeaderSnapshotUpdate(previous, next);
	}

	/**
	 * Latest row per commander on the same asteroid as that commander's globally latest row (same logic as gather leaders).
	 */
	public static Map<String, MiningLeaderSnapshot> computeLeaderSnapshots(List<ProspectorLogRow> toPlot) {
		Map<String, MiningLeaderSnapshot> out = new HashMap<>();
		if (toPlot == null || toPlot.isEmpty()) {
			return out;
		}
		Map<String, ProspectorLogRow> latestRowByCommander = new HashMap<>();
		for (ProspectorLogRow r : toPlot) {
			if (r.getTimestamp() == null) {
				continue;
			}
			String cmdr = r.getCommanderName() != null ? r.getCommanderName() : "";
			ProspectorLogRow prev = latestRowByCommander.get(cmdr);
			if (prev == null || (prev.getTimestamp() != null && r.getTimestamp().isAfter(prev.getTimestamp()))) {
				latestRowByCommander.put(cmdr, r);
			}
		}
		for (Map.Entry<String, ProspectorLogRow> e : latestRowByCommander.entrySet()) {
			String cmdr = e.getKey();
			ProspectorLogRow latest = e.getValue();
			int run = latest.getRun();
			String aid = latest.getAsteroidId() != null ? latest.getAsteroidId() : "";
			List<ProspectorLogRow> sameAsteroid = toPlot.stream()
				.filter(x -> Objects.equals(x.getCommanderName() != null ? x.getCommanderName() : "", cmdr)
					&& x.getRun() == run
					&& Objects.equals(x.getAsteroidId() != null ? x.getAsteroidId() : "", aid))
				.toList();
			ProspectorLogRow leader = sameAsteroid.stream()
				.filter(r -> r.getTimestamp() != null)
				.max(Comparator.comparing(ProspectorLogRow::getTimestamp))
				.orElse(null);
			if (leader != null) {
				out.put(cmdr, new MiningLeaderSnapshot(leader));
			}
		}
		return out;
	}

	/**
	 * Line-art rocks for the active session: per commander, anchor = latest row in-session (max timestamp).
	 * For each prospector material key, at most one row on that commander's current asteroid (latest timestamp
	 * for that material). Materials not in the current prospector readout are excluded by the caller's key set.
	 */
	public static List<ProspectorLogRow> computeRockMarkerRows(
			List<ProspectorLogRow> inSession,
			Set<String> prospectorMaterialKeysNormalized) {
		if (inSession == null || inSession.isEmpty()
			|| prospectorMaterialKeysNormalized == null || prospectorMaterialKeysNormalized.isEmpty()) {
			return List.of();
		}
		Map<String, ProspectorLogRow> latestRowByCommander = new HashMap<>();
		for (ProspectorLogRow r : inSession) {
			Instant ts = r.getTimestamp();
			if (ts == null) {
				continue;
			}
			String cmdr = r.getCommanderName() != null ? r.getCommanderName() : "";
			ProspectorLogRow prev = latestRowByCommander.get(cmdr);
			if (prev == null || (prev.getTimestamp() != null && ts.isAfter(prev.getTimestamp()))) {
				latestRowByCommander.put(cmdr, r);
			}
		}
		List<ProspectorLogRow> out = new ArrayList<>();
		for (Map.Entry<String, ProspectorLogRow> e : latestRowByCommander.entrySet()) {
			String cmdr = e.getKey();
			ProspectorLogRow anchor = e.getValue();
			int run = anchor.getRun();
			String aid = anchor.getAsteroidId() != null ? anchor.getAsteroidId() : "";
			for (String matKey : prospectorMaterialKeysNormalized) {
				ProspectorLogRow best = null;
				for (ProspectorLogRow r : inSession) {
					if (!Objects.equals(r.getCommanderName() != null ? r.getCommanderName() : "", cmdr)) {
						continue;
					}
					if (r.getRun() != run) {
						continue;
					}
					if (!Objects.equals(r.getAsteroidId() != null ? r.getAsteroidId() : "", aid)) {
						continue;
					}
					String nk = GalacticAveragePrices.normalizeMaterialKey(r.getMaterial());
					if (!Objects.equals(matKey, nk)) {
						continue;
					}
					Instant ts = r.getTimestamp();
					if (ts == null) {
						continue;
					}
					if (best == null || (best.getTimestamp() != null && ts.isAfter(best.getTimestamp()))) {
						best = r;
					}
				}
				if (best != null) {
					out.add(best);
				}
			}
		}
		out.sort(Comparator
			.comparing((ProspectorLogRow r) -> r.getCommanderName() != null ? r.getCommanderName() : "")
			.thenComparing((ProspectorLogRow r) -> GalacticAveragePrices.normalizeMaterialKey(r.getMaterial()))
			.thenComparing(ProspectorLogRow::getTimestamp, Comparator.nullsFirst(Comparator.naturalOrder())));
		return out;
	}
}
