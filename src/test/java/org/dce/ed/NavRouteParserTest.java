package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.dce.ed.route.RouteEntry;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Unit tests for NavRoute.json parsing via {@link RouteTabPanel#parseNavRouteFromJson(JsonObject)}.
 */
class NavRouteParserTest {

    @Test
    void parseNavRouteFromJson_emptyRoot_returnsEmptyList() {
        JsonObject root = new JsonObject();
        List<RouteEntry> entries = RouteTabPanel.parseNavRouteFromJson(root);
        assertEquals(0, entries.size());
    }

    @Test
    void parseNavRouteFromJson_null_returnsEmptyList() {
        List<RouteEntry> entries = RouteTabPanel.parseNavRouteFromJson(null);
        assertEquals(0, entries.size());
    }

    @Test
    void parseNavRouteFromJson_twoSystems_computesDistance() {
        String json = "{ \"Route\": ["
                + "  { \"StarSystem\": \"Sol\", \"SystemAddress\": 10477373803, \"StarClass\": \"G\", \"StarPos\": [0.0, 0.0, 0.0] },"
                + "  { \"StarSystem\": \"Alpha Centauri\", \"SystemAddress\": 7267754896994, \"StarClass\": \"G\", \"StarPos\": [3.03125, -0.09375, 3.15625] }"
                + "]}";
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<RouteEntry> entries = RouteTabPanel.parseNavRouteFromJson(root);
        assertEquals(2, entries.size());
        assertEquals("Sol", entries.get(0).systemName);
        assertEquals(10477373803L, entries.get(0).systemAddress);
        assertNull(entries.get(0).distanceLy);
        assertEquals("Alpha Centauri", entries.get(1).systemName);
        assertEquals(7267754896994L, entries.get(1).systemAddress);
        assertNotNull(entries.get(1).distanceLy);
        // Distance Sol -> Alpha Centauri approx 4.37 Ly
        double dist = entries.get(1).distanceLy;
        assertEquals(4.37, dist, 0.1);
    }

    @Test
    void parseNavRouteFromJson_threeSystems_computesLegDistances() {
        JsonObject root = new JsonObject();
        JsonArray route = new JsonArray();
        JsonObject a = new JsonObject();
        a.addProperty("StarSystem", "A");
        a.addProperty("SystemAddress", 100);
        a.add("StarPos", array(0.0, 0.0, 0.0));
        route.add(a);
        JsonObject b = new JsonObject();
        b.addProperty("StarSystem", "B");
        b.addProperty("SystemAddress", 200);
        b.add("StarPos", array(1.0, 0.0, 0.0));
        route.add(b);
        JsonObject c = new JsonObject();
        c.addProperty("StarSystem", "C");
        c.addProperty("SystemAddress", 300);
        c.add("StarPos", array(1.0, 1.0, 0.0));
        route.add(c);
        root.add("Route", route);

        List<RouteEntry> entries = RouteTabPanel.parseNavRouteFromJson(root);
        assertEquals(3, entries.size());
        assertNull(entries.get(0).distanceLy);
        assertEquals(1.0, entries.get(1).distanceLy, 1e-6);
        assertEquals(1.0, entries.get(2).distanceLy, 1e-6);
    }

    private static JsonArray array(double a, double b, double c) {
        JsonArray arr = new JsonArray();
        arr.add(a);
        arr.add(b);
        arr.add(c);
        return arr;
    }
}
