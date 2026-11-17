package data.scripts.betterautopilot;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.JumpPointInteractionDialogPluginImpl;

/**
 * The game's jump point dialog, opened hidden. One frame after it opens it gives the game's own "Order a jump" order.
 * When the dialog is still needed after that (the game asked about the transponder), it is shown as normal.
 */
public class BAJumpDialog extends JumpPointInteractionDialogPluginImpl {
    /** The game's dialog background dim. */
    static final float DIM = 0.75f;

    private InteractionDialogAPI dlg;
    private int step;

    @Override
    public void init(InteractionDialogAPI dialog) {
        dlg = dialog;
        dialog.setOpacity(0f);
        dialog.setBackgroundDimAmount(0f);
        super.init(dialog);
        step = 1;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (step != 1) return;
        step = 2;
        Object jump = BAJump.jumpOption();
        try {
            if (jump == null) { show("option not found"); return; }
            String name = dlg.getInteractionTarget() == null ? "?" : dlg.getInteractionTarget().getName();
            optionSelected(null, jump);
            Object confirm = BAJump.confirmOption();
            if (confirm != null && dlg.getOptionPanel().hasOption(confirm)) { show("the game asked about the transponder"); return; }
            BAJump.LOG.info("Auto-er Pilot: jumped without the dialog at " + name);
        } catch (Throwable t) {
            BAJump.LOG.warn("Auto-er Pilot: jump without the dialog failed; dialog shown", t);
            show("error");
        }
    }

    private void show(String why) {
        BAJump.LOG.info("Auto-er Pilot: jump dialog shown: " + why);
        dlg.setOpacity(1f);
        dlg.setBackgroundDimAmount(DIM);
    }
}
