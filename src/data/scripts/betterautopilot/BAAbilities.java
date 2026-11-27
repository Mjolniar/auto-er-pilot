package data.scripts.betterautopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.SustainedBurnAbility;
import com.fs.starfarer.api.impl.campaign.abilities.ToggleAbilityWithCost;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Sustained Burn and Reverse Polarity for the player fleet.
 * Sustained Burn: turned on for long trips the steering flies, also while riding a slipstream with its flow, and back on
 * when the game or another ability turned it off. A turn-off within 0.6 seconds of a key or mouse button press, with no other ability taking its place, counts as
 * the player's and is left alone until the next target or until the player turns it on again.
 * Reverse Polarity: switched by the steering script; this class only turns off what it turned on.
 */
final class BAAbilities {
    static final String SB = "sustained_burn", RP = "reverse_polarity", GO_DARK = "go_dark";
    /**
     * Burn variants of other mods, used when the fleet has them. SEEKER's Enhanced Sustained Burn and Enhanced Emergency
     * Burn take the game's abilities' places on the ability bar. RAT's Abyssal Burn: 75 percent more burn (the game's
     * Sustained Burn: 100), acceleration x0.35 (x0.2), less detection, made for the Abyssal Depths; it cancels any go-slow
     * while it is on.
     */
    static final String SB_SEEKER = "SKR_sustained_burn", SB_ABYSS = "rat_abyssal_burn";
    static final String EB = "emergency_burn", EB_SEEKER = "SKR_emergency_burn";
    /** RAT's Abyssal Depths system tag. */
    static final String ABYSS_TAG = "rat_abyssal_depths";
    private static final String[] SB_ORDER = {SB_SEEKER, SB, SB_ABYSS}, SB_ORDER_ABYSS = {SB_ABYSS, SB_SEEKER, SB};

    /**
     * The Sustained Burn the steering turns on: in the Abyssal Depths, Abyssal Burn first; elsewhere Enhanced Sustained
     * Burn, then the game's, then Abyssal Burn (less top speed).
     */
    static AbilityPlugin sustainedBurn(CampaignFleetAPI fleet) {
        boolean abyss = fleet.getContainingLocation() != null && fleet.getContainingLocation().hasTag(ABYSS_TAG);
        for (String id : abyss ? SB_ORDER_ABYSS : SB_ORDER) {
            AbilityPlugin a = fleet.getAbility(id);
            if (a != null) return a;
        }
        return null;
    }

    /** The Sustained Burn that is on now (any kind), or null. */
    static AbilityPlugin activeSB(CampaignFleetAPI fleet) {
        for (String id : SB_ORDER) {
            AbilityPlugin a = fleet.getAbility(id);
            if (a != null && a.isActive()) return a;
        }
        return null;
    }

    /** Abyssal Burn is on: it cancels the go-slow, so it goes off while the steering slows the fleet. */
    static boolean cancelsSlow(AbilityPlugin a) {
        return a != null && SB_ABYSS.equals(a.getId());
    }

    /** The Emergency Burn the steering uses: Enhanced Emergency Burn when the fleet has it, else the game's. */
    static AbilityPlugin emergencyBurn(CampaignFleetAPI fleet) {
        AbilityPlugin a = fleet.getAbility(EB_SEEKER);
        return a != null ? a : fleet.getAbility(EB);
    }

    /** Enhanced Sustained Burn is on: it stops asteroid impacts, so the asteroid slow-down is not needed. */
    static boolean noAsteroidImpacts(CampaignFleetAPI fleet) {
        AbilityPlugin a = activeSB(fleet);
        return a != null && SB_SEEKER.equals(a.getId());
    }
    /** The ability tag of the game's abilities that hold the fleet slow while they run (Go Dark, Sensor Burst, Interdiction Pulse). */
    static final String SLOW_TAG = "burn-";
    static final long INPUT_WINDOW_NS = 600_000_000L;

    private final boolean[] keys = new boolean[Keyboard.KEYBOARD_SIZE];
    private final boolean[] buttons = new boolean[3];
    private long inputAt = Long.MIN_VALUE / 2;

