package org.dce.ed.logreader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.session.EdoSessionPersistence;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemEventProcessor;
import org.dce.ed.state.SystemState;
import org.dce.ed.util.FirstBonusHelper;
import org.dce.ed.util.SpanshBodyExobiologyInfo;
import org.dce.ed.util.SpanshLandmark;
import org.dce.ed.util.SpanshLandmarkCache;

/**
 * Standalone utility with a main() that scans all Elite Dangerous journal files
 * and populates the local SystemCache with every system/body it can reconstruct.
 *
 * Run this once (with the same JVM/Classpath as the overlay)
 * before starting the overlay, or periodically to refresh the local body cache.
 * <p>
 * <strong>Incremental vs full:</strong> without {@code --full}, only journal lines at/after the timestamp in
 * {@code edo-cache.lastRescanTimestamp} (next to your journals) are replayed — often seconds of work. Pass
 * {@code --full} to wipe the local SQLite cache and replay every {@code Journal.*.log} line from disk.
 */
public class RescanJournalsMain {

	/**
	 * Optional UI/CLI progress hook. {@code percent} is 0–100, or negative for indeterminate.
	 * Invoked from the rescan worker thread.
	 */
	@FunctionalInterface
	public interface RescanProgressListener {
		void onProgress(String phase, int percent, String detail);
	}

	public static void main(String[] args) throws IOException {
		System.out.println("Rescanning Elite Dangerous journals and rebuilding local system cache...");

		boolean forceFull = false;
		Path forcedJournalFile = null;
		Path forcedCacheFile = null;
		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];

				if ("--full".equalsIgnoreCase(arg)) {
					forceFull = true;
					continue;
				}

				if ("--journal".equalsIgnoreCase(arg) || "-j".equalsIgnoreCase(arg)) {
					if (i + 1 < args.length) {
						forcedJournalFile = Path.of(args[++i]).toAbsolutePath().normalize();
					}
					continue;
				}

