package io.github.himath2002.maze.api.callback;

/**
 * Receives collectible acquisition events.
 */
@FunctionalInterface
public interface ItemCallback {
    void onItemAcquired(String itemName);
}
