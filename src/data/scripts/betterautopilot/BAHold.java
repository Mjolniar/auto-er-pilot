package data.scripts.betterautopilot;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.AbilityPlugin;

/**
 * After a Transverse Jump the player used (the game's, or another mod's ability whose id starts with fracture_jump), the
 * laid-in course stays, but the fleet waits where it came out until the player resumes it: the game takes the course up
 * again on its own after the jump, so its interaction target is cleared and the fleet held. The hold ends when the player
 * resumes the course (a key or mouse press within INPUT_NS before the fleet gets a target), lays in another course or
 * clears it. The pulsar beam escape (BAEscape) is not held.
 */
final class BAHold {
    static final String TJ_PREFIX = "fracture_jump";
    /** A target set within this long after a key or mouse press is the player's. */
    static final long INPUT_NS = 600_000_000L;

    private boolean jumping, holding;
    private LocationAPI from;
    private SectorEntityToken heldCourse;

    /** Every unpaused frame. ourJump: the pulsar beam escape ordered the jump. True while the fleet is held. */
    boolean check(CampaignFleetAPI fleet, CampaignUIAPI ui, BAAbilities abilities, boolean ourJump) {
        if (!BASettings.enabled || !BASettings.holdAfterJump) { reset(); return false; }
        if (!holding) {
            boolean tj = !ourJump && transverseInProgress(fleet);
            if (tj && !jumping) { jumping = true; from = fleet.getContainingLocation(); }
            if (!jumping || fleet.isInHyperspaceTransition()) return false;
            if (fleet.getContainingLocation() == from) {
                if (!tj) reset();   // the jump did not happen
                return false;
            }
            jumping = false;
            from = null;
            heldCourse = ui.getUltimateCourseTarget();
            if (heldCourse == null) return false;   // no course: nothing to hold
            holding = true;
            stop(fleet);
            BAScript.LOG.info("Auto-er Pilot: Transverse Jump by the player; course to " + heldCourse.getName()
                    + " kept, the fleet waits until you resume it");
            return true;
        }
        SectorEntityToken course = ui.getUltimateCourseTarget();
        if (course != heldCourse) {
            BAScript.LOG.info("Auto-er Pilot: wait after Transverse Jump ended: " + (course == null ? "course cleared" : "new course"));
            reset();
            return false;
        }
        if (fleet.getInteractionTarget() != null) {
            if (abilities.inputWithin(INPUT_NS)) {
                BAScript.LOG.info("Auto-er Pilot: course to " + course.getName() + " resumed");
                reset();
                return false;
            }
            stop(fleet);   // the game took the course up again on its own
        }
        return true;
    }

    /** The player's fleet has a Transverse Jump (any fracture_jump ability) under way. */
    static boolean transverseInProgress(CampaignFleetAPI fleet) {
        for (AbilityPlugin a : fleet.getAbilities().values()) {
            if (a == null || a.getId() == null || !a.getId().startsWith(TJ_PREFIX)) continue;
            if (a.isActiveOrInProgress()) return true;
        }
        return false;
    }

    private static void stop(CampaignFleetAPI fleet) {
        fleet.setInteractionTarget(null);
        fleet.setMoveDestination(fleet.getLocation().x, fleet.getLocation().y);
    }

    private void reset() {
        jumping = holding = false;
        from = null;
        heldCourse = null;
    }
}
