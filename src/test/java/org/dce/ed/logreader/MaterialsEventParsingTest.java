package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.event.MaterialsEvent;
import org.junit.jupiter.api.Test;

class MaterialsEventParsingTest {

    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void parse_materialsEvent_readsAllCategories() {
        String line = "{ \"timestamp\":\"2025-12-02T03:52:03Z\", \"event\":\"Materials\", "
                + "\"Raw\":[{\"Name\":\"phosphorus\",\"Count\":18}], "
                + "\"Manufactured\":[{\"Name\":\"chemicalprocessors\",\"Name_Localised\":\"Chemical Processors\",\"Count\":4}], "
                + "\"Encoded\":[{\"Name\":\"fsdtelemetry\",\"Count\":45}] }";

        var event = parser.parseRecord(line);
        assertInstanceOf(MaterialsEvent.class, event);
        MaterialsEvent me = (MaterialsEvent) event;
        assertEquals(1, me.getRaw().size());
        assertEquals("phosphorus", me.getRaw().get(0).getName());
        assertEquals(18, me.getRaw().get(0).getCount());
        assertEquals(1, me.getManufactured().size());
        assertEquals("chemicalprocessors", me.getManufactured().get(0).getName());
        assertEquals(1, me.getEncoded().size());
        assertEquals(45, me.getEncoded().get(0).getCount());
    }

    @Test
    void engineeringDatabase_hasChargeEnhancedG3() {
        var db = org.dce.ed.engineering.EngineeringDatabase.getInstance();
        assertTrue(db.getAllBlueprints().size() > 500);
        long ceG3 = db.getAllBlueprints().stream()
                .filter(b -> "Charge Enhanced".equals(b.getName()) && b.getGrade() == 3)
                .count();
        assertTrue(ceG3 >= 1);
    }
}