    /** sbPlayerOff: the player turned Sustained Burn off during this target; sbOnHere/sbOffHere: switched here. */
    private boolean sbWas, sbPlayerOff, sbOnHere, sbOffHere;
    /** sbOff turned Sustained Burn off for a slipstream: it goes back on later even with the setting off. */
    private boolean sbRestore;
    private float sbRetryIn;
    /** When this class last turned Sustained Burn on (System.nanoTime). */
    private long sbOnAt = Long.MIN_VALUE / 2;
    /** A game turn-off within this time after a turn-on here means the next try waits QUICK_OFF_WAIT seconds. */
    static final long QUICK_OFF_NS = 3_000_000_000L;
    static final float QUICK_OFF_WAIT = 60f;
    /** Shortest trip for Sustained Burn, in units (the NPC fleets' close range, where they turn it off). */
    static final float SB_MIN_DISTANCE = 2000f;
    /**
     * Shortest trip for Sustained Burn, in seconds at the current travel speed, on top of twice its spin-up time. With it
     * the fleet stops, then accelerates to about double speed at a fifth of its acceleration (about 5 seconds), so it
     * arrives sooner only on trips longer than about 6 seconds, or 9 with flat burn bonuses (Nav Buoy, tugs).
     */
    static final float SB_MIN_SECONDS = 10f;
    /**
     * Reverse Polarity turned on here (so it may be turned off here). Kept in the player fleet's memory under RP_MINE, so
     * it survives saving and loading; read back every frame.
     */
    boolean rpMine;
    static final String RP_MINE = "$better_autopilot_rpMine";

    /** Keys that never switch an ability: Shift, Ctrl, Alt (speed-up and modifiers) and Space (pause). */
    static boolean ignoredKey(int k) {
        return k == Keyboard.KEY_LSHIFT || k == Keyboard.KEY_RSHIFT || k == Keyboard.KEY_LCONTROL || k == Keyboard.KEY_RCONTROL
                || k == Keyboard.KEY_LMENU || k == Keyboard.KEY_RMENU || k == Keyboard.KEY_SPACE;
    }

    /** Every frame, also while paused: key and mouse presses, and Sustained Burn turning on or off. */
    void track(CampaignFleetAPI fleet) {
        long now = System.nanoTime();
        try {
            for (int k = 1; k < keys.length; k++) {
                boolean d = Keyboard.isKeyDown(k);
                if (d && !keys[k] && !ignoredKey(k)) inputAt = now;
                keys[k] = d;
            }
            for (int b = 0; b < buttons.length; b++) {
                boolean d = Mouse.isButtonDown(b);
                if (d && !buttons[b]) inputAt = now;
                buttons[b] = d;
            }
        } catch (Throwable t) { /* no input devices */ }

        AbilityPlugin act = activeSB(fleet);
        AbilityPlugin sb = act != null ? act : sustainedBurn(fleet);
        boolean on = act != null;
        if (on && !sbWas) {
            if (!sbOnHere) {   // the player (or the game) turned it on
                sbPlayerOff = false;
                fleet.getMemoryWithoutUpdate().unset(SB_OFF_FOR);
            }
            sbOnHere = false;
            sbRestore = false;
        }
        if (!on && sbWas) {
            boolean byPlayer = !sbOffHere && now - inputAt < INPUT_WINDOW_NS && !otherActive(fleet, sb);
            // turned off by the game soon after this class turned it on: wait longer, each start outside a stream stops the fleet
            if (!byPlayer && !sbOffHere && now - sbOnAt < QUICK_OFF_NS) {
                sbRetryIn = Math.max(sbRetryIn, QUICK_OFF_WAIT);
                BAScript.LOG.info("Auto-er Pilot: Sustained Burn turned off by the game right after it was turned on; next try in "
                        + Math.round(QUICK_OFF_WAIT) + " seconds");
            }
            sbOffHere = false;
            if (byPlayer) {
                sbPlayerOff = true;
                // kept in the fleet's memory with the target, so a save and load keeps it off for this target
                // (not for a fleet: the save would keep it after it despawns)
                if (target != null && !(target instanceof CampaignFleetAPI)) fleet.getMemoryWithoutUpdate().set(SB_OFF_FOR, target);
            }
            if (BASettings.debug) {
                BAScript.LOG.info("Auto-er Pilot: Sustained Burn turned off" + (byPlayer ? " by the player" : " by the game or another ability"));
            }
        }
        sbWas = on;

        AbilityPlugin rp = fleet.getAbility(RP);
        rpMine = fleet.getMemoryWithoutUpdate().getBoolean(RP_MINE);
        if (rpMine && (rp == null || !rp.isActive())) {
            fleet.getMemoryWithoutUpdate().unset(RP_MINE);
            rpMine = false;
        }
    }

