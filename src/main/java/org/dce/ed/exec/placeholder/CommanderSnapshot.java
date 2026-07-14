package org.dce.ed.exec.placeholder;

import org.dce.ed.logreader.event.FsdTargetEvent;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.SetUserShipNameEvent;
import org.dce.ed.logreader.event.StatusEvent;

/** Last-known commander, ship, and high-frequency Status fields for exec placeholders. */
public final class CommanderSnapshot {

    private volatile String commander;
    private volatile String gameMode;
    private volatile String shipType;
    private volatile String shipName;
    private volatile String shipIdent;
    private volatile int shipId = -1;
    private volatile long creditsFromLoadGame;
    private volatile double shipFuelCapacity;

    private volatile long balance;
    private volatile double cargo;
    private volatile double shipFuel;
    private volatile String legalState;
    private volatile String bodyName;

    private volatile String statusDestSystem;
    private volatile String statusDestName;

    private volatile String fsdTarget;
    private volatile Integer fsdRemainingJumps;

    public void updateFromLoadGame(LoadGameEvent e) {
        if (e == null) {
            return;
        }
        commander = blankToNull(e.getCommander());
        gameMode = blankToNull(e.getGameMode());
        shipType = blankToNull(e.getShip());
        shipName = blankToNull(e.getShipName());
        shipIdent = blankToNull(e.getShipIdent());
        shipId = e.getShipId();
        creditsFromLoadGame = e.getCredits();
        shipFuelCapacity = e.getFuelCapacity();
        shipFuel = e.getFuelLevel();
    }

    public void updateFromLoadout(LoadoutEvent e) {
        if (e == null) {
            return;
        }
        shipType = blankToNull(e.getShip());
        shipName = blankToNull(e.getShipName());
        shipIdent = blankToNull(e.getShipIdent());
        shipId = e.getShipId();
    }

    public void updateFromSetUserShipName(SetUserShipNameEvent e) {
        if (e == null || e.getShipId() < 0) {
            return;
        }
        if (shipId >= 0 && (long) shipId != e.getShipId()) {
            return;
        }
        shipId = (int) e.getShipId();
        if (!e.getShipType().isBlank()) {
            shipType = blankToNull(e.getShipType());
        }
        shipName = blankToNull(e.getUserShipName());
        shipIdent = blankToNull(e.getUserShipId());
    }

    public void updateFromStatus(StatusEvent e) {
        if (e == null) {
            return;
        }
        balance = e.getBalance();
        cargo = e.getCargo();
        shipFuel = e.getFuelMain();
        legalState = blankToNull(e.getLegalState());
        bodyName = blankToNull(e.getBodyName());
        Long destSys = e.getDestinationSystem();
        statusDestSystem = destSys != null ? Long.toString(destSys) : blankToNull(e.getDestinationName());
        statusDestName = blankToNull(e.getDestinationDisplayName());
        if (statusDestName == null) {
            statusDestName = blankToNull(e.getDestinationName());
        }
    }

    public void updateFromFsdTarget(FsdTargetEvent e) {
        if (e == null) {
            return;
        }
        fsdTarget = blankToNull(e.getName());
        fsdRemainingJumps = e.getRemainingJumpsInRoute();
    }

    public String getCommander() {
        return commander;
    }

    public String getGameMode() {
        return gameMode;
    }

    public String getShipType() {
        return shipType;
    }

    public String getShipName() {
        return shipName;
    }

    public String getShipIdent() {
        return shipIdent;
    }

    public int getShipId() {
        return shipId;
    }

    public long getCreditsFromLoadGame() {
        return creditsFromLoadGame;
    }

    public double getShipFuelCapacity() {
        return shipFuelCapacity;
    }

    public long getBalance() {
        return balance;
    }

    public double getCargo() {
        return cargo;
    }

    public double getShipFuel() {
        return shipFuel;
    }

    public String getLegalState() {
        return legalState;
    }

    public String getBodyName() {
        return bodyName;
    }

    public String getStatusDestSystem() {
        return statusDestSystem;
    }

    public String getStatusDestName() {
        return statusDestName;
    }

    public String getFsdTarget() {
        return fsdTarget;
    }

    public Integer getFsdRemainingJumps() {
        return fsdRemainingJumps;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
