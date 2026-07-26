package io.github.himath2002.maze.api.callback;

/**
 * Receives successful player movement events.
 */
@FunctionalInterface
public interface MoveCallback {
    void onPlayerMoved();
}
