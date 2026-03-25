package org.dce.ed.logreader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.prefs.Preferences;

import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.OverlayFrame;
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
 */
public class RescanJournalsMain {


	private static final String PREF_KEY_EXO_CREDITS_TOTAL = "exo.creditsTotal";

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
	 *  - forcedCacheFile: if provided, sets a system property that SystemCache may honor.
	 */
	public static void rescanJournals(boolean forceFull, Path forcedJournalFile, Path forcedCacheFile) throws IOException {
		Path journalDirectory = org.dce.ed.OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
		if (journalDirectory == null || !Files.isDirectory(journalDirectory)) {
			System.out.println("Journal directory not found; skipping rescan.");
			return;
		}
		EliteJournalReader reader = new EliteJournalReader(journalDirectory);

		if (forcedCacheFile != null) {
			System.setProperty(SystemCache.CACHE_PATH_PROPERTY, forcedCacheFile.toString());
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

		System.out.println("Loaded " + events.size() + " events from journal files.");

		SystemCache cache = SystemCache.getInstance();
		if (forceFull)
		{
			cache.clearAndDeleteOnDisk();
		}

		SystemState state = new SystemState();
		SystemEventProcessor processor = new SystemEventProcessor(EliteDangerousOverlay.clientKey, state);

		// Recompute exobiology expected credits total from scratch during a rescan.
		// This avoids double-counting if the overlay has already been running.
		Preferences prefs = Preferences.userNodeForPackage(OverlayFrame.class);
		long exoCreditsTotal = 0L;

		Instant newestEventTimestamp = lastImport;

		// Track latest carrier-related event so we can update session state for overlay countdown.
		EliteLogEvent latestCarrierEvent = null;

		for (EliteLogEvent event : events) {
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
				persistIfSystemIsChanging(cache, state, le.getStarSystem(), le.getSystemAddress());
			} else if (event instanceof FsdJumpEvent) {
				FsdJumpEvent je = (FsdJumpEvent) event;
				persistIfSystemIsChanging(cache, state, je.getStarSystem(), je.getSystemAddress());
			}

			processor.handleEvent(event);

			// Exobiology running total (Analyse == 3rd scan completion)
			if (event.getType() == EliteEventType.SELL_ORGANIC_DATA) {
				System.out.println("Sold " + exoCreditsTotal);
				exoCreditsTotal = 0L;
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
						System.out.println("Earned total: " + exoCreditsTotal);
					}
				}
			}
			//            persistIfStarScan(cache, state, event);
		}

		// Persist the final system (if valid)
		cache.storeSystem(state);

		// Persist recomputed exobiology expected credits total.
		prefs.putLong(PREF_KEY_EXO_CREDITS_TOTAL, exoCreditsTotal);
		System.out.println("Recomputed exobiology expected credits total (unsold): " + exoCreditsTotal + " Cr");

		// Update carrier jump state in session file so overlay restores countdown when tool was closed during jump.
		EdoSessionState sessionState = EdoSessionPersistence.load();
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
				// CarrierJump or CarrierJumpCancelled: clear countdown.
				sessionState.setCarrierJumpDepartureTime(null);
				sessionState.setCarrierJumpTargetSystem(null);
			}
			EdoSessionPersistence.save(sessionState);
		}

		if (journalDirectory != null && newestEventTimestamp != null) {
			JournalImportCursor.write(journalDirectory, newestEventTimestamp);
			System.out.println("Updated last journal import time to: " + newestEventTimestamp);
		}

		System.out.println("Rescan complete.");

		prefs.putLong(PREF_KEY_EXO_CREDITS_TOTAL, exoCreditsTotal);
		System.out.println("Recomputed exobiology expected credits total: " + exoCreditsTotal);
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
