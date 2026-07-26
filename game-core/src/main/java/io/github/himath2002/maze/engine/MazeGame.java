package io.github.himath2002.maze.engine;

import io.github.himath2002.maze.api.GameAPI;
import io.github.himath2002.maze.api.Plugin;
import io.github.himath2002.maze.api.callback.ItemCallback;
import io.github.himath2002.maze.api.callback.MenuCallback;
import io.github.himath2002.maze.api.callback.MoveCallback;
import io.github.himath2002.maze.api.model.Item;
import io.github.himath2002.maze.api.model.Obstacle;
import io.github.himath2002.maze.i18n.Utf8ResourceControl;
import io.github.himath2002.maze.parser.MazeMapParser;
import io.github.himath2002.maze.parser.ParseException;
import io.github.himath2002.maze.persistence.GameSnapshot;
import io.github.himath2002.maze.scripting.ScriptManager;
import io.github.himath2002.maze.ui.MazeWindow;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Owns maze state, configuration loading, plugin discovery, and gameplay rules.
 */
public final class MazeGame implements GameAPI {
    private static final Logger LOGGER = Logger.getLogger(MazeGame.class.getName());
    private static final LocalDate INITIAL_DATE = LocalDate.of(2025, 10, 21);
    private static final String GOAL_MARKER = "goal";

    private final List<MoveCallback> moveCallbacks = new ArrayList<>();
    private final List<ItemCallback> itemCallbacks = new ArrayList<>();
    private final List<MenuCallback> menuCallbacks = new ArrayList<>();
    private final List<Plugin> activePlugins = new ArrayList<>();
    private final ScriptManager scriptManager = new ScriptManager(this);

    private int numRows;
    private int numColumns;
    private int playerRow;
    private int playerColumn;
    private int goalRow;
    private int goalColumn;
    private boolean[][] visible;
    private Object[][] cells;
    private List<String> inventory = new ArrayList<>();
    private String latestItem;
    private Locale locale = Locale.getDefault();
    private ResourceBundle messages;
    private LocalDate currentDate = INITIAL_DATE;
    private int dayCount;
    private boolean cheatEnabled;
    private long lastMoveTime;
    private boolean lastTraversedObstacle;
    private boolean hasPrize;
    private boolean ownsGoldenKey;
    private boolean teleported;
    private boolean gameOver;
    private boolean resourcesClosed;
    private MazeWindow window;

    public MazeGame() {
        updateMessages();
    }

    public void setWindow(MazeWindow window) {
        this.window = window;
        if (hasPrize) {
            window.updatePrizeProgress(0);
        }
    }

