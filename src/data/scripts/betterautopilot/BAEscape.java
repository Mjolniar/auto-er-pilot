package data.scripts.betterautopilot;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.PulsarBeamTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

/**
 * Pulsar beam escape. While the fleet flies itself to a target and a pulsar beam holds it (no progress of PROGRESS units
 * toward the target for STUCK_SECONDS), Transverse Jump takes it into hyperspace, at the game's own fuel and CR cost.
 * When the course target is in the system it left, the course is cleared once the fleet is in hyperspace, so it does not
 * fly back into the beam. Only pulsar beams (PulsarBeamTerrainPlugin and mods' subclasses).
 */
final class BAEscape {
    /** The game's id for Transverse Jump (Abilities.TRANSVERSE_JUMP). */
    static final String TJ = "fracture_jump";
    static final float PROGRESS = 100f, STUCK_SECONDS = 6f;
    /** Seconds to wait for the jump to happen after the ability was used. */
    static final float JUMP_WAIT = 20f;

    private final BAGeom.Stuck stuck = new BAGeom.Stuck(PROGRESS, STUCK_SECONDS);
    private SectorEntityToken lastStep;
    /** The system the escape jump left; set from the jump order until the fleet is in hyperspace. */
    private LocationAPI jumpedFrom;
    private String jumpedFor;
    private float waitJump;
    private boolean noted;

    /** An escape jump was ordered and the fleet is not in hyperspace yet. */
    boolean busy() { return jumpedFrom != null; }

    /**
     * Every unpaused frame with the step the fleet flies itself to (null while the player steers or the game is busy).
     * True while an escape jump is under way: the steering stays off then.
     */
    boolean check(CampaignFleetAPI fleet, CampaignUIAPI ui, SectorEntityToken step, float amount) {
        if (jumpedFrom != null) return afterJump(fleet, ui, amount);
        if (!BASettings.enabled || !BASettings.pulsarEscape || step == null || fleet.isInHyperspace()
                || !(fleet.getContainingLocation() instanceof StarSystemAPI)) {
            reset();
            return false;
        }
        if (beamHolding(fleet) == null) { reset(); return false; }
        if (step != lastStep) { reset(); lastStep = step; }
        if (!stuck.update(Misc.getDistance(fleet, step), amount)) return false;

        AbilityPlugin tj = fleet.getAbility(TJ);
        if (tj == null || !tj.isUsable()) {
            if (!noted) {
                noted = true;
                BAScript.LOG.info("Auto-er Pilot: held in a pulsar beam on the way to " + step.getName() + "; Transverse Jump "
                        + (tj == null ? "not known" : "not usable (fuel or cooldown)"));
            }
            return false;
        }
        tj.activate();
        if (tj.isUsable()) {   // still usable: the jump did not start
            if (!noted) { noted = true; BAScript.LOG.info("Auto-er Pilot: Transverse Jump out of a pulsar beam did not start"); }
            stuck.reset();
            return false;
        }
        jumpedFrom = fleet.getContainingLocation();
        jumpedFor = step.getName();
        waitJump = JUMP_WAIT;
        BAScript.LOG.info("Auto-er Pilot: held in a pulsar beam for " + Math.round(stuck.since()) + " seconds on the way to "
                + step.getName() + "; Transverse Jump into hyperspace");
        stuck.reset();
        return true;
    }

    /** After the jump order: once in hyperspace, a course back into the system left is cleared. */
    private boolean afterJump(CampaignFleetAPI fleet, CampaignUIAPI ui, float amount) {
        if (fleet.isInHyperspace() && !fleet.isInHyperspaceTransition()) {
            SectorEntityToken course = ui.getUltimateCourseTarget();
            if (course != null && course.getContainingLocation() == jumpedFrom) {
                ui.clearLaidInCourse();
                BAScript.LOG.info("Auto-er Pilot: out of the pulsar beam; course to " + course.getName()
                        + " cleared (it leads back into " + jumpedFrom.getName() + ")");
            } else {
                BAScript.LOG.info("Auto-er Pilot: out of the pulsar beam on the way to " + jumpedFor);
            }
            jumpedFrom = null;
            jumpedFor = null;
            return false;
        }
        waitJump -= amount;
        if (waitJump <= 0f) {
            BAScript.LOG.info("Auto-er Pilot: Transverse Jump out of the pulsar beam did not happen");
            jumpedFrom = null;
            jumpedFor = null;
            return false;
        }
        return true;
    }

    /** The pulsar beam the fleet is in, or null. A beam that throws is skipped. */
    static PulsarBeamTerrainPlugin beamHolding(CampaignFleetAPI fleet) {
        LocationAPI loc = fleet.getContainingLocation();
        if (loc == null) return null;
        for (CampaignTerrainAPI t : loc.getTerrainCopy()) {
            try {
                if (!(t.getPlugin() instanceof PulsarBeamTerrainPlugin)) continue;
                PulsarBeamTerrainPlugin p = (PulsarBeamTerrainPlugin) t.getPlugin();
                if (p.containsEntity(fleet) && p.getIntensityAtPoint(fleet.getLocation()) > 0f) return p;
            } catch (Throwable e) { BAObstacles.bad(t, e); }
        }
        return null;
    }

    private void reset() {
        stuck.reset();
        lastStep = null;
        noted = false;
    }
}
