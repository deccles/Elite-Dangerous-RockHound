package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class EngineeringLoadoutCraftPatchTest {

    private static final String LOADOUT_G3 = """
            {"timestamp":"2026-07-23T13:27:52Z","event":"Loadout","Ship":"anaconda","ShipID":7,
             "UnladenMass":1318.813599,"MaxJumpRange":25.373886,
             "Modules":[{
               "Slot":"FrameShiftDrive","Item":"int_hyperdrive_overcharge_size6_class5",
               "Engineering":{
                 "Engineer":"Professor Palin","EngineerID":300220,
                 "BlueprintID":128673692,"BlueprintName":"FSD_LongRange",
                 "Level":3,"Quality":1.0,
                 "ExperimentalEffect":"special_fsd_heavy",
                 "ExperimentalEffect_Localised":"Mass Manager",
                 "Modifiers":[
                   {"Label":"Mass","Value":48.0,"OriginalValue":40.0,"LessIsGood":1},
                   {"Label":"FSDOptimalMass","Value":2808.0,"OriginalValue":2000.0,"LessIsGood":0}
                 ]
               }
             }]}
            """;

    private static EngineerCraftEvent gradeCraftG5() {
        JsonObject raw = JsonParser.parseString("""
                {"timestamp":"2026-07-23T13:36:51Z","event":"EngineerCraft",
                 "Slot":"FrameShiftDrive","Module":"int_hyperdrive_overcharge_size6_class5",
                 "Engineer":"Felicity Farseer","EngineerID":300100,
                 "BlueprintID":128673694,"BlueprintName":"FSD_LongRange",
                 "Level":5,"Quality":1.0,
                 "ExperimentalEffect":"special_fsd_heavy",
                 "ExperimentalEffect_Localised":"Mass Manager",
                 "Modifiers":[
                   {"Label":"Mass","Value":52.0,"OriginalValue":40.0,"LessIsGood":1},
                   {"Label":"FSDOptimalMass","Value":3224.0,"OriginalValue":2000.0,"LessIsGood":0}
                 ]}
                """).getAsJsonObject();
        return new EngineerCraftEvent(
                Instant.parse("2026-07-23T13:36:51Z"), raw, "FrameShiftDrive",
                "int_hyperdrive_overcharge_size6_class5", "Felicity Farseer", 300100,
                "FSD_LongRange", 128673694, 5, 1.0, "", "special_fsd_heavy", "Mass Manager",
                List.of());
    }

    @Test
    void gradeCraftUpdatesLevelModifiersAndUnladenMass() {
        String patched = EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(LOADOUT_G3, gradeCraftG5());
        assertNotNull(patched);
        JsonObject root = JsonParser.parseString(patched).getAsJsonObject();
        assertFalse(root.has("MaxJumpRange"), "stale max jump range should be cleared after FSD craft");
        assertEquals(1322.813599, root.get("UnladenMass").getAsDouble(), 1e-6);

        JsonObject eng = root.getAsJsonArray("Modules").get(0).getAsJsonObject()
                .getAsJsonObject("Engineering");
        assertEquals(5, eng.get("Level").getAsInt());
        assertEquals(3224.0, eng.getAsJsonArray("Modifiers").get(1).getAsJsonObject()
                .get("Value").getAsDouble(), 1e-6);
        assertEquals("Felicity Farseer", eng.get("Engineer").getAsString());
    }

    @Test
    void identicalGradeCraftIsNoOp() {
        String once = EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(LOADOUT_G3, gradeCraftG5());
        assertNotNull(once);
        assertNull(EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(once, gradeCraftG5()));
    }

    @Test
    void shouldPatchRecognizesGradeAndExperimental() {
        assertTrue(EngineeringLoadoutExperimentalPatch.isGradeCraft(gradeCraftG5()));
        assertTrue(EngineeringLoadoutExperimentalPatch.shouldPatchLoadout(gradeCraftG5()));
        assertFalse(EngineeringLoadoutExperimentalPatch.isExperimentalApply(gradeCraftG5()));
    }
}
