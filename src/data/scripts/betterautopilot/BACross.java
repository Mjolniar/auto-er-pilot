package data.scripts.betterautopilot;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * Slipstreams on the way that do not flow toward the target, for a fleet without a usable Reverse Polarity.
 * Target on this side of the stream: a circle on the stream so the route goes along its edge.
 * Target on the far side: one crossing, picked along the stream within SEARCH of where the way meets it, at the lowest
 * time cost (a gap or faded part costs no drift). The fleet lines up on this side, then goes straight across.
 */
final class BACross {
    static final float SEARCH = 4000f, SAMPLE = 250f, LOOK = 5000f;

    /** One plan step: a circle to route around, or a point to fly to (across: inside or entering the stream). */
    static final class Step {
        BAGeom.Circle circle;
        float x, y;
        boolean across;
        String why;
    }

    // the crossing in progress
    private SlipstreamTerrainPlugin2 s;
    private float ax, ay, fx, fy, cx, cy, nx, ny, half, core;
    boolean active;

    void reset() { active = false; s = null; }

    SlipstreamTerrainPlugin2 stream() { return active ? s : null; }

    /** rev: the fleet's polarity is reversed (the player's own Reverse Polarity), so the streams flow the other way. */
    Step plan(List<SlipstreamTerrainPlugin2> streams, CampaignFleetAPI fleet, float px, float py, float tx, float ty, float vf,
              boolean rev) {
        if (active && !streams.contains(s)) reset();
        if (active) {
            float progress = (px - cx) * nx + (py - cy) * ny;   // distance past the centre line toward the far side
            if (progress > half) reset();
            else {
                Step st = new Step();
                // along the stream from the crossing line (the flow is across n)
                float along = Math.abs(-(px - cx) * ny + (py - cy) * nx);
                // outside the stream and not at the crossing yet: line up first (also from within the margin band, so a
                // fleet beside the stream does not cut across diagonally to a crossing far along it)
                if ((progress < -half || (progress < -core && along > 300f)) && BAGeom.dist(px, py, ax, ay) > 300f) {
                    st.x = ax;
                    st.y = ay;
                    st.why = "slipstream: lining up to cross";
                } else {
                    st.x = fx;
                    st.y = fy;
                    st.across = true;
                    st.why = "slipstream: crossing";
                }
                return st;
            }
        }

        float direct = BAGeom.dist(px, py, tx, ty);
        BASlip.Hit hit = BASlip.firstHit(streams, px, py, tx, ty, Math.min(LOOK, direct), fleet.getRadius());
        if (hit == null) return null;
        float[] f = BASlip.flow(hit.s, hit.along, rev);
        if (f == null) return null;
        // a stream that carries toward the target is for the riding rules
        if (BAGeom.angleBetween(f[0], f[1], tx - px, ty - py) < 60f) return null;

        Vector2f c = hit.s.getPointAt(hit.along, 0f);
        if (c == null) return null;
        float n0x = -f[1], n0y = f[0];
        float sP = (px - c.x) * n0x + (py - c.y) * n0y, sT = (tx - c.x) * n0x + (ty - c.y) * n0y;
        float halfW = hit.s.getWidth(hit.along) / 2f;
        // the target is in the stream: it has to be entered. Near the tangent line but outside the stream (the stream
        // curves away) is not, so the side tests below decide.
        if (Math.abs(sT) < halfW && hit.s.containsPoint(new Vector2f(tx, ty), 0f)) return null;

        float margin = fleet.getRadius() + 300f;
        if (Math.signum(sP) == Math.signum(sT)) {
            Step st = new Step();
            st.circle = new BAGeom.Circle(c.x, c.y, halfW + fleet.getRadius() + 200f, hit.s, "slipstream");
            st.why = "slipstream: along its edge";
            return st;
        }

        // target across the stream: the cheapest crossing near where the way meets it
        float best = Float.MAX_VALUE;
        float total = hit.s.getTotalLength();
        for (float d = Math.max(0f, hit.along - SEARCH); d <= Math.min(total, hit.along + SEARCH); d += SAMPLE) {
            Vector2f cd = hit.s.getPointAt(d, 0f);
            float[] fd = BASlip.flow(hit.s, d, rev);
            if (cd == null || fd == null) continue;
            float hw = hit.s.getWidth(d) / 2f + margin;
            float[] cp = BAGeom.crossPoints(cd.x, cd.y, fd[0], fd[1], hw, tx, ty);
            if (hit.s.containsPoint(new Vector2f(cp[2], cp[3]), fleet.getRadius())) continue;   // the stream bends back here
            // the fleet must be on this side of the crossing (a curved stream can put it past one further along)
            if ((px - cd.x) * cp[0] + (py - cd.y) * cp[1] > -hit.s.getWidth(d) / 2f) continue;
            float b = hit.s.getFaderBrightness(d);
            float wind = b < 0.1f ? 0f
                    : Misc.getSpeedForBurnLevel(hit.s.getParams().burnLevel * hit.s.getWidthBasedSpeedMult(d) * Math.min(1f, b));
            float cost = BAGeom.crossCost(px, py, cp, hw, tx, ty, vf, wind);
            if (cost < best) {
                best = cost;
                s = hit.s;
                cx = cd.x; cy = cd.y; nx = cp[0]; ny = cp[1]; half = hw; core = hit.s.getWidth(d) / 2f;
                ax = cp[2]; ay = cp[3]; fx = cp[4]; fy = cp[5];
                active = true;
            }
        }
        if (!active) return null;
        Step st = new Step();
        st.x = ax;
        st.y = ay;
        st.why = "slipstream: lining up to cross";
        return st;
    }
}
