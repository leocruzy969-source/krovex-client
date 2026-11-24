package com.krovex.launcher.ui;

import com.krovex.launcher.model.MicrosoftAccount;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;

final class LauncherSettings extends JPanel {
    private final LauncherPreferences prefs;
    private final Path home;
    private final Map<String, Color> accents;
    private final Consumer<String> setAccent;
    private final Supplier<MicrosoftAccount> account;
    private final Runnable changed;
    private final JPanel navigation = new JPanel();
    private final JPanel body = new JPanel(new BorderLayout());
    private final Map<String, Runnable> sections = new LinkedHashMap<>();
    private JPanel form;
    private JPanel header;

    LauncherSettings(LauncherPreferences prefs, Path home, Map<String, Color> accents,
                     Consumer<String> setAccent, Supplier<MicrosoftAccount> account,
                     Runnable changed, Runnable back) {
        super(new BorderLayout(14, 12));
        this.prefs = prefs;
        this.home = home;
        this.accents = accents;
        this.setAccent = setAccent;
        this.account = account;
        this.changed = changed;
        setBackground(LauncherStyle.BASE);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(38, 40, 48)),
                BorderFactory.createEmptyBorder(0, 0, 14, 0)));
        header.add(label("Launcher settings", 24, LauncherStyle.INK), BorderLayout.WEST);
        header.add(LauncherStyle.button("\u2190 Back", false, back), BorderLayout.EAST);
        this.header = header;
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setBackground(LauncherStyle.SURFACE);
        navigation.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        section("Appearance", this::appearance);
        section("Features", this::features);
        section("Behavior", this::behavior);
        section("Language", () -> row("English (United States)",
                "Krovex currently ships with English launcher text.", LauncherControls.checkBadge()));
        section("Profile", this::profile);
        section("Social", () -> row("Social services",
                "Friends, shared-instance invites, hosting invites, and blocked users need a Krovex account service.",
                LauncherControls.chip("Needs sign-in", new Color(48, 42, 28), new Color(232, 184, 80))));
        section("Privacy", () -> {
            row("Telemetry", "Krovex does not collect launcher analytics.",
                    LauncherControls.chip("Disabled", new Color(32, 44, 26), new Color(150, 220, 90)));
            row("Advertising", "Krovex does not serve ads or store advertising preferences.",
                    LauncherControls.chip("Disabled", new Color(32, 44, 26), new Color(150, 220, 90)));
            row("Discord Rich Presence", "Discord status sharing is currently unavailable.",
                    LauncherControls.chip("Unavailable", new Color(34, 36, 44), new Color(120, 124, 136)));
        });
        section("Java and memory", this::javaSettings);
        section("Game window", this::gameWindow);
        section("Launch hooks", this::launchHooks);
        JScrollPane rail = new JScrollPane(navigation);
        rail.setPreferredSize(new Dimension(177, 300));
        rail.setBorder(LauncherStyle.outline(12));
        rail.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(rail, BorderLayout.WEST);
        body.setBackground(LauncherStyle.BASE);
        add(body, BorderLayout.CENTER);
        showSection("Appearance");
    }

    private void section(String name, Runnable render) { sections.put(name, render); }

    private void showSection(String selected) {
        navigation.removeAll();
        for (var section : sections.entrySet()) {
            String name = section.getKey();
            String group = switch (name) { case "Appearance" -> "DISPLAY"; case "Profile" -> "ACCOUNT";
                case "Java and memory" -> "INSTANCE DEFAULTS"; default -> ""; };
            if (!group.isEmpty()) {
                navigation.add(Box.createVerticalStrut(9));
                navigation.add(label(group, 11, LauncherStyle.MUTED));
                navigation.add(Box.createVerticalStrut(6));
            }
            boolean active = name.equals(selected);
            Hover[] hover = new Hover[1];
            JButton button = new JButton(name) {
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (active) {
                        g.setColor(LauncherStyle.ACCENT);
                        g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    } else if (hover[0] != null && hover[0].amount() > 0.01f) {
                        g.setColor(new Color(36, 39, 47, Math.round(255 * hover[0].amount())));
                        g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    }
                    g.dispose();
                    setForeground(active ? new Color(15, 20, 10) : LauncherStyle.INK);
                    super.paintComponent(graphics);
                }
            };
            hover[0] = new Hover(button);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setFocusPainted(false);
            button.setRolloverEnabled(true);
            button.setFont(new Font("Segoe UI", active ? Font.BOLD : Font.PLAIN, 14));
            button.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 10));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(event -> showSection(name));
            button.setAlignmentX(LEFT_ALIGNMENT);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            navigation.add(button);
            navigation.add(Box.createVerticalStrut(4));
        }
        form = new JPanel();
        form.setBackground(LauncherStyle.BASE);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(8, 12, 16, 10));
        form.add(label(selected, 22, LauncherStyle.INK));
        form.add(Box.createVerticalStrut(14));
        sections.get(selected).run();
        form.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        body.removeAll();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 10));
        top.add(header, BorderLayout.CENTER);
        body.add(top, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void appearance() {
        JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        choices.setOpaque(false);
        for (String name : java.util.List.of("Lime", "Mint", "Violet", "Ice")) {
            boolean active = accents.get(name).equals(LauncherStyle.ACCENT);
            Color color = accents.get(name);
            JButton pill = new JButton(name) {
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(active ? LauncherControls.blend(LauncherStyle.SURFACE, color, 0.22f) : new Color(30, 32, 40));
                    g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                    g.setColor(active ? color : LauncherStyle.EDGE);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                    g.setColor(color);
                    g.fillOval(11, getHeight() / 2 - 6, 12, 12);
                    g.dispose();
                    super.paintComponent(graphics);
                }
            };
            pill.setContentAreaFilled(false);
            pill.setBorderPainted(false);
            pill.setFocusPainted(false);
            pill.setForeground(LauncherStyle.INK);
            pill.setFont(new Font("Segoe UI", Font.BOLD, 13));
            pill.setBorder(BorderFactory.createEmptyBorder(7, 32, 7, 16));
            pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            pill.addActionListener(event -> {
                setAccent.accept(name);
                showSection("Appearance");
            });
            choices.add(pill);
        }
        row("Accent color", "Used for primary actions and selected controls.", choices);
        information("Cross-device sync", "Appearance preferences are stored on this device.");
    }

    private void features() {
        toggle("Show Files tab", "Browse the instance directory.", "tabs.files", true);
        toggle("Show Worlds tab", "Open the instance’s saves folder.", "tabs.worlds", true);
        toggle("Show Screenshots tab", "Open the instance’s screenshots folder.", "tabs.screenshots", false);
        toggle("Screenshots shortcut", "Add a sidebar shortcut to screenshot folders for all instances.", "sidebar.screenshots", false);
        JSpinner quick = spinner(prefs.integer("sidebar.quick", 0), 0, 20, 1);
        quick.addChangeListener(event -> save("sidebar.quick", quick.getValue().toString()));
        row("Quick instances", "Maximum number of instance shortcuts in the sidebar.", quick);
        information("Skin selector", "Skin management is currently unavailable.");
    }

    private void behavior() {
        toggle("Minimize when Minecraft starts", "Keep the launcher out of the way while playing.", "behavior.minimize", false);
        toggle("Refocus when Minecraft closes", "Restore the launcher after a session ends.", "behavior.refocus", false);
        toggle("Hide account sidebar", "Use the Accounts button to show or hide the sidebar.", "sidebar.hide", false);
        toggle("Compact library", "Use a single-column instance list.", "home.compact", false);
        toggle("Show play time", "Display total play time in the instance header.", "instance.playtime", true);
    }

    private void profile() {
        MicrosoftAccount current = account.get();
        information("Minecraft account", current == null ? "No active Minecraft account." : current.minecraftUsername());
        Path avatar = home.resolve("launcher-profile.png");
        JLabel image = new JLabel();
        if (Files.isRegularFile(avatar)) image.setIcon(new ImageIcon(avatar.toString()));
        JPanel picture = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        picture.setOpaque(false);
        picture.add(image);
        JLabel imageDrop = new JLabel("Drop a PNG or JPEG here");
        picture.add(imageDrop);
        picture.add(LauncherStyle.button("Choose image", false,
                () -> com.krovex.launcher.ui.screens.ExplorerFiles.open(imageDrop::setText)));
        picture.setTransferHandler(com.krovex.launcher.ui.screens.ExplorerFiles.drop(files -> {
            if (files.size() != 1) {
                imageDrop.setText("Drop one PNG or JPEG.");
                return;
            }
            try {
                if (Files.size(files.getFirst().toPath()) > 5 * 1024 * 1024) throw new IOException("Choose an image under 5 MB.");
                try (var input = javax.imageio.ImageIO.createImageInputStream(files.getFirst())) {
                    var readers = javax.imageio.ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw new IOException("Choose a PNG or JPEG image.");
                    var reader = readers.next();
                    try {
                        reader.setInput(input);
                        if (reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) {
                            throw new IOException("Image dimensions must be 4096 pixels or smaller.");
                        }
                        java.awt.image.BufferedImage original = reader.read(0);
                        var thumbnail = new java.awt.image.BufferedImage(48, 48, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = thumbnail.createGraphics();
                        double scale = 48.0 / Math.max(original.getWidth(), original.getHeight());
                        int width = Math.max(1, (int) (original.getWidth() * scale));
                        int height = Math.max(1, (int) (original.getHeight() * scale));
                        graphics.drawImage(original, (48 - width) / 2, (48 - height) / 2, width, height, null);
                        graphics.dispose();
                        Files.createDirectories(home);
                        javax.imageio.ImageIO.write(thumbnail, "png", avatar.toFile());
                    } finally {
                        reader.dispose();
                    }
                }
                showSection("Profile");
            } catch (IOException error) {
                JOptionPane.showMessageDialog(this, error.getMessage(), "Profile picture", JOptionPane.WARNING_MESSAGE);
            }
        }));
        imageDrop.setTransferHandler(picture.getTransferHandler());
        image.setTransferHandler(picture.getTransferHandler());
        if (Files.isRegularFile(avatar)) picture.add(LauncherStyle.button("Remove image", false, () -> {
            try {
                Files.deleteIfExists(avatar);
                showSection("Profile");
            } catch (IOException error) {
                JOptionPane.showMessageDialog(this, error.getMessage());
            }
        }));
        row("Profile picture", "Visible in your local profile.", picture);
        JTextField name = LauncherControls.field(prefs.text("profile.name", ""), 18);
        JTextField bio = LauncherControls.field(prefs.text("profile.bio", ""), 22);
        row("Display name", "A local launcher profile name.", name);
        row("Bio", "A local launcher bio of up to 160 characters.", bio);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        actions.setOpaque(false);
        actions.add(LauncherStyle.button("Save profile", true, () -> {
            if (bio.getText().length() > 160) {
                JOptionPane.showMessageDialog(this, "Bio must be 160 characters or fewer.");
                return;
            }
            save(Map.of("profile.name", name.getText().strip(), "profile.bio", bio.getText()));
        }));
        actions.add(LauncherStyle.button("Refresh profile", false, () -> showSection("Profile")));
        row("Profile details", "Stored on this device.", actions);
    }

    private void javaSettings() {
        JTextField path = LauncherControls.field(prefs.text("java.path", ""), 19);
        JSpinner minimum = spinner(prefs.integer("memory.min", 512), 256, 65536, 256);
        JSpinner maximum = spinner(prefs.integer("memory.max", 4096), 256, 65536, 256);
        row("Java executable", "Leave blank to detect Java automatically.", path);
        row("Minimum memory (MB)", "For newly created instances.", minimum);
        row("Maximum memory (MB)", "For newly created instances.", maximum);
        row("", "", LauncherStyle.button("Save defaults", true, () -> {
            String executable = path.getText().strip();
            try {
                if (!executable.isEmpty() && (!Path.of(executable).isAbsolute() || !Files.isRegularFile(Path.of(executable)))) {
                    JOptionPane.showMessageDialog(this, "Enter an absolute path to the Java executable.");
                    return;
                }
            } catch (InvalidPathException error) {
                JOptionPane.showMessageDialog(this, "The Java path is invalid.");
                return;
            }
            if ((int) minimum.getValue() > (int) maximum.getValue()) {
                JOptionPane.showMessageDialog(this, "Minimum memory must be at most the maximum.");
                return;
            }
            save(Map.of("java.path", executable, "memory.min", minimum.getValue().toString(),
                    "memory.max", maximum.getValue().toString()));
        }));
    }

    private void gameWindow() {
        JSpinner width = spinner(prefs.integer("window.width", 1280), 320, 8192, 16);
        JSpinner height = spinner(prefs.integer("window.height", 720), 240, 8192, 16);
        JCheckBox full = LauncherControls.checkBox("Full screen", prefs.flag("window.fullscreen", false));
        row("Default width", "For newly created instances.", width);
        row("Default height", "For newly created instances.", height);
        row("Window mode", "", full);
        row("", "", LauncherStyle.button("Save defaults", true, () -> save(Map.of(
                "window.width", width.getValue().toString(), "window.height", height.getValue().toString(),
                "window.fullscreen", Boolean.toString(full.isSelected())))));
    }

    private void launchHooks() {
        JTextField before = LauncherControls.field(prefs.text("hook.before", ""), 21);
        JTextField after = LauncherControls.field(prefs.text("hook.after", ""), 21);
        row("Before launch", "Waits for this executable before starting Minecraft.", before);
        row("After exit", "Starts this executable when Minecraft closes.", after);
        row("", "Use absolute executable paths. Leave blank to disable a hook.",
                LauncherStyle.button("Save hooks", true, () -> {
                    String pre = before.getText().strip();
                    String post = after.getText().strip();
                    try {
                        for (String value : java.util.List.of(pre, post)) {
                            if (value.isBlank()) continue;
                            Path executable = Path.of(value);
                            if (!executable.isAbsolute() || !Files.isRegularFile(executable)) {
                                throw new IOException("Enter an absolute path to an existing executable.");
                            }
                        }
                        save(Map.of("hook.before", pre, "hook.after", post));
                    } catch (IOException | InvalidPathException error) {
                        JOptionPane.showMessageDialog(this, error.getMessage(), "Launch hooks", JOptionPane.WARNING_MESSAGE);
                    }
                }));
    }

    private void toggle(String title, String description, String key, boolean fallback) {
        JToggleButton toggle = LauncherControls.pillSwitch(prefs.flag(key, fallback));
        toggle.getAccessibleContext().setAccessibleName(title);
        toggle.addActionListener(event -> {
            boolean selected = toggle.isSelected();
            if (!save(key, Boolean.toString(selected))) toggle.setSelected(!selected);
        });
        row(title, description, toggle);
    }

    private boolean save(String key, String value) { return save(Map.of(key, value)); }
    private boolean save(Map<String, String> values) {
        Properties changes = new Properties();
        changes.putAll(values);
        try {
            prefs.save(changes);
            changed.run();
            return true;
        } catch (IOException error) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "Settings could not be saved", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static JSpinner spinner(int value, int minimum, int maximum, int step) {
        return LauncherControls.stepper(value, minimum, maximum, step);
    }

    private void information(String title, String description) { row(title, description, null); }

    private void row(String title, String description, JComponent control) {
        JPanel row = LauncherStyle.card(new BorderLayout(12, 0), 11);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JPanel text = new JPanel(new GridLayout(0, 1, 0, 4));
        text.setOpaque(false);
        if (!title.isBlank()) text.add(label(title, 15, LauncherStyle.INK));
        if (!description.isBlank()) {
            JLabel hint = label(description, 13, LauncherStyle.MUTED);
            hint.setToolTipText(description);
            text.add(hint);
        }
        row.add(text, BorderLayout.CENTER);
        if (control != null) {
            JPanel holder = new JPanel(new GridBagLayout());
            holder.setOpaque(false);
            holder.add(control);
            row.add(holder, BorderLayout.EAST);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(57, row.getPreferredSize().height)));
        form.add(row);
        form.add(Box.createVerticalStrut(8));
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", true);
        label.setText(text);
        label.setForeground(color);
        label.setFont(new Font("Segoe UI", size >= 15 ? Font.BOLD : Font.PLAIN, size));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }
}
