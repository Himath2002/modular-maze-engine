package io.github.himath2002.maze.app;

import io.github.himath2002.maze.engine.MazeGame;
import io.github.himath2002.maze.ui.MazeWindow;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Desktop entry point for the modular maze engine.
 */
public final class MazeApplication {
    private static final Logger LOGGER = Logger.getLogger(MazeApplication.class.getName());
    private static final String DEFAULT_MAP = "maps/coastal-treasure.utf8.map";
    private static final String USAGE = """
        Usage: game-core [map-path]

        With no map path, the bundled Coastal Treasure scenario is loaded.
        """;

    private MazeApplication() {
    }

    @SuppressWarnings("PMD.SystemPrintln")
    public static void main(String[] args) {
        if (args.length == 1
            && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            System.out.print(USAGE);
            return;
        }
        if (args.length > 1) {
            showStartupError("Expected zero or one map path.\n\n" + USAGE);
            return;
        }

        String mapLocation = args.length == 1 ? args[0] : DEFAULT_MAP;
        MazeGame game = new MazeGame();

        try {
            game.readMapFile(mapLocation);
            SwingUtilities.invokeLater(() -> new MazeWindow(game));
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "Maze startup failed", ex);
            showStartupError(ex.getMessage());
        }
    }

    private static void showStartupError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
            null,
            "Unable to start Modular Maze Engine: " + message,
            "Startup error",
            JOptionPane.ERROR_MESSAGE
        ));
    }
}
