package io.github.himath2002.maze.api.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Traversal barrier that may require inventory items.
 */
public final class Obstacle implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final String STANDARD = "standard";
    public static final String PENALTY = "penalty";

    private final List<String> requiredItems;
    private List<int[]> positions = new ArrayList<>();
    private String type = STANDARD;

    public Obstacle(List<String> requiredItems) {
        this.requiredItems = new ArrayList<>(Objects.requireNonNull(requiredItems, "requiredItems"));
    }

    public void setPositions(List<int[]> positions) {
        this.positions = new ArrayList<>(Objects.requireNonNull(positions, "positions"));
    }

    public List<int[]> getPositions() {
        return positions;
    }

    public List<String> getRequiredItems() {
        return List.copyOf(requiredItems);
    }

    public String getName() {
        return type;
    }

    public void setName(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public String toString() {
        return type;
    }
}
