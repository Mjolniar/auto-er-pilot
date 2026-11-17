package data.scripts.betterautopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.JumpPointInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Jumps into and out of star systems without the jump point dialog. The dialog still opens when the game would show more
 * than the jump order: its hostile-fleet warning at the exit, the transponder question, too little fuel, a wormhole, an
 * unstable jump point, more than one exit, or the tutorial. The jump itself is the game's own (same fuel, course and
 * jump pay handling).
 */
public final class BAJump {
    private BAJump() { }

    static final Logger LOG = Global.getLogger(BAJump.class);
    /** The game's warning radius around a jump exit. */
    static final float EXIT_RADIUS = 1000f;
    /** The game's warning level: a hostile fleet at least a third of your strength. */
    static final int WARN_DANGER = 2;

    private static boolean resolved;
    private static Object jumpFirst, jumpConfirm;

    /** The game dialog's option ids for "Order a jump" (first exit) and the transponder question. Null when not found. */
    static Object jumpOption() { resolve(); return jumpFirst; }
    static Object confirmOption() { resolve(); return jumpConfirm; }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> c = Class.forName(JumpPointInteractionDialogPluginImpl.class.getName() + "$OptionId", false,
                    JumpPointInteractionDialogPluginImpl.class.getClassLoader());
            Object[] all = c.getEnumConstants();
            if (all != null) {
                for (Object o : all) {
                    String n = ((Enum<?>) o).name();
                    if ("JUMP_1".equals(n)) jumpFirst = o;
                    else if ("JUMP_CONFIRM".equals(n)) jumpConfirm = o;
                }
            }
        } catch (Throwable t) {
            LOG.warn("Auto-er Pilot: jump dialog options not found; jumps keep the dialog", t);
        }
        if (jumpFirst == null) LOG.warn("Auto-er Pilot: jump dialog option JUMP_1 not found; jumps keep the dialog");
    }

    /**
     * True when the jump point dialog for jp can be skipped: a jump into a star system (hyperspace jump point) or out of
     * one into hyperspace. Logs the reason when the dialog stays for a hostile exit, the transponder or fuel.
     */
    static boolean skipDialog(JumpPointAPI jp) {
        if (!BASettings.enabled || !BASettings.autoJump || jp == null) return false;
        if (jp.getDestinations() == null || jp.getDestinations().size() != 1) return false;
        JumpDestination dest = jp.getDestinations().get(0);
        SectorEntityToken exit = dest == null ? null : dest.getDestination();
        if (exit == null || exit.getContainingLocation() == null) return false;
        boolean into = jp.isInHyperspace();
        // into a system from hyperspace, or out of a system into hyperspace; other links keep the dialog
        if (into == exit.getContainingLocation().isHyperspace()) return false;
        String name = exit.getContainingLocation().getName();
        if (jp.isWormhole()) return false;
        if (jp.getMemoryWithoutUpdate().getBoolean(JumpPointInteractionDialogPluginImpl.UNSTABLE_KEY)) return false;
        if (TutorialMissionIntel.isTutorialInProgress()) return false;
        if (jumpOption() == null) return false;
        if (!into) {
            float cost = fuelCost();
            if (cost > Global.getSector().getPlayerFleet().getCargo().getFuel()) {
                LOG.info("Auto-er Pilot: jump dialog kept for " + name + ": fuel " + Math.round(cost) + " needed");
                return false;
            }
        }
        if (transponderQuestion(exit)) { LOG.info("Auto-er Pilot: jump dialog kept for " + name + ": transponder off"); return false; }
        if (danger(exit)) { LOG.info("Auto-er Pilot: jump dialog kept for " + name + ": hostile fleets near the exit"); return false; }
        return true;
    }

    /**
     * The game's exit warning (JumpPointInteractionDialogPluginImpl.showWarningIfNeeded): a fleet within 1000 units of
     * the exit, once one of them is hostile (its AI, its faction, or a pirate flag), at danger level 2 or more.
     */
    static boolean danger(SectorEntityToken exit) {
        LocationAPI loc = exit == null ? null : exit.getContainingLocation();
        if (loc == null) return false;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        boolean hostile = false;
        int maxDanger = 0;
        for (CampaignFleetAPI other : loc.getFleets()) {
            if (other == player) continue;
            if (Misc.getDistance(exit, other) >= EXIT_RADIUS) continue;
            if (other.getAI() != null) hostile |= other.getAI().isHostileTo(player);
            else hostile |= other.getFaction() != null && other.getFaction().isHostileTo(Factions.PLAYER);
            if (other.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PIRATE)) hostile = true;
            if (hostile) maxDanger = Math.max(maxDanger, Misc.getDangerLevel(other));
        }
        return maxDanger >= WARN_DANGER;
    }

    /** The game's fuel cost for a jump out of a system: fuel per light year, rounded, at least 1 when above 0. */
    static float fuelCost() {
        float cost = Global.getSector().getPlayerFleet().getLogistics().getFuelCostPerLightYear();
        float rounded = Math.round(cost);
        if (cost > 0 && rounded <= 0) rounded = 1;
        return rounded;
    }

    /** The game asks about the transponder: it is off and the exit's system has a market of another faction. */
    static boolean transponderQuestion(SectorEntityToken exit) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI loc = exit.getContainingLocation();
        if (loc == null || loc.isHyperspace() || player.isTransponderOn()) return false;
        for (MarketAPI market : Global.getSector().getEconomy().getMarkets(loc)) {
            if (market.isHidden()) continue;
            if (market.getFaction() != null && market.getFaction().isPlayerFaction()) continue;
            return true;
        }
        return false;
    }
}
