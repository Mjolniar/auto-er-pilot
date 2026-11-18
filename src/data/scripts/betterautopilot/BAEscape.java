package data.scripts.betterautopilot;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.EmergencyBurnAbility;
import com.fs.starfarer.api.impl.campaign.terrain.EventHorizonPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.PulsarBeamTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

/**
 * Getting out of wind hazards: a star corona or black hole event horizon (StarCoronaTerrainPlugin and its subclasses) and
 * a pulsar beam (PulsarBeamTerrainPlugin), which push or pull the fleet. Only while the fleet flies itself to a target.
 * Steps, cheapest first, each after STUCK_SECONDS without PROGRESS units toward the target:
 * 1. normal burn: Sustained Burn off (its acceleration is a fifth, while the corona wind keeps its full strength). Also
 *    at once, without waiting, when the target is within NEAR units. Sustained Burn is not turned on again inside.
 * 2. Emergency Burn, when usable and every ship has the CR it costs (no ship can take activation damage).
 * 3. pulsar beams only: Transverse Jump into hyperspace. When the course target is in the system it left, the course is
 *    cleared once the fleet is in hyperspace, so it does not fly back into the beam.
 * A step that can not be used is skipped. Each step costs what the game charges for it.
 */
final class BAEscape {
    /** The game's ids (Abilities.TRANSVERSE_JUMP, Abilities.EMERGENCY_BURN). */
    static final String TJ = "fracture_jump", EB = "emergency_burn";
    static final float PROGRESS = 100f, STUCK_SECONDS = 4f;
    /** Within this distance of the target in a wind hazard, Sustained Burn goes off at once. */
    static final float NEAR = 2000f;
    /** Seconds to wait for the jump to happen after the ability was used. */
    static final float JUMP_WAIT = 20f;

    private final BAGeom.Stuck stuck = new BAGeom.Stuck(PROGRESS, STUCK_SECONDS);
    private SectorEntityToken lastStep;
    /** The fleet is in a wind hazard this frame (Sustained Burn stays off). */
    private boolean inWind;
    private boolean ebTried, noted;
    /** The system the escape jump left; set from the jump order until the fleet is in hyperspace. */
    private LocationAPI jumpedFrom;
    private String jumpedFor;
    private float waitJump;

    /** An escape jump was ordered and the fleet is not in hyperspace yet. */
    boolean busy() { return jumpedFrom != null; }

    /** The fleet was in a corona, event horizon or pulsar beam on its way this frame. */
    boolean inWind() { return inWind; }

    /**
     * Every unpaused frame with the step the fleet flies itself to (null while the player steers or the game is busy).
     * True while an escape jump is under way: the steering stays off then.
     */
    boolean check(CampaignFleetAPI fleet, CampaignUIAPI ui, SectorEntityToken step, float amount, BAAbilities abilities) {
        inWind = false;
        if (jumpedFrom != null) return afterJump(fleet, ui, amount);
        if (!BASettings.enabled || step == null || fleet.isInHyperspace() || !(fleet.getContainingLocation() instanceof StarSystemAPI)) {
            reset();
            return false;
        }
        CampaignTerrainPlugin wind = windHolding(fleet);
        if (wind == null) { reset(); return false; }
        inWind = true;
        if (step != lastStep) { reset(); lastStep = step; }
        float dist = Misc.getDistance(fleet, step);
        AbilityPlugin sb = fleet.getAbility(BAAbilities.SB);
        boolean sbOn = sb != null && sb.isActive();
        String what = wind instanceof PulsarBeamTerrainPlugin ? "a pulsar beam"
                : (wind instanceof EventHorizonPlugin ? "an event horizon" : "a corona");

        // step 1, at once near the target: normal burn
        if (BASettings.windBurn && sbOn && dist < NEAR + step.getRadius()) {
            abilities.sbOff(fleet, "normal burn in " + what + " close to " + step.getName());
            sbOn = false;
        }
        if (!stuck.update(dist, amount)) return false;
        stuck.reset();
        // step 1 when held: normal burn
        if (BASettings.windBurn && sbOn) {
            abilities.sbOff(fleet, "held in " + what + " on the way to " + step.getName() + ", normal burn");
            return false;
        }
        // step 2: Emergency Burn, once per stay
        if (BASettings.windBurn && !ebTried) {
            ebTried = true;
            String why = emergencyBurn(fleet);
            if (why == null) {
                BAScript.LOG.info("Auto-er Pilot: held in " + what + " on the way to " + step.getName() + "; Emergency Burn");
                return false;
            }
            BAScript.LOG.info("Auto-er Pilot: held in " + what + " on the way to " + step.getName() + "; Emergency Burn skipped: " + why);
        }
        // step 3: Transverse Jump out of a pulsar beam
        if (wind instanceof PulsarBeamTerrainPlugin && BASettings.pulsarEscape) return transverseJump(fleet, step);
        if (!noted) {
            noted = true;
            BAScript.LOG.info("Auto-er Pilot: held in " + what + " on the way to " + step.getName() + "; nothing more to try");
        }
        return false;
    }

