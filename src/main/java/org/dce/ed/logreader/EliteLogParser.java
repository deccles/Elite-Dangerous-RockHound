package org.dce.ed.logreader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.logreader.EliteLogEvent.GenericEvent;
import org.dce.ed.logreader.EliteLogEvent.NavRouteClearEvent;
import org.dce.ed.logreader.EliteLogEvent.NavRouteEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.CommanderEvent;
import org.dce.ed.logreader.event.FileheaderEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.FsdTargetEvent;
import org.dce.ed.logreader.event.FssAllBodiesFoundEvent;
import org.dce.ed.logreader.event.FssBodySignalsEvent;
import org.dce.ed.logreader.event.FssDiscoveryScanEvent;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.ReceiveTextEvent;
import org.dce.ed.logreader.event.SaasignalsFoundEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.logreader.event.StartJumpEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.event.SupercruiseExitEvent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Parses individual Elite Dangerous journal JSON records into strongly-typed events.
 */
public class EliteLogParser {

    private final Gson gson = new Gson(); // kept for future use if needed

    /**
     * Parses Elite's {@code Status.json} (full file, not a journal line) into a {@link StatusEvent}.
     */
    public StatusEvent parseStatusJsonFile(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonObject obj = JsonParser.parseString(json.trim()).getAsJsonObject();
        Instant ts = Instant.EPOCH;
        if (obj.has("timestamp") && !obj.get("timestamp").isJsonNull()) {
            try {
                ts = Instant.parse(obj.get("timestamp").getAsString());
            } catch (Exception ignored) {
            }
        }
        return parseStatus(ts, obj);
    }

    public EliteLogEvent parseRecord(String jsonLine) {
        JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();

        String eventName = obj.has("event") ? obj.get("event").getAsString() : "Status";
        EliteEventType type = EliteEventType.fromJournalName(eventName);
        Instant ts = Instant.parse(obj.get("timestamp").getAsString());

        switch (type) {
            case FILEHEADER:
                return parseFileheader(ts, obj);
            case COMMANDER:
                return parseCommander(ts, obj);
            case LOAD_GAME:
                return parseLoadGame(ts, obj);
            case LOADOUT:
                return parseLoadout(ts, obj);
            case LOCATION:
                return parseLocation(ts, obj);
            case START_JUMP:
                return parseStartJump(ts, obj);
            case UNDOCKED:
                return new EliteLogEvent.GenericEvent(ts, type, obj);
            case DOCKED:
                return new EliteLogEvent.GenericEvent(ts, type, obj);
            case FSD_JUMP:
                return parseFsdJump(ts, obj);
            case FSD_TARGET:
                return parseFsdTarget(ts, obj);
            case SAASIGNALS_FOUND:
                return parseSaaSignalsFound(ts, obj);

            // NEW: system/bodies-related events
            case SCAN:
                return parseScan(ts, obj);
            case SCAN_ORGANIC:
            	return parseScanOrganic(ts, obj);
            case FSS_DISCOVERY_SCAN:
                return parseFssDiscoveryScan(ts, obj);
            case FSS_ALL_BODIES_FOUND:
                return new FssAllBodiesFoundEvent(
                    ts,
                    obj,
                    obj.has("SystemName") ? obj.get("SystemName").getAsString() : null,
                    		obj.has("SystemAddress") ? obj.get("SystemAddress").getAsLong() : 0L,
                    				obj.has("Count") ? obj.get("Count").getAsInt() : 0
                );
            case FSS_BODY_SIGNAL_DISCOVERED:
                return parseFssBodySignals(ts, obj);

            case NAV_ROUTE:
                return new NavRouteEvent(ts, obj);
            case NAV_ROUTE_CLEAR:
                return new NavRouteClearEvent(ts, obj);
            case RECEIVE_TEXT:
                return parseReceiveText(ts, obj);
            case STATUS:
                return parseStatus(ts, obj);
                
            case CARRIER_LOCATION:
                return parseCarrierLocation(ts, obj);

            case CARRIER_JUMP:
                return parseCarrierJump(ts, obj);
                
            case CARRIER_JUMP_REQUEST:
                return parseCarrierJumpRequest(ts, obj);
            
            case CARRIER_JUMP_CANCELLED:
                return new GenericEvent(ts, type, obj);
            case SUPERCRUISE_EXIT:
                return parseSupercruiseExit(ts, obj);

            case PROSPECTED_ASTEROID:
                return parseProspectedAsteroid(ts, obj);
default:
                // For everything else, fall back to generic event.
                return new GenericEvent(ts, type, obj);
        }
    }

