package io.github.himath2002.maze.api;

/**
 * Base contract for runtime-discovered engine extensions.
 */
public interface Plugin {
    /**
     * Stable identifier used by the engine and interface.
     */
    String id();

    /**
     * Supplies the restricted engine boundary after the map is initialized.
     */
    void initialize(GameAPI api);

    /**
     * Releases optional timers or other extension resources.
     */
    default void shutdown() {
        // Most plugins are stateless and need no shutdown work.
    }
}
