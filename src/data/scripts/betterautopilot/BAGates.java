package data.scripts.betterautopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.JumpPointInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.GateCMD;
import com.fs.starfarer.api.impl.campaign.terrain.PulsarBeamTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.lang.invoke.MethodHandle;
import java.lang.ref.WeakReference;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Routes laid-in courses through gates when the way through two gates of one network is shorter: course to the entry
 * gate, the transit (the network's own, same costs), then the course to the target again. Networks: the game's gates
 * (once the game offers "Travel through the Gate": $gatesActive and $playerCanUseGates) and AoTD's Bifrost gates (with
 * a Bifrost Control Center). The game's network also exits at gate beacons (non-gate places in its list of gate
 * destinations, such as a Random Assortment of Things settlement with a Gate Beacon). Each new course target is planned
 * once. A course laid in again for the same target while its route is under way flies direct. A route under way is kept
 * in the player fleet's memory, so it continues after a save load.
 */
public final class BAGates {
    private BAGates() { }

    static final Logger LOG = Global.getLogger(BAGates.class);
    /** Autopilot With Gates' toggle: while it is on, that mod routes through gates and this one does not. */
    static final String AWG_ABILITY = "AutoPilotWithGates";
    /** Distance counted for each jump through a jump point and for the gate transit (each takes a few seconds). */
    static final float JUMP_COST = 1000f, GATE_COST = 1000f;
    /** A gate route must be at least this many units shorter (one light year). */
    static final float MIN_GAIN = 2000f;
    /** Seconds to wait for the transit before the course to the target is laid in again. */
    static final float TRANSIT_WAIT = 30f;
    /** Seconds at the entry gate, on the course and not flying by hand, without its dialog before it is opened here. */
    static final float ARRIVAL_WAIT = 5f;
    /** The fleet counts as through the gate within this distance of the exit gate. */
    static final float ARRIVAL_RANGE = 3000f;

    /** AoTD Vaults of Knowledge: Bifrost gates (custom entity type, tag), the control center industry, gate memory keys. */
    static final String BIFROST_MOD = "aotd_vok", BIFROST_TYPE = "bifrost_gate", BIFROST_TAG = "bifrost",
            BIFROST_CENTER = "aotd_bfc_ind", BF_USED = "$used", BF_COOLDOWN = "$cooldown", BF_SUPPLIED = "$supplied";
    /** Days both Bifrost gates cool down after a transit (BiFrostCMD). */
    static final float BIFROST_COOLDOWN = 30f;

    /** The route under way, in the player fleet's memory. */
    static final String K_GOAL = "$ba_gateGoal", K_ENTRY = "$ba_gateEntry", K_EXIT = "$ba_gateExit", K_BIFROST = "$ba_gateBifrost";

    static final int NONE = 0, TO_ENTRY = 1, TRANSIT = 2;
    static int stage = NONE;
    /**
     * The last course target planned for (planned once per new target), and the target, entry and exit of the route
     * under way. Held weakly: a static strong reference would keep an old save's sector in memory after quitting to the
     * menu or while another save loads. The route's places are also in the player fleet's memory, which holds them.
     */
    private static WeakReference<SectorEntityToken> plannedRef = none(), goalRef = none(), entryRef = none(), exitRef = none();
    private static WeakReference<SectorEntityToken> none() { return new WeakReference<SectorEntityToken>(null); }
    static SectorEntityToken planned() { return plannedRef.get(); }
    static SectorEntityToken goal() { return goalRef.get(); }
    static SectorEntityToken entry() { return entryRef.get(); }
    static SectorEntityToken exit() { return exitRef.get(); }
    static void setPlanned(SectorEntityToken e) { plannedRef = new WeakReference<SectorEntityToken>(e); }
    static void setGoal(SectorEntityToken e) { goalRef = new WeakReference<SectorEntityToken>(e); }
    static void setEntry(SectorEntityToken e) { entryRef = new WeakReference<SectorEntityToken>(e); }
    static void setExit(SectorEntityToken e) { exitRef = new WeakReference<SectorEntityToken>(e); }
    /** The route uses Bifrost gates. */
    static boolean bifrost;
    static float waited;
    /** Seconds before a new course is planned, after one was laid in during a transit (the jump has a delay). */
    static float holdPlan;
    static final float PLAN_HOLD = 5f;
    /** Lay in the course to goal once no dialog is open (the gate dialog was shown instead of the transit). */
    static boolean restoreGoal;
    /** The route was taken up from the save this frame (load); its first advance lays in a missing course. */
    static boolean fromSave;
    private static boolean awgNoted;

