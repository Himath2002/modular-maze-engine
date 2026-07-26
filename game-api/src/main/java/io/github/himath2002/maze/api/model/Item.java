package io.github.himath2002.maze.api.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Collectible map entity with one or more configured positions.
 */
public final class Item implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String message;
    private List<int[]> positions = new ArrayList<>();

    public Item(String name, String message) {
        this.name = Objects.requireNonNull(name, "name");
        this.message = Objects.requireNonNull(message, "message");
    }

    public void setPositions(List<int[]> positions) {
        this.positions = new ArrayList<>(Objects.requireNonNull(positions, "positions"));
    }

    public List<int[]> getPositions() {
        return positions;
    }

    public String getName() {
        return name;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return name;
    }
}
