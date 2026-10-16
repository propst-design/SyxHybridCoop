package menu;

import java.io.IOException;
import java.nio.file.Path;

import coop.net.CoopSessionManager;
import coop.net.CoopSessionManager.SessionMode;
import coop.net.SaveStreamer;
import init.constant.C;
import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.misc.ACTION;
import snake2d.util.sprite.text.StringInputSprite;
import util.colors.GCOLOR;
import util.gui.misc.GInput;
import view.menu.MenuScreen;
import view.menu.MenuScreen.ScreenButton;

/**
 * Main menu screen for hosting and joining multiplayer sessions.
 * Accessible from ScMain ("Multiplayer").
 */
public class ScMultiplayer implements SC {

    private final Menu menu;
    private final GuiSection current;

    private SessionMode selectedMode = SessionMode.MODE_A_SHARED_CITY;
    private String statusMessage = "Ready";

    private final StringInputSprite hostPortInput;
    private final StringInputSprite clientIpInput;
    private final StringInputSprite clientPortInput;

    public ScMultiplayer(final Menu menu) {
        this.menu = menu;

        GuiSection main = new GUI.Shadower();

        MenuScreen screen = new MenuScreen("MULTIPLAYER CO-OP", GUI.labelColor) {
            @Override
            protected void back() {
                menu.switchScreen(menu.main);
            }
        };

        main.add(screen);

        int leftX = MenuScreen.inner.x1() + 60;
        int rightX = MenuScreen.inner.x1() + 620;
        int topY = MenuScreen.inner.y1() + 40;

        // ==========================================
        // LEFT COLUMN: HOST A MULTIPLAYER GAME
        // ==========================================
        GuiSection hostCol = new GuiSection();
        hostCol.body().moveX1(leftX);
        hostCol.body().moveY1(topY);

        RENDEROBJ hostTitle = new RENDEROBJ.RenderImp(480, 36) {
            @Override
            public void render(SPRITE_RENDERER r, float ds) {
                GUI.COLORS.copper.bind();
                UI.FONT().H2.render(r, "HOST SESSION", body().x1(), body().y1());
                COLOR.unbind();
            }
        };
        hostCol.addDown(0, hostTitle);

        RENDEROBJ modeLabel = new RENDEROBJ.RenderImp(480, 26) {
            @Override
            public void render(SPRITE_RENDERER r, float ds) {
                GUI.COLORS.label.bind();
                UI.FONT().M.render(r, "Multiplayer Mode:", body().x1(), body().y1());
                COLOR.unbind();
            }
        };
        hostCol.addDown(12, modeLabel);

        final ScreenButton modeButt = new ScreenButton("Mode: Mode A (Shared-City Lockstep)", UI.FONT().M) {
            @Override
            protected void clickA() {
                if (selectedMode == SessionMode.MODE_A_SHARED_CITY) {
                    selectedMode = SessionMode.MODE_B_SHARED_WORLD;
                    clickActionSet(null);
                } else {
                    selectedMode = SessionMode.MODE_A_SHARED_CITY;
                }
            }
        };
        modeButt.clickActionSet(new ACTION() {
            @Override
            public void exe() {
                if (selectedMode == SessionMode.MODE_A_SHARED_CITY) {
                    selectedMode = SessionMode.MODE_B_SHARED_WORLD;
                } else {
                    selectedMode = SessionMode.MODE_A_SHARED_CITY;
                }
            }
        });
        hostCol.addDown(6, modeButt);

        RENDEROBJ portLabel = new RENDEROBJ.RenderImp(480, 26) {
            @Override
            public void render(SPRITE_RENDERER r, float ds) {
                GUI.COLORS.label.bind();
                UI.FONT().M.render(r, "Hosting TCP Port:", body().x1(), body().y1());
                COLOR.unbind();
            }
        };
        hostCol.addDown(16, portLabel);

        hostPortInput = new StringInputSprite(8, UI.FONT().M);
        hostPortInput.text().add("27015");
        GInput hostPortGInput = new GInput(hostPortInput);
        hostCol.addDown(4, hostPortGInput);

        // Host Save Button
        ScreenButton hostSaveButt = new ScreenButton("Host from Save", UI.FONT().H2);
        hostSaveButt.clickActionSet(new ACTION() {
            @Override
            public void exe() {
                int port = parsePort(hostPortInput.text().toString(), 27015);
                try {
                    CoopSessionManager.getInstance().startHost(port, selectedMode);
                    statusMessage = "Host started on port " + port + ". Select a save to launch.";
                    if (menu.load.hasSaves()) {
                        menu.load.loadSave();
                    } else {
                        statusMessage = "No existing saves found. Please host a new game.";
                    }
                } catch (IOException e) {
                    statusMessage = "Failed to start host: " + e.getMessage();
                }
            }
        });
        hostCol.addDown(24, hostSaveButt);

        // Host New World Button
        ScreenButton hostNewButt = new ScreenButton("Host New World", UI.FONT().H2);
        hostNewButt.clickActionSet(new ACTION() {
            @Override
            public void exe() {
                int port = parsePort(hostPortInput.text().toString(), 27015);
                try {
                    CoopSessionManager.getInstance().startHost(port, selectedMode);
                    statusMessage = "Host started on port " + port + ". Configuring new world...";
                    menu.switchScreen(menu.sandboxSettings);
                } catch (IOException e) {
                    statusMessage = "Failed to start host: " + e.getMessage();
                }
            }
        });
        hostCol.addDown(12, hostNewButt);

        screen.add(hostCol);

        // ==========================================
        // RIGHT COLUMN: JOIN A MULTIPLAYER GAME
        // ==========================================
        GuiSection joinCol = new GuiSection();
        joinCol.body().moveX1(rightX);
        joinCol.body().moveY1(topY);

        RENDEROBJ joinTitle = new RENDEROBJ.RenderImp(480, 36) {
            @Override
            public void render(SPRITE_RENDERER r, float ds) {
                GUI.COLORS.copper.bind();
                UI.FONT().H2.render(r, "JOIN SESSION", body().x1(), body().y1());
                COLOR.unbind();
            }
        };
        joinCol.addDown(0, joinTitle);

        RENDEROBJ ipLabel = new RENDEROBJ.RenderImp(480, 26) {
            @Override
            public void render(SPRITE_RENDERER r, float ds) {
                GUI.COLORS.label.bind();
                UI.FONT().M.render(r, "Server IP Address:", body().x1(), body().y1());
                COLOR.unbind();
            }
        };
        joinCol.addDown(12, ipLabel);

        clientIpInput = new StringInputSprite(32, UI.FONT().M);
        clientIpInput.text().add("127.0.0.1");
        GInput clientIpGInput = new GInput(clientIpInput);
        joinCol.addDown(4, clientIpGInput);

        RENDEROBJ clientPortLabel = new RENDEROBJ.RenderImp(480, 26) {
            @Override
            public void render(SPRITE_RENDERER r, float ds) {
                GUI.COLORS.label.bind();
                UI.FONT().M.render(r, "Server Port:", body().x1(), body().y1());
                COLOR.unbind();
            }
        };
        joinCol.addDown(16, clientPortLabel);

        clientPortInput = new StringInputSprite(8, UI.FONT().M);
        clientPortInput.text().add("27015");
        GInput clientPortGInput = new GInput(clientPortInput);
        joinCol.addDown(4, clientPortGInput);

        // Connect & Join Button
        ScreenButton connectButt = new ScreenButton("Connect & Join", UI.FONT().H2);
        connectButt.clickActionSet(new ACTION() {
            @Override
            public void exe() {
                final String host = clientIpInput.text().toString().trim();
                final int port = parsePort(clientPortInput.text().toString(), 27015);
                statusMessage = "Connecting to " + host + ":" + port + "...";

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            CoopSessionManager.getInstance().connectClient(host, port, new SaveStreamer.SaveTransferCallback() {
                                @Override
                                public void onSaveReceived(Path savedFile) {
                                    statusMessage = "Save received! Launching game...";
                                    new game.save.GameLoader(savedFile).set();
                                }

                                @Override
                                public void onError(String message, Throwable cause) {
                                    statusMessage = "Sync error: " + message;
                                }
                            });
                            statusMessage = "Connected! Awaiting settlement save from host...";
                        } catch (Exception e) {
                            statusMessage = "Connection failed: " + e.getMessage();
                        }
                    }
                }).start();
            }
        });
        joinCol.addDown(24, connectButt);

        // Status Label
        RENDEROBJ statusLabel = new RENDEROBJ.RenderImp(480, 32) {
            @Override
            public void render(SPRITE_RENDERER r, float ds) {
                GCOLOR.T().CLICKABLE.bind();
                UI.FONT().S.render(r, "Status: " + statusMessage, body().x1(), body().y1());
                COLOR.unbind();
            }
        };
        joinCol.addDown(20, statusLabel);

        screen.add(joinCol);

        current = main;
    }

    private static int parsePort(String text, int defaultPort) {
        if (text == null || text.trim().isEmpty()) {
            return defaultPort;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }

    @Override
    public boolean hover(COORDINATE mCoo) {
        return current.hover(mCoo);
    }

    @Override
    public boolean click() {
        return current.click();
    }

    @Override
    public void render(SPRITE_RENDERER r, float ds) {
        current.render(r, ds);
    }

    @Override
    public boolean back(Menu menu) {
        menu.switchScreen(menu.main);
        return true;
    }
}
