package data.scripts.betterautopilot;

import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin.CellStateTracker;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Hyperspace storm cells. A cell that is storming or signaling counts as active: the game strikes fleets in storming cells
 * unless they move slowly, and a signaling cell close to storming turns into a storm when a fleet enters it.
 */
final class BAStorms {
    private BAStorms() { }

    static final Object STORM_KEY = new Object();
    static final int MAX_CELLS = 600;

    static HyperspaceTerrainPlugin plugin(LocationAPI loc) {
        if (loc == null || !loc.isHyperspace()) return null;
        CampaignTerrainAPI t = Misc.getHyperspaceTerrain();
        if (t == null || !(t.getPlugin() instanceof HyperspaceTerrainPlugin)) return null;
        HyperspaceTerrainPlugin h = (HyperspaceTerrainPlugin) t.getPlugin();
        return h.getActiveCells() == null ? null : h;
    }

    static boolean active(CellStateTracker c) { return c != null && (c.isStorming() || c.isSignaling()); }

    /** Distance along the ray (unit dx, dy) to the first point whose cells within r are active; -1 when none. */
    static float ahead(HyperspaceTerrainPlugin h, float px, float py, float dx, float dy, float maxDist, float r) {
        Vector2f q = new Vector2f();
        maxDist = Math.min(maxDist, BAObstacles.MAX_LOOK);
        for (float d = 0f; d <= maxDist; d += 100f) {
            q.set(px + dx * d, py + dy * d);
            if (active(h.getCellAt(q, r))) return d;
        }
        return -1f;
    }

    /** Circle {x, y, r} around the active cells joined to the active cell at seed (8 neighbours, up to MAX_CELLS). */
    static float[] cluster(HyperspaceTerrainPlugin h, Vector2f seed, float r) {
        int[] tile = h.getTilePreferStorm(seed, r);
        CellStateTracker[][] cells = h.getActiveCells();
        if (tile == null || cells == null || cells.length == 0) return null;
        int w = cells.length, hh = cells[0] == null ? 0 : cells[0].length;
        if (tile.length < 2 || tile[0] < 0 || tile[1] < 0 || tile[0] >= w || tile[1] >= hh) return null;
        if (!active(cells[tile[0]][tile[1]])) return null;
        float size = h.getTileSize();
        Vector2f centre = Misc.getHyperspaceTerrain().getLocation();
        float x0 = centre.x - w * size / 2f, y0 = centre.y - hh * size / 2f;

        float[] xs = new float[MAX_CELLS], ys = new float[MAX_CELLS];
        int n = 0;
        Set<Integer> seen = new HashSet<Integer>();
        ArrayDeque<int[]> open = new ArrayDeque<int[]>();
        open.add(tile);
        seen.add(tile[0] * hh + tile[1]);
        while (!open.isEmpty() && n < MAX_CELLS) {
            int[] c = open.poll();
            xs[n] = x0 + (c[0] + 0.5f) * size;
            ys[n] = y0 + (c[1] + 0.5f) * size;
            n++;
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    int i = c[0] + di, j = c[1] + dj;
                    if (i < 0 || j < 0 || i >= w || j >= hh || cells[i] == null || j >= cells[i].length) continue;
                    if (!seen.add(i * hh + j)) continue;
                    if (active(cells[i][j])) open.add(new int[] {i, j});
                }
            }
        }
        return BAGeom.cellCircle(xs, ys, n, h.getTileContainsSize());
    }

    /**
     * Circle around the storm on the line P-B (within 6000 units) when going around it adds at most share of the leg.
     * Null when there is no storm on the line, when P or B is inside the storm, or when the detour is too long; the fleet
     * then moves slowly through.
     */
    static BAGeom.Circle detour(HyperspaceTerrainPlugin h, float fleetRadius, float px, float py, float bx, float by, float share, float step) {
        float leg = BAGeom.dist(px, py, bx, by);
        if (leg < 500f) return null;
        float ux = (bx - px) / leg, uy = (by - py) / leg;
        float r = fleetRadius + 100f;
        float d = ahead(h, px, py, ux, uy, Math.min(leg, 6000f), r);
        if (d < 0f) return null;
        float[] c = cluster(h, new Vector2f(px + ux * d, py + uy * d), r);
        if (c == null) return null;
        float rr = c[2] + fleetRadius + 200f;
        if (BAGeom.dist(px, py, c[0], c[1]) <= rr || BAGeom.dist(bx, by, c[0], c[1]) <= rr) return null;
        float[] w = BAGeom.detour(px, py, bx, by, c[0], c[1], rr, 0, step);
        if (w == null || !BAGeom.detourWorth(leg, w[2], leg, share)) return null;
        return new BAGeom.Circle(c[0], c[1], rr, STORM_KEY, "storm");
    }
}
