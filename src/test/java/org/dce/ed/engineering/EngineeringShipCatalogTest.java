package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.ed.ShipTypeNames;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.StoredShipsEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class EngineeringShipCatalogTest {

    @AfterEach
    void clearLearned() {
        ShipTypeNames.clearLearnedForTests();
    }

    @Test
    void displayLabelUsesKnownShipNames() {
        EngineeringShipCatalog catalog = new EngineeringShipCatalog();
        catalog.remember(new EngineeringShipRef(42L, "cobramkiv", "Nightingale", "AB-123"));
        EngineeringShipRef ref = catalog.get(42L);
        assertEquals("Cobra MkIV · Nightingale", catalog.displayLabel(ref));
    }

    @Test
    void storedShipsKeepsInternalTypeAndLearnsLocalised() {
        EngineeringShipCatalog catalog = new EngineeringShipCatalog();
        StoredShipsEvent.StoredShip stored = new StoredShipsEvent.StoredShip(
                7L, "cobramkiv", "Cobra Mk IV", "Nightingale", false);
        JsonObject raw = new JsonObject();
        raw.addProperty("event", "StoredShips");
        catalog.rememberStoredShips(new StoredShipsEvent(
                Instant.parse("2020-01-01T00:00:00Z"),
                raw,
                "Jameson Memorial",
                List.of(stored),
                List.of()));

        EngineeringShipRef ref = catalog.get(7L);
        assertEquals("cobramkiv", ref.getShipType());
        assertEquals("Cobra Mk IV · Nightingale", catalog.displayLabel(ref));
    }

    @Test
    void loadoutDoesNotClobberInternalWithPriorLocalisedType() {
        EngineeringShipCatalog catalog = new EngineeringShipCatalog();
        // Legacy mistake: localised string stored as type.
        catalog.remember(new EngineeringShipRef(9L, "Cobra Mk IV", "Nightingale", ""));

        JsonObject raw = new JsonObject();
        raw.addProperty("event", "Loadout");
        raw.addProperty("Ship", "cobramkiv");
        raw.addProperty("ShipID", 9);
        catalog.rememberLoadout(new LoadoutEvent(
                Instant.parse("2020-01-01T00:00:00Z"),
                raw,
                "cobramkiv",
                9,
                "Nightingale",
                "AB-123",
                0L,
                0L,
                1.0,
                200.0,
                16,
                30.0,
                null,
                0L,
                List.of()));

        EngineeringShipRef ref = catalog.get(9L);
        assertEquals("cobramkiv", ref.getShipType());
        assertTrue(catalog.displayLabel(ref).startsWith("Cobra MkIV"));
    }
}
