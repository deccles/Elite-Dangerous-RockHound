package org.dce.ed.exec;

import java.util.ArrayList;
import java.util.List;

import org.dce.ed.exec.placeholder.ExecPlaceholderId;
import org.dce.ed.logreader.EliteEventType;

/** Builds reference rows for Exec tab help dialogs. */
public final class ExecReferenceHelp {

    private ExecReferenceHelp() {
    }

    public static List<String[]> journalEventRows() {
        List<String[]> rows = new ArrayList<>();
        for (EliteEventType type : EliteEventType.execSelectableValues()) {
            rows.add(new String[] { type.getJournalName(), type.execHelpDescription() });
        }
        return rows;
    }

    public static List<String[]> variableRows() {
        List<String[]> rows = new ArrayList<>();
        for (ExecPlaceholderId id : ExecPlaceholderId.sortedCatalog()) {
            rows.add(new String[] { id.token(), id.getDescription() });
        }
        return rows;
    }
}
