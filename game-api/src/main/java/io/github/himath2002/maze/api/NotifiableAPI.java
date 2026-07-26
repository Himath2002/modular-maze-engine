package io.github.himath2002.maze.api;

/**
 * Presentation notifications available to independently compiled extensions.
 */
public interface NotifiableAPI {
    void notifyCountdownUpdate(int seconds);

    void notifyObstacleAdded();

    void notifyTeleportCursorUpdate(int row, int col);

    void notifyTeleportModeOff();

    void notifyPenaltyDayAdded();

    void notifyRevealActivated();
}
