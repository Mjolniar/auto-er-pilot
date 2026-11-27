package data.scripts.betterautopilot;

import java.util.ArrayList;
import java.util.List;

/**
 * Steering math in plain floats, so the offline check (JBACheck) runs it without the game.
 * Obstacles are circles. Routes go around them along the tangent lines.
 */
public final class BAGeom {
    private BAGeom() { }

    static final float TWO_PI = (float) (Math.PI * 2.0);
    /** Largest step along a circle for one route point, in radians (about 29 degrees). */
    static final float MAX_ARC_STEP = 0.5f;

    /** A circle to keep out of. kind: "hazard", "star", "fleet" or "storm". key: the object it stands for. */
    public static final class Circle {
        public final float x, y, r;
        public final Object key;
        public final String kind;
        public Circle(float x, float y, float r, Object key, String kind) {
            this.x = x; this.y = y; this.r = r; this.key = key; this.kind = kind;
        }
    }

    /** A route's next point, and the circle and side it goes around (null and 0 when the line is clear). */
    public static final class Route {
        public float x, y;
        public Circle around;
        public int side;
    }

    public static float len(float x, float y) { return (float) Math.sqrt(x * x + y * y); }

    public static float dist(float ax, float ay, float bx, float by) { return len(bx - ax, by - ay); }