    /**
     * Loads a classpath map or a map file supplied by the user.
     *
     * @param location classpath resource name or filesystem path
     */
    public void readMapFile(String location) {
        Objects.requireNonNull(location, "location");
        resetRuntimeState();

        try (InputStream input = openMapStream(location);
             PushbackInputStream pushback = new PushbackInputStream(input, 4)) {
            Charset charset = detectEncoding(pushback, location);
            try (Reader reader = new BufferedReader(new InputStreamReader(pushback, charset))) {
                MazeMapParser parser = new MazeMapParser(reader);
                parser.parse(reader);
                initializeFrom(parser);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to open map '" + location + "'", ex);
        } catch (ParseException ex) {
            throw new IllegalStateException("Map syntax is invalid: " + ex.getMessage(), ex);
        }
    }

    private void initializeFrom(MazeMapParser parser) {
        if (parser.getStartX() == parser.getGoalX()
            && parser.getStartY() == parser.getGoalY()) {
            throw new IllegalStateException(messages.getString("startGoalConflict"));
        }

        numRows = parser.getWidth();
        numColumns = parser.getHeight();
        playerRow = parser.getStartX();
        playerColumn = parser.getStartY();
        goalRow = parser.getGoalX();
        goalColumn = parser.getGoalY();
        cells = new Object[numRows][numColumns];
        visible = new boolean[numRows][numColumns];
        cells[goalRow][goalColumn] = GOAL_MARKER;

        loadPlugins(parser.getPluginClasses());
        placeItems(parser.getItems());
        placeObstacles(parser.getObstacles());
        scriptManager.load(parser.getScriptCodes());

        revealAround(playerRow, playerColumn);
        refreshWindow();
    }

    private void resetRuntimeState() {
        shutdown();
        moveCallbacks.clear();
        itemCallbacks.clear();
        menuCallbacks.clear();
        activePlugins.clear();
        scriptManager.shutdown();
        inventory = new ArrayList<>();
        latestItem = null;
        currentDate = INITIAL_DATE;
        dayCount = 0;
        cheatEnabled = false;
        lastMoveTime = 0L;
        lastTraversedObstacle = false;
        hasPrize = false;
        ownsGoldenKey = false;
        teleported = false;
        gameOver = false;
        resourcesClosed = false;
    }

    private void loadPlugins(List<String> classNames) {
        for (String className : classNames) {
            try {
                Class<? extends Plugin> pluginType = Class
                    .forName(className)
                    .asSubclass(Plugin.class);
                Plugin plugin = pluginType.getDeclaredConstructor().newInstance();
                plugin.initialize(this);
                activePlugins.add(plugin);
                registerCallbacks(plugin);
                LOGGER.info(() -> "Loaded plugin " + plugin.id() + " (" + className + ")");
            } catch (ReflectiveOperationException | ClassCastException ex) {
                LOGGER.log(Level.SEVERE, "Unable to load plugin " + className, ex);
            }
        }
    }

    private void registerCallbacks(Plugin plugin) {
        if (plugin instanceof MoveCallback moveCallback) {
            moveCallbacks.add(moveCallback);
        }
        if (plugin instanceof ItemCallback itemCallback) {
            itemCallbacks.add(itemCallback);
        }
        if (plugin instanceof MenuCallback menuCallback) {
            menuCallbacks.add(menuCallback);
        }
    }

    private void placeItems(List<Item> items) {
        boolean revealAvailable = hasPlugin("reveal");
        for (Item item : items) {
            if (isMapItem(item) && !revealAvailable) {
                LOGGER.warning(() -> "Skipped map item because the reveal plugin is unavailable");
                continue;
            }
            for (int[] position : item.getPositions()) {
                placeEntity(position[0], position[1], item, "item " + item.getName());
            }
        }
    }

    private void placeObstacles(List<Obstacle> obstacles) {
        for (Obstacle obstacle : obstacles) {
            for (int[] position : obstacle.getPositions()) {
                placeEntity(position[0], position[1], obstacle, "obstacle");
            }
        }
    }

    private void placeEntity(int row, int column, Object entity, String description) {
        if (row == playerRow && column == playerColumn) {
            throw new IllegalStateException(description + " overlaps the player start at "
                + formatPosition(row, column));
        }
        if (cells[row][column] != null) {
            throw new IllegalStateException(description + " overlaps "
                + cells[row][column] + " at " + formatPosition(row, column));
        }
        cells[row][column] = entity;
    }

    /**
     * Attempts one cardinal movement.
     *
     * @return {@code true} when the player position changed
     */
    public boolean move(String direction) {
        if (gameOver) {
            return false;
        }

        int[] delta = movementDelta(direction);
        int nextRow = playerRow + delta[0];
        int nextColumn = playerColumn + delta[1];
        if (!isWithinBounds(nextRow, nextColumn)) {
            return false;
        }

        Object content = cells[nextRow][nextColumn];
        lastTraversedObstacle = false;
        if (content instanceof Obstacle obstacle
            && !crossObstacle(obstacle, nextRow, nextColumn)) {
            return false;
        }

        playerRow = nextRow;
        playerColumn = nextColumn;
        revealAround(playerRow, playerColumn);

        if (content instanceof Item item) {
            collectItem(item);
            cells[playerRow][playerColumn] = null;
        }

        advanceDay();
        lastMoveTime = System.currentTimeMillis();
        notifyMovement();

        if (playerRow == goalRow && playerColumn == goalColumn) {
            JOptionPane.showMessageDialog(
                window,
                messages.getString("gameEnd") + " " + dayCount + " "
                    + messages.getString("daysElapsed")
            );
            endGame();
        } else {
            refreshWindow();
        }
        return true;
    }

    private int[] movementDelta(String direction) {
        return switch (Objects.requireNonNull(direction, "direction").toLowerCase(Locale.ROOT)) {
            case "up" -> new int[]{-1, 0};
            case "down" -> new int[]{1, 0};
            case "left" -> new int[]{0, -1};
            case "right" -> new int[]{0, 1};
            default -> throw new IllegalArgumentException("Unsupported direction: " + direction);
        };
    }

    private boolean crossObstacle(Obstacle obstacle, int row, int column) {
        List<String> missingItems = obstacle.getRequiredItems().stream()
            .filter(requirement -> !ownsItem(requirement))
            .toList();
        if (!ownsGoldenKey && !missingItems.isEmpty()) {
            JOptionPane.showMessageDialog(
                window,
                messages.getString("requiredItem") + ": " + String.join(", ", missingItems)
            );
            return false;
        }

        if (Obstacle.PENALTY.equals(obstacle.getName())) {
            addPenaltyDay();
            cells[row][column] = null;
        } else {
            lastTraversedObstacle = true;
        }
        return true;
    }

    private boolean ownsItem(String requirement) {
        String normalizedRequirement = normalize(requirement);
        return inventory.stream()
            .map(MazeGame::normalize)
            .anyMatch(normalizedRequirement::equals);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC);
    }

