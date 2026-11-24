package com.krovex.launcher.ui.screens;

import com.krovex.launcher.model.Installation;
import com.krovex.launcher.model.InstallationStore;
import com.krovex.launcher.model.MinecraftVersion;
import com.krovex.launcher.network.LoaderCatalog;
import com.krovex.launcher.network.VersionCatalogService;
import com.krovex.launcher.ui.SwingThemeUtil;
import com.krovex.launcher.ui.LauncherStyle;
import com.krovex.launcher.ui.components.InstanceLogo;
import com.krovex.theme.KrovexTheme;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.SwingWorker;

public final class CreateInstanceDialog
extends JDialog {
    private final KrovexTheme theme;
    private final InstallationStore installationStore;
    private final VersionCatalogService versionCatalog;
    private final LoaderCatalog loaderCatalog;
    private final Consumer<Installation> onCreated;
    private Consumer<Installation> defaults = installation -> {};
    private boolean importing;
    private JLabel dialogTitle;
    private JPanel importCard;
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private String selectedLogoId = InstanceLogo.BUILTIN_LOGO_IDS[0];
    private JTextField nameField;
    private JLabel previewName;
    private JLabel previewMetadata;
    private JPanel previewArt;
    private final Color[] swatchColors = {new Color(145, 30, 222), new Color(34, 136, 244),
            new Color(255, 63, 67), new Color(16, 197, 83), new Color(255, 174, 0), new Color(126, 133, 150)};
    private Installation.Loader selectedLoader = Installation.Loader.FABRIC;
    private JComboBox<MinecraftVersion> versionCombo;
    private JCheckBox showSnapshots;
    private JCheckBox showHistorical;
    private JLabel compatibilityLabel;
    private JLabel clientSupportWarning;
    private JButton createButton;
    private boolean acknowledgedClientWarning;
    private List<MinecraftVersion> allVersions = List.of();

    public CreateInstanceDialog(Frame owner, KrovexTheme theme, InstallationStore installationStore, VersionCatalogService versionCatalog, LoaderCatalog loaderCatalog, Consumer<Installation> onCreated) {
        super(owner, "Create instance", true);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setUndecorated(true);
        getRootPane().setBorder(LauncherStyle.outline(14));
        getRootPane().registerKeyboardAction(event -> closeWhenIdle(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        this.theme = theme;
        this.installationStore = installationStore;
        this.versionCatalog = versionCatalog;
        this.loaderCatalog = loaderCatalog;
        this.onCreated = onCreated;
        this.getContentPane().setBackground(SwingThemeUtil.background(theme));
        this.setLayout(new BorderLayout());
        com.krovex.launcher.ui.MockupChrome.dimOwner(this);
        this.setSize(675, 368);
        this.setLocationRelativeTo(owner);
        this.cardLayout = new CardLayout();
        this.cardPanel = new JPanel(this.cardLayout);
        this.cardPanel.setOpaque(false);
        this.cardPanel.add((Component)this.choicePanel(), "choice");
        this.cardPanel.add((Component)this.customInstancePanel(), "custom");
        this.add((Component)this.cardPanel, "Center");
        JPanel heading = new JPanel(new BorderLayout(10, 0));
        heading.setBackground(LauncherStyle.SURFACE);
        heading.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 14));
        JLabel title = dialogTitle = typography("Create instance", 29, true, LauncherStyle.INK);
        title.setIcon(com.krovex.launcher.ui.MockupChrome.icon("brand", 44));
        title.setIconTextGap(15);
        heading.add(title, BorderLayout.WEST);
        JButton close = LauncherStyle.button("\u2715", false, this::closeWhenIdle);
        close.setPreferredSize(new Dimension(44, 44));
        close.getAccessibleContext().setAccessibleName("Close create instance");
        heading.add(close, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);
    }

    public void setDefaults(Consumer<Installation> defaults) { this.defaults = defaults; }

    private void showStep(String card, int height) {
        cardLayout.show(cardPanel, card);
        dialogTitle.setText(card.startsWith("import") ? "Import modpack" : "Create instance");
        var screen = getOwner().getGraphicsConfiguration().getBounds();
        setSize(Math.min(card.equals("custom") ? 1158 : card.equals("importPreview") ? 860 : 675, screen.width - 50),
                Math.min(height, screen.height - 60));
        setLocationRelativeTo(getOwner());
    }

    private JPanel choicePanel() {
        JPanel outer = new JPanel();
        outer.setOpaque(false);
        outer.setLayout(new BoxLayout(outer, 1));
        outer.setBorder(BorderFactory.createEmptyBorder(14, 22, 18, 22));
        outer.add(typography("Choose how to create your Minecraft instance.", 15, false, LauncherStyle.MUTED));
        outer.add(Box.createVerticalStrut(13));
        outer.add(choiceRow("Custom setup", "Choose a Minecraft version, loader, and instance icon.",
                () -> showStep("custom", 810)));
        outer.add(Box.createVerticalStrut(12));
        outer.add(choiceRow("Import a modpack", "Open a .mrpack or .krovexpack archive.",
                this::handleImportModpackClicked));
        outer.add(Box.createVerticalGlue());
        return outer;
    }

    private JPanel choiceRow(String title, String subtitle, Runnable onClick) {
        boolean primary = title.equals("Custom setup");
        boolean[] hover = {false};
        JPanel row = new JPanel(new BorderLayout(18, 0)) {
            @Override protected void paintComponent(java.awt.Graphics graphics) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(hover[0] ? new Color(32, 34, 43) : new Color(26, 28, 35));
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
                g.setColor(primary || hover[0] ? LauncherStyle.ACCENT : LauncherStyle.EDGE);
                if (primary && !hover[0]) g.setColor(new Color(LauncherStyle.ACCENT.getRed(), LauncherStyle.ACCENT.getGreen(), LauncherStyle.ACCENT.getBlue(), 150));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
                g.dispose();
            }
        };
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 20));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 99));
        // icon on a rounded tile: a lime square for a fresh setup, an arrow for an import
        JLabel symbol = new JLabel() {
            @Override protected void paintComponent(java.awt.Graphics graphics) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(primary ? new Color(46, 56, 34) : new Color(38, 40, 48));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g.setStroke(new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2, cy = getHeight() / 2;
                if (primary) {
                    g.setColor(LauncherStyle.ACCENT);
                    g.drawRoundRect(cx - 9, cy - 9, 18, 18, 6, 6);
                } else {
                    g.setColor(LauncherStyle.INK);
                    g.drawLine(cx, cy - 9, cx, cy + 8);
                    g.drawPolyline(new int[] {cx - 6, cx, cx + 6}, new int[] {cy + 2, cy + 8, cy + 2}, 3);
                }
                g.dispose();
            }
        };
        symbol.setPreferredSize(new Dimension(56, 56));
        JPanel tile = new JPanel(new java.awt.GridBagLayout());
        tile.setOpaque(false);
        tile.add(symbol);
        row.add(tile, BorderLayout.WEST);
        JPanel words = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        words.setOpaque(false);
        JButton button = new JButton(title);
        button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setForeground(LauncherStyle.INK);
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setFont(button.getFont().deriveFont(java.awt.Font.BOLD, 21f));
        button.addActionListener(event -> onClick.run());
        words.add(button);
        JLabel detail = typography(subtitle, 14, false, LauncherStyle.MUTED);
        words.add(detail);
        JPanel centred = new JPanel(new java.awt.GridBagLayout());
        centred.setOpaque(false);
        java.awt.GridBagConstraints fill = new java.awt.GridBagConstraints();
        fill.weightx = 1; fill.fill = java.awt.GridBagConstraints.HORIZONTAL;
        centred.add(words, fill);
        row.add(centred, BorderLayout.CENTER);
        JLabel arrow = typography("\u203A", 26, false, LauncherStyle.MUTED);
        row.add(arrow, BorderLayout.EAST);
        var activate = new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (javax.swing.SwingUtilities.isLeftMouseButton(event)) onClick.run();
            }
            @Override public void mouseEntered(java.awt.event.MouseEvent event) { hover[0] = true; row.repaint(); }
            @Override public void mouseExited(java.awt.event.MouseEvent event) {
                java.awt.Point p = javax.swing.SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), row);
                hover[0] = row.contains(p);
                row.repaint();
            }
        };
        for (Component part : new Component[]{row, symbol, tile, words, centred, detail, arrow, button}) part.addMouseListener(activate);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return row;
    }
    private void handleImportModpackClicked() {
        JPanel target = LauncherStyle.card(new BorderLayout(0, 14), 24);
        JLabel hint = new JLabel("Drag one .mrpack or .krovexpack from File Explorer into this window.");
        target.add(hint, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        buttons.add(LauncherStyle.button("Back", false, () -> showStep("choice", 368)));
        buttons.add(LauncherStyle.button("Open File Explorer", true, () -> ExplorerFiles.open(hint::setText)));
        target.add(buttons, BorderLayout.SOUTH);
        var drop = ExplorerFiles.drop(files -> {
            if (files.size() != 1 || !files.getFirst().isFile()
                    || !files.getFirst().getName().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mrpack|krovexpack)")) {
                hint.setText("Drop one .mrpack or .krovexpack file.");
                return;
            }
            setImportCard(new ImportModpackPanel(files.getFirst().toPath(), installationStore,
                    () -> showStep("choice", 368), created -> {
                        onCreated.accept(created);
                        dispose();
                    }, busy -> importing = busy), "importPreview", 690);
        });
        target.setTransferHandler(drop);
        hint.setTransferHandler(drop);
        setImportCard(target, "importFiles", 300);
        ExplorerFiles.open(hint::setText);
    }

    private void setImportCard(JPanel panel, String key, int height) {
        if (importCard != null) cardPanel.remove(importCard);
        importCard = panel;
        cardPanel.add(panel, key);
        showStep(key, height);
    }

    private void closeWhenIdle() {
        if (!importing) dispose();
    }

    private JPanel customInstancePanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 22, 16, 22));
        panel.add(sectionLabel("Name (optional)"));
        nameField = new JTextField() {
            @Override protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                if (!getText().isEmpty()) return;
                g.setColor(LauncherStyle.MUTED);
                g.drawString(defaultName(), getInsets().left, (getHeight() + g.getFontMetrics().getAscent()) / 2 - 2);
            }
        };
        styleField(nameField);
        panel.add(Box.createVerticalStrut(8));
        panel.add(nameField);
        addDivider(panel);
        panel.add(sectionLabel("Instance color"));
        panel.add(Box.createVerticalStrut(8));
        panel.add(logoRow());
        addDivider(panel);
        panel.add(this.sectionLabel("Loader"));
        JPanel loaderRow = new JPanel(new java.awt.GridLayout(1, 0, 8, 0));
        loaderRow.setOpaque(false);
        loaderRow.setAlignmentX(LEFT_ALIGNMENT);
        loaderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
        ButtonGroup loaderGroup = new ButtonGroup();
        for (Installation.Loader loader : Installation.Loader.values()) {
            JToggleButton button = choiceButton(this.loaderLabel(loader));
            button.setSelected(loader == this.selectedLoader);
            button.setFocusPainted(false);
            button.addActionListener(e -> {
                this.selectedLoader = loader;
                resetClientWarning();
                updatePreview();
                this.refreshVersionFilter();
                this.checkCompatibilityAsync();
            });
            loaderGroup.add(button);
            loaderRow.add(button);
        }
        panel.add(Box.createVerticalStrut(5));
        panel.add(loaderRow);
        addDivider(panel);
        panel.add(this.sectionLabel("Game version"));
        this.versionCombo = new JComboBox<>();
        this.styleField(this.versionCombo);
        panel.add(Box.createVerticalStrut(5));
        panel.add(this.versionCombo);
        addDivider(panel);
        JPanel filters = new JPanel();
        filters.setLayout(new BoxLayout(filters, BoxLayout.Y_AXIS));
        filters.setOpaque(false);
        filters.setAlignmentX(LEFT_ALIGNMENT);
        filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 61));
        this.showSnapshots = this.checkbox("Show snapshots");
        this.showHistorical = this.checkbox("Show historical versions");
        filters.add(this.showSnapshots);
        filters.add(this.showHistorical);
        panel.add(filters);
        panel.add(Box.createVerticalStrut(12));
        this.compatibilityLabel = new JLabel(" ");
        this.compatibilityLabel.setForeground(SwingThemeUtil.subtleText(this.theme));
        this.compatibilityLabel.setFont(SwingThemeUtil.baseFont(this.theme, 0));
        this.compatibilityLabel.setAlignmentX(0.0f);
        panel.add(this.compatibilityLabel);
        clientSupportWarning = typography(" ", 14, true, LauncherStyle.MUTED);
        panel.add(clientSupportWarning);
        panel.add(Box.createVerticalGlue());
        JButton jButton = LauncherStyle.button("←  Back", false, () -> showStep("choice", 368));
        createButton = LauncherStyle.button("Create instance", true, this::handleCreate);
        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(0.0f);
        buttonRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, LauncherStyle.EDGE),
                BorderFactory.createEmptyBorder(16, 22, 16, 22)));
        buttonRow.add(jButton, BorderLayout.WEST);
        buttonRow.add(createButton, BorderLayout.EAST);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        this.showSnapshots.addActionListener(e -> this.refreshVersionFilter());
        this.showHistorical.addActionListener(e -> this.refreshVersionFilter());
        JPanel preview = previewPanel();
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { updatePreview(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { updatePreview(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { updatePreview(); }
        });
        versionCombo.addActionListener(e -> {
            resetClientWarning();
            updatePreview();
            checkCompatibilityAsync();
        });
        this.allVersions = versionCatalog.knownVersions();
        this.refreshVersionFilter();
        this.loadVersionsAsync();
        updatePreview();
        JPanel layout = new JPanel(new BorderLayout());
        layout.setBackground(LauncherStyle.BASE);
        var form = new javax.swing.JScrollPane(panel);
        form.setBorder(null);
        form.getViewport().setBackground(LauncherStyle.BASE);
        form.getVerticalScrollBar().setUnitIncrement(16);
        layout.add(form, BorderLayout.CENTER);
        layout.add(preview, BorderLayout.EAST);
        layout.add(buttonRow, BorderLayout.SOUTH);
        return layout;
    }

    private void addDivider(JPanel panel) {
        panel.add(Box.createVerticalStrut(15));
        var divider = new javax.swing.JSeparator();
        divider.setForeground(LauncherStyle.EDGE);
        divider.setAlignmentX(LEFT_ALIGNMENT);
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        panel.add(divider);
        panel.add(Box.createVerticalStrut(15));
    }

    private String versionName() {
        return versionCombo.getSelectedItem() instanceof MinecraftVersion version ? version.id() : "";
    }

    private String defaultName() {
        String version = versionName();
        return version.isEmpty() ? "Minecraft" : "Minecraft " + version;
    }

    private void updatePreview() {
        if (previewName == null) return;
        String name = nameField.getText().strip();
        previewName.setText(name.isEmpty() ? defaultName() : name);
        previewMetadata.setText(defaultName() + "    │    " + loaderLabel(selectedLoader));
        previewArt.repaint();
        nameField.repaint();
    }

    private JPanel previewPanel() {
        JPanel side = new JPanel();
        side.setBackground(LauncherStyle.BASE);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(405, 0));
        side.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, LauncherStyle.EDGE),
                BorderFactory.createEmptyBorder(21, 23, 21, 23)));
        side.add(typography("Live preview", 25, true, LauncherStyle.INK));
        side.add(typography("This is how your instance will look in Krovex.", 15, false, LauncherStyle.MUTED));
        side.add(Box.createVerticalStrut(22));
        JPanel card = LauncherStyle.card(new BorderLayout(), 0);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 335));
        previewArt = new JPanel() {
            @Override protected void paintComponent(java.awt.Graphics graphics) {
                var g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth(), height = getHeight();
                int index = java.util.Arrays.asList(InstanceLogo.BUILTIN_LOGO_IDS).indexOf(selectedLogoId);
                Color color = swatchColors[Math.max(0, index)];
                g.setPaint(new java.awt.GradientPaint(0, 0, color.darker().darker(), width, height, LauncherStyle.BASE));
                g.fillRect(0, 0, width, height);
                g.setColor(new Color(255, 255, 255, 26));
                for (int x = 0; x < width; x += 22) g.drawLine(x, 0, x, height);
                for (int y = 0; y < height; y += 22) g.drawLine(0, y, width, y);
                g.translate(width / 2, height / 2);
                g.scale(1.7, 1.7);
                g.setColor(color.brighter());
                g.fillPolygon(new int[]{-44, 0, 44, 0}, new int[]{-25, -50, -25, 0}, 4);
                g.setColor(color);
                g.fillPolygon(new int[]{-44, 0, 0, -44}, new int[]{-25, 0, 53, 27}, 4);
                g.setColor(color.darker());
                g.fillPolygon(new int[]{0, 44, 44, 0}, new int[]{0, -25, 27, 53}, 4);
                g.setColor(Color.WHITE);
                g.setFont(getFont().deriveFont(java.awt.Font.BOLD, 32f));
                g.shear(0, -.58);
                g.drawString("K", 9, 36);
                g.dispose();
            }
        };
        previewArt.setPreferredSize(new Dimension(0, 220));
        card.add(previewArt, BorderLayout.CENTER);
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setBorder(BorderFactory.createEmptyBorder(14, 18, 16, 18));
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        previewName = typography("", 24, true, LauncherStyle.INK);
        previewMetadata = typography("", 15, false, LauncherStyle.MUTED);
        details.add(previewName);
        details.add(Box.createVerticalStrut(8));
        details.add(previewMetadata);
        card.add(details, BorderLayout.SOUTH);
        side.add(card);
        return side;
    }

    private String loaderLabel(Installation.Loader loader) {
        return switch (loader) {
            default -> throw new MatchException(null, null);
            case Installation.Loader.VANILLA -> "Vanilla";
            case Installation.Loader.FABRIC -> "Fabric";
            case Installation.Loader.QUILT -> "Quilt";
            case Installation.Loader.FORGE -> "Forge";
            case Installation.Loader.NEOFORGE -> "NeoForge";
        };
    }

    private JLabel sectionLabel(String text) {
        return typography(text, 18, true, LauncherStyle.INK);
    }

    private JLabel typography(String text, int size, boolean bold, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new java.awt.Font("Segoe UI", bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN, size));
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JToggleButton choiceButton(String text) {
        JToggleButton button = new JToggleButton(text) {
            @Override protected void paintComponent(java.awt.Graphics graphics) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(isSelected() ? new Color(33, 64, 67) : LauncherStyle.SURFACE);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 11, 11);
                if (isSelected()) {
                    g.setColor(LauncherStyle.ACCENT);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 11, 11);
                }
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setFont(button.getFont().deriveFont(java.awt.Font.BOLD));
        button.setBorder(BorderFactory.createCompoundBorder(LauncherStyle.outline(11),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        if (!text.isEmpty()) {
            Dimension preferred = button.getPreferredSize();
            int textWidth = button.getFontMetrics(button.getFont()).stringWidth(text);
            button.setPreferredSize(new Dimension(Math.max(preferred.width, textWidth + 34), 32));
        }
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JCheckBox checkbox(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setForeground(SwingThemeUtil.text(this.theme));
        box.setFont(SwingThemeUtil.baseFont(this.theme, 0));
        return box;
    }

    private void styleField(JComponent field) {
        field.setBackground(SwingThemeUtil.panel(this.theme));
        field.setForeground(SwingThemeUtil.text(this.theme));
        field.setAlignmentX(0.0f);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
    }

    private JPanel logoRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 1));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 61));
        ButtonGroup group = new ButtonGroup();
        for (String logoId : InstanceLogo.BUILTIN_LOGO_IDS) {
            JToggleButton swatch = choiceButton("");
            int index = java.util.Arrays.asList(InstanceLogo.BUILTIN_LOGO_IDS).indexOf(logoId);
            swatch.setPreferredSize(new Dimension(55, 55));
            swatch.setBorder(BorderFactory.createEmptyBorder());
            swatch.setIcon(new javax.swing.Icon() {
                public int getIconWidth() { return 55; }
                public int getIconHeight() { return 55; }
                public void paintIcon(Component c, java.awt.Graphics graphics, int x, int y) {
                    var g = (java.awt.Graphics2D) graphics.create();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(swatchColors[index]);
                    g.fillRoundRect(x + 2, y + 2, 51, 51, 13, 13);
                    if (swatch.isSelected()) {
                        g.setColor(LauncherStyle.ACCENT);
                        g.setStroke(new java.awt.BasicStroke(3));
                        g.drawRoundRect(x + 2, y + 2, 51, 51, 13, 13);
                        g.setColor(Color.WHITE);
                        g.drawPolyline(new int[]{x + 17, x + 24, x + 38}, new int[]{y + 28, y + 35, y + 21}, 3);
                    }
                    g.dispose();
                }
            });
            swatch.getAccessibleContext().setAccessibleName(logoId + " instance color");
            swatch.setSelected(logoId.equals(this.selectedLogoId));
            swatch.setToolTipText("Icon " + logoId);
            swatch.setFocusPainted(false);
            swatch.addActionListener(e -> {
                this.selectedLogoId = logoId;
                updatePreview();
            });
            group.add(swatch);
            row.add(swatch);
        }
        return row;
    }

    private ListCellRenderer<MinecraftVersion> versionRenderer() {
        return (list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.id());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            label.setForeground(LauncherStyle.INK);
            label.setBackground(isSelected ? new Color(33, 64, 67) : LauncherStyle.SURFACE);
            return label;
        };
    }

    private void loadVersionsAsync() {
        this.compatibilityLabel.setText("Loading Minecraft versions\u2026");
        new SwingWorker<List<MinecraftVersion>, Void>(){

            @Override
            protected List<MinecraftVersion> doInBackground() {
                return CreateInstanceDialog.this.versionCatalog.fetchOrFallback();
            }

            @Override
            protected void done() {
                try {
                    CreateInstanceDialog.this.allVersions = this.get();
                    if (CreateInstanceDialog.this.versionCatalog.isUsingFallback()) {
                        CreateInstanceDialog.this.compatibilityLabel.setText("Offline -- showing a short list of known versions.");
                    } else {
                        CreateInstanceDialog.this.compatibilityLabel.setText(" ");
                    }
                    CreateInstanceDialog.this.refreshVersionFilter();
                }
                catch (Exception ignored) {
                    CreateInstanceDialog.this.compatibilityLabel.setText("Could not load the version list.");
                }
            }
        }.execute();
    }

    private void refreshVersionFilter() {
        MinecraftVersion previouslySelected = (MinecraftVersion)this.versionCombo.getSelectedItem();
        this.versionCombo.removeAllItems();
        for (MinecraftVersion v : this.allVersions) {
            if (!v.isRelease() && (!v.isSnapshot() || !this.showSnapshots.isSelected()) && (!v.isHistorical() || !this.showHistorical.isSelected())) continue;
            this.versionCombo.addItem(v);
        }
        if (previouslySelected != null) {
            for (int i = 0; i < this.versionCombo.getItemCount(); ++i) {
                if (!this.versionCombo.getItemAt(i).id().equals(previouslySelected.id())) continue;
                this.versionCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    private void checkCompatibilityAsync() {
        final MinecraftVersion version = (MinecraftVersion)this.versionCombo.getSelectedItem();
        final Installation.Loader loader = this.selectedLoader;
        if (version == null) {
            return;
        }
        if (loader == Installation.Loader.VANILLA) {
            this.compatibilityLabel.setText(" ");
            return;
        }
        this.compatibilityLabel.setText("Checking " + loader.name() + " compatibility\u2026");
        new SwingWorker<Boolean, Void>(){

            @Override
            protected Boolean doInBackground() throws Exception {
                return CreateInstanceDialog.this.loaderCatalog.isSupported(loader, version.id());
            }

            @Override
            protected void done() {
                try {
                    boolean supported = this.get();
                    if (selectedLoader != loader || !version.equals(versionCombo.getSelectedItem())) return;
                    if (!supported) {
                        CreateInstanceDialog.this.compatibilityLabel.setText(loader.name() + " has no published build for Minecraft " + version.id() + ".");
                    } else if (!CreateInstanceDialog.this.loaderCatalog.isLaunchable(loader)) {
                        CreateInstanceDialog.this.compatibilityLabel.setText(loader.name() + " builds exist for this version, but Krovex can't launch " + loader.name() + " yet (see README) -- the instance can still be created.");
                    } else {
                        CreateInstanceDialog.this.compatibilityLabel.setText(" ");
                    }
                }
                catch (Exception e) {
                    if (selectedLoader != loader || !version.equals(versionCombo.getSelectedItem())) return;
                    CreateInstanceDialog.this.compatibilityLabel.setText("Could not verify compatibility (offline?) -- you can still create this instance.");
                }
            }
        }.execute();
    }

    private void resetClientWarning() {
        acknowledgedClientWarning = false;
        if (clientSupportWarning != null) clientSupportWarning.setText(" ");
        if (createButton != null) createButton.setText("Create instance");
    }

    private void handleCreate() {
        MinecraftVersion version = (MinecraftVersion)this.versionCombo.getSelectedItem();
        if (version == null) {
            JOptionPane.showMessageDialog(this, "Pick a Minecraft version first.");
            return;
        }
        if (!com.krovex.launcher.launch.ManagedClientMod.supports(version.id(), selectedLoader)
                && !acknowledgedClientWarning) {
            clientSupportWarning.setText("<html>Krovex’s in-game features currently require Minecraft 1.21.11 with Fabric."
                    + "<br>You can still create this instance and play without those features.</html>");
            createButton.setText("Create without Krovex features");
            acknowledgedClientWarning = true;
            return;
        }
        String typedName = this.nameField.getText().trim();
        String name = typedName.isEmpty() ? "Minecraft " + version.id() : typedName;
        Installation installation = this.installationStore.create(name, version.id(), this.selectedLoader);
        defaults.accept(installation);
        installation.setLogoId(this.selectedLogoId);
        this.installationStore.save();
        this.onCreated.accept(installation);
        this.dispose();
    }
}