    static void reset() {
        stage = NONE;
        setPlanned(null); setGoal(null); setEntry(null); setExit(null);
        bifrost = false;
        waited = 0f;
        holdPlan = 0f;
        restoreGoal = false;
        fromSave = false;
        awgNoted = false;
    }

    /** On game load: clears the state, then takes up a route saved under way. */
    static void load() {
        reset();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;
        MemoryAPI m = fleet.getMemoryWithoutUpdate();
        Object g = m.get(K_GOAL), a = m.get(K_ENTRY), b = m.get(K_EXIT);
        if (g instanceof SectorEntityToken && a instanceof SectorEntityToken && b instanceof SectorEntityToken) {
            setGoal((SectorEntityToken) g);
            setEntry((SectorEntityToken) a);
            setExit((SectorEntityToken) b);
            bifrost = Boolean.TRUE.equals(m.get(K_BIFROST));
            stage = TO_ENTRY;
            fromSave = true;
            setPlanned(entry());
            LOG.info("Auto-er Pilot: gate route to " + goal().getName() + " taken up from the save: " + where(entry()) + " to " + where(exit()));
        } else {
            unsetMemory(m);
        }
    }

    /** Every unpaused frame, also with the mod or gate routing off (a route under way still gets its course back). */
    static void advance(CampaignFleetAPI fleet, CampaignUIAPI ui, float amount) {
        if (holdPlan > 0f) holdPlan -= amount;
        boolean saved = fromSave;
        fromSave = false;
        if (stage != NONE && (goal() == null || entry() == null || exit() == null)) {
            LOG.info("Auto-er Pilot: gate route dropped: its target or a gate is gone");
            restoreGoal = false;
            clear();
        }
        if (restoreGoal) {
            if (ui.isShowingDialog()) return;
            restoreGoal = false;
            SectorEntityToken g = goal();
            clear();
            layIn(g);
        }
        SectorEntityToken course = ui.getUltimateCourseTarget();
        if (stage == TRANSIT) {
            waited += amount;
            if (throughGate(fleet)) {
                LOG.info("Auto-er Pilot: through the gate to " + where(exit()) + "; course laid in for " + goal().getName());
                finish();
            } else if (course != null) {
                LOG.info("Auto-er Pilot: gate route to " + goal().getName() + " ended: new course");
                setPlanned(course == goal() ? goal() : null);   // the same target again: direct
                holdPlan = PLAN_HOLD;                      // planned from where the fleet comes out
                clear();
            } else if (waited > TRANSIT_WAIT) {
                LOG.info("Auto-er Pilot: gate transit did not happen; course laid in for " + goal().getName());
                finish();
            }
            return;
        }
        boolean on = BASettings.enabled && BASettings.gates;
        if (stage == TO_ENTRY) {
            if (course == null && (saved || throughGate(fleet))) {
                // saved during the transit (the fleet is at the exit gate), or saved with no course while the route waited
                // for its course back: the course to the target is laid in
                LOG.info("Auto-er Pilot: " + (saved ? "route taken up from the save without a course" : "at " + where(exit()))
                        + "; course laid in for " + goal().getName());
                finish();
                return;
            }
            if (!on) {
                LOG.info("Auto-er Pilot: gate route to " + goal().getName() + " ended: gate routing off; course laid in for it");
                finish();
                return;
            }
            if (course == entry()) {
                if (!usable(entry(), bifrost) || !exitUsable(exit(), bifrost)) {
                    LOG.info("Auto-er Pilot: gate route to " + goal().getName() + " ended: a gate can not be used now; course laid in for it");
                    finish();
                    return;
                }
                // the gate's dialog opens on arrival (pickDialog). At the gate on the course, not flying by hand, and still
                // no dialog after ARRIVAL_WAIT: open it here. At the gate with no interaction target after ARRIVAL_WAIT: a
                // dialog at the gate closed without the transit (another dialog, or the player left it), so the route ends
                // and the course to the target is laid in.
                if (!near(fleet, entry()) || ui.isFollowingDirectCommand()) { waited = 0f; return; }
                SectorEntityToken it = fleet.getInteractionTarget();
                if (it != entry() && it != null) { waited = 0f; return; }
                waited += amount;
                if (waited < ARRIVAL_WAIT || ui.isShowingDialog()) return;
                waited = 0f;
                if (it == null) {
                    LOG.info("Auto-er Pilot: no transit at " + where(entry()) + ": the gate's dialog closed; course laid in for " + goal().getName());
                    finish();
                    return;
                }
                String why = blocked();
                if (why != null) {
                    LOG.info("Auto-er Pilot: no transit at " + where(entry()) + ": " + why + "; course laid in for " + goal().getName());
                    finish();
                    return;
                }
                ui.showInteractionDialog(new BAGateDialog(), entry());
                return;
            }
            // the player laid in another course or cleared it
            LOG.info("Auto-er Pilot: gate route to " + goal().getName() + " ended: course changed");
            setPlanned(course == goal() ? goal() : null);   // the same target again: direct this time
            clear();
        }
        if (course == null) { setPlanned(null); return; }
        if (course == planned()) return;
        if (!on) { setPlanned(course); return; }
        if (holdPlan > 0f) return;
        if (fleet.isInHyperspaceTransition()) return;   // plan once the jump is over, from where the fleet comes out
        setPlanned(course);
        plan(fleet, ui, course);
    }

