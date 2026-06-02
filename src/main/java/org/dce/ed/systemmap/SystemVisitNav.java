package org.dce.ed.systemmap;

import java.util.ArrayList;
import java.util.List;

/** Back/forward navigation through journal system visits (and manual off-journal loads). */
public final class SystemVisitNav {

    private final List<String> history = new ArrayList<>();
    private int index = -1;

    public void setJournalHistory(List<String> journalSystems, String currentSystemName) {
        history.clear();
        if (journalSystems != null) {
            for (String name : journalSystems) {
                if (name != null && !name.isBlank()) {
                    history.add(name.trim());
                }
            }
        }
        index = indexOfIgnoreCase(currentSystemName);
        if (index < 0 && !history.isEmpty()) {
            index = history.size() - 1;
        }
    }

    public void visit(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return;
        }
        String trimmed = systemName.trim();
        int hit = indexOfIgnoreCase(trimmed);
        if (hit >= 0) {
            index = hit;
            return;
        }
        if (index >= 0 && index < history.size() - 1) {
            history.subList(index + 1, history.size()).clear();
        }
        history.add(trimmed);
        index = history.size() - 1;
    }

    public String back() {
        if (!canBack()) {
            return null;
        }
        index--;
        return history.get(index);
    }

    public String forward() {
        if (!canForward()) {
            return null;
        }
        index++;
        return history.get(index);
    }

    public boolean canBack() {
        return index > 0;
    }

    public boolean canForward() {
        return index >= 0 && index < history.size() - 1;
    }

    public int historySize() {
        return history.size();
    }

    public int currentIndex() {
        return index;
    }

    public void setIndex(int newIndex) {
        if (newIndex < -1 || newIndex >= history.size()) {
            return;
        }
        index = newIndex;
    }

    private int indexOfIgnoreCase(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return -1;
        }
        String trimmed = systemName.trim();
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).equalsIgnoreCase(trimmed)) {
                return i;
            }
        }
        return -1;
    }
}
