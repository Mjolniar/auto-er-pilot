package data.scripts.betterautopilot;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

/**
 * The settings. From LunaLib's settings menu when LunaLib is enabled, otherwise from data/config/better_autopilot.json.
 * A value missing from both uses the default written here.
 */
public final class BASettings {
    private BASettings() { }

    public static final String MOD = "better_autopilot", LUNA = "lunalib", FILE = "data/config/better_autopilot.json";
    static final Logger LOG = Global.getLogger(BASettings.class);

    public static boolean enabled = true;
    public static boolean storms = true, stormDetour = true;
    public static int stormDetourShare = 15;
    public static boolean slipExit = true, slipUse = true;
    public static int slipAngle = 45, slipSaving = 10;
    public static boolean slipReverse = true, slipReverseInside = true, slipAvoid = true;
    public static boolean keepSB = true;
    public static boolean hazards = true;
    public static int hazardMargin = 300;
    public static boolean fleets = true;
    public static float fleetMargin = 1f;
    public static int fleetClearance = 800;
    public static boolean asteroidsSlow = true;
    public static boolean pulsarEscape = true, windBurn = true, holdAfterJump = true;
    public static boolean autoJump = true, gates = true, bifrost = true, gatesNoPulsar = true, wormholes = true;
    public static int gateSaving = 10;
    public static boolean debug = false;

    /** The Targets tab, one switch per BATargets category (same order). Defaults: what it steered for before 0.1.28. */
    static final String[] TARGET_IDS = {
            "ba_tPlanets", "ba_tColonies", "ba_tStars",
            "ba_tStations", "ba_tAbandoned", "ba_tResearch", "ba_tMining", "ba_tHabitats",
            "ba_tWrecks", "ba_tCaches", "ba_tDebris", "ba_tDerelicts",
            "ba_tObjectives", "ba_tStable", "ba_tBeacons", "ba_tOther",
            "ba_tJumpPoints", "ba_tGates", "ba_tSystems",
            "ba_tHostile", "ba_tFleets"};
    static final boolean[] TARGET_DEFAULTS = {
            true, true, true,
            false, false, false, false, false,
            false, false, false, false,
            false, false, false, false,
            true, true, true,
            false, false};
    public static boolean[] targets = TARGET_DEFAULTS.clone();

    static JSONObject file;
    static boolean useLuna;

    public static boolean luna() { return Global.getSettings().getModManager().isModEnabled(LUNA); }

    /** Reads every value. False (nothing changed) while LunaLib is enabled but has not loaded its settings yet. */
    public static boolean load() { return load(true); }

    /** fromLuna false reads the file even when LunaLib is enabled. */
    public static boolean load(boolean fromLuna) {
        // read on every load, so edits to the file apply on the next save load
        try { file = Global.getSettings().loadJSON(FILE, MOD); }
        catch (Throwable t) { file = new JSONObject(); LOG.warn("Auto-er Pilot: can't read " + FILE + "; using defaults", t); }
        useLuna = fromLuna && luna();
        if (useLuna) {
            Boolean probe;
            try { probe = BALuna.bool("ba_enabled"); } catch (Throwable t) { probe = null; }
            if (probe == null) return false;
        }
        enabled = bool("ba_enabled", true);
        storms = bool("ba_storms", true);
        stormDetour = bool("ba_stormDetour", true);
        stormDetourShare = clamp(integer("ba_stormDetourShare", 15), 0, 100);
        slipExit = bool("ba_slipExit", true);
        slipUse = bool("ba_slipUse", true);
        slipAngle = clamp(integer("ba_slipAngle", 45), 5, 90);
        // at least 5: below that a ride that saves almost nothing is entered and left again in turn
        slipSaving = clamp(integer("ba_slipSaving", 10), 5, 90);
        slipReverse = bool("ba_slipReverse", true);
        slipReverseInside = bool("ba_slipReverseInside", true);
        slipAvoid = bool("ba_slipAvoid", true);
        keepSB = bool("ba_keepSB", true);
        hazards = bool("ba_hazards", true);
        hazardMargin = clamp(integer("ba_hazardMargin", 300), 0, 3000);
        fleets = bool("ba_fleets", true);
        fleetMargin = (float) Math.max(0.1, Math.min(10.0, dbl("ba_fleetMargin", 1.0)));
        fleetClearance = clamp(integer("ba_fleetClearance", 800), 0, 5000);
        asteroidsSlow = bool("ba_asteroidsSlow", true);
        pulsarEscape = bool("ba_pulsarEscape", true);
        windBurn = bool("ba_windBurn", true);
        holdAfterJump = bool("ba_holdAfterJump", true);
        autoJump = bool("ba_autoJump", true);
        gates = bool("ba_gates", true);
        bifrost = bool("ba_bifrost", true);
        gatesNoPulsar = bool("ba_gatesNoPulsar", true);
        wormholes = bool("ba_wormholes", true);
        gateSaving = clamp(integer("ba_gateSaving", 10), 0, 90);
        debug = bool("ba_debug", false);
        boolean[] t = new boolean[TARGET_IDS.length];
        StringBuilder on = new StringBuilder();
        for (int i = 0; i < t.length; i++) {
            t[i] = bool(TARGET_IDS[i], TARGET_DEFAULTS[i]);
            if (t[i]) on.append(on.length() == 0 ? "" : ", ").append(BATargets.NAMES[i]);
        }
        targets = t;
        LOG.info("Auto-er Pilot: settings from " + (useLuna ? "LunaLib" : FILE) + ": enabled " + enabled
                + ", storms " + storms + ", storm detour " + stormDetour + " (" + stormDetourShare + " percent)"
                + ", slipstream exit " + slipExit + ", slipstream use " + slipUse + " (" + slipAngle + " degrees, " + slipSaving + " percent), reverse polarity " + slipReverse + " (inside " + slipReverseInside + "), avoid wrong-way slipstreams " + slipAvoid
                + ", keep sustained burn " + keepSB
                + ", hazards " + hazards + " (" + hazardMargin + ")"
                + ", fleets " + fleets + " (x" + fleetMargin + ", " + fleetClearance + ")"
                + ", asteroids " + asteroidsSlow + ", wind burn " + windBurn + ", wait after Transverse Jump " + holdAfterJump + ", pulsar escape " + pulsarEscape + ", jump without dialog " + autoJump
                + ", gates " + gates + " (" + gateSaving + " percent, Bifrost " + bifrost + ", wormholes " + wormholes + ", skip pulsar systems " + gatesNoPulsar + "), debug " + debug
                + "; steers for: " + (on.length() == 0 ? "nothing" : on));
        return true;
    }

    static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    static boolean bool(String id, boolean def) {
        if (useLuna) {
            try { Boolean b = BALuna.bool(id); if (b != null) return b.booleanValue(); } catch (Throwable t) { }
        }
        return file.optBoolean(id, def);
    }

    static int integer(String id, int def) {
        if (useLuna) {
            try { Integer i = BALuna.integer(id); if (i != null) return i.intValue(); } catch (Throwable t) { }
        }
        return file.optInt(id, def);
    }

    /** A value that is not a finite number uses the default. */
    static double dbl(String id, double def) {
        if (useLuna) {
            try {
                Double d = BALuna.dbl(id);
                if (d != null && !d.isNaN() && !d.isInfinite()) return d.doubleValue();
            } catch (Throwable t) { }
        }
        double v = file.optDouble(id, def);
        return Double.isNaN(v) || Double.isInfinite(v) ? def : v;
    }
}
