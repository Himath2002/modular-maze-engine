package io.github.himath2002.maze.persistence;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * Complete serializable state required to resume a maze session.
 */
public record GameSnapshot(
    int playerRow,
    int playerColumn,
    int goalRow,
    int goalColumn,
    Object[][] cells,
    boolean[][] visible,
    List<String> inventory,
    String latestItem,
    int dayCount,
    LocalDate currentDate,
    boolean ownsGoldenKey,
    boolean teleported
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