    /**
     * A new target: a turn-off by the player applies to one target only, and Sustained Burn turned off for a slipstream of
     * the last target is not turned back on for this one with the setting off.
     */
    /** A key (not Shift, Ctrl, Alt or Space) or mouse button was pressed within ns nanoseconds. */
    boolean inputWithin(long ns) { return System.nanoTime() - inputAt < ns; }

    void newTarget(CampaignFleetAPI fleet, SectorEntityToken step) {
        sbRestore = false;
        nearOff = false;
        target = step;
        // the player's turn-off for this target, from before a save and load
        MemoryAPI m = fleet.getMemoryWithoutUpdate();
        Object off = m.get(SB_OFF_FOR);
        sbPlayerOff = off != null && off == step;
        if (off != null && off != step) m.unset(SB_OFF_FOR);
    }

    /** The target the fleet flies itself to (set by newTarget). */
    private SectorEntityToken target;
    /** Player fleet memory: the target the player turned Sustained Burn off for. */
    static final String SB_OFF_FOR = "$better_autopilot_sbOffFor";

    /** Sustained Burn was turned off near this target once (turned on again after that, it stays on). */
    private boolean nearOff;

    /**
     * Near where the trip ends (within SB_MIN_DISTANCE), Sustained Burn goes off once per target, as NPC fleets do close
     * to their destination: normal acceleration for the arrival.
     */
    void offNear(CampaignFleetAPI fleet, float left) {
        if (nearOff || left >= SB_MIN_DISTANCE) return;
        AbilityPlugin sb = activeSB(fleet);
        if (sb == null || !sb.isActive()) return;
        nearOff = true;
        sbOff(fleet, "close to the target");
    }

    /**
     * The id of an active or winding-up ability with the slow-down tag, or null. Any other such ability comes before Go
     * Dark, so Go Dark is returned only when it is the only one.
     */
    static String slowAbility(CampaignFleetAPI fleet) {
        String found = null;
        for (AbilityPlugin a : fleet.getAbilities().values()) {
            if (a == null || !a.isActiveOrInProgress() || a.getSpec() == null || !a.getSpec().hasTag(SLOW_TAG)) continue;
            if (!GO_DARK.equals(a.getId())) return a.getId();
            found = a.getId();
        }
        return found;
    }

    /**
     * While the fleet flies itself to a target: turns Sustained Burn on when it is usable, no incompatible ability is
     * active or winding up (Go Dark, Emergency Burn and others the game does not allow with it), the player did not turn it off
     * during this target, and the straight trip is longer than SB_MIN_DISTANCE and takes more than twice its spin-up time
     * plus SB_MIN_SECONDS. quiet: the steering is slowing down, in or near a slipstream, correcting drift after one, or
     * a stronger hostile fleet is close by (turning it on stops the fleet). distance: to the target, or to the stop point
     * outside a hazard that holds the target.
     * riding: the fleet rides a slipstream with its flow. The game then keeps the fleet's speed when Sustained Burn starts
     * (memory SB_NO_STOP and SB_NO_SLOW, set by the stream each frame), so quiet does not apply and the spin-up costs no
     * time; the trip must take more than SB_MIN_SECONDS at the current speed.
     */
    void keepSustainedBurn(CampaignFleetAPI fleet, float distance, boolean quiet, boolean riding, float amount) {
        if (sbRetryIn > 0f) sbRetryIn -= amount;
        // with the setting off, only Sustained Burn that sbOff turned off for a slipstream is turned back on
        if ((!BASettings.keepSB && !sbRestore) || sbPlayerOff || sbRetryIn > 0f) return;
        if (quiet && !riding) return;
        // no fuel in hyperspace: the game's Sustained Burn is not usable then; modded kinds may not check it
        if (fleet.isInHyperspace() && fleet.getCargo().getFuel() <= 0f) return;
        AbilityPlugin sb = sustainedBurn(fleet);
        if (sb == null || sb.isActiveOrInProgress() || !sb.isUsable() || activeSB(fleet) != null) return;
        if (otherActive(fleet, sb)) return;
        if (distance < SB_MIN_DISTANCE) return;
        float seconds;
        String where;
        if (riding) {
            MemoryAPI mem = fleet.getMemoryWithoutUpdate();
            if (!mem.is(SustainedBurnAbility.SB_NO_STOP, true) || !mem.is(SustainedBurnAbility.SB_NO_SLOW, true)) return;
            seconds = distance / Math.max(10f, Math.max(fleet.getVelocity().length(), fleet.getTravelSpeed()));
            if (seconds <= SB_MIN_SECONDS) return;
            where = "riding a slipstream, trip of ";
        } else {
            float spinUp = sb.getSpec().getActivationDays() * Global.getSector().getClock().getSecondsPerDay();
            seconds = distance / Math.max(10f, fleet.getTravelSpeed());
            if (seconds <= spinUp * 2f + SB_MIN_SECONDS) return;
            where = "trip of ";
        }
        sbOnHere = true;
        sb.activate();
        if (!sb.isActiveOrInProgress()) sbOnHere = false;
        else sbOnAt = System.nanoTime();
        sbRetryIn = 10f;
        BAScript.LOG.info("Auto-er Pilot: Sustained Burn on (" + where + Math.round(seconds) + " seconds)");
    }

