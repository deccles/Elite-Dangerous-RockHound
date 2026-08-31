package org.dce.ed.route.pacing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Colonia-area names for EDSM pacing experiments, expanded to match batch demand. */
public final class EdsmPacingSampleSystems {
    private EdsmPacingSampleSystems() {
    }

    public static final List<String> NAMES = List.of(
            "Eol Prou GW-U c3-58",
            "Eol Prou DL-X d1-1065",
            "Eol Prou DL-X d1-4",
            "Eol Prou DL-X d1-1294",
            "Eol Prou NC-T c4-143",
            "Eol Prou NC-T c4-160",
            "Eol Prou DQ-W c2-28",
            "Eol Prou DL-X d1-918",
            "Eol Prou HW-U c3-14",
            "Eol Prou FG-X d1-3",
            "Eol Prou LW-L c8-75",
            "Eol Prou TV-A c15-43",
            "Eol Prou AE-R c5-465",
            "Eol Prou OR-V d2-399",
            "Eol Prou WB-Z c15-363",
            "Eol Prou DL-X d1-1",
            "Eol Prou DL-X d1-2",
            "Eol Prou DL-X d1-3",
            "Eol Prou DL-X d1-5",
            "Eol Prou DL-X d1-6",
            "Eol Prou DL-X d1-7",
            "Eol Prou DL-X d1-8",
            "Eol Prou DL-X d1-9",
            "Eol Prou DL-X d1-10",
            "Eol Prou DL-X d1-11",
            "Eol Prou DL-X d1-12",
            "Eol Prou DL-X d1-13",
            "Eol Prou DL-X d1-14",
            "Eol Prou DL-X d1-15",
            "Eol Prou DL-X d1-16",
            "Eol Prou NC-T c4-1",
            "Eol Prou NC-T c4-2",
            "Eol Prou NC-T c4-3",
            "Eol Prou NC-T c4-4",
            "Eol Prou NC-T c4-5",
            "Eol Prou GW-U c3-1",
            "Eol Prou GW-U c3-2",
            "Eol Prou GW-U c3-3",
            "Eol Prou GW-U c3-4",
            "Eol Prou GW-U c3-5",
            "Eol Prou HW-U c3-1",
            "Eol Prou HW-U c3-2",
            "Eol Prou HW-U c3-3",
            "Eol Prou HW-U c3-4",
            "Eol Prou HW-U c3-5",
            "Eol Prou FG-X d1-1",
            "Eol Prou FG-X d1-2",
            "Eol Prou FG-X d1-4",
            "Eol Prou FG-X d1-5",
            "Eol Prou DQ-W c2-1");

    public static String asEditableText() {
        return asEditableText(NAMES);
    }

    public static String asEditableText(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        return String.join("\n", names);
    }

    public static int demand(List<EdsmPacingExperimentSettings.BatchSpec> batches) {
        int total = 0;
        if (batches == null) {
            return 0;
        }
        for (EdsmPacingExperimentSettings.BatchSpec spec : batches) {
            if (spec != null) {
                total += spec.count() * spec.repeats();
            }
        }
        return total;
    }

    public static List<String> namesFor(int needed) {
        return namesFor(NAMES, needed);
    }

    public static List<String> namesFor(List<String> seed, int needed) {
        if (needed <= 0) {
            return List.of();
        }
        List<String> base = new ArrayList<>();
        if (seed != null) {
            for (String name : seed) {
                if (name != null && !name.isBlank()) {
                    base.add(name.trim());
                }
            }
        }
        if (base.isEmpty()) {
            base.addAll(NAMES);
        }
        if (needed <= base.size()) {
            return List.copyOf(base.subList(0, needed));
        }
        List<String> names = new ArrayList<>(needed);
        Set<String> seen = new LinkedHashSet<>();
        for (String name : base) {
            if (seen.add(name)) {
                names.add(name);
            }
        }
        int extra = 1;
        while (names.size() < needed) {
            String candidate = "Eol Prou PX-X d1-" + extra;
            extra++;
            if (seen.add(candidate)) {
                names.add(candidate);
            }
        }
        return List.copyOf(names);
    }
}
