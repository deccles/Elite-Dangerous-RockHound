package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.TestEnvironment;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.FsdTargetEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.MaterialTradeEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.event.TouchdownEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EliteLogParser} parseRecord for key event types
 * that affect route/target display and other regression-prone behaviour.
 */
class EliteLogParserTest {

    static {
        TestEnvironment.ensureTestIsolation();
    }

    private EliteLogParser parser;
    private static final String ISO_TS = "2026-02-15T22:47:39Z";

    @BeforeEach
    void setUp() {
        parser = new EliteLogParser();
    }

    @Test
    void parseRecord_status_withDestination_parsesDestinationFields() {
        String json = "{\"event\":\"Status\",\"timestamp\":\"" + ISO_TS + "\",\"Flags\":0,\"Flags2\":0,"
                + "\"Destination\":{\"System\":12345,\"Body\":7,\"Name\":\"Sol\"}}";
        EliteLogEvent event = parser.parseRecord(json);
        assertInstanceOf(StatusEvent.class, event);
        StatusEvent se = (StatusEvent) event;
        assertEquals(Long.valueOf(12345L), se.getDestinationSystem());
        assertEquals(Integer.valueOf(7), se.getDestinationBody());
        assertEquals("Sol", se.getDestinationDisplayName());
    }

    @Test
    void parseRecord_status_noDestination_parsesNullDestination() {
        String json = "{\"event\":\"Status\",\"timestamp\":\"" + ISO_TS + "\",\"Flags\":0,\"Flags2\":0}";
        EliteLogEvent event = parser.parseRecord(json);
        assertInstanceOf(StatusEvent.class, event);
        StatusEvent se = (StatusEvent) event;
        assertNull(se.getDestinationSystem());
        assertNull(se.getDestinationBody());
        assertNull(se.getDestinationName());
    }

    @Test
    void parseRecord_fsdTarget_parsesNameAndAddress() {
        String json = "{\"event\":\"FSDTarget\",\"timestamp\":\"" + ISO_TS + "\",\"Name\":\"Alpha Centauri\",\"SystemAddress\":123456789}";
        EliteLogEvent event = parser.parseRecord(json);
        assertInstanceOf(FsdTargetEvent.class, event);
        FsdTargetEvent fe = (FsdTargetEvent) event;
        assertEquals("Alpha Centauri", fe.getName());
        assertEquals(123456789L, fe.getSystemAddress());
    }

    @Test
    void parseRecord_fsdTarget_emptyName_parsesAsBlank() {
        String json = "{\"event\":\"FSDTarget\",\"timestamp\":\"" + ISO_TS + "\",\"Name\":\"\",\"SystemAddress\":0}";
        EliteLogEvent event = parser.parseRecord(json);
        assertInstanceOf(FsdTargetEvent.class, event);
        FsdTargetEvent fe = (FsdTargetEvent) event;
        assertEquals("", fe.getName());
        assertEquals(0L, fe.getSystemAddress());
    }

    @Test
    void parseRecord_location_parsesStarSystemAndAddress() {
        String json = "{\"event\":\"Location\",\"timestamp\":\"" + ISO_TS + "\",\"StarSystem\":\"Sol\",\"SystemAddress\":12345,"
                + "\"StarPos\":[0.0,0.0,0.0]}";
        EliteLogEvent event = parser.parseRecord(json);
        assertInstanceOf(LocationEvent.class, event);
        LocationEvent le = (LocationEvent) event;
        assertEquals("Sol", le.getStarSystem());
        assertEquals(12345L, le.getSystemAddress());
        assertNotNull(le.getStarPos());
        assertEquals(3, le.getStarPos().length);
    }

    @Test
    void parseRecord_fsdJump_parsesStarSystemAndAddress() {
        String json = "{\"event\":\"FSDJump\",\"timestamp\":\"" + ISO_TS + "\",\"StarSystem\":\"Barnard's Star\",\"SystemAddress\":45678,"
                + "\"StarPos\":[1.1,2.2,3.3]}";
        EliteLogEvent event = parser.parseRecord(json);
        assertInstanceOf(FsdJumpEvent.class, event);
        FsdJumpEvent je = (FsdJumpEvent) event;
        assertEquals("Barnard's Star", je.getStarSystem());
        assertEquals(45678L, je.getSystemAddress());
        assertNotNull(je.getStarPos());
    }

    @Test
    void parseRecord_prospectedAsteroid_parsesMaterialsAndContent() {
        String json = "{\"event\":\"ProspectedAsteroid\",\"timestamp\":\"" + ISO_TS + "\",\"Content\":\"High\","
                + "\"Materials\":[{\"Name\":\"tritium\",\"Proportion\":25.5}]}";
        EliteLogEvent event = parser.parseRecord(json);
        assertInstanceOf(ProspectedAsteroidEvent.class, event);
        ProspectedAsteroidEvent pe = (ProspectedAsteroidEvent) event;
        assertEquals("High", pe.getContent());
        assertNotNull(pe.getMaterials());
        assertEquals(1, pe.getMaterials().size());
        assertEquals("tritium", pe.getMaterials().get(0).getName());
        assertEquals(25.5, pe.getMaterials().get(0).getProportion(), 1e-6);
    }