    private ProspectedAsteroidEvent parseProspectedAsteroid(Instant ts, JsonObject obj) {
        String motherlode = getString(obj, "MotherlodeMaterial");
        String content = getString(obj, "Content");

        List<ProspectedAsteroidEvent.MaterialProportion> materials = new ArrayList<>();
        if (obj.has("Materials") && obj.get("Materials").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("Materials");
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject m = el.getAsJsonObject();
                String name = getString(m, "Name");
                double proportion = (m.has("Proportion") && !m.get("Proportion").isJsonNull())
                        ? m.get("Proportion").getAsDouble()
                        : 0.0;
                if (name != null && !name.isBlank()) {
                    materials.add(new ProspectedAsteroidEvent.MaterialProportion(name, proportion));
                }
            }
        }
        return new ProspectedAsteroidEvent(ts, obj, materials, motherlode, content);
    }
    private SupercruiseExitEvent parseSupercruiseExit(Instant ts, JsonObject obj) {
        boolean taxi = getBoolean(obj, "Taxi", false);
        boolean multicrew = getBoolean(obj, "Multicrew", false);
        String starSystem = getString(obj, "StarSystem");
        long systemAddress = getLong(obj, "SystemAddress");
        String body = getString(obj, "Body");
        int bodyId = (int) getLong(obj, "BodyID");
        String bodyType = getString(obj, "BodyType");

        return new SupercruiseExitEvent(ts,
                                       obj,
                                       taxi,
                                       multicrew,
                                       starSystem,
                                       systemAddress,
                                       body,
                                       bodyId,
                                       bodyType);
    }

    private CarrierJumpRequestEvent parseCarrierJumpRequest(Instant ts, JsonObject obj) {
        String carrierType = getString(obj, "CarrierType");
        long carrierId = getLong(obj, "CarrierID");
        String systemName = getString(obj, "SystemName");
        String body = getString(obj, "Body");
        long systemAddress = getLong(obj, "SystemAddress");
        int bodyId = (int) getLong(obj, "BodyID");

        Instant departureTime = null;
        String departureTimeStr = getString(obj, "DepartureTime");
        if (departureTimeStr != null) {
            departureTime = Instant.parse(departureTimeStr);
        }

        return new CarrierJumpRequestEvent(ts,
                                          obj,
                                          carrierType,
                                          carrierId,
                                          systemName,
                                          body,
                                          systemAddress,
                                          bodyId,
                                          departureTime);
    }

    
    private FileheaderEvent parseFileheader(Instant ts, JsonObject obj) {
        int part = obj.has("part") ? obj.get("part").getAsInt() : 0;
        String language = obj.has("language") ? obj.get("language").getAsString() : null;
        boolean odyssey = obj.has("Odyssey") && obj.get("Odyssey").getAsBoolean();
        String gameVersion = obj.has("gameversion") ? obj.get("gameversion").getAsString() : null;
        String build = obj.has("build") ? obj.get("build").getAsString() : null;

        return new FileheaderEvent(ts, obj, part, language, odyssey, gameVersion, build);
    }

    private CommanderEvent parseCommander(Instant ts, JsonObject obj) {
        String fid = obj.has("FID") ? obj.get("FID").getAsString() : null;
        String name = obj.has("Name") ? obj.get("Name").getAsString() : null;
        return new CommanderEvent(ts, obj, fid, name);
    }

    private LoadGameEvent parseLoadGame(Instant ts, JsonObject obj) {
        String commander = getString(obj, "Commander");
        String fid = getString(obj, "FID");
        String ship = getString(obj, "Ship");
        int shipId = obj.has("ShipID") ? obj.get("ShipID").getAsInt() : -1;
        String shipName = getString(obj, "ShipName");
        String shipIdent = getString(obj, "ShipIdent");
        double fuelLevel = obj.has("FuelLevel") ? obj.get("FuelLevel").getAsDouble() : 0.0;
        double fuelCapacity = obj.has("FuelCapacity") ? obj.get("FuelCapacity").getAsDouble() : 0.0;
        String gameMode = getString(obj, "GameMode");
        long credits = obj.has("Credits") ? obj.get("Credits").getAsLong() : 0L;

        return new LoadGameEvent(
                ts, obj,
                commander, fid,
                ship, shipId,
                shipName, shipIdent,
                fuelLevel, fuelCapacity,
                gameMode, credits
        );
    }

    

    private LoadoutEvent parseLoadout(Instant ts, JsonObject obj) {
        String ship = getString(obj, "Ship");
        int shipId = getInt(obj, "ShipID", -1);
        String shipName = getString(obj, "ShipName");
        String shipIdent = getString(obj, "ShipIdent");

        long hullValue = getLong(obj, "HullValue", 0L);
        long modulesValue = getLong(obj, "ModulesValue", 0L);
        double hullHealth = getDouble(obj, "HullHealth", 0.0);
        double unladenMass = getDouble(obj, "UnladenMass", 0.0);

        int cargoCapacity = getInt(obj, "CargoCapacity", 0);
        double maxJumpRange = getDouble(obj, "MaxJumpRange", 0.0);

        long rebuy = getLong(obj, "Rebuy", 0L);

        LoadoutEvent.FuelCapacity fuelCapacity = null;
        if (obj.has("FuelCapacity") && obj.get("FuelCapacity").isJsonObject()) {
            JsonObject fc = obj.getAsJsonObject("FuelCapacity");
            double main = getDouble(fc, "Main", 0.0);
            double reserve = getDouble(fc, "Reserve", 0.0);
            fuelCapacity = new LoadoutEvent.FuelCapacity(main, reserve);
        }

        List<LoadoutEvent.Module> modules = new ArrayList<>();
        if (obj.has("Modules") && obj.get("Modules").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("Modules");
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                modules.add(parseLoadoutModule(el.getAsJsonObject()));
            }
        }

        return new LoadoutEvent(
                ts,
                obj,
                ship,
                shipId,
                shipName,
                shipIdent,
                hullValue,
                modulesValue,
                hullHealth,
                unladenMass,
                cargoCapacity,
                maxJumpRange,
                fuelCapacity,
                rebuy,
                modules
        );
    }

    private LoadoutEvent.Module parseLoadoutModule(JsonObject m) {
        String slot = getString(m, "Slot");
        String item = getString(m, "Item");
        boolean on = getBoolean(m, "On", false);
        int priority = getInt(m, "Priority", 0);
        double health = getDouble(m, "Health", 0.0);
        long value = getLong(m, "Value", 0L);

        Integer ammoInClip = m.has("AmmoInClip") && !m.get("AmmoInClip").isJsonNull()
                ? Integer.valueOf(m.get("AmmoInClip").getAsInt())
                : null;
        Integer ammoInHopper = m.has("AmmoInHopper") && !m.get("AmmoInHopper").isJsonNull()
                ? Integer.valueOf(m.get("AmmoInHopper").getAsInt())
                : null;

        LoadoutEvent.Engineering engineering = null;
        if (m.has("Engineering") && m.get("Engineering").isJsonObject()) {
            engineering = parseLoadoutEngineering(m.getAsJsonObject("Engineering"));
        }

        return new LoadoutEvent.Module(
                slot,
                item,
                on,
                priority,
                health,
                value,
                ammoInClip,
                ammoInHopper,
                engineering,
                m
        );
    }

    private LoadoutEvent.Engineering parseLoadoutEngineering(JsonObject e) {
        String engineer = getString(e, "Engineer");
        long engineerId = getLong(e, "EngineerID", 0L);
        long blueprintId = getLong(e, "BlueprintID", 0L);
        String blueprintName = getString(e, "BlueprintName");
        int level = getInt(e, "Level", 0);
        double quality = getDouble(e, "Quality", 0.0);
        String experimentalEffect = getString(e, "ExperimentalEffect");
        String experimentalEffectLocalised = getString(e, "ExperimentalEffect_Localised");

        List<LoadoutEvent.Modifier> modifiers = new ArrayList<>();
        if (e.has("Modifiers") && e.get("Modifiers").isJsonArray()) {
            JsonArray arr = e.getAsJsonArray("Modifiers");
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject mo = el.getAsJsonObject();
                String label = getString(mo, "Label");
                double value = getDouble(mo, "Value", 0.0);
                double originalValue = getDouble(mo, "OriginalValue", 0.0);
                int lessIsGood = getInt(mo, "LessIsGood", 0);
                modifiers.add(new LoadoutEvent.Modifier(label, value, originalValue, lessIsGood, mo));
            }
        }

        return new LoadoutEvent.Engineering(
                engineer,
                engineerId,
                blueprintId,
                blueprintName,
                level,
                quality,
                experimentalEffect,
                experimentalEffectLocalised,
                modifiers,
                e
        );
    }

