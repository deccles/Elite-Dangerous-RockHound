package org.dce.ed;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.FactionKillBondEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.event.UnderAttackEvent;

/** Classifies journal activity that can automatically reveal the Combat tab. */
final class CombatAutoTabLogic {
    private boolean beingInterdicted;

    boolean shouldSwitch(EliteLogEvent event, boolean attackedEnabled, boolean rewardEnabled) {
        if (event instanceof StatusEvent status) {
            boolean nowBeingInterdicted = status.getDecodedFlags().beingInterdicted;
            boolean enteredInterdiction = nowBeingInterdicted && !beingInterdicted;
            beingInterdicted = nowBeingInterdicted;
            return attackedEnabled && enteredInterdiction;
        }
        if (attackedEnabled && event instanceof UnderAttackEvent) {
            return true;
        }
        return rewardEnabled && (event instanceof BountyEvent || event instanceof FactionKillBondEvent);
    }
}
