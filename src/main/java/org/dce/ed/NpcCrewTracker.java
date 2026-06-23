package org.dce.ed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.LoadoutEvent;

import com.google.gson.JsonObject;

/**
 * Tracks NPC crew assignment from journal events so we can warn when a ship has fighters
 * available but no crew member is set Active in the crew lounge.
 *
 * Active crew per ship is also persisted in preferences so overlay restarts and same-ship
 * {@code Loadout} events (e.g. game startup) can restore assignment without a new {@code CrewAssign}.
 */
public final class NpcCrewTracker {

	public static final String FIGHTER_PILOT_REMINDER_SPEECH =
			"Did you forget your fighter pilot again, commander?";

	public static final String FIGHTER_PILOT_STATUS_WARNING = "No assigned fighter pilot!";

	private static final NpcCrewTracker INSTANCE = new NpcCrewTracker();

	public static NpcCrewTracker getInstance() {
		return INSTANCE;
	}

	private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
	private final Set<String> hiredCrew = new HashSet<>();

	private volatile String activeCrewName;
	private volatile int currentShipId = -1;
	private volatile Instant lastLoadoutTimestamp;
	/**
	 * After swapping ships, crew must be set Active again in the crew lounge before we trust
	 * persisted assignment or skip the fighter-pilot warning.
	 */
	private volatile boolean requiresCrewLoungeAssign;

	private NpcCrewTracker() {
	}

