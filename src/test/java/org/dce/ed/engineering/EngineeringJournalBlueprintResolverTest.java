package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineeringJournalBlueprintResolverTest {

    private static EngineeringDatabase db;

    @BeforeAll
    static void loadDb() {
        db = EngineeringDatabase.getInstance();
    }

    @Test
    void resolve_mapsShieldGeneratorThermicJournalName() {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(
                        "Slot01_Size7",
                        "ShieldGenerator_Thermic",
                        db);
        assertTrue(resolved.isPresent());
        assertEquals("Shield Generator", resolved.get().moduleType());
        assertEquals("Thermal Resistant Shields", resolved.get().blueprintName());
    }

    @Test
    void resolve_derivesShieldGeneratorThermicWhenUnmapped() {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.deriveFromJournalName(
                        "Slot01_Size7",
                        "ShieldGenerator_Thermic",
                        db);
        assertTrue(resolved.isPresent());
        assertEquals("Thermal Resistant Shields", resolved.get().blueprintName());
    }

    @Test
    void resolve_keepsExplicitJournalMapEntry() {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(
                        "PowerDistributor",
                        "PowerDistributor_HighFrequency",
                        db);
        assertTrue(resolved.isPresent());
        assertEquals("Charge Enhanced", resolved.get().blueprintName());
    }
}
