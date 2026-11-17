package data.scripts.betterautopilot;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.impl.campaign.abilities.ReversePolarityToggle;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2.SlipstreamSegment;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/** Slipstreams: where the fleet is in one, where to leave it, and which one to enter. */
final class BASlip {
    private BASlip() { }

    static final int MAX = 120;
    static final float SAMPLE = 400f;

    /** Position in a slipstream: distance along it and side offset (-1 to 1). */
    static final class Pos {
        final SlipstreamTerrainPlugin2 s;
        final float along, side;
        Pos(SlipstreamTerrainPlugin2 s, float along, float side) { this.s = s; this.along = along; this.side = side; }
    }

    static List<SlipstreamTerrainPlugin2> streams(LocationAPI loc) {
        List<SlipstreamTerrainPlugin2> out = new ArrayList<SlipstreamTerrainPlugin2>();
        for (CampaignTerrainAPI t : loc.getTerrainCopy()) {
            if (t.getPlugin() instanceof SlipstreamTerrainPlugin2) out.add((SlipstreamTerrainPlugin2) t.getPlugin());
        }
        return out;
    }

    /** The game's own push strength at a place in the stream (0 to about 1.5). */
    static float intensity(SlipstreamTerrainPlugin2 s, float along, float side) {
        return s.getIntensity(side) * s.getWidthBasedSpeedMult(along) * s.getFaderBrightness(along);
    }

    /** The stream that pushes the fleet at p (the game's 0.05 intensity floor), or null. */
    static Pos inside(List<SlipstreamTerrainPlugin2> streams, Vector2f p) {
        for (SlipstreamTerrainPlugin2 s : streams) {
            if (!s.containsPoint(p, 0f)) continue;
            float[] c = s.getLengthAndWidthFractionWithinStream(p);
            if (c == null || intensity(s, c[0], c[1]) <= 0.05f) continue;
            return new Pos(s, c[0], c[1]);
        }
        return null;
    }

    /** Unit flow direction at distance d, or null near the ends. Reverse polarity flows the other way. */
    static float[] flow(SlipstreamTerrainPlugin2 s, float d, boolean reverse) {
        Vector2f a = s.getPointAt(d, 0f), b = s.getPointAt(reverse ? d - 1f : d + 1f, 0f);
        if (a == null || b == null) return null;
        float dx = b.x - a.x, dy = b.y - a.y, l = BAGeom.len(dx, dy);
        if (l <= 0f) return null;
        return new float[] {dx / l, dy / l};
    }

    /**
     * Best exit downstream from distance d: {index, time, x, y}; index 0 means leave at d. Samples every SAMPLE units until
     * the stream fades (brightness under 0.3) or ends. Stream speed: the game's wind burn (burn level x width speed mult x
     * brightness) plus the fleet's burn above its slowest ship; with reverse polarity upstream at the game's 0.75 of that.
     */
    static float[] ride(SlipstreamTerrainPlugin2 s, float d, boolean reverse, CampaignFleetAPI fleet, float tx, float ty, float vf) {
        float[] x = new float[MAX], y = new float[MAX], step = new float[MAX], speed = new float[MAX];
        float bonus = Math.max(0f, fleet.getFleetData().getBurnLevel() - fleet.getFleetData().getMinBurnLevel());
        float total = s.getTotalLength();
        int n = 0;
        float dd = d;
        while (n < MAX && dd >= 0f && dd <= total) {
            Vector2f q = s.getPointAt(dd, 0f);
            if (q == null) break;
            float b = s.getFaderBrightness(dd);
            if (n > 0 && b < 0.3f) break;
            x[n] = q.x;
            y[n] = q.y;
            step[n] = n == 0 ? 0f : SAMPLE;
            speed[n] = Misc.getSpeedForBurnLevel(s.getParams().burnLevel * s.getWidthBasedSpeedMult(dd) * Math.min(1f, b) + bonus)
                    * (reverse ? ReversePolarityToggle.SLIPSTREAM_SPEED_MULT : 1f);
            n++;
            dd += reverse ? -SAMPLE : SAMPLE;
        }
        if (n == 0) return null;
        float[] b = BAGeom.bestExit(x, y, step, speed, n, tx, ty, vf);
        int i = (int) b[0];
        return new float[] {i, b[1], x[i], y[i]};
    }

