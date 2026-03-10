package org.dce.ed;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.dce.ed.logreader.EliteLogFileLocator;

/**
 * Single place to poll Cargo.json and keep the latest parsed snapshot in memory.
 */
public class CargoMonitor {

	public static final class Snapshot {
		private final Path cargoFile;
		private final long lastModifiedMs;
		private final JsonObject cargoJson;
		private final int limpetCount;

		private Snapshot(Path cargoFile, long lastModifiedMs, JsonObject cargoJson, int limpetCount) {
			this.cargoFile = cargoFile;
			this.lastModifiedMs = lastModifiedMs;
			this.cargoJson = cargoJson;
			this.limpetCount = limpetCount;
		}

		public Path getCargoFile() {
			return cargoFile;
		}

		public long getLastModifiedMs() {
			return lastModifiedMs;
		}

		public JsonObject getCargoJson() {
			return cargoJson;
		}

		public int getLimpetCount() {
			return limpetCount;
		}
	}

	private static final CargoMonitor INSTANCE = new CargoMonitor();

	public static CargoMonitor getInstance() {
		return INSTANCE;
	}

	private final CopyOnWriteArrayList<Consumer<Snapshot>> listeners = new CopyOnWriteArrayList<>();

	private volatile Snapshot snapshot;

	private volatile Path cargoFile;
	private volatile long lastModifiedMs = -1L;

	private final ScheduledExecutorService exec;

	private CargoMonitor() {
		ThreadFactory tf = r -> {
			Thread t = new Thread(r, "EDO-CargoMonitor");
			t.setDaemon(true);
			return t;
		};
		exec = Executors.newSingleThreadScheduledExecutor(tf);
		exec.scheduleWithFixedDelay(this::pollOnce, 0, 1500, TimeUnit.MILLISECONDS);
	}

	public void addListener(Consumer<Snapshot> listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	/**
	 * Returns the latest cargo snapshot. If none is cached yet, runs a single poll
	 * so callers get data when Cargo.json is available.
	 */
	public Snapshot getSnapshot() {
		if (snapshot == null) {
			pollOnce();
		}
		return snapshot;
	}

	private void pollOnce() {
		try {
			Path journalDir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
			if (journalDir == null) {
				updateSnapshot(null, -1L, null);
				return;
			}

			if (cargoFile == null) {
				cargoFile = EliteLogFileLocator.findCargoFile(journalDir);
			}

			if (cargoFile == null || !Files.exists(cargoFile)) {
				updateSnapshot(cargoFile, -1L, null);
				return;
			}

			long modified = Files.getLastModifiedTime(cargoFile).toMillis();
			if (modified == lastModifiedMs && snapshot != null) {
				return;
			}
			lastModifiedMs = modified;

			JsonObject cargoObj = readJsonObject(cargoFile);
			updateSnapshot(cargoFile, modified, cargoObj);
		} catch (Exception e) {
			// swallow; we don't want this thread to die
		}
	}

	private void updateSnapshot(Path file, long modified, JsonObject cargoObj) {
		int limpets = getLimpetCount(cargoObj);
		Snapshot next = (cargoObj == null) ? null : new Snapshot(file, modified, cargoObj, limpets);

		Snapshot prev = this.snapshot;
		this.snapshot = next;

		// Notify on any meaningful change (file missing -> now present, modified time changed, etc).
		boolean changed = (prev == null && next != null)
				|| (prev != null && next == null)
				|| (prev != null && next != null && prev.getLastModifiedMs() != next.getLastModifiedMs());

		if (changed) {
			for (Consumer<Snapshot> c : listeners) {
				try {
					c.accept(next);
				} catch (Exception ignored) {
				}
			}
		}
	}

	/**
	 * Debug/testing hook: allow callers (e.g. MiningDebugHarness) to inject a synthetic
	 * cargo snapshot without touching the real Cargo.json on disk.
	 *
	 * This is intended for development use only.
	 */
	public void setDebugSnapshot(JsonObject cargoObj) {
		long now = System.currentTimeMillis();
		updateSnapshot(null, now, cargoObj);
	}

	private static JsonObject readJsonObject(Path file) {
		if (file == null) {
			return null;
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement el = JsonParser.parseReader(r);
			if (el != null && el.isJsonObject()) {
				return el.getAsJsonObject();
			}
			return null;
		} catch (Exception e) {
			return null;
		}
	}

	private static int getLimpetCount(JsonObject cargo) {
		if (cargo == null) {
			return 0;
		}

		JsonArray inv = null;
		if (cargo.has("Inventory") && cargo.get("Inventory").isJsonArray()) {
			inv = cargo.getAsJsonArray("Inventory");
		} else if (cargo.has("inventory") && cargo.get("inventory").isJsonArray()) {
			inv = cargo.getAsJsonArray("inventory");
		}

		if (inv == null) {
			return 0;
		}

		for (JsonElement e : inv) {
			if (e == null || !e.isJsonObject()) {
				continue;
			}

			JsonObject o = e.getAsJsonObject();

			String name = null;
			if (o.has("Name") && !o.get("Name").isJsonNull()) {
				try {
					name = o.get("Name").getAsString();
				} catch (Exception ignored) {
				}
			} else if (o.has("name") && !o.get("name").isJsonNull()) {
				try {
					name = o.get("name").getAsString();
				} catch (Exception ignored) {
				}
			}

			if (name == null || !name.equalsIgnoreCase("drones")) {
				continue;
			}

			if (o.has("Count") && !o.get("Count").isJsonNull()) {
				try {
					return (int) o.get("Count").getAsLong();
				} catch (Exception ignored) {
				}
			} else if (o.has("count") && !o.get("count").isJsonNull()) {
				try {
					return (int) o.get("count").getAsLong();
				} catch (Exception ignored) {
				}
			}

			return 0;
		}

		return 0;
	}
}
