package org.dce.ed.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.market.GalacticAveragePrices;
import org.junit.jupiter.api.Test;

class MiningScatterAsteroidModelTest {

	private static ProspectorLogRow row(
			int run,
			String asteroidId,
			Instant ts,
			String material,
			double pct,
			double tons,
			String commander) {
		return new ProspectorLogRow(
			run,
			asteroidId,
			"Body",
			ts,
			material,
			pct,
			0,
			0,
			tons,
			commander,
			"",
			0,
			null,
			null);
	}

	@Test
	void onProspectorLimpet_incrementsGeneration_andStoresNormalizedPercents() {
		MiningScatterAsteroidModel m = new MiningScatterAsteroidModel();
		assertEquals(0, m.getProspectorGeneration());
		Map<String, Double> ui = new HashMap<>();
		ui.put("Tritium", 0.28);
		m.onProspectorLimpet(ui);
		assertEquals(1, m.getProspectorGeneration());
		String nk = GalacticAveragePrices.normalizeMaterialKey("Tritium");
		assertEquals(0.28, m.getProspectorPercentByNormalizedKey().get(nk), 1e-9);
	}

	@Test
	void updateRockSnapshotsForGather_oneSnapshotPerCommanderAndMaterial() {
		Instant t0 = Instant.parse("2026-03-31T10:00:00Z");
		Instant t1 = Instant.parse("2026-03-31T10:01:00Z");
		ProspectorLogRow tr = row(1, "A", t0, "Tritium", 10, 5, "Cmdr");
		ProspectorLogRow br = row(1, "A", t1, "Bromelite", 5, 3, "Cmdr");
		List<ProspectorLogRow> rows = List.of(tr, br);
		Set<String> keys = Set.of(
			GalacticAveragePrices.normalizeMaterialKey("Tritium"),
			GalacticAveragePrices.normalizeMaterialKey("Bromelite"));
		MiningScatterAsteroidModel m = new MiningScatterAsteroidModel();
		MiningScatterAsteroidModel.LeaderSnapshotUpdate u = m.updateRockSnapshotsForGather(rows, keys, 1);
		assertEquals(2, u.next().size());
		String trK = "Cmdr\t" + GalacticAveragePrices.normalizeMaterialKey("Tritium");
		String brK = "Cmdr\t" + GalacticAveragePrices.normalizeMaterialKey("Bromelite");
		assertTrue(u.next().containsKey(trK));
		assertTrue(u.next().containsKey(brK));
	}

	@Test
	void onProspectorLimpet_clearsLeaderSnapshots() {
		MiningScatterAsteroidModel m = new MiningScatterAsteroidModel();
		Instant t0 = Instant.parse("2026-03-31T10:00:00Z");
		Instant t1 = Instant.parse("2026-03-31T10:01:00Z");
		List<ProspectorLogRow> rows = List.of(
			row(1, "A", t0, "Tritium", 10, 5, "Cmdr"),
			row(1, "A", t1, "Tritium", 12, 6, "Cmdr"));
		MiningScatterAsteroidModel.LeaderSnapshotUpdate u1 = m.updateLeadersFromPlotRows(rows);
		assertEquals(1, u1.next().size());
		m.onProspectorLimpet(Map.of("Iron", 0.5));
		MiningScatterAsteroidModel.LeaderSnapshotUpdate u2 = m.updateLeadersFromPlotRows(rows);
		assertTrue(u2.previous().isEmpty());
	}

	@Test
	void computeRockMarkerRows_oneRowPerMaterial_latestTimestampWins() {
		Instant t0 = Instant.parse("2026-03-31T10:00:00Z");
		Instant t1 = Instant.parse("2026-03-31T10:05:00Z");
		ProspectorLogRow older = row(1, "A", t0, "Tritium", 10, 5, "Cmdr");
		ProspectorLogRow newer = row(1, "A", t1, "Tritium", 20, 8, "Cmdr");
		List<ProspectorLogRow> inSession = List.of(older, newer);
		String nk = GalacticAveragePrices.normalizeMaterialKey("Tritium");
		List<ProspectorLogRow> rocks = MiningScatterAsteroidModel.computeRockMarkerRows(inSession, Set.of(nk));
		assertEquals(1, rocks.size());
		assertEquals(newer, rocks.get(0));
	}