    /** Uses Emergency Burn; returns null when it started, else why not. */
    private static String emergencyBurn(CampaignFleetAPI fleet) {
        AbilityPlugin eb = fleet.getAbility(EB);
        if (eb == null) return "not known";
        if (!eb.isUsable()) return "on cooldown or short of fuel";
        if (BAAbilities.otherActive(fleet, eb)) return "another ability is on that it does not go with";
        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            if (Math.round(m.getRepairTracker().getCR() * 100f) < Math.round(EmergencyBurnAbility.getCRCost(m, fleet) * 100f)) {
                return m.getShipName() + " has less CR than it costs";
            }
        }
        eb.activate();
        return eb.isUsable() ? "it did not start" : null;   // usable afterwards: it did not start
    }

    private boolean transverseJump(CampaignFleetAPI fleet, SectorEntityToken step) {
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
            return false;
        }
        jumpedFrom = fleet.getContainingLocation();
        jumpedFor = step.getName();
        waitJump = JUMP_WAIT;
        BAScript.LOG.info("Auto-er Pilot: held in a pulsar beam on the way to " + step.getName() + "; Transverse Jump into hyperspace");
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
            reset();
            return false;
        }
        waitJump -= amount;
        if (waitJump <= 0f) {
            BAScript.LOG.info("Auto-er Pilot: Transverse Jump out of the pulsar beam did not happen");
            jumpedFrom = null;
            jumpedFor = null;
            reset();
            return false;
        }
        return true;
    }

    /**
     * The wind hazard the fleet is in, or null: a corona or event horizon (StarCoronaTerrainPlugin) or a pulsar beam, at
     * more than zero strength where the fleet is. A terrain that throws is skipped.
     */
    static CampaignTerrainPlugin windHolding(CampaignFleetAPI fleet) {
        LocationAPI loc = fleet.getContainingLocation();
        if (loc == null) return null;
        CampaignTerrainPlugin found = null;
        for (CampaignTerrainAPI t : loc.getTerrainCopy()) {
            try {
                CampaignTerrainPlugin p = t.getPlugin();
                if (p instanceof PulsarBeamTerrainPlugin) {
                    PulsarBeamTerrainPlugin b = (PulsarBeamTerrainPlugin) p;
                    if (b.containsEntity(fleet) && b.getIntensityAtPoint(fleet.getLocation()) > 0f) return b;   // beams first
                } else if (p instanceof StarCoronaTerrainPlugin && found == null) {
                    StarCoronaTerrainPlugin c = (StarCoronaTerrainPlugin) p;
                    if (c.containsEntity(fleet) && c.getIntensityAtPoint(fleet.getLocation()) > 0f) found = c;
                }
            } catch (Throwable e) { BAObstacles.bad(t, e); }
        }
        return found;
    }

    private void reset() {
        stuck.reset();
        lastStep = null;
        ebTried = false;
        noted = false;
    }
}
