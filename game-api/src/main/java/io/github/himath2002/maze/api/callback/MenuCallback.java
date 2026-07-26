package io.github.himath2002.maze.api.callback;

/**
 * Contributes an action to the extension menu.
 */
public interface MenuCallback {
    String menuLabel();

    void activate();
}
