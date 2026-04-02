package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Unit tests for {@link MiningTabPanel}: buildInventoryTonsFromCargo and csvEscape.
 */
class MiningTabPanelTest {

    @Test
    void csvEscape_null_returnsEmpty() {
        assertEquals("", MiningTabPanel.csvEscape(null));
    }

    @Test
    void csvEscape_noSpecialChars_returnsAsIs() {
        assertEquals("Tritium", MiningTabPanel.csvEscape("Tritium"));
    }

    @Test
    void csvEscape_comma_wrapsInQuotes() {
        assertEquals("\"a,b\"", MiningTabPanel.csvEscape("a,b"));
    }

    @Test
    void csvEscape_quote_escapesAsDoubleQuote() {
        assertEquals("\"\"\"\"", MiningTabPanel.csvEscape("\""));
        assertEquals("\"a\"\"b\"", MiningTabPanel.csvEscape("a\"b"));
    }

    @Test
    void csvEscape_newline_wrapsInQuotes() {
        String result = MiningTabPanel.csvEscape("a\nb");
        assertTrue(result.startsWith("\""));
        assertTrue(result.endsWith("\""));
    }

    @Test
    void buildInventoryTonsFromCargo_null_returnsEmpty() {
        Map<String, Double> out = MiningTabPanel.buildInventoryTonsFromCargo(null, s -> s);
        assertTrue(out.isEmpty());
    }

    @Test
    void buildInventoryTonsFromCargo_emptyInventory_returnsEmpty() {
        JsonObject cargo = new JsonObject();
        cargo.add("Inventory", new JsonArray());
        Map<String, Double> out = MiningTabPanel.buildInventoryTonsFromCargo(cargo, s -> s);
        assertTrue(out.isEmpty());
    }

    @Test
    void buildInventoryTonsFromCargo_singleItem_returnsTons() {
        String json = "{\"Inventory\":[{\"Name\":\"tritium\",\"Count\":100}]}";
        JsonObject cargo = JsonParser.parseString(json).getAsJsonObject();
        Map<String, Double> out = MiningTabPanel.buildInventoryTonsFromCargo(cargo, s -> s);
        assertEquals(1, out.size());
        assertEquals(100.0, out.get("tritium"), 1e-6);
    }

    @Test
    void buildInventoryTonsFromCargo_twoItems_sumsByName() {
        String json = "{\"Inventory\":["
                + "{\"Name\":\"tritium\",\"Count\":50},"
                + "{\"Name\":\"tritium\",\"Count\":30}"
                + "]}";
        JsonObject cargo = JsonParser.parseString(json).getAsJsonObject();
        Map<String, Double> out = MiningTabPanel.buildInventoryTonsFromCargo(cargo, s -> s);
        assertEquals(1, out.size());
        assertEquals(80.0, out.get("tritium"), 1e-6);
    }

    @Test
    void buildInventoryTonsFromCargo_usesNameResolver() {
        String json = "{\"Inventory\":[{\"Name\":\"tritium\",\"Count\":10}]}";
        JsonObject cargo = JsonParser.parseString(json).getAsJsonObject();
        Map<String, Double> out = MiningTabPanel.buildInventoryTonsFromCargo(cargo, String::toUpperCase);
        assertEquals(1, out.size());
        assertEquals(10.0, out.get("TRITIUM"), 1e-6);
    }

    @Test
    void buildInventoryTonsFromCargo_prefersName_Localised() {
        String json = "{\"Inventory\":[{\"Name\":\"$tritium;\",\"Name_Localised\":\"Tritium\",\"Count\":5}]}";
        JsonObject cargo = JsonParser.parseString(json).getAsJsonObject();
        Map<String, Double> out = MiningTabPanel.buildInventoryTonsFromCargo(cargo, s -> s);
        assertEquals(1, out.size());
        assertEquals(5.0, out.get("Tritium"), 1e-6);
    }

    @Test
    void parseAsteroidIdToIndex_examples() {
        assertEquals(0, MiningTabPanel.parseAsteroidIdToIndex("A"));
        assertEquals(1, MiningTabPanel.parseAsteroidIdToIndex("B"));
        assertEquals(25, MiningTabPanel.parseAsteroidIdToIndex("Z"));
        assertEquals(26, MiningTabPanel.parseAsteroidIdToIndex("AA"));
        assertEquals(-1, MiningTabPanel.parseAsteroidIdToIndex(""));
        assertEquals(-1, MiningTabPanel.parseAsteroidIdToIndex("A1"));
    }

    @Test
    void formatAsteroidId_parseAsteroidIdToIndex_roundTrip_throughAA() {
        for (int i = 0; i <= 80; i++) {
            String letter = MiningTabPanel.formatAsteroidId(i);
            assertEquals(i, MiningTabPanel.parseAsteroidIdToIndex(letter), "index " + i);
        }
    }

}
