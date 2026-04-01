package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.dce.ed.route.RouteEntry;
import org.dce.ed.route.RouteNavRouteJson;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Unit tests for Spansh fleet-carrier route parsing via
 * {@link RouteTabPanel#parseSpanshFleetCarrierRouteFromJson(JsonObject)}.
 */
class RouteTabPanelSpanshParserTest {

	@Test
	void parseSpanshFleetCarrierRouteFromJson_emptyRoot_returnsEmptyList() {
		JsonObject root = new JsonObject();
		List<RouteEntry> entries = RouteTabPanel.parseSpanshFleetCarrierRouteFromJson(root);
		assertNotNull(entries);
		assertEquals(0, entries.size());
	}

	@Test
	void parseSpanshFleetCarrierRouteFromJson_twoJumps_preservesOrderAndCoords() {
		String json = "{ \"jumps\": ["
				+ "{ \"id64\": 1, \"name\": \"Sol\", \"x\": 0.0, \"y\": 0.0, \"z\": 0.0 },"
				+ "{ \"id64\": 2, \"name\": \"Alpha Centauri\", \"x\": 3.0, \"y\": 0.0, \"z\": 0.0 }"
				+ "] }";

		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		List<RouteEntry> entries = RouteTabPanel.parseSpanshFleetCarrierRouteFromJson(root);

		assertEquals(2, entries.size());

		RouteEntry a = entries.get(0);
		assertEquals("Sol", a.systemName);
		assertEquals(1L, a.systemAddress);
		assertEquals(0.0, a.x.doubleValue(), 1e-9);
		assertEquals(0.0, a.y.doubleValue(), 1e-9);
		assertEquals(0.0, a.z.doubleValue(), 1e-9);
		assertNull(a.distanceLy);

		RouteEntry b = entries.get(1);
		assertEquals("Alpha Centauri", b.systemName);
		assertEquals(2L, b.systemAddress);
		assertEquals(3.0, b.x.doubleValue(), 1e-9);
		assertEquals(0.0, b.y.doubleValue(), 1e-9);
		assertEquals(0.0, b.z.doubleValue(), 1e-9);
		assertEquals(3.0, b.distanceLy, 1e-9);
	}

	@Test
	void parseSpanshFleetCarrierRouteFromJson_nestedResultJumps_parses() {
		String json = "{ \"result\": { \"jumps\": ["
				+ "{ \"id64\": 1, \"name\": \"Sol\", \"x\": 0.0, \"y\": 0.0, \"z\": 0.0 },"
				+ "{ \"id64\": 2, \"name\": \"Alpha Centauri\", \"x\": 3.0, \"y\": 0.0, \"z\": 0.0 }"
				+ "] } }";

		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		List<RouteEntry> entries = RouteTabPanel.parseSpanshFleetCarrierRouteFromJson(root);

		assertEquals(2, entries.size());
		assertEquals("Sol", entries.get(0).systemName);
		assertEquals(1L, entries.get(0).systemAddress);
		assertEquals("Alpha Centauri", entries.get(1).systemName);
		assertEquals(2L, entries.get(1).systemAddress);
	}

	@Test
	void parseSpanshFleetCarrierRouteFromCsv_twoSystems_preservesOrderAndDistanceColumn() throws IOException {
		String csv = "\"System Name\",\"Distance\",\"Distance Remaining\"\n"
				+ "\"Sol\",\"0\",\"3.0\"\n"
				+ "\"Alpha Centauri\",\"3.0\",\"0\"\n";

		List<RouteEntry> entries = RouteNavRouteJson.parseSpanshFleetCarrierRouteFromCsv(new StringReader(csv));

		assertEquals(2, entries.size());
		assertEquals("Sol", entries.get(0).systemName);
		assertNull(entries.get(0).distanceLy);
		assertEquals("Alpha Centauri", entries.get(1).systemName);
		assertEquals(3.0, entries.get(1).distanceLy, 1e-9);
	}

	@Test
	void parseSpanshFleetCarrierRouteFromCsv_spanshExportStyle_parses() throws IOException {
		String csv = "\"System Name\",\"Distance\",\"Distance Remaining\",\"Tritium in tank\"\n"
				+ "\"Schee Flyi WS-L c23-5229\",\"0\",\"2536.77\",\"\"\n"
				+ "\"Schee Flyi DJ-B c29-6992\",\"499.96\",\"2037.34\",\"\"\n";

		List<RouteEntry> entries = RouteNavRouteJson.parseSpanshFleetCarrierRouteFromCsv(new StringReader(csv));

		assertEquals(2, entries.size());
		assertEquals("Schee Flyi WS-L c23-5229", entries.get(0).systemName);
		assertNull(entries.get(0).distanceLy);
		assertEquals(499.96, entries.get(1).distanceLy, 0.01);
	}

	@Test
	void parseSpanshFleetCarrierRouteFromCsv_noSystemNameHeader_returnsEmpty() throws IOException {
		String csv = "\"Foo\",\"Distance\"\n\"Sol\",\"0\"\n";

		List<RouteEntry> entries = RouteNavRouteJson.parseSpanshFleetCarrierRouteFromCsv(new StringReader(csv));

		assertEquals(0, entries.size());
	}
}

