package data.scripts.betterautopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;

/**
 * Target categories for the Targets settings tab. Each entity the fleet can fly to falls in one category; the steering
 * runs only for categories switched on (BASettings.targets, ids and defaults in BASettings.TARGET_IDS/TARGET_DEFAULTS).
 */
final class BATargets {
    private BATargets() { }

    static final int PLANETS = 0, COLONIES = 1, STARS = 2,
            STATIONS = 3, ABANDONED = 4, RESEARCH = 5, MINING = 6, HABITATS = 7,
            WRECKS = 8, CACHES = 9, DEBRIS = 10, DERELICTS = 11,
            OBJECTIVES = 12, STABLE = 13, BEACONS = 14, OTHER = 15,
            JUMP_POINTS = 16, GATES = 17, SYSTEMS = 18,
            HOSTILE = 19, FLEETS = 20;
    static final int COUNT = 21;

    /** For the debug log and the reason it does not steer. */
    static final String[] NAMES = {
            "planet", "colony", "star",
            "station", "abandoned station", "research station", "mining station", "orbital habitat",
            "derelict ship", "cache or cargo pods", "debris field", "Domain-era derelict",
            "relay, buoy or array", "stable location", "warning beacon", "other object",
            "jump point", "gate", "system or point in space",
            "hostile fleet", "fleet"};

    /** The category of e. Checked in this order: fleets, planets, travel targets, named entity types, tags, the rest. */
    static int category(SectorEntityToken e) {
        if (e instanceof CampaignFleetAPI) {
            CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
            return pf != null && ((CampaignFleetAPI) e).isHostileTo(pf) ? HOSTILE : FLEETS;
        }
        if (e instanceof PlanetAPI) {
            PlanetAPI p = (PlanetAPI) e;
            if (p.isStar() || p.isBlackHole()) return STARS;
            MarketAPI m = p.getMarket();
            return m != null && !m.isPlanetConditionMarketOnly() ? COLONIES : PLANETS;
        }
        if (e instanceof JumpPointAPI) return JUMP_POINTS;
        if (e.hasTag(Tags.GATE) || BAGates.beacon(e) || BAScript.bifrostGate(e)) return GATES;
        if (BAScript.pointToken(e) || anchor(e)) return SYSTEMS;
        String type = e.getCustomEntityType();
        if (type == null) type = "";
        if (Entities.STATION_RESEARCH_REMNANT.equals(type) || "station_research".equals(type)) return RESEARCH;
        if (Entities.STATION_MINING_REMNANT.equals(type) || "station_mining".equals(type)) return MINING;
        if (Entities.ORBITAL_HABITAT_REMNANT.equals(type) || "orbital_habitat".equals(type)) return HABITATS;
        if (Entities.WRECK.equals(type)) return WRECKS;
        if (Entities.CARGO_PODS.equals(type) || Entities.CARGO_POD_SPECIAL.equals(type) || type.contains("cache")) return CACHES;
        if (e.hasTag(Tags.DEBRIS_FIELD) || Entities.DEBRIS_FIELD_SHARED.equals(type)
                || (e instanceof CampaignTerrainAPI && ((CampaignTerrainAPI) e).getPlugin() instanceof DebrisFieldTerrainPlugin)) return DEBRIS;
        if (Entities.DERELICT_SURVEY_PROBE.equals(type) || Entities.DERELICT_SURVEY_SHIP.equals(type)
                || Entities.DERELICT_MOTHERSHIP.equals(type) || Entities.DERELICT_CRYOSLEEPER.equals(type)
                || Entities.DERELICT_GATEHAULER.equals(type) || Entities.CORONAL_TAP.equals(type)
                || Entities.GENERIC_PROBE.equals(type)) return DERELICTS;
        MarketAPI m = e.getMarket();
        if (m != null && m.hasCondition(Conditions.ABANDONED_STATION)) return ABANDONED;
        if (e.hasTag(Tags.STATION) || (m != null && !m.isPlanetConditionMarketOnly())) return STATIONS;
        if (e.hasTag(Tags.OBJECTIVE)) return OBJECTIVES;
        if (e.hasTag(Tags.STABLE_LOCATION) || Entities.STABLE_LOCATION.equals(type)) return STABLE;
        if (e.hasTag(Tags.WARNING_BEACON) || Entities.WARNING_BEACON.equals(type)) return BEACONS;
        return OTHER;
    }

    /** A star system's hyperspace anchor (a course to a system). */
    static boolean anchor(SectorEntityToken e) {
        for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
            if (sys.getHyperspaceAnchor() == e) return true;
        }
        return false;
    }

    /** True when the steering runs for e's category. */
    static boolean on(int category) {
        return category >= 0 && category < BASettings.targets.length && BASettings.targets[category];
    }
}
