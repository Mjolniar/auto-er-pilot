package data.scripts.betterautopilot;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * Picks the dialog for a jump point (hidden jump into or out of a system, BAJump) and for the entry gate of a gate route (hidden
 * transit, BAGates). Returns null otherwise, so the game's own dialog opens. Transient: registered on every game load.
 */
public class BACampaignPlugin extends BaseCampaignPlugin {
    public static final String ID = "better_autopilot_dialogs";
    private static String lastError;

    @Override
    public String getId() { return ID; }

    @Override
    public boolean isTransient() { return true; }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken target) {
        try {
            if (target instanceof JumpPointAPI) {
                if (!BAJump.skipDialog((JumpPointAPI) target)) return null;
                return new PluginPick<InteractionDialogPlugin>(new BAJumpDialog(), CampaignPlugin.PickPriority.MOD_GENERAL);
            }
            InteractionDialogPlugin gate = BAGates.pickDialog(target);
            if (gate != null) return new PluginPick<InteractionDialogPlugin>(gate, CampaignPlugin.PickPriority.MOD_GENERAL);
        } catch (Throwable t) {
            String msg = String.valueOf(t);
            if (!msg.equals(lastError)) { lastError = msg; BAJump.LOG.warn("Auto-er Pilot: dialog pick failed; the game's dialog opens", t); }
        }
        return null;
    }
}
