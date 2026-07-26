package io.github.himath2002.maze.ui;

import io.github.himath2002.maze.api.InteractionCommand;
import io.github.himath2002.maze.api.InteractiveMenuPlugin;
import io.github.himath2002.maze.api.callback.MenuCallback;
import io.github.himath2002.maze.api.model.Item;
import io.github.himath2002.maze.api.model.Obstacle;
import io.github.himath2002.maze.engine.MazeGame;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serial;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Swing presentation layer for exploration, extension controls, and session state.
 */
public final class MazeWindow extends JFrame {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final Color DEEP_TEAL = new Color(20, 67, 62);
    private static final Color PALE_MINT = new Color(221, 241, 232);
    private static final Color WARM_SAND = new Color(249, 237, 207);
    private static final Color CORAL = new Color(238, 111, 90);
    private static final Color SOFT_WHITE = new Color(250, 252, 249);
    private static final Color GRID_BORDER = new Color(86, 105, 92, 170);
    private static final Path SAVE_PATH = Path.of("saves", "maze-state.ser");
    private static final int CELL_SIZE = 52;

    private final MazeGame game;
    private final JLabel gridSizeLabel = new JLabel();
    private final JLabel inventoryTitle = new JLabel();
    private final JLabel dateLabel = new JLabel();
    private final JLabel daysElapsedLabel = new JLabel();
    private final JLabel penaltyCountdownLabel = new JLabel("Penalty: —");
    private final JLabel prizeProgressLabel = new JLabel("Prize: 0/5");
    private final JLabel revealStatusLabel = new JLabel();
    private final JLabel teleportBadge = new JLabel();
    private final JLabel penaltyBadge = new JLabel();
    private final JLabel revealBadge = new JLabel();
    private final JButton extensionsButton = new JButton();
    private final JButton settingsButton = new JButton();
    private final JTextArea inventoryArea = new JTextArea(10, 18);
    private final JPanel gridPanel;
    private final JPanel[][] cellPanels;

    private final BufferedImage backgroundImage;
    private final BufferedImage shellImage;
    private final BufferedImage starfishImage;
    private final BufferedImage goalImage;
    private final BufferedImage crabImage;
    private final BufferedImage mapImage;
    private final BufferedImage timerImage;

    private Integer interactionRow;
    private Integer interactionColumn;
    private Timer blinkTimer;
    private int blinkCount;

