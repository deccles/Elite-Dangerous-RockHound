package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaterialTradeRateCalculatorTest {

    private static EngineeringMaterial mat(String key, String type, String subtype, int grade) {
        return new EngineeringMaterial(key, key, type, subtype, grade);
    }

    @Test
    void sameGroup_upTwoGrades_is36to1() {
        EngineeringMaterial g1 = mat("a", "Encoded", "EncodedFirmware", 1);
        EngineeringMaterial g3 = mat("b", "Encoded", "EncodedFirmware", 3);
        assertEquals(36, MaterialTradeRateCalculator.inputPerOneOutput(g1, g3));
    }

    @Test
    void crossCategory_g3ToG2_is2to1() {
        EngineeringMaterial emissionG3 = mat("em", "Encoded", "EmissionData", 3);
        EngineeringMaterial firmwareG2 = mat("fw", "Encoded", "EncodedFirmware", 2);
        assertEquals(2, MaterialTradeRateCalculator.inputPerOneOutput(emissionG3, firmwareG2));
    }

    @Test
    void differentTypes_notTradable() {
        EngineeringMaterial raw = mat("phosphorus", "Raw", "Category1", 1);
        EngineeringMaterial enc = mat("fw", "Encoded", "EncodedFirmware", 1);
        assertEquals(Integer.MAX_VALUE, MaterialTradeRateCalculator.inputPerOneOutput(raw, enc));
    }

    @Test
    void crossCategory_g5WakeToG3Firmware_is216to1() {
        EngineeringMaterial dmwe = mat("dataminedwakeexceptions", "Encoded", "WakeScans", 5);
        EngineeringMaterial firmware = mat("crackedindustrialfirmware", "Encoded", "EncodedFirmware", 3);
        assertEquals(216, MaterialTradeRateCalculator.inputPerOneOutput(dmwe, firmware));
    }

    @Test
    void crossCategory_g3EncryptionToG5Wake_is216to1() {
        EngineeringMaterial keys = mat("opensymmetrickeys", "Encoded", "EncryptionFiles", 3);
        EngineeringMaterial dmwe = mat("dataminedwakeexceptions", "Encoded", "WakeScans", 5);
        assertEquals(216, MaterialTradeRateCalculator.inputPerOneOutput(keys, dmwe));
    }

    @Test
    void crossCategory_g4WakeToG3Firmware_is36to1() {
        EngineeringMaterial eht = mat("eccentrichyperspacetrajectories", "Encoded", "WakeScans", 4);
        EngineeringMaterial cif = mat("crackedindustrialfirmware", "Encoded", "EncodedFirmware", 3);
        assertEquals(36, MaterialTradeRateCalculator.inputPerOneOutput(eht, cif));
    }

    @Test
    void guardianMaterials_notTradableAtMaterialTrader() {
        EngineeringMaterial guardian = mat("guardian_sentinel_weaponparts", "Manufactured", "GuardianRuinsActive", 3);
        EngineeringMaterial hybrid = mat("hybridcapacitors", "Manufactured", "Capacitors", 3);
        assertEquals(Integer.MAX_VALUE, MaterialTradeRateCalculator.inputPerOneOutput(guardian, hybrid));
        assertEquals(Integer.MAX_VALUE, MaterialTradeRateCalculator.inputPerOneOutput(hybrid, guardian));
    }

    @Test
    void sameGroup_tradeDown_g3ToG2_isOneInputForThreeOutputs() {
        EngineeringMaterial pa = mat("precipitatedalloys", "Manufactured", "Thermic", 3);
        EngineeringMaterial hrc = mat("heatresistantceramics", "Manufactured", "Thermic", 2);
        assertEquals(3, MaterialTradeRateCalculator.outputPerOneInput(pa, hrc));
        assertEquals(1, MaterialTradeRateCalculator.inputPerOneOutput(pa, hrc));

        MaterialTradeRateCalculator.Exchange exchange =
                MaterialTradeRateCalculator.planExchange(pa, hrc, 10, 3).orElseThrow();
        assertEquals(1, exchange.getFromCount());
        assertEquals(3, exchange.getToCount());
    }
}
