package data.scripts.betterautopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import java.util.Map;

/** A hidden dialog for the entry gate of a gate route. One frame after it opens it orders the transit (BAGates.transit). */
public class BAGateDialog implements InteractionDialogPlugin {
    static final String LEAVE = "ba_leave";
    private InteractionDialogAPI dlg;
    private boolean pending;

    @Override
    public void init(InteractionDialogAPI dialog) {
        dlg = dialog;
        dialog.setOpacity(0f);
        dialog.setBackgroundDimAmount(0f);
        dialog.hideVisualPanel();
        dialog.setOptionOnEscape("Leave", LEAVE);
        pending = true;
    }

    @Override
    public void advance(float amount) {
        if (!pending) return;
        pending = false;
        try {
            BAGates.transit(dlg);
        } catch (Throwable t) {
            BAGates.LOG.warn("Auto-er Pilot: gate transit failed; course to the target laid in again", t);
            BAGates.failed();
            close();
        }
    }

    private void close() {
        try { dlg.dismiss(); } catch (Throwable t) { }
        Global.getSector().setPaused(false);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) { close(); }

    @Override
    public void optionMousedOver(String optionText, Object optionData) { }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) { }

    @Override
    public Object getContext() { return null; }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() { return null; }
}
