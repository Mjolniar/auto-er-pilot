package data.scripts.betterautopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.terrain.PulsarBeamTerrainPlugin;
import org.lwjgl.util.vector.Vector2f;

/**
 * A jump point into a star system whose exit is inside a pulsar beam: the fleet waits short of the jump point, outside
 * its reach, until the beam has turned past the exit (beams turn 10 to 20 degrees a day), then flies on and jumps. After
 * MAX_DAYS it jumps anyway. Jump points with more than one exit keep the game's choice and are not checked.
 */
final class BAJumpWait {
    /** Checked from this far out (plus the braking distance); the fleet stops GAP units outside the jump point's reach. */
    static final float LOOK = 2500f, GAP = 300f;
    /** Extra distance around the exit counted as in the beam (the beam turns while the fleet flies off). */
    static final float MARGIN = 1000f;
    static final float MAX_DAYS = 10f;

    private SectorEntityToken jp;
    private boolean waiting, gaveUp;
    private long since;

    /** The point to wait at ({x, y}), or null to fly on. speed and brake: the fleet's speed and braking acceleration. */
    float[] hold(CampaignFleetAPI fleet, SectorEntityToken step, float speed, float brake) {
        if (!(step instanceof JumpPointAPI)) { reset(null); return null; }
        if (step != jp) reset(step);
        if (gaveUp) return null;
        JumpPointAPI p = (JumpPointAPI) step;
        if (p.getDestinations() == null || p.getDestinations().size() != 1 || p.getDestinations().get(0) == null) return null;
        SectorEntityToken exit = p.getDestinations().get(0).getDestination();
        if (exit == null || exit.getContainingLocation() == null || exit.getContainingLocation().isHyperspace()) return null;
        Vector2f fl = fleet.getLocation(), jl = step.getLocation();
        float d = BAGeom.dist(fl.x, fl.y, jl.x, jl.y);
        float stopAt = step.getRadius() + fleet.getRadius() + GAP;
        if (!waiting && d > stopAt + LOOK + BAGeom.brakeDistance(speed, 0f, brake)) return null;
        if (!inBeam(exit, fleet.getRadius())) {
            if (waiting) {
                waiting = false;
                BAScript.LOG.info("Auto-er Pilot: the pulsar beam has passed the exit of " + step.getName() + "; flying on");
            }
            return null;
        }
        if (!waiting) {
            waiting = true;
            since = Global.getSector().getClock().getTimestamp();
            BAScript.LOG.info("Auto-er Pilot: waiting short of " + step.getName() + ": its exit in "
                    + exit.getContainingLocation().getName() + " is in a pulsar beam");
        } else if (Global.getSector().getClock().getElapsedDaysSince(since) > MAX_DAYS) {
            gaveUp = true;
            waiting = false;
            BAScript.LOG.info("Auto-er Pilot: the exit of " + step.getName() + " is still in a pulsar beam after "
                    + Math.round(MAX_DAYS) + " days; flying on");
            return null;
        }
        if (d <= stopAt) return new float[] {fl.x, fl.y};
        return new float[] {jl.x + (fl.x - jl.x) / d * stopAt, jl.y + (fl.y - jl.y) / d * stopAt};
    }

    /** The fleet waits for the jump point it flies to. */
    boolean waiting() { return waiting; }

    /** A pulsar beam in exit's location holds the exit, with the fleet's radius and MARGIN around it. */
    static boolean inBeam(SectorEntityToken exit, float fleetRadius) {
        LocationAPI loc = exit.getContainingLocation();
        if (loc == null) return false;
        Vector2f e = exit.getLocation();
        float r = exit.getRadius() + fleetRadius + MARGIN;
        for (CampaignTerrainAPI t : loc.getTerrainCopy()) {
            try {
                if (!(t.getPlugin() instanceof PulsarBeamTerrainPlugin)) continue;
                PulsarBeamTerrainPlugin b = (PulsarBeamTerrainPlugin) t.getPlugin();
                // the beam's arc test takes no radius: the exit and the points r to either side of it, across the beam
                Vector2f c = t.getLocation();
                float dx = e.x - c.x, dy = e.y - c.y, l = Math.max(1f, BAGeom.len(dx, dy));
                float sx = -dy / l * r, sy = dx / l * r;
                if (b.containsPoint(e, r) || b.containsPoint(new Vector2f(e.x + sx, e.y + sy), 0f)
                        || b.containsPoint(new Vector2f(e.x - sx, e.y - sy), 0f)) return true;
            } catch (Throwable x) { BAObstacles.bad(t, x); }
        }
        return false;
    }

    private void reset(SectorEntityToken step) {
        jp = step;
        waiting = false;
        gaveUp = false;
    }
}