    public MazeWindow(MazeGame game) {
        super("Modular Maze Engine");
        this.game = Objects.requireNonNull(game, "game");
        game.setWindow(this);

        configureGlobalFonts();
        backgroundImage = loadImage("images/seabackground.jpg", 0, 0);
        shellImage = loadImage("images/shell.png", 44, 38);
        starfishImage = loadImage("images/starfish.png", 44, 38);
        goalImage = loadImage("images/goal.png", 44, 38);
        crabImage = loadImage("images/crab.png", 44, 38);
        mapImage = loadImage("images/map.png", 38, 38);
        timerImage = loadImage("images/timer.png", 46, 40);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(SOFT_WHITE);
        add(createHeader(), BorderLayout.NORTH);

        gridPanel = new BackgroundPanel(backgroundImage);
        gridPanel.setLayout(new GridLayout(
            game.getNumRows(),
            game.getNumCols(),
            1,
            1
        ));
        gridPanel.setBorder(BorderFactory.createLineBorder(GRID_BORDER));
        gridPanel.setPreferredSize(new Dimension(
            game.getNumCols() * CELL_SIZE,
            game.getNumRows() * CELL_SIZE
        ));
        cellPanels = new JPanel[game.getNumRows()][game.getNumCols()];
        initializeGrid();

        JScrollPane gridScroll = new JScrollPane(gridPanel);
        gridScroll.setBorder(BorderFactory.createEmptyBorder());
        gridScroll.getViewport().setBackground(WARM_SAND);
        add(gridScroll, BorderLayout.CENTER);
        add(createInventoryPanel(), BorderLayout.EAST);
        add(createFooter(), BorderLayout.SOUTH);

        installKeyBindings();
        installWindowLifecycle();

        updateUiText();
        updateGrid();
        pack();
        setMinimumSize(new Dimension(880, 680));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new GridBagLayout());
        header.setBackground(DEEP_TEAL);
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 6, 4, 6);
        constraints.anchor = GridBagConstraints.WEST;

        gridSizeLabel.setForeground(SOFT_WHITE);
        gridSizeLabel.setFont(gridSizeLabel.getFont().deriveFont(Font.BOLD, 14f));
        constraints.gridx = 0;
        constraints.gridy = 0;
        header.add(gridSizeLabel, constraints);

        JComboBox<String> languageSelector = new JComboBox<>(new String[]{
            "en-AU", "fr-FR", "si-LK", "es-ES", "de-DE",
            "ja-JP", "it-IT", "zh-CN", "ru-RU"
        });
        languageSelector.setSelectedItem(defaultLanguageTag());
        languageSelector.addActionListener(event -> {
            String languageTag = (String) languageSelector.getSelectedItem();
            if (languageTag != null) {
                game.changeLocale(languageTag);
            }
        });
        constraints.gridx = 1;
        header.add(languageSelector, constraints);

        styleHeaderButton(extensionsButton);
        extensionsButton.addActionListener(event -> showExtensionActions());
        constraints.gridx = 2;
        header.add(extensionsButton, constraints);

        styleHeaderButton(settingsButton);
        settingsButton.addActionListener(event -> showSettingsMenu());
        constraints.gridx = 3;
        header.add(settingsButton, constraints);

        JPanel statuses = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        statuses.setOpaque(false);
        penaltyCountdownLabel.setForeground(WARM_SAND);
        prizeProgressLabel.setForeground(WARM_SAND);
        revealStatusLabel.setForeground(new Color(181, 241, 191));
        statuses.add(penaltyCountdownLabel);
        statuses.add(prizeProgressLabel);
        statuses.add(revealStatusLabel);
        statuses.add(teleportBadge);
        statuses.add(penaltyBadge);
        statuses.add(revealBadge);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 4;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        header.add(statuses, constraints);
        return header;
    }

    private JPanel createInventoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(PALE_MINT);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(191, 214, 203)),
            BorderFactory.createEmptyBorder(14, 14, 14, 14)
        ));

        inventoryTitle.setForeground(DEEP_TEAL);
        inventoryTitle.setFont(inventoryTitle.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(inventoryTitle, BorderLayout.NORTH);

        inventoryArea.setEditable(false);
        inventoryArea.setLineWrap(true);
        inventoryArea.setWrapStyleWord(true);
        inventoryArea.setBackground(SOFT_WHITE);
        inventoryArea.setForeground(DEEP_TEAL);
        inventoryArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane inventoryScroll = new JScrollPane(inventoryArea);
        inventoryScroll.setBorder(BorderFactory.createLineBorder(new Color(191, 214, 203)));
        panel.add(inventoryScroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(DEEP_TEAL);
        footer.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        dateLabel.setForeground(SOFT_WHITE);
        daysElapsedLabel.setForeground(WARM_SAND);
        daysElapsedLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        footer.add(dateLabel, BorderLayout.CENTER);
        footer.add(daysElapsedLabel, BorderLayout.EAST);
        return footer;
    }

    private void initializeGrid() {
        for (int row = 0; row < game.getNumRows(); row++) {
            for (int column = 0; column < game.getNumCols(); column++) {
                JPanel cell = new FogCellPanel(row, column);
                cell.setLayout(new GridBagLayout());
                cell.setBorder(BorderFactory.createLineBorder(GRID_BORDER));
                cell.setOpaque(false);
                cell.add(new JLabel());
                cellPanels[row][column] = cell;
                gridPanel.add(cell);
            }
        }
    }

    private void installKeyBindings() {
        InputMap inputMap = getRootPane().getInputMap(
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        ActionMap actionMap = getRootPane().getActionMap();

        bindKey(inputMap, actionMap, "UP", "move-up",
            event -> handleNavigation("up", InteractionCommand.UP));
        bindKey(inputMap, actionMap, "DOWN", "move-down",
            event -> handleNavigation("down", InteractionCommand.DOWN));
        bindKey(inputMap, actionMap, "LEFT", "move-left",
            event -> handleNavigation("left", InteractionCommand.LEFT));
        bindKey(inputMap, actionMap, "RIGHT", "move-right",
            event -> handleNavigation("right", InteractionCommand.RIGHT));
        bindKey(inputMap, actionMap, "ENTER", "interaction-confirm",
            event -> handleInteraction(InteractionCommand.CONFIRM));
        bindKey(inputMap, actionMap, "ESCAPE", "interaction-cancel",
            event -> handleInteraction(InteractionCommand.CANCEL));
    }

    private void bindKey(
        InputMap inputMap,
        ActionMap actionMap,
        String keyStroke,
        String actionName,
        Consumer<ActionEvent> action
    ) {
        inputMap.put(KeyStroke.getKeyStroke(keyStroke), actionName);
        actionMap.put(actionName, new KeyAction(action));
    }

    private void handleNavigation(
        String direction,
        InteractionCommand interactionCommand
    ) {
        InteractiveMenuPlugin interactivePlugin = activeInteraction();
        if (interactivePlugin != null) {
            interactivePlugin.handleCommand(interactionCommand);
        } else {
            game.move(direction);
        }
        refreshState();
    }

    private void handleInteraction(InteractionCommand command) {
        InteractiveMenuPlugin interactivePlugin = activeInteraction();
        if (interactivePlugin != null) {
            interactivePlugin.handleCommand(command);
            refreshState();
        }
    }

    private InteractiveMenuPlugin activeInteraction() {
        for (MenuCallback callback : game.getMenuCallbacks()) {
            if (callback instanceof InteractiveMenuPlugin interactive
                && interactive.isInteractionActive()) {
                return interactive;
            }
        }
        return null;
    }

    private void showExtensionActions() {
        List<MenuCallback> callbacks = game.getMenuCallbacks();
        if (callbacks.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                message("noPlugins", "No extension actions are available."),
                message("infoTitle", "Information"),
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        if (callbacks.size() == 1) {
            callbacks.get(0).activate();
            return;
        }

        JPopupMenu menu = new JPopupMenu();
        for (MenuCallback callback : callbacks) {
            JMenuItem item = new JMenuItem(callback.menuLabel());
            item.addActionListener(event -> callback.activate());
            menu.add(item);
        }
        menu.show(extensionsButton, 0, extensionsButton.getHeight());
    }

    private void showSettingsMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem cheat = new JMenuItem(message("cheatMode", "Toggle visibility mode"));
        cheat.addActionListener(event -> {
            game.toggleCheat();
            updateGrid();
        });
        menu.add(cheat);

        JMenuItem save = new JMenuItem(message("saveButton", "Save"));
        save.addActionListener(event -> game.saveGame(SAVE_PATH));
        menu.add(save);

        JMenuItem load = new JMenuItem(message("loadButton", "Load"));
        load.addActionListener(event -> game.loadGame(SAVE_PATH));
        menu.add(load);

        menu.show(settingsButton, 0, settingsButton.getHeight());
    }

    private void installWindowLifecycle() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                stopBlinking();
                game.shutdown();
            }
        });
    }

    public void refreshState() {
        updateGrid();
        updateInventory();
        refreshTimeStatus();
        updateStatusVisibility();
    }

    public void refreshTimeStatus() {
        dateLabel.setText(game.getLocalizedDate());
        daysElapsedLabel.setText(
            message("daysElapsedLabel", "Days elapsed") + ": " + game.getDayCount()
        );
    }

    public void updateGrid() {
        for (int row = 0; row < game.getNumRows(); row++) {
            for (int column = 0; column < game.getNumCols(); column++) {
                updateCell(row, column);
            }
        }
        gridPanel.repaint();
    }

    private void updateCell(int row, int column) {
        JPanel cell = cellPanels[row][column];
        JLabel icon = (JLabel) cell.getComponent(0);
        icon.setIcon(null);

        boolean visibleCell = game.isVisible(row, column) || game.isCheatEnabled();
        if (visibleCell) {
            BufferedImage image = imageForCell(row, column);
            if (image != null) {
                icon.setIcon(new ImageIcon(image));
            }
        }

        boolean selected = interactionRow != null
            && interactionColumn != null
            && row == interactionRow
            && column == interactionColumn;
        if (selected && blinkCount % 2 == 0) {
            cell.setBorder(BorderFactory.createLineBorder(CORAL, 3));
        } else {
            cell.setBorder(BorderFactory.createLineBorder(GRID_BORDER));
        }
    }

    private BufferedImage imageForCell(int row, int column) {
        if (row == game.getPlayerRowIdx() && column == game.getPlayerColIdx()) {
            return crabImage;
        }

        Object content = game.getContentAt(row, column);
        if ("goal".equals(content)) {
            return goalImage;
        }
        if (content instanceof Item item) {
            return item.getName().toLowerCase(Locale.ROOT).contains("map")
                ? mapImage
                : shellImage;
        }
        if (content instanceof Obstacle obstacle) {
            return Obstacle.PENALTY.equals(obstacle.getName())
                ? timerImage
                : starfishImage;
        }
        return null;
    }

    private void updateInventory() {
        List<String> items = game.getInventory();
        inventoryArea.setText(items.isEmpty() ? "—" : String.join("\n", items));
        inventoryArea.setCaretPosition(0);
    }

    public void updateUiText() {
        setTitle(message("gameTitle", "Modular Maze Engine"));
        gridSizeLabel.setText(
            message("gridSize", "Grid size") + ": "
                + game.getNumRows() + " × " + game.getNumCols()
        );
        inventoryTitle.setText(message("inventory", "Inventory"));
        extensionsButton.setText(message("extensionsButton", "Extensions"));
        extensionsButton.setToolTipText(
            message("extensionsTooltip", "Open extension actions")
        );
        settingsButton.setText(message("settingsButton", "Settings") + " ⚙");
        settingsButton.setToolTipText(
            message("settingsTooltip", "Open settings")
        );
        updatePluginBadge(
            teleportBadge,
            message("teleportPlugin", "Teleport"),
            game.hasPlugin("teleport")
        );
        updatePluginBadge(
            penaltyBadge,
            message("penaltyPlugin", "Penalty"),
            game.hasPlugin("penalty")
        );
        updatePluginBadge(
            revealBadge,
            message("revealPlugin", "Reveal"),
            game.hasPlugin("reveal")
        );
        refreshState();
    }

    private void updatePluginBadge(JLabel label, String name, boolean available) {
        String state = available
            ? message("available", "Available")
            : message("notAvailable", "Unavailable");
        label.setText("● " + name + ": " + state);
        label.setForeground(available ? new Color(156, 231, 173) : new Color(225, 170, 158));
    }

    private void updateStatusVisibility() {
        penaltyCountdownLabel.setVisible(game.hasPlugin("penalty"));
        prizeProgressLabel.setVisible(game.hasPrize());
        revealStatusLabel.setVisible(game.hasPlugin("reveal"));
    }

    public void updateTeleportCursor(int row, int column) {
        interactionRow = row;
        interactionColumn = column;
        if (blinkTimer == null) {
            blinkTimer = new Timer(450, event -> {
                blinkCount++;
                updateGrid();
            });
            blinkTimer.start();
        }
        updateGrid();
    }

    public void updateTeleportModeOff() {
        interactionRow = null;
        interactionColumn = null;
        stopBlinking();
        updateGrid();
    }

    private void stopBlinking() {
        if (blinkTimer != null) {
            blinkTimer.stop();
            blinkTimer = null;
        }
        blinkCount = 0;
    }

    public void updatePenaltyCountdown(int seconds) {
        penaltyCountdownLabel.setText(
            seconds < 0 ? "Penalty: —" : "Penalty: " + seconds + "s"
        );
    }

    public void updatePenaltyObstacleAdded() {
        Toolkit.getDefaultToolkit().beep();
        updateGrid();
    }

    public void notifyPenaltyDayAdded() {
        Toolkit.getDefaultToolkit().beep();
    }

    public void updatePrizeProgress(int count) {
        int boundedCount = Math.max(0, Math.min(5, count));
        prizeProgressLabel.setText(
            message("prizeProgress", "Prize progress") + ": "
                + boundedCount + "/5"
        );
        prizeProgressLabel.setVisible(true);
    }

    public void updateRevealStatus(String text) {
        revealStatusLabel.setText(text);
        Timer clearTimer = new Timer(3_000, event -> revealStatusLabel.setText(""));
        clearTimer.setRepeats(false);
        clearTimer.start();
    }

    private void styleHeaderButton(JButton button) {
        button.setForeground(DEEP_TEAL);
        button.setBackground(WARM_SAND);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(225, 208, 167)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
    }

    private void configureGlobalFonts() {
        Font interfaceFont = new Font(Font.DIALOG, Font.PLAIN, 13);
        UIManager.put("Label.font", interfaceFont);
        UIManager.put("Button.font", interfaceFont.deriveFont(Font.BOLD));
        UIManager.put("TextArea.font", interfaceFont);
        UIManager.put("ComboBox.font", interfaceFont);
        UIManager.put("MenuItem.font", interfaceFont);
    }

    private String defaultLanguageTag() {
        String language = game.getLocale().getLanguage();
        return switch (language) {
            case "fr" -> "fr-FR";
            case "si" -> "si-LK";
            case "es" -> "es-ES";
            case "de" -> "de-DE";
            case "ja" -> "ja-JP";
            case "it" -> "it-IT";
            case "zh" -> "zh-CN";
            case "ru" -> "ru-RU";
            default -> "en-AU";
        };
    }

    private String message(String key, String fallback) {
        try {
            return game.getMessages().getString(key);
        } catch (MissingResourceException ex) {
            return fallback;
        }
    }

    private BufferedImage loadImage(String resourceName, int width, int height) {
        URL resource = Thread.currentThread()
            .getContextClassLoader()
            .getResource(resourceName);
        if (resource == null) {
            throw new IllegalStateException("Missing image resource: " + resourceName);
        }
        try {
            BufferedImage image = ImageIO.read(resource);
            if (image == null) {
                throw new IOException("Unsupported image format");
            }
            return width > 0 && height > 0 ? scaleImage(image, width, height) : image;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load image resource: " + resourceName, ex);
        }
    }

    private BufferedImage scaleImage(BufferedImage image, int width, int height) {
        BufferedImage scaled = new BufferedImage(
            width,
            height,
            BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private final class FogCellPanel extends JPanel {
        @Serial
        private static final long serialVersionUID = 1L;

        private final int row;
        private final int column;

        private FogCellPanel(int row, int column) {
            this.row = row;
            this.column = column;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!game.isVisible(row, column) && !game.isCheatEnabled()) {
                graphics.setColor(new Color(44, 59, 57, 190));
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }

    private static final class BackgroundPanel extends JPanel {
        @Serial
        private static final long serialVersionUID = 1L;

        private final BufferedImage background;

        private BackgroundPanel(BufferedImage background) {
            this.background = background;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            graphics.drawImage(background, 0, 0, getWidth(), getHeight(), this);
        }
    }

    private static final class KeyAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Consumer<ActionEvent> delegate;

        private KeyAction(Consumer<ActionEvent> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            delegate.accept(event);
        }
    }
}
