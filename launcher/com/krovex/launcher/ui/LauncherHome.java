package com.krovex.launcher.ui;

import com.krovex.launcher.model.Installation;
import com.krovex.launcher.ui.components.InstanceLogo;
import com.krovex.theme.KrovexTheme;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class LauncherHome extends JScrollPane {
    private final JPanel library = new JPanel(new GridLayout(0, 2, 12, 12));
    private JPanel libraryHeader;
    private final JTextField search = new JTextField(12);
    private final JComboBox<String> loader = new JComboBox<>(
            new String[]{"All loaders", "Vanilla", "Fabric", "Quilt", "Forge", "NeoForge"});
    private final JComboBox<String> sort = new JComboBox<>(new String[]{"Name", "Last played"});

    LauncherHome(List<Installation> instances, KrovexTheme theme, Consumer<Installation> open,
                 Consumer<Installation> play, Runnable create, Installation primary, boolean compact) {
        int columns = compact ? 1 : 2;
        library.setLayout(new GridLayout(0, columns, 12, 12));
        JPanel page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBackground(LauncherStyle.BASE);
        page.setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        setViewportView(page);
        setBorder(null);
        getVerticalScrollBar().setUnitIncrement(20);

        Installation featured = primary != null ? primary : instances.stream()
                .max(Comparator.comparingLong(Installation::lastPlayedEpochMs)).orElse(null);
        JPanel welcome = new LaunchBanner(320, .88f);
        JPanel heroText = new JPanel();
        heroText.setOpaque(false);
        heroText.setLayout(new BoxLayout(heroText, BoxLayout.Y_AXIS));

        heroText.add(label("<html>Minecraft<br>on KROVEX</html>", 43, LauncherStyle.INK));
        heroText.add(Box.createVerticalStrut(6));
        heroText.add(label(featured == null ? "Create your first Minecraft instance"
                : (primary == null ? "" : "Primary  ·  ") + featured.name() + "  ·  " + featured.loaderDisplayLabel() + " " + featured.minecraftVersion(),
                15, LauncherStyle.MUTED));
        heroText.add(Box.createVerticalStrut(17));
        JButton heroButton = LauncherStyle.button(featured == null ? "+  New instance" : "Launch game",
                true, featured == null ? create : () -> play.accept(featured));
        heroButton.setFont(heroButton.getFont().deriveFont(17f));
        heroButton.setAlignmentX(LEFT_ALIGNMENT);
        JPanel launchActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        launchActions.setOpaque(false);
        launchActions.setAlignmentX(LEFT_ALIGNMENT);
        launchActions.add(heroButton);
        if (featured != null) launchActions.add(LauncherStyle.button("Instance details", false, () -> open.accept(featured)));
        heroText.add(launchActions);
        welcome.add(heroText, BorderLayout.CENTER);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.setAlignmentX(LEFT_ALIGNMENT);
        heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 59));
        heading.add(label("Launchpad", 38, LauncherStyle.INK), BorderLayout.NORTH);
        heading.add(label("Your Minecraft hub on KROVEX", 16, LauncherStyle.MUTED), BorderLayout.SOUTH);
        page.add(heading);
        page.add(Box.createVerticalStrut(8));
        page.add(welcome);
        page.add(Box.createVerticalStrut(22));

        List<Installation> recent = instances.stream().filter(i -> i.lastPlayedEpochMs() > 0)
                .sorted(Comparator.comparingLong(Installation::lastPlayedEpochMs).reversed())
                .limit(2).toList();
        if (!recent.isEmpty()) {
            page.add(label("Recently played", 19, LauncherStyle.INK));
            page.add(Box.createVerticalStrut(8));
            JPanel recentRow = new JPanel(new GridLayout(0, 2, 10, 8));
            recentRow.setOpaque(false);
            recentRow.setAlignmentX(LEFT_ALIGNMENT);
            recent.forEach(instance -> recentRow.add(card(instance, theme, open, play, true)));
            recentRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, ((recent.size() + 1) / 2) * 70));
            page.add(recentRow);
            page.add(Box.createVerticalStrut(14));
        }
        libraryHeader = new JPanel(new BorderLayout());
        libraryHeader.setOpaque(false);
        libraryHeader.setAlignmentX(LEFT_ALIGNMENT);
        libraryHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        libraryHeader.add(label("Instance library", 23, LauncherStyle.INK), BorderLayout.WEST);
        if (featured != null) {
            libraryHeader.add(LauncherStyle.button("+  New instance", true, create), BorderLayout.EAST);
        }
        page.add(libraryHeader);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        tools.setOpaque(false);
        tools.setAlignmentX(LEFT_ALIGNMENT);
        tools.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        JLabel prompt = label("Search", 14, LauncherStyle.MUTED);
        prompt.setLabelFor(search);
        search.getAccessibleContext().setAccessibleName("Search instances");
        tools.add(prompt);
        tools.add(search);
        tools.add(loader);
        tools.add(sort);
        libraryHeader.add(tools, BorderLayout.CENTER);
        library.setOpaque(false);
        library.setAlignmentX(LEFT_ALIGNMENT);
        page.add(library);
        page.add(Box.createVerticalGlue());

        Runnable refresh = () -> {
            library.removeAll();
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            String selected = (String) loader.getSelectedItem();
            Comparator<Installation> order = sort.getSelectedIndex() == 0
                    ? Comparator.comparing(Installation::name, String.CASE_INSENSITIVE_ORDER)
                    : Comparator.comparingLong(Installation::lastPlayedEpochMs).reversed();
            instances.stream().filter(i -> i.name().toLowerCase(Locale.ROOT).contains(query))
                    .filter(i -> loader.getSelectedIndex() == 0 || i.loader().name().equalsIgnoreCase(selected))
                    .sorted(order).forEach(i -> library.add(card(i, theme, open, play, false)));
            if (library.getComponentCount() == 0) {
                library.add(label(instances.isEmpty() ? "Create an instance to get started." : "No matching instances.",
                        15, LauncherStyle.MUTED));
            }
            library.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    ((library.getComponentCount() + columns - 1) / columns) * (compact ? 103 : 116)));
            library.revalidate();
            library.repaint();
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { refresh.run(); }
            public void removeUpdate(DocumentEvent event) { refresh.run(); }
            public void changedUpdate(DocumentEvent event) { refresh.run(); }
        });
        loader.addActionListener(event -> refresh.run());
        sort.addActionListener(event -> refresh.run());
        refresh.run();
    }

    void showInstances() {
        SwingUtilities.invokeLater(() -> {
            if (libraryHeader != null) ((JComponent) getViewport().getView()).scrollRectToVisible(
                    new Rectangle(libraryHeader.getX(), libraryHeader.getY(), libraryHeader.getWidth(),
                            getViewport().getHeight()));
            search.requestFocusInWindow();
        });
    }

    private static JPanel card(Installation instance, KrovexTheme theme, Consumer<Installation> open,
                               Consumer<Installation> play, boolean recent) {
        JPanel card = LauncherStyle.card(new BorderLayout(10, 0), 10);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        Icon art = LaunchBanner.thumbnail(recent ? 45 : 135, recent ? 42 : 88, instance.id().hashCode());
        JLabel icon = new JLabel(art == null ? new InstanceLogo(instance, theme, 40) : art);
        card.add(icon, BorderLayout.WEST);
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        JButton name = new JButton(instance.name());
        name.setHorizontalAlignment(SwingConstants.LEFT);
        name.setBorder(BorderFactory.createEmptyBorder());
        name.setContentAreaFilled(false);
        name.setFocusPainted(false);
        name.setForeground(LauncherStyle.INK);
        name.setFont(name.getFont().deriveFont(Font.BOLD, recent ? 14f : 16f));
        name.addActionListener(event -> open.accept(instance));
        text.add(name);
        String detail = instance.loaderDisplayLabel() + "  /  " + instance.minecraftVersion();
        if (recent) {
            detail += "  /  " + DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(instance.lastPlayedEpochMs()));
        }
        text.add(label(detail, 12, LauncherStyle.MUTED));
        var openCard = new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) open.accept(instance);
            }
        };
        for (Component part : new Component[]{card, icon, text, text.getComponent(1)}) {
            part.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            part.addMouseListener(openCard);
        }
        name.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        name.setToolTipText("Open " + instance.name());
        card.add(text, BorderLayout.CENTER);
        card.add(LauncherStyle.button(recent ? "▶" : "▶  Launch", !recent, () -> play.accept(instance)), BorderLayout.EAST);
        return card;
    }

    private static JLabel label(String value, int size, Color color) {
        JLabel label = new JLabel(value);
        label.setFont(new Font("Segoe UI", size >= 19 ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }
}
