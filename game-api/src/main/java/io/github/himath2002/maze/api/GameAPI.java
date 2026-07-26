package io.github.himath2002.maze.api;

import java.util.List;
import java.util.ResourceBundle;

/**
 * Stable boundary exposed to plugins and embedded scripts.
 *
 * <p>The engine owns all mutable state. Implementations return defensive
 * collections where callers must not mutate internal data directly.</p>
 */
public interface GameAPI extends NotifiableAPI {
    int getNumRows();

    int getNumCols();

    int getPlayerRowIdx();

    int getPlayerColIdx();

    void setPlayerPosition(int row, int col);

    List<String> getInventory();

    String getLatestItem();

    void addItem(String item);

    Object getContentAt(int row, int col);

    void setContentAt(int row, int col, Object content);

    boolean isVisible(int row, int col);

    void setVisible(int row, int col, boolean visible);

    void revealGoal();

    void revealAllItems();

    boolean traversedObstacle();

    long getLastMoveTime();

    void setLastMoveTime(long time);

    int getGoalRowIdx();

    int getGoalColIdx();

    int getDayCount();

    void addPenaltyDay();

    void setPrizeProgress(int count);

    void setHasPrize(boolean enabled);

    boolean hasGoldenPrizeKey();

    void setOwnsGoldenKey(boolean owned);

    boolean hasTeleported();

    void setTeleported(boolean teleported);

    ResourceBundle getMessages();
}
