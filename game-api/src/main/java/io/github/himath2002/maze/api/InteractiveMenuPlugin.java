package io.github.himath2002.maze.api;

import io.github.himath2002.maze.api.callback.MenuCallback;

/**
 * Optional menu extension that temporarily captures navigation commands.
 */
public interface InteractiveMenuPlugin extends MenuCallback {
    boolean isInteractionActive();

    void handleCommand(InteractionCommand command);
}
