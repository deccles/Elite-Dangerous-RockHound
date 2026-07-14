package org.dce.ed.logreader.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal event: {@code StoredShips} (ships here / remote while docked at a shipyard).
 */
public final class StoredShipsEvent extends EliteLogEvent {

    public static final class StoredShip {
        private final long shipId;
        private final String shipType;
        private final String shipTypeLocalised;
        private final String name;
        private final boolean remote;

        public StoredShip(long shipId,
                          String shipType,
                          String shipTypeLocalised,
                          String name,
                          boolean remote) {
            this.shipId = shipId;
            this.shipType = shipType != null ? shipType : "";
            this.shipTypeLocalised = shipTypeLocalised != null ? shipTypeLocalised : "";
            this.name = name != null ? name : "";
            this.remote = remote;
        }

        public long getShipId() {
            return shipId;
        }

        public String getShipType() {
            return shipType;
        }

        public String getShipTypeLocalised() {
            return shipTypeLocalised;
        }

        public String getName() {
            return name;
        }

        public boolean isRemote() {
            return remote;
        }
    }

    private final String stationName;
    private final List<StoredShip> shipsHere;
    private final List<StoredShip> shipsRemote;

    public StoredShipsEvent(Instant timestamp,
                            JsonObject rawJson,
                            String stationName,
                            List<StoredShip> shipsHere,
                            List<StoredShip> shipsRemote) {
        super(timestamp, EliteEventType.STORED_SHIPS, rawJson);
        this.stationName = stationName != null ? stationName : "";
        this.shipsHere = shipsHere != null ? List.copyOf(shipsHere) : List.of();
        this.shipsRemote = shipsRemote != null ? List.copyOf(shipsRemote) : List.of();
    }

    public String getStationName() {
        return stationName;
    }

    public List<StoredShip> getShipsHere() {
        return shipsHere;
    }

    public List<StoredShip> getShipsRemote() {
        return shipsRemote;
    }

    public List<StoredShip> getAllShips() {
        List<StoredShip> all = new ArrayList<>(shipsHere.size() + shipsRemote.size());
        all.addAll(shipsHere);
        all.addAll(shipsRemote);
        return Collections.unmodifiableList(all);
    }
}
