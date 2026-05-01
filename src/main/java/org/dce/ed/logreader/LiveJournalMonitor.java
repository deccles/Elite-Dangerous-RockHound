package org.dce.ed.logreader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.ExceptionReporting;
import org.dce.ed.logreader.event.StatusEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Tails the latest Elite Dangerous journal file and emits EliteLogEvent
 * objects to registered listeners in (roughly) real time.
 *
 * This class does NOT replay history on startup; the UI that wants
 * history (e.g. SystemTabPanel) should use EliteJournalReader once at
 * construction time, then rely on this monitor for new events only.
 */
public final class LiveJournalMonitor {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private Path statusFile;

    private WatchService statusWatchService;
    private Thread statusWatcherThread;

    private Instant lastStatusTimestamp;
    private int lastStatusFlags  = Integer.MIN_VALUE;
    private int lastStatusFlags2 = Integer.MIN_VALUE;

    private Double lastStatusLatitude;
    private Double lastStatusLongitude;
    private Double lastStatusAltitude;
    private Double lastStatusHeading;
    private String lastStatusBodyName;
    private Double lastStatusPlanetRadius;

    private static Map<String,LiveJournalMonitor> INSTANCE = new HashMap<String,LiveJournalMonitor>();

    private final CopyOnWriteArrayList<Consumer<EliteLogEvent>> listeners =
            new CopyOnWriteArrayList<>();

    private final EliteLogParser parser = new EliteLogParser();

    private volatile boolean running = false;
    private Thread workerThread;

    private Instant lastJournalIoErrorLog;

    private String clientKey;


    private Path journalDirectory;

    private Instant lastProcessedJournalTimestamp;
    private Instant lastCursorPersistAt;
    private LiveJournalMonitor(String clientKey) {
        this.clientKey = clientKey;
    }

    public static LiveJournalMonitor getInstance(String clientKey) {
        LiveJournalMonitor liveJournalMonitor = INSTANCE.get(clientKey);
        if (liveJournalMonitor == null) {
            INSTANCE.put(clientKey,  new LiveJournalMonitor(clientKey));
        }
        return INSTANCE.get(clientKey);
    }

    public void addListener(Consumer<EliteLogEvent> listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        startIfNeeded();
    }

