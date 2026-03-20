package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.dce.ed.RouteTabPanel.RouteEntry;
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
}

