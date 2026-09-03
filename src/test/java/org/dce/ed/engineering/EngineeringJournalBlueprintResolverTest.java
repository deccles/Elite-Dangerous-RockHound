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

    @Test
    void resolve_mapsWeaponOverchargedUsingModuleItem() {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(
                        "SmallHardpoint2",
                        "hpt_multicannon_turret_small",
                        "Weapon_Overcharged",
                        db);
        assertTrue(resolved.isPresent());
        assertEquals("Multi-cannon", resolved.get().moduleType());
        assertEquals("Overcharged Weapon", resolved.get().blueprintName());
    }

    @Test
    void resolve_mapsShieldBoosterResistiveToResistanceAugmented() {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(
                        "TinyHardpoint1",
                        "hpt_shieldbooster_size0_class5",
                        "ShieldBooster_Resistive",
                        db);
        assertTrue(resolved.isPresent());
        assertEquals("Shield Booster", resolved.get().moduleType());
        assertEquals("Resistance Augmented", resolved.get().blueprintName());
    }

    @Test
    void resolve_mapsSensorLightWeight() {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(
                        "Radar",
                        "int_sensors_size8_class5",
                        "Sensor_LightWeight",
                        db);
        assertTrue(resolved.isPresent());
        assertEquals("Sensors", resolved.get().moduleType());
        assertEquals("Light Weight Scanner", resolved.get().blueprintName());
    }

    @Test
    void resolve_mapsHullReinforcementAdvancedToLightweight() {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(
                        "Slot08_Size4",
                        "int_hullreinforcement_size4_class2",
                        "HullReinforcement_Advanced",
                        db);
        assertTrue(resolved.isPresent());
        assertEquals("Hull Reinforcement Package", resolved.get().moduleType());
        assertEquals("Lightweight Hull Reinforcement", resolved.get().blueprintName());
    }

    @Test
    void displayModuleName_keepsWeaponMountAndHardpointSize() {
        assertEquals("Multi-cannon Gimbal Large",
                EngineeringJournalBlueprintResolver.displayModuleName("hpt_multicannon_gimbal_large"));
        assertEquals("Pulse Laser Fixed Small",
                EngineeringJournalBlueprintResolver.displayModuleName("hpt_pulselaser_fixed_small"));
        assertEquals("Multi-cannon Turret Small",
                EngineeringJournalBlueprintResolver.displayModuleName("hpt_multicannon_turret_small"));
    }

    @Test
    void displayModuleName_stripsInternalSizeAndClass() {
        assertEquals("Life Support",
                EngineeringJournalBlueprintResolver.displayModuleName("int_lifesupport_size4_class1"));
        assertEquals("Thrusters",
                EngineeringJournalBlueprintResolver.displayModuleName("int_engine_size6_class5"));
        assertEquals("Sensors",
                EngineeringJournalBlueprintResolver.displayModuleName("int_sensors_size8_class5"));
        assertEquals("Shield Booster",
                EngineeringJournalBlueprintResolver.displayModuleName("hpt_shieldbooster_size0_class5"));
    }

    @Test
    void resolve_mapsFsdLongRangeToIncreasedFsdRange() {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(
                        "FrameShiftDrive",
                        "int_hyperdrive_overcharge_size5_class5",
                        "FSD_LongRange",
                        db);
        assertTrue(resolved.isPresent());
        assertEquals("Frame Shift Drive", resolved.get().moduleType());
        assertEquals("Increased FSD Range", resolved.get().blueprintName());
    }

    @Test
    void resolve_mapsFrontierAmmoCapacityNamesForUtilityLaunchers() {
        assertResolved(
                "TinyHardpoint8",
                "hpt_heatsinklauncher_turret_tiny",
                "Misc_HeatSinkCapacity",
                "Heat Sink Launcher",
                "Ammo Capacity");
        assertResolved(
                "TinyHardpoint1",
                "hpt_chafflauncher_turret_tiny",
                "Misc_ChaffCapacity",
                "Chaff Launcher",
                "Ammo Capacity");
        assertResolved(
                "TinyHardpoint2",
                "hpt_pointdefence_turret_tiny",
                "Misc_PointDefenseCapacity",
                "Point Defence",
                "Ammo Capacity");
    }

    private static void assertResolved(String slot,
                                       String moduleItem,
                                       String journalName,
                                       String expectedModuleType,
                                       String expectedBlueprintName) {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.resolve(slot, moduleItem, journalName, db);
        assertTrue(resolved.isPresent(), journalName);
        assertEquals(expectedModuleType, resolved.get().moduleType());
        assertEquals(expectedBlueprintName, resolved.get().blueprintName());
    }

    @Test
    void derive_mapsResistiveWhenUnmapped() {
        Optional<EngineeringJournalBlueprintResolver.ResolvedBlueprint> resolved =
                EngineeringJournalBlueprintResolver.deriveFromJournalName(
                        "TinyHardpoint1",
                        "hpt_shieldbooster_size0_class5",
                        "ShieldBooster_Resistive",
                        db);
        assertTrue(resolved.isPresent());
        assertEquals("Resistance Augmented", resolved.get().blueprintName());
    }

    @Test
    void resolve_mapsMercGearFuelScoopAndCargoRack() {
        assertResolved(
                "Slot01_Size6",
                "int_fuelscoop_size6_class5",
                "FuelScoop_ScoopRateEnhanced",
                "Fuel Scoop",
                "Scoop Rate Enhanced");
        assertResolved(
                "Slot04_Size6",
                "int_cargorack_size6_class1",
                "CargoRack_Extended",
                "Cargo Rack",
                "Extended");
        assertResolved(
                "MediumHardpoint1",
                "hpt_beamlaser_fixed_medium",
                "Weapon_PlasmaConversion",
                "Beam Laser",
                "Plasma Conversion");
        assertResolved(
                "TinyHardpoint1",
                "int_detailedsurfacescanner_tiny",
                "SurfaceScanner_LongRangeDetailed",
                "Surface Scanner",
                "Long Range Detailed");
        assertResolved(
                "PowerDistributor",
                "int_powerdistributor_size6_class5",
                "PowerDistributor_Balanced",
                "Power Distributor",
                "Balanced");
        assertResolved(
                "Slot08_Size4",
                "int_modulereinforcement_size4_class5",
                "ModuleReinforcement_HeavyDuty",
                "Module Reinforcement Package",
                "Heavy Duty");
    }
}