    /** After an error: the route is dropped, the course to its target laid in again, the current target not planned again. */
    static void error(CampaignUIAPI ui) {
        SectorEntityToken g = goal();
        stage = NONE;
        restoreGoal = false;
        try { clear(); } catch (Throwable t) { setGoal(null); setEntry(null); setExit(null); }
        setPlanned(ui.getUltimateCourseTarget());
        if (g != null) {
            try { layIn(g); } catch (Throwable t) { }
        }
    }

    /** A candidate route: entry and exit gate, their spots, the distance through them, the network. */
    static final class Route {
        SectorEntityToken in, out;
        Place pin, pout;
        float via;
        boolean bf;
    }

    /** Plans the gate route for a new course target and lays in the course to the entry gate when it is shorter. */
    static void plan(CampaignFleetAPI fleet, CampaignUIAPI ui, SectorEntityToken target) {
        if (target.getContainingLocation() == null || fleet.getContainingLocation() == null) return;
        AbilityPlugin awg = fleet.getAbility(AWG_ABILITY);
        if (awg != null && awg.isActive()) {
            if (!awgNoted) { awgNoted = true; LOG.info("Auto-er Pilot: Autopilot With Gates is on; gate routing left to it"); }
            return;
        }
        Place f = Place.of(fleet), t = Place.of(target);
        Route r = best(f, t, gameGates(), false);
        Route rb = best(f, t, bifrostGates(), true);
        if (r == null || (rb != null && rb.via < r.via)) r = rb;
        if (r == null) return;
        float direct = leg(f, t);
        if (!BAGeom.gateWorth(direct, r.via, BASettings.gateSaving / 100f, MIN_GAIN)) {
            if (BASettings.debug) LOG.info("Auto-er Pilot: no gate route to " + target.getName() + ": " + Math.round(r.via)
                    + " units through " + where(r.in) + " and " + where(r.out) + ", " + Math.round(direct) + " direct");
            return;
        }
        float perLY = fleet.getLogistics().getFuelCostPerLightYear();
        float need = perLY * (hyperLY(f, r.pin) + hyperLY(r.pout, t));
        if (!r.bf) need += (float) Math.ceil(Misc.getDistanceLY(r.in, r.out) * perLY * Misc.GATE_FUEL_COST_MULT);
        if (need > fleet.getCargo().getFuel()) {
            LOG.info("Auto-er Pilot: no gate route to " + target.getName() + ": needs about " + Math.round(need) + " fuel");
            return;
        }
        setGoal(target);
        setEntry(r.in);
        setExit(r.out);
        bifrost = r.bf;
        stage = TO_ENTRY;
        waited = 0f;
        setPlanned(entry());
        Global.getSector().layInCourseFor(entry());
        SectorEntityToken next = null;
        try { next = ui.getNextStepForCourse(entry()); } catch (Throwable e) { next = entry(); }
        if (next == null) {
            LOG.info("Auto-er Pilot: no gate route to " + target.getName() + ": the game finds no course to " + where(entry()));
            SectorEntityToken g = goal();
            clear();
            layIn(g);
            return;
        }
        MemoryAPI m = fleet.getMemoryWithoutUpdate();
        m.set(K_GOAL, goal());
        m.set(K_ENTRY, entry());
        m.set(K_EXIT, exit());
        m.set(K_BIFROST, bifrost);
        LOG.info("Auto-er Pilot: " + (bifrost ? "Bifrost" : "gate") + " route to " + target.getName() + ": " + where(entry())
                + " to " + where(exit()) + (beacon(exit()) ? " (gate beacon)" : "") + ", " + Math.round(r.via) + " units, direct "
                + (direct == Float.MAX_VALUE ? "no way" : Math.round(direct) + " units"));
    }

