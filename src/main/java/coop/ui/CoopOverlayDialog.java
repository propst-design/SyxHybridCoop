package coop.ui;

import coop.cmd.CoopLockstepCoordinator;
import coop.net.CoopSessionManager;
import coop.net.CoopSessionManager.Role;
import coop.net.CoopSessionManager.SessionMode;
import init.constant.C;
import init.sprite.UI.UI;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.panel.GPanel;

/**
 * In-game modal overlay for inspecting and managing the multiplayer co-op session.
 * Toggled via the HUD pill or F8 shortcut.
 */
public class CoopOverlayDialog {

    private static final CoopOverlayDialog INSTANCE = new CoopOverlayDialog();

    private boolean visible = false;
    private final GuiSection window;
    private final GPanel panel;

    private String notificationText = "";
    private double notificationTimer = 0;

    private CoopOverlayDialog() {
        window = new GuiSection();

        panel = new GPanel();
        panel.setTitle("CO-OP SESSION CONTROLS");
        panel.body().setDim(440, 320);
        panel.body().centerIn(C.DIM());

        GuiSection content = new GuiSection();
        content.body().moveX1(panel.body().x1() + 24);
        content.body().moveY1(panel.body().y1() + 36);

        // Status Information Rows
        RENDEROBJ infoRow = new RENDEROBJ.RenderImp(400, 110) {
            @Override
            public void render(SPRITE_RENDERER r, float ds) {
                CoopSessionManager sm = CoopSessionManager.getInstance();
                int x = body().x1();
                int y = body().y1();

                GCOLOR.T().NORMAL.bind();
                UI.FONT().M.render(r, "Role: " + sm.getRole(), x, y);
                UI.FONT().M.render(r, "Mode: " + sm.getSessionMode(), x, y + 20);
                UI.FONT().M.render(r, "State: " + sm.getSessionState(), x, y + 40);
                UI.FONT().M.render(r, "Simulation Tick: " + CoopLockstepCoordinator.getCurrentTick(), x, y + 60);
                UI.FONT().M.render(r, "Peers Connected: " + sm.getConnectedPeerCount(), x, y + 80);
                COLOR.unbind();
            }
        };
        content.addDown(0, infoRow);

        // Host: Force Save Resync Button
        GButt.Panel resyncButt = new GButt.Panel("Force Save Resync (All Clients)") {
            @Override
            public boolean activeIs() {
                return CoopSessionManager.getInstance().isHost() && CoopSessionManager.getInstance().isConnected();
            }
        };
        resyncButt.clickActionSet(new ACTION() {
            @Override
            public void exe() {
                CoopSessionManager.getInstance().triggerHostSaveSync();
                showNotification("Save capture initiated and streaming to peers...");
            }
        });
        content.addDown(16, resyncButt);

        // Host: Toggle Mode A / Mode B
        GButt.Panel toggleModeButt = new GButt.Panel("Switch Mode A <-> Mode B") {
            @Override
            public boolean activeIs() {
                return CoopSessionManager.getInstance().isHost();
            }
        };
        toggleModeButt.clickActionSet(new ACTION() {
            @Override
            public void exe() {
                CoopSessionManager sm = CoopSessionManager.getInstance();
                if (sm.getSessionMode() == SessionMode.MODE_A_SHARED_CITY) {
                    sm.setSessionMode(SessionMode.MODE_B_SHARED_WORLD);
                } else {
                    sm.setSessionMode(SessionMode.MODE_A_SHARED_CITY);
                }
                showNotification("Switched to " + sm.getSessionMode());
            }
        });
        content.addDown(8, toggleModeButt);

        // Bottom Row: Disconnect & Close
        GuiSection bottomRow = new GuiSection();

        GButt.Panel disconnectButt = new GButt.Panel("Disconnect");
        disconnectButt.clickActionSet(new ACTION() {
            @Override
            public void exe() {
                CoopSessionManager.getInstance().close();
                showNotification("Session disconnected.");
            }
        });
        bottomRow.addRight(0, disconnectButt);

        GButt.Panel closeButt = new GButt.Panel("Close (Esc)");
        closeButt.clickActionSet(new ACTION() {
            @Override
            public void exe() {
                visible = false;
            }
        });
        bottomRow.addRight(16, closeButt);

        content.addDown(16, bottomRow);

        // Notification Message
        RENDEROBJ notifLabel = new RENDEROBJ.RenderImp(400, 24) {
            @Override
            public void render(SPRITE_RENDERER r, float ds) {
                if (notificationTimer > 0 && !notificationText.isEmpty()) {
                    GCOLOR.T().HOVERED.bind();
                    UI.FONT().S.render(r, notificationText, body().x1(), body().y1());
                    COLOR.unbind();
                }
            }
        };
        content.addDown(12, notifLabel);

        window.add(panel);
        window.add(content);
    }

    public static CoopOverlayDialog getInstance() {
        return INSTANCE;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void toggle() {
        this.visible = !this.visible;
    }

    public void showNotification(String text) {
        this.notificationText = text;
        this.notificationTimer = 4.0;
    }

    public void update(double ds) {
        if (notificationTimer > 0) {
            notificationTimer -= ds;
        }
    }

    public boolean hover(COORDINATE mCoo, boolean mouseHasMoved) {
        if (!visible) return false;
        return window.hover(mCoo) || mCoo.isWithinRec(panel.body());
    }

    public boolean mouseClick(MButt button) {
        if (!visible) return false;
        if (button == MButt.LEFT) {
            return window.click();
        } else if (button == MButt.RIGHT) {
            visible = false;
            return true;
        }
        return false;
    }

    public void render(Renderer r, float ds) {
        if (!visible) return;

        // Render modal window and widgets
        window.render(r, ds);
    }
}
