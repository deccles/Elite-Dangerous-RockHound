package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class NpcCrewTrackerTest {

	@BeforeEach
	void resetTracker() {
		OverlayPreferences.setNpcCrewActiveName(1, null);
		OverlayPreferences.setNpcCrewActiveName(2, null);
		NpcCrewTracker.getInstance().onLoadout(null);
	}

	@Test
	void warnsWhenFighterHangarStockedButNoActiveCrew() {
		LoadoutEvent loadout = loadoutWithFighterHangar(1, 2);
		assertTrue(NpcCrewTracker.shouldShowNoFighterPilotWarning(true, loadout));
	}

	@Test
	void noWarningWhenCrewActive() {
		LoadoutEvent loadout = loadoutWithFighterHangar(1, 1);
		NpcCrewTracker.getInstance().onLoadout(loadout);
		applyCrewEvent("{\"event\":\"CrewAssign\",\"Name\":\"Dannie Koller\",\"Role\":\"Active\"}");
		assertFalse(NpcCrewTracker.shouldShowNoFighterPilotWarning(true, loadout));
	}

	@Test
	void noWarningWhenUndocked() {
		LoadoutEvent loadout = loadoutWithFighterHangar(1, 1);
		assertFalse(NpcCrewTracker.shouldShowNoFighterPilotWarning(false, loadout));
	}

	@Test
	void noWarningWhenNoFightersStocked() {
		LoadoutEvent loadout = loadoutWithFighterHangar(1, 0);
		assertFalse(NpcCrewTracker.shouldShowNoFighterPilotWarning(true, loadout));
	}

	@Test
	void shipSwapClearsActiveCrewEvenWhenPersisted() {
		LoadoutEvent shipOne = loadoutWithFighterHangar(1, 1);
		NpcCrewTracker.getInstance().onLoadout(shipOne);
		applyCrewEvent("{\"event\":\"CrewAssign\",\"Name\":\"Dannie Koller\",\"Role\":\"Active\"}");
		assertTrue(NpcCrewTracker.getInstance().hasActiveNpcCrew());

		LoadoutEvent shipTwo = loadoutWithFighterHangar(2, 1);
		NpcCrewTracker.getInstance().onLoadout(shipTwo);
		assertFalse(NpcCrewTracker.getInstance().hasActiveNpcCrew());
	}

	@Test
	void sameShipLoadoutRestoresPersistedActiveCrew() {
		OverlayPreferences.setNpcCrewActiveName(1, "Dannie Koller");
		LoadoutEvent loadout = loadoutWithFighterHangar(1, 1);
		NpcCrewTracker.getInstance().onLoadout(loadout);
		assertTrue(NpcCrewTracker.getInstance().hasActiveNpcCrew());
		assertFalse(NpcCrewTracker.shouldShowNoFighterPilotWarning(true, loadout));
	}

	@Test
	void detectsFighterBayModuleNaming() {
		LoadoutEvent loadout = loadoutWithFighterBayItem(1, "int_fighterbay_size6_class1", 1);
		assertTrue(NpcCrewTracker.hasFighterHangar(loadout));
		assertTrue(NpcCrewTracker.shouldShowNoFighterPilotWarning(true, loadout));
	}

	@Test
	void swapBackToFighterWarnsDespitePreviouslyPersistedPilot() {
		LoadoutEvent fighter = loadoutWithFighterHangar(1, 2);
		NpcCrewTracker.getInstance().onLoadout(fighter);
		applyCrewEvent("{\"event\":\"CrewAssign\",\"Name\":\"Dannie Koller\",\"Role\":\"Active\"}");
		assertFalse(NpcCrewTracker.shouldShowNoFighterPilotWarning(true, fighter));

		LoadoutEvent mandalay = loadoutWithoutFighterHangar(2);
		NpcCrewTracker.getInstance().onLoadout(mandalay);
		assertFalse(NpcCrewTracker.getInstance().hasActiveNpcCrew());

		NpcCrewTracker.getInstance().onLoadout(fighter);
		assertFalse(NpcCrewTracker.getInstance().hasActiveNpcCrew());
		assertTrue(NpcCrewTracker.shouldShowNoFighterPilotWarning(true, fighter));
	}

	@Test
	void secondSameShipLoadoutAfterSwapDoesNotRestoreStalePilot() {
		OverlayPreferences.setNpcCrewActiveName(1, "Dannie Koller");
		LoadoutEvent fighter = loadoutWithFighterHangar(1, 2);
		LoadoutEvent mandalay = loadoutWithoutFighterHangar(2);

		NpcCrewTracker tracker = NpcCrewTracker.getInstance();
		tracker.onLoadout(fighter);
		tracker.onLoadout(mandalay);
		tracker.onLoadout(fighter);
		tracker.onLoadout(fighter);

		assertFalse(tracker.hasActiveNpcCrew());
		assertTrue(NpcCrewTracker.shouldShowNoFighterPilotWarning(true, fighter));
	}

	@Test
	void crewAssignOnShoreLeaveClearsActiveAndPersistence() {
		OverlayPreferences.setNpcCrewActiveName(1, "Roscoe Francis");
		LoadoutEvent loadout = loadoutWithFighterHangar(1, 1);
		NpcCrewTracker.getInstance().onLoadout(loadout);
		assertTrue(NpcCrewTracker.getInstance().hasActiveNpcCrew());

		applyCrewEvent("{\"event\":\"CrewAssign\",\"Name\":\"Roscoe Francis\",\"Role\":\"OnShoreLeave\"}");

		assertFalse(NpcCrewTracker.getInstance().hasActiveNpcCrew());
		assertTrue(NpcCrewTracker.shouldShowNoFighterPilotWarning(true, loadout));
		assertNull(OverlayPreferences.getNpcCrewActiveName(1));
	}

	@Test
	void crewAssignInactiveClearsPersistence() {
		LoadoutEvent loadout = loadoutWithFighterHangar(1, 1);
		NpcCrewTracker.getInstance().onLoadout(loadout);
		applyCrewEvent("{\"event\":\"CrewAssign\",\"Name\":\"Dannie Koller\",\"Role\":\"Active\"}");
		applyCrewEvent("{\"event\":\"CrewAssign\",\"Name\":\"Dannie Koller\",\"Role\":\"Inactive\"}");

		assertFalse(NpcCrewTracker.getInstance().hasActiveNpcCrew());
		assertTrue(NpcCrewTracker.shouldShowNoFighterPilotWarning(true, loadout));

		NpcCrewTracker.getInstance().onLoadout(loadout);
		assertFalse(NpcCrewTracker.getInstance().hasActiveNpcCrew());
	}

	private static void applyCrewEvent(String json) {
		JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
		EliteLogEvent event = new EliteLogEvent.GenericEvent(Instant.now(), EliteEventType.UNKNOWN, obj);
		NpcCrewTracker.getInstance().applyJournalEvent(event);
	}

	private static LoadoutEvent loadoutWithoutFighterHangar(int shipId) {
		JsonObject raw = new JsonObject();
		raw.addProperty("event", "Loadout");
		raw.addProperty("Ship", "mandalay");
		raw.addProperty("ShipID", shipId);
		raw.addProperty("CargoCapacity", 16);
		raw.add("Modules", JsonParser.parseString("[]").getAsJsonArray());

		return new LoadoutEvent(
				Instant.parse("2025-01-01T00:00:00Z"),
				raw,
				"mandalay",
				shipId,
				"",
				"XX-02A",
				0L,
				0L,
				1.0,
				200.0,
				16,
				30.0,
				null,
				0L,
				List.of());
	}

	private static LoadoutEvent loadoutWithFighterBayItem(int shipId, String itemName, int ammoInClip) {
		JsonObject bay = new JsonObject();
		bay.addProperty("Slot", "Slot02_Size6");
		bay.addProperty("Item", itemName);
		bay.addProperty("On", true);
		bay.addProperty("Priority", 0);
		bay.addProperty("Health", 1.0);
		bay.addProperty("Value", 1000);
		if (ammoInClip >= 0) {
			bay.addProperty("AmmoInClip", ammoInClip);
		}

		JsonObject raw = new JsonObject();
		raw.addProperty("event", "Loadout");
		raw.addProperty("Ship", "krait_mkii");
		raw.addProperty("ShipID", shipId);
		raw.addProperty("CargoCapacity", 16);
		raw.add("Modules", JsonParser.parseString("[" + bay + "]").getAsJsonArray());

		LoadoutEvent.Module module = new LoadoutEvent.Module(
				"Slot02_Size6",
				itemName,
				true,
				0,
				1.0,
				1000L,
				ammoInClip >= 0 ? ammoInClip : null,
				null,
				null,
				bay);

		return new LoadoutEvent(
				Instant.parse("2025-01-01T00:00:00Z"),
				raw,
				"krait_mkii",
				shipId,
				"",
				"XX-01A",
				0L,
				0L,
				1.0,
				200.0,
				16,
				30.0,
				null,
				0L,
				List.of(module));
	}

	private static LoadoutEvent loadoutWithFighterHangar(int shipId, int ammoInClip) {
		JsonObject hangar = new JsonObject();
		hangar.addProperty("Slot", "Slot07_Size7");
		hangar.addProperty("Item", "int_fighterhangar_size2_class1");
		hangar.addProperty("On", true);
		hangar.addProperty("Priority", 0);
		hangar.addProperty("Health", 1.0);
		hangar.addProperty("Value", 1000);
		if (ammoInClip >= 0) {
			hangar.addProperty("AmmoInClip", ammoInClip);
		}

		JsonObject raw = new JsonObject();
		raw.addProperty("event", "Loadout");
		raw.addProperty("Ship", "mamba");
		raw.addProperty("ShipID", shipId);
		raw.addProperty("CargoCapacity", 16);
		raw.add("Modules", JsonParser.parseString("[" + hangar + "]").getAsJsonArray());

		LoadoutEvent.Module module = new LoadoutEvent.Module(
				"Slot07_Size7",
				"int_fighterhangar_size2_class1",
				true,
				0,
				1.0,
				1000L,
				ammoInClip >= 0 ? ammoInClip : null,
				null,
				null,
				hangar);

		return new LoadoutEvent(
				Instant.parse("2025-01-01T00:00:00Z"),
				raw,
				"mamba",
				shipId,
				"",
				"XX-01A",
				0L,
				0L,
				1.0,
				200.0,
				16,
				30.0,
				null,
				0L,
				List.of(module));
	}
}
