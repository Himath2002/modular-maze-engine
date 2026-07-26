package io.github.himath2002.maze.api;

/**
 * Lifecycle implemented by an embedded map script.
 */
public interface GameScript {
    void initialize(GameAPI api);

    void onPlayerMoved();

    void onItemAcquired(String itemName);
}