	public void addListener(Runnable listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	public boolean hasActiveNpcCrew() {
		String n = activeCrewName;
		return n != null && !n.isBlank();
	}

	public void onLoadout(LoadoutEvent loadout) {
		if (loadout == null) {
			activeCrewName = null;
			currentShipId = -1;
			requiresCrewLoungeAssign = false;
			notifyListeners();
			return;
		}

		int shipId = loadout.getShipId();
		boolean shipSwap = currentShipId >= 0 && currentShipId != shipId;
		currentShipId = shipId;
		lastLoadoutTimestamp = loadout.getTimestamp();

		if (shipSwap) {
			activeCrewName = null;
			OverlayPreferences.setNpcCrewActiveName(shipId, null);
			requiresCrewLoungeAssign = true;
		} else if (!requiresCrewLoungeAssign) {
			activeCrewName = OverlayPreferences.getNpcCrewActiveName(shipId);
		}

		if (!hasFighterHangar(loadout)) {
			activeCrewName = null;
			requiresCrewLoungeAssign = true;
		}
		notifyListeners();
	}

	public void applyJournalEvent(EliteLogEvent event) {
		if (event == null) {
			return;
		}
		JsonObject raw = event.getRawJson();
		if (raw == null || !raw.has("event")) {
			return;
		}
		String eventName = raw.get("event").getAsString();
		if (eventName == null || eventName.isBlank()) {
			return;
		}

		boolean changed = false;
		switch (eventName) {
			case "CrewAssign" -> changed = onCrewAssign(raw);
			case "CrewHire" -> changed = onCrewHire(raw);
			case "CrewFire" -> changed = onCrewFire(raw);
			default -> {
				return;
			}
		}
		if (changed) {
			notifyListeners();
		}
	}

	/**
	 * Rebuild crew state from journal events recorded after the most recent {@code Loadout},
	 * then fall back to persisted per-ship assignment when the journal is silent.
	 */
	public void bootstrapFromJournal(Path journalDir, LoadoutEvent loadout) {
		hiredCrew.clear();
		activeCrewName = null;
		currentShipId = -1;
		requiresCrewLoungeAssign = false;
		lastLoadoutTimestamp = loadout != null ? loadout.getTimestamp() : null;
		if (loadout != null) {
			currentShipId = loadout.getShipId();
		}

		if (journalDir != null && Files.isDirectory(journalDir)) {
			try {
				EliteJournalReader reader = new EliteJournalReader(journalDir);
				List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(8);
				int start = 0;
				if (loadout != null && loadout.getTimestamp() != null) {
					Instant loadoutTs = loadout.getTimestamp();
					for (int i = events.size() - 1; i >= 0; i--) {
						EliteLogEvent e = events.get(i);
						if (e instanceof LoadoutEvent lo && loadoutTs.equals(lo.getTimestamp())) {
							start = i + 1;
							break;
						}
					}
				}
				for (int i = start; i < events.size(); i++) {
					applyJournalEvent(events.get(i));
				}
			} catch (IOException ignored) {
				// Keep best-effort in-memory state.
			}
		}

		if (loadout != null && !hasActiveNpcCrew()) {
			activeCrewName = OverlayPreferences.getNpcCrewActiveName(currentShipId);
		}
		notifyListeners();
	}

	private static boolean isFighterBayModuleItem(String item) {
		if (item == null || item.isBlank()) {
			return false;
		}
		String norm = item.toLowerCase(Locale.US);
		return norm.contains("fighterhangar") || norm.contains("fighterbay");
	}

	public static boolean hasFighterHangar(LoadoutEvent loadout) {
		if (loadout == null) {
			return false;
		}
		List<LoadoutEvent.Module> modules = loadout.getModules();
		if (modules == null || modules.isEmpty()) {
			return false;
		}
		for (LoadoutEvent.Module m : modules) {
			if (m == null) {
				continue;
			}
			if (isFighterBayModuleItem(m.getItem())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True when the loadout reports at least one fighter ready in a hangar bay.
	 * {@code AmmoInClip} on fighter hangar modules tracks stocked SLFs; when omitted we assume fighters exist.
	 */
	public static boolean hasDockedFighters(LoadoutEvent loadout) {
		if (loadout == null) {
			return false;
		}
		List<LoadoutEvent.Module> modules = loadout.getModules();
		if (modules == null || modules.isEmpty()) {
			return false;
		}

		boolean sawHangar = false;
		boolean sawAmmoField = false;
		int totalStock = 0;

		for (LoadoutEvent.Module m : modules) {
			if (m == null) {
				continue;
			}
			String item = m.getItem();
			if (!isFighterBayModuleItem(item)) {
				continue;
			}
			sawHangar = true;
			Integer clip = m.getAmmoInClip();
			Integer hopper = m.getAmmoInHopper();
			if (clip != null) {
				sawAmmoField = true;
				totalStock += Math.max(0, clip);
			}
			if (hopper != null) {
				sawAmmoField = true;
				totalStock += Math.max(0, hopper);
			}
		}

		if (!sawHangar) {
			return false;
		}
		if (!sawAmmoField) {
			return true;
		}
		return totalStock > 0;
	}

	public static boolean shouldShowNoFighterPilotWarning(boolean docked, LoadoutEvent loadout) {
		if (!docked) {
			return false;
		}
		if (!OverlayPreferences.isFighterPilotReminderEnabled()) {
			return false;
		}
		if (!hasFighterHangar(loadout)) {
			return false;
		}
		if (!hasDockedFighters(loadout)) {
			return false;
		}
		return !getInstance().hasActiveNpcCrew();
	}

	private boolean onCrewAssign(JsonObject raw) {
		requiresCrewLoungeAssign = false;
		String name = getString(raw, "Name");
		String role = getString(raw, "Role");
		if (role == null) {
			return false;
		}
		if ("Active".equalsIgnoreCase(role)) {
			if (name == null || name.isBlank()) {
				return false;
			}
			activeCrewName = name;
			hiredCrew.add(name);
			persistActiveCrewName(name);
			return true;
		}
		// Inactive, OnShoreLeave, and any other non-Active role mean the pilot is not assigned.
		if (name == null || name.isBlank()) {
			return false;
		}
		boolean changed = false;
		if (name.equals(activeCrewName)) {
			activeCrewName = null;
			changed = true;
		}
		if (name.equals(OverlayPreferences.getNpcCrewActiveName(currentShipId))) {
			persistActiveCrewName(null);
			changed = true;
		}
		return changed;
	}

	private boolean onCrewHire(JsonObject raw) {
		String name = getString(raw, "Name");
		if (name == null || name.isBlank()) {
			return false;
		}
		return hiredCrew.add(name);
	}

	private boolean onCrewFire(JsonObject raw) {
		String name = getString(raw, "Name");
		if (name == null || name.isBlank()) {
			return false;
		}
		boolean changed = hiredCrew.remove(name);
		if (name.equals(activeCrewName)) {
			activeCrewName = null;
			persistActiveCrewName(null);
			changed = true;
		}
		return changed;
	}

	private void persistActiveCrewName(String name) {
		if (currentShipId < 0) {
			return;
		}
		OverlayPreferences.setNpcCrewActiveName(currentShipId, name);
	}

	private static String getString(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return null;
		}
		try {
			return obj.get(key).getAsString();
		} catch (Exception e) {
			return null;
		}
	}

	private void notifyListeners() {
		for (Runnable r : listeners) {
			try {
				r.run();
			} catch (Exception ignored) {
			}
		}
	}
}
