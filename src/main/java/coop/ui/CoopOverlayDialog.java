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
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.panel.GPanel;
import view.interrupter.Interrupter;
import view.keyboard.KEYS;
import view.main.VIEW;

/**
 * In-game modal overlay for inspecting and managing the multiplayer co-op session.
 * Managed as a top-level Interrupter so it renders above all settlement/world layers.
 */
public class CoopOverlayDialog extends Interrupter {

    private static CoopOverlayDialog instance;

    private final GuiSection window;
    private final GPanel panel;
    private final GuiSection content;

    private String notificationText = "";
    private double notificationTimer = 0;

    public static synchronized CoopOverlayDialog getInstance() {
        if (instance == null) {
            instance = new CoopOverlayDialog();
        }
        return instance;
    }

    private CoopOverlayDialog() {
        super(false, false, true);

        window = new GuiSection();

        panel = new GPanel();
        panel.setTitle("CO-OP SESSION CONTROLS", UI.FONT().H2);
        panel.body().setDim(480, 360);
        panel.setCloseAction(new ACTION() {
            @Override
            public void exe() {
                hide();
            }
        });

        content = new GuiSection();

        // 1. Status Information Rows
        RENDEROBJ infoRow = new RENDEROBJ.RenderImp(430, 110) {
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

        // 2. Host: Force Save Resync Button
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

        // 3. Host: Toggle Mode A / Mode B
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

        // 4. Bottom Row: Disconnect & Close Buttons
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
                hide();
            }
        });
        bottomRow.addRight(16, closeButt);

        content.addDown(16, bottomRow);

        // 5. Notification Message Label
        RENDEROBJ notifLabel = new RENDEROBJ.RenderImp(430, 24) {
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

    private void realign() {
        panel.body().centerIn(C.DIM());
        content.body().moveX1Y1(panel.body().x1() + 24, panel.body().y1() + 36);
    }

    public void show() {
        if (VIEW.inters() == null || VIEW.inters().manager == null) {
            return;
        }
        realign();
        super.show(VIEW.inters().manager);
    }

    public void toggle() {
        if (isActivated()) {
            hide();
        } else {
            show();
        }
    }

    public void close() {
        hide();
    }

    public void showNotification(String text) {
        this.notificationText = text;
        this.notificationTimer = 4.0;
    }

    @Override
    protected boolean hover(COORDINATE mCoo, boolean mouseHasMoved) {
        return window.hover(mCoo) || mCoo.isWithinRec(panel.body());
    }

    @Override
    protected void mouseClick(MButt button) {
        if (button == MButt.LEFT) {
            window.click();
        } else if (button == MButt.RIGHT) {
            hide();
        }
    }

    @Override
    protected boolean otherClick(MButt button) {
        if (button == MButt.RIGHT) {
            hide();
            return true;
        }
        return false;
    }

    @Override
    protected void hoverTimer(GBox text) {
        window.hoverInfoGet(text);
    }

    @Override
    protected boolean render(Renderer r, float ds) {
        window.render(r, ds);
        return true;
    }

    @Override
    protected boolean update(float ds) {
        if (notificationTimer > 0) {
            notificationTimer -= ds;
        }
        if (KEYS.MAIN().ESCAPE.consumeClick()) {
            hide();
            return false;
        }
        return true;
    }
}