    /** Position (0 to 1) of the point on segment P-T closest to C. */
    public static float segParam(float px, float py, float tx, float ty, float cx, float cy) {
        float dx = tx - px, dy = ty - py;
        float l2 = dx * dx + dy * dy;
        if (l2 <= 0f) return 0f;
        float t = ((cx - px) * dx + (cy - py) * dy) / l2;
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    /** Distance from C to segment P-T. */
    public static float segDist(float px, float py, float tx, float ty, float cx, float cy) {
        float t = segParam(px, py, tx, ty, cx, cy);
        return dist(px + t * (tx - px), py + t * (ty - py), cx, cy);
    }

    /** Angle between two vectors in degrees (0 to 180). 0 when either is zero. */
    public static float angleBetween(float ax, float ay, float bx, float by) {
        float la = len(ax, ay), lb = len(bx, by);
        if (la <= 0f || lb <= 0f) return 0f;
        float c = (ax * bx + ay * by) / (la * lb);
        if (c > 1f) c = 1f;
        if (c < -1f) c = -1f;
        return (float) Math.toDegrees(Math.acos(c));
    }

    static float wrap(float a) {
        a %= TWO_PI;
        if (a < 0f) a += TWO_PI;
        return a;
    }

    /**
     * The stop point outside circle hold: the point on it facing P, or the nearest one along it (10 degree steps, both
     * ways) that lies outside every circle in others. The facing point when none is clear.
     */
    public static float[] stopPoint(Circle hold, float px, float py, java.util.List<Circle> others) {
        float d = dist(px, py, hold.x, hold.y);
        double a0 = d > 1f ? Math.atan2(py - hold.y, px - hold.x) : 0.0;
        for (int k = 0; k <= 18; k++) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                if (k == 0 && sgn > 0) continue;
                double a = a0 + sgn * k * Math.PI / 18.0;
                float x = hold.x + (float) Math.cos(a) * hold.r, y = hold.y + (float) Math.sin(a) * hold.r;
                boolean clear = true;
                if (others != null) {
                    for (Circle c : others) {
                        if (dist(x, y, c.x, c.y) < c.r) { clear = false; break; }
                    }
                }
                if (clear) return new float[] {x, y};
            }
        }
        return new float[] {hold.x + (float) Math.cos(a0) * hold.r, hold.y + (float) Math.sin(a0) * hold.r};
    }

    /**
     * Length of the way from P to T around circle C on one side: tangent line, arc, tangent line.
     * side +1 goes counterclockwise around the centre, -1 clockwise. P and T must be outside the circle.
     */
    public static float sideLength(float px, float py, float tx, float ty, float cx, float cy, float r, int side) {
        float dP = dist(px, py, cx, cy), dT = dist(tx, ty, cx, cy);
        float aP = (float) Math.atan2(py - cy, px - cx), aT = (float) Math.atan2(ty - cy, tx - cx);
        float bP = (float) Math.acos(Math.min(1f, r / dP)), bT = (float) Math.acos(Math.min(1f, r / dT));
        float leave = aP + side * bP, arrive = aT - side * bT;
        float arc = wrap(side * (arrive - leave));
        return (float) Math.sqrt(Math.max(0f, dP * dP - r * r)) + r * arc + (float) Math.sqrt(Math.max(0f, dT * dT - r * r));
    }

    /**
     * Next point on the way from P to T around circle C.
     * Returns {x, y, path length, side} or null when segment P-T clears the circle or T is inside it.
     * P inside the circle: a point out of it, away from the centre and turned toward T, side 0.
     * preferSide +1 or -1 keeps that side unless the other side is more than 10 percent shorter; +2 or -2 forces it.
     * step: least distance to the returned point. A nearer tangent point moves on along the circle.
     */
    public static float[] detour(float px, float py, float tx, float ty, float cx, float cy, float r, int preferSide, float step) {
        float dT = dist(tx, ty, cx, cy);
        if (dT <= r) return null;
        float dP = dist(px, py, cx, cy);
        if (dP <= r) {
            float ux, uy;
            if (dP > 1f) { ux = (px - cx) / dP; uy = (py - cy) / dP; }
            else { ux = (tx - cx) / dT; uy = (ty - cy) / dT; }
            float tl = dist(px, py, tx, ty);
            float wx = ux, wy = uy;
            if (tl > 0f) { wx += 0.7f * (tx - px) / tl; wy += 0.7f * (ty - py) / tl; }
            float wl = len(wx, wy);
            wx /= wl; wy /= wl;
            float out = Math.max(step, r - dP + 100f);
            return new float[] {px + wx * out, py + wy * out, out + tl, 0f};
        }
        if (segDist(px, py, tx, ty, cx, cy) >= r) return null;

        float ccw = sideLength(px, py, tx, ty, cx, cy, r, 1);
        float cw = sideLength(px, py, tx, ty, cx, cy, r, -1);
        int side = ccw <= cw ? 1 : -1;
        if (preferSide == 1 && ccw <= cw * 1.1f) side = 1;
        if (preferSide == -1 && cw <= ccw * 1.1f) side = -1;
        if (preferSide == 2) side = 1;     // forced sides (route: the other side when this one is blocked)
        if (preferSide == -2) side = -1;
        float length = side == 1 ? ccw : cw;

        float aP = (float) Math.atan2(py - cy, px - cx);
        float bP = (float) Math.acos(Math.min(1f, r / dP));
        float a = aP + side * bP;
        float rr = r * 1.01f;
        float wx = cx + rr * (float) Math.cos(a), wy = cy + rr * (float) Math.sin(a);
        if (dist(px, py, wx, wy) < step) {
            // close to the circle: the next point is one step further along it, a bit outside so the chord clears it; at
            // most MAX_ARC_STEP radians on, so a small circle does not put the point beside or behind the fleet
            float da = Math.min(step / Math.max(1f, r), MAX_ARC_STEP);
            a = aP + side * (bP + da);
            rr = r / (float) Math.cos(Math.min(1f, da));
            rr *= 1.01f;
            wx = cx + rr * (float) Math.cos(a);
            wy = cy + rr * (float) Math.sin(a);
        }
        return new float[] {wx, wy, length, side};
    }

    /**
     * Next point from P toward T around the circles. The first circle on the line is passed first. A circle that holds T
     * is left out (the target is inside it). Up to three nested passes for circles between P and the detour point.
     * stickyKey and stickySide keep the side chosen before for the same circle.
     */
    public static Route route(float px, float py, float tx, float ty, List<Circle> circles, Object stickyKey, int stickySide, float step) {
        Route out = new Route();
        out.x = tx;
        out.y = ty;
        List<Circle> left = new ArrayList<Circle>(circles);
        float gx = tx, gy = ty;
        for (int depth = 0; depth < 4; depth++) {
            Circle first = null;
            float best = Float.MAX_VALUE;
            for (Circle c : left) {
                if (dist(gx, gy, c.x, c.y) <= c.r) continue;
                float dp = dist(px, py, c.x, c.y);
                float t;
                if (dp <= c.r) t = -1f;
                else if (segDist(px, py, gx, gy, c.x, c.y) >= c.r) continue;
                else t = segParam(px, py, gx, gy, c.x, c.y);
                // same place on the line (circles around one centre, such as a star inside its asteroid belt): the larger
                // circle first, so the detour point is outside both
                if (t < best - 1e-4f || (first != null && Math.abs(t - best) <= 1e-4f && c.r > first.r)) { best = t; first = c; }
            }
            if (first == null) break;
            int prefer = first.key != null && first.key == stickyKey ? stickySide : 0;
            float[] d = detour(px, py, gx, gy, first.x, first.y, first.r, prefer, step);
            if (d == null) break;
            // the detour point lies in another circle (overlapping circles kept apart, or of other kinds): the other
            // side of this circle, when its point is clear
            if (d[3] != 0f && blockedBy(left, first, d[0], d[1], tx, ty)) {
                float[] o = detour(px, py, gx, gy, first.x, first.y, first.r, d[3] > 0f ? -2 : 2, step);
                if (o != null && !blockedBy(left, first, o[0], o[1], tx, ty)) d = o;
            }
            gx = d[0];
            gy = d[1];
            out.x = gx;
            out.y = gy;
            if (depth == 0) {
                out.around = first;
                out.side = (int) d[3];
            }
            left.remove(first);
        }
        return out;
    }

    /** True when (x, y) is inside a circle of list other than self that does not hold the target (tx, ty). */
    static boolean blockedBy(List<Circle> list, Circle self, float x, float y, float tx, float ty) {
        for (Circle c : list) {
            if (c == self || dist(tx, ty, c.x, c.y) <= c.r) continue;
            if (dist(x, y, c.x, c.y) < c.r) return true;
        }
        return false;
    }

    /** Joins overlapping circles of the same kind into one circle that holds both, until none overlap. */
    public static List<Circle> merge(List<Circle> in) {
        return merge(in, Float.NaN, Float.NaN, Float.NaN, Float.NaN);
    }

    /**
     * As merge(in), but two circles stay apart when the joined circle would hold P (the fleet) or T (the goal) and neither
     * circle does: route() skips a circle that holds the goal, and a circle that holds the fleet pushes it out first.
     */
    public static List<Circle> merge(List<Circle> in, float px, float py, float tx, float ty) {
        List<Circle> list = new ArrayList<Circle>(in);
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 200) {
            changed = false;
            outer:
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    Circle a = list.get(i), b = list.get(j);
                    if (!a.kind.equals(b.kind)) continue;
                    if (dist(a.x, a.y, b.x, b.y) >= a.r + b.r) continue;
                    Circle m = hull(a, b);
                    if (newlyHolds(m, a, b, px, py) || newlyHolds(m, a, b, tx, ty)) continue;
                    list.set(i, m);
                    list.remove(j);
                    changed = true;
                    break outer;
                }
            }
        }
        return list;
    }

    /** True when m holds point (x, y) and neither a nor b does. False for a NaN point. */
    static boolean newlyHolds(Circle m, Circle a, Circle b, float x, float y) {
        if (Float.isNaN(x) || Float.isNaN(y)) return false;
        return dist(x, y, m.x, m.y) <= m.r && dist(x, y, a.x, a.y) > a.r && dist(x, y, b.x, b.y) > b.r;
    }

    /** Smallest circle that holds circles a and b. Keeps a's key. */
    public static Circle hull(Circle a, Circle b) {
        float d = dist(a.x, a.y, b.x, b.y);
        if (d + b.r <= a.r) return a;
        if (d + a.r <= b.r) return new Circle(b.x, b.y, b.r, a.key, a.kind);
        float r = (d + a.r + b.r) / 2f;
        float f = (r - a.r) / d;
        return new Circle(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, r, a.key, a.kind);
    }

    /**
     * Destination that makes the game's fleet movement accelerate along (want - vel).
     * The game accelerates along 3 * (dest - loc) - vel * (|vel| / accel + 0.75), capped at the fleet's acceleration
     * (SmoothMovementModule, 0.98.5a). gain sets how far out the point is; the direction is the same for any gain.
     */
    public static float[] aimFor(float px, float py, float vx, float vy, float wantX, float wantY, float accel, float gain) {
        float k = len(vx, vy) / Math.max(1f, accel) + 0.75f;
        float ex = wantX - vx, ey = wantY - vy;
        return new float[] {px + (vx * k + gain * ex) / 3f, py + (vy * k + gain * ey) / 3f};
    }

    /**
     * A straight crossing of a stream at centre point C with unit flow (fx, fy): the normal that points to the target's
     * side, the line-up point A on the other side and the far point F, each hw (half width plus margin) from C.
     * Returns {nx, ny, ax, ay, farX, farY}.
     */
    public static float[] crossPoints(float cx, float cy, float fx, float fy, float hw, float tx, float ty) {
        float nx = -fy, ny = fx;
        if ((tx - cx) * nx + (ty - cy) * ny < 0f) { nx = -nx; ny = -ny; }
        return new float[] {nx, ny, cx - nx * hw, cy - ny * hw, cx + nx * hw, cy + ny * hw};
    }

    /** Time cost of a crossing: P to A, across, F to T at speed vf, plus 1.5 x the drift (wind x crossing time) to win back. */
    public static float crossCost(float px, float py, float[] cp, float hw, float tx, float ty, float vf, float wind) {
        float crossTime = 2f * hw / vf;
        return (dist(px, py, cp[2], cp[3]) + 2f * hw + dist(cp[4], cp[5], tx, ty)) / vf + 1.5f * wind * crossTime / vf;
    }

    /** Distance to slow from speed v to speed slow at constant deceleration accel. */
    public static float brakeDistance(float v, float slow, float accel) {
        if (v <= slow) return 0f;
        return (v * v - slow * slow) / (2f * Math.max(1f, accel));
    }

    /** True when a detour of detourLength (direct line directLength) adds at most share (0 to 1) of leg. */
    public static boolean detourWorth(float directLength, float detourLength, float leg, float share) {
        return detourLength - directLength <= share * leg;
    }

    /**
     * Circle around grid cells: centre at their mean, radius out to the farthest cell centre plus half a cell diagonal.
     * xs, ys: cell centres. Returns {x, y, r}.
     */
    public static float[] cellCircle(float[] xs, float[] ys, int n, float cell) {
        float mx = 0f, my = 0f;
        for (int i = 0; i < n; i++) { mx += xs[i]; my += ys[i]; }
        mx /= n;
        my /= n;
        float r = 0f;
        for (int i = 0; i < n; i++) r = Math.max(r, dist(mx, my, xs[i], ys[i]));
        return new float[] {mx, my, r + cell * 0.71f};
    }

    /**
     * Best place to leave a slipstream. Samples run downstream from the fleet: x, y position, step length before each
     * sample (step[0] = 0) and stream speed over that step. Time at sample i: ride time to it plus the straight line from
     * it to T at fleet speed vf. Returns {index, time}; index 0 means leave now.
     */
    public static float[] bestExit(float[] x, float[] y, float[] step, float[] speed, int n, float tx, float ty, float vf) {
        float ride = 0f;
        int best = 0;
        float bestTime = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (i > 0) ride += step[i] / Math.max(vf, speed[i]);
            float t = ride + dist(x[i], y[i], tx, ty) / Math.max(1f, vf);
            if (t < bestTime) { bestTime = t; best = i; }
        }
        return new float[] {best, bestTime};
    }

    /**
     * True when using a slipstream saves at least minSaving (0 to 1) of the straight trip.
     * toEntry: distance to the entry point; rideTime: best time from the entry point (bestExit); direct: straight distance.
     */
    public static boolean slipWorth(float toEntry, float rideTime, float direct, float vf, float minSaving) {
        float straight = direct / Math.max(1f, vf);
        float via = toEntry / Math.max(1f, vf) + rideTime;
        return via <= straight * (1f - minSaving);
    }

    /**
     * The entry gate a and exit gate b (a != b) with the least toEntry[a] + fromExit[b]. Float.MAX_VALUE marks a gate that
     * can not be reached. Null when no pair can be used.
     */
    public static int[] bestPair(float[] toEntry, float[] fromExit) {
        int n = Math.min(toEntry.length, fromExit.length);
        int b1 = -1, b2 = -1;
        for (int i = 0; i < n; i++) {
            if (fromExit[i] == Float.MAX_VALUE) continue;
            if (b1 < 0 || fromExit[i] < fromExit[b1]) { b2 = b1; b1 = i; }
            else if (b2 < 0 || fromExit[i] < fromExit[b2]) b2 = i;
        }
        int bestA = -1, bestB = -1;
        float best = Float.MAX_VALUE;
        for (int a = 0; a < n; a++) {
            if (toEntry[a] == Float.MAX_VALUE) continue;
            int b = a != b1 ? b1 : b2;
            if (b < 0) continue;
            float c = toEntry[a] + fromExit[b];
            if (c < best) { best = c; bestA = a; bestB = b; }
        }
        return bestA < 0 ? null : new int[] {bestA, bestB};
    }

    /**
     * Progress toward a target while something pushes the fleet: stuck once the distance has not dropped by at least
     * progress units for seconds in a row.
     */
    public static final class Stuck {
        final float progress, seconds;
        private float best = Float.MAX_VALUE, since;

        public Stuck(float progress, float seconds) { this.progress = progress; this.seconds = seconds; }

        /** dist: the distance to the target now; dt: seconds since the last call. True once stuck. */
        public boolean update(float dist, float dt) {
            if (Float.isNaN(dist)) return false;
            if (dist < best - progress || best == Float.MAX_VALUE) { best = dist; since = 0f; }
            else since += Math.max(0f, dt);
            return since >= seconds;
        }

        public float since() { return since; }

        public void reset() { best = Float.MAX_VALUE; since = 0f; }
    }

    /** A toggle ability switching on or off now: its level is between 0 and 1 (0 is off, 1 is on). */
    public static boolean midSwitch(float level) {
        return level > 0f && level < 1f;
    }

    /** True when the gate route is at least minSaving (0 to 1) and minGain units shorter than the direct way. */
    public static boolean gateWorth(float direct, float via, float minSaving, float minGain) {
        if (via == Float.MAX_VALUE) return false;
        if (direct == Float.MAX_VALUE) return true;
        return via <= direct * (1f - minSaving) && direct - via >= minGain;
    }
}