    public void removeListener(Consumer<EliteLogEvent> listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    public void shutdown() {
        running = false;
        Thread t = workerThread;
        if (t != null) {
            t.interrupt();
        }

        Thread sw = statusWatcherThread;
        if (sw != null) {
            sw.interrupt();
        }
        WatchService ws = statusWatchService;
        if (ws != null) {
            try {
                ws.close();
            } catch (IOException ignored) {
            }
        }
    }

    private synchronized void startIfNeeded() {
        if (running) {
            return;
        }
        running = true;
        workerThread = new Thread(this::runLoop, "Elite-LiveJournalMonitor");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void runLoop() {
        Path journalDir = OverlayPreferences.resolveJournalDirectory(clientKey);

        if (journalDir == null || !Files.isDirectory(journalDir) || !EliteLogFileLocator.looksLikeJournalDirectory(journalDir)) {
            System.err.println("[EDO] LiveJournalMonitor: journal directory not found. clientKey=" + clientKey
                    + " autoLogDir=" + OverlayPreferences.isAutoLogDir(clientKey)
                    + " customDir=\"" + OverlayPreferences.getCustomLogDir(clientKey) + "\"");
            running = false;
            return;
        }

        System.err.println("[EDO] LiveJournalMonitor: watching journals in \""
                + journalDir.toAbsolutePath() + "\" (clientKey=" + clientKey + ")");

        // remember Status.json in the same directory
        statusFile = journalDir.resolve("Status.json");


        journalDirectory = journalDir;
        lastProcessedJournalTimestamp = JournalImportCursor.read(journalDirectory);
        if (lastProcessedJournalTimestamp != null) {
            System.err.println("[EDO] LiveJournalMonitor: last journal timestamp (UTC): " + lastProcessedJournalTimestamp);
        }
        // watch Status.json for immediate updates
        startStatusWatcher(journalDir);

        // Seed with the current Status.json so listeners (e.g. Biology tab) have an initial position immediately.
        pollStatusFileWithRetry();

        Path currentFile = null;
        long filePointer = 0L;

        while (running) {
            try {
                Path latest = findLatestJournalFile(journalDir);

                if (!Objects.equals(latest, currentFile)) {
                    currentFile = latest;

                    if (currentFile != null) {
                        System.err.println("[EDO] LiveJournalMonitor: tailing journal file \""
                                + currentFile.toAbsolutePath() + "\"");

                        // IMPORTANT: when the journal rotates while we're running, start at 0
                        // so we don't miss initial events that were written before our next poll.
                        filePointer = 0L;
                    } else {
                        filePointer = 0L;
                    }
                }

                if (currentFile != null) {
                    filePointer = readFromFile(currentFile, filePointer);
                }

                if (statusWatcherThread == null || !statusWatcherThread.isAlive()) {
                    pollStatusFileWithRetry();
                }

                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (Exception ex) {
                try {
                    Thread.sleep(POLL_INTERVAL.toMillis());
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private synchronized void startStatusWatcher(Path journalDir) {
        if (statusWatcherThread != null) {
            return;
        }
        if (journalDir == null) {
            return;
        }

        try {
            statusWatchService = journalDir.getFileSystem().newWatchService();
            journalDir.register(
                    statusWatchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );

            statusWatcherThread = new Thread(() -> runStatusWatchLoop(journalDir), "Elite-StatusWatchService");
            statusWatcherThread.setDaemon(true);
            statusWatcherThread.start();
        } catch (IOException ex) {
            statusWatchService = null;
            statusWatcherThread = null;
        }
    }

    private void runStatusWatchLoop(Path journalDir) {
        long lastTriggerNs = 0L;

        while (running && statusWatchService != null) {
            try {
                WatchKey key = statusWatchService.take();

                boolean statusTouched = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    Object ctx = event.context();
                    if (!(ctx instanceof Path)) {
                        continue;
                    }

                    Path changed = (Path) ctx;
                    if (changed != null && "Status.json".equalsIgnoreCase(changed.getFileName().toString())) {
                        statusTouched = true;
                    }
                }

                // keep watch service alive
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }

                if (!statusTouched) {
                    continue;
                }

                // Debounce: Elite may write Status.json in multiple steps.
                long now = System.nanoTime();
                long since = now - lastTriggerNs;
                lastTriggerNs = now;

                // If we’re getting hammered, still read, but give the writer a moment.
                if (since < 40_000_000L) { // < 40ms
                    sleepQuietly(25);
                } else {
                    sleepQuietly(10);
                }

                pollStatusFileWithRetry();

            } catch (InterruptedException ie) {
                break;
            } catch (Exception ex) {
                // If the watcher fails for any reason, fall back to polling.
                break;
            }
        }

        // mark watcher dead so the polling fallback can pick up
        statusWatcherThread = null;
        WatchService ws = statusWatchService;
        statusWatchService = null;
        if (ws != null) {
            try {
                ws.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            // ignore
        }
    }

    private void pollStatusFileWithRetry() {
        try {
            pollStatusFile();
        } catch (RuntimeException ex) {
            // in case we read mid-write; retry once
            sleepQuietly(20);
            try {
                pollStatusFile();
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * Poll Status.json in the journal directory.
     * When Flags / Flags2 change, emit a StatusEvent into the normal pipeline.
     */
    private void pollStatusFile() {
        if (statusFile == null || !Files.isRegularFile(statusFile)) {
            return;
        }
        try {
            String json = Files.readString(statusFile, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) {
                return;
            }

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // timestamp
            Instant ts = null;
            JsonElement tsEl = root.get("timestamp");
            if (tsEl != null && !tsEl.isJsonNull()) {
                try {
                    ts = Instant.parse(tsEl.getAsString());
                } catch (Exception ignored) {
                }
            }

            int flags = getIntOrDefault(root, "Flags", 0);
            int flags2 = getIntOrDefault(root, "Flags2", 0);

            // Pips: [sys, eng, wep]
            int[] pips = new int[] { 0, 0, 0 };
            JsonElement pipsEl = root.get("Pips");
            if (pipsEl != null && pipsEl.isJsonArray()) {
                JsonArray arr = pipsEl.getAsJsonArray();
                for (int i = 0; i < Math.min(3, arr.size()); i++) {
                    try {
                        pips[i] = arr.get(i).getAsInt();
                    } catch (Exception ignored) {
                    }
                }
            }

            int fireGroup = getIntOrDefault(root, "FireGroup", 0);
            int guiFocus = getIntOrDefault(root, "GuiFocus", 0);

            double fuelMain = 0.0;
            double fuelReservoir = 0.0;
            JsonElement fuelEl = root.get("Fuel");
            if (fuelEl != null && fuelEl.isJsonObject()) {
                JsonObject fuel = fuelEl.getAsJsonObject();
                fuelMain = getDoubleOrDefault(fuel, "FuelMain", 0.0);
                fuelReservoir = getDoubleOrDefault(fuel, "FuelReservoir", 0.0);
            }

            double cargo = getDoubleOrDefault(root, "Cargo", 0.0);
            String legalState = getStringOrNull(root, "LegalState");
            long balance = getLongOrDefault(root, "Balance", 0L);

            // ---- Extra Status.json fields ----
            Double latitude = null;
            if (root.has("Latitude") && !root.get("Latitude").isJsonNull()) {
                try {
                    latitude = root.get("Latitude").getAsDouble();
                } catch (Exception ignored) {
                }
            }

            Double longitude = null;
            if (root.has("Longitude") && !root.get("Longitude").isJsonNull()) {
                try {
                    longitude = root.get("Longitude").getAsDouble();
                } catch (Exception ignored) {
                }
            }

            Double altitude = null;
            if (root.has("Altitude") && !root.get("Altitude").isJsonNull()) {
                try {
                    altitude = root.get("Altitude").getAsDouble();
                } catch (Exception ignored) {
                }
            }

            Double heading = null;
            if (root.has("Heading") && !root.get("Heading").isJsonNull()) {
                try {
                    heading = root.get("Heading").getAsDouble();
                } catch (Exception ignored) {
                }
            }

            String bodyName = getStringOrNull(root, "BodyName");

            Double planetRadius = null;
            if (root.has("PlanetRadius") && !root.get("PlanetRadius").isJsonNull()) {
                try {
                    planetRadius = root.get("PlanetRadius").getAsDouble();
                } catch (Exception ignored) {
                }
            }

            // ---- Destination block ----
            Long destSystem = null;
            Integer destBody = null;
            String destName = null;
            String destNameLocalised = null;

            JsonElement destEl = root.get("Destination");
            if (destEl != null && destEl.isJsonObject()) {
                JsonObject dest = destEl.getAsJsonObject();

                JsonElement sysEl = dest.get("System");
                if (sysEl != null && !sysEl.isJsonNull()) {
                    try {
                        destSystem = sysEl.getAsLong();
                    } catch (Exception ignored) {
                    }
                }

                JsonElement bodyEl = dest.get("Body");
                if (bodyEl != null && !bodyEl.isJsonNull()) {
                    try {
                        destBody = bodyEl.getAsInt();
                    } catch (Exception ignored) {
                    }
                }
                if (destBody != null && destBody.intValue() == 0) {
                    destBody = null;
                }

                JsonElement nameEl = dest.get("Name");
                if (nameEl != null && !nameEl.isJsonNull()) {
                    try {
                        destName = nameEl.getAsString();
                    } catch (Exception ignored) {
                    }
                }

                JsonElement nameLocEl = dest.get("Name_Localised");
                if (nameLocEl != null && !nameLocEl.isJsonNull()) {
                    try {
                        destNameLocalised = nameLocEl.getAsString();
                    } catch (Exception ignored) {
                    }
                }
            }

            // Build the StatusEvent using the extended constructor
            StatusEvent event =
                    new StatusEvent(
                            ts,
                            root,
                            flags,
                            flags2,
                            pips,
                            fireGroup,
                            guiFocus,
                            fuelMain,
                            fuelReservoir,
                            cargo,
                            legalState,
                            balance,
                            latitude,
                            longitude,
                            altitude,
                            heading,
                            bodyName,
                            planetRadius,
                            destSystem,
                            destBody,
                            destName,
                            destNameLocalised
                    );

            // Emit Status updates promptly (not just Flags changes).
            // For surface biology tracking we need high-frequency Lat/Lon updates.
            boolean flagsChanged = (flags != lastStatusFlags) || (flags2 != lastStatusFlags2);
            boolean tsChanged = !Objects.equals(ts, lastStatusTimestamp);
            boolean positionChanged =
                    !Objects.equals(latitude, lastStatusLatitude)
                            || !Objects.equals(longitude, lastStatusLongitude)
                            || !Objects.equals(altitude, lastStatusAltitude)
                            || !Objects.equals(heading, lastStatusHeading)
                            || !Objects.equals(bodyName, lastStatusBodyName)
                            || !Objects.equals(planetRadius, lastStatusPlanetRadius);

            if (flagsChanged || tsChanged || positionChanged) {
                dispatch(event);
            }

            lastStatusTimestamp = ts;
            lastStatusFlags = flags;
            lastStatusFlags2 = flags2;
            lastStatusLatitude = latitude;
            lastStatusLongitude = longitude;
            lastStatusAltitude = altitude;
            lastStatusHeading = heading;
            lastStatusBodyName = bodyName;
            lastStatusPlanetRadius = planetRadius;

        } catch (IOException | JsonSyntaxException ex) {
            ex.printStackTrace();
        }
    }

    private static int getIntOrDefault(JsonObject obj, String key, int def) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return def;
        }
        try {
            return el.getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    private static long getLongOrDefault(JsonObject obj, String key, long def) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return def;
        }
        try {
            return el.getAsLong();
        } catch (Exception e) {
            return def;
        }
    }

    private static double getDoubleOrDefault(JsonObject obj, String key, double def) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return def;
        }
        try {
            return el.getAsDouble();
        } catch (Exception e) {
            return def;
        }
    }

    private static String getStringOrNull(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        try {
            return el.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private long readFromFile(Path file, long startPos) {
        if (!Files.isRegularFile(file)) {
            return startPos;
        }

        long newPos = startPos;

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(startPos);

            String raw;
            while ((raw = raf.readLine()) != null) {
                newPos = raf.getFilePointer();

                // Convert from ISO-8859-1 assumption to UTF-8
                String line = new String(raw.getBytes(StandardCharsets.ISO_8859_1),
                                         StandardCharsets.UTF_8).trim();
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    EliteLogEvent event = parser.parseRecord(line);
                    if (event != null) {
                        Instant ts = event.getTimestamp();
                        if (ts != null && lastProcessedJournalTimestamp != null && ts.isBefore(lastProcessedJournalTimestamp)) {
                            continue;
                        }
                        dispatch(event);
                        updateCursorIfNeeded(ts);
                    }
                } catch (JsonSyntaxException | IllegalStateException ex) {
                    // malformed line – skip
                }
            }
        } catch (IOException ex) {
            // transient I/O – skip; retry next poll
            maybeLogJournalIoError(file, ex);
        }

        return newPos;
    }

    private void updateCursorIfNeeded(Instant eventTimestamp) {
        if (eventTimestamp == null) {
            return;
        }
        if (lastProcessedJournalTimestamp == null || eventTimestamp.isAfter(lastProcessedJournalTimestamp)) {
            lastProcessedJournalTimestamp = eventTimestamp;
        }

        Instant now = Instant.now();
        if (lastCursorPersistAt != null && Duration.between(lastCursorPersistAt, now).toMillis() < 1000) {
            return;
        }
        lastCursorPersistAt = now;
        JournalImportCursor.write(journalDirectory, lastProcessedJournalTimestamp);
    }

    private void maybeLogJournalIoError(Path file, IOException ex) {
        Instant now = Instant.now();
        if (lastJournalIoErrorLog != null && Duration.between(lastJournalIoErrorLog, now).getSeconds() < 30) {
            return;
        }
        lastJournalIoErrorLog = now;

        String msg = ex.getMessage();
        if (msg == null) {
            msg = ex.getClass().getSimpleName();
        }

        System.err.println("[EDO] LiveJournalMonitor: I/O while reading \""
                + file.toAbsolutePath() + "\": " + msg);
    }

    public void dispatch(EliteLogEvent event) {
        for (Consumer<EliteLogEvent> l : listeners) {
            try {
                l.accept(event);
            } catch (RuntimeException ex) {
                // don't let one bad listener break the others, but make the failure visible
                ExceptionReporting.report(ex, "LiveJournalMonitor listener");
            }
        }
    }

    private Path findLatestJournalFile(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> candidates = s
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("Journal.") && name.endsWith(".log");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());

            if (candidates.isEmpty()) {
                return null;
            }
            return candidates.get(candidates.size() - 1);
        }
    }
}
