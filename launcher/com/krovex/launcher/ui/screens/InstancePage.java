package com.krovex.launcher.ui.screens;

import com.krovex.launcher.launch.GameLauncher;
import com.krovex.launcher.launch.LaunchSession;
import com.krovex.launcher.model.Installation;
import com.krovex.launcher.model.InstallationStore;
import com.krovex.launcher.model.InstalledContentScanner;
import com.krovex.launcher.model.MicrosoftAccount;
import com.krovex.launcher.ui.SwingThemeUtil;
import com.krovex.launcher.ui.components.InstanceLogo;
import com.krovex.launcher.ui.screens.ExportInstanceDialog;
import com.krovex.theme.KrovexTheme;
import com.krovex.theme.ThemeStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import com.krovex.launcher.ui.LauncherStyle;
import com.krovex.launcher.ui.LauncherPreferences;
import java.awt.Desktop;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Locale;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.JTabbedPane;
import javax.swing.TransferHandler;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

public final class InstancePage
extends JPanel {
    private final KrovexTheme theme;
    private final Installation installation;
    private final LauncherPreferences preferences;
    private final InstallationStore installationStore;
    private final ThemeStore themeStore;
    private final InstalledContentScanner scanner = new InstalledContentScanner();
    private final Supplier<MicrosoftAccount> activeAccountSupplier;
    private final Path krovexHome;
    private final Runnable onInstanceChanged;
    private final Consumer<Installation> onDeleted;
    private record ActiveLaunch(LaunchSession session, String status) {}
    private static final java.util.Map<String, ActiveLaunch> ACTIVE_LAUNCHES = new java.util.HashMap<>();
    private final javax.swing.Timer launchUiTimer = new javax.swing.Timer(400, event -> {
        if (isShowing()) refreshLaunchButtons();
    });
    private boolean showingLaunchStatus;
    private JButton stopButton;
    private java.awt.Frame launcherWindow;
    private JPanel launchActions;
    private JPanel instanceInfo;
    private JTable contentTable;
    private List<InstalledContentScanner.ContentEntry> scannedContent = List.of();
    private final java.util.Map<InstalledContentScanner.ContentEntry, Path> contentPaths = new java.util.HashMap<>();
    private final java.util.Map<String, String> displayNames = new java.util.HashMap<>();
    private JPanel contentDropFooter;
    private JButton backToLibrary;
    private JButton playButton;
    private JLabel nameLabel;
    private JLabel instanceLogo;
    private JLabel statusLabel;
    private final JPanel deletePrompt = new JPanel(new BorderLayout());
    private ContentTableModel tableModel;
    private final java.awt.CardLayout contentLayout = new java.awt.CardLayout();
    private final JPanel contentBody = new JPanel(contentLayout);
    private final java.awt.CardLayout screens = new java.awt.CardLayout();
    private final JPanel workspace = new JPanel(screens);
    private ExplorePanel explore;
    private JPanel instanceSettings;
    private InstalledContentScanner.Category activeFilter;
    private JTextField searchField;

    public InstancePage(Installation installation, KrovexTheme theme, InstallationStore installationStore, ThemeStore themeStore, Path krovexHome, Supplier<MicrosoftAccount> activeAccountSupplier, Runnable onInstanceChanged, Consumer<Installation> onDeleted, LauncherPreferences preferences) {
        this.installation = installation;
        this.preferences = preferences;
        this.theme = theme;
        this.installationStore = installationStore;
        this.themeStore = themeStore;
        this.krovexHome = krovexHome;
        this.activeAccountSupplier = activeAccountSupplier;
        this.onInstanceChanged = onInstanceChanged;
        this.onDeleted = onDeleted;
        this.setLayout(new BorderLayout());
        this.setBackground(SwingThemeUtil.background(theme));
        JPanel top = new JPanel(new BorderLayout());
        top.add(header(), BorderLayout.NORTH);
        top.add(deletePrompt, BorderLayout.CENTER);
        deletePrompt.setVisible(false);
        add(top, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(LauncherStyle.BASE);
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void installDefaults() {
                super.installDefaults();
                tabInsets = new java.awt.Insets(8, 18, 8, 18);
                selectedTabPadInsets = new java.awt.Insets(0, 0, 0, 0);
                contentBorderInsets = new java.awt.Insets(1, 0, 0, 0);
            }
            @Override protected void paintTabBackground(java.awt.Graphics g, int placement, int index,
                                                        int x, int y, int w, int h, boolean selected) {
                g.setColor(LauncherStyle.BASE);
                g.fillRect(x, y, w, h);
                if (selected) {
                    g.setColor(LauncherStyle.ACCENT);
                    g.fillRect(x + 4, y + h - 3, w - 8, 3);
                }
            }
            @Override protected void paintTabBorder(java.awt.Graphics g, int placement, int index,
                                                    int x, int y, int w, int h, boolean selected) {}
            @Override protected void paintContentBorder(java.awt.Graphics g, int placement, int selected) {
                g.setColor(LauncherStyle.EDGE);
                java.awt.Rectangle tab = getTabBounds(tabPane, Math.max(0, selected));
                g.drawLine(0, tab.y + tab.height, tabPane.getWidth(), tab.y + tab.height);
            }
        });
        tabs.addTab("Content", this.contentArea());
        if (preferences.flag("tabs.files", true)) tabs.addTab("Files", folderPanel("Open instance folder", installation.gameDirectory()));
        if (preferences.flag("tabs.worlds", true)) tabs.addTab("Worlds", folderPanel("Open saves folder", installation.savesDirectory()));
        if (preferences.flag("tabs.screenshots", false)) {
            tabs.addTab("Screenshots", folderPanel("Open screenshots folder", installation.gameDirectory().resolve("screenshots")));
        }
        tabs.addTab("Logs", folderPanel("Open logs folder", installation.gameDirectory().resolve("logs")));
        JPanel share = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
        share.add(LauncherStyle.button("Export modpack", true, this::showExportDialog));
        tabs.addTab("Share", share);
        workspace.add(tabs, "instance");
        this.add(workspace, BorderLayout.CENTER);
    }

    private JPanel folderPanel(String action, Path directory) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
        panel.add(LauncherStyle.button(action, true, () -> {
            try {
                Files.createDirectories(directory);
                Desktop.getDesktop().open(directory.toFile());
            } catch (IOException | UnsupportedOperationException error) {
                JOptionPane.showMessageDialog(this, error.getMessage(), "Could not open folder",
                        JOptionPane.ERROR_MESSAGE);
            }
        }));
        return panel;
    }

    private JPanel header() {
        JPanel header = LauncherStyle.card(new BorderLayout(14, 0), 14);
        this.instanceLogo = new JLabel(com.krovex.launcher.ui.LaunchBanner.thumbnail(180, 111, installation.id().hashCode()));
        if (instanceLogo.getIcon() == null) instanceLogo.setIcon(new InstanceLogo(installation, theme, 60));
        header.add(this.instanceLogo, BorderLayout.WEST);
        JPanel info = new JPanel();
        instanceInfo = info;
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, 1));
        this.nameLabel = new JLabel(this.installation.name());
        nameLabel.setFont(nameLabel.getFont().deriveFont(java.awt.Font.BOLD, 30f));
        nameLabel.setForeground(SwingThemeUtil.text(this.theme));
        this.statusLabel = new JLabel(" ");
        this.statusLabel.setFont(SwingThemeUtil.baseFont(this.theme, 0));
        this.statusLabel.setForeground(SwingThemeUtil.accent(this.theme));
        info.add(nameLabel);
        JPanel badges = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badges.setOpaque(false);
        badges.setAlignmentX(LEFT_ALIGNMENT);
        for (String value : new String[]{installation.loaderDisplayLabel(), "Minecraft " + installation.minecraftVersion()}) {
            JLabel badge = new JLabel(value);
            badge.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            badge.setOpaque(false);
            badges.add(new JPanel(new BorderLayout()) {
                { setOpaque(false); add(badge); }
                @Override protected void paintComponent(java.awt.Graphics graphics) {
                    java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(36, 38, 46));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                    g.dispose();
                }
            });
        }
        info.add(badges);
        if (preferences.flag("instance.playtime", true)) info.add(new JLabel(installation.formattedPlaytime()));
        info.add(this.statusLabel);
        header.add((Component)info, "Center");
        JPanel actions = new JPanel(new FlowLayout(2, 8, 0));
        actions.setOpaque(false);
        launchActions = actions;
        this.playButton = LauncherStyle.button("▶  Play", true, this::handlePlay);
        JButton moreMenu = LauncherStyle.button("More  \u2022\u2022\u2022", false, () -> {});
        moreMenu.addActionListener(event -> showMoreMenu(moreMenu));
        actions.add(this.playButton);
        this.stopButton = LauncherStyle.button("Stop", false, this::stopLaunch);
        actions.add(this.stopButton);
        refreshLaunchButtons();
        actions.add(LauncherStyle.button("Settings", false, this::showInstanceSettings));
        actions.add(moreMenu);
        header.add((Component)actions, "East");
        JPanel section = new JPanel(new BorderLayout(0, 8));
        section.setBackground(LauncherStyle.BASE);
        section.setBorder(BorderFactory.createEmptyBorder(4, 16, 8, 16));
        JPanel titleBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleBar.setOpaque(false);
        backToLibrary = LauncherStyle.button("‹", false, () -> {});
        backToLibrary.getAccessibleContext().setAccessibleName("Back to instances");
        titleBar.add(backToLibrary);
        JLabel crumb = new JLabel("Instances");
        crumb.setFont(crumb.getFont().deriveFont(java.awt.Font.PLAIN, 16f));
        crumb.setForeground(LauncherStyle.MUTED);
        JLabel slash = new JLabel("/");
        slash.setForeground(LauncherStyle.MUTED);
        JLabel heading = new JLabel(installation.name());
        heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        titleBar.add(crumb);
        titleBar.add(slash);
        titleBar.add(heading);
        section.add(titleBar, BorderLayout.NORTH);
        section.add(header, BorderLayout.CENTER);
        return section;
    }

    public void setBackAction(Runnable action) {
        for (var listener : backToLibrary.getActionListeners()) backToLibrary.removeActionListener(listener);
        backToLibrary.addActionListener(event -> action.run());
    }

    public void setPrimaryAction(java.util.function.BooleanSupplier isPrimary, Runnable toggle) {
        JButton star = LauncherStyle.button("", false, toggle);
        Runnable update = () -> {
            boolean primary = isPrimary.getAsBoolean();
            star.setText(primary ? "★ Primary" : "☆ Set primary");
            star.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 14));
            star.setToolTipText(primary ? "Remove this instance from Launchpad" : "Use this instance for Launchpad");
        };
        star.addActionListener(event -> SwingUtilities.invokeLater(update));
        update.run();
        star.setAlignmentX(LEFT_ALIGNMENT);
        instanceInfo.add(star);
    }

    public void triggerPlay() {
        this.handlePlay();
    }

    @Override public void addNotify() {
        super.addNotify();
        if (SwingUtilities.getWindowAncestor(this) instanceof java.awt.Frame frame) launcherWindow = frame;
        refreshLaunchButtons();
        if (ACTIVE_LAUNCHES.containsKey(installation.id())) launchUiTimer.start();
    }

    @Override public void removeNotify() {
        launchUiTimer.stop();
        super.removeNotify();
    }

    private void refreshLaunchButtons() {
        ActiveLaunch launch = ACTIVE_LAUNCHES.get(installation.id());
        MicrosoftAccount shown = activeAccountSupplier.get();
        String playText = shown == null || shown.isOffline() ? "Play offline" : "Play";
        if (!playText.equals(playButton.getText().replace("\u25B6  ", ""))) playButton.setText("\u25B6  " + playText);
        playButton.setEnabled(launch == null);
        stopButton.setVisible(launch != null);
        stopButton.setEnabled(launch != null && !launch.session().isStopping());
        if (launch == null) launchUiTimer.stop();
        if (launch != null) {
            showingLaunchStatus = true;
            String message = launch.session().isStopping() ? "Stopping…" : launch.status();
            if (!message.equals(statusLabel.getText())) statusLabel.setText(message);
        } else if (showingLaunchStatus) {
            showingLaunchStatus = false;
            statusLabel.setText("Session ended.");
        }
    }

    private void stopLaunch() {
        ActiveLaunch launch = ACTIVE_LAUNCHES.get(installation.id());
        if (launch != null) {
            launch.session().stop();
            refreshLaunchButtons();
        }
    }

    private void handlePlay() {
        if (ACTIVE_LAUNCHES.containsKey(installation.id())) {
            refreshLaunchButtons();
            return;
        }
        MicrosoftAccount account = activeAccountSupplier.get();
        LaunchSession session = new LaunchSession();
        ACTIVE_LAUNCHES.put(installation.id(), new ActiveLaunch(session, "Preparing to launch…"));
        refreshLaunchButtons();
        GameLauncher launcher = new GameLauncher(krovexHome);
        new Thread(() -> launcher.launch(installation, account, session, event ->
                SwingUtilities.invokeLater(() -> updateLaunch(session, event))),
                "krovex-launch-" + installation.id()).start();
    }

    private void updateLaunch(LaunchSession session, GameLauncher.LaunchEvent event) {
        String id = installation.id();
        switch (event) {
            case GameLauncher.Status status -> ACTIVE_LAUNCHES.put(id, new ActiveLaunch(session, status.message()));
            case GameLauncher.Progress progress -> ACTIVE_LAUNCHES.put(id, new ActiveLaunch(session,
                    progress.phase() + ": " + progress.completed() + "/" + progress.total()));
            case GameLauncher.Started started -> {
                ACTIVE_LAUNCHES.put(id, new ActiveLaunch(session, "Running…"));
                installationStore.touch(installation);
                if (preferences.flag("behavior.minimize", false) && launcherWindow != null) {
                    launcherWindow.setExtendedState(launcherWindow.getExtendedState() | java.awt.Frame.ICONIFIED);
                }
            }
            case GameLauncher.Failed failure -> {
                ACTIVE_LAUNCHES.remove(id);
                showingLaunchStatus = false;
                statusLabel.setText(failure.readableMessage());
            }
            case GameLauncher.Stopped stopped -> {
                ACTIVE_LAUNCHES.remove(id);
                showingLaunchStatus = false;
                statusLabel.setText(session.isStopping() ? "Session stopped."
                        : "Closed (exit code " + stopped.exitCode() + ").");
                installationStore.recordPlaytime(installation, stopped.secondsPlayed());
                if (preferences.flag("behavior.refocus", false) && launcherWindow != null) {
                    launcherWindow.setExtendedState(launcherWindow.getExtendedState() & ~java.awt.Frame.ICONIFIED);
                    launcherWindow.toFront();
                }
                onInstanceChanged.run();
            }
        }
        refreshLaunchButtons();
    }

    private void showMoreMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem settings = new JMenuItem("Settings");
        settings.addActionListener(e -> this.showInstanceSettings());
        menu.add(settings);
        JMenuItem export = new JMenuItem("Export modpack");
        export.addActionListener(e -> this.showExportDialog());
        menu.add(export);
        menu.addSeparator();
        JMenuItem delete = new JMenuItem("Delete instance");
        delete.setForeground(new Color(226, 61, 82));
        delete.addActionListener(e -> this.confirmAndDelete());
        delete.setEnabled(!ACTIVE_LAUNCHES.containsKey(installation.id()));
        menu.add(delete);
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void showInstanceSettings() {
        if (instanceSettings != null) return;
        String previous = explore != null && explore.isVisible() ? "explore" : "instance";
        JTextField name = new JTextField(installation.name(), 24);
        JLabel nameLabel = new JLabel("Instance name");
        nameLabel.setLabelFor(name);
        nameLabel.setForeground(LauncherStyle.MUTED);
        JPanel nameRow = new JPanel(new BorderLayout(0, 8));
        nameRow.setOpaque(false);
        name.setBackground(new Color(13, 14, 17));
        nameRow.add(nameLabel, BorderLayout.NORTH);
        nameRow.add(name, BorderLayout.CENTER);

        JPanel colors = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        colors.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        String current = installation.logoId() == null ? InstanceLogo.BUILTIN_LOGO_IDS[0] : installation.logoId();
        for (String logoId : InstanceLogo.BUILTIN_LOGO_IDS) {
            JToggleButton color = new JToggleButton(new InstanceLogo(logoId, "K", 36));
            color.setContentAreaFilled(false);
            color.setFocusPainted(false);
            color.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
            color.addChangeListener(event -> color.setBorder(color.isSelected()
                    ? BorderFactory.createCompoundBorder(LauncherStyle.outline(14), BorderFactory.createEmptyBorder(2, 2, 2, 2))
                    : BorderFactory.createEmptyBorder(3, 3, 3, 3)));
            color.setActionCommand(logoId);
            color.setToolTipText(logoId);
            color.getAccessibleContext().setAccessibleName(logoId + " instance color");
            group.add(color);
            color.setSelected(logoId.equals(current));
            colors.add(color);
        }
        JPanel colorRow = new JPanel(new BorderLayout(0, 8));
        colorRow.setOpaque(false);
        JLabel colorLabel = new JLabel("Instance color");
        colorLabel.setForeground(LauncherStyle.MUTED);
        colorRow.add(colorLabel, BorderLayout.NORTH);
        colorRow.add(colors, BorderLayout.CENTER);

        instanceSettings = new JPanel(new BorderLayout());
        instanceSettings.setBackground(LauncherStyle.BASE);
        instanceSettings.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Instance settings");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 24f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        form.add(title);
        for (JPanel row : new JPanel[]{nameRow, colorRow}) {
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            form.add(Box.createVerticalStrut(12));
            form.add(row);
        }
        Runnable close = () -> {
            workspace.remove(instanceSettings);
            instanceSettings = null;
            screens.show(workspace, previous);
            workspace.revalidate();
            workspace.repaint();
        };
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        controls.setAlignmentX(LEFT_ALIGNMENT);
        controls.add(LauncherStyle.button("Cancel", false, close));
        controls.add(LauncherStyle.button("Save changes", true, () -> {
            String updated = name.getText().strip();
            if (!updated.isEmpty()) installation.setName(updated);
            if (group.getSelection() != null) installation.setLogoId(group.getSelection().getActionCommand());
            installationStore.save();
            this.nameLabel.setText(installation.name());
            instanceLogo.setIcon(new InstanceLogo(installation, theme, 56));
            close.run();
            onInstanceChanged.run();
        }));
        form.add(Box.createVerticalStrut(14));
        form.add(controls);
        instanceSettings.add(form, BorderLayout.NORTH);
        workspace.add(instanceSettings, "settings");
        screens.show(workspace, "settings");
        name.requestFocusInWindow();
    }

    private void showExportDialog() {
        Frame owner = (Frame)SwingUtilities.getWindowAncestor(this);
        new ExportInstanceDialog(owner, this.theme, this.installation, this.themeStore).setVisible(true);
    }

    private void confirmAndDelete() {
        JTextField name = new JTextField(18);
        showDeletePrompt("Type the instance name to permanently delete its files.", name, () -> {
            if (!name.getText().equals(installation.name())) {
                statusLabel.setText("Instance name did not match.");
                return;
            }
            if (ACTIVE_LAUNCHES.containsKey(installation.id())) {
                statusLabel.setText("Stop Minecraft before deleting this instance.");
                return;
            }
            try {
                installationStore.deleteWithFiles(installation);
                onDeleted.accept(installation);
            } catch (Exception error) {
                statusLabel.setText("Could not delete instance files: " + error.getMessage());
            }
        });
        name.requestFocusInWindow();
    }

    private void showDeletePrompt(String message, JTextField input, Runnable delete) {
        deletePrompt.removeAll();
        JPanel row = LauncherStyle.card(new BorderLayout(12, 0), 10);
        JLabel label = new JLabel(message);
        if (input != null) label.setLabelFor(input);
        row.add(label, input == null ? BorderLayout.CENTER : BorderLayout.WEST);
        if (input != null) row.add(input, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(LauncherStyle.button("Cancel", false, () -> deletePrompt.setVisible(false)));
        actions.add(LauncherStyle.button("Delete", false, () -> {
            deletePrompt.setVisible(false);
            delete.run();
        }));
        row.add(actions, BorderLayout.EAST);
        deletePrompt.add(row);
        deletePrompt.setVisible(true);
        revalidate();
        repaint();
    }

    private JPanel contentArea() {
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(LauncherStyle.BASE);
        area.setBorder(BorderFactory.createEmptyBorder(12, 20, 20, 20));
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setOpaque(false);
        JPanel tabs = new JPanel(new FlowLayout(0, 6, 0));
        tabs.setOpaque(false);
        ButtonGroup tabGroup = new ButtonGroup();
        tabs.add(this.filterTab("All", null, tabGroup, true));
        tabs.add(this.filterTab("Mods", InstalledContentScanner.Category.MOD, tabGroup, false));
        tabs.add(this.filterTab("Texture Packs", InstalledContentScanner.Category.TEXTURE_PACK, tabGroup, false));
        tabs.add(this.filterTab("Shaders", InstalledContentScanner.Category.SHADER, tabGroup, false));
        tabs.add(this.filterTab("Other", InstalledContentScanner.Category.OTHER, tabGroup, false));

        this.searchField = new JTextField(18);
        this.searchField.putClientProperty("JTextField.placeholderText", "Search installed content\u2026");
        this.searchField.getDocument().addDocumentListener(new SimpleDocListener(this::filterContent));
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchField.getAccessibleContext().setAccessibleName("Search content");
        searchField.setToolTipText("Search content");
        searchRow.add(this.searchField, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(LauncherStyle.button("+  Add files", true, this::chooseFiles));
        actions.add(LauncherStyle.button("Explore", false, this::browseMods));
        actions.add(LauncherStyle.button("↻  Refresh", false, () -> {
            ContentIcons.refreshLocal(installation.gameDirectory());
            refreshTable();
        }));
        searchRow.add(actions, BorderLayout.EAST);
        toolbar.add(tabs, BorderLayout.SOUTH);
        toolbar.add(searchRow, BorderLayout.NORTH);
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        area.add((Component)toolbar, "North");
        this.tableModel = new ContentTableModel();
        JTable table = new JTable(this.tableModel) {
            @Override protected void processMouseEvent(java.awt.event.MouseEvent event) {
                if (event.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED) {
                    putClientProperty("content.keyboardFocus", false);
                    repaint();
                }
                super.processMouseEvent(event);
            }

            @Override protected void processKeyEvent(java.awt.event.KeyEvent event) {
                putClientProperty("content.keyboardFocus", true);
                super.processKeyEvent(event);
                repaint();
            }

            @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component rendered = super.prepareRenderer(renderer, row, column);
                if (rendered instanceof javax.swing.JComponent cell && !(renderer instanceof ContentAction)) {
                    boolean keyboardLead = isFocusOwner()
                            && getClientProperty("content.keyboardFocus") == Boolean.TRUE
                            && row == getSelectionModel().getLeadSelectionIndex()
                            && column == getColumnModel().getSelectionModel().getLeadSelectionIndex();
                    cell.setBackground(getBackground());
                    cell.setForeground(getForeground());
                    cell.setBorder(keyboardLead
                            ? BorderFactory.createMatteBorder(0, 0, 1, 0, LauncherStyle.ACCENT)
                            : BorderFactory.createEmptyBorder(0, column == 0 ? 8 : 2, 0, 5));
                }
                return rendered;
            }

            @Override public String getToolTipText(java.awt.event.MouseEvent event) {
                int column = columnAtPoint(event.getPoint());
                int row = rowAtPoint(event.getPoint());
                if (row >= 0 && column >= 0) {
                    int modelColumn = convertColumnIndexToModel(column);
                    if (modelColumn == 4) return "Delete content";
                    if (modelColumn == 3) return tableModel.entries.get(convertRowIndexToModel(row)).enabled()
                            ? "Disable content" : "Enable content";
                }
                return super.getToolTipText(event);
            }
        };
        table.setUI(new javax.swing.plaf.basic.BasicTableUI());
        table.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) {
                var cause = event.getCause();
                if (cause == java.awt.event.FocusEvent.Cause.TRAVERSAL_FORWARD
                        || cause == java.awt.event.FocusEvent.Cause.TRAVERSAL_BACKWARD) {
                    table.putClientProperty("content.keyboardFocus", true);
                }
            }
        });
        contentTable = table;
        table.setToolTipText("");
        table.setSelectionBackground(table.getBackground());
        table.setSelectionForeground(table.getForeground());
        table.getTableHeader().setReorderingAllowed(false);
        for (int col = 3; col <= 4; col++) {
            boolean trash = col == 4;
            var control = new ContentAction(trash, trash ? this::deleteContent : this::toggleContent);
            var column = table.getColumnModel().getColumn(col);
            column.setCellRenderer(control);
            column.setCellEditor(control);
            int width = trash ? 52 : 57;
            column.setMinWidth(width);
            column.setPreferredWidth(width);
            column.setMaxWidth(width);
        }
        table.setFillsViewportHeight(true);
        table.setRowHeight(54);
        table.setFont(table.getFont().deriveFont(14f));
        table.getColumnModel().getColumn(0).setCellRenderer(new javax.swing.table.TableCellRenderer() {
            private final JPanel cell = new JPanel(new BorderLayout(8, 0));
            private final JLabel icon = new JLabel();
            private final JLabel title = new JLabel();
            private final JLabel filename = new JLabel();
            {
                JPanel labels = new JPanel(new java.awt.GridLayout(2, 1));
                labels.setOpaque(false);
                title.putClientProperty("html.disable", true);
                filename.putClientProperty("html.disable", true);
                title.setFont(table.getFont().deriveFont(java.awt.Font.BOLD));
                filename.setFont(table.getFont().deriveFont(12f));
                filename.setForeground(LauncherStyle.MUTED);
                labels.add(title);
                labels.add(filename);
                cell.add(icon, BorderLayout.WEST);
                cell.add(labels, BorderLayout.CENTER);
            }

            @Override public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                                                                      boolean focused, int row, int column) {
                var entry = tableModel.entries.get(source.convertRowIndexToModel(row));
                icon.setIcon(ContentIcons.local(contentPaths.get(entry), source));
                title.setForeground(source.getForeground());
                title.setText(displayNames.get(entry.fileName()));
                filename.setText(entry.fileName());
                return cell;
            }
        });
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setFont(table.getFont());
        table.getTableHeader().setPreferredSize(new Dimension(1, 34));
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(300);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Object payload = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (payload instanceof List<?> files && files.stream().allMatch(File.class::isInstance)) {
                        return addFiles(files.stream().map(File.class::cast).toList());
                    }
                } catch (java.awt.datatransfer.UnsupportedFlavorException | IOException error) {
                    JOptionPane.showMessageDialog(InstancePage.this, error.getMessage());
                }
                return false;
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0), LauncherStyle.outline(10)));
        JPanel tableInset = new JPanel(new BorderLayout());
        tableInset.setOpaque(false);
        tableInset.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        tableInset.add(scroll);
        contentBody.add(tableInset, "table");
        contentBody.add(emptyContent(table.getTransferHandler()), "empty");
        area.add(contentBody, BorderLayout.CENTER);
        JPanel footer = contentDropFooter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        footer.setOpaque(false);
        footer.setLayout(new FlowLayout(FlowLayout.CENTER, 8, 10));
        footer.setBorder(BorderFactory.createDashedBorder(LauncherStyle.MUTED, 1, 5, 4, true));
        footer.setTransferHandler(table.getTransferHandler());
        JLabel dropLabel = new JLabel("Drop files to add more content");
        dropLabel.setTransferHandler(table.getTransferHandler());
        dropLabel.setFont(table.getFont());
        footer.add(dropLabel);
        JPanel footerInset = new JPanel(new BorderLayout());
        footerInset.setOpaque(false);
        footerInset.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        footerInset.add(footer);
        area.add(footerInset, BorderLayout.SOUTH);
        this.refreshTable();
        return area;
    }

    private JPanel emptyContent(TransferHandler dropHandler) {
        JPanel outer = LauncherStyle.card(new java.awt.GridBagLayout(), 18);
        outer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(LauncherStyle.EDGE, 1, 6, 4, true),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        JLabel box = new JLabel(new javax.swing.Icon() {
            public int getIconWidth() { return 76; }
            public int getIconHeight() { return 66; }
            public void paintIcon(Component component, java.awt.Graphics graphics, int x, int y) {
                var g = (java.awt.Graphics2D) graphics.create();
                g.translate(x, y);
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND,
                        java.awt.BasicStroke.JOIN_ROUND));
                g.setColor(LauncherStyle.MUTED);
                g.drawPolygon(new int[]{13, 38, 63, 38}, new int[]{27, 18, 27, 38}, 4);
                g.drawPolyline(new int[]{13, 13, 38, 63, 63}, new int[]{27, 50, 62, 50, 27}, 5);
                g.drawLine(38, 38, 38, 62);
                g.setColor(LauncherStyle.ACCENT);
                g.drawLine(38, 3, 38, 11);
                g.drawLine(25, 6, 29, 11);
                g.drawLine(51, 6, 47, 11);
                g.dispose();
            }
        });
        box.setAlignmentX(CENTER_ALIGNMENT);
        actions.add(box);
        actions.add(Box.createVerticalStrut(10));
        JLabel title = new JLabel("No content added yet");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 35f));
        title.setAlignmentX(CENTER_ALIGNMENT);
        actions.add(title);
        JLabel hint = new JLabel("Drop mods, resource packs, or shaders here");
        hint.setForeground(LauncherStyle.MUTED);
        hint.setAlignmentX(CENTER_ALIGNMENT);
        actions.add(Box.createVerticalStrut(8));
        actions.add(hint);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttons.setOpaque(false);
        buttons.add(LauncherStyle.button("Choose files", true, this::chooseFiles));
        actions.add(Box.createVerticalStrut(18));
        actions.add(buttons);
        outer.add(actions);
        outer.setTransferHandler(dropHandler);
        actions.setTransferHandler(dropHandler);
        return outer;
    }

    private void browseMods() {
        if (explore == null) {
            explore = new ExplorePanel(installation, this::refreshTable, () -> screens.show(workspace, "instance"));
            workspace.add(explore, "explore");
        } else {
            explore.refreshInstalled();
        }
        screens.show(workspace, "explore");
    }

    private void chooseFiles() {
        statusLabel.setText("Drag files from File Explorer onto the content area.");
        ExplorerFiles.open(statusLabel::setText);
    }

    private boolean addFiles(List<File> files) {
        boolean added = false;
        StringBuilder errors = new StringBuilder();
        for (File file : files) {
            try {
                Path destination = contentDestination(file);
                Files.createDirectories(destination.getParent());
                String filename = destination.getFileName().toString();
                int extension = filename.lastIndexOf('.');
                for (int copy = 1; ; copy++) {
                    Path candidate = copy == 1 ? destination : destination.resolveSibling(
                            filename.substring(0, extension) + " (" + copy + ")" + filename.substring(extension));
                    if (Files.exists(candidate.resolveSibling(candidate.getFileName() + ".disabled"))) continue;
                    try {
                        Files.copy(file.toPath(), candidate);
                        break;
                    } catch (java.nio.file.FileAlreadyExistsException ignored) {
                        // A manual upload keeps both archives.
                    }
                }
                added = true;
            } catch (IOException error) {
                errors.append(file.getName()).append(" — ").append(error.getMessage()).append('\n');
            }
        }
        refreshTable();
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(this, errors.toString(), "Some files could not be added",
                    JOptionPane.WARNING_MESSAGE);
        }
        return added;
    }

    private Path contentDestination(File file) throws IOException {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (!file.isFile() || (!lower.endsWith(".jar") && !lower.endsWith(".zip"))) {
            throw new IOException("Choose a mod JAR or a resource pack or shader ZIP.");
        }
        try (var archive = new java.util.zip.ZipFile(file)) {
            if (lower.endsWith(".jar")) {
                if (java.util.stream.Stream.of("fabric.mod.json", "quilt.mod.json",
                        "META-INF/mods.toml", "META-INF/neoforge.mods.toml", "mcmod.info")
                        .noneMatch(name -> archive.getEntry(name) != null)) {
                    throw new IOException("No supported mod metadata was found in the JAR.");
                }
                return installation.modsDirectory().resolve(file.getName());
            }
            if (archive.stream().anyMatch(entry -> entry.getName().startsWith("shaders/"))) {
                return installation.shaderPacksDirectory().resolve(file.getName());
            }
            if (archive.getEntry("pack.mcmeta") != null &&
                    archive.stream().anyMatch(entry -> entry.getName().startsWith("assets/"))) {
                return installation.resourcePacksDirectory().resolve(file.getName());
            }
            throw new IOException("The ZIP does not appear to be a resource pack or shader pack.");
        }
    }

    private Path contentFolder(InstalledContentScanner.Category category) {
        return switch (category) {
            case MOD -> installation.modsDirectory();
            case TEXTURE_PACK -> installation.resourcePacksDirectory();
            case SHADER -> installation.shaderPacksDirectory();
            case OTHER -> null;
        };
    }

    private Path contentPath(Path folder, InstalledContentScanner.ContentEntry entry) {
        Path normal = folder.resolve(entry.fileName());
        if (entry.enabled() || Files.exists(normal)) return normal;
        return folder.resolve(".krovex-disabled").resolve(entry.fileName());
    }

    private void toggleContent(int row) {
        var entry = tableModel.entries.get(row);
        Path folder = contentFolder(entry.category());
        if (folder == null) return;
        String name = entry.fileName();
        String suffix = ".disabled";
        String newName = entry.enabled() ? name + suffix : name.substring(0, name.length() - suffix.length());
        try {
            Path source = contentPath(folder, entry);
            Path target = folder.resolve(newName);
            if (entry.enabled() && Files.isDirectory(source)) {
                if (Files.exists(target)) throw new IOException("An item with that disabled name already exists.");
                Path hidden = folder.resolve(".krovex-disabled");
                Files.createDirectories(hidden);
                target = hidden.resolve(newName);
            }
            Files.move(source, target);
            ContentAction.animate(contentTable, newName, !entry.enabled());
            refreshTable();
        } catch (IOException error) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "Could not change content state",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteContent(int row) {
        var entry = tableModel.entries.get(row);
        Path folder = contentFolder(entry.category());
        if (folder == null) return;
        Path file = contentPath(folder, entry);
        showDeletePrompt("Move " + entry.fileName() + " to the Recycle Bin?", null, () -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
                        || !Desktop.getDesktop().moveToTrash(file.toFile())) {
                    statusLabel.setText("Windows could not move this item to the Recycle Bin.");
                    return;
                }
                if (explore != null) explore.contentDeleted(file);
                ContentIcons.refreshLocal(folder);
                refreshTable();
            } catch (RuntimeException error) {
                statusLabel.setText("Could not delete content: " + error.getMessage());
            }
        });
    }

    private JToggleButton filterTab(String label, InstalledContentScanner.Category category, ButtonGroup group, boolean selected) {
        JToggleButton tab = new JToggleButton(label) {
            @Override protected void paintComponent(java.awt.Graphics graphics) {
                var g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(isSelected() ? LauncherStyle.ACCENT : LauncherStyle.SURFACE);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 13, 13);
                g.dispose();
                setForeground(isSelected() ? Color.BLACK : LauncherStyle.INK);
                super.paintComponent(graphics);
            }
        };
        tab.setContentAreaFilled(false);
        tab.setBorder(BorderFactory.createCompoundBorder(LauncherStyle.outline(13),
                BorderFactory.createEmptyBorder(6, 13, 6, 13)));
        tab.setSelected(selected);
        tab.setFocusPainted(false);
        tab.addActionListener(e -> {
            this.activeFilter = category;
            filterContent();
        });
        group.add(tab);
        return tab;
    }

    private void refreshTable() {
        List<InstalledContentScanner.ContentEntry> all = new java.util.ArrayList<>(scanner.scan(installation));
        for (var category : InstalledContentScanner.Category.values()) {
            Path folder = contentFolder(category);
            if (folder == null) continue;
            Path disabled = folder.resolve(".krovex-disabled");
            if (!Files.isDirectory(disabled)) continue;
            try (var children = Files.list(disabled)) {
                children.filter(Files::isDirectory).forEach(path -> {
                    String name = path.getFileName().toString();
                    if (name.endsWith(".disabled") && !Files.exists(folder.resolve(name))) {
                        all.add(new InstalledContentScanner.ContentEntry(name, category, 0, false));
                    }
                });
            } catch (IOException ignored) {
                // The rest of the instance remains available if this directory cannot be read.
            }
        }
        all.sort(java.util.Comparator.comparing(InstalledContentScanner.ContentEntry::fileName,
                String.CASE_INSENSITIVE_ORDER));
        scannedContent = List.copyOf(all);
        contentPaths.clear();
        displayNames.clear();
        for (var entry : scannedContent) {
            Path folder = contentFolder(entry.category());
            if (folder != null) contentPaths.put(entry, contentPath(folder, entry));
            String name = entry.fileName();
            String title = name.replaceFirst("(?i)\\.(jar|zip)(\\.disabled)?$", "")
                    .replaceFirst("[-_ ]v?\\d.*$", "").replaceAll("[-_]+", " ").strip();
            displayNames.put(name, title.isEmpty() ? name : title);
        }
        filterContent();
    }

    private void filterContent() {
        if (tableModel == null) return;
        var filtered = scanner.filter(scannedContent, activeFilter, searchField.getText());
        if (!tableModel.entries.equals(filtered)) tableModel.setEntries(filtered);
        contentLayout.show(contentBody, scannedContent.isEmpty() ? "empty" : "table");
        if (contentDropFooter != null) contentDropFooter.setVisible(!scannedContent.isEmpty());
    }

    private static final class SimpleDocListener
    implements DocumentListener {
        private final Runnable onChange;

        SimpleDocListener(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            this.onChange.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            this.onChange.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            this.onChange.run();
        }
    }

    private static final class ContentTableModel
    extends AbstractTableModel {
        private List<InstalledContentScanner.ContentEntry> entries = List.of();
        private static final String[] COLUMNS = new String[]{"File", "Type", "Size", "Enabled", "Delete"};

        private ContentTableModel() {
        }

        void setEntries(List<InstalledContentScanner.ContentEntry> entries) {
            this.entries = entries;
            this.fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return this.entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column >= 3 && entries.get(row).category() != InstalledContentScanner.Category.OTHER;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            InstalledContentScanner.ContentEntry entry = this.entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.fileName();
                case 1 -> this.labelFor(entry.category());
                case 2 -> this.humanSize(entry.sizeBytes());
                case 3 -> entry.enabled();
                default -> "";
            };
        }

        private String labelFor(InstalledContentScanner.Category c) {
            return switch (c) {
                default -> throw new MatchException(null, null);
                case MOD -> "Mod";
                case TEXTURE_PACK -> "Texture Pack";
                case SHADER -> "Shader";
                case OTHER -> "Other";
            };
        }

        private String humanSize(long bytes) {
            if (bytes <= 0L) {
                return "--";
            }
            if (bytes < 1024L) {
                return bytes + " B";
            }
            if (bytes < 0x100000L) {
                return bytes / 1024L + " KB";
            }
            return bytes / 0x100000L + " MB";
        }
    }
}
