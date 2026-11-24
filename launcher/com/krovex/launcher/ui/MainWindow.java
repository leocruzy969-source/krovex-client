package com.krovex.launcher.ui;

import com.krovex.launcher.auth.AccountManager;
import com.krovex.launcher.model.AccountStore;
import com.krovex.launcher.model.Installation;
import com.krovex.launcher.model.InstallationStore;
import com.krovex.launcher.network.LoaderCatalog;
import com.krovex.launcher.network.VersionCatalogService;
import com.krovex.launcher.ui.SwingThemeUtil;
import com.krovex.launcher.ui.components.AccountSidebarPanel;
import com.krovex.launcher.ui.screens.CreateInstanceDialog;
import com.krovex.launcher.ui.screens.InstancePage;
import com.krovex.theme.KrovexTheme;
import com.krovex.theme.ThemeStore;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class MainWindow
extends JFrame {
    private final Path krovexHome;
    private final InstallationStore installationStore;
    private final AccountStore accountStore;
    private final ThemeStore themeStore;
    private final AccountManager accountManager;
    private final VersionCatalogService versionCatalog = new VersionCatalogService();
    private final LoaderCatalog loaderCatalog = new LoaderCatalog();
    private JPanel centerContainer;
    private AccountSidebarPanel accountSidebar;
    private JPanel rail;
    private float veil;
    private javax.swing.Timer veilTimer;
    private final LauncherPreferences preferences;
    private Installation activeInstallation;
    private String primaryInstanceId;
    private InstancePage activeInstancePage;

    public MainWindow(Path krovexHome, InstallationStore installationStore, AccountStore accountStore, ThemeStore themeStore) {
        super("Krovex Client");
        this.krovexHome = krovexHome;
        this.preferences = new LauncherPreferences(krovexHome);
        this.installationStore = installationStore;
        this.accountStore = accountStore;
        this.themeStore = themeStore;
        this.accountManager = new AccountManager(krovexHome, com.krovex.launcher.auth.MicrosoftRegistration.clientId(krovexHome));
        this.setDefaultCloseOperation(3);
        try {
            primaryInstanceId = java.nio.file.Files.readString(krovexHome.resolve("primary-instance.txt")).strip();
        } catch (java.io.IOException ignored) { }
        loadAccent();
        LauncherStyle.install();
        setIconImages(java.util.stream.IntStream.of(16, 24, 32, 48, 64, 128, 256)
                .mapToObj(size -> {
                    var image = new java.awt.image.BufferedImage(size, size,
                            java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    MockupChrome.icon("brand", size).paintIcon(this, graphics, 0, 0);
                    graphics.dispose();
                    return (java.awt.Image) image;
                }).toList());
        // every other window the launcher opens (dialogs, pickers, message boxes) gets the K too, never Java's default icon
        java.util.List<java.awt.Image> brandIcons = getIconImages();
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == java.awt.event.WindowEvent.WINDOW_OPENED && event.getSource() instanceof java.awt.Window window
                    && window != this) window.setIconImages(brandIcons);
        }, java.awt.AWTEvent.WINDOW_EVENT_MASK);
        this.setSize(1380, 865);
        this.setMinimumSize(new Dimension(960, 640));
        this.setLocationRelativeTo(null);
        KrovexTheme theme = themeStore.getActiveTheme();
        this.getContentPane().setBackground(SwingThemeUtil.background(theme));
        this.setLayout(new BorderLayout());
        JComponent chrome = WindowChrome.install(this);
        JPanel top = new JPanel(new BorderLayout());
        top.add(chrome, BorderLayout.NORTH);
        top.add(this.brandingBar(theme), BorderLayout.CENTER);
        this.add((Component)top, "North");
        this.centerContainer = new JPanel(new BorderLayout()) {
            @Override protected void paintChildren(java.awt.Graphics graphics) {
                super.paintChildren(graphics);
                if (veil > 0.01f) {
                    graphics.setColor(new java.awt.Color(13, 14, 17, Math.round(255 * veil)));
                    graphics.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };
        this.centerContainer.setBackground(SwingThemeUtil.background(theme));
        this.add((Component)this.centerContainer, "Center");
        this.accountSidebar = new AccountSidebarPanel(theme, this.accountManager, this::onAccountChanged);
        this.add((Component)this.accountSidebar, "East");
        refreshNavigation();
        this.showLibrary();
    }

    private static final java.util.Map<String, java.awt.Color> ACCENTS = java.util.Map.of(
            "Lime", new java.awt.Color(181, 245, 62),
            "Mint", new java.awt.Color(89, 227, 192),
            "Violet", new java.awt.Color(190, 155, 255),
            "Ice", new java.awt.Color(116, 198, 255));

    private Path appearanceFile() {
        return krovexHome.resolve("launcher-accent.txt");
    }

    private void loadAccent() {
        String name = "Lime";
        try {
            name = java.nio.file.Files.readString(appearanceFile()).strip();
        } catch (java.io.IOException ignored) { }
        LauncherStyle.ACCENT = ACCENTS.getOrDefault(name, ACCENTS.get("Lime"));
    }

    private JPanel brandingBar(KrovexTheme theme) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(LauncherStyle.BASE);
        bar.setBorder(BorderFactory.createEmptyBorder(14, 20, 12, 20));
        JLabel brand = new JLabel("KROVEX");
        brand.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 18));
        brand.setForeground(SwingThemeUtil.text(theme));
        JPanel identity = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
        identity.setOpaque(false);
        JLabel mark = new JLabel(MockupChrome.icon("brand", 38));
        identity.add(mark);
        identity.add(brand);
        JLabel product = new JLabel("Minecraft Launcher");
        product.setForeground(LauncherStyle.MUTED);
        identity.add(product);
        // The sidebar carries the brand so the workspace header stays clear.
        JButton accounts = LauncherStyle.button("Accounts  ▾", false, () -> {
            accountSidebar.setVisible(!accountSidebar.isVisible());
            revalidate();
        });
        bar.add(accounts, BorderLayout.EAST);
        return bar;
    }

    private JPanel navigation() {
        JPanel rail = new JPanel();
        rail.setBackground(new java.awt.Color(18, 19, 23));
        rail.setPreferredSize(new Dimension(204, 0));
        rail.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 0));
        rail.setLayout(new javax.swing.BoxLayout(rail, javax.swing.BoxLayout.Y_AXIS));
        JButton home = LauncherControls.flatNav("Home", MockupChrome.icon("home", 19), this::showLibrary);
        JButton instances = LauncherControls.flatNav("Instances", MockupChrome.icon("box", 19), () -> {
            showLibrary();
            if (centerContainer.getComponent(0) instanceof LauncherHome library) library.showInstances();
        });
        JButton create = LauncherStyle.button("+   New instance", false, this::openCreateInstanceDialog);
        create.setToolTipText("Create an instance");
        JButton settings = LauncherControls.flatNav("Settings", null, this::showAppearance);
        settings.setToolTipText("Launcher settings");
        settings.getAccessibleContext().setAccessibleName("Launcher settings");
        for (JButton button : new JButton[]{home, instances, create, settings}) {
            button.setMaximumSize(new Dimension(188, 44));
            button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            button.setAlignmentX(CENTER_ALIGNMENT);
        }
        identityForRail(rail);
        rail.add(home);
        rail.add(javax.swing.Box.createVerticalStrut(10));
        rail.add(instances);
        rail.add(javax.swing.Box.createVerticalStrut(8));
        rail.add(create);
        rail.add(javax.swing.Box.createVerticalStrut(8));
        rail.add(settings);
        int limit = Math.max(0, Math.min(20, preferences.integer("sidebar.quick", 0)));
        if (limit > 0) {
            JPanel shortcuts = new JPanel();
            shortcuts.setLayout(new javax.swing.BoxLayout(shortcuts, javax.swing.BoxLayout.Y_AXIS));
            shortcuts.setBackground(LauncherStyle.BASE);
            for (Installation instance : installationStore.recent(limit)) {
                JButton shortcut = LauncherStyle.button(instance.name(), false, () -> openInstance(instance));
                shortcut.setToolTipText(instance.name());
                shortcut.setMaximumSize(new Dimension(94, 36));
                shortcuts.add(shortcut);
                shortcuts.add(javax.swing.Box.createVerticalStrut(4));
            }
            javax.swing.JScrollPane quick = new javax.swing.JScrollPane(shortcuts);
            quick.setBorder(null);
            quick.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            quick.setMaximumSize(new Dimension(94, 300));
            rail.add(javax.swing.Box.createVerticalStrut(8));
            rail.add(quick);
        }
        rail.add(javax.swing.Box.createVerticalGlue());
        if (preferences.flag("sidebar.screenshots", false)) {
            JButton shots = LauncherStyle.button("Screenshots", false, this::showScreenshots);
            shots.setFont(shots.getFont().deriveFont(11f));
            shots.setAlignmentX(CENTER_ALIGNMENT);
            rail.add(shots);
            rail.add(javax.swing.Box.createVerticalStrut(6));
        }
        return rail;
    }

    private void identityForRail(JPanel rail) {
        JLabel identity = new JLabel("KROVEX", MockupChrome.icon("brand", 42), JLabel.LEFT);
        identity.setFont(identity.getFont().deriveFont(java.awt.Font.BOLD, 25f));
        identity.setAlignmentX(CENTER_ALIGNMENT);
        identity.setBorder(BorderFactory.createEmptyBorder(4, 0, 23, 0));
        rail.add(identity);
    }

    private void showAppearance() {
        if (centerContainer.getComponentCount() == 0
                || centerContainer.getComponent(0) instanceof LauncherSettings) return;
        JComponent previous = (JComponent) centerContainer.getComponent(0);
        boolean[] changed = {false};
        setCenter(new LauncherSettings(preferences, krovexHome, ACCENTS, this::setAccent,
                () -> accountManager.activeAccount().orElse(null), () -> {
                    changed[0] = true;
                    refreshNavigation();
                }, () -> {
                    if (!changed[0]) setCenter(previous);
                    else if (activeInstallation == null) showLibrary();
                    else openInstance(activeInstallation);
                }));
    }

    private void setAccent(String name) {
        try {
            java.nio.file.Files.createDirectories(krovexHome);
            java.nio.file.Files.writeString(appearanceFile(), name);
            loadAccent();
            LauncherStyle.install();
            repaint();
        } catch (java.io.IOException error) {
            javax.swing.JOptionPane.showMessageDialog(this, error.getMessage(), "Appearance",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshNavigation() {
        if (rail != null) remove(rail);
        rail = navigation();
        add(rail, BorderLayout.WEST);
        accountSidebar.setVisible(!preferences.flag("sidebar.hide", true));
        revalidate();
        repaint();
    }

    private void showScreenshots() {
        JPanel list = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        list.setBackground(LauncherStyle.BASE);
        list.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        list.add(new JLabel("Instance screenshots"));
        for (Installation instance : installationStore.all()) {
            list.add(LauncherStyle.button(instance.name(), false, () -> {
                Path folder = instance.gameDirectory().resolve("screenshots");
                try {
                    java.nio.file.Files.createDirectories(folder);
                    java.awt.Desktop.getDesktop().open(folder.toFile());
                } catch (java.io.IOException | UnsupportedOperationException error) {
                    javax.swing.JOptionPane.showMessageDialog(this, error.getMessage());
                }
            }));
        }
        JPanel page = new JPanel(new BorderLayout());
        page.add(list, BorderLayout.NORTH);
        setCenter(new javax.swing.JScrollPane(page));
    }

    private void onAccountChanged() {
    }

    private void showLibrary() {
        this.activeInstallation = null;
        this.activeInstancePage = null;
        KrovexTheme theme = this.themeStore.getActiveTheme();
        LauncherHome library = new LauncherHome(this.installationStore.all(), theme, this::openInstance, this::playInstanceFromLibrary, this::openCreateInstanceDialog,
                primaryInstanceId == null ? null : installationStore.findById(primaryInstanceId).orElse(null),
                preferences.flag("home.compact", false));
        this.setCenter(library);
    }

    private void playInstanceFromLibrary(Installation installation) {
        this.openInstance(installation);
        if (this.activeInstancePage != null) {
            this.activeInstancePage.triggerPlay();
        }
    }

    private void openInstance(Installation installation) {
        InstancePage page;
        this.activeInstallation = installation;
        KrovexTheme theme = this.themeStore.getActiveTheme();
        this.activeInstancePage = page = new InstancePage(installation, theme, this.installationStore, this.themeStore, this.krovexHome, () -> this.accountManager.activeAccount().orElse(null), this::refreshLibraryIfShown, deleted -> {
            this.activeInstallation = null;
            this.showLibrary();
        }, preferences);
        page.setBackAction(this::showLibrary);
        page.setPrimaryAction(() -> installation.id().equals(primaryInstanceId), () -> togglePrimary(installation));
        this.setCenter(page);
    }

    private void togglePrimary(Installation instance) {
        String next = instance.id().equals(primaryInstanceId) ? "" : instance.id();
        try {
            java.nio.file.Files.createDirectories(krovexHome);
            java.nio.file.Files.writeString(krovexHome.resolve("primary-instance.txt"), next);
            primaryInstanceId = next.isEmpty() ? null : next;
        } catch (java.io.IOException error) {
            javax.swing.JOptionPane.showMessageDialog(this, error.getMessage(), "Primary instance",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshLibraryIfShown() {
        if (this.activeInstallation == null) {
            this.showLibrary();
        }
    }

    private void setCenter(JComponent component) {
        this.centerContainer.removeAll();
        this.centerContainer.add((Component)component, "Center");
        this.centerContainer.revalidate();
        this.centerContainer.repaint();
        veil = 0.85f;
        if (veilTimer != null) veilTimer.stop();
        veilTimer = new javax.swing.Timer(12, event -> {
            veil *= 0.80f;
            if (veil < 0.02f) { veil = 0; ((javax.swing.Timer) event.getSource()).stop(); }
            centerContainer.repaint();
        });
        veilTimer.start();
    }

    private void openCreateInstanceDialog() {
        KrovexTheme theme = this.themeStore.getActiveTheme();
        CreateInstanceDialog dialog = new CreateInstanceDialog(this, theme, this.installationStore, this.versionCatalog, this.loaderCatalog, created -> {
            this.openInstance(created);
        });
        dialog.setDefaults(preferences::applyDefaults);
        dialog.setVisible(true);
    }
}




