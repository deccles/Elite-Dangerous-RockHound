package org.dce.ed.exobiology;

import java.util.*;

import org.dce.ed.exobiology.ExobiologyData.AtmosphereType;
import org.dce.ed.exobiology.ExobiologyData.PlanetType;
import org.dce.ed.exobiology.ExobiologyData.SpeciesConstraint;
import org.dce.ed.exobiology.ExobiologyData.SpeciesRule.SpeciesRuleBuilder;

public final class ExobiologyDataConstraints {

    private ExobiologyDataConstraints() {
    }

    /**
     * Generated from rulesets/*.py by generate_exobio_java_snippet.py
     */
    public static void initConstraints(Map<String, SpeciesConstraint> CONSTRAINTS) {
        SpeciesConstraint sc;

        sc = new SpeciesConstraint("Albidum", "Sinuous Tubers", 1514500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.ROCKY)
                .maxOrbitalPeriod(86400.0)
                .tubers(Arrays.asList("Inner S-C Arm B 2", "Inner S-C Arm D", "Trojan Belt"))
                .volcanismAnyOf("major silicate vapour", "major metallic magma")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Aleoida", "Arcus", 7252500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(175.0, 180.0)
                .pressure(0.0161, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Aleoida", "Coronamus", 6284600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(180.0, 190.0)
                .pressure(0.025, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Aleoida", "Gravis", 12934900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(190.0, 197.0)
                .pressure(0.054, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Aleoida", "Laminiae", 3385200, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(152.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("orion-cygnus", "sagittarius-carina"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Aleoida", "Spica", 3385200, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(170.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("outer", "perseus", "scutum-centaurus"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Aureum", "Brain Tree", 1593700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(null, 2.9)
                .temperature(300.0, 500.0)
                .planetTypes(PlanetType.METAL_RICH, PlanetType.HIGH_METAL)
                .guardian(Boolean.TRUE)
                .regions(Arrays.asList("['brain-tree']"))
                .volcanismAnyOf("metallic", "rocky", "silicate")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Acies", 1000000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.255, 0.61)
                .temperature(20.0, 61.0)
                .pressure(null, 0.01)
                .atmospheres(AtmosphereType.NEON)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Alcyoneum", 1658500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.376)
                .temperature(152.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Aurasus", 1000000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.039, 0.608)
                .temperature(145.0, 400.0)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL, PlanetType.ROCKY_ICE)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Bullaris", 1152500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.0245, 0.35)
                .temperature(67.0, 109.0)
                .atmospheres(AtmosphereType.METHANE)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.44, 0.6)
                .temperature(74.0, 141.0)
                .pressure(0.01, 0.05)
                .atmospheres(AtmosphereType.METHANE_RICH)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Cerbrus", 1689800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.042, 0.605)
                .temperature(132.0, 500.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.064)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.064)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.4, 0.5)
                .temperature(190.0, 330.0)
                .atmospheres(AtmosphereType.WATER_RICH)
                .planetTypes(PlanetType.ROCKY_ICE)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Informem", 8418000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.05, 0.6)
                .temperature(42.5, 151.0)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.17, 0.63)
                .temperature(50.0, 90.0)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ICY)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Nebulus", 5289900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.4, 0.55)
                .temperature(20.0, 21.0)
                .pressure(0.067, null)
                .atmospheres(AtmosphereType.HELIUM)
                .planetTypes(PlanetType.ICY)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.4, 0.7)
                .temperature(20.0, 21.0)
                .pressure(0.067, null)
                .atmospheres(AtmosphereType.HELIUM)
                .planetTypes(PlanetType.ROCKY_ICE)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Omentum", 4638900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.045, 0.45)
                .temperature(50.0, null)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.23, 0.45)
                .temperature(80.0, 90.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.ARGON_RICH)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.4, 0.51)
                .temperature(20.0, 21.0)
                .pressure(0.065, null)
                .atmospheres(AtmosphereType.HELIUM)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.0265, 0.0455)
                .temperature(84.0, 108.0)
                .pressure(0.035, null)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.31, 0.6)
                .temperature(20.0, 61.0)
                .pressure(null, 0.0065)
                .atmospheres(AtmosphereType.NEON)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.27, 0.61)
                .temperature(20.0, 93.0)
                .pressure(0.0027, null)
                .atmospheres(AtmosphereType.NEON_RICH)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.2, 0.26)
                .temperature(60.0, 80.0)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.38, 0.45)
                .temperature(190.0, 330.0)
                .pressure(0.07, null)
                .atmospheres(AtmosphereType.WATER_RICH)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Scopulum", 4934500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.15, 0.26)
                .temperature(56.0, 150.0)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .volcanismAnyOf("carbon dioxide", "methane")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.48, 0.51)
                .temperature(20.0, 21.0)
                .pressure(0.075, null)
                .atmospheres(AtmosphereType.HELIUM)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("methane")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.047)
                .temperature(84.0, 110.0)
                .pressure(0.03, null)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("methane")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.61)
                .temperature(20.0, 65.0)
                .pressure(null, 0.008)
                .atmospheres(AtmosphereType.NEON)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .volcanismAnyOf("carbon dioxide", "methane")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.61)
                .temperature(20.0, 65.0)
                .pressure(0.005, null)
                .atmospheres(AtmosphereType.NEON_RICH)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .volcanismAnyOf("carbon dioxide", "methane")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.2, 0.3)
                .temperature(60.0, 70.0)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .volcanismAnyOf("carbon dioxide", "methane")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.27, 0.4)
                .temperature(150.0, 220.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .volcanismAnyOf("carbon dioxide", "methane")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Tela", 1949000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.045, 0.45)
                .temperature(50.0, null)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.24, 0.45)
                .temperature(50.0, 150.0)
                .pressure(null, 0.05)
                .atmospheres(AtmosphereType.ARGON_RICH)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.23)
                .temperature(165.0, 177.0)
                .pressure(0.0025, 0.02)
                .atmospheres(AtmosphereType.AMMONIA)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.45, 0.61)
                .temperature(300.0, null)
                .pressure(0.006, null)
                .atmospheres(AtmosphereType.CO2)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.61)
                .temperature(167.0, null)
                .pressure(0.006, null)
                .atmospheres(AtmosphereType.CO2, AtmosphereType.CO2_RICH)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.61)
                .temperature(20.0, 21.0)
                .pressure(0.067, null)
                .atmospheres(AtmosphereType.HELIUM)
                .planetTypes(PlanetType.ICY)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.026, 0.126)
                .temperature(80.0, 109.0)
                .pressure(0.012, null)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.27, 0.61)
                .temperature(20.0, 95.0)
                .pressure(null, 0.008)
                .atmospheres(AtmosphereType.NEON)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.27, 0.61)
                .temperature(20.0, 95.0)
                .pressure(0.003, null)
                .atmospheres(AtmosphereType.NEON_RICH)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.21, 0.35)
                .temperature(55.0, 80.0)
                .atmospheres(AtmosphereType.NITROGEN)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.23, 0.5)
                .temperature(150.0, 240.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.18, 0.61)
                .temperature(148.0, 550.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.18, 0.61)
                .temperature(300.0, 550.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.5, 0.55)
                .temperature(500.0, 650.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.063)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.315, 0.44)
                .temperature(190.0, 330.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.WATER_RICH)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .volcanism("Any")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Verrata", 3897000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.03, 0.09)
                .temperature(160.0, 180.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.165, 0.33)
                .temperature(57.5, 145.0)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ROCKY_ICE, PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.08)
                .temperature(80.0, 90.0)
                .pressure(null, 0.01)
                .atmospheres(AtmosphereType.ARGON_RICH)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.25, 0.32)
                .temperature(167.0, 240.0)
                .atmospheres(AtmosphereType.CO2, AtmosphereType.CO2_RICH)
                .planetTypes(PlanetType.ROCKY_ICE, PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.49, 0.53)
                .temperature(20.0, 21.0)
                .pressure(0.065, null)
                .atmospheres(AtmosphereType.HELIUM)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.29, 0.61)
                .temperature(20.0, 51.0)
                .pressure(null, 0.075)
                .atmospheres(AtmosphereType.NEON)
                .planetTypes(PlanetType.ROCKY_ICE, PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.43, 0.61)
                .temperature(20.0, 65.0)
                .pressure(0.005, null)
                .atmospheres(AtmosphereType.NEON_RICH)
                .planetTypes(PlanetType.ROCKY_ICE, PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.205, 0.241)
                .temperature(60.0, 80.0)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.24, 0.35)
                .temperature(154.0, 220.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ROCKY_ICE, PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.054)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY)
                .volcanismAnyOf("water")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Vesicula", 1000000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.027, 0.51)
                .temperature(50.0, 245.0)
                .atmospheres(AtmosphereType.ARGON)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Bacterium", "Volu", 7774700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.239, 0.61)
                .temperature(143.5, 246.0)
                .pressure(0.013, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Blatteum", "Bioluminescent Anemone", 1499900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .temperature(220.0, null)
                .planetTypes(PlanetType.METAL_RICH, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("anemone-a"))
                .stars(Arrays.asList("B IV", "B V"))
                .volcanism("Any")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Blatteum", "Sinuous Tubers", 1514500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.METAL_RICH, PlanetType.HIGH_METAL)
                .tubers(Arrays.asList("Arcadian Stream", "Inner Orion Spur", "Inner S-C Arm B 2", "Hawking A"))
                .volcanismAnyOf("=metallic magma volcanism", "=rocky magma volcanism", "major silicate vapour")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Cactoida", "Cortexum", 3667600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(180.0, 197.0)
                .pressure(0.025, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("orion-cygnus"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Cactoida", "Lapis", 2483600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(160.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("sagittarius-carina"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Cactoida", "Peperatis", 2483600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(160.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("scutum-centaurus"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Cactoida", "Pullulanta", 3667600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(180.0, 197.0)
                .pressure(0.025, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("perseus"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Cactoida", "Vermis", 16202800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.265, 0.276)
                .temperature(160.0, 210.0)
                .pressure(null, 0.005)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ROCKY)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanismAnyOf("water")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Caeruleum", "Sinuous Tubers", 1514500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.ROCKY)
                .maxOrbitalPeriod(86400.0)
                .tubers(Arrays.asList("Galactic Center", "Inner S-C Arm D", "Norma Arm A"))
                .volcanismAnyOf("major silicate vapour")
                .build(),
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("empyrean-straits"))
                .volcanismAnyOf("major silicate vapour")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Clypeus", "Lacrimam", 8418000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(190.0, null)
                .pressure(0.054, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY)
                .volcanismAnyOf("water")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Clypeus", "Margaritus", 11873200, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(190.0, 197.0)
                .pressure(0.054, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Clypeus", "Speculumi", 16202800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(190.0, 197.0)
                .pressure(0.055, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .distance(2000.0)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY)
                .distance(2000.0)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY)
                .distance(2000.0)
                .volcanismAnyOf("water")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Concha", "Aureolas", 7774700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(152.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Concha", "Biconcavis", 16777215, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.053, 0.275)
                .temperature(42.0, 52.0)
                .pressure(null, 0.0047)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Concha", "Labiata", 2352400, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(150.0, 200.0)
                .pressure(0.002, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Concha", "Renibus", 4572400, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.045)
                .temperature(176.0, 177.0)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanismAnyOf("silicate", "metallic")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(180.0, null)
                .pressure(0.025, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.15)
                .temperature(78.0, 100.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanismAnyOf("silicate", "metallic")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.65)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.65)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanismAnyOf("water")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Croceum", "Anemone", 1499900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.047, 0.37)
                .temperature(200.0, 440.0)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("anemone-a"))
                .stars(Arrays.asList("B V", "B VI", "A III"))
                .volcanismAnyOf("silicate", "rocky", "metallic")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Crystalline", "Shards", 1628800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(null, 2.0)
                .temperature(null, 273.0)
                .atmospheres(AtmosphereType.NONE, AtmosphereType.ARGON, AtmosphereType.ARGON_RICH, AtmosphereType.CO2, AtmosphereType.CO2_RICH, AtmosphereType.HELIUM, AtmosphereType.METHANE, AtmosphereType.NEON, AtmosphereType.NEON_RICH)
                .bodies(Arrays.asList("Earthlike body", "Ammonia world", "Water world", "Gas giant with water based life", "Gas giant with ammonia based life", "Water giant"))
                .distance(12000.0)
                .regions(Arrays.asList("exterior"))
                .stars(Arrays.asList("A", "F", "G", "K", "MS", "S"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Electricae", "Pluma", 6284600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.276)
                .temperature(50.0, 150.0)
                .atmospheres(AtmosphereType.ARGON, AtmosphereType.ARGON_RICH)
                .planetTypes(PlanetType.ICY)
                .parentStars(Arrays.asList("A", "N", "D", "H", "AeBe"))
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.26, 0.276)
                .temperature(20.0, 70.0)
                .pressure(null, 0.005)
                .atmospheres(AtmosphereType.NEON, AtmosphereType.NEON_RICH)
                .planetTypes(PlanetType.ICY)
                .parentStars(Arrays.asList("A", "N", "D", "H", "AeBe"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Electricae", "Radialem", 6284600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.276)
                .temperature(50.0, 150.0)
                .atmospheres(AtmosphereType.ARGON, AtmosphereType.ARGON_RICH)
                .planetTypes(PlanetType.ICY)
                .nebula("all")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.026, 0.276)
                .temperature(20.0, 70.0)
                .pressure(null, 0.005)
                .atmospheres(AtmosphereType.NEON, AtmosphereType.NEON_RICH)
                .planetTypes(PlanetType.ICY)
                .nebula("all")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fonticulua", "Campestris", 1000000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.027, 0.276)
                .temperature(50.0, 150.0)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fonticulua", "Digitos", 1804100, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.07)
                .temperature(83.0, 109.0)
                .pressure(0.03, null)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fonticulua", "Fluctus", 20000000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.235, 0.276)
                .temperature(143.0, 200.0)
                .pressure(0.012, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ICY)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fonticulua", "Lapida", 3111000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.19, 0.276)
                .temperature(50.0, 81.0)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fonticulua", "Segmentatus", 19010800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.25, 0.276)
                .temperature(50.0, 75.0)
                .pressure(null, 0.006)
                .atmospheres(AtmosphereType.NEON, AtmosphereType.NEON_RICH)
                .planetTypes(PlanetType.ICY)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fonticulua", "Upupam", 5727600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.209, 0.276)
                .temperature(61.0, 125.0)
                .pressure(0.0175, null)
                .atmospheres(AtmosphereType.ARGON_RICH)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Frutexa", "Acus", 7774700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.237)
                .temperature(146.0, 197.0)
                .pressure(0.0029, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("orion-cygnus"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Frutexa", "Collum", 1639800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(132.0, 215.0)
                .pressure(null, 0.004)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ROCKY)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.265, 0.276)
                .temperature(132.0, 135.0)
                .pressure(null, 0.004)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Frutexa", "Fera", 1632500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(146.0, 197.0)
                .pressure(0.003, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("outer"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Frutexa", "Flabellum", 1808900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(152.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("!scutum-centaurus"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Frutexa", "Flammasis", 10326000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(152.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("scutum-centaurus"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Frutexa", "Metallicum", 1632500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(152.0, 176.0)
                .pressure(null, 0.01)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(146.0, 197.0)
                .pressure(0.002, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.05, 0.1)
                .temperature(100.0, 300.0)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.HIGH_METAL)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.07)
                .temperature(null, 400.0)
                .pressure(null, 0.07)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Frutexa", "Sponsae", 5988000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.056)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.056)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY)
                .volcanismAnyOf("water")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fumerola", "Aquatis", 6284600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.028, 0.276)
                .temperature(161.0, 177.0)
                .pressure(0.002, 0.02)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE, PlanetType.ROCKY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.166, 0.276)
                .temperature(57.0, 150.0)
                .atmospheres(AtmosphereType.ARGON, AtmosphereType.ARGON_RICH)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.25, 0.276)
                .temperature(160.0, 180.0)
                .pressure(0.01, 0.03)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(80.0, 100.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ROCKY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.26, 0.276)
                .temperature(20.0, 60.0)
                .atmospheres(AtmosphereType.NEON)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.195, 0.245)
                .temperature(56.0, 80.0)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.23, 0.276)
                .temperature(153.0, 190.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.18, 0.276)
                .temperature(150.0, 270.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE, PlanetType.ROCKY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.06)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY)
                .volcanismAnyOf("water")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fumerola", "Carbosis", 6284600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.168, 0.276)
                .temperature(57.0, 150.0)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .volcanismAnyOf("carbon", "methane")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.047)
                .temperature(84.0, 110.0)
                .pressure(0.03, null)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("methane magma")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.26, 0.276)
                .temperature(40.0, 60.0)
                .atmospheres(AtmosphereType.NEON)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("carbon", "methane")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.2, 0.276)
                .temperature(57.0, 70.0)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("carbon", "methane")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.26, 0.276)
                .temperature(160.0, 180.0)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("carbon")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.185, 0.276)
                .temperature(149.0, 272.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .volcanismAnyOf("carbon", "methane")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(null, 0.276)
                .atmospheres(AtmosphereType.AMMONIA, AtmosphereType.ARGON_RICH, AtmosphereType.CO2_RICH)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("carbon")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fumerola", "Extremus", 16202800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.09)
                .temperature(161.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .volcanismAnyOf("silicate", "metallic", "rocky")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.07, 0.276)
                .temperature(50.0, 121.0)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .volcanismAnyOf("silicate", "metallic", "rocky")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.127)
                .temperature(77.0, 109.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .volcanismAnyOf("silicate", "metallic", "rocky")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.07, 0.276)
                .temperature(54.0, 210.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE)
                .volcanismAnyOf("silicate", "metallic", "rocky")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.05, 0.276)
                .temperature(500.0, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.HIGH_METAL)
                .volcanismAnyOf("silicate", "metallic", "rocky")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fumerola", "Nitris", 7500900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(30.0, 129.0)
                .atmospheres(AtmosphereType.NEON)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.044, 0.276)
                .temperature(50.0, 141.0)
                .atmospheres(AtmosphereType.ARGON, AtmosphereType.ARGON_RICH, AtmosphereType.NEON_RICH)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.025, 0.1)
                .temperature(83.0, 109.0)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.21, 0.276)
                .temperature(60.0, 81.0)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(null, 0.276)
                .temperature(150.0, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.21, 0.276)
                .temperature(160.0, 250.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ICY)
                .volcanismAnyOf("nitrogen", "ammonia")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fungoida", "Bullarum", 3703200, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.058, 0.276)
                .temperature(50.0, 129.0)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.155, 0.276)
                .temperature(50.0, 70.0)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fungoida", "Gelata", 3330300, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.041, 0.276)
                .temperature(160.0, 180.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE)
                .regions(Arrays.asList("!orion-cygnus-core"))
                .volcanismAnyOf("major silicate")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.042, 0.071)
                .temperature(160.0, 180.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE)
                .regions(Arrays.asList("!orion-cygnus-core"))
                .volcanismAnyOf("major silicate")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.042, 0.071)
                .temperature(160.0, 180.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.HIGH_METAL)
                .regions(Arrays.asList("!orion-cygnus-core"))
                .volcanismAnyOf("major rocky")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.041, 0.276)
                .temperature(180.0, null)
                .pressure(0.025, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("!orion-cygnus-core"))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.044, 0.125)
                .temperature(80.0, 110.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("!orion-cygnus-core"))
                .volcanismAnyOf("major silicate", "major metallic")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.039, 0.063)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("!orion-cygnus-core"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fungoida", "Setisis", 1670100, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(152.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.033, 0.276)
                .temperature(68.0, 109.0)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ROCKY_ICE)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.033, 0.276)
                .temperature(67.0, 109.0)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Fungoida", "Stabitis", 2680300, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.045)
                .temperature(172.0, 177.0)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE)
                .regions(Arrays.asList("orion-cygnus"))
                .volcanismAnyOf("silicate")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.2, 0.23)
                .temperature(60.0, 90.0)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ROCKY_ICE)
                .regions(Arrays.asList("orion-cygnus"))
                .volcanismAnyOf("silicate", "rocky")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.3, 0.5)
                .temperature(60.0, 90.0)
                .atmospheres(AtmosphereType.ARGON_RICH)
                .planetTypes(PlanetType.ICY)
                .regions(Arrays.asList("orion-cygnus"))
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.0405, 0.27)
                .temperature(180.0, null)
                .pressure(0.025, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("orion-cygnus"))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.043, 0.126)
                .temperature(78.5, 109.0)
                .pressure(0.012, null)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("orion-cygnus"))
                .volcanismAnyOf("major silicate")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.039, 0.064)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("orion-cygnus"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Gypseeum", "Brain Tree", 1593700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(null, 0.42)
                .temperature(200.0, 400.0)
                .planetTypes(PlanetType.ROCKY)
                .bodies(Arrays.asList("Earthlike body", "Gas giant with water based life", "Water giant"))
                .guardian(Boolean.TRUE)
                .regions(Arrays.asList("['brain-tree']"))
                .volcanismAnyOf("metallic", "rocky", "silicate", "water")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Lindigoticum", "Brain Tree", 1593700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(null, 2.7)
                .temperature(300.0, 500.0)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .bodies(Arrays.asList("Earthlike body", "Gas giant with water based life", "Water giant"))
                .guardian(Boolean.TRUE)
                .regions(Arrays.asList("['brain-tree']"))
                .volcanismAnyOf("rocky", "silicate", "metallic")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Lindigoticum", "Sinuous Tubers", 1514500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.ROCKY)
                .maxOrbitalPeriod(86400.0)
                .tubers(Arrays.asList("Inner S-C Arm A", "Inner S-C Arm C", "Hawking B", "Norma Expanse A", "Odin B"))
                .volcanismAnyOf("major silicate vapour")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Lividum", "Brain Tree", 1593700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(null, 0.5)
                .temperature(300.0, 500.0)
                .planetTypes(PlanetType.ROCKY)
                .guardian(Boolean.TRUE)
                .regions(Arrays.asList("['brain-tree']"))
                .volcanismAnyOf("metallic", "rocky", "silicate", "water")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Luteolum", "Anemone", 1499900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.044, 1.28)
                .temperature(200.0, 440.0)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("anemone-a"))
                .stars(Arrays.asList("B IV", "B V"))
                .volcanismAnyOf("metallic", "silicate", "rocky", "water")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Osseus", "Cornibus", 1483000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.0405, 0.276)
                .temperature(180.0, null)
                .pressure(0.025, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("perseus"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Osseus", "Discus", 12934900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.088)
                .temperature(161.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.2, 0.276)
                .temperature(65.0, 120.0)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ROCKY_ICE)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.026, 0.276)
                .temperature(500.0, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.HIGH_METAL)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.127)
                .temperature(80.0, 110.0)
                .pressure(0.012, null)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ROCKY)
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
            
            /*
             * Manually fixed the max gravity value
             *
             */
                .gravity(0.04, 0.0597)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Osseus", "Fractus", 4027800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(180.0, null)
                .pressure(0.025, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("!perseus"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Osseus", "Pellebantus", 9739000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.0405, 0.276)
                .temperature(191.0, null)
                .pressure(0.057, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("!perseus"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Osseus", "Pumice", 3156300, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.059, 0.276)
                .temperature(50.0, 135.0)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.059, 0.276)
                .temperature(50.0, 135.0)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ROCKY_ICE)
                .volcanismAnyOf("water", "geysers")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.035, 0.276)
                .temperature(60.0, 80.5)
                .pressure(0.03, null)
                .atmospheres(AtmosphereType.ARGON_RICH)
                .planetTypes(PlanetType.ROCKY_ICE)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.033, 0.276)
                .temperature(67.0, 109.0)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.05, 0.276)
                .temperature(42.0, 70.1)
                .atmospheres(AtmosphereType.NITROGEN)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Osseus", "Spiralis", 2404700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(160.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE, PlanetType.HIGH_METAL)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Ostrinum", "Brain Tree", 1593700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .planetTypes(PlanetType.METAL_RICH, PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .guardian(Boolean.TRUE)
                .regions(Arrays.asList("['brain-tree']"))
                .volcanismAnyOf("metallic", "rocky", "silicate")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Prasinum", "Bioluminescent Anemone", 1499900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.036, null)
                .temperature(110.0, 3050.0)
                .planetTypes(PlanetType.METAL_RICH, PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .stars(Arrays.asList("O"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Prasinum", "Sinuous Tubers", 1514500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.METAL_RICH, PlanetType.HIGH_METAL, PlanetType.ROCKY)
                .tubers(Arrays.asList("Inner S-C Arm B 1"))
                .volcanism("Any")
                .build(),
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.METAL_RICH, PlanetType.HIGH_METAL)
                .tubers(Arrays.asList("Inner S-C Arm D", "Norma Expanse B", "Odin B"))
                .volcanismAnyOf("major rocky magma", "major silicate vapour")
                .build(),
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.METAL_RICH, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("empyrean-straits"))
                .volcanismAnyOf("major rocky magma", "major silicate vapour")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Puniceum", "Anemone", 1499900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.17, 2.52)
                .temperature(65.0, 800.0)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .regions(Arrays.asList("anemone-a"))
                .stars(Arrays.asList("O"))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.17, 2.52)
                .temperature(65.0, 800.0)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .regions(Arrays.asList("anemone-a"))
                .stars(Arrays.asList("O"))
                .volcanismAnyOf("carbon dioxide geysers")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Puniceum", "Brain Tree", 1593700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .planetTypes(PlanetType.METAL_RICH, PlanetType.HIGH_METAL)
                .bodies(Arrays.asList("Earthlike body", "Gas giant with water based life", "Water giant"))
                .guardian(Boolean.TRUE)
                .regions(Arrays.asList("['brain-tree']"))
                .volcanism("Any")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Recepta", "Conditivus", 14313700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(150.0, 195.0)
                .atmospheres(AtmosphereType.CO2, AtmosphereType.CO2_RICH)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .atmosphereComponents(Collections.singletonMap("SulphurDioxide", 1.05))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.23, 0.276)
                .temperature(154.0, 175.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ICY)
                .atmosphereComponents(Collections.singletonMap("SulphurDioxide", 1.05))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.23, 0.276)
                .temperature(154.0, 175.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ICY)
                .atmosphereComponents(Collections.singletonMap("SulphurDioxide", 1.05))
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(132.0, 275.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .atmosphereComponents(Collections.singletonMap("SulphurDioxide", 1.05))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Recepta", "Deltahedronix", 16202800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(150.0, 195.0)
                .atmospheres(AtmosphereType.CO2)
                .atmosphereComponents(Collections.singletonMap("SulphurDioxide", 1.05))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(150.0, 195.0)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ICY, PlanetType.ROCKY_ICE)
                .atmosphereComponents(Collections.singletonMap("SulphurDioxide", 1.05))
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(132.0, 272.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .atmosphereComponents(Collections.singletonMap("SulphurDioxide", 1.05))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Recepta", "Umbrux", 12934900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(151.0, 200.0)
                .atmospheres(AtmosphereType.CO2)
                .atmosphereComponents(Collections.singletonMap("SulphurDioxide", 1.05))
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.23, 0.276)
                .temperature(154.0, 175.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ICY)
                .atmosphereComponents(Collections.singletonMap("SulphurDioxide", 1.05))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.23, 0.276)
                .temperature(154.0, 175.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ICY)
                .atmosphereComponents(Collections.singletonMap("SulphurDioxide", 1.05))
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(132.0, 273.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .atmosphereComponents(Collections.singletonMap("SulphurDioxide", 1.05))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Roseum", "Anemone", 1499900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.045, 0.37)
                .temperature(200.0, 440.0)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("anemone-a"))
                .stars(Arrays.asList("B I", "B II", "B III", "B IV"))
                .volcanismAnyOf("silicate", "rocky", "metallic")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Roseum", "Bioluminescent Anemone", 1499900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.036, 4.61)
                .temperature(400.0, null)
                .planetTypes(PlanetType.METAL_RICH, PlanetType.HIGH_METAL)
                .stars(Arrays.asList("B I", "B II", "B III"))
                .volcanism("Any")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Roseum", "Brain Tree", 1593700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .guardian(Boolean.TRUE)
                .regions(Arrays.asList("['brain-tree']"))
                .volcanism("Any")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Roseum", "Sinuous Tubers", 1514500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.HIGH_METAL)
                .tubers(Arrays.asList("Galactic Center", "Odin A", "Ryker B"))
                .volcanismAnyOf("rocky magma")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Rubeum", "Bioluminescent Anemone", 1499900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.036, 4.61)
                .temperature(160.0, 1800.0)
                .planetTypes(PlanetType.METAL_RICH, PlanetType.HIGH_METAL)
                .stars(Arrays.asList("B VI", "A I", "A II", "A III", "N"))
                .volcanism("Any")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Stratum", "Aranaemus", 2448900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(

        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Stratum", "Araneamus", 2448900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.26, 0.57)
                .temperature(165.0, 373.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ROCKY)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Stratum", "Cucumisis", 16202800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.6)
                .temperature(191.0, 371.0)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("sagittarius-carina"))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.44, 0.56)
                .temperature(210.0, 246.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.CO2_RICH)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("sagittarius-carina"))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.4, 0.6)
                .temperature(200.0, 250.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("sagittarius-carina"))
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.26, 0.55)
                .temperature(191.0, 373.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("sagittarius-carina"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Stratum", "Excutitus", 2448900, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.48)
                .temperature(165.0, 190.0)
                .pressure(0.0035, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("orion-cygnus"))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.27, 0.4)
                .temperature(165.0, 190.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("orion-cygnus"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Stratum", "Frigus", 2637500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.043, 0.54)
                .temperature(191.0, 365.0)
                .pressure(0.001, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("perseus-core"))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.45, 0.56)
                .temperature(200.0, 250.0)
                .pressure(0.01, null)
                .atmospheres(AtmosphereType.CO2_RICH)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("perseus-core"))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.29, 0.52)
                .temperature(191.0, 369.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("perseus-core"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Stratum", "Laminamus", 2788300, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.34)
                .temperature(165.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("orion-cygnus"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Stratum", "Limaxus", 1362000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.03, 0.4)
                .temperature(165.0, 190.0)
                .pressure(0.05, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("scutum-centaurus-core"))
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.27, 0.4)
                .temperature(165.0, 190.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("scutum-centaurus-core"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Stratum", "Paleas", 1362000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.35)
                .temperature(165.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.585)
                .temperature(165.0, 395.0)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.43, 0.585)
                .temperature(185.0, 260.0)
                .pressure(0.015, null)
                .atmospheres(AtmosphereType.CO2_RICH)
                .planetTypes(PlanetType.ROCKY)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.056)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.056)
                .pressure(0.065, null)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY)
                .volcanismAnyOf("water")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.39, 0.59)
                .temperature(165.0, 250.0)
                .pressure(0.022, null)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.ROCKY)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Stratum", "Tectonicas", 19010800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.045, 0.38)
                .temperature(165.0, 177.0)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.HIGH_METAL)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.485, 0.54)
                .temperature(167.0, 199.0)
                .atmospheres(AtmosphereType.ARGON, AtmosphereType.ARGON_RICH)
                .planetTypes(PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.045, 0.61)
                .temperature(165.0, 430.0)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.HIGH_METAL)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.035, 0.61)
                .temperature(165.0, 260.0)
                .atmospheres(AtmosphereType.CO2_RICH)
                .planetTypes(PlanetType.HIGH_METAL)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.4, 0.52)
                .temperature(165.0, 246.0)
                .atmospheres(AtmosphereType.OXYGEN)
                .planetTypes(PlanetType.HIGH_METAL)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.29, 0.62)
                .temperature(165.0, 450.0)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.HIGH_METAL)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.045, 0.063)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tubus", "Cavas", 11873200, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.152)
                .temperature(160.0, 197.0)
                .pressure(0.003, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("scutum-centaurus"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tubus", "Compagibus", 7774700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.153)
                .temperature(160.0, 197.0)
                .pressure(0.003, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("sagittarius-carina"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tubus", "Conifer", 2415500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.041, 0.153)
                .temperature(160.0, 197.0)
                .pressure(0.003, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY)
                .regions(Arrays.asList("perseus"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tubus", "Rosarium", 2637500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.153)
                .temperature(160.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tubus", "Sororibus", 5727600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.045, 0.152)
                .temperature(160.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.HIGH_METAL)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.045, 0.152)
                .temperature(160.0, 195.0)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.HIGH_METAL)
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Albata", 3252500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.042, 0.276)
                .temperature(175.0, 180.0)
                .pressure(0.016, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("sagittarius-carina-core-9", "perseus-core", "orion-cygnus-core"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Capillum", 7025800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.22, 0.276)
                .temperature(80.0, 129.0)
                .atmospheres(AtmosphereType.ARGON)
                .planetTypes(PlanetType.ROCKY_ICE)
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.033, 0.276)
                .temperature(80.0, 110.0)
                .atmospheres(AtmosphereType.METHANE)
                .planetTypes(PlanetType.ROCKY, PlanetType.ROCKY_ICE)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Caputus", 3472400, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.041, 0.27)
                .temperature(181.0, 190.0)
                .pressure(0.0275, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("sagittarius-carina-core-9", "perseus-core", "orion-cygnus-core"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Catena", 1766600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(152.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("scutum-centaurus-core"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Cultro", 1766600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(152.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("orion-cygnus"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Divisa", 1766600, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.042, 0.276)
                .temperature(152.0, 177.0)
                .pressure(null, 0.0135)
                .atmospheres(AtmosphereType.AMMONIA)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("perseus-core"))
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Ignis", 1849000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.2)
                .temperature(161.0, 170.0)
                .pressure(0.00289, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("sagittarius-carina-core-9", "perseus-core", "orion-cygnus-core"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Pennata", 5853800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.09)
                .temperature(146.0, 154.0)
                .pressure(0.00289, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("sagittarius-carina-core-9", "perseus-core", "orion-cygnus-core"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Pennatis", 1000000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(147.0, 197.0)
                .pressure(0.00289, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("outer"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Propagito", 1000000, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(145.0, 197.0)
                .pressure(0.00289, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("scutum-centaurus"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Serrati", 4447100, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.042, 0.23)
                .temperature(171.0, 174.0)
                .pressure(0.01, 0.071)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("sagittarius-carina-core-9", "perseus-core", "orion-cygnus-core"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Stigmasis", 19010800, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(132.0, 180.0)
                .pressure(null, 0.01)
                .atmospheres(AtmosphereType.SULPHUR_DIOXIDE)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Triticum", 7774700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.276)
                .temperature(191.0, 197.0)
                .pressure(0.058, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("sagittarius-carina-core-9", "perseus-core", "orion-cygnus-core"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Ventusa", 3227700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.13)
                .temperature(155.0, 160.0)
                .pressure(0.00289, null)
                .atmospheres(AtmosphereType.CO2)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .regions(Arrays.asList("sagittarius-carina-core-9", "perseus-core", "orion-cygnus-core"))
                .volcanism("None")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Tussock", "Virgam", 14313700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.065)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanism("None")
                .build(),
            SpeciesRuleBuilder.create()
                .gravity(0.04, 0.065)
                .atmospheres(AtmosphereType.WATER)
                .planetTypes(PlanetType.ROCKY, PlanetType.HIGH_METAL)
                .volcanismAnyOf("water")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Violaceum", "Sinuous Tubers", 1514500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.METAL_RICH, PlanetType.HIGH_METAL)
                .tubers(Arrays.asList("Arcadian Stream", "Empyrean Straits", "Norma Arm B"))
                .volcanismAnyOf("major rocky magma", "major silicate vapour")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Viride", "Brain Tree", 1593700, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .gravity(null, 0.4)
                .temperature(100.0, 270.0)
                .planetTypes(PlanetType.ROCKY_ICE)
                .bodies(Arrays.asList("Earthlike body", "Gas giant with water based life", "Water giant"))
                .guardian(Boolean.TRUE)
                .regions(Arrays.asList("['brain-tree']"))
                .volcanism("Any")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

        sc = new SpeciesConstraint("Viride", "Sinuous Tubers", 1514500, new ArrayList<>());
        sc.getRules().addAll(Arrays.asList(
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.HIGH_METAL)
                .tubers(Arrays.asList("Inner O-P Conflux", "Izanami", "Ryker A"))
                .volcanismAnyOf("major rocky magma", "major silicate vapour")
                .build(),
            SpeciesRuleBuilder.create()
                .temperature(200.0, 500.0)
                .planetTypes(PlanetType.ROCKY)
                .maxOrbitalPeriod(86400.0)
                .tubers(Arrays.asList("Inner O-P Conflux", "Izanami", "Ryker A"))
                .volcanismAnyOf("major rocky magma", "major silicate vapour")
                .build()
        ));
        CONSTRAINTS.put(sc.key(), sc);

    }
}