    /**
     * Inside a stream: null while riding on (rev: reverse polarity) is at least 2 percent faster than leaving now.
     * Otherwise a point out of the stream on the target's side, 500 units on toward the target.
     */
    static float[] leavePoint(Pos pos, CampaignFleetAPI fleet, float tx, float ty, float vf, boolean rev) {
        Vector2f p = fleet.getLocation();
        float now = BAGeom.dist(p.x, p.y, tx, ty) / Math.max(1f, vf);
        float[] r = ride(pos.s, pos.along, rev, fleet, tx, ty, vf);
        if (r == null) return null;
        if (r[0] > 0f && r[1] < now * 0.98f) return null;
        float nx, ny;
        Vector2f n = pos.s.getNormalAt(pos.along);
        if (n != null && BAGeom.len(n.x, n.y) > 0.01f) { nx = n.x; ny = n.y; }
        else {
            float[] f = flow(pos.s, pos.along, rev);
            if (f == null) return new float[] {tx, ty};
            nx = -f[1];
            ny = f[0];
        }
        float nl = BAGeom.len(nx, ny);
        nx /= nl;
        ny /= nl;
        if (nx * (tx - p.x) + ny * (ty - p.y) < 0f) { nx = -nx; ny = -ny; }
        float tl = Math.max(1f, BAGeom.dist(p.x, p.y, tx, ty));
        float out = pos.s.getWidth(pos.along) + fleet.getRadius() + 200f;
        return new float[] {p.x + nx * out + (tx - p.x) / tl * 500f, p.y + ny * out + (ty - p.y) / tl * 500f};
    }

    /** Where a line first meets a slipstream: the stream, distance along it, and distance along the line. */
    static final class Hit {
        SlipstreamTerrainPlugin2 s;
        float along, d;
    }

    /** First slipstream on the line P-T within look units (sampled every 150 units, radius r), or null. */
    static Hit firstHit(List<SlipstreamTerrainPlugin2> streams, float px, float py, float tx, float ty, float look, float r) {
        float l = BAGeom.dist(px, py, tx, ty);
        if (l < 1f) return null;
        float dx = (tx - px) / l, dy = (ty - py) / l;
        List<SlipstreamTerrainPlugin2> near = new ArrayList<SlipstreamTerrainPlugin2>();
        Vector2f p = new Vector2f(px, py);
        for (SlipstreamTerrainPlugin2 s : streams) {
            if (!s.getSegmentsNear(p, look).isEmpty()) near.add(s);
        }
        if (near.isEmpty()) return null;
        Vector2f q = new Vector2f();
        for (float d = 0f; d <= look; d += 150f) {
            q.set(px + dx * d, py + dy * d);
            for (SlipstreamTerrainPlugin2 s : near) {
                if (!s.containsPoint(q, r)) continue;
                float[] c = s.getLengthAndWidthFractionWithinStream(q);
                if (c == null || intensity(s, c[0], c[1]) <= 0.05f) continue;
                Hit h = new Hit();
                h.s = s;
                h.along = c[0];
                h.d = d;
                return h;
            }
        }
        return null;
    }