    @Test
    void parseRecord_navRoute_returnsNavRouteEvent() {
        String json = "{\"event\":\"NavRoute\",\"timestamp\":\"" + ISO_TS + "\"}";
        EliteLogEvent event = parser.parseRecord(json);
        assertInstanceOf(EliteLogEvent.NavRouteEvent.class, event);
    }

    @Test
    void parseRecord_navRouteClear_returnsNavRouteClearEvent() {
        String json = "{\"event\":\"NavRouteClear\",\"timestamp\":\"" + ISO_TS + "\"}";
        EliteLogEvent event = parser.parseRecord(json);
        assertInstanceOf(EliteLogEvent.NavRouteClearEvent.class, event);
    }

    @Test
    void parseRecord_missingEvent_defaultsToStatus() {
        String json = "{\"timestamp\":\"" + ISO_TS + "\",\"Flags\":0}";
        EliteLogEvent event = parser.parseRecord(json);
        assertInstanceOf(StatusEvent.class, event);
    }

    @Test
    void parseRecord_touchdown_playerControlled_parsesLatLonAndBody() {
        String json = "{\"timestamp\":\"2026-06-03T13:57:33Z\",\"event\":\"Touchdown\","
                + "\"PlayerControlled\":true,\"Taxi\":false,\"Multicrew\":false,"
                + "\"StarSystem\":\"Eol Prou VK-N d7-150\",\"SystemAddress\":5162374402371,"
                + "\"Body\":\"Eol Prou VK-N d7-150 A 14 b\",\"BodyID\":68,"
                + "\"OnStation\":false,\"OnPlanet\":true,"
                + "\"Latitude\":27.656620,\"Longitude\":-87.234451}";
        TouchdownEvent td = assertInstanceOf(TouchdownEvent.class, parser.parseRecord(json));
        assertTrue(td.isPlayerControlled());
        assertTrue(td.isOnPlanet());
        assertEquals("Eol Prou VK-N d7-150 A 14 b", td.getBodyName());
        assertEquals(68, td.getBodyId());
        assertEquals(27.656620, td.getLatitude().doubleValue(), 1e-6);
        assertEquals(-87.234451, td.getLongitude().doubleValue(), 1e-6);
    }

    @Test
    void parseRecord_touchdown_srvLanding_playerControlledFalse() {
        String json = "{\"timestamp\":\"2026-06-03T14:19:55Z\",\"event\":\"Touchdown\","
                + "\"PlayerControlled\":false,\"OnPlanet\":true,"
                + "\"Body\":\"Eol Prou VK-N d7-150 A 14 b\",\"BodyID\":68,"
                + "\"Latitude\":27.637203,\"Longitude\":-87.579308}";
        TouchdownEvent td = assertInstanceOf(TouchdownEvent.class, parser.parseRecord(json));
        assertTrue(!td.isPlayerControlled());
    }

    @Test
    void parseRecord_materialTrade_nestedPaidReceived_parsesMaterialSides() {
        String json = "{\"timestamp\":\"" + ISO_TS + "\",\"event\":\"MaterialTrade\","
                + "\"MarketID\":3226579456,\"TraderType\":\"manufactured\","
                + "\"Paid\":{\"Material\":\"protolightalloys\",\"Material_Localised\":\"Proto Light Alloys\","
                + "\"Category\":\"Manufactured\",\"Quantity\":3},"
                + "\"Received\":{\"Material\":\"salvagedalloys\",\"Material_Localised\":\"Salvaged Alloys\","
                + "\"Category\":\"Manufactured\",\"Quantity\":81}}";
        MaterialTradeEvent trade = assertInstanceOf(MaterialTradeEvent.class, parser.parseRecord(json));
        assertEquals("manufactured", trade.getCategory());
        assertEquals("protolightalloys", trade.getPaidName());
        assertEquals("Proto Light Alloys", trade.getPaidNameLocalised());
        assertEquals(3, trade.getPaidCount());
        assertEquals("salvagedalloys", trade.getReceivedName());
        assertEquals("Salvaged Alloys", trade.getReceivedNameLocalised());
        assertEquals(81, trade.getReceivedCount());
    }

    @Test
    void parseRecord_materialTrade_legacyFlatFields_parsesMaterialSides() {
        String json = "{\"timestamp\":\"" + ISO_TS + "\",\"event\":\"MaterialTrade\","
                + "\"Category\":\"Encoded\",\"Paid\":\"filamentcomposites\",\"PaidCount\":3,"
                + "\"Received\":\"militarysupercapacitors\",\"ReceivedCount\":1}";
        MaterialTradeEvent trade = assertInstanceOf(MaterialTradeEvent.class, parser.parseRecord(json));
        assertEquals("Encoded", trade.getCategory());
        assertEquals("filamentcomposites", trade.getPaidName());
        assertEquals(3, trade.getPaidCount());
        assertEquals("militarysupercapacitors", trade.getReceivedName());
        assertEquals(1, trade.getReceivedCount());
    }
}
