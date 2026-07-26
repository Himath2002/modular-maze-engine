package io.github.himath2002.maze.plugins.penalty;

import io.github.himath2002.maze.api.GameAPI;
import io.github.himath2002.maze.api.Plugin;
import io.github.himath2002.maze.api.callback.MoveCallback;
import io.github.himath2002.maze.api.model.Obstacle;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds a temporary nearby obstacle after five seconds without movement.
 */
public final class PenaltyPlugin implements Plugin, MoveCallback {
    public static final String ID = "penalty";

    private static final int TIMEOUT_MILLIS = 5_000;
    private static final int TICK_MILLIS = 1_000;
    private static final int COUNTDOWN_START = TIMEOUT_MILLIS / TICK_MILLIS;
    private static final int[][] ADJACENT_OFFSETS = {
        {0, 1}, {1, 0}, {0, -1}, {-1, 0}
    };

    private GameAPI api;
    private long lastMoveTime;
    private Timer countdownTimer;
    private boolean penaltyApplied;
    private boolean movementStarted;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void initialize(GameAPI api) {
        this.api = api;
        lastMoveTime = api.getLastMoveTime();
    }

    @Override
    public void onPlayerMoved() {
        movementStarted = true;
        penaltyApplied = false;
        lastMoveTime = System.currentTimeMillis();
        restartCountdown();
    }

    @Override
    public void shutdown() {
        stopCountdown();
        if (api != null) {
            api.notifyCountdownUpdate(-1);
        }
    }

    private void restartCountdown() {
        stopCountdown();
        countdownTimer = new Timer(TICK_MILLIS, event -> updateCountdown());
        countdownTimer.setInitialDelay(TICK_MILLIS);
        countdownTimer.setRepeats(true);
        countdownTimer.start();
    }

    private void updateCountdown() {
        if (!movementStarted) {
            return;
        }

        long elapsed = System.currentTimeMillis() - lastMoveTime;
        if (elapsed >= TIMEOUT_MILLIS && !penaltyApplied) {
            applyPenalty();
            penaltyApplied = true;
            stopCountdown();
            return;
        }

        int remainingSeconds = Math.max(
            0,
            COUNTDOWN_START - (int) (elapsed / TICK_MILLIS)
        );
        api.notifyCountdownUpdate(remainingSeconds);
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    private void applyPenalty() {
        int playerRow = api.getPlayerRowIdx();
        int playerColumn = api.getPlayerColIdx();
        List<int[]> availablePositions = new ArrayList<>();

        for (int[] offset : ADJACENT_OFFSETS) {
            int row = playerRow + offset[0];
            int column = playerColumn + offset[1];
            boolean inBounds = row >= 0
                && row < api.getNumRows()
                && column >= 0
                && column < api.getNumCols();
            boolean isGoal = row == api.getGoalRowIdx() && column == api.getGoalColIdx();

            if (inBounds && !isGoal && api.getContentAt(row, column) == null) {
                availablePositions.add(new int[]{row, column});
            }
        }

        if (availablePositions.isEmpty()) {
            api.notifyCountdownUpdate(-1);
            return;
        }

        int[] position = availablePositions.get(0);
        Obstacle obstacle = new Obstacle(List.of());
        obstacle.setName(Obstacle.PENALTY);
        obstacle.setPositions(List.of(position));
        api.setContentAt(position[0], position[1], obstacle);
        api.notifyObstacleAdded();
    }
}
