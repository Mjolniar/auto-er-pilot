package data.scripts.betterautopilot;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

/**
 * Reads the settings at startup and again on every save load (LunaLib has loaded its settings by then), and adds the
 * steering script and the dialog plugin (jumps and gates) on every load. Both are transient: nothing goes into the save.
 */
public class BAModPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() {
        try { if (BASettings.luna()) BALuna.register(); }
        catch (Throwable t) { Global.getLogger(BAModPlugin.class).warn("Auto-er Pilot: LunaLib link failed; using " + BASettings.FILE, t); }
        BASettings.load();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        if (!BASettings.load()) {
            Global.getLogger(BAModPlugin.class).warn("Auto-er Pilot: LunaLib settings not loaded; using " + BASettings.FILE);
            BASettings.load(false);
        }
        BASlip.chosen = null;
        BAGates.load();
        try {
            Global.getSector().unregisterPlugin(BACampaignPlugin.ID);
            Global.getSector().registerPlugin(new BACampaignPlugin());
        } catch (Throwable t) {
            Global.getLogger(BAModPlugin.class).warn("Auto-er Pilot: dialog plugin not registered; jump and gate dialogs stay as they are", t);
        }
        if (Global.getSector().hasTransientScript(BAScript.class)) return;
        Global.getSector().addTransientScript(new BAScript());
        Global.getLogger(BAModPlugin.class).info("Auto-er Pilot: steering script added");
    }
}
