package org.dce.ed.logreader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.exobiology.ExobiologyData;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.CargoDepotEvent;
import org.dce.ed.logreader.event.MissionAbandonedEvent;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.dce.ed.logreader.event.MissionCompletedEvent;
import org.dce.ed.logreader.event.MissionFailedEvent;
import org.dce.ed.logreader.event.MissionRedirectedEvent;
import org.dce.ed.logreader.event.MissionsEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.logreader.event.SupercruiseExitEvent;
import org.dce.ed.mission.MissionTracker;
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

	/** Console/UI replay progress: at most about one line every 2s or 10k events. */
	private static final int REPLAY_PROGRESS_EVERY_EVENTS = 10_000;
	private static final long REPLAY_PROGRESS_INTERVAL_NS = 2_000_000_000L;

	/**
	 * Journal {@code event} names required for system-cache rebuild + mission/exo/carrier session
	 * fields during {@link #rescanJournals}. Other lines are skipped before Gson parse.
	 */
	private static final Set<String> RESCAN_INCLUDE_EVENT_NAMES = Set.of(
			"Location",
			"FSDJump",
			"CarrierJump",
			"CarrierLocation",
			"CarrierJumpRequest",
			"CarrierJumpCancelled",
			"Docked",
			"Undocked",
			"SupercruiseExit",
			"FSSDiscoveryScan",
			"Scan",
			"ScanBaryCentre",
			"SAASignalsFound",
			"FSSBodySignals",
			"FSSAllBodiesFound",
			"ScanOrganic",
			"SellOrganicData",
			"MissionAccepted",
			"MissionCompleted",
			"MissionFailed",
			"MissionAbandoned",
			"MissionRedirected",
			"CargoDepot",
			"Missions",
			// Massacre kill rebuild during full history (system-gated with DestinationSystem).
			"Bounty"
	);

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
		System.out.println("Reading and parsing journal log files (this may take a while)...");
		System.out.flush();
		long loadStartNs = System.nanoTime();
		List<EliteLogEvent> events;
		if (forcedJournalFile != null) {
			// We intentionally do NOT stage/copy anything into the live journal directory
			// (EDMC watches that directory and will ingest anything we drop there).
			events = reader.readEventsFromJournalFile(forcedJournalFile, RESCAN_INCLUDE_EVENT_NAMES);
		} else if (lastImport == null) {
			events = reader.readEventsFromLastNJournalFiles(Integer.MAX_VALUE, RESCAN_INCLUDE_EVENT_NAMES);
		} else {
			events = reader.readEventsSince(lastImport, RESCAN_INCLUDE_EVENT_NAMES);
		}

		double loadSeconds = (System.nanoTime() - loadStartNs) / 1_000_000_000.0;
		System.out.printf(Locale.US, "Journal read + parse: %.2f s — %d parsed event(s).%n", loadSeconds, events.size());
		reportProgress(progress, "Journals parsed", 0, events.size() + " event" + (events.size() == 1 ? "" : "s"));

		SystemCache cache = SystemCache.getInstance();
		if (forceFull)
		{
			System.out.println("Clearing local system cache database...");
			System.out.flush();
			reportProgress(progress, "Clearing cache", -1, null);
			cache.clearAndDeleteOnDiskPreservingSession();
			System.out.println("Cache cleared; rebuilding from journal events...");
			System.out.flush();
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
		CarrierJumpRequestEvent openCarrierJumpRequest = null;
		Instant carrierJumpCompletionTime = null;
		boolean carrierJumpCompletionOffCarrier = false;

		MissionTracker missionReplayTracker = new MissionTracker();
		EdoSessionState missionReplaySeed = EdoSessionPersistence.load();
		missionReplayTracker.applySessionState(missionReplaySeed);
		/*
		 * Full-history replay can rebuild massacre progress from Bounty + DestinationSystem.
		 * Incremental windows must not: resetting would drop prior kills, and applying without
		 * reset would double-count kills already stored in session.
		 */
		final boolean rebuildMassacreFromBounties = (lastImport == null);
		final String[] missionReplaySystem = { null };
		if (rebuildMassacreFromBounties) {
			missionReplayTracker.resetEstimatedMassacreProgress();
			missionReplayTracker.setCurrentSystemSupplier(() -> missionReplaySystem[0]);
		}

		String prevBulkCacheWrite = System.getProperty(SystemCache.CACHE_BULK_SYSTEM_WRITE_PROPERTY);
		final int eventCount = events.size();
		int[] lastReportedPercent = { -1 };
		int systemsStored = 0;
		long replayStartNs = System.nanoTime();
		long lastReplayProgressNs = replayStartNs;
		if (eventCount > 0) {
			System.out.println("Replaying " + eventCount + " event(s) into system cache...");
			System.out.flush();
		}
		try {
			System.setProperty(SystemCache.CACHE_BULK_SYSTEM_WRITE_PROPERTY, "true");
			cache.beginBulkSqliteTransaction();
			for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
			EliteLogEvent event = events.get(eventIndex);
			lastReplayProgressNs = maybeReportReplayProgress(progress, eventIndex, eventCount, systemsStored,
					replayStartNs, lastReplayProgressNs, lastReportedPercent);
			Instant ts = event.getTimestamp();
			if (ts != null && (newestEventTimestamp == null || ts.isAfter(newestEventTimestamp))) {
				newestEventTimestamp = ts;
			}

			// Carrier jump: countdown request, jump completion, or cancelled.
			if (event instanceof CarrierJumpRequestEvent req) {
				openCarrierJumpRequest = req;
				if (ts != null && (latestCarrierEvent == null || ts.isAfter(latestCarrierEvent.getTimestamp()))) {
					latestCarrierEvent = event;
				}
			} else if (event.getType() == EliteEventType.CARRIER_JUMP_CANCELLED) {
				openCarrierJumpRequest = null;
				// Cancel also starts the post-jump cooldown window.
				if (ts != null) {
					carrierJumpCompletionTime = ts;
					carrierJumpCompletionOffCarrier = true;
					if (latestCarrierEvent == null || ts.isAfter(latestCarrierEvent.getTimestamp())) {
						latestCarrierEvent = event;
					}
				}
			} else if (event.getType() == EliteEventType.CARRIER_JUMP) {
				openCarrierJumpRequest = null;
				if (ts != null) {
					carrierJumpCompletionTime = ts;
					carrierJumpCompletionOffCarrier = false;
					if (latestCarrierEvent == null || ts.isAfter(latestCarrierEvent.getTimestamp())) {
						latestCarrierEvent = event;
					}
				}
			} else if (event instanceof CarrierLocationEvent loc && openCarrierJumpRequest != null && ts != null) {
				CarrierJumpRequestEvent req = openCarrierJumpRequest;
				Instant dep = req.getDepartureTime();
				if (dep != null
						&& CarrierJumpCooldown.isCarrierLocationJumpArrival(ts, dep)
						&& CarrierJumpCooldown.carrierLocationMatchesPendingJump(
								loc.getStarSystem(),
								loc.getSystemAddress(),
								req.getSystemName(),
								Long.valueOf(req.getSystemAddress()))) {
					carrierJumpCompletionTime = ts;
					carrierJumpCompletionOffCarrier = true;
					openCarrierJumpRequest = null;
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
				systemsStored += persistIfSystemIsChanging(cache, state, le.getStarSystem(), le.getSystemAddress());
			} else if (event instanceof FsdJumpEvent) {
				FsdJumpEvent je = (FsdJumpEvent) event;
				if (ts != null && (latestTransitionTs == null || ts.isAfter(latestTransitionTs))) {
					latestTransitionTs = ts;
					latestTransitionType = "FSDJump";
					latestTransitionSystem = je.getStarSystem();
					latestTransitionAddress = je.getSystemAddress();
				}
				systemsStored += persistIfSystemIsChanging(cache, state, je.getStarSystem(), je.getSystemAddress());
			}

			processor.handleEvent(event);

			if (rebuildMassacreFromBounties) {
				if (event instanceof LocationEvent le) {
					missionReplaySystem[0] = le.getStarSystem();
				} else if (event instanceof FsdJumpEvent je) {
					missionReplaySystem[0] = je.getStarSystem();
				} else if (event instanceof CarrierJumpEvent cj) {
					missionReplaySystem[0] = cj.getStarSystem();
				} else if (event instanceof SupercruiseExitEvent sc) {
					missionReplaySystem[0] = sc.getStarSystem();
				}
			}

			if (event instanceof MissionAcceptedEvent
					|| event instanceof MissionCompletedEvent
					|| event instanceof MissionFailedEvent
					|| event instanceof MissionAbandonedEvent
					|| event instanceof MissionRedirectedEvent
					|| event instanceof CargoDepotEvent
					|| event instanceof MissionsEvent
					|| (rebuildMassacreFromBounties && event instanceof BountyEvent)) {
				missionReplayTracker.applyEvent(event);
			}

			// Exobiology unsold total: during bulk, leave the preserved session value alone
			// (first-bonus needs live Spansh; do not guess Analyse payouts while replaying).
			if (!SystemCache.isBulkSystemWrite()) {
				if (event.getType() == EliteEventType.SELL_ORGANIC_DATA) {
					exoCreditsTotal = 0L;
					state.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
				}

				if (event instanceof ScanOrganicEvent) {
					ScanOrganicEvent so = (ScanOrganicEvent) event;
					if (so.getScanType() != null && "Analyse".equalsIgnoreCase(so.getScanType().trim())) {
						boolean firstBonus = false;
						BodyInfo body = state.getBodies().get(so.getBodyId());
						if (body != null) {
							if (!Boolean.TRUE.equals(body.getWasFootfalled()) && body.getSpanshLandmarks() == null) {
								SpanshBodyExobiologyInfo info = SpanshLandmarkCache.getInstance()
										.getOrFetch(body.getStarSystem(), body.getBodyName());
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
						}
					}
				}
			}
			//            persistIfStarScan(cache, state, event);
		}
		if (eventCount > 0) {
			double replaySeconds = (System.nanoTime() - replayStartNs) / 1_000_000_000.0;
			System.out.printf(Locale.US,
					"Replay finished: %d events processed, %d systems written, %.2f s%n",
					eventCount, systemsStored, replaySeconds);
			System.out.flush();
		}
		} finally {
			cache.endBulkSqliteTransaction();
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
			systemsStored++;
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

		// Persist exobiology total + carrier countdown into the same SQLite session blob as the overlay.
		EdoSessionState sessionState = EdoSessionPersistence.load();
		missionReplayTracker.fillSessionState(sessionState);
		sessionState.setExobiologyCreditsTotalUnsold(exoCreditsTotal);
		Integer replayParkedBody = state.getCarrierParkedBodyId();
		long replayParkedSys = state.getCarrierParkedSystemAddress();
		if (replayParkedBody != null && replayParkedBody.intValue() > 0) {
			sessionState.setCarrierParkedBodyId(replayParkedBody);
			sessionState.setCarrierParkedSystemAddress(replayParkedSys != 0L ? Long.valueOf(replayParkedSys) : null);
		}
		if (carrierJumpCompletionTime != null) {
			Instant now = Instant.now();
			sessionState.setCarrierJumpDepartureTime(null);
			sessionState.setCarrierJumpTargetSystem(null);
			Instant cooldownEnd = CarrierJumpCooldown.cooldownEndFromJump(
					carrierJumpCompletionTime, carrierJumpCompletionOffCarrier);
			if (cooldownEnd != null && cooldownEnd.isAfter(now)) {
				sessionState.setCarrierJumpCooldownEndTime(cooldownEnd.toString());
			} else {
				sessionState.setCarrierJumpCooldownEndTime(null);
			}
		} else if (latestCarrierEvent != null) {
			Instant now = Instant.now();
			if (latestCarrierEvent instanceof CarrierJumpRequestEvent) {
				CarrierJumpRequestEvent req = (CarrierJumpRequestEvent) latestCarrierEvent;
				Instant dep = req.getDepartureTime();
				if (dep != null && dep.isAfter(now)) {
					sessionState.setCarrierJumpDepartureTime(dep.toString());
					sessionState.setCarrierJumpTargetSystem(req.getSystemName());
				} else {
					sessionState.setCarrierJumpDepartureTime(null);
					sessionState.setCarrierJumpTargetSystem(null);
				}
				sessionState.setCarrierJumpCooldownEndTime(null);
			} else if (latestCarrierEvent.getType() == EliteEventType.CARRIER_JUMP
					|| latestCarrierEvent.getType() == EliteEventType.CARRIER_JUMP_CANCELLED) {
				sessionState.setCarrierJumpDepartureTime(null);
				sessionState.setCarrierJumpTargetSystem(null);
				Instant jumpTs = latestCarrierEvent.getTimestamp();
				boolean offCarrier = latestCarrierEvent.getType() == EliteEventType.CARRIER_JUMP_CANCELLED;
				Instant cooldownEnd = CarrierJumpCooldown.cooldownEndFromJump(jumpTs, offCarrier);
				if (cooldownEnd != null && cooldownEnd.isAfter(now)) {
					sessionState.setCarrierJumpCooldownEndTime(cooldownEnd.toString());
				} else {
					sessionState.setCarrierJumpCooldownEndTime(null);
				}
			} else {
				sessionState.setCarrierJumpDepartureTime(null);
				sessionState.setCarrierJumpTargetSystem(null);
				sessionState.setCarrierJumpCooldownEndTime(null);
			}
		}
		EdoSessionPersistence.save(sessionState);
		logMassacreRebuildSummary(missionReplayTracker, rebuildMassacreFromBounties);
		reportProgress(progress, "Finishing", 100, null);

		if (forcedJournalFile == null && journalDirectory != null && newestEventTimestamp != null) {
			JournalImportCursor.write(journalDirectory, newestEventTimestamp);
			System.out.println("Updated last journal import time to: " + newestEventTimestamp);
		} else if (forcedJournalFile != null) {
			System.out.println("Single-file rescan: left journal import cursor unchanged.");
		}

		double totalSeconds = (System.nanoTime() - rescanStartNs) / 1_000_000_000.0;
		System.out.printf(Locale.US,
				"Rescan complete: %d events replayed, %d systems written, total wall time %.2f s%n",
				eventCount, systemsStored, totalSeconds);
		System.out.flush();
		System.out.println("Exobiology expected credits total (unsold): " + exoCreditsTotal + " Cr");
	}

	private static void logMassacreRebuildSummary(MissionTracker tracker, boolean rebuilt) {
		if (!rebuilt || tracker == null) {
			System.out.println("Mission massacre progress: not rebuilt (incremental rescan).");
			return;
		}
		int combatWithKills = 0;
		int attributed = 0;
		for (org.dce.ed.mission.MissionRecord r : tracker.getActive()) {
			if (r.getCategory() != org.dce.ed.mission.MissionCategory.COMBAT || r.getKillCount() <= 0) {
				continue;
			}
			combatWithKills++;
			attributed += r.getKillsCompleted();
		}
		System.out.println("Mission massacre progress rebuilt into session_json: "
				+ combatWithKills + " combat mission(s), " + attributed + " attributed kill(s).");
	}

	/**
	 * Throttled replay progress (~every {@value #REPLAY_PROGRESS_EVERY_EVENTS} events or 2s, plus first/last).
	 *
	 * @return updated {@code lastProgressLogNs}
	 */
	private static long maybeReportReplayProgress(RescanProgressListener progress, int eventIndex, int eventCount,
			int systemsStored, long replayStartNs, long lastProgressLogNs, int[] lastReportedPercent) {
		if (eventCount <= 0) {
			return lastProgressLogNs;
		}
		int processed = eventIndex + 1;
		long nowNs = System.nanoTime();
		boolean first = eventIndex == 0;
		boolean last = processed >= eventCount;
		boolean interval = processed % REPLAY_PROGRESS_EVERY_EVENTS == 0;
		boolean time = (nowNs - lastProgressLogNs) >= REPLAY_PROGRESS_INTERVAL_NS;
		if (!first && !last && !interval && !time) {
			return lastProgressLogNs;
		}

		int pct = (int) (processed * 100L / eventCount);
		double pctExact = processed * 100.0 / eventCount;
		String eventsDetail = String.format(Locale.US, "%d / %d (%.1f%%)", processed, eventCount, pctExact);
		String systemsSuffix = systemsStored > 0
				? String.format(Locale.US, ", systems cached: %d", systemsStored)
				: "";

		if (progress != null) {
			if (pct != lastReportedPercent[0] || first || last || interval || time) {
				lastReportedPercent[0] = pct;
				double elapsed = (nowNs - replayStartNs) / 1_000_000_000.0;
				String timing = formatElapsedWithEta(elapsed, processed, eventCount);
				reportProgress(progress, "Rebuilding cache", pct,
						processed + " / " + eventCount + " events"
								+ (systemsStored > 0 ? ", " + systemsStored + " systems stored" : "")
								+ timing);
			}
		} else {
			System.out.printf(Locale.US, "Processing events: %s%s%n", eventsDetail, systemsSuffix);
			System.out.flush();
		}
		return nowNs;
	}

	/** e.g. {@code , 45 s (~2m left)} once enough events have run for a stable estimate. */
	private static String formatElapsedWithEta(double elapsedSeconds, int processed, int eventCount) {
		String elapsed = String.format(Locale.US, ", %.0f s", Math.max(0.0, elapsedSeconds));
		if (processed <= 0 || eventCount <= processed || elapsedSeconds < 2.0) {
			return elapsed;
		}
		double remaining = elapsedSeconds * (eventCount - processed) / (double) processed;
		return elapsed + " (~" + formatDurationRough(remaining) + " left)";
	}

	private static String formatDurationRough(double seconds) {
		long sec = Math.max(0L, Math.round(seconds));
		if (sec < 60L) {
			return sec + "s";
		}
		long min = sec / 60L;
		long remSec = sec % 60L;
		if (min < 60L) {
			return remSec > 0 ? min + "m " + remSec + "s" : min + "m";
		}
		long hours = min / 60L;
		long remMin = min % 60L;
		return remMin > 0 ? hours + "h " + remMin + "m" : hours + "h";
	}

	private static void reportProgress(RescanProgressListener progress, String phase, int percent, String detail) {
		if (progress != null) {
			progress.onProgress(phase, percent, detail);
		} else {
			logCliProgress(phase, percent, detail);
		}
	}

	/** Console progress for CLI {@link #main} and any caller without a {@link RescanProgressListener}. */
	private static void logCliProgress(String phase, int percent, String detail) {
		String phaseText = phase != null ? phase : "Working";
		String line;
		if (percent >= 0 && percent <= 100) {
			if (detail != null && !detail.isBlank()) {
				line = phaseText + ": " + percent + "% — " + detail;
			} else {
				line = phaseText + ": " + percent + "%";
			}
		} else if (detail != null && !detail.isBlank()) {
			line = phaseText + " — " + detail;
		} else {
			line = phaseText + "...";
		}
		System.out.println(line);
		System.out.flush();
	}

	private static int persistIfSystemIsChanging(SystemCache cache, SystemState state, String nextName, long nextAddr) {
		String curName = state.getSystemName();
		long curAddr = state.getSystemAddress();

		boolean sameName = nextName != null && nextName.equals(curName);
		boolean sameAddr = nextAddr != 0L && nextAddr == curAddr;

		// Only treat it as "same system" if BOTH match (when available).
		if (sameName && sameAddr) {
			return 0;
		}

		cache.storeSystem(state);
		return 1;
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
