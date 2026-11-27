package data.scripts.betterautopilot;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.TerrainAIFlags;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.PulsarBeamTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Hazard terrain, stars and hostile fleets as circles to steer around; asteroid belts and fields to slow down in. */
final class BAObstacles {
    private BAObstacles() { }

    /** Longest look-ahead for storms and asteroids, in units (sampled every 100), so a low acceleration stays cheap. */
    static final float MAX_LOOK = 6000f;

    /**
     * Terrain the NPC fleet AI avoids: CR_DRAIN (star coronas, pulsar beams) and AVOID_VERY_CAREFULLY (black hole event
     * horizons, 200 more units). Radius: the largest max effect radius around the terrain (an active flare counts).
     * Stars: their own radius. Each gets the hazard margin plus the fleet's radius. Left out: terrain or stars that hold the
     * target or belong to it.
     */
    static void hazards(LocationAPI loc, CampaignFleetAPI fleet, SectorEntityToken step, List<BAGeom.Circle> out) {
        float margin = BASettings.hazardMargin + fleet.getRadius();
        Vector2f t = step.getLocation();
        Vector2f q = new Vector2f();
        for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
          try {
            CampaignTerrainPlugin p = terrain.getPlugin();
            // pulsar beams: two turning arcs, as wide as the whole beam length (well over 10000 units), never a disc
            if (p == null || p instanceof HyperspaceTerrainPlugin || p instanceof SlipstreamTerrainPlugin2
                    || p instanceof PulsarBeamTerrainPlugin) continue;
            boolean careful = p.hasAIFlag(TerrainAIFlags.AVOID_VERY_CAREFULLY);
            boolean drain = p.hasAIFlag(TerrainAIFlags.CR_DRAIN) || p.hasAIFlag(TerrainAIFlags.CR_DRAIN, fleet);
            if (!careful && !drain) continue;
            if (p instanceof BaseTerrain && ((BaseTerrain) p).getRelatedEntity() == step) continue;
            Vector2f c = terrain.getLocation();
            float r = effectRadius(p, c, q);
            if (r <= 0f) continue;
            float rr = capped(r + margin + (careful ? 200f : 0f), r, BAGeom.dist(t.x, t.y, c.x, c.y), step.getRadius());
            if (rr <= 0f) continue;
            out.add(new BAGeom.Circle(c.x, c.y, rr, terrain, "hazard"));
          } catch (Throwable e) { bad(terrain, e); }
        }
        for (PlanetAPI pl : loc.getPlanets()) {
            if (!pl.isStar() || pl == step) continue;
            Vector2f c = pl.getLocation();
            float rr = capped(pl.getRadius() + margin, pl.getRadius(), BAGeom.dist(t.x, t.y, c.x, c.y), step.getRadius());
            if (rr <= 0f) continue;
            out.add(new BAGeom.Circle(c.x, c.y, rr, pl, "star"));
        }
    }

    /**
     * A hazard circle's radius with the target dT from its centre: 0 (left out) when the target is inside the hazard
     * itself (radius r), else rr, made smaller where needed so the circle stops short of the target (a planet close to
     * a star's corona: the way still goes around the corona).
     */
    static float capped(float rr, float r, float dT, float targetRadius) {
        if (dT <= r) return 0f;
        return Math.max(0f, Math.min(rr, dT - targetRadius - 1f));
    }

    /**
     * The largest max effect radius of terrain p around centre c (24 directions, 100 units out; an active flare counts).
     * 0 when the terrain reports a value that is not a finite number, or one over MAX_HAZARD_RADIUS (a modded terrain
     * that would turn the whole system into one obstacle).
     */
    static float effectRadius(CampaignTerrainPlugin p, Vector2f c, Vector2f q) {
        float r = 0f;
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2.0 * i / 24.0;
            q.set(c.x + (float) Math.cos(a) * 100f, c.y + (float) Math.sin(a) * 100f);
            float e = p.getMaxEffectRadius(q);
            if (Float.isNaN(e) || Float.isInfinite(e)) return 0f;
            r = Math.max(r, e);
        }
        return r > MAX_HAZARD_RADIUS ? 0f : r;
    }

    static final float MAX_HAZARD_RADIUS = 20000f;

    /**
     * True when a hostile fleet stronger than the player's (times the strength margin) could reach the player fleet
     * within seconds at its travel speed. Counts fleets the player sees, whether or not they see the player; leaves out
     * stations, fleets in a battle and non-aggressive fleets.
     */
    static boolean strongerNear(LocationAPI loc, CampaignFleetAPI fleet, float seconds) {
        float mine = fleet.getEffectiveStrength();
        Vector2f p = fleet.getLocation();
        for (CampaignFleetAPI f : loc.getFleets()) {
            if (f == fleet || f.isPlayerFleet()) continue;
            Vector2f c = f.getLocation();
            float reach = Math.max(1f, f.getTravelSpeed()) * seconds + f.getRadius() + fleet.getRadius();
            if (BAGeom.dist(p.x, p.y, c.x, c.y) > reach) continue;
            if (f.getBattle() != null || f.isStationMode()) continue;
            if (!f.isHostileTo(fleet)) continue;
            if (f.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE)) continue;
            if (f.getVisibilityLevelToPlayerFleet() == VisibilityLevel.NONE) continue;
            if (f.getEffectiveStrength() <= mine * BASettings.fleetMargin) continue;
            return true;
        }
        return false;
    }

    /**
     * Hostile fleets within 10000 units that the player sees, that see the player, and whose effective strength is above
     * the player's times the strength margin. Left out: stations, fleets in a battle, non-aggressive fleets, the
     * target, and fleets whose circle holds the target. Radius: both fleets' radii plus the clearance setting.
     */
    static void fleets(LocationAPI loc, CampaignFleetAPI fleet, SectorEntityToken step, List<BAGeom.Circle> out) {
        float mine = fleet.getEffectiveStrength();
        Vector2f p = fleet.getLocation(), t = step.getLocation();
        for (CampaignFleetAPI f : loc.getFleets()) {
            if (f == fleet || f == step || f.isPlayerFleet()) continue;
            Vector2f c = f.getLocation();
            if (BAGeom.dist(p.x, p.y, c.x, c.y) > 10000f) continue;
            if (f.getBattle() != null || f.isStationMode()) continue;
            if (!f.isHostileTo(fleet)) continue;
            if (f.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE)) continue;
            if (f.getVisibilityLevelToPlayerFleet() == VisibilityLevel.NONE) continue;
            if (f.getVisibilityLevelOfPlayerFleet() == VisibilityLevel.NONE) continue;
            if (f.getEffectiveStrength() <= mine * BASettings.fleetMargin) continue;
            float rr = f.getRadius() + fleet.getRadius() + BASettings.fleetClearance;
            if (BAGeom.dist(t.x, t.y, c.x, c.y) <= rr + step.getRadius()) continue;
            out.add(new BAGeom.Circle(c.x, c.y, rr, f, "fleet"));
        }
    }

    /**
     * The largest hazard circle that holds the target: a star corona or black hole event horizon (as in hazards, pulsar
     * beams left out: they turn), or a star's own radius, each with the hazard margin and the fleet's radius. Null when
     * the target is in none.
     */
    static BAGeom.Circle holding(LocationAPI loc, CampaignFleetAPI fleet, SectorEntityToken step) {
        float margin = BASettings.hazardMargin + fleet.getRadius();
        Vector2f t = step.getLocation();
        Vector2f q = new Vector2f();
        BAGeom.Circle best = null;
        for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
          try {
            CampaignTerrainPlugin p = terrain.getPlugin();
            if (p == null || p instanceof HyperspaceTerrainPlugin || p instanceof SlipstreamTerrainPlugin2
                    || p instanceof PulsarBeamTerrainPlugin) continue;
            boolean careful = p.hasAIFlag(TerrainAIFlags.AVOID_VERY_CAREFULLY);
            if (!careful && !p.hasAIFlag(TerrainAIFlags.CR_DRAIN)) continue;
            Vector2f c = terrain.getLocation();
            float r = effectRadius(p, c, q);
            if (r <= 0f) continue;
            float rr = r + margin + (careful ? 200f : 0f);
            if (BAGeom.dist(t.x, t.y, c.x, c.y) > rr) continue;
            if (best == null || rr > best.r) best = new BAGeom.Circle(c.x, c.y, rr, terrain, "hazard");
          } catch (Throwable e) { bad(terrain, e); }
        }
        for (PlanetAPI pl : loc.getPlanets()) {
            if (!pl.isStar() && !pl.isBlackHole()) continue;
            Vector2f c = pl.getLocation();
            float rr = pl.getRadius() + margin;
            if (BAGeom.dist(t.x, t.y, c.x, c.y) > rr) continue;
            if (best == null || rr > best.r) best = new BAGeom.Circle(c.x, c.y, rr, pl, "star");
        }
        return best;
    }

    /** Terrain that hits fleets unless they move slowly: asteroid belts and fields (DANGEROUS_UNLESS_GO_SLOW). */
    static boolean slowTerrain(CampaignTerrainPlugin p) {
        return p != null && !(p instanceof HyperspaceTerrainPlugin) && !(p instanceof SlipstreamTerrainPlugin2)
                && p.hasAIFlag(TerrainAIFlags.DANGEROUS_UNLESS_GO_SLOW);
    }

    /**
     * True when the fleet is inside an asteroid belt or field, or the line from it in direction (dx, dy) enters one
     * within look units. Samples every 100 units with the fleet's radius.
     */
    static boolean asteroidsAhead(LocationAPI loc, CampaignFleetAPI fleet, float px, float py, float dx, float dy, float look) {
        Vector2f q = new Vector2f();
        float r = fleet.getRadius();
        look = Math.min(look, MAX_LOOK);
        for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
          try {
            CampaignTerrainPlugin p = terrain.getPlugin();
            if (!slowTerrain(p)) continue;
            for (float d = 0f; d <= look; d += 100f) {
                q.set(px + dx * d, py + dy * d);
                if (p.containsPoint(q, r)) return true;
            }
          } catch (Throwable e) { bad(terrain, e); }
        }
        return false;
    }

    /** Terrain types already logged as throwing. Strings only: nothing of a save is kept. */
    private static final Set<String> badLogged = new HashSet<String>();

    /** A terrain (another mod's) that throws is skipped, logged once per type, so it does not stop all steering there. */
    static void bad(CampaignTerrainAPI terrain, Throwable e) {
        String k;
        try { k = terrain == null || terrain.getPlugin() == null ? "?" : terrain.getPlugin().getClass().getName(); }
        catch (Throwable t) { k = "?"; }
        if (badLogged.add(k)) BAScript.LOG.warn("Auto-er Pilot: terrain " + k + " skipped: it throws", e);
    }

    /** Name for the debug log. */
    static String name(BAGeom.Circle c) {
        Object k = c.key;
        String n = null;
        try {
            if (k instanceof CampaignTerrainAPI && ((CampaignTerrainAPI) k).getPlugin() != null) n = ((CampaignTerrainAPI) k).getPlugin().getTerrainName();
            else if (k instanceof SectorEntityToken) n = ((SectorEntityToken) k).getName();
        } catch (Throwable t) { n = null; }
        return c.kind + (n != null ? " " + n : "");
    }
}