				if ("--cache".equalsIgnoreCase(arg)
						|| "--cacheFile".equalsIgnoreCase(arg)
						|| "-c".equalsIgnoreCase(arg)) {
					if (i + 1 < args.length) {
						forcedCacheFile = Path.of(args[++i]).toAbsolutePath().normalize();
					}
				}
			}
		}

		rescanJournals(forceFull, forcedJournalFile, forcedCacheFile);
	}

	public static void rescanJournals(boolean forceFull) throws IOException {
		// Keep default GUI behavior unchanged.
		rescanJournals(forceFull, null, null);
	}

	/**
	 * Optional CLI-oriented overload:
	 *  - forcedJournalFile: if provided, rescan ONLY that file (no copying into the game journal directory).
	 *  The journal import cursor is not updated for single-file replays (avoids rewinding incremental import).
	 *  - forcedCacheFile: if provided, sets {@link SystemCache#CACHE_DB_PATH_PROPERTY} (SQLite DB path).
	 */
	public static void rescanJournals(boolean forceFull, Path forcedJournalFile, Path forcedCacheFile) throws IOException {
		rescanJournals(forceFull, forcedJournalFile, forcedCacheFile, null);
	}

	/**
	 * @param progress optional; may be called frequently from the rescan worker thread
	 */
	public static void rescanJournals(boolean forceFull, Path forcedJournalFile, Path forcedCacheFile,
			RescanProgressListener progress) throws IOException {
		Path journalDirectory;
		if (forcedJournalFile != null) {
			Path abs = forcedJournalFile.toAbsolutePath().normalize();
			if (!Files.isRegularFile(abs)) {
				System.out.println("Forced journal path is not a regular file; skipping rescan: " + abs);
				return;
			}
			Path parent = abs.getParent();
			if (parent == null || !Files.isDirectory(parent)) {
				System.out.println("Forced journal file has no valid parent directory; skipping rescan: " + abs);
				return;
			}
			journalDirectory = parent;
			forcedJournalFile = abs;
		} else {
			journalDirectory = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
			if (journalDirectory == null || !Files.isDirectory(journalDirectory)) {
				System.out.println("Journal directory not found; skipping rescan.");
				return;
			}
		}
		long rescanStartNs = System.nanoTime();
		reportProgress(progress, "Preparing", -1, null);
		EliteJournalReader reader = new EliteJournalReader(journalDirectory);
		List<Path> journalLogFiles = reader.listJournalPaths();
		System.out.println("Journal folder: " + journalDirectory);
		System.out.println("Journal.*.log files on disk: " + journalLogFiles.size());

		if (forcedCacheFile != null) {
			System.setProperty(SystemCache.CACHE_DB_PATH_PROPERTY, forcedCacheFile.toString());
		}

		Instant lastImport = null;
		if (!forceFull) {
			lastImport = JournalImportCursor.read(journalDirectory);
			if (lastImport == null) {
				System.out.println("No previous journal import timestamp found; doing full rescan.");
			} else {
				System.out.println("Last journal import time (UTC): " + lastImport);
			}
		} else {
			System.out.println("Forcing full rescan (--full). Ignoring any existing import timestamp.");
		}

		// If the import cursor advanced (live play / prior runs) but the SQLite cache was deleted, is on a new PC,
		// or was switched to a new path, incremental replay would only load events after the cursor — leaving
		// systems empty until new jumps. Replay full journal history once when we see that mismatch.
		if (forcedJournalFile == null && !forceFull && lastImport != null
				&& !SystemCache.getInstance().hasAnyCachedSystems()) {
			System.out.println(
					"Journal import cursor exists but the system cache has no systems; replaying all journals once to rebuild the cache.");
			lastImport = null;
		}

		if (forcedJournalFile != null) {
			System.out.println("Mode: SINGLE FILE — " + forcedJournalFile);
		} else if (lastImport == null) {
			System.out.println("Mode: FULL HISTORY — scanning all " + journalLogFiles.size() + " journal log file(s).");
		} else {
			Path cursorPath = JournalImportCursor.getCursorFile(journalDirectory);
			System.out.println("Mode: INCREMENTAL — replaying events at/after " + lastImport + " UTC only.");
			System.out.println("        For a full replay of every journal line, run with --full or delete:");
			System.out.println("        " + cursorPath);
		}

		reportProgress(progress, "Reading journals", -1,
				journalLogFiles.size() + " log file" + (journalLogFiles.size() == 1 ? "" : "s"));
		long loadStartNs = System.nanoTime();
		List<EliteLogEvent> events;
		if (forcedJournalFile != null) {
			// We intentionally do NOT stage/copy anything into the live journal directory
			// (EDMC watches that directory and will ingest anything we drop there).
			//
			// Add a tiny helper method to EliteJournalReader:
			//   List<EliteLogEvent> readEventsFromJournalFile(Path journalFile)
			// which reads/parses exactly like your normal directory scan.
			events = reader.readEventsFromJournalFile(forcedJournalFile);
		} else if (lastImport == null) {
			events = reader.readEventsFromLastNJournalFiles(Integer.MAX_VALUE);
		} else {
			events = reader.readEventsSince(lastImport);
		}

		double loadSeconds = (System.nanoTime() - loadStartNs) / 1_000_000_000.0;
		System.out.printf(Locale.US, "Journal read + parse: %.2f s — %d parsed event(s).%n", loadSeconds, events.size());
		reportProgress(progress, "Journals parsed", 0, events.size() + " event" + (events.size() == 1 ? "" : "s"));

		SystemCache cache = SystemCache.getInstance();
		if (forceFull)
		{
			reportProgress(progress, "Clearing cache", -1, null);
			cache.clearAndDeleteOnDisk();
		}

		SystemState state = new SystemState();
		SystemEventProcessor processor = new SystemEventProcessor(EliteDangerousOverlay.clientKey, state);

		// Exobiology running total (expected credits, unsold): seed from commander session blob.
		Long cachedTotal = null;
		try {
			cachedTotal = SystemCache.getInstance().getPersistedExobiologyCreditsTotalUnsold();
		} catch (Exception ignored) {
			// fall through to 0
		}
		long exoCreditsTotal = cachedTotal != null ? cachedTotal.longValue() : 0L;
		state.setExobiologyCreditsTotalUnsold(exoCreditsTotal);

		Instant newestEventTimestamp = lastImport;
		Instant latestTransitionTs = null;
		String latestTransitionType = null;
		String latestTransitionSystem = null;
		long latestTransitionAddress = 0L;

		// Track latest carrier-related event so we can update session state for overlay countdown.
		EliteLogEvent latestCarrierEvent = null;

		String prevBulkCacheWrite = System.getProperty(SystemCache.CACHE_BULK_SYSTEM_WRITE_PROPERTY);
		final int eventCount = events.size();
		int lastReportedPercent = -1;
		try {
			System.setProperty(SystemCache.CACHE_BULK_SYSTEM_WRITE_PROPERTY, "true");
			for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
			EliteLogEvent event = events.get(eventIndex);
			if (eventCount > 0) {
				int pct = (int) ((eventIndex + 1L) * 100L / eventCount);
				if (pct != lastReportedPercent && (pct == 100 || pct % 2 == 0 || eventIndex == 0)) {
					lastReportedPercent = pct;
					reportProgress(progress, "Rebuilding cache", pct,
							(eventIndex + 1) + " / " + eventCount + " events");
				}
			}
			Instant ts = event.getTimestamp();
			if (ts != null && (newestEventTimestamp == null || ts.isAfter(newestEventTimestamp))) {
				newestEventTimestamp = ts;
			}

			// Carrier jump: countdown request, jump happened, or cancelled.
			if (event instanceof CarrierJumpRequestEvent
					|| event.getType() == EliteEventType.CARRIER_JUMP
					|| event.getType() == EliteEventType.CARRIER_JUMP_CANCELLED) {
				if (ts != null && (latestCarrierEvent == null || ts.isAfter(latestCarrierEvent.getTimestamp()))) {
					latestCarrierEvent = event;
				}
			}

			// IMPORTANT:
				// SystemEventProcessor.enterSystem(...) clears bodies when we jump/relocate to a new system.
			// To avoid losing the previous system's accumulated state, persist BEFORE processing the
			// Location/FSDJump that causes the reset.
			if (event instanceof LocationEvent) {
				LocationEvent le = (LocationEvent) event;
				if (ts != null && (latestTransitionTs == null || ts.isAfter(latestTransitionTs))) {
					latestTransitionTs = ts;
					latestTransitionType = "Location";
					latestTransitionSystem = le.getStarSystem();
					latestTransitionAddress = le.getSystemAddress();
				}
				persistIfSystemIsChanging(cache, state, le.getStarSystem(), le.getSystemAddress());
			} else if (event instanceof FsdJumpEvent) {
				FsdJumpEvent je = (FsdJumpEvent) event;
				if (ts != null && (latestTransitionTs == null || ts.isAfter(latestTransitionTs))) {
					latestTransitionTs = ts;
					latestTransitionType = "FSDJump";
					latestTransitionSystem = je.getStarSystem();
					latestTransitionAddress = je.getSystemAddress();
				}
				persistIfSystemIsChanging(cache, state, je.getStarSystem(), je.getSystemAddress());
			}

			processor.handleEvent(event);

			// Exobiology running total (Analyse == 3rd scan completion)
			if (event.getType() == EliteEventType.SELL_ORGANIC_DATA) {
				System.out.println("Sold " + exoCreditsTotal);
				exoCreditsTotal = 0L;
				state.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
			}

			if (event instanceof ScanOrganicEvent) {
				ScanOrganicEvent so = (ScanOrganicEvent) event;
				if (so.getScanType() != null && "Analyse".equalsIgnoreCase(so.getScanType().trim())) {
					boolean firstBonus = true;
					BodyInfo body = state.getBodies().get(so.getBodyId());
					if (body != null) {
						if (!Boolean.TRUE.equals(body.getWasFootfalled()) && body.getSpanshLandmarks() == null) {
							SpanshBodyExobiologyInfo info = SpanshLandmarkCache.getInstance().getOrFetch(body.getStarSystem(), body.getBodyName());
							if (info != null) {
								body.setSpanshLandmarks(info.getLandmarks());
								body.setSpanshExcludeFromExobiology(info.isExcludeFromExobiology());
							}
						}
						firstBonus = FirstBonusHelper.firstBonusApplies(body);
					}

					Long payout = ExobiologyData.estimatePayout(
							so.getGenusLocalised(),
							so.getSpeciesLocalised(),
							firstBonus);
					if (payout != null && payout.longValue() > 0L) {
						exoCreditsTotal += payout.longValue();
						state.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
						System.out.println("Earned total: " + exoCreditsTotal);
					}
				}
			}
			//            persistIfStarScan(cache, state, event);
		}
		} finally {
			if (prevBulkCacheWrite != null) {
				System.setProperty(SystemCache.CACHE_BULK_SYSTEM_WRITE_PROPERTY, prevBulkCacheWrite);
			} else {
				System.clearProperty(SystemCache.CACHE_BULK_SYSTEM_WRITE_PROPERTY);
			}
			// Replay may throw mid-loop; post-try code then never runs. Still merge carrier/exo into session_json.
			try {
				state.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
				cache.mergeCommanderSessionFromReplayedState(state);
			} catch (Exception ex) {
				System.err.println("RescanJournalsMain: post-replay session merge (finally) failed: " + ex.getMessage());
			}
		}

		// Persist exobiology expected credits total (unsold) for toolbar + future rescans.
		state.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
		if (state.getSystemName() != null && state.getSystemAddress() != 0L) {
			// Persist together with the final system (best-effort).
			cache.storeSystem(state);
		} else {
			// If we never built a valid system snapshot, update the cached last-system instead.
			try {
				CachedSystem last = SystemCache.load();
				if (last != null) {
					SystemState tmp = new SystemState();
					cache.loadInto(tmp, last);
					tmp.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
					cache.storeSystem(tmp);
				}
			} catch (Exception ignored) {
				// Fallback to preferences below.
			}
		}

		System.out.println("Exobiology expected credits total (unsold): " + exoCreditsTotal + " Cr");

		// Persist exobiology total + carrier countdown into the same SQLite session blob as the overlay.
		EdoSessionState sessionState = EdoSessionPersistence.load();
		sessionState.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
		Integer replayParkedBody = state.getCarrierParkedBodyId();
		long replayParkedSys = state.getCarrierParkedSystemAddress();
		if (replayParkedBody != null && replayParkedBody.intValue() > 0) {
			sessionState.setCarrierParkedBodyId(replayParkedBody);
			sessionState.setCarrierParkedSystemAddress(replayParkedSys != 0L ? Long.valueOf(replayParkedSys) : null);
		}
		if (latestCarrierEvent != null) {
			if (latestCarrierEvent instanceof CarrierJumpRequestEvent) {
				CarrierJumpRequestEvent req = (CarrierJumpRequestEvent) latestCarrierEvent;
				Instant dep = req.getDepartureTime();
				if (dep != null && dep.isAfter(Instant.now())) {
					sessionState.setCarrierJumpDepartureTime(dep.toString());
					sessionState.setCarrierJumpTargetSystem(req.getSystemName());
				} else {
					sessionState.setCarrierJumpDepartureTime(null);
					sessionState.setCarrierJumpTargetSystem(null);
				}
			} else {
				sessionState.setCarrierJumpDepartureTime(null);
				sessionState.setCarrierJumpTargetSystem(null);
			}
		}
		EdoSessionPersistence.save(sessionState);
		reportProgress(progress, "Finishing", 100, null);

		if (forcedJournalFile == null && journalDirectory != null && newestEventTimestamp != null) {
			JournalImportCursor.write(journalDirectory, newestEventTimestamp);
			System.out.println("Updated last journal import time to: " + newestEventTimestamp);
		} else if (forcedJournalFile != null) {
			System.out.println("Single-file rescan: left journal import cursor unchanged.");
		}

		double totalSeconds = (System.nanoTime() - rescanStartNs) / 1_000_000_000.0;
		System.out.printf(Locale.US, "Total rescan wall time: %.2f s%n", totalSeconds);

		System.out.println("Rescan complete. Exobiology expected credits total (unsold): " + exoCreditsTotal);
	}

	private static void reportProgress(RescanProgressListener progress, String phase, int percent, String detail) {
		if (progress != null) {
			progress.onProgress(phase, percent, detail);
		}
	}

	private static void persistIfSystemIsChanging(SystemCache cache, SystemState state, String nextName, long nextAddr) {
		String curName = state.getSystemName();
		long curAddr = state.getSystemAddress();

		boolean sameName = nextName != null && nextName.equals(curName);
		boolean sameAddr = nextAddr != 0L && nextAddr == curAddr;

		// Only treat it as "same system" if BOTH match (when available).
		if (sameName && sameAddr) {
			return;
		}

		cache.storeSystem(state);
	}
	private static void persistIfStarScan(SystemCache cache, SystemState state, EliteLogEvent event) {
		if (!(event instanceof ScanEvent)) {
			return;
		}

		ScanEvent se = (ScanEvent) event;

		// Star scans have StarType and distance 0; BodyID 0 is common but not required.
		String st = se.getStarType();
		if (st == null || st.isEmpty()) {
			return;
		}

		cache.storeSystem(state);
	}

}
