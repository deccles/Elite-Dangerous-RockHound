package org.dce.ed;

import java.util.List;

/**
 * Catalog of Combat-tab keyboard command buttons (Elite {@code .binds} names + short UI labels).
 */
public final class CombatTabCommands {

    public record Command(String bindName, String label) {
        public Command {
            if (bindName == null || bindName.isBlank()) {
                throw new IllegalArgumentException("bindName");
            }
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("label");
            }
        }
    }

    public static final List<Command> FIGHTER = List.of(
            new Command("OrderDefensiveBehaviour", "Defensive"),
            new Command("OrderAggressiveBehaviour", "Aggressive"),
            new Command("OrderFocusTarget", "Focus"),
            new Command("OrderHoldFire", "Hold Fire"),
            new Command("OrderHoldPosition", "Hold Pos"),
            new Command("OrderFollow", "Follow"),
            new Command("OrderRequestDock", "Dock"),
            new Command("OpenOrders", "Orders"));

    public static final List<Command> TARGETING = List.of(
            new Command("SelectTarget", "Ahead"),
            new Command("CycleNextTarget", "Next"),
            new Command("CyclePreviousTarget", "Prev"),
            new Command("SelectHighestThreat", "Highest Threat"),
            new Command("CycleNextHostileTarget", "Next Hostile"),
            new Command("CyclePreviousHostileTarget", "Prev Hostile"),
            new Command("SelectTargetsTarget", "Target's Target"),
            new Command("CycleNextSubsystem", "Next Subsys"),
            new Command("CyclePreviousSubsystem", "Prev Subsys"),
            new Command("TargetWingman0", "Wingman 1"),
            new Command("TargetWingman1", "Wingman 2"),
            new Command("TargetWingman2", "Wingman 3"));

    private CombatTabCommands() {
    }

    public static String[] fighterBindNames() {
        return bindNames(FIGHTER);
    }

    public static String[] targetingBindNames() {
        return bindNames(TARGETING);
    }

    private static String[] bindNames(List<Command> commands) {
        String[] out = new String[commands.size()];
        for (int i = 0; i < commands.size(); i++) {
            out[i] = commands.get(i).bindName();
        }
        return out;
    }
}
