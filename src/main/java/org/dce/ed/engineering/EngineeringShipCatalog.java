package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.SetUserShipNameEvent;
import org.dce.ed.logreader.event.StoredShipsEvent;

/**
 * Catalog of ships known from journal Loadout / StoredShips (and goals).
 */
public final class EngineeringShipCatalog {

    private final Map<Long, EngineeringShipRef> byId = new LinkedHashMap<>();

    public synchronized void clear() {
        byId.clear();
    }

    public synchronized void remember(EngineeringShipRef ref) {
        if (ref == null || !ref.isKnown()) {
            return;
        }
        EngineeringShipRef prev = byId.get(ref.getShipId());
        String type = ref.getShipType();
        String ident = ref.getShipIdent();
        boolean nameProvided = !ref.getShipName().isBlank();
        if (prev == null) {
            String name = EngineeringShipRef.extractCustomName(type, ref.getShipName(), ident);
            byId.put(ref.getShipId(), new EngineeringShipRef(ref.getShipId(), type, name, ident));
            return;
        }
        // Prefer newer non-blank type/ident. Custom name: take extracted name when present;
        // if the event carried a name that is only the type/ident, clear previous custom;
        // if the event omitted name, keep previous custom.
        type = !type.isBlank() ? type : prev.getShipType();
        ident = !ident.isBlank() ? ident : prev.getShipIdent();
        String incomingCustom = EngineeringShipRef.extractCustomName(type, ref.getShipName(), ident);
        String name;
        if (!incomingCustom.isEmpty()) {
            name = incomingCustom;
        } else if (nameProvided) {
            name = "";
        } else {
            name = EngineeringShipRef.extractCustomName(type, prev.getShipName(), ident);
        }
        byId.put(ref.getShipId(), new EngineeringShipRef(ref.getShipId(), type, name, ident));
    }

    public synchronized void rememberLoadout(LoadoutEvent loadout) {
        if (loadout == null || loadout.getShipId() < 0) {
            return;
        }
        remember(new EngineeringShipRef(
                loadout.getShipId(),
                loadout.getShip(),
                loadout.getShipName(),
                loadout.getShipIdent()));
    }

    public synchronized void rememberSetUserShipName(SetUserShipNameEvent event) {
        if (event == null || event.getShipId() < 0) {
            return;
        }
        remember(new EngineeringShipRef(
                event.getShipId(),
                event.getShipType(),
                event.getUserShipName(),
                event.getUserShipId()));
    }

    public synchronized void rememberStoredShips(StoredShipsEvent event) {
        if (event == null) {
            return;
        }
        for (StoredShipsEvent.StoredShip ship : event.getAllShips()) {
            if (ship == null || ship.getShipId() < 0) {
                continue;
            }
            String type = !ship.getShipTypeLocalised().isBlank()
                    ? ship.getShipTypeLocalised()
                    : ship.getShipType();
            remember(new EngineeringShipRef(
                    ship.getShipId(),
                    type,
                    ship.getName(),
                    ""));
        }
    }

    public synchronized void rememberGoal(EngineeringGoal goal) {
        if (goal == null || goal.getShipId() < 0) {
            return;
        }
        // Prefer journal Loadout/StoredShips data; goal.shipLabel is display text only.
        if (byId.containsKey(Long.valueOf(goal.getShipId()))) {
            return;
        }
        String label = goal.getShipLabel() != null ? goal.getShipLabel().trim() : "";
        remember(new EngineeringShipRef(goal.getShipId(), "", label, ""));
    }

    public synchronized EngineeringShipRef get(long shipId) {
        return byId.get(shipId);
    }

    /** Label with callsign only when another known ship shares the same base label. */
    public synchronized String displayLabel(EngineeringShipRef ref) {
        return EngineeringShipRef.displayLabelAmong(ref, byId.values());
    }

    public synchronized List<EngineeringShipRef> listSorted() {
        List<EngineeringShipRef> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparing(this::displayLabel, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    public synchronized Collection<EngineeringShipRef> all() {
        return List.copyOf(byId.values());
    }

    /** Seed from journal history (Loadout + StoredShips). */
    public void bootstrapFromJournal(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return;
        }
        try {
            EliteJournalReader reader = new EliteJournalReader(clientKey);
            for (EliteLogEvent event : reader.readAllEvents()) {
                if (event instanceof LoadoutEvent loadout) {
                    rememberLoadout(loadout);
                } else if (event instanceof StoredShipsEvent stored) {
                    rememberStoredShips(stored);
                } else if (event instanceof SetUserShipNameEvent renamed) {
                    rememberSetUserShipName(renamed);
                }
            }
        } catch (Exception ignored) {
            // journal unavailable
        }
    }

    public static EngineeringShipRef fromLoadout(LoadoutEvent loadout) {
        if (loadout == null || loadout.getShipId() < 0) {
            return new EngineeringShipRef(EngineeringShipRef.UNKNOWN_SHIP_ID, "", "", "");
        }
        return new EngineeringShipRef(
                loadout.getShipId(),
                loadout.getShip(),
                loadout.getShipName(),
                loadout.getShipIdent());
    }

    public static String normalizeType(String shipType) {
        return shipType == null ? "" : shipType.trim().toLowerCase(Locale.ROOT);
    }
}
