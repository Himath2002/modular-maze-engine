package io.github.himath2002.maze.plugins.reveal;

import io.github.himath2002.maze.api.GameAPI;
import io.github.himath2002.maze.api.Plugin;
import io.github.himath2002.maze.api.callback.ItemCallback;

import javax.swing.JOptionPane;
import java.util.Locale;

/**
 * Reveals the goal and remaining collectibles when a map item is acquired.
 */
public final class RevealPlugin implements Plugin, ItemCallback {
    public static final String ID = "reveal";

    private GameAPI api;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void initialize(GameAPI api) {
        this.api = api;
    }

    @Override
    public void onItemAcquired(String itemName) {
        if (!itemName.toLowerCase(Locale.ROOT).contains("map")) {
            return;
        }

        api.revealGoal();
        api.revealAllItems();
        api.notifyRevealActivated();
        JOptionPane.showMessageDialog(
            null,
            "Map acquired — goal and collectibles revealed.",
            "Reveal",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
}