	@Test
	void computeRockMarkerRows_twoCommanders_twoMaterials_fourMarkers_onePerMaterialPerCommander() {
		Instant t1 = Instant.parse("2026-03-31T10:00:00Z");
		Instant t2 = Instant.parse("2026-03-31T10:01:00Z");
		Instant t3 = Instant.parse("2026-03-31T10:02:00Z");
		ProspectorLogRow tr1 = row(1, "A", t1, "Tritium", 10, 5, "Cmdr1");
		ProspectorLogRow tr2 = row(1, "A", t2, "Tritium", 10, 6, "Cmdr2");
		ProspectorLogRow br1 = row(1, "A", t2, "Bromelite", 5, 3, "Cmdr1");
		ProspectorLogRow br2 = row(1, "A", t3, "Bromelite", 5, 4, "Cmdr2");
		List<ProspectorLogRow> inSession = List.of(tr1, tr2, br1, br2);
		Set<String> keys = Set.of(
			GalacticAveragePrices.normalizeMaterialKey("Tritium"),
			GalacticAveragePrices.normalizeMaterialKey("Bromelite"));
		List<ProspectorLogRow> rocks = MiningScatterAsteroidModel.computeRockMarkerRows(inSession, keys);
		assertEquals(4, rocks.size());
		assertEquals(tr1, rocks.stream().filter(r -> "Cmdr1".equals(r.getCommanderName()) && r.getMaterial().contains("Tritium")).findFirst().orElseThrow());
		assertEquals(br1, rocks.stream().filter(r -> "Cmdr1".equals(r.getCommanderName()) && r.getMaterial().contains("Bromelite")).findFirst().orElseThrow());
		assertEquals(tr2, rocks.stream().filter(r -> "Cmdr2".equals(r.getCommanderName()) && r.getMaterial().contains("Tritium")).findFirst().orElseThrow());
		assertEquals(br2, rocks.stream().filter(r -> "Cmdr2".equals(r.getCommanderName()) && r.getMaterial().contains("Bromelite")).findFirst().orElseThrow());
	}

	@Test
	void computeRockMarkerRows_twoMaterials_twoRocks() {
		Instant t0 = Instant.parse("2026-03-31T10:00:00Z");
		Instant t1 = Instant.parse("2026-03-31T10:01:00Z");
		ProspectorLogRow tr = row(1, "A", t0, "Tritium", 10, 5, "Cmdr");
		ProspectorLogRow fe = row(1, "A", t1, "Iron", 15, 3, "Cmdr");
		List<ProspectorLogRow> inSession = List.of(tr, fe);
		Set<String> keys = Set.of(
			GalacticAveragePrices.normalizeMaterialKey("Tritium"),
			GalacticAveragePrices.normalizeMaterialKey("Iron"));
		List<ProspectorLogRow> rocks = MiningScatterAsteroidModel.computeRockMarkerRows(inSession, keys);
		assertEquals(2, rocks.size());
	}

	@Test
	void computeRockMarkerRows_ignoresOlderAsteroidWhenAnchorIsNewer() {
		Instant tOld = Instant.parse("2026-03-31T09:00:00Z");
		Instant tNew = Instant.parse("2026-03-31T11:00:00Z");
		ProspectorLogRow rockA = row(1, "A", tOld, "Tritium", 10, 5, "Cmdr");
		ProspectorLogRow rockB = row(1, "B", tNew, "Tritium", 30, 12, "Cmdr");
		List<ProspectorLogRow> inSession = List.of(rockA, rockB);
		String nk = GalacticAveragePrices.normalizeMaterialKey("Tritium");
		List<ProspectorLogRow> rocks = MiningScatterAsteroidModel.computeRockMarkerRows(inSession, Set.of(nk));
		assertEquals(1, rocks.size());
		assertEquals(rockB, rocks.get(0));
	}

	@Test
	void computeLeaderSnapshot_matchesLatestOnSameAsteroid() {
		Instant t0 = Instant.parse("2026-03-31T10:00:00Z");
		Instant t1 = Instant.parse("2026-03-31T10:02:00Z");
		List<ProspectorLogRow> rows = List.of(
			row(1, "A", t0, "Tritium", 10, 5, "Cmdr"),
			row(1, "A", t1, "Tritium", 12, 6, "Cmdr"));
		Map<String, MiningLeaderSnapshot> leaders = MiningScatterAsteroidModel.computeLeaderSnapshots(rows);
		MiningLeaderSnapshot s = leaders.get("Cmdr");
		assertEquals(12.0, s.pct(), 1e-9);
		assertEquals(6.0, s.tons(), 1e-9);
		assertEquals(t1, s.instant());
	}

	@Test
	void miningLeaderSnapshot_sameAsteroid() {
		ProspectorLogRow a = row(1, "B", Instant.now(), "X", 1, 1, "C");
		MiningLeaderSnapshot s1 = new MiningLeaderSnapshot(a);
		ProspectorLogRow b = row(1, "B", Instant.now(), "Y", 2, 2, "C");
		MiningLeaderSnapshot s2 = new MiningLeaderSnapshot(b);
		assertTrue(s1.sameAsteroid(s2));
		ProspectorLogRow c = row(1, "C", Instant.now(), "Y", 2, 2, "C");
		assertNotEquals(s1.asteroidId(), new MiningLeaderSnapshot(c).asteroidId());
	}
}
