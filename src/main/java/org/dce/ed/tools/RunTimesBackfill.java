package org.dce.ed.tools;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.mining.GoogleSheetsBackend;
import org.dce.ed.mining.ProspectorLogRow;

/**
 * Command-line / tools-panel helper that backfills missing mining run
 * start/end times in the Google Sheet using Elite Dangerous journals.
 */
public final class RunTimesBackfill {

	private static final DateTimeFormatter TS_FMT =
		DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US).withZone(ZoneId.systemDefault());

	private record RunKey(int run, String commander) {}

	public static void main(String[] args) {
		backfillUsingPreferences(null);
	}

	/**
	 * Entry-point used from the Preferences -> Tools panel. Uses the mining
	 * Google Sheets URL and commander name from {@link OverlayPreferences},
	 * and the journal directory from {@link OverlayPreferences#resolveJournalDirectory}.
	 */
	public static void backfillUsingPreferences(java.awt.Component parent) {
		String url = OverlayPreferences.getMiningGoogleSheetsUrl();
		if (url == null || url.isBlank()) {
			showMessage(parent,
				"No mining Google Sheet is configured.\nSet a Google Sheets URL in the Mining preferences first.",
				"Mining Sheet Not Configured",
				JOptionPane.WARNING_MESSAGE);
			return;
		}

		String commanderFilter = OverlayPreferences.getMiningLogCommanderName();
		if (commanderFilter == null || commanderFilter.isBlank()) {
			commanderFilter = "-";
		}

		List<ProspectorLogRow> rows;
		try {
			GoogleSheetsBackend backend = new GoogleSheetsBackend(url);
			rows = backend.loadRows();
		} catch (Exception ex) {
			showMessage(parent,
				"Unable to load mining rows from Google Sheets:\n" + ex.getMessage(),
				"Mining Sheet Error",
				JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (rows == null || rows.isEmpty()) {
			showMessage(parent,
				"No mining rows were found in the configured sheet.",
				"Mining Sheet",
				JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// Group rows by (run, commander) but only for the active commander.
		Map<RunKey, List<ProspectorLogRow>> byRun = new HashMap<>();
		for (ProspectorLogRow r : rows) {
			if (r == null) continue;
			String cmdr = r.getCommanderName();
			if (cmdr == null || cmdr.isBlank()) cmdr = "-";
			if (!cmdr.equalsIgnoreCase(commanderFilter)) {
				continue;
			}
			byRun.computeIfAbsent(new RunKey(r.getRun(), cmdr), k -> new ArrayList<>()).add(r);
		}
		if (byRun.isEmpty()) {
			showMessage(parent,
				"No mining rows were found for commander \"" + commanderFilter + "\".",
				"Mining Sheet",
				JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// Read all journal events once; we'll filter them by commander and time window per run.
		List<EliteLogEvent> events;
		try {
			EliteJournalReader reader = new EliteJournalReader(OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey));
			events = reader.readAllEvents();
		} catch (Exception ex) {
			showMessage(parent,
				"Unable to read Elite Dangerous journals:\n" + ex.getMessage(),
				"Journals Not Available",
				JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (events.isEmpty()) {
			showMessage(parent,
				"No journal events were found in the configured journal directory.",
				"Journals",
				JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// Filter journal events down to those for the active commander. Some event
		// types don't repeat the Commander field on every line, so we track the
		// "current" commander as we walk the events in chronological order,
		// updating it whenever we see a Commander field, and then associate each
		// event with whatever commander was active at that moment.
		final String commanderFinal = commanderFilter;
		List<EliteLogEvent> commanderEvents = new ArrayList<>();
		String activeCommander = null;
		// events are already sorted by timestamp in EliteJournalReader.readAllEvents()
		for (EliteLogEvent e : events) {
			String cmdr = extractCommanderName(e);
			if (cmdr != null && !cmdr.isBlank()) {
				activeCommander = cmdr;
			}
			if (activeCommander != null
					&& activeCommander.equalsIgnoreCase(commanderFinal)) {
				commanderEvents.add(e);
			}
		}

		if (commanderEvents.isEmpty()) {
			showMessage(parent,
				"No journal events were found in the journals for commander \""
					+ commanderFilter + "\".",
				"Journals",
				JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// For each run, decide whether we need backfill and compute start/end from journals.
		List<ProspectorLogRow> updatedRows = new ArrayList<>();
		int examinedRuns = 0;
		for (Map.Entry<RunKey, List<ProspectorLogRow>> entry : byRun.entrySet()) {
			RunKey key = entry.getKey();
			List<ProspectorLogRow> runRows = new ArrayList<>(entry.getValue());
			if (runRows.isEmpty()) continue;
			examinedRuns++;

			// Sort rows by timestamp so we can compute the span.
			runRows.sort(Comparator.comparing(ProspectorLogRow::getTimestamp,
				Comparator.nullsLast(Comparator.naturalOrder())));

			Instant firstTs = runRows.stream()
				.map(ProspectorLogRow::getTimestamp)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
			Instant lastTs = runRows.stream()
				.map(ProspectorLogRow::getTimestamp)
				.filter(Objects::nonNull)
				.reduce((a, b) -> b) // last non-null
				.orElse(firstTs);
			if (firstTs == null || lastTs == null) {
				continue; // nothing to anchor this run on
			}

			// Canonical row: first row that already has a run start time; otherwise A asteroid, otherwise earliest.
			ProspectorLogRow canonical = runRows.stream()
				.filter(r -> r.getRunStartTime() != null)
				.findFirst()
				.orElseGet(() -> {
					for (ProspectorLogRow r : runRows) {
						String aid = r.getAsteroidId();
						if (aid != null && aid.equalsIgnoreCase("A")) {
							return r;
						}
					}
					return runRows.get(0);
				});

			Instant existingStart = canonical.getRunStartTime();
			Instant existingEnd = canonical.getRunEndTime();

			// Skip runs that already have both start and end times.
			if (existingStart != null && existingEnd != null) {
				continue;
			}

			// Find journal-based start/end for this run.
			Instant backfillStart = findRunStartFromJournals(commanderEvents, firstTs);
			Instant backfillEnd = findRunEndFromJournals(commanderEvents, lastTs);

			// Only fill missing fields; never overwrite existing times.
			boolean changed = false;
			Instant newStart = existingStart;
			Instant newEnd = existingEnd;
			if (existingStart == null && backfillStart != null) {
				newStart = backfillStart;
				changed = true;
			}
			if (existingEnd == null && backfillEnd != null) {
				newEnd = backfillEnd;
				changed = true;
			}

			if (!changed) {
				continue;
			}

			ProspectorLogRow updated = new ProspectorLogRow(
				canonical.getRun(),
				canonical.getAsteroidId(),
				canonical.getFullBodyName(),
				canonical.getTimestamp(),
				canonical.getMaterial(),
				canonical.getPercent(),
				canonical.getBeforeAmount(),
				canonical.getAfterAmount(),
				canonical.getDifference(),
				canonical.getCommanderName(),
				canonical.getShipType(),
				canonical.getCoreType(),
				canonical.getDuds(),
				newStart,
				newEnd,
				canonical.getComments()
			);
			updatedRows.add(updated);
		}

		if (updatedRows.isEmpty()) {
			showMessage(parent,
				"No runs for commander \"" + commanderFilter + "\" needed backfilling.\n"
					+ "Examined " + examinedRuns + " run(s).",
				"Backfill Mining Runs",
				JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		try {
			GoogleSheetsBackend backend = new GoogleSheetsBackend(url);
			backend.upsertRows(updatedRows);
		} catch (Exception ex) {
			showMessage(parent,
				"Unable to write updated run times back to the mining sheet:\n" + ex.getMessage(),
				"Backfill Failed",
				JOptionPane.ERROR_MESSAGE);
			return;
		}

		String summary = updatedRows.stream()
			.collect(Collectors.groupingBy(ProspectorLogRow::getRun, Collectors.counting()))
			.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(e -> "Run " + e.getKey() + " (" + e.getValue() + " updated row(s))")
			.collect(Collectors.joining("\n"));

		showMessage(parent,
			String.format(Locale.US,
				"Backfilled mining runs for commander \"%s\".\n"
					+ "Examined %d run(s); updated %d row(s).\n\n%s",
				commanderFilter, examinedRuns, updatedRows.size(), summary),
			"Backfill Mining Runs",
			JOptionPane.INFORMATION_MESSAGE);
	}

	private static String extractCommanderName(EliteLogEvent e) {
		if (e == null || e.getRawJson() == null) {
			return null;
		}
		try {
			if (e.getRawJson().has("Commander")) {
				String cmdr = e.getRawJson().get("Commander").getAsString();
				return (cmdr != null && !cmdr.isBlank()) ? cmdr : null;
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static Instant findRunStartFromJournals(List<EliteLogEvent> events, Instant firstTs) {
		if (firstTs == null || events.isEmpty()) {
			return null;
		}
		// Only consider undock-like events within a reasonable window before the run
		// start, so we don't accidentally pick something days earlier.
		Instant windowStart = firstTs.minusSeconds(12 * 3600); // 12 hours back
		Instant bestTs = null;

		// First preference: explicit UNDOCKED events in the window.
		for (EliteLogEvent e : events) {
			Instant ts = e.getTimestamp();
			if (ts == null || ts.isAfter(firstTs)) {
				break;
			}
			if (ts.isBefore(windowStart)) {
				continue;
			}
			if (e.getType() == EliteEventType.UNDOCKED) {
				if (bestTs == null || ts.isAfter(bestTs)) {
					bestTs = ts;
				}
			}
		}
		if (bestTs != null) {
			return bestTs;
		}

		// Fallback: last Location event with Docked == false in the window.
		LocationEvent bestLoc = null;
		for (EliteLogEvent e : events) {
			if (!(e instanceof LocationEvent le)) {
				continue;
			}
			Instant ts = le.getTimestamp();
			if (ts == null || ts.isAfter(firstTs)) {
				break;
			}
			if (ts.isBefore(windowStart)) {
				continue;
			}
			if (!le.isDocked()) {
				if (bestLoc == null || ts.isAfter(bestLoc.getTimestamp())) {
					bestLoc = le;
				}
			}
		}
		return bestLoc != null ? bestLoc.getTimestamp() : null;
	}

	private static Instant findRunEndFromJournals(List<EliteLogEvent> events, Instant lastTs) {
		if (lastTs == null || events.isEmpty()) {
			return null;
		}
		for (EliteLogEvent e : events) {
			Instant ts = e.getTimestamp();
			if (ts == null || ts.isBefore(lastTs) || ts.equals(lastTs)) {
				continue;
			}
			// We treat the end of a run as the first "dock" AFTER the run, but BEFORE
			// the next "undock". This keeps a run within a single undock/dock cycle.
			if (e.getType() == EliteEventType.DOCKED) {
				return ts;
			}
			if (e.getType() == EliteEventType.UNDOCKED) {
				// We've seen a new undock without a dock first; don't borrow a later dock.
				break;
			}
			if (e instanceof LocationEvent le) {
				if (le.isDocked()) {
					return ts;
				} else if (!le.isDocked()) {
					break;
				}
			}
		}
		return null;
	}

	private static void showMessage(java.awt.Component parent, String message, String title, int type) {
		if (parent != null) {
			JOptionPane.showMessageDialog(parent, message, title, type);
		} else {
			// CLI usage: print to stdout/stderr instead of showing a dialog.
			String prefix = (type == JOptionPane.ERROR_MESSAGE) ? "ERROR: " :
				(type == JOptionPane.WARNING_MESSAGE) ? "WARN: " : "";
			System.out.println(prefix + title + ": " + message.replace('\n', ' '));
		}
	}
}

