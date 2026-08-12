package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MissionDestinationResolverTest {

    @Test
    void commodityObjective_isAmountAndCommodity() {
        MissionRecord r = new MissionRecord(1L);
        r.setName("Mission_Mining_Boom");
        r.setCommodityLocalised("Bromellite");
        r.setCountRequired(36);
        MissionDestination obj = MissionDestinationResolver.objectiveFor(r);
        assertEquals("36 Bromellite", obj.displayLine());
    }

    @Test
    void commodityTurnIn_usesDestinationStation() {
        MissionRecord r = new MissionRecord(2L);
        r.setName("Mission_Mining_Boom");
        r.setCommodityLocalised("Osmium");
        r.setDestinationSystem("Coeus");
        r.setDestinationStation("Foster Terminal");
        MissionDestination turnIn = MissionDestinationResolver.turnInFor(r);
        assertEquals("Coeus / Foster Terminal", turnIn.displayLine());
        assertEquals("Coeus", turnIn.copyLine());
    }

    @Test
    void courierObjective_isDataNotDestination() {
        MissionRecord r = new MissionRecord(3L);
        r.setName("Mission_Courier");
        r.setDestinationSystem("Tenjin");
        r.setDestinationStation("Balakor's Beacon");
        MissionDestination obj = MissionDestinationResolver.objectiveFor(r);
        MissionDestination turnIn = MissionDestinationResolver.turnInFor(r);
        assertEquals("Data on board", obj.displayLine());
        assertEquals("Data on board", obj.copyLine());
        assertEquals("Tenjin / Balakor's Beacon", turnIn.displayLine());
        assertEquals("Tenjin", turnIn.copyLine());
    }

    @Test
    void courierObjective_usesCommodityWhenPresent() {
        MissionRecord r = new MissionRecord(31L);
        r.setName("Mission_Courier_Boom");
        r.setCommodityLocalised("Data");
        r.setCountRequired(1);
        r.setDestinationSystem("Tenjin");
        assertEquals("1 Data", MissionDestinationResolver.objectiveFor(r).displayLine());
        assertEquals("Tenjin", MissionDestinationResolver.turnInFor(r).displayLine());
    }

    @Test
    void originFor_usesAcceptLocation() {
        MissionRecord r = new MissionRecord(9L);
        r.setName("Mission_Courier");
        r.setOriginSystem("Sol");
        r.setOriginStation("Abraham Lincoln");
        r.setDestinationSystem("Tenjin");
        r.setDestinationStation("Balakor's Beacon");
        assertEquals("Sol / Abraham Lincoln", MissionDestinationResolver.originFor(r).displayLine());
        assertEquals("Tenjin / Balakor's Beacon", MissionDestinationResolver.turnInFor(r).displayLine());
    }

    @Test
    void sourcedMission_originIsEmptyUntilExplicitPurchaseSourceIsSet() {
        MissionRecord r = new MissionRecord(10L);
        r.setName("Mission_Sourced_Boom");
        r.setCommodityLocalised("Gold");
        r.setOriginSystem("Sol");
        r.setOriginStation("Galileo");
        r.setDestinationSystem("Achenar");
        r.setDestinationStation("Dawes Hub");

        assertEquals("—", MissionDestinationResolver.originFor(r).displayLine());

        r.setSourcedFromSystem("Lave");
        r.setSourcedFromStation("Lave Station");
        assertEquals("Lave / Lave Station", MissionDestinationResolver.originFor(r).displayLine());
        assertEquals("Achenar / Dawes Hub", MissionDestinationResolver.turnInFor(r).displayLine());
    }

    @Test
    void collectMission_isCommanderSourcedAndDoesNotReuseAcceptanceStation() {
        MissionRecord r = new MissionRecord(11L);
        r.setName("Mission_Collect_Industrial");
        r.setCommodityLocalised("Gold");
        r.setOriginSystem("Col 285 Sector OK-P a35-2");
        r.setOriginStation("Preuss City");
        r.setDestinationSystem("Col 285 Sector OK-P a35-2");
        r.setDestinationStation("Preuss City");

        assertEquals("—", MissionDestinationResolver.originFor(r).displayLine());
        assertEquals("Col 285 Sector OK-P a35-2 / Preuss City",
                MissionDestinationResolver.turnInFor(r).displayLine());
    }

    @Test
    void passengerObjective_isPassengersNotDestination() {
        MissionRecord r = new MissionRecord(32L);
        r.setName("Mission_PassengerBulk");
        r.setDestinationSystem("Sol");
        r.setDestinationStation("Abraham Lincoln");
        MissionDestination obj = MissionDestinationResolver.objectiveFor(r);
        MissionDestination turnIn = MissionDestinationResolver.turnInFor(r);
        assertEquals("Passengers on board", obj.displayLine());
        assertEquals("Sol / Abraham Lincoln", turnIn.displayLine());
    }

    @Test
    void unknownObjective_isNotDestination() {
        MissionRecord r = new MissionRecord(33L);
        r.setName("Mission_SomethingElse");
        r.setDestinationSystem("Lave");
        assertEquals("Complete mission", MissionDestinationResolver.objectiveFor(r).displayLine());
        assertEquals("Lave", MissionDestinationResolver.turnInFor(r).displayLine());
    }

    @Test
    void combatObjective_fallbackIsNotDestination() {
        MissionRecord r = new MissionRecord(34L);
        r.setName("Mission_Combat");
        r.setDestinationSystem("Nuenets");
        assertEquals("Complete objective", MissionDestinationResolver.objectiveFor(r).displayLine());
        assertEquals("Nuenets", MissionDestinationResolver.turnInFor(r).displayLine());
    }

    @Test
    void settlementDestination_copyLineIsSystemOnly() {
        MissionDestination dest = new MissionDestination("Lave", null, "Lave 2");
        assertEquals("Lave / Lave 2", dest.displayLine());
        assertEquals("Lave", dest.copyLine());
    }

    @Test
    void commodityObjective_copyLineKeepsLabel() {
        MissionRecord r = new MissionRecord(1L);
        r.setName("Mission_Mining_Boom");
        r.setCommodityLocalised("Bromellite");
        r.setCountRequired(36);
        MissionDestination obj = MissionDestinationResolver.objectiveFor(r);
        assertEquals("36 Bromellite", obj.copyLine());
    }

    @Test
    void combatAssassinate_showsNamedTarget() {
        MissionRecord r = new MissionRecord(4L);
        r.setName("Mission_Assassinate_name");
        r.setTarget("Jasper \"Blaze\" Venn");
        r.setKillCount(1);
        r.setDestinationSystem("LHS 3447");
        r.setDestinationStation("Worlidge Terminal");
        MissionDestination obj = MissionDestinationResolver.objectiveFor(r);
        MissionDestination turnIn = MissionDestinationResolver.turnInFor(r);
        assertEquals("Jasper \"Blaze\" Venn", obj.displayLine());
        assertEquals("LHS 3447 / Worlidge Terminal", turnIn.displayLine());
    }

    @Test
    void combatMassacre_showsKillProgress() {
        MissionRecord r = new MissionRecord(5L);
        r.setName("Mission_Massacre");
        r.setKillCount(30);
        r.setKillsCompleted(23);
        r.setTargetTypeLocalised("Pirate");
        r.setTargetFaction("Nuenets Corp.");
        r.setDestinationSystem("Nuenets");
        MissionDestination obj = MissionDestinationResolver.objectiveFor(r);
        assertEquals("23/30 pirates", obj.displayLine());
        assertEquals("Nuenets", MissionDestinationResolver.turnInFor(r).displayLine());
    }

    @Test
    void combatMassacre_redirectedShowsComplete() {
        MissionRecord r = new MissionRecord(6L);
        r.setName("Mission_Massacre");
        r.setKillCount(12);
        r.setKillsCompleted(10);
        r.setRedirected(true);
        r.setTargetTypeLocalised("Pirate");
        assertEquals("12/12 pirates", MissionDestinationResolver.objectiveFor(r).displayLine());
    }

    @Test
    void pluralizeKillNoun_basic() {
        assertEquals("pirates", MissionDestinationResolver.pluralizeKillNoun("Pirate"));
        assertEquals("enemies", MissionDestinationResolver.pluralizeKillNoun("Enemy"));
        assertEquals("ships", MissionDestinationResolver.pluralizeKillNoun("ships"));
    }
}