    /**
     * First slipstream that the line from (px, py) in direction (dx, dy) enters within look units, sampled every 150 units
     * with radius r: {forward flow x, flow y, distance}, or null. Streams with no segment within look are skipped.
     */
    static float[] crossing(List<SlipstreamTerrainPlugin2> streams, float px, float py, float dx, float dy, float look, float r) {
        List<SlipstreamTerrainPlugin2> near = new ArrayList<SlipstreamTerrainPlugin2>();
        Vector2f p = new Vector2f(px, py);
        for (SlipstreamTerrainPlugin2 s : streams) {
            if (!s.getSegmentsNear(p, look).isEmpty()) near.add(s);
        }
        if (near.isEmpty()) return null;
        Vector2f q = new Vector2f();
        for (float d = 0f; d <= look; d += 150f) {
            q.set(px + dx * d, py + dy * d);
            for (SlipstreamTerrainPlugin2 s : near) {
                if (!s.containsPoint(q, r)) continue;
                float[] c = s.getLengthAndWidthFractionWithinStream(q);
                if (c == null || intensity(s, c[0], c[1]) <= 0.05f) continue;
                float[] f = flow(s, c[0], false);
                if (f == null) continue;
                return new float[] {f[0], f[1], d};
            }
        }
        return null;
    }

    /** The stream entryPoint picked last, or null, and whether riding it needs reverse polarity. */
    static SlipstreamTerrainPlugin2 chosen;
    static boolean chosenRev;

    /**
     * Inside a stream: time to the target riding with the other polarity from here, or -1 when that does not ride at all
     * (best exit is right here) or the stream ends.
     */
    static float otherWay(Pos pos, CampaignFleetAPI fleet, float tx, float ty, float vf, boolean rev) {
        float[] r = ride(pos.s, pos.along, !rev, fleet, tx, ty, vf);
        if (r == null || r[0] < 1f) return -1f;
        return r[1];
    }

    /**
     * Outside a stream: a point on the centre line of the best stream within reach (4000 units, at most half the trip),
     * or null. A stream counts when its flow at the entry point is within the angle limit of the target, the player has
     * discovered that part (or it is within 2000 units), and riding it saves at least the set share of the trip time.
     * sticky (the stream picked last time) needs half that saving, so the choice does not flip.
     * polarities: the polarities to try (false forward, true reverse); chosenRev says which one the pick needs.
     */
    static float[] entryPoint(List<SlipstreamTerrainPlugin2> streams, CampaignFleetAPI fleet, float tx, float ty, float vf,
                              SlipstreamTerrainPlugin2 sticky, boolean[] polarities) {
        chosen = null;
        chosenRev = false;
        Vector2f p = fleet.getLocation();
        float direct = BAGeom.dist(p.x, p.y, tx, ty);
        float range = Math.min(4000f, direct * 0.5f);
        if (range < 500f) return null;
        float minSave = BASettings.slipSaving / 100f;
        float best = Float.MAX_VALUE;
        float[] out = null;
        for (boolean rev : polarities)
        for (SlipstreamTerrainPlugin2 s : streams) {
            if (s.getSegmentsNear(p, range).isEmpty()) continue;
            float[] c = s.getLengthAndWidthFractionWithinStream(p, range, true, 0f);
            if (c == null) continue;
            float d = c[0] + (rev ? -300f : 300f);
            Vector2f e = s.getPointAt(d, 0f);
            if (e == null) continue;
            float toEntry = BAGeom.dist(p.x, p.y, e.x, e.y);
            if (toEntry > range || s.getFaderBrightness(d) < 0.5f) continue;
            SlipstreamSegment seg = s.getSegmentForDist(d);
            if (seg != null && !seg.discovered && toEntry > 2000f) continue;
            float[] f = flow(s, d, rev);
            if (f == null || BAGeom.angleBetween(f[0], f[1], tx - e.x, ty - e.y) > BASettings.slipAngle) continue;
            float[] r = ride(s, d, rev, fleet, tx, ty, vf);
            if (r == null || r[0] < 1f) continue;
            float save = s == sticky ? minSave * 0.5f : minSave;
            if (!BAGeom.slipWorth(toEntry, r[1], direct, vf, save)) continue;
            float via = toEntry / Math.max(1f, vf) + r[1];
            if (via < best) {
                best = via;
                out = new float[] {e.x, e.y};
                chosen = s;
                chosenRev = rev;
            }
        }
        return out;
    }
}