    private void collectItem(Item item) {
        inventory.add(item.getName());
        latestItem = item.getName();
        JOptionPane.showMessageDialog(window, item.getMessage());
        notifyItemAcquired(item.getName());
    }

    private void notifyItemAcquired(String itemName) {
        for (ItemCallback callback : itemCallbacks) {
            callback.onItemAcquired(itemName);
        }
        scriptManager.onItemAcquired(itemName);
    }

    private void notifyMovement() {
        for (MoveCallback callback : moveCallbacks) {
            callback.onPlayerMoved();
        }
        scriptManager.onPlayerMoved();
    }

    private void advanceDay() {
        dayCount++;
        currentDate = currentDate.plusDays(1);
    }

    private void endGame() {
        if (gameOver) {
            return;
        }
        gameOver = true;
        notifyCountdownUpdate(-1);
        shutdown();

        SwingUtilities.invokeLater(() -> {
            if (window != null) {
                window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
            }
        });
    }

    public void shutdown() {
        if (resourcesClosed) {
            return;
        }
        resourcesClosed = true;
        scriptManager.shutdown();
        for (Plugin plugin : activePlugins) {
            try {
                plugin.shutdown();
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Plugin shutdown failed: " + plugin.id(), ex);
            }
        }
    }

    public void changeLocale(String languageTag) {
        Locale selectedLocale = Locale.forLanguageTag(languageTag);
        if (selectedLocale.getLanguage().isBlank()) {
            throw new IllegalArgumentException("Invalid language tag: " + languageTag);
        }
        locale = selectedLocale;
        updateMessages();
        if (window != null) {
            window.updateUiText();
        }
    }

    public Locale getLocale() {
        return locale;
    }

