package org.dce.ed.mining;

import java.time.Instant;

/**
 * One row of prospector log data (run, asteroid, body, timestamp, material, amounts, core, commander, ship, duds).
 * Sheet/CSV column order: Run, Asteroid, Timestamp, Type, Percentage, Before, After, Actual, Core, Duds, System, Body,
 * Commander, Ship, Comments, Start time, End time (Google Sheets column Q).
 */
public final class ProspectorLogRow {

    private final int run;
    private final String asteroidId;
    private final String fullBodyName;
    private final Instant timestamp;
    private final String material;
    private final double percent;
    private final double beforeAmount;
    private final double afterAmount;
    private final double difference;
    private final String commanderName;
    /** Journal {@code Ship} (e.g. {@code python}); empty if unknown. */
    private final String shipType;
    private final String coreType;
    private final int duds;
    private final Instant runStartTime;
    private final Instant runEndTime;
    /** Google Sheets column Q; optional free-text comments per row. */
    private final String comments;

    /** Constructor for backward compatibility (asteroid "", core "", duds 0). */
    public ProspectorLogRow(int run, String fullBodyName, Instant timestamp, String material,
                           double percent, double beforeAmount, double afterAmount, double difference,
                           String commanderName) {
        this(run, "", fullBodyName, timestamp, material, percent, beforeAmount, afterAmount, difference, commanderName, "", "", 0, null, null);
    }

    public ProspectorLogRow(int run, String asteroidId, String fullBodyName, Instant timestamp, String material,
                           double percent, double beforeAmount, double afterAmount, double difference,
                           String commanderName, String coreType, int duds) {
        this(run, asteroidId, fullBodyName, timestamp, material, percent, beforeAmount, afterAmount, difference, commanderName, "", coreType, duds, null, null);
    }

    public ProspectorLogRow(int run, String asteroidId, String fullBodyName, Instant timestamp, String material,
                           double percent, double beforeAmount, double afterAmount, double difference,
                           String commanderName, String shipType, String coreType, int duds, Instant runStartTime, Instant runEndTime) {
        this(run, asteroidId, fullBodyName, timestamp, material, percent, beforeAmount, afterAmount, difference,
                commanderName, shipType, coreType, duds, runStartTime, runEndTime, "");
    }

    public ProspectorLogRow(int run, String asteroidId, String fullBodyName, Instant timestamp, String material,
                           double percent, double beforeAmount, double afterAmount, double difference,
                           String commanderName, String shipType, String coreType, int duds, Instant runStartTime, Instant runEndTime,
                           String comments) {
        this.run = run;
        this.asteroidId = asteroidId != null ? asteroidId : "";
        this.fullBodyName = fullBodyName != null ? fullBodyName : "";
        this.timestamp = timestamp;
        this.material = material != null ? material : "";
        this.percent = percent;
        this.beforeAmount = beforeAmount;
        this.afterAmount = afterAmount;
        this.difference = difference;
        this.commanderName = commanderName != null ? commanderName : "";
        this.shipType = shipType != null ? shipType : "";
        this.coreType = coreType != null ? coreType : "";
        this.duds = duds;
        this.runStartTime = runStartTime;
        this.runEndTime = runEndTime;
        this.comments = comments != null ? comments : "";
    }

    public int getRun() {
        return run;
    }

    /** Asteroid ID in order: A, B, ..., Z, AA, AB, ... */
    public String getAsteroidId() {
        return asteroidId;
    }

    public String getFullBodyName() {
        return fullBodyName;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getMaterial() {
        return material;
    }

    public double getPercent() {
        return percent;
    }

    public double getBeforeAmount() {
        return beforeAmount;
    }

    public double getAfterAmount() {
        return afterAmount;
    }

    public double getDifference() {
        return difference;
    }

    public String getCommanderName() {
        return commanderName;
    }

    /** Ship model from the journal ({@code LoadGame}/{@code Loadout} {@code Ship} field), or empty. */
    public String getShipType() {
        return shipType;
    }

    /** Core (motherlode) material type if this was a core asteroid, otherwise empty. */
    public String getCoreType() {
        return coreType;
    }

    /** Number of prospector limpets fired (duds) before this one that generated inventory. */
    public int getDuds() {
        return duds;
    }

    /** Run start time (last undock); only set on the first row of the run. */
    public Instant getRunStartTime() {
        return runStartTime;
    }

    /** Run end time (concluding dock); only set on the canonical row after dock. */
    public Instant getRunEndTime() {
        return runEndTime;
    }

    /** Free-text comments (sheet column Q). */
    public String getComments() {
        return comments;
    }

    /** @deprecated Use {@link #getCommanderName()}. */
    public String getEmailAddress() {
        return commanderName;
    }
}
