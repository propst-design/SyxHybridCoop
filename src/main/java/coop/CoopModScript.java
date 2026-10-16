package coop;

import java.io.IOException;

import coop.net.CoopSessionManager;
import init.paths.PATHS;
import script.SCRIPT;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

/**
 * Main entry point for the Songs of Syx Hybrid Co-Op mod.
 * Loaded dynamically by script.ScriptLoad.
 */
public class CoopModScript implements SCRIPT {

    private static final String MOD_NAME = "Syx Hybrid Co-Op";
    private static final String MOD_DESC = "Shared-City lockstep co-op and Shared-World multiplayer for Songs of Syx V71.";

    @Override
    public CharSequence name() {
        return MOD_NAME;
    }

    @Override
    public CharSequence desc() {
        return MOD_DESC;
    }

    @Override
    public boolean forceInit() {
        // Automatically injects into all new games and save loads
        return true;
    }

    @Override
    public boolean isSelectable() {
        return true;
    }

    @Override
    public void initBeforeGameCreated() {
        System.out.println("[CoopModScript] Initializing Syx Hybrid Co-Op mod...");

        // If custom save folder is specified (e.g. saves_client for dual testing), isolate save storage
        String customSaveFolder = System.getProperty("coop.saveFolder");
        if (customSaveFolder != null && !customSaveFolder.trim().isEmpty()) {
            System.out.println("[CoopModScript] Setting custom save folder to: " + customSaveFolder);
            PATHS.local().setCustomSaveFolder(customSaveFolder.trim());
        }

        // Initialize session manager, TCP transport, or Steam P2P transport
        CoopSessionManager.getInstance().initFromSystemProperties();
    }

    @Override
    public void initBeforeGameInited() {
        // Called after game instance is created but before full initialization
    }

    @Override
    public SCRIPT_INSTANCE createInstance() {
        return new CoopModScriptInstance();
    }

    public static class CoopModScriptInstance implements SCRIPT_INSTANCE {

        private final coop.ui.CoopHudOverlay hud = new coop.ui.CoopHudOverlay();

        @Override
        public void update(double ds) {
            CoopSessionManager.getInstance().update(ds);
            hud.update(ds);
        }

        @Override
        public void render(snake2d.Renderer r, float ds) {
            hud.render(r, ds);
        }

        @Override
        public void hover(snake2d.util.datatypes.COORDINATE mCoo, boolean mouseHasMoved) {
            hud.hover(mCoo, mouseHasMoved);
        }

        @Override
        public void mouseClick(snake2d.MButt button) {
            hud.mouseClick(button);
        }

        @Override
        public void keyPush(view.keyboard.KEYS key) {
            hud.keyPush(key);
        }

        @Override
        public void save(FilePutter file) {
            // Write multiplayer session metadata into save block
            file.chars(CoopSessionManager.getInstance().getRole().name());
            file.chars(CoopSessionManager.getInstance().getSessionMode().name());
            coop.world.CoopWorldRegistry.save(file);
        }

        @Override
        public void load(FileGetter file) throws IOException {
            // Restore multiplayer metadata from save file
            try {
                String roleStr = file.chars();
                String modeStr = file.chars();
                // Roles are dynamically reassigned in lobby, but mode is persistent
                if (modeStr != null && !modeStr.isEmpty()) {
                    try {
                        CoopSessionManager.getInstance().setSessionMode(CoopSessionManager.SessionMode.valueOf(modeStr));
                    } catch (IllegalArgumentException ignored) {}
                }
                coop.world.CoopWorldRegistry.load(file);
            } catch (Exception ignored) {
                // Backward compatibility with saves that do not have coop metadata
            }
        }
    }
}
