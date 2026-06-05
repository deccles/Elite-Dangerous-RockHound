package org.dce.ed.logreader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Persists the last processed Elite journal position.
 *
 * <p>Shared by {@link RescanJournalsMain} and {@link LiveJournalMonitor}. The live monitor also
 * stores the tailed journal file name and byte offset so events in the same UTC second are not
 * replayed on restart (second-precision timestamps alone are not enough).</p>
 */
public final class JournalImportCursor {

    private static final String LAST_IMPORT_FILENAME = "edo-cache.lastRescanTimestamp";

    private JournalImportCursor() {
    }

    /** Live tail checkpoint: timestamp plus exact file offset in the tailed journal. */
    public static final class TailPosition {
        public final Instant instant;
        public final String journalFileName;
        public final long byteOffset;

        public TailPosition(Instant instant, String journalFileName, long byteOffset) {
            this.instant = instant;
            this.journalFileName = journalFileName;
            this.byteOffset = Math.max(0L, byteOffset);
        }

        boolean matchesFile(Path journalFile) {
            if (journalFile == null || journalFileName == null || journalFileName.isBlank()) {
                return false;
            }
            return journalFileName.equals(journalFile.getFileName().toString());
        }
    }

    public static Path getCursorFile(Path journalDirectory) {
        if (journalDirectory == null) {
            return null;
        }
        return journalDirectory.resolve(LAST_IMPORT_FILENAME);
    }

    public static Instant read(Path journalDirectory) {
        TailPosition pos = readTailPosition(journalDirectory);
        return pos != null ? pos.instant : null;
    }

    public static TailPosition readTailPosition(Path journalDirectory) {
        Path cursor = getCursorFile(journalDirectory);
        if (cursor == null || !Files.isRegularFile(cursor)) {
            return null;
        }
        try {
            String text = Files.readString(cursor, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return null;
            }
            String[] lines = text.split("\\R");
            Instant instant = Instant.parse(lines[0].trim());
            if (lines.length >= 3) {
                String fileName = lines[1].trim();
                long offset = Long.parseLong(lines[2].trim());
                return new TailPosition(instant, fileName, offset);
            }
            return new TailPosition(instant, null, 0L);
        } catch (Exception ex) {
            System.err.println("Failed to read last journal timestamp from " + cursor + ": " + ex.getMessage());
            return null;
        }
    }

    public static void write(Path journalDirectory, Instant instant) {
        write(journalDirectory, new TailPosition(instant, null, 0L));
    }

    public static void write(Path journalDirectory, TailPosition position) {
        if (position == null || position.instant == null) {
            return;
        }
        Path cursor = getCursorFile(journalDirectory);
        if (cursor == null) {
            return;
        }
        try {
            String fileName = position.journalFileName == null ? "" : position.journalFileName;
            String payload = position.instant.toString() + System.lineSeparator()
                    + fileName + System.lineSeparator()
                    + position.byteOffset;
            Files.writeString(cursor, payload, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            System.err.println("Failed to write last journal timestamp to " + cursor + ": " + ex.getMessage());
        }
    }
}
