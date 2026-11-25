package data.scripts.betterautopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Steers the player fleet while it flies itself to a travel target (a category switched on in the Targets tab, BATargets): from a laid-in course or a click. Plans
 * every 0.15 seconds, applies every frame: a move destination override (one frame each, so the game's own steering takes
 * over the moment this stops) and the game's go-slow for storms. The target changes only at a hazardous target (the
 * course ends outside it); the course also for a gate route (BAGates). A click on empty space is manual flying.
 * Also switches Reverse Polarity for slipstreams and turns Sustained Burn back on (BAAbilities).
 */
public class BAScript implements EveryFrameScript {
    static final Logger LOG = Global.getLogger(BAScript.class);
    static final float PLAN_EVERY = 0.15f, STEP = 300f, EXIT_WINDOW = 8f, STATUS_EVERY = 10f;

    private float planIn = 0f;
    private SectorEntityToken lastStep;
    private boolean slow, override;
    /** Go Dark holds the fleet slow this frame (the steering goes on at slow speed). */
    private boolean darkSlow;
    private float aimX, aimY;
    private String state = "";
    private Object stickyKey;
    private int stickySide;
    private boolean wasInSlip;
    private float slipExitLeft;
    private SlipstreamTerrainPlugin2 entering;
    private String lastError;
    /** Seconds the steering stays paused after an error. */
    private float errorPause;
    static final float ERROR_PAUSE = 5f;
    private float statusIn = 0f;
    private final String[] idleWhy = new String[1];
    /**
     * Seconds since this script last sent goSlowOneFrame. Tells the game's go-slow key apart from this script's slow-down:
     * the game clears its slow-moving flag only on normal iterations, not on extra fast-forward ones.
     */
    private float slowSentAgo = 99f;
    /** Debug: write the obstacle list once for each new target. */
    private boolean listObstacles;
    private final BAAbilities abilities = new BAAbilities();
    /** Out of a corona, event horizon or pulsar beam that holds the fleet: normal burn, Emergency Burn, Transverse Jump. */
    private final BAEscape escape = new BAEscape();
    /** After a Transverse Jump the player used: the fleet waits until the player resumes the course. */
    private final BAHold hold = new BAHold();
    /** Crossing a slipstream that does not flow toward the target, without a usable Reverse Polarity. */
    private final BACross cross = new BACross();
    /** Seconds before Reverse Polarity may be switched again. */
    private float rpHold;
    /**
     * Reverse Polarity switches inside the current slipstream (each costs fuel and CR, on and off). At most
     * MAX_PAID_SWITCHES per stay in a stream, so a plan that flips cannot drain the fleet.
     */
    private int paidSwitches;
    static final int MAX_PAID_SWITCHES = 2;
    /** The last plan rode, left or headed for a slipstream. */
    private boolean slipBusy;
    /** The last plan had a stronger hostile fleet close by (Sustained Burn is not turned on then: it stops the fleet). */
    private boolean fleetNear;
    /** The last plan rode a slipstream with its flow, lined up with it and away from its edges (Sustained Burn may go on). */
    private boolean rideSB;
    /** The slipstream the fleet is leaving: Sustained Burn is not turned on again inside it. */
    private SlipstreamTerrainPlugin2 leftStream;
    /** Riding with Sustained Burn: the fleet's course within this many degrees of the flow, inside this share of the half width. */
    static final float RIDE_SB_ANGLE = 30f, RIDE_SB_SIDE = 0.6f;
    /** A stronger hostile fleet that can reach the fleet within this many seconds keeps Sustained Burn off. */
    static final float FLEE_SECONDS = 8f;
    /** Seconds a storm or asteroid slow-down is kept after its last trigger, so it does not flicker on and off. */
    private float slowHold;
    private String slowWhy = "";
    static final float SLOW_HOLD = 1f;
    /** A slipstream flowing more than this many degrees away from the target direction counts as flowing away. */
    static final float RP_ANGLE = 110f;
    /** The last target checked, and its category (BATargets); the category's switch is read every frame. */
    private SectorEntityToken checkedStep;
    private int checkedCategory;
    /** Where the trip ends, from the last plan: the target, or the stop point outside a hazard that holds it. */
    private float goalX, goalY;

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return true; }

    /**
     * Every frame. Any error is caught here, logged once per message, and the steering pauses for ERROR_PAUSE seconds,
     * so a fault in this mod never reaches the game's script loop (an uncaught one ends the game).
     */
    @Override
    public void advance(float amount) {
        if (errorPause > 0f) {
            errorPause -= amount;
            return;
        }
        try { frame(amount); }
        catch (Throwable t) {
            slow = false;
            override = false;
            errorPause = ERROR_PAUSE;
            String msg = String.valueOf(t);
            if (!msg.equals(lastError)) { lastError = msg; LOG.warn("Auto-er Pilot: error, steering paused for " + Math.round(ERROR_PAUSE) + " seconds", t); }
        }
    }

    private void frame(float amount) {
        SectorAPI sector = Global.getSector();
        if (sector == null) return;
        CampaignFleetAPI fleet = sector.getPlayerFleet();
        CampaignUIAPI ui = sector.getCampaignUI();
        if (fleet == null || ui == null) return;
        if (!sector.isPaused()) {
            try { BAGates.advance(fleet, ui, amount); }
            catch (Throwable t) {
                BAGates.error(ui);
                String msg = String.valueOf(t);
                if (!msg.equals(lastError)) { lastError = msg; LOG.warn("Auto-er Pilot: gate routing error; route dropped", t); }
            }
        }
        if (!BASettings.enabled) {
            // an escape jump under way is still followed to its end (its course check)
            if (!sector.isPaused() && escape.busy()) escape.check(fleet, ui, null, amount, abilities);
            // turned off in the settings: Reverse Polarity turned on here goes off where that is free (outside a stream)
            abilities.track(fleet);   // also clears the memory flag when Reverse Polarity is already off
            if (abilities.rpMine && !sector.isPaused() && !Misc.isInsideSlipstream(fleet)) {
                abilities.rpOff(fleet.getAbility(BAAbilities.RP), "the mod was turned off");
            }
            idle("off");
            return;
        }
        abilities.track(fleet);
        if (sector.isPaused()) return;
        // a Transverse Jump out of a pulsar beam under way: followed before the hand-off checks (the jump slows the fleet)
        if (escape.busy() && escape.check(fleet, ui, null, amount, abilities)) { idle("not steering: Transverse Jump out of a pulsar beam"); return; }
        if (hold.check(fleet, ui, abilities, escape.busy())) { idle("not steering: waiting after a Transverse Jump"); return; }
        if (rpHold > 0f) rpHold -= amount;
        if (slowHold > 0f) slowHold -= amount;
        if (slowSentAgo < 99f) slowSentAgo += amount;
        // the fleet moves slowly without a request from here: the game's go-slow key (any binding), or an ability that
        // holds the fleet slow. Go Dark keeps the steering (a slow trip); Sensor Burst, Interdiction Pulse and other
        // abilities with the slow-down tag are the player's own stop.
        darkSlow = false;
        if (fleet.wasSlowMoving() && slowSentAgo > 0.5f) {
            String stopper = BAAbilities.slowAbility(fleet);
            if (stopper == null) { idle("not steering: go-slow key"); return; }
            if (!BAAbilities.GO_DARK.equals(stopper)) { idle("not steering: " + stopper + " active"); return; }
            darkSlow = true;
        }
        SectorEntityToken step = following(ui, fleet, idleWhy);
        // held in a corona, event horizon or pulsar beam with no progress: normal burn, Emergency Burn, Transverse Jump (BAEscape)
        if (escape.check(fleet, ui, step, amount, abilities)) { idle("not steering: Transverse Jump out of a pulsar beam"); return; }
        // a fleet's category is checked every frame (it can turn hostile on the way)
        if (step != null && (step != checkedStep || step instanceof CampaignFleetAPI)) {
            checkedStep = step;
            checkedCategory = BATargets.category(step);
        }
        if (step != null && !BATargets.on(checkedCategory)) {
            idleWhy[0] = "target " + step.getName() + " (" + BATargets.NAMES[checkedCategory] + ") is switched off in Targets";
            step = null;
        }
        if (step == null) {
            // Reverse Polarity turned on here stays on only while this script steers (off is free outside a stream)
            if (abilities.rpMine && rpHold <= 0f && !Misc.isInsideSlipstream(fleet)) {
                abilities.rpOff(fleet.getAbility(BAAbilities.RP), "steering stopped");
            }
            idle("not steering: " + idleWhy[0]);
            return;
        }
        if (step != lastStep) {
            lastStep = step;
            planIn = 0f;
            stickyKey = null;
            stickySide = 0;
            entering = null;
            cross.reset();
            abilities.newTarget();
            statusIn = 0f;
            listObstacles = true;
            if (BASettings.debug) LOG.info("Auto-er Pilot: target " + step.getName()
                    + (ui.isFollowingDirectCommand() ? " (clicked)" : " (course)"));
        }
        if (slipExitLeft > 0f) slipExitLeft -= amount;
        planIn -= amount;
        if (planIn <= 0f) {
            planIn = PLAN_EVERY;
            try { plan(fleet, step); }
            catch (Throwable t) {
                slow = false;
                override = false;
                planIn = 5f;
                String msg = String.valueOf(t);
                if (!msg.equals(lastError)) { lastError = msg; LOG.warn("Auto-er Pilot: steering skipped", t); }
            }
        }
        apply(fleet);
        Vector2f fp = fleet.getLocation();
        abilities.keepSustainedBurn(fleet, BAGeom.dist(fp.x, fp.y, goalX, goalY),
                slow || slipBusy || fleetNear || slipExitLeft > 0f || escape.inWind() || (fleet.isInHyperspace() && Misc.isInsideSlipstream(fleet)),
                rideSB, amount);
        if (BASettings.debug) {
            statusIn -= amount;
            if (statusIn <= 0f) {
                statusIn = STATUS_EVERY;
                Vector2f p = fleet.getLocation(), t = step.getLocation();
                LOG.info("Auto-er Pilot: status: " + state + "; target " + step.getName() + " at "
                        + Math.round(BAGeom.dist(p.x, p.y, t.x, t.y)) + " units; speed " + Math.round(fleet.getVelocity().length())
                        + " of " + Math.round(fleet.getTravelSpeed()) + (override ? "; steering" : "") + (slow ? "; moving slowly" : ""));
            }
        }
    }

    /**
     * The entity the fleet flies itself to: the fleet's interaction target (a course point, or a clicked planet, station,
     * jump point or fleet). Null, with the reason in why[0], while the player steers or the game is busy: a held mouse
     * button, a dialog or menu, no target (idle, or a click on empty space), a target in another location, a battle, a jump
     * or an orbit. The game itself counts mouse following as manual only while the button is held. Keys are not checked:
     * the campaign has no movement keys, and the speed-up key (often Shift) must not stop the steering.
     */
    static SectorEntityToken following(CampaignUIAPI ui, CampaignFleetAPI fleet, String[] why) {
        if (ui.isShowingDialog() || ui.isShowingMenu()) { why[0] = "dialog or menu"; return null; }
        if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1)) { why[0] = "mouse button held"; return null; }
        SectorEntityToken step = fleet.getInteractionTarget();
        if (step == null || step.isExpired()) { why[0] = "no target"; return null; }
        if (fleet.getContainingLocation() == null || step.getContainingLocation() != fleet.getContainingLocation()) {
            why[0] = "target in another location";
            return null;
        }
        if (fleet.getBattle() != null) { why[0] = "battle"; return null; }
        if (fleet.isInHyperspaceTransition()) { why[0] = "jump"; return null; }
        if (fleet.getOrbit() != null) { why[0] = "orbit"; return null; }
        return step;
    }

    /** The game's location token (BaseLocation.LocationToken): a bare point, made for a course to a place. */
    static boolean pointToken(SectorEntityToken e) {
        return e != null && "LocationToken".equals(e.getClass().getSimpleName());
    }

    /**
     * A target that is itself a hazard or only marks a place in one: a star or black hole, a point token, or a system's
     * hyperspace anchor. The steering stops outside the hazard holding it and ends the course there. Anything else inside a
     * corona (a planet, station, jump point, gate, fleet or other object) is still flown to.
     */
    static boolean hazardousTarget(SectorEntityToken e) {
        if (e instanceof PlanetAPI) return ((PlanetAPI) e).isStar() || ((PlanetAPI) e).isBlackHole();
        // stations, salvage, fleets and other objects are flown to like planets
        return pointToken(e) || BATargets.anchor(e);
    }

    /** An AoTD Bifrost gate (it has no "gate" tag). */
    static boolean bifrostGate(SectorEntityToken e) {
        return e != null && BAGates.BIFROST_TYPE.equals(e.getCustomEntityType());
    }

    /**
     * Clears the laid-in course when it leads to step (the course's target or its next step). A course somewhere else,
     * laid in before the player clicked step directly, is kept.
     */
    static void endCourse(CampaignFleetAPI fleet, SectorEntityToken step) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        SectorEntityToken course = ui.getCurrentCourseTarget();
        if (course == null) return;
        boolean ours = course == step;
        if (!ours) {
            try { ours = ui.getNextStepForCourse(course) == step; } catch (Throwable t) { ours = false; }
        }
        if (ours) ui.clearLaidInCourse();
    }

    /** Not steering: nothing is applied, and slipstream drift from an earlier stay is not corrected later. */
    private void idle(String why) {
        slow = false;
        override = false;
        planIn = 0f;
        wasInSlip = false;
        slipExitLeft = 0f;
        // plans made before the player took over (a crossing line-up point, a stream to enter, a detour side) start over
        cross.reset();
        entering = null;
        stickyKey = null;
        stickySide = 0;
        note(why);
    }

    private void note(String why) {
        if (why.equals(state)) return;
        state = why;
        if (BASettings.debug) LOG.info("Auto-er Pilot: " + why);
    }

    private void plan(CampaignFleetAPI fleet, SectorEntityToken step) {
        slow = false;
        override = false;
        fleetNear = false;
        rideSB = false;
        LocationAPI loc = fleet.getContainingLocation();
        Vector2f p = fleet.getLocation(), t = step.getLocation(), v = fleet.getVelocity();
        float px = p.x, py = p.y, tx = t.x, ty = t.y;
        goalX = tx;
        goalY = ty;
        boolean hyper = loc.isHyperspace();
        float vf = Math.max(10f, fleet.getTravelSpeed());
        float accel = Math.max(1f, fleet.getAcceleration());
        // braking: with Sustained Burn on the game cuts acceleration to a fifth while cruising but to 0.6 while braking
        AbilityPlugin sbA = BAAbilities.activeSB(fleet);
        float brake = sbA != null && sbA.isActive() ? Math.max(accel, 0.6f * vf) : accel;
        float speed = v.length();
        String why = "straight to the target";
        String slowText = null;

        // slipstreams
        List<SlipstreamTerrainPlugin2> streams = hyper && (BASettings.slipUse || BASettings.slipExit || BASettings.slipAvoid)
                ? BASlip.streams(loc) : Collections.<SlipstreamTerrainPlugin2>emptyList();
        BASlip.Pos in = streams.isEmpty() ? null : BASlip.inside(streams, p);
        AbilityPlugin rp = hyper && BASettings.slipUse ? BAAbilities.reversePolarity(fleet) : null;
        boolean rev = Misc.isReversePolarity(fleet) || (rp != null && rp.isActive());
        boolean wantRev = false;
        // no usable Reverse Polarity (not owned, setting off, cooldown, fuel): wrong-way streams are kept out of or crossed
        boolean noRp = rp == null || !(rp.isActive() || rp.isUsable());
        BAGeom.Circle slipCircle = null;
        boolean crossing = false;
        slipBusy = false;
        if (wasInSlip && in == null && BASettings.slipExit) slipExitLeft = EXIT_WINDOW;
        wasInSlip = in != null;
        // the game charges for Reverse Polarity switches anywhere inside a stream, also at its faint edges (in is null there)
        boolean inStream = hyper && Misc.isInsideSlipstream(fleet);
        if (in == null) leftStream = null;
        if (!inStream) paidSwitches = 0;

        // no storms inside a slipstream (the game keeps them off there, and go-slow would cut the stream's push)
        HyperspaceTerrainPlugin h = hyper && in == null && !inStream ? BAStorms.plugin(loc) : null;

        // storms: slow down inside or ahead of storming and signaling cells (in slipstreams the game keeps storms off)
        if (h != null && BASettings.storms) {
            float dx, dy;
            if (speed > 20f) { dx = v.x / speed; dy = v.y / speed; }
            else {
                float l = Math.max(1f, BAGeom.dist(px, py, tx, ty));
                dx = (tx - px) / l;
                dy = (ty - py) / l;
            }
            // braking distance from full speed, also while already slow, so the fleet stays slow until the storm is that far off
            float slowV = Misc.getSpeedForBurnLevel(Misc.getGoSlowBurnLevel(fleet));
            float look = BAGeom.brakeDistance(Math.max(speed, vf), slowV, brake) + fleet.getRadius() + 200f;
            if (BAStorms.ahead(h, px, py, dx, dy, look, fleet.getRadius() + 100f) >= 0f) { slow = true; slowText = "storm: moving slowly"; why = slowText; }
        }
        // Enhanced Sustained Burn (SEEKER) stops asteroid impacts while it is on
        boolean rocks = !hyper && BASettings.asteroidsSlow && !BAAbilities.noAsteroidImpacts(fleet);

        // a star, black hole or a point inside a corona or event horizon: stop outside it and end the course there; the
        // way to the stop point goes around other hazards and fleets like any other
        float bx = tx, by = ty;
        boolean away = false;
        BAGeom.Circle hold = !hyper && BASettings.hazards && hazardousTarget(step) ? BAObstacles.holding(loc, fleet, step) : null;
        if (hold != null) {
            float d = BAGeom.dist(px, py, hold.x, hold.y);
            float ux, uy;
            if (d > 1f) { ux = (px - hold.x) / d; uy = (py - hold.y) / d; }
            else { ux = 1f; uy = 0f; }
            float sx = hold.x + ux * hold.r, sy = hold.y + uy * hold.r;
            goalX = sx;
            goalY = sy;
            if (BAGeom.dist(px, py, sx, sy) < 150f + fleet.getRadius() * 0.5f && speed < 40f) {
                endCourse(fleet, step);
                fleet.setInteractionTarget(null);
                // the override lasts one frame; the move destination keeps the fleet at the stop point after it
                fleet.setMoveDestination(sx, sy);
                fleet.setMoveDestinationOverride(sx, sy);
                override = false;
                LOG.info("Auto-er Pilot: course ended outside " + BAObstacles.name(hold) + "; the target " + step.getName()
                        + " is inside it");
                note("course ended outside " + BAObstacles.name(hold));
                return;
            }
            bx = sx;
            by = sy;
            away = true;
            why = "stopping outside " + BAObstacles.name(hold);
        }

        // final approach: the game's own, slowed down in asteroids
        else if (BAGeom.dist(px, py, tx, ty) < step.getRadius() + fleet.getRadius() + 400f) {
            if (rocks && asteroidsAhead(loc, fleet, px, py, tx, ty, speed, vf, brake)) { slow = true; slowText = "asteroids: moving slowly"; why = slowText; }
            why = holdSlow(why, slowText);
            note(slow ? why : "final approach");
            return;
        }

        boolean riding = false;
        if (in != null) {
            entering = null;
            slipBusy = true;
            if (BASettings.slipAvoid && noRp && cross.stream() == in.s) {
                BACross.Step cs = cross.plan(streams, fleet, px, py, tx, ty, vf, rev);
                if (cs != null && cs.circle == null) {
                    bx = cs.x;
                    by = cs.y;
                    away = true;
                    crossing = true;
                    why = cs.why;
                    abilities.sbOff(fleet, "crossing a slipstream");
                }
            }
            if (crossing) { /* straight across, below */ }
            else if (!BASettings.slipUse) riding = true;
            else {
                float[] leave = BASlip.leavePoint(in, fleet, tx, ty, vf, rev);
                String skipped = "";
                // carried away from the target (flow more than RP_ANGLE degrees off), or riding the other way beats
                // leaving: switch Reverse Polarity at once (on, or off when it was turned on here)
                if (rp != null && BASettings.slipReverseInside && rpHold <= 0f && (!rev || abilities.rpMine)
                        && paidSwitches < MAX_PAID_SWITCHES) {
                    float[] f = BASlip.flow(in.s, in.along, rev);
                    // not at the stream's edge (outer fifth of its width), where the fleet is about out anyway
                    boolean awayNow = f != null && Math.abs(in.side) < 0.8f
                            && BAGeom.angleBetween(f[0], f[1], tx - px, ty - py) > RP_ANGLE;
                    boolean otherBetter = false;
                    if (!awayNow && leave != null) {
                        // the other way must not flow away from the target here, or the next plan switches back
                        float[] fo = BASlip.flow(in.s, in.along, !rev);
                        float now = BAGeom.dist(px, py, tx, ty) / vf;
                        float other = BASlip.otherWay(in, fleet, tx, ty, vf, rev);
                        otherBetter = other > 0f && other < now * (1f - BASettings.slipSaving / 100f)
                                && fo != null && BAGeom.angleBetween(fo[0], fo[1], tx - px, ty - py) <= RP_ANGLE;
                    }
                    if (awayNow || otherBetter) {
                        if (BAAbilities.safeToSwitch(fleet, rp)) {
                            if (!rev) abilities.rpOn(rp, "the slipstream flows away from the target");
                            else abilities.rpOff(rp, "the slipstream flows toward the target");
                            rpHold = 5f;
                            paidSwitches++;
                            if (paidSwitches >= MAX_PAID_SWITCHES) LOG.info("Auto-er Pilot: Reverse Polarity: "
                                    + MAX_PAID_SWITCHES + " paid switches in this slipstream; no more until the fleet leaves it");
                            rev = rp.isActive();
                            leave = BASlip.leavePoint(in, fleet, tx, ty, vf, rev);
                        } else skipped = " (Reverse Polarity skipped: a ship's CR is below its cost, or fuel is short)";
                    }
                }
                if (leave == null) { riding = true; why = rev ? "slipstream: riding with Reverse Polarity" : "slipstream: riding"; }
                else {
                    bx = leave[0];
                    by = leave[1];
                    away = true;
                    why = "slipstream: leaving" + skipped;
                    leftStream = in.s;
                    if ((noRp && BASettings.slipAvoid) || BASettings.keepSB) abilities.sbOff(fleet, "leaving a slipstream");
                }
            }
        } else if (BASettings.slipUse && !streams.isEmpty()) {
            boolean[] pol;
            if (rp != null && rp.isActive() && !abilities.rpMine) pol = new boolean[] {true};        // the player's choice
            else if (rp != null && (rp.isActive() || rp.isUsable())) pol = new boolean[] {false, true};
            else pol = new boolean[] {rev};
            float[] e = BASlip.entryPoint(streams, fleet, tx, ty, vf, entering, pol);
            entering = BASlip.chosen;
            BASlip.chosen = null;   // static: holding the stream would keep this save in memory after loading another
            if (e != null) {
                bx = e[0];
                by = e[1];
                away = true;
                slipBusy = true;
                wantRev = BASlip.chosenRev;
                why = wantRev ? "slipstream: entering with Reverse Polarity" : "slipstream: entering";
                // switch before the stream, where it costs nothing
                if (rp != null && rpHold <= 0f && !Misc.isInsideSlipstream(fleet)) {
                    if (wantRev && !rp.isActive() && BAAbilities.safeToSwitch(fleet, rp)) {
                        abilities.rpOn(rp, "entering a slipstream against its flow");
                        rpHold = 3f;
                    } else if (!wantRev && rp.isActive()) {
                        abilities.rpOff(rp, "entering a slipstream with its flow");
                        rpHold = 3f;
                    }
                }
            }
        }
        // a slipstream across the way ahead: polarity set before reaching it, where switching costs nothing
        if (in == null && !slipBusy && rp != null && !streams.isEmpty() && rpHold <= 0f && !Misc.isInsideSlipstream(fleet)) {
            float dx, dy;
            if (speed > 50f) { dx = v.x / speed; dy = v.y / speed; }
            else {
                float l = Math.max(1f, BAGeom.dist(px, py, tx, ty));
                dx = (tx - px) / l;
                dy = (ty - py) / l;
            }
            float look = Math.min(BAObstacles.MAX_LOOK, Math.max(1200f, speed * 2.5f) + fleet.getRadius());
            float[] cr = BASlip.crossing(streams, px, py, dx, dy, look, fleet.getRadius());
            if (cr != null) {
                float ang = BAGeom.angleBetween(cr[0], cr[1], tx - px, ty - py);
                if (ang > RP_ANGLE) {
                    wantRev = true;
                    if (!rp.isActive() && BAAbilities.safeToSwitch(fleet, rp)) {
                        abilities.rpOn(rp, "a slipstream ahead flows away from the target");
                        rpHold = 3f;
                    }
                    why = "slipstream ahead: Reverse Polarity";
                } else if (ang >= 180f - RP_ANGLE) {
                    wantRev = rp.isActive() && abilities.rpMine;   // across the way: kept as it is
                }
            }
        }
        // no usable Reverse Polarity: a stream on the way that does not flow toward the target is kept out of (target on
        // this side: along its edge) or crossed straight across at the cheapest place, with Sustained Burn off
        if (in == null && !slipBusy && BASettings.slipAvoid && noRp && !streams.isEmpty()) {
            BACross.Step cs = cross.plan(streams, fleet, px, py, tx, ty, vf, rev);
            if (cs != null) {
                why = cs.why;
                if (cs.circle != null) slipCircle = cs.circle;
                else {
                    bx = cs.x;
                    by = cs.y;
                    away = true;
                    slipBusy = true;
                    crossing = cs.across;
                    if (cs.across || BAGeom.dist(px, py, cs.x, cs.y) < 1500f) abilities.sbOff(fleet, "crossing a slipstream");
                }
            }
        }
        // out of the streams with nothing reversed ahead: turn off what was turned on here (free outside a stream), also
        // when a setting change took Reverse Polarity out of the steering (rp null)
        AbilityPlugin rpOwn = rp != null ? rp : fleet.getAbility(BAAbilities.RP);
        if (in == null && !wantRev && rpOwn != null && abilities.rpMine && rpHold <= 0f && !Misc.isInsideSlipstream(fleet)) {
            abilities.rpOff(rpOwn, "out of the slipstream");
        }

        // obstacles
        List<BAGeom.Circle> circles = new ArrayList<BAGeom.Circle>();
        if (BASettings.hazards) BAObstacles.hazards(loc, fleet, step, circles);
        if (BASettings.fleets) BAObstacles.fleets(loc, fleet, step, circles);
        // a stronger hostile fleet that could reach the fleet within 8 seconds (the NPC rule for fleeing: spin-up plus 5);
        // independent of the fleet setting and of what the fleet sees (Sustained Burn doubles how far it is seen)
        fleetNear = BAObstacles.strongerNear(loc, fleet, FLEE_SECONDS);
        if (h != null && BASettings.stormDetour) {
            BAGeom.Circle storm = BAStorms.detour(h, fleet.getRadius(), px, py, bx, by, BASettings.stormDetourShare / 100f, STEP);
            if (storm != null) circles.add(storm);
        }
        if (slipCircle != null) circles.add(slipCircle);
        circles = BAGeom.merge(circles, px, py, bx, by);
        if (BASettings.debug && listObstacles) {
            listObstacles = false;
            StringBuilder sb = new StringBuilder("Auto-er Pilot: obstacles for " + step.getName() + ":");
            if (circles.isEmpty()) sb.append(" none");
            for (BAGeom.Circle c : circles) {
                sb.append(' ').append(BAObstacles.name(c)).append(" (radius ").append(Math.round(c.r)).append(", line passes at ")
                        .append(Math.round(BAGeom.segDist(px, py, bx, by, c.x, c.y))).append(");");
            }
            LOG.info(sb.toString());
        }
        BAGeom.Route r = BAGeom.route(px, py, bx, by, circles, stickyKey, stickySide, STEP);
        if (r.around != null) {
            stickyKey = r.around.key;
            stickySide = r.side;
            why = "around " + BAObstacles.name(r.around);
        }
        float wx = r.x, wy = r.y;

        // asteroid belts and fields: slow down in and ahead of them on the way (slow-moving fleets take no impacts)
        if (rocks && asteroidsAhead(loc, fleet, px, py, wx, wy, speed, vf, brake)) {
            slow = true;
            slowText = "asteroids: moving slowly";
        }
        boolean steer = away || r.around != null;
        if (riding && r.around == null) steer = false;   // the stream steers

        // riding with the flow, lined up with it and away from its edges: Sustained Burn may go on (no stop in a stream)
        if (riding && r.around == null && BASettings.slipUse && in != null && in.s != leftStream
                && Math.abs(in.side) < RIDE_SB_SIDE && speed > 50f) {
            float[] f = BASlip.flow(in.s, in.along, rev);
            rideSB = f != null && BAGeom.angleBetween(f[0], f[1], v.x, v.y) < RIDE_SB_ANGLE;
        }

        // crossing a stream: the acceleration goes against the speed error, so the push along the stream is fought too
        if (crossing) {
            float l = Math.max(1f, BAGeom.dist(px, py, wx, wy));
            float[] a = BAGeom.aimFor(px, py, v.x, v.y, (wx - px) / l * vf, (wy - py) / l * vf, accel, 10f);
            wx = a[0];
            wy = a[1];
            steer = true;
        }

        // after a slipstream: point the acceleration at the speed error, so the sideways speed goes first
        if (in == null && slipExitLeft > 0f) {
            float l = Math.max(1f, BAGeom.dist(px, py, wx, wy));
            float wantX = (wx - px) / l * vf, wantY = (wy - py) / l * vf;
            if (BAGeom.angleBetween(v.x, v.y, wantX, wantY) > 8f || speed > vf * 1.05f) {
                float[] a = BAGeom.aimFor(px, py, v.x, v.y, wantX, wantY, accel, 10f);
                wx = a[0];
                wy = a[1];
                steer = true;
                why = why + ", slipstream drift correction";
            } else slipExitLeft = 0f;
        }
        override = steer;
        aimX = wx;
        aimY = wy;
        note(holdSlow(why, slowText));
    }

    /**
     * Keeps a storm or asteroid slow-down for SLOW_HOLD seconds after its last trigger (slowText: what triggered it this
     * plan, or null). Returns the state text with the slow-down named once, the same while triggered and while held.
     */
    private String holdSlow(String why, String slowText) {
        if (slow && slowText != null) {
            slowHold = SLOW_HOLD;
            slowWhy = slowText;
        } else if (slowHold > 0f) slow = true;
        if (!slow || slowWhy.isEmpty() || why.contains(slowWhy)) return why;
        String plain = "straight to the target";
        if (why.startsWith(plain)) return slowWhy + why.substring(plain.length());
        return why + ", " + slowWhy;
    }

    /**
     * Asteroids in the fleet's way toward (ax, ay): inside one now, or entering one within the braking distance from full
     * speed (so the fleet is slow when it gets there).
     */
    private static boolean asteroidsAhead(LocationAPI loc, CampaignFleetAPI fleet, float px, float py, float ax, float ay,
                                          float speed, float vf, float accel) {
        float l = BAGeom.dist(px, py, ax, ay);
        float dx = l > 1f ? (ax - px) / l : 0f, dy = l > 1f ? (ay - py) / l : 0f;
        float slowV = Misc.getSpeedForBurnLevel(Misc.getGoSlowBurnLevel(fleet));
        float look = BAGeom.brakeDistance(Math.max(speed, vf), slowV, accel) + 100f;
        return BAObstacles.asteroidsAhead(loc, fleet, px, py, dx, dy, look);
    }

    /**
     * Go-slow first; while the game brakes (above slow speed) it needs its own destination, so no override then. The same
     * while Go Dark holds the fleet slow. An aim point that is not a finite number is never sent (it would be saved).
     */
    private void apply(CampaignFleetAPI fleet) {
        // Abyssal Burn cancels the go-slow: off while the steering slows for storms or asteroids (back on later)
        if (slow && BAAbilities.cancelsSlow(BAAbilities.activeSB(fleet))) abilities.sbOff(fleet, "Abyssal Burn cancels the slow-down");
        if (slow) {
            fleet.goSlowOneFrame(false);
            slowSentAgo = 0f;
        }
        if (slow || darkSlow) {
            float slowV = Misc.getSpeedForBurnLevel(Misc.getGoSlowBurnLevel(fleet));
            if (fleet.getVelocity().length() > slowV + 0.1f * fleet.getAcceleration()) return;
        }
        if (!override) return;
        if (Float.isNaN(aimX) || Float.isNaN(aimY) || Float.isInfinite(aimX) || Float.isInfinite(aimY)) {
            override = false;
            String msg = "aim point not a number";
            if (!msg.equals(lastError)) { lastError = msg; LOG.warn("Auto-er Pilot: steering skipped: " + msg); }
            return;
        }
        fleet.setMoveDestinationOverride(aimX, aimY);
    }
}
