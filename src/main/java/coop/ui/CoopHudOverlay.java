package coop.ui;

import coop.net.CoopSessionManager;
import coop.net.CoopSessionManager.Role;
import init.constant.C;
import init.sprite.UI.UI;
import snake2d.CORE;
import snake2d.KEYCODES;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.Rec;
import util.colors.GCOLOR;
import view.keyboard.KEYS;

/**
 * Renders the persistent lightweight in-game HUD indicator and delegates
 * click and keyboard interactions to CoopOverlayDialog.
 */
public class CoopHudOverlay {

    private final Rec pillBody = new Rec(220, 26);
    private boolean hovered = false;
    private boolean f8WasDown = false;

    public CoopHudOverlay() {
        updatePosition();
    }

    private void updatePosition() {
        // Dock pill near top-right corner
        int x = C.DIM().width() - pillBody.width() - 20;
        int y = 8;
        pillBody.moveX1(x);
        pillBody.moveY1(y);
    }

    public void update(double ds) {
        updatePosition();

        // Edge-triggered F8 shortcut to toggle the co-op session overlay
        if (CORE.getInput().getKeyboard().isPressed(KEYCODES.KEY_F8)) {
            if (!f8WasDown) {
                f8WasDown = true;
                toggleDialog();
            }
        } else {
            f8WasDown = false;
        }
    }

    public void render(Renderer r, float ds) {
        // Render HUD Pill
        GCOLOR.UI().bg(true, false, hovered).render(r, pillBody);
        GCOLOR.UI().border().renderFrame(r, pillBody, 0, 1);

        CoopSessionManager sm = CoopSessionManager.getInstance();
        String text;
        COLOR textColor;

        if (sm.getRole() == Role.HOST) {
            text = "Co-Op [HOST] | Peers: " + sm.getConnectedPeerCount() + " [F8]";
            textColor = COLOR.WHITE100;
        } else if (sm.getRole() == Role.CLIENT) {
            text = "Co-Op [CLIENT] | Synced [F8]";
            textColor = COLOR.WHITE100;
        } else {
            text = "Co-Op [OFFLINE] [F8]";
            textColor = GCOLOR.T().INACTIVE;
        }

        textColor.bind();
        UI.FONT().S.render(r, text, pillBody.x1() + 10, pillBody.y1() + 5);
        COLOR.unbind();
    }

    public boolean hover(COORDINATE mCoo, boolean mouseHasMoved) {
        hovered = mCoo.isWithinRec(pillBody);
        return hovered;
    }

    public boolean mouseClick(MButt button) {
        if (button == MButt.LEFT && hovered) {
            toggleDialog();
            return true;
        }
        return false;
    }

    public void keyPush(KEYS key) {
        if (CoopOverlayDialog.getInstance().isActivated()) {
            if (KEYS.MAIN().ESCAPE.consumeClick()) {
                CoopOverlayDialog.getInstance().close();
            }
        }
    }

    public void toggleDialog() {
        CoopOverlayDialog.getInstance().toggle();
    }
}
