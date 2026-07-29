package org.dce.ed.logreader.event;

/** A journal event that awards credits for destroying a ship in combat. */
public interface CombatRewardEvent {

    /** Face-value combat credits awarded by this event. */
    long getCombatReward();
}
