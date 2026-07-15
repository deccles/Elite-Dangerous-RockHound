package org.dce.ed;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.session.EdoSessionPersistence;
import org.dce.ed.session.EdoSessionState;

import com.google.gson.JsonObject;

/**
 * Tracks NPC crew assignment from journal events so we can warn when a ship has fighters
 * available but no crew member is set Active in the crew lounge.
 *
 * Active crew per ship is persisted in {@link EdoSessionState} (with exobio/bounty totals) so
 * overlay restarts and same-ship {@code Loadout} events can restore assignment without re-reading
 * journals for {@code CrewAssign}.
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
	/** ShipID → Active crew name (authoritative in-memory copy of session state). */
	private final ConcurrentHashMap<Integer, String> activeCrewByShipId = new ConcurrentHashMap<>();

	private volatile String activeCrewName;
	private volatile int currentShipId = -1;
	/**
	 * After swapping ships, crew must be set Active again in the crew lounge before we trust
	 * persisted assignment or skip the fighter-pilot warning.
	 */
	private volatile boolean requiresCrewLoungeAssign;
	private volatile Runnable sessionStateChangeCallback;

	private NpcCrewTracker() {
	}

	public void addListener(Runnable listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	public void setSessionStateChangeCallback(Runnable callback) {
		this.sessionStateChangeCallback = callback;
	}

	public boolean hasActiveNpcCrew() {
		String n = activeCrewName;
		return n != null && !n.isBlank();
	}

	/** Current Active pilot name for the tracked ship, or {@code null}. */
	public String getActiveNpcCrewName() {
		String n = activeCrewName;
		return n != null && !n.isBlank() ? n : null;
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

		if (shipSwap) {
			activeCrewName = null;
			persistActiveCrewName(null);
			requiresCrewLoungeAssign = true;
		} else if (!requiresCrewLoungeAssign) {
			activeCrewName = getPersistedActiveName(shipId);
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
	 * Restore Active crew from {@link EdoSessionState} (or migrate legacy prefs), then apply the
	 * current loadout. Does not scan journals.
	 */
	public void bootstrapFromSession(LoadoutEvent loadout) {
		hiredCrew.clear();
		activeCrewName = null;
		currentShipId = -1;
		requiresCrewLoungeAssign = false;
		activeCrewByShipId.clear();

		EdoSessionState state = EdoSessionPersistence.load();
		applySessionState(state);
		if (activeCrewByShipId.isEmpty()) {
			migrateLegacyPrefsIntoSessionMap();
		}

		if (loadout != null) {
			onLoadout(loadout);
		}
		notifyListeners();
	}

	/**
	 * @deprecated Use {@link #bootstrapFromSession(LoadoutEvent)}; journal reparse is no longer needed.
	 */
	@Deprecated
	public void bootstrapFromJournal(java.nio.file.Path journalDir, LoadoutEvent loadout) {
		bootstrapFromSession(loadout);
	}

	public void fillSessionState(EdoSessionState state) {
		if (state == null) {
			return;
		}
		Map<String, String> out = new LinkedHashMap<>();
		for (Map.Entry<Integer, String> e : activeCrewByShipId.entrySet()) {
			if (e.getKey() == null || e.getValue() == null || e.getValue().isBlank()) {
				continue;
			}
			out.put(Integer.toString(e.getKey()), e.getValue().trim());
		}
		state.setNpcCrewActiveByShipId(out.isEmpty() ? null : out);
	}

	public void applySessionState(EdoSessionState state) {
		activeCrewByShipId.clear();
		if (state == null || state.getNpcCrewActiveByShipId() == null) {
			return;
		}
		for (Map.Entry<String, String> e : state.getNpcCrewActiveByShipId().entrySet()) {
			if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue().isBlank()) {
				continue;
			}
			try {
				int shipId = Integer.parseInt(e.getKey().trim());
				activeCrewByShipId.put(shipId, e.getValue().trim());
			} catch (NumberFormatException ignored) {
				// Skip corrupt keys.
			}
		}
		if (currentShipId >= 0 && !requiresCrewLoungeAssign) {
			activeCrewName = getPersistedActiveName(currentShipId);
		}
	}

	private void migrateLegacyPrefsIntoSessionMap() {
		Map<Integer, String> legacy = OverlayPreferences.exportNpcCrewActiveByShipId();
		if (legacy == null || legacy.isEmpty()) {
			return;
		}
		activeCrewByShipId.putAll(legacy);
		try {
			EdoSessionState state = EdoSessionPersistence.load();
			fillSessionState(state);
			EdoSessionPersistence.save(state);
			OverlayPreferences.clearNpcCrewActiveByShipId();
		} catch (Exception ignored) {
			// Keep prefs if session write failed so the next startup can retry migration.
		}
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
		if (name.equals(getPersistedActiveName(currentShipId))) {
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

	private String getPersistedActiveName(int shipId) {
		if (shipId < 0) {
			return null;
		}
		String n = activeCrewByShipId.get(shipId);
		return n != null && !n.isBlank() ? n : null;
	}

	private void persistActiveCrewName(String name) {
		if (currentShipId < 0) {
			return;
		}
		if (name == null || name.isBlank()) {
			activeCrewByShipId.remove(currentShipId);
		} else {
			activeCrewByShipId.put(currentShipId, name.trim());
		}
		requestSessionPersist();
	}

	private void requestSessionPersist() {
		Runnable cb = sessionStateChangeCallback;
		if (cb != null) {
			try {
				cb.run();
			} catch (Exception ignored) {
			}
		}
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

	/** Test helper: replace in-memory ship→crew map. */
	void replaceActiveCrewByShipIdForTests(Map<Integer, String> map) {
		activeCrewByShipId.clear();
		if (map != null) {
			activeCrewByShipId.putAll(map);
		}
	}

	Map<Integer, String> snapshotActiveCrewByShipIdForTests() {
		return new HashMap<>(activeCrewByShipId);
	}
}