    /**
     * Turns Sustained Burn off for a slipstream crossing or exit, so the fleet has its full acceleration against the push
     * (with it on the game cuts acceleration to a fifth when the fleet turns less than 120 degrees). It counts as turned
     * off by the game, so keepSustainedBurn turns it back on once the way is clear.
     */
    void sbOff(CampaignFleetAPI fleet, String why) {
        AbilityPlugin sb = activeSB(fleet);
        if (sb == null || !sb.isActive()) return;
        sbOffHere = true;
        sb.deactivate();
        if (sb.isActive()) sbOffHere = false;
        else {
            sbRestore = true;
            BAScript.LOG.info("Auto-er Pilot: Sustained Burn off (" + why + ")");
        }
    }

    /** True when another active or winding-up ability on the fleet does not go with ability a (the game's own rule). */
    static boolean otherActive(CampaignFleetAPI fleet, AbilityPlugin a) {
        if (a == null || a.getSpec() == null) return false;
        for (AbilityPlugin o : fleet.getAbilities().values()) {
            if (o == null || o == a || o.getSpec() == null || !o.isActiveOrInProgress()) continue;
            if (!compatible(a.getSpec(), o.getSpec())) return true;
        }
        return false;
    }

    /** BaseAbilityPlugin.isCompatible, on specs. */
    static boolean compatible(AbilitySpecAPI a, AbilitySpecAPI b) {
        for (String tag : a.getTags()) {
            if (!a.isPositiveTag(tag) && !a.isNegativeTag(tag)) continue;
            if (a.isPositiveTag(tag) && b.hasTag(tag)) return false;
            if (b.hasOppositeTag(tag)) return false;
        }
        return true;
    }

    /** The fleet's Reverse Polarity when it has it and the setting is on, else null. */
    static AbilityPlugin reversePolarity(CampaignFleetAPI fleet) {
        return BASettings.slipReverse ? fleet.getAbility(RP) : null;
    }

    /**
     * True when switching Reverse Polarity now damages no ship: inside a slipstream the switch costs each ship CR (its
     * deployment cost x 0.25) and ships below that may be damaged; outside it costs nothing. Also needs the fuel.
     */
    static boolean safeToSwitch(CampaignFleetAPI fleet, AbilityPlugin rp) {
        // a toggle's progress is its level (1 while on), so only a level between 0 and 1 means it is switching now
        if (rp == null || !rp.isUsable() || BAGeom.midSwitch(rp.getProgressFraction())) return false;
        if (!Misc.isInsideSlipstream(fleet)) return true;
        if (!(rp instanceof ToggleAbilityWithCost)) return true;
        ToggleAbilityWithCost t = (ToggleAbilityWithCost) rp;
        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            if (Math.round(m.getRepairTracker().getCR() * 100f) < Math.round(t.getCRCost(m, false) * 100f)) return false;
        }
        return true;
    }

    void rpOn(AbilityPlugin rp, String why) {
        if (rp == null || rp.isActive()) return;
        rp.activate();
        if (rp.isActive()) {
            rpMine = true;
            rp.getEntity().getMemoryWithoutUpdate().set(RP_MINE, true);
            BAScript.LOG.info("Auto-er Pilot: Reverse Polarity on (" + why + ")");
        }
    }

    void rpOff(AbilityPlugin rp, String why) {
        if (rp == null || !rp.isActive() || !rpMine) return;
        rp.deactivate();
        if (!rp.isActive()) {
            rpMine = false;
            rp.getEntity().getMemoryWithoutUpdate().unset(RP_MINE);
            BAScript.LOG.info("Auto-er Pilot: Reverse Polarity off (" + why + ")");
        }
    }
}
