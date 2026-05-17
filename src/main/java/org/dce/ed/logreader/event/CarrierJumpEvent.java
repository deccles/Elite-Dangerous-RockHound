package org.dce.ed.logreader.event;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

public class CarrierJumpEvent extends EliteLogEvent implements IFsdJump {

    public static final class StationEconomy {
        private final String name;
        private final String nameLocalised;
        private final double proportion;

        public StationEconomy(String name, String nameLocalised, double proportion) {
            this.name = name;
            this.nameLocalised = nameLocalised;
            this.proportion = proportion;
        }

        public String getName() {
            return name;
        }

        public String getNameLocalised() {
            return nameLocalised;
        }

        public double getProportion() {
            return proportion;
        }
    }

    private final boolean docked;
    private final boolean onFoot;
    private final String stationName;
    private final String stationType;
    private final long marketId;

    private final String stationFaction;

    private final String stationGovernment;
    private final String stationGovernmentLocalised;

    private final List<String> stationServices;

    private final String stationEconomy;
    private final String stationEconomyLocalised;
    private final List<StationEconomy> stationEconomies;

    private final boolean taxi;
    private final boolean multicrew;

    private final String starSystem;
    private final long systemAddress;
    private final double[] starPos;

    private final String systemAllegiance;
    private final String systemEconomy;
    private final String systemEconomyLocalised;
    private final String systemSecondEconomy;
    private final String systemSecondEconomyLocalised;
    private final String systemGovernment;
    private final String systemGovernmentLocalised;
    private final String systemSecurity;
    private final String systemSecurityLocalised;
    private final long population;

    private final String body;
    private final int bodyId;
    private final String bodyType;

    public CarrierJumpEvent(Instant timestamp,
                            JsonObject json,
                            boolean docked,
                            boolean onFoot,
                            String stationName,
                            String stationType,
                            long marketId,
                            String stationFaction,
                            String stationGovernment,
                            String stationGovernmentLocalised,
                            List<String> stationServices,
                            String stationEconomy,
                            String stationEconomyLocalised,
                            List<StationEconomy> stationEconomies,
                            boolean taxi,
                            boolean multicrew,
                            String starSystem,
                            long systemAddress,
                            double[] starPos,
                            String systemAllegiance,
                            String systemEconomy,
                            String systemEconomyLocalised,
                            String systemSecondEconomy,
                            String systemSecondEconomyLocalised,
                            String systemGovernment,
                            String systemGovernmentLocalised,
                            String systemSecurity,
                            String systemSecurityLocalised,
                            long population,
                            String body,
                            int bodyId,
                            String bodyType) {

        super(timestamp, EliteEventType.CARRIER_JUMP, json);

        this.docked = docked;
        this.onFoot = onFoot;
        this.stationName = stationName;
        this.stationType = stationType;
        this.marketId = marketId;

        this.stationFaction = stationFaction;

        this.stationGovernment = stationGovernment;
        this.stationGovernmentLocalised = stationGovernmentLocalised;

        this.stationServices = stationServices == null ? Collections.emptyList() : stationServices;

        this.stationEconomy = stationEconomy;
        this.stationEconomyLocalised = stationEconomyLocalised;
        this.stationEconomies = stationEconomies == null ? Collections.emptyList() : stationEconomies;

        this.taxi = taxi;
        this.multicrew = multicrew;

        this.starSystem = starSystem;
        this.systemAddress = systemAddress;
        this.starPos = starPos;

        this.systemAllegiance = systemAllegiance;
        this.systemEconomy = systemEconomy;
        this.systemEconomyLocalised = systemEconomyLocalised;
        this.systemSecondEconomy = systemSecondEconomy;
        this.systemSecondEconomyLocalised = systemSecondEconomyLocalised;
        this.systemGovernment = systemGovernment;
        this.systemGovernmentLocalised = systemGovernmentLocalised;
        this.systemSecurity = systemSecurity;
        this.systemSecurityLocalised = systemSecurityLocalised;
        this.population = population;

        this.body = body;
        this.bodyId = bodyId;
        this.bodyType = bodyType;
    }

    public boolean isDocked() {
        return docked;
    }

    public boolean isOnFoot() {
        return onFoot;
    }

    public String getStationName() {
        return stationName;
    }

    public String getStationType() {
        return stationType;
    }

    public long getMarketId() {
        return marketId;
    }

    public String getStationFaction() {
        return stationFaction;
    }

    public String getStationGovernment() {
        return stationGovernment;
    }

    public String getStationGovernmentLocalised() {
        return stationGovernmentLocalised;
    }

    public List<String> getStationServices() {
        return stationServices;
    }

    public String getStationEconomy() {
        return stationEconomy;
    }

    public String getStationEconomyLocalised() {
        return stationEconomyLocalised;
    }

    public List<StationEconomy> getStationEconomies() {
        return stationEconomies;
    }

    public boolean isTaxi() {
        return taxi;
    }

    public boolean isMulticrew() {
        return multicrew;
    }

    public String getStarSystem() {
        return starSystem;
    }

    public long getSystemAddress() {
        return systemAddress;
    }

    public double[] getStarPos() {
        return starPos;
    }

    public String getSystemAllegiance() {
        return systemAllegiance;
    }

    public String getSystemEconomy() {
        return systemEconomy;
    }

    public String getSystemEconomyLocalised() {
        return systemEconomyLocalised;
    }

    public String getSystemSecondEconomy() {
        return systemSecondEconomy;
    }

    public String getSystemSecondEconomyLocalised() {
        return systemSecondEconomyLocalised;
    }

    public String getSystemGovernment() {
        return systemGovernment;
    }

    public String getSystemGovernmentLocalised() {
        return systemGovernmentLocalised;
    }

    public String getSystemSecurity() {
        return systemSecurity;
    }

    public String getSystemSecurityLocalised() {
        return systemSecurityLocalised;
    }

    public long getPopulation() {
        return population;
    }

    public String getBody() {
        return body;
    }

    public int getBodyId() {
        return bodyId;
    }

    public String getBodyType() {
        return bodyType;
    }
}
