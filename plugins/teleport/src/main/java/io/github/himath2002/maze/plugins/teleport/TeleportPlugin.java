package io.github.himath2002.maze.plugins.teleport;

import io.github.himath2002.maze.api.GameAPI;
import io.github.himath2002.maze.api.InteractionCommand;
import io.github.himath2002.maze.api.InteractiveMenuPlugin;
import io.github.himath2002.maze.api.Plugin;
import io.github.himath2002.maze.api.model.Item;
import io.github.himath2002.maze.api.model.Obstacle;

import javax.swing.JOptionPane;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Provides one manual or automatically selected teleport per game.
 */
public final class TeleportPlugin implements Plugin, InteractiveMenuPlugin {
    public static final String ID = "teleport";

    private GameAPI api;
    private boolean interactionActive;
    private boolean automaticSelection;
    private int selectedRow;
    private int selectedColumn;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void initialize(GameAPI api) {
        this.api = api;
        selectedRow = api.getPlayerRowIdx();
        selectedColumn = api.getPlayerColIdx();
    }

    @Override
    public String menuLabel() {
        return api.getMessages().getString("teleportButton");
    }

    @Override
    public void activate() {
        if (api.hasTeleported()) {
            JOptionPane.showMessageDialog(
                null,
                "The one-time teleport has already been used.",
                "Teleport",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        Object[] choices = {"Choose a cell", "Select automatically"};
        int selection = JOptionPane.showOptionDialog(
            null,
            "Choose a teleport mode.",
            "Teleport",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            choices,
            choices[0]
        );

        if (selection == 0) {
            beginManualSelection();
        } else if (selection == 1) {
            teleportAutomatically();
        }
    }

    @Override
    public boolean isInteractionActive() {
        return interactionActive;
    }

    @Override
    public void handleCommand(InteractionCommand command) {
        if (!interactionActive) {
            return;
        }

        switch (command) {
            case UP -> moveSelection(-1, 0);
            case DOWN -> moveSelection(1, 0);
            case LEFT -> moveSelection(0, -1);
            case RIGHT -> moveSelection(0, 1);
            case CONFIRM -> performTeleport();
            case CANCEL -> cancelSelection();
        }
    }

    private void beginManualSelection() {
        interactionActive = true;
        automaticSelection = false;
        selectedRow = api.getPlayerRowIdx();
        selectedColumn = api.getPlayerColIdx();
        JOptionPane.showMessageDialog(null, api.getMessages().getString("teleportModeMessage"));
        api.notifyTeleportCursorUpdate(selectedRow, selectedColumn);
    }

    private void moveSelection(int rowDelta, int columnDelta) {
        int nextRow = selectedRow + rowDelta;
        int nextColumn = selectedColumn + columnDelta;
        if (!isWithinBounds(nextRow, nextColumn)) {
            return;
        }

        selectedRow = nextRow;
        selectedColumn = nextColumn;
        api.notifyTeleportCursorUpdate(selectedRow, selectedColumn);
    }

    private void performTeleport() {
        if (!interactionActive || api.hasTeleported()) {
            return;
        }

        Object content = api.getContentAt(selectedRow, selectedColumn);
        boolean destinationAllowed = canTraverse(content)
            && (automaticSelection || api.isVisible(selectedRow, selectedColumn));

        if (!destinationAllowed) {
            showBlockedDestination();
            return;
        }

        if (content instanceof Obstacle obstacle && Obstacle.PENALTY.equals(obstacle.getName())) {
            api.addPenaltyDay();
            api.setContentAt(selectedRow, selectedColumn, null);
        }

        api.setPlayerPosition(selectedRow, selectedColumn);
        revealLandingArea();

        if (content instanceof Item item) {
            api.setContentAt(selectedRow, selectedColumn, null);
            api.addItem(item.getName());
            JOptionPane.showMessageDialog(null, item.getMessage());
        }

        api.setTeleported(true);
        interactionActive = false;
        automaticSelection = false;
        api.notifyTeleportModeOff();
    }

    private void showBlockedDestination() {
        Object[] options = {"Choose again", "Cancel"};
        int selection = JOptionPane.showOptionDialog(
            null,
            "That cell is not an available teleport destination.",
            "Teleport blocked",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[0]
        );

        if (selection == 0) {
            api.notifyTeleportCursorUpdate(selectedRow, selectedColumn);
        } else {
            cancelSelection();
        }
    }

    private void cancelSelection() {
        interactionActive = false;
        automaticSelection = false;
        api.notifyTeleportModeOff();
    }

    private void teleportAutomatically() {
        List<int[]> candidates = new ArrayList<>();
        for (int row = 0; row < api.getNumRows(); row++) {
            for (int column = 0; column < api.getNumCols(); column++) {
                boolean currentPosition = row == api.getPlayerRowIdx()
                    && column == api.getPlayerColIdx();
                if (!currentPosition && canTraverse(api.getContentAt(row, column))) {
                    candidates.add(new int[]{row, column});
                }
            }
        }

        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(
                null,
                "No available teleport destination was found.",
                "Teleport",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        int[] destination = candidates.get(
            ThreadLocalRandom.current().nextInt(candidates.size())
        );
        selectedRow = destination[0];
        selectedColumn = destination[1];
        automaticSelection = true;
        interactionActive = true;
        performTeleport();
    }

    private boolean canTraverse(Object content) {
        if (!(content instanceof Obstacle obstacle) || api.hasGoldenPrizeKey()) {
            return true;
        }

        for (String requirement : obstacle.getRequiredItems()) {
            String normalizedRequirement = normalize(requirement);
            boolean owned = api.getInventory().stream()
                .map(TeleportPlugin::normalize)
                .anyMatch(normalizedRequirement::equals);
            if (!owned) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC);
    }

    private void revealLandingArea() {
        setVisibleIfValid(selectedRow, selectedColumn);
        setVisibleIfValid(selectedRow - 1, selectedColumn);
        setVisibleIfValid(selectedRow + 1, selectedColumn);
        setVisibleIfValid(selectedRow, selectedColumn - 1);
        setVisibleIfValid(selectedRow, selectedColumn + 1);
    }

    private void setVisibleIfValid(int row, int column) {
        if (isWithinBounds(row, column)) {
            api.setVisible(row, column, true);
        }
    }

    private boolean isWithinBounds(int row, int column) {
        return row >= 0
            && row < api.getNumRows()
            && column >= 0
            && column < api.getNumCols();
    }
}
