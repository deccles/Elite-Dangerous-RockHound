package org.dce.systemmodel.journal;

public sealed interface JournalRecord permits ScanRecord, ScanBaryCentreRecord {
}