    /** The shortest way from f to t through two gates of one network, or null. A gate beacon is an exit only. */
    static Route best(Place f, Place t, List<SectorEntityToken> gates, boolean bf) {
        int n = gates.size();
        if (n < 2) return null;
        float[] to = new float[n], from = new float[n];
        Place[] g = new Place[n];
        for (int i = 0; i < n; i++) {
            g[i] = Place.of(gates.get(i));
            to[i] = !bf && beacon(gates.get(i)) ? Float.MAX_VALUE : leg(f, g[i]);
            from[i] = leg(g[i], t);
        }
        int[] p = BAGeom.bestPair(to, from);
        if (p == null) return null;
        Route r = new Route();
        r.in = gates.get(p[0]);
        r.out = gates.get(p[1]);
        r.pin = g[p[0]];
        r.pout = g[p[1]];
        r.via = to[p[0]] + GATE_COST + from[p[1]];
        r.bf = bf;
        return r;
    }

    /** The dialog for the entry gate on arrival: the transit, or null (the gate's own dialog) when it can not go now. */
    static InteractionDialogPlugin pickDialog(SectorEntityToken target) {
        if (stage != TO_ENTRY || target == null || target != entry()) return null;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || !near(fleet, entry())) return null;
        String why = blocked();
        if (why != null) {
            LOG.info("Auto-er Pilot: gate dialog shown at " + where(entry()) + ": " + why + "; course for " + goal().getName() + " laid in after");
            stage = NONE;
            restoreGoal = true;
            return null;
        }
        return new BAGateDialog();
    }

    /** Why the transit can not go now, or null. */
    static String blocked() {
        if (!BASettings.enabled || !BASettings.gates) return "gate routing off";
        if (bifrost) {
            if (!bifrostOpen()) return "Bifrost network not usable (no Bifrost Control Center, or the setting is off)";
            if (!usable(entry(), true) || !usable(exit(), true)) return "a Bifrost gate is cooling down or not supplied";
            Set<SectorEntityToken> act = bifrostActive();
            if (act != null && (!act.contains(entry()) || !act.contains(exit()))) return "a Bifrost gate's section is not active";
        } else {
            if (!gameGatesOpen()) return "gates not usable";
            if (!usable(entry(), false) || !exitUsable(exit(), false)) return "a gate is not active";
            int cost = GateCMD.computeFuelCost(exit());
            if (cost > Global.getSector().getPlayerFleet().getCargo().getFuel()) return "not enough fuel (" + cost + ")";
        }
        if (skipPulsar(entry()) || skipPulsar(exit())) return "a gate is in a pulsar system";
        if (BAJump.danger(exit())) return "hostile fleets near " + where(exit());
        return null;
    }

    /** The network's transit: GateCMD's or BiFrostCMD's pickedEntity, in the same order. */
    static void transit(InteractionDialogAPI dialog) {
        String why = blocked();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (why != null) {
            LOG.info("Auto-er Pilot: no transit at " + where(entry()) + ": " + why + "; course for " + goal().getName() + " laid in");
            dialog.dismiss();
            Global.getSector().setPaused(false);
            stage = NONE;
            restoreGoal = true;
            return;
        }
        SectorEntityToken from = entry(), to = exit();
        String cost;
        if (bifrost) {
            dialog.dismiss();
            from.getMemoryWithoutUpdate().set(BF_USED, true);
            to.getMemoryWithoutUpdate().set(BF_USED, true);
            from.getMemoryWithoutUpdate().set(BF_COOLDOWN, BIFROST_COOLDOWN);
            to.getMemoryWithoutUpdate().set(BF_COOLDOWN, BIFROST_COOLDOWN);
            cost = "no fuel";
        } else {
            int fuel = GateCMD.computeFuelCost(to);
            fleet.getCargo().removeFuel(fuel);
            dialog.dismiss();
            cost = fuel + " fuel";
        }
        Global.getSector().setPaused(false);
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui.getUltimateCourseTarget() == from) ui.clearLaidInCourse();
        Global.getSector().doHyperspaceTransition(fleet, from, new JumpDestination(to, null), 2f);
        float distLY = Misc.getDistanceLY(to, from);
        showBeingUsed(to, distLY);
        showBeingUsed(from, distLY);
        ListenerUtil.reportFleetTransitingGate(fleet, from, to);
        stage = TRANSIT;
        waited = 0f;
        LOG.info("Auto-er Pilot: " + (bifrost ? "Bifrost" : "gate") + " transit " + where(from) + " to " + where(to) + ", " + cost);
    }

    /** The gate's "in use" effect: GateEntityPlugin directly, Bifrost's plugin through a method handle (visual only). */
    static void showBeingUsed(SectorEntityToken gate, float distLY) {
        Object p = gate.getCustomPlugin();
        if (p instanceof GateEntityPlugin) { ((GateEntityPlugin) p).showBeingUsed(distLY); return; }
        if (p == null || beacon(gate)) return;
        try {
            MethodHandle h = MethodHandles.publicLookup().findVirtual(p.getClass(), "showBeingUsed",
                    MethodType.methodType(void.class, float.class));
            h.invoke(p, distLY);
        } catch (Throwable t) { }
    }

    /** The transit failed with an error: the course to the target is laid in again. */
    static void failed() {
        if (goal() == null) return;
        stage = NONE;
        restoreGoal = true;
    }

    /** The fleet came out of the transit next to the exit gate. */
    private static boolean throughGate(CampaignFleetAPI fleet) {
        return exit() != null && fleet.getContainingLocation() == exit().getContainingLocation() && !fleet.isInHyperspaceTransition()
                && Misc.getDistance(fleet, exit()) < ARRIVAL_RANGE;
    }

    /** Ends the route and lays in the course to its target. */
    private static void finish() {
        SectorEntityToken g = goal();
        clear();
        layIn(g);
    }

    private static void layIn(SectorEntityToken target) {
        if (target == null || target.isExpired()) return;
        setPlanned(target);
        Global.getSector().layInCourseFor(target);
    }

    private static void clear() {
        stage = NONE;
        setGoal(null); setEntry(null); setExit(null);
        bifrost = false;
        waited = 0f;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null) unsetMemory(fleet.getMemoryWithoutUpdate());
    }

    private static void unsetMemory(MemoryAPI m) {
        m.unset(K_GOAL);
        m.unset(K_ENTRY);
        m.unset(K_EXIT);
        m.unset(K_BIFROST);
    }

    /** The game offers "Travel through the Gate": the Janus Device works ($gatesActive) and the player may use gates. */
    static boolean gameGatesOpen() {
        MemoryAPI g = Global.getSector().getMemoryWithoutUpdate();
        return g.getBoolean(GateEntityPlugin.GATES_ACTIVE) && g.getBoolean(GateEntityPlugin.PLAYER_CAN_USE_GATES);
    }

    /** AoTD Vaults of Knowledge is on, the setting allows it and a player colony has a Bifrost Control Center. */
    static boolean bifrostOpen() {
        if (!BASettings.bifrost || !Global.getSettings().getModManager().isModEnabled(BIFROST_MOD)) return false;
        for (MarketAPI m : Misc.getPlayerMarkets(true)) {
            if (m.hasIndustry(BIFROST_CENTER)) return true;
        }
        return false;
    }

    /** Scanned, active gates of the game and gate beacons, once the game lets the player use gates. */
    /**
     * With the setting on, gates (game gates, gate beacons, Bifrost gates) in a star system with a pulsar beam are not used
     * for gate routes: the fleet can come out in the middle of the beam.
     */
    static boolean skipPulsar(SectorEntityToken g) {
        if (!BASettings.gatesNoPulsar || g == null) return false;
        LocationAPI loc = g.getContainingLocation();
        if (loc == null || loc.isHyperspace()) return false;
        for (CampaignTerrainAPI t : loc.getTerrainCopy()) {
            if (t.getPlugin() instanceof PulsarBeamTerrainPlugin) return true;
        }
        return false;
    }

    static List<SectorEntityToken> gameGates() {
        if (!gameGatesOpen()) return Collections.emptyList();
        List<SectorEntityToken> out = new ArrayList<SectorEntityToken>();
        for (SectorEntityToken g : GateEntityPlugin.getGateData().scanned) {
            if ((usable(g, false) || beacon(g)) && !skipPulsar(g)) out.add(g);
        }
        return out;
    }

    /**
     * A gate beacon: a place in the game's list of gate destinations that is not a gate. Random Assortment of Things adds
     * a Frontiers settlement with a Gate Beacon to the list. The game's gate dialog offers it as a destination, so it is
     * an exit of the game's network, never an entry.
     */
    static boolean beacon(SectorEntityToken e) {
        if (e == null || e.isExpired() || e.getContainingLocation() == null) return false;
        if (e.getCustomPlugin() instanceof GateEntityPlugin) return false;
        return GateEntityPlugin.getGateData().scanned.contains(e);
    }

    /** An exit that can take a transit now: a usable gate of the network, or a gate beacon on the game's network. */
    static boolean exitUsable(SectorEntityToken g, boolean bf) {
        return usable(g, bf) || (!bf && beacon(g));
    }

    /** Bifrost gates that can be used now, with a Bifrost Control Center: gates of active sections only, as AoTD offers. */
    static List<SectorEntityToken> bifrostGates() {
        if (!bifrostOpen()) return Collections.emptyList();
        Set<SectorEntityToken> act = bifrostActive();
        List<SectorEntityToken> out = new ArrayList<SectorEntityToken>();
        for (SectorEntityToken g : Global.getSector().getCustomEntitiesWithTag(BIFROST_TAG)) {
            if (usable(g, true) && (act == null || act.contains(g)) && !skipPulsar(g)) out.add(g);
        }
        return out;
    }

    /** AoTD's Bifrost megastructure classes: BiFrostCMD's destination picker offers the gates of the active sections. */
    static final String BF_MANAGER = "data.kaysaar.aotd.vok.campaign.econ.megastructures.impl.scripts.BifrostMegastructureManager",
            BF_MEGA = "data.kaysaar.aotd.vok.campaign.econ.megastructures.impl.scripts.BifrostMegastructure",
            BF_SECTION = "data.kaysaar.aotd.vok.campaign.econ.megastructures.impl.sections.bifrost.BifrostSection";
    private static MethodHandle bfInstance, bfMega, bfActive, bfGate;
    private static boolean bfResolved, bfNoted;

    /**
     * The gates of AoTD's active Bifrost sections (not restoring, not disabled), read through method handles. Null when
     * AoTD's classes can not be read; the gate memory flags alone decide then.
     */
    static Set<SectorEntityToken> bifrostActive() {
        try {
            if (!bfResolved) {
                bfResolved = true;
                ClassLoader cl = Global.getSettings().getScriptClassLoader();
                if (cl == null) cl = BAGates.class.getClassLoader();
                Class<?> mgr = Class.forName(BF_MANAGER, false, cl), mega = Class.forName(BF_MEGA, false, cl),
                        sec = Class.forName(BF_SECTION, false, cl);
                MethodHandles.Lookup lk = MethodHandles.publicLookup();
                bfInstance = lk.findStatic(mgr, "getInstance", MethodType.methodType(mgr));
                bfMega = lk.findVirtual(mgr, "getMegastructure", MethodType.methodType(mega));
                bfActive = lk.findVirtual(mega, "getActiveSections", MethodType.methodType(ArrayList.class));
                bfGate = lk.findVirtual(sec, "getGateTiedTo", MethodType.methodType(SectorEntityToken.class));
            }
            if (bfGate == null) return null;
            Object m = bfInstance.invoke();
            Object mega = m == null ? null : bfMega.invoke(m);
            Set<SectorEntityToken> out = new HashSet<SectorEntityToken>();
            if (mega == null) return out;
            Object list = bfActive.invoke(mega);
            if (!(list instanceof List)) return out;
            for (Object s : (List<?>) list) {
                Object g = s == null ? null : bfGate.invoke(s);
                if (g instanceof SectorEntityToken) out.add((SectorEntityToken) g);
            }
            return out;
        } catch (Throwable t) {
            bfGate = null;
            if (!bfNoted) { bfNoted = true; LOG.warn("Auto-er Pilot: AoTD's Bifrost sections can not be read; the gates' own flags decide", t); }
            return null;
        }
    }

    /**
     * A gate that can take a transit now. Game gate: scanned and active. Bifrost gate: not used ($used), not cooling down
     * ($cooldown) and supplied ($supplied), the conditions of AoTD's "Use the Bifrost Network" option.
     */
    static boolean usable(SectorEntityToken g, boolean bf) {
        if (g == null || g.isExpired() || g.getContainingLocation() == null) return false;
        if (!bf) return GateEntityPlugin.isActive(g);
        if (!BIFROST_TYPE.equals(g.getCustomEntityType())) return false;
        MemoryAPI m = g.getMemoryWithoutUpdate();
        if (Boolean.TRUE.equals(m.get(BF_USED))) return false;
        Object cd = m.get(BF_COOLDOWN);
        if (cd instanceof Number && ((Number) cd).floatValue() > 0f) return false;
        return Boolean.TRUE.equals(m.get(BF_SUPPLIED));
    }

    static boolean near(CampaignFleetAPI fleet, SectorEntityToken e) {
        if (e == null || fleet.getContainingLocation() != e.getContainingLocation()) return false;
        return Misc.getDistance(fleet, e) < fleet.getRadius() + e.getRadius() + 500f;
    }

    static String where(SectorEntityToken e) {
        if (e == null) return "?";
        LocationAPI loc = e.getContainingLocation();
        return e.getName() + (loc == null ? "" : " (" + loc.getName() + ")");
    }

    /** A spot: its location, its position there and its position in hyperspace. */
    static final class Place {
        final LocationAPI loc;
        final float x, y, hx, hy;

        Place(LocationAPI loc, float x, float y, float hx, float hy) {
            this.loc = loc; this.x = x; this.y = y; this.hx = hx; this.hy = hy;
        }

        static Place of(SectorEntityToken e) {
            Vector2f p = e.getLocation(), h = e.getLocationInHyperspace();
            return new Place(e.getContainingLocation(), p.x, p.y, h.x, h.y);
        }
    }

    /**
     * Travel distance from p to q: straight within one location, otherwise to the nearest jump point, through hyperspace
     * and from the nearest jump point there, plus JUMP_COST per jump. Float.MAX_VALUE when a system has no way out.
     */
    static float leg(Place p, Place q) {
        if (p.loc == null || q.loc == null) return Float.MAX_VALUE;
        if (p.loc == q.loc) return BAGeom.dist(p.x, p.y, q.x, q.y);
        float c = BAGeom.dist(p.hx, p.hy, q.hx, q.hy);
        if (!p.loc.isHyperspace()) {
            float e = toJumpPoint(p.loc, p.x, p.y);
            if (e == Float.MAX_VALUE) return Float.MAX_VALUE;
            c += e + JUMP_COST;
        }
        if (!q.loc.isHyperspace()) {
            float e = toJumpPoint(q.loc, q.x, q.y);
            if (e == Float.MAX_VALUE) return Float.MAX_VALUE;
            c += e + JUMP_COST;
        }
        return c;
    }

    /** Light years of hyperspace travel from p to q. */
    static float hyperLY(Place p, Place q) {
        float units;
        if (p.loc == q.loc) units = p.loc != null && p.loc.isHyperspace() ? BAGeom.dist(p.x, p.y, q.x, q.y) : 0f;
        else units = BAGeom.dist(p.hx, p.hy, q.hx, q.hy);
        return units / Misc.getUnitsPerLightYear();
    }

    /** Distance from (x, y) to the nearest usable jump point to hyperspace in loc. */
    static float toJumpPoint(LocationAPI loc, float x, float y) {
        float best = Float.MAX_VALUE;
        for (SectorEntityToken e : loc.getJumpPoints()) {
            if (!(e instanceof JumpPointAPI)) continue;
            JumpPointAPI jp = (JumpPointAPI) e;
            if (jp.isWormhole() || jp.getMemoryWithoutUpdate().getBoolean(JumpPointInteractionDialogPluginImpl.UNSTABLE_KEY)) continue;
            boolean toHyper = false;
            for (JumpDestination d : jp.getDestinations()) {
                SectorEntityToken t = d.getDestination();
                if (t != null && t.getContainingLocation() != null && t.getContainingLocation().isHyperspace()) { toHyper = true; break; }
            }
            if (!toHyper) continue;
            Vector2f p = jp.getLocation();
            best = Math.min(best, BAGeom.dist(x, y, p.x, p.y));
        }
        return best;
    }
}