private LocationEvent parseLocation(Instant ts, JsonObject obj) {
        boolean docked = obj.has("Docked") && obj.get("Docked").getAsBoolean();
        boolean taxi = obj.has("Taxi") && obj.get("Taxi").getAsBoolean();
        boolean multicrew = obj.has("Multicrew") && obj.get("Multicrew").getAsBoolean();
        String starSystem = getString(obj, "StarSystem");
        long systemAddress = obj.has("SystemAddress") ? obj.get("SystemAddress").getAsLong() : 0L;

        double[] starPos = null;
        if (obj.has("StarPos") && obj.get("StarPos").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("StarPos");
            if (arr.size() == 3) {
                starPos = new double[]{
                        arr.get(0).getAsDouble(),
                        arr.get(1).getAsDouble(),
                        arr.get(2).getAsDouble()
                };
            }
        }

        String body = getString(obj, "Body");
        int bodyId = obj.has("BodyID") ? obj.get("BodyID").getAsInt() : -1;
        String bodyType = getString(obj, "BodyType");

        return new LocationEvent(
                ts, obj,
                docked, taxi, multicrew,
                starSystem, systemAddress,
                starPos, body, bodyId, bodyType
        );
    }

    private StartJumpEvent parseStartJump(Instant ts, JsonObject obj) {
        String jumpType = getString(obj, "JumpType");
        boolean taxi = obj.has("Taxi") && obj.get("Taxi").getAsBoolean();
        String starSystem = getString(obj, "StarSystem");
        Long systemAddress = obj.has("SystemAddress") ? obj.get("SystemAddress").getAsLong() : null;
        String starClass = getString(obj, "StarClass");
        return new StartJumpEvent(ts, obj, jumpType, taxi, starSystem, systemAddress, starClass);
    }

    private FsdJumpEvent parseFsdJump(Instant ts, JsonObject obj) {
        String starSystem = getString(obj, "StarSystem");
        long systemAddress = obj.has("SystemAddress") ? obj.get("SystemAddress").getAsLong() : 0L;

        double[] starPos = null;
        if (obj.has("StarPos") && obj.get("StarPos").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("StarPos");
            if (arr.size() == 3) {
                starPos = new double[]{
                        arr.get(0).getAsDouble(),
                        arr.get(1).getAsDouble(),
                        arr.get(2).getAsDouble()
                };
            }
        }

        boolean carrier = true;
        Boolean docked = null;
        if (obj.get("event").getAsString().equals("CarrierJump")) {
        	 docked = obj.has("Docked") ? obj.get("Docked").getAsBoolean() : false;
        }
        String body = getString(obj, "Body");
        int bodyId = obj.has("BodyID") ? obj.get("BodyID").getAsInt() : -1;
        String bodyType = getString(obj, "BodyType");
        double jumpDist = obj.has("JumpDist") ? obj.get("JumpDist").getAsDouble() : 0.0;
        double fuelUsed = obj.has("FuelUsed") ? obj.get("FuelUsed").getAsDouble() : 0.0;
        double fuelLevel = obj.has("FuelLevel") ? obj.get("FuelLevel").getAsDouble() : 0.0;
        
        return new FsdJumpEvent(
                ts, obj,
                starSystem, systemAddress, starPos,
                body, bodyId, bodyType,
                jumpDist, fuelUsed, fuelLevel, docked
        );
    }

    private CarrierJumpEvent parseCarrierJump(Instant ts, JsonObject obj) {
        boolean docked = getBoolean(obj, "Docked", false);
        String stationName = getString(obj, "StationName");
        String stationType = getString(obj, "StationType");
        long marketId = getLong(obj, "MarketID");

        String stationFaction = null;
        if (obj.has("StationFaction") && obj.get("StationFaction").isJsonObject()) {
            stationFaction = getString(obj.getAsJsonObject("StationFaction"), "Name");
        }

        String stationGovernment = getString(obj, "StationGovernment");
        String stationGovernmentLocalised = getString(obj, "StationGovernment_Localised");

        List<String> stationServices = Collections.emptyList();
        if (obj.has("StationServices") && obj.get("StationServices").isJsonArray()) {
            List<String> tmp = new ArrayList<>();
            for (JsonElement e : obj.getAsJsonArray("StationServices")) {
                if (e != null && !e.isJsonNull()) {
                    tmp.add(e.getAsString());
                }
            }
            stationServices = Collections.unmodifiableList(tmp);
        }

        String stationEconomy = getString(obj, "StationEconomy");
        String stationEconomyLocalised = getString(obj, "StationEconomy_Localised");

        List<CarrierJumpEvent.StationEconomy> stationEconomies = Collections.emptyList();
        if (obj.has("StationEconomies") && obj.get("StationEconomies").isJsonArray()) {
            List<CarrierJumpEvent.StationEconomy> tmp = new ArrayList<>();
            for (JsonElement e : obj.getAsJsonArray("StationEconomies")) {
                if (e == null || e.isJsonNull() || !e.isJsonObject()) {
                    continue;
                }
                JsonObject eo = e.getAsJsonObject();
                tmp.add(new CarrierJumpEvent.StationEconomy(
                        getString(eo, "Name"),
                        getString(eo, "Name_Localised"),
                        eo.has("Proportion") && !eo.get("Proportion").isJsonNull()
                                ? eo.get("Proportion").getAsDouble()
                                : 0.0
                ));
            }
            stationEconomies = Collections.unmodifiableList(tmp);
        }

        boolean taxi = getBoolean(obj, "Taxi", false);
        boolean multicrew = getBoolean(obj, "Multicrew", false);

        String starSystem = getString(obj, "StarSystem");
        long systemAddress = getLong(obj, "SystemAddress");

        double[] starPos = null;
        if (obj.has("StarPos") && obj.get("StarPos").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("StarPos");
            if (arr.size() >= 3) {
                starPos = new double[] {
                        arr.get(0).getAsDouble(),
                        arr.get(1).getAsDouble(),
                        arr.get(2).getAsDouble()
                };
            }
        }

        String systemAllegiance = getString(obj, "SystemAllegiance");
        String systemEconomy = getString(obj, "SystemEconomy");
        String systemEconomyLocalised = getString(obj, "SystemEconomy_Localised");
        String systemSecondEconomy = getString(obj, "SystemSecondEconomy");
        String systemSecondEconomyLocalised = getString(obj, "SystemSecondEconomy_Localised");
        String systemGovernment = getString(obj, "SystemGovernment");
        String systemGovernmentLocalised = getString(obj, "SystemGovernment_Localised");
        String systemSecurity = getString(obj, "SystemSecurity");
        String systemSecurityLocalised = getString(obj, "SystemSecurity_Localised");
        long population = getLong(obj, "Population");

        String body = getString(obj, "Body");
        int bodyId = getInt(obj, "BodyID", -1);
        String bodyType = getString(obj, "BodyType");

        return new CarrierJumpEvent(
                ts,
                obj,
                docked,
                stationName,
                stationType,
                marketId,
                stationFaction,
                stationGovernment,
                stationGovernmentLocalised,
                stationServices,
                stationEconomy,
                stationEconomyLocalised,
                stationEconomies,
                taxi,
                multicrew,
                starSystem,
                systemAddress,
                starPos,
                systemAllegiance,
                systemEconomy,
                systemEconomyLocalised,
                systemSecondEconomy,
                systemSecondEconomyLocalised,
                systemGovernment,
                systemGovernmentLocalised,
                systemSecurity,
                systemSecurityLocalised,
                population,
                body,
                bodyId,
                bodyType
        );
    }

    private CarrierLocationEvent parseCarrierLocation(Instant ts, JsonObject obj) {
        String starSystem = obj.has("StarSystem") && !obj.get("StarSystem").isJsonNull()
                ? obj.get("StarSystem").getAsString()
                : null;
        long systemAddress = obj.has("SystemAddress") && !obj.get("SystemAddress").isJsonNull()
                ? obj.get("SystemAddress").getAsLong()
                : 0L;
        int bodyId = obj.has("BodyID") && !obj.get("BodyID").isJsonNull()
                ? obj.get("BodyID").getAsInt()
                : 0;
        return new CarrierLocationEvent(ts, obj, starSystem, systemAddress, bodyId);
    }

    private FsdTargetEvent parseFsdTarget(Instant ts, JsonObject obj) {
        String name = getString(obj, "Name");
        long systemAddress = obj.has("SystemAddress") ? obj.get("SystemAddress").getAsLong() : 0L;
        String starClass = getString(obj, "StarClass");
        int remaining = obj.has("RemainingJumpsInRoute") ? obj.get("RemainingJumpsInRoute").getAsInt() : 0;
        return new FsdTargetEvent(ts, obj, name, systemAddress, starClass, remaining);
    }

    private SaasignalsFoundEvent parseSaaSignalsFound(Instant ts, JsonObject obj) {
        String bodyName = getString(obj, "BodyName");
        long systemAddress = obj.has("SystemAddress") ? obj.get("SystemAddress").getAsLong() : 0L;
        int bodyId = obj.has("BodyID") ? obj.get("BodyID").getAsInt() : -1;

        List<SaasignalsFoundEvent.Signal> signals = new ArrayList<>();
        if (obj.has("Signals") && obj.get("Signals").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("Signals")) {
                JsonObject so = e.getAsJsonObject();
                String type = getString(so, "Type");
                String typeLocalised = getString(so, "Type_Localised");
                int count = so.has("Count") ? so.get("Count").getAsInt() : 0;
                signals.add(new SaasignalsFoundEvent.Signal(type, typeLocalised, count));
            }
        }

        List<SaasignalsFoundEvent.Genus> genuses = new ArrayList<>();
        if (obj.has("Genuses") && obj.get("Genuses").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("Genuses")) {
                JsonObject go = e.getAsJsonObject();
                String genus = getString(go, "Genus");
                String genusLocalised = getString(go, "Genus_Localised");
                genuses.add(new SaasignalsFoundEvent.Genus(genus, genusLocalised));
            }
        }

        return new SaasignalsFoundEvent(ts, obj, bodyName, systemAddress, bodyId, signals, genuses);
    }

    private ReceiveTextEvent parseReceiveText(Instant ts, JsonObject obj) {
        String from = getString(obj, "From");
        String msg = getString(obj, "Message");
        String msgLoc = getString(obj, "Message_Localised");
        String channel = getString(obj, "Channel");
        return new ReceiveTextEvent(ts, obj, from, msg, msgLoc, channel);
    }

    private StatusEvent parseStatus(Instant ts, JsonObject obj) {
        int flags = obj.has("Flags") ? obj.get("Flags").getAsInt() : 0;
        int flags2 = obj.has("Flags2") ? obj.get("Flags2").getAsInt() : 0;

        int[] pips = new int[3];
        if (obj.has("Pips") && obj.get("Pips").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("Pips");
            for (int i = 0; i < Math.min(3, arr.size()); i++) {
                pips[i] = arr.get(i).getAsInt();
            }
        }

        int fireGroup = obj.has("FireGroup") ? obj.get("FireGroup").getAsInt() : 0;
        int guiFocus = obj.has("GuiFocus") ? obj.get("GuiFocus").getAsInt() : 0;

        double fuelMain = 0.0;
        double fuelReservoir = 0.0;
        if (obj.has("Fuel") && obj.get("Fuel").isJsonObject()) {
            JsonObject fuel = obj.getAsJsonObject("Fuel");
            fuelMain = fuel.has("FuelMain") ? fuel.get("FuelMain").getAsDouble() : 0.0;
            fuelReservoir = fuel.has("FuelReservoir") ? fuel.get("FuelReservoir").getAsDouble() : 0.0;
        }

        double cargo = obj.has("Cargo") ? obj.get("Cargo").getAsDouble() : 0.0;
        String legalState = getString(obj, "LegalState");
        long balance = obj.has("Balance") ? obj.get("Balance").getAsLong() : 0L;

        // Extra Status.json fields
        Double latitude = obj.has("Latitude") && !obj.get("Latitude").isJsonNull()
                ? obj.get("Latitude").getAsDouble()
                : null;
        Double longitude = obj.has("Longitude") && !obj.get("Longitude").isJsonNull()
                ? obj.get("Longitude").getAsDouble()
                : null;
        Double altitude = obj.has("Altitude") && !obj.get("Altitude").isJsonNull()
                ? obj.get("Altitude").getAsDouble()
                : null;
        Double heading = obj.has("Heading") && !obj.get("Heading").isJsonNull()
                ? obj.get("Heading").getAsDouble()
                : null;
        // Note:
        // * In the Status.json snapshot written by the game, BodyName is present when near a body.
        // * In the journal "Status" event, BodyName is often absent, but a nested Destination
        //   object may be present (System/Body/Name).
        // For UI features that want to highlight the current/near body, we use Destination.Name
        // as a fallback if BodyName is missing.
        String bodyName = getString(obj, "BodyName");
        Double planetRadius = obj.has("PlanetRadius") && !obj.get("PlanetRadius").isJsonNull()
                ? obj.get("PlanetRadius").getAsDouble()
                : null;

        // Destination
        Long destSystem = null;
        Integer destBody = null;
        String destName = null;
        String destNameLocalised = null;
        if (obj.has("Destination") && obj.get("Destination").isJsonObject()) {
            JsonObject dest = obj.getAsJsonObject("Destination");
            if (dest.has("System") && !dest.get("System").isJsonNull()) {
                try {
                    destSystem = dest.get("System").getAsLong();
                } catch (Exception ignored) { }
            }
            if (dest.has("Body") && !dest.get("Body").isJsonNull()) {
                try {
                    destBody = dest.get("Body").getAsInt();
                } catch (Exception ignored) { }
            }
            if (dest.has("Name") && !dest.get("Name").isJsonNull()) {
                destName = dest.get("Name").getAsString();
            }
            if (dest.has("Name_Localised") && !dest.get("Name_Localised").isJsonNull()) {
                try {
                    destNameLocalised = dest.get("Name_Localised").getAsString();
                } catch (Exception ignored) {
                }
            }
        }

        if (bodyName == null || bodyName.isBlank()) {
            if (destNameLocalised != null && !destNameLocalised.isBlank()) {
                bodyName = destNameLocalised;
            } else {
                bodyName = destName;
            }
        }

        return new StatusEvent(
                ts,
                obj,
                flags,
                flags2,
                pips,
                fireGroup,
                guiFocus,
                fuelMain,
                fuelReservoir,
                cargo,
                legalState,
                balance,
                latitude,
                longitude,
                altitude,
                heading,
                bodyName,
                planetRadius,
                destSystem,
                destBody,
                destName,
                destNameLocalised
        );
    }
    private ScanOrganicEvent parseScanOrganic(Instant ts, JsonObject json) {
        long systemAddress = json.has("SystemAddress")
                ? json.get("SystemAddress").getAsLong()
                : 0L;

        // In ScanOrganic, "Body" is the body index (BodyID), not a name.
        int bodyId = json.has("Body") ? json.get("Body").getAsInt() : -1;

        String scanType = getString(json, "ScanType");
        String genus = getString(json, "Genus");
        String genusLocalised = getString(json, "Genus_Localised");
        String species = getString(json, "Species");
        String speciesLocalised = getString(json, "Species_Localised");

        // There is no body name in ScanOrganic. We pass null here; the
        // SystemTabPanel will already have the name from the Scan/FSS data.
        String bodyName = null;

        return new ScanOrganicEvent(
                ts,
                json,
                systemAddress,
                bodyName,
                bodyId,
                scanType,
                genus,
                genusLocalised,
                species,
                speciesLocalised
        );
    }

    private ScanEvent parseScan(Instant ts, JsonObject obj) {
        String bodyName = getString(obj, "BodyName");
        int bodyId = obj.has("BodyID") ? obj.get("BodyID").getAsInt() : -1;
        String starSystem = obj.has("StarSystem") ? obj.get("StarSystem").getAsString() : "";
        
        long systemAddress = obj.has("SystemAddress") ? obj.get("SystemAddress").getAsLong() : 0L;
        double distanceLs = obj.has("DistanceFromArrivalLS")
                ? obj.get("DistanceFromArrivalLS").getAsDouble()
                : Double.NaN;
        boolean landable = obj.has("Landable") && obj.get("Landable").getAsBoolean();
        String planetClass = getString(obj, "PlanetClass");
        String atmosphere = getString(obj, "Atmosphere");
        String terraformState = getString(obj, "TerraformState");
        Map<String, Double> atmoComp = parseAtmosphereComposition(obj);

        Double surfaceGravity = obj.has("SurfaceGravity")
                ? obj.get("SurfaceGravity").getAsDouble()
                : null;
        Double surfaceTemp = obj.has("SurfaceTemperature")
                ? obj.get("SurfaceTemperature").getAsDouble()
                : null;
        Double orbitalPeriod = obj.has("OrbitalPeriod")
                ? obj.get("OrbitalPeriod").getAsDouble()
                : null;
        String volcanism = getString(obj, "Volcanism");
        Boolean wasDiscovered = obj.has("WasDiscovered") ? Boolean.valueOf(obj.get("WasDiscovered").getAsBoolean()) : null;
        Boolean wasMapped = obj.has("WasMapped") ? Boolean.valueOf(obj.get("WasMapped").getAsBoolean()) : null;
        Boolean wasFootfalled = obj.has("WasFootfalled") ? Boolean.valueOf(obj.get("WasFootfalled").getAsBoolean()) : null;
        
        String starType = getString(obj, "StarType");

        List<ScanEvent.ParentRef> parents = parseParentRefs(obj);
        List<ScanEvent.RingInfo> rings = parseScanRings(obj);
        String reserveLevel = getString(obj, "ReserveLevel");

        Double surfacePressure = obj.has("SurfacePressure")
        		? obj.get("SurfacePressure").getAsDouble(): null;

        return new ScanEvent(
                ts,
                obj,
                bodyName,
                bodyId,
                starSystem,
                systemAddress,
                distanceLs,
                landable,
                planetClass,
                atmosphere,
                terraformState,
                surfaceGravity,
                surfacePressure,
                surfaceTemp,
                orbitalPeriod,
                volcanism,
                wasDiscovered,
                wasMapped,
                wasFootfalled,
                atmoComp,
                starType,
                parents,
                rings,
                reserveLevel
        );
    }

    private List<ScanEvent.RingInfo> parseScanRings(JsonObject obj) {
        if (obj == null || !obj.has("Rings") || obj.get("Rings").isJsonNull()) {
            return Collections.emptyList();
        }
        JsonElement ringsEl = obj.get("Rings");
        if (!ringsEl.isJsonArray()) {
            return Collections.emptyList();
        }
        List<ScanEvent.RingInfo> out = new ArrayList<>();
        for (JsonElement ringEl : ringsEl.getAsJsonArray()) {
            if (ringEl == null || !ringEl.isJsonObject()) {
                continue;
            }
            JsonObject ro = ringEl.getAsJsonObject();
            String name = getString(ro, "Name");
            String ringClass = getString(ro, "RingClass");
            if (ringClass == null || ringClass.isEmpty()) {
                continue;
            }
            out.add(new ScanEvent.RingInfo(name, ringClass));
        }
        return out.isEmpty() ? Collections.emptyList() : out;
    }

    /**
     * Parse the journal Scan event's Parents field.
     *
     * Parents is an array of objects, where each object has exactly one entry.
     * Example: [ {"Planet":17}, {"Star":5}, {"Null":0} ]
     */
    private static List<ScanEvent.ParentRef> parseParentRefs(JsonObject obj) {
        if (obj == null || !obj.has("Parents") || obj.get("Parents").isJsonNull()) {
            return Collections.emptyList();
        }

        JsonElement el = obj.get("Parents");
        if (!el.isJsonArray()) {
            return Collections.emptyList();
        }

        List<ScanEvent.ParentRef> out = new ArrayList<>();
        for (JsonElement parentEl : el.getAsJsonArray()) {
            if (parentEl == null || !parentEl.isJsonObject()) {
                continue;
            }
            JsonObject parentObj = parentEl.getAsJsonObject();
            if (parentObj.entrySet().isEmpty()) {
                continue;
            }

            // Each element should be a single-entry object like {"Star": 5}
            for (Map.Entry<String, JsonElement> e : parentObj.entrySet()) {
                String type = e.getKey();
                JsonElement v = e.getValue();
                if (type == null || type.isEmpty() || v == null || !v.isJsonPrimitive()) {
                    continue;
                }
                try {
                    int bodyId = v.getAsInt();
                    out.add(new ScanEvent.ParentRef(type, bodyId));
                } catch (Exception ignore) {
                    // Ignore malformed entries; keep parsing others
                }
            }
        }
        return out;
    }

    private FssDiscoveryScanEvent parseFssDiscoveryScan(Instant ts, JsonObject obj) {
        double progress = obj.has("Progress") ? obj.get("Progress").getAsDouble() : 0.0;
        int bodyCount = obj.has("BodyCount") ? obj.get("BodyCount").getAsInt() : 0;
        int nonBodyCount = obj.has("NonBodyCount") ? obj.get("NonBodyCount").getAsInt() : 0;
        String systemName = getString(obj, "SystemName");
        long systemAddress = obj.has("SystemAddress") ? obj.get("SystemAddress").getAsLong() : 0L;

        return new FssDiscoveryScanEvent(
                ts,
                obj,
                progress,
                bodyCount,
                nonBodyCount,
                systemName,
                systemAddress
        );
    }


    private FssBodySignalsEvent parseFssBodySignals(Instant ts, JsonObject obj) {
        String bodyName = getString(obj, "BodyName");
        long systemAddress = obj.has("SystemAddress") ? obj.get("SystemAddress").getAsLong() : 0L;
        int bodyId = obj.has("BodyID") ? obj.get("BodyID").getAsInt() : -1;

        List<SaasignalsFoundEvent.Signal> signals = new ArrayList<>();
        if (obj.has("Signals") && obj.get("Signals").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("Signals")) {
                JsonObject so = e.getAsJsonObject();
                String type = getString(so, "Type");
                String typeLocalised = getString(so, "Type_Localised");
                int count = so.has("Count") ? so.get("Count").getAsInt() : 0;
                signals.add(new SaasignalsFoundEvent.Signal(type, typeLocalised, count));
            }
        }

        return new FssBodySignalsEvent(
                ts,
                obj,
                bodyName,
                systemAddress,
                bodyId,
                signals
        );
    }

    
    private String getString(JsonObject obj, String field) {
        return obj.has(field) && !obj.get(field).isJsonNull()
                ? obj.get(field).getAsString()
                : null;
    }
    
    private long getLong(JsonObject obj, String field) {
        return getLong(obj, field, 0L);
    }

    private long getLong(JsonObject obj, String field, long defaultValue) {
        return obj.has(field) && !obj.get(field).isJsonNull()
                ? obj.get(field).getAsLong()
                : defaultValue;
    }

    private int getInt(JsonObject obj, String field) {
        return getInt(obj, field, 0);
    }

    private int getInt(JsonObject obj, String field, int defaultValue) {
        return obj.has(field) && !obj.get(field).isJsonNull()
                ? obj.get(field).getAsInt()
                : defaultValue;
    }

    private double getDouble(JsonObject obj, String field, double defaultValue) {
        return obj.has(field) && !obj.get(field).isJsonNull()
                ? obj.get(field).getAsDouble()
                : defaultValue;
    }

    private boolean getBoolean(JsonObject obj, String field, boolean defaultValue) {
        return obj.has(field) && !obj.get(field).isJsonNull()
                ? obj.get(field).getAsBoolean()
                : defaultValue;
    }