    public String getLocalizedDate() {
        DateTimeFormatter formatter = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.FULL)
            .withLocale(locale);
        return currentDate.format(formatter);
    }

    public boolean hasPlugin(String pluginId) {
        return activePlugins.stream().anyMatch(plugin -> plugin.id().equals(pluginId));
    }

    public boolean hasPrize() {
        return hasPrize;
    }

    public boolean isCheatEnabled() {
        return cheatEnabled;
    }

    public void toggleCheat() {
        cheatEnabled = !cheatEnabled;
    }

    public List<MenuCallback> getMenuCallbacks() {
        return List.copyOf(menuCallbacks);
    }

    public void saveGame(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (ObjectOutputStream output = new ObjectOutputStream(
                Files.newOutputStream(path)
            )) {
                output.writeObject(createSnapshot());
            }
            JOptionPane.showMessageDialog(
                window,
                messages.getString("saveSuccess"),
                "Save",
                JOptionPane.INFORMATION_MESSAGE
            );
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Unable to save game to " + path, ex);
            JOptionPane.showMessageDialog(
                window,
                messages.getString("saveFailed"),
                "Save error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    public void loadGame(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            JOptionPane.showMessageDialog(
                window,
                messages.getString("noSaveFile"),
                "Load error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        try (ObjectInputStream input = new ObjectInputStream(
            Files.newInputStream(path)
        )) {
            Object value = input.readObject();
            if (!(value instanceof GameSnapshot snapshot)) {
                throw new IOException("Unsupported save format");
            }
            restoreSnapshot(snapshot);
            refreshWindow();
            JOptionPane.showMessageDialog(
                window,
                messages.getString("loadSuccess"),
                "Load",
                JOptionPane.INFORMATION_MESSAGE
            );
        } catch (IOException | ClassNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "Unable to load game from " + path, ex);
            JOptionPane.showMessageDialog(
                window,
                messages.getString("loadFailed"),
                "Load error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private GameSnapshot createSnapshot() {
        return new GameSnapshot(
            playerRow,
            playerColumn,
            goalRow,
            goalColumn,
            cells,
            visible,
            List.copyOf(inventory),
            latestItem,
            dayCount,
            currentDate,
            ownsGoldenKey,
            teleported
        );
    }

    private void restoreSnapshot(GameSnapshot snapshot) throws IOException {
        validateSnapshot(snapshot);
        playerRow = snapshot.playerRow();
        playerColumn = snapshot.playerColumn();
        goalRow = snapshot.goalRow();
        goalColumn = snapshot.goalColumn();
        cells = snapshot.cells();
        visible = snapshot.visible();
        inventory = new ArrayList<>(snapshot.inventory());
        latestItem = snapshot.latestItem();
        dayCount = snapshot.dayCount();
        currentDate = snapshot.currentDate();
        ownsGoldenKey = snapshot.ownsGoldenKey();
        teleported = snapshot.teleported();
        gameOver = false;
        revealAround(playerRow, playerColumn);
    }

    private void validateSnapshot(GameSnapshot snapshot) throws IOException {
        if (snapshot.cells() == null
            || snapshot.visible() == null
            || snapshot.cells().length != numRows
            || snapshot.visible().length != numRows) {
            throw new IOException("Save file belongs to a different map");
        }
        for (int row = 0; row < numRows; row++) {
            if (snapshot.cells()[row].length != numColumns
                || snapshot.visible()[row].length != numColumns) {
                throw new IOException("Save file dimensions do not match the active map");
            }
        }
    }

    private void refreshWindow() {
        if (window != null) {
            window.refreshState();
        }
    }

    private void updateMessages() {
        try {
            messages = ResourceBundle.getBundle(
                "messages",
                locale,
                new Utf8ResourceControl()
            );
        } catch (MissingResourceException ex) {
            messages = ResourceBundle.getBundle(
                "messages",
                Locale.ENGLISH,
                new Utf8ResourceControl()
            );
        }
    }

    private InputStream openMapStream(String location) throws IOException {
        String resourceName = location.startsWith("/") ? location.substring(1) : location;
        InputStream resource = Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream(resourceName);
        if (resource != null) {
            return resource;
        }
        return Files.newInputStream(Path.of(location));
    }

    private Charset detectEncoding(PushbackInputStream input, String name) throws IOException {
        byte[] bom = new byte[4];
        int count = input.read(bom, 0, bom.length);
        int skip = 0;
        Charset charset = null;

        if (startsWith(bom, count, 0xFF, 0xFE, 0x00, 0x00)) {
            charset = Charset.forName("UTF-32LE");
            skip = 4;
        } else if (startsWith(bom, count, 0x00, 0x00, 0xFE, 0xFF)) {
            charset = Charset.forName("UTF-32BE");
            skip = 4;
        } else if (startsWith(bom, count, 0xFF, 0xFE)) {
            charset = StandardCharsets.UTF_16LE;
            skip = 2;
        } else if (startsWith(bom, count, 0xFE, 0xFF)) {
            charset = StandardCharsets.UTF_16BE;
            skip = 2;
        } else if (startsWith(bom, count, 0xEF, 0xBB, 0xBF)) {
            charset = StandardCharsets.UTF_8;
            skip = 3;
        }

        if (count > 0) {
            input.unread(bom, skip, count - skip);
        }
        return charset == null ? charsetFromExtension(name) : charset;
    }

    private boolean startsWith(byte[] source, int count, int... signature) {
        if (count < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (Byte.toUnsignedInt(source[index]) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private Charset charsetFromExtension(String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".utf16.map")) {
            return StandardCharsets.UTF_16;
        }
        if (lowerName.endsWith(".utf32.map")) {
            try {
                return Charset.forName("UTF-32LE");
            } catch (UnsupportedCharsetException | IllegalCharsetNameException ex) {
                LOGGER.log(Level.WARNING, "UTF-32 is unavailable; using UTF-8", ex);
            }
        }
        return StandardCharsets.UTF_8;
    }

    private boolean isWithinBounds(int row, int column) {
        return row >= 0 && row < numRows && column >= 0 && column < numColumns;
    }

    private void revealAround(int row, int column) {
        setVisibleIfValid(row, column);
        setVisibleIfValid(row - 1, column);
        setVisibleIfValid(row + 1, column);
        setVisibleIfValid(row, column - 1);
        setVisibleIfValid(row, column + 1);
    }

    private void setVisibleIfValid(int row, int column) {
        if (isWithinBounds(row, column)) {
            visible[row][column] = true;
        }
    }

    private static boolean isMapItem(Item item) {
        return item.getName().toLowerCase(Locale.ROOT).contains("map");
    }

    private static String formatPosition(int row, int column) {
        return "(" + row + ", " + column + ")";
    }

    @Override
    public int getNumRows() {
        return numRows;
    }

    @Override
    public int getNumCols() {
        return numColumns;
    }

    @Override
    public int getPlayerRowIdx() {
        return playerRow;
    }

    @Override
    public int getPlayerColIdx() {
        return playerColumn;
    }

    @Override
    public void setPlayerPosition(int row, int column) {
        if (!isWithinBounds(row, column)) {
            throw new IllegalArgumentException("Player position is outside the map");
        }
        playerRow = row;
        playerColumn = column;
    }

    @Override
    public List<String> getInventory() {
        return List.copyOf(inventory);
    }

    @Override
    public String getLatestItem() {
        return latestItem;
    }

    @Override
    public void addItem(String itemName) {
        inventory.add(itemName);
        latestItem = itemName;
        if ("Golden Prize Key".equals(itemName)) {
            ownsGoldenKey = true;
            JOptionPane.showMessageDialog(
                window,
                messages.getString("prizeKeyAcquired"),
                "Prize",
                JOptionPane.INFORMATION_MESSAGE
            );
        }
        notifyItemAcquired(itemName);
        refreshWindow();
    }

    @Override
    public Object getContentAt(int row, int column) {
        return cells[row][column];
    }

    @Override
    public void setContentAt(int row, int column, Object content) {
        cells[row][column] = content;
    }

    @Override
    public boolean isVisible(int row, int column) {
        return visible[row][column];
    }

    @Override
    public void setVisible(int row, int column, boolean value) {
        visible[row][column] = value;
    }

    @Override
    public void revealGoal() {
        setVisibleIfValid(goalRow, goalColumn);
    }

    @Override
    public void revealAllItems() {
        for (int row = 0; row < numRows; row++) {
            for (int column = 0; column < numColumns; column++) {
                if (cells[row][column] instanceof Item) {
                    visible[row][column] = true;
                }
            }
        }
    }

    @Override
    public boolean traversedObstacle() {
        return lastTraversedObstacle;
    }

    @Override
    public long getLastMoveTime() {
        return lastMoveTime;
    }

    @Override
    public void setLastMoveTime(long time) {
        lastMoveTime = time;
    }

    @Override
    public int getGoalRowIdx() {
        return goalRow;
    }

    @Override
    public int getGoalColIdx() {
        return goalColumn;
    }

    @Override
    public int getDayCount() {
        return dayCount;
    }

    @Override
    public void addPenaltyDay() {
        advanceDay();
        if (window != null) {
            window.notifyPenaltyDayAdded();
            window.refreshTimeStatus();
        }
    }

    @Override
    public void setPrizeProgress(int count) {
        if (window != null) {
            window.updatePrizeProgress(count);
        }
    }

    @Override
    public void setHasPrize(boolean enabled) {
        hasPrize = enabled;
        if (window != null && enabled) {
            window.updatePrizeProgress(0);
        }
    }

    @Override
    public boolean hasGoldenPrizeKey() {
        return ownsGoldenKey;
    }

    @Override
    public void setOwnsGoldenKey(boolean owned) {
        ownsGoldenKey = owned;
    }

    @Override
    public boolean hasTeleported() {
        return teleported;
    }

    @Override
    public void setTeleported(boolean value) {
        teleported = value;
    }

    @Override
    public ResourceBundle getMessages() {
        return messages;
    }

    @Override
    public void notifyCountdownUpdate(int seconds) {
        if (window != null) {
            window.updatePenaltyCountdown(seconds);
        }
    }

    @Override
    public void notifyObstacleAdded() {
        if (window != null) {
            window.updatePenaltyObstacleAdded();
        }
    }

    @Override
    public void notifyTeleportCursorUpdate(int row, int column) {
        if (window != null) {
            window.updateTeleportCursor(row, column);
        }
    }

    @Override
    public void notifyTeleportModeOff() {
        if (window != null) {
            window.updateTeleportModeOff();
        }
    }

    @Override
    public void notifyPenaltyDayAdded() {
        if (window != null) {
            window.notifyPenaltyDayAdded();
        }
    }

    @Override
    public void notifyRevealActivated() {
        if (window != null) {
            window.updateRevealStatus(messages.getString("revealActivated"));
            window.updateGrid();
        }
    }
}
