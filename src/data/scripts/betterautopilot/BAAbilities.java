package data.scripts.betterautopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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

        AbilityPlugin sb = fleet.getAbility(SB);
        boolean on = sb != null && sb.isActive();
        if (on && !sbWas) {
            if (!sbOnHere) sbPlayerOff = false;   // the player (or the game) turned it on
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
            if (byPlayer) sbPlayerOff = true;
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

    /** A new target: a turn-off by the player applies to one target only. */
    void newTarget() { sbPlayerOff = false; }

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
     * going around a fleet (turning it on stops the fleet).
     * riding: the fleet rides a slipstream with its flow. The game then keeps the fleet's speed when Sustained Burn starts
     * (memory SB_NO_STOP and SB_NO_SLOW, set by the stream each frame), so quiet does not apply and the spin-up costs no
     * time; the trip must take more than SB_MIN_SECONDS at the current speed.
     */
    void keepSustainedBurn(CampaignFleetAPI fleet, float distance, boolean quiet, boolean riding, float amount) {
        if (sbRetryIn > 0f) sbRetryIn -= amount;
        // with the setting off, only Sustained Burn that sbOff turned off for a slipstream is turned back on
        if ((!BASettings.keepSB && !sbRestore) || sbPlayerOff || sbRetryIn > 0f) return;
        if (quiet && !riding) return;
        AbilityPlugin sb = fleet.getAbility(SB);
        if (sb == null || sb.isActiveOrInProgress() || !sb.isUsable()) return;
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
        AbilityPlugin sb = fleet.getAbility(SB);
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
        if (a == null) return false;
        for (AbilityPlugin o : fleet.getAbilities().values()) {
            if (o == a || !o.isActiveOrInProgress()) continue;
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
        if (rp == null || !rp.isUsable() || rp.isInProgress()) return false;
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