/**
 * Parse the journal Scan event's AtmosphereComposition field into a simple map:
 *   gasName -> percent
 *
 * Keys are canonicalized to match Exobiology rules (e.g., "SulphurDioxide").
 */
private Map<String, Double> parseAtmosphereComposition(JsonObject obj) {
    if (obj == null || !obj.has("AtmosphereComposition") || obj.get("AtmosphereComposition").isJsonNull()) {
        return Collections.emptyMap();
    }
    JsonElement el = obj.get("AtmosphereComposition");
    if (!el.isJsonArray()) {
        return Collections.emptyMap();
    }

    Map<String, Double> out = new HashMap<>();
    for (JsonElement item : el.getAsJsonArray()) {
        if (item == null || !item.isJsonObject()) {
            continue;
        }
        JsonObject o = item.getAsJsonObject();
        String name = getString(o, "Name");
        if (name == null || name.isEmpty()) {
            continue;
        }
        if (!o.has("Percent") || o.get("Percent").isJsonNull()) {
            continue;
        }
        double pct;
        try {
            pct = o.get("Percent").getAsDouble();
        } catch (Exception ex) {
            continue;
        }
        out.put(canonicalGasName(name), Double.valueOf(pct));
    }

    if (out.isEmpty()) {
        return Collections.emptyMap();
    }
    return out;
}

private static String canonicalGasName(String name) {
    String raw = name.trim();
    String norm = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");

    if ("sulphurdioxide".equals(norm) || "sulfurdioxide".equals(norm)) {
        return "SulphurDioxide";
    }
    if ("carbondioxide".equals(norm)) {
        return "CarbonDioxide";
    }
    return raw;
}
}

