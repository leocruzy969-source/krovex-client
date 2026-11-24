package com.krovex.launcher.ui.screens;

import com.krovex.launcher.model.Installation;
import com.krovex.launcher.model.InstallationStore;
import com.krovex.launcher.modpack.ModpackImportService;
import com.krovex.launcher.modpack.ModpackPreview;
import com.krovex.launcher.ui.LauncherStyle;
import com.krovex.launcher.ui.MockupChrome;
import java.awt.*;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.*;

final class ImportModpackPanel extends JPanel {
    private final ModpackImportService service = new ModpackImportService();
    private final JLabel status = new JLabel("Reading modpack…");
    private final JButton confirm;
    private final JButton cancel;
    private ModpackPreview preview;

    ImportModpackPanel(Path archive, InstallationStore store, Runnable back, Consumer<Installation> imported,
                       Consumer<Boolean> busy) {
        super(new BorderLayout(0, 12));
        setBackground(LauncherStyle.BASE);
        setBorder(BorderFactory.createEmptyBorder(16, 26, 24, 26));
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(details);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(LauncherStyle.BASE);
        add(scroll);
        confirm = LauncherStyle.button("Import modpack", true, () -> {
            confirmEnabled(false);
            busy.accept(true);
            status.setText("Importing…");
            new SwingWorker<Installation, Integer>() {
                protected Installation doInBackground() throws Exception {
                    return service.importAsNewInstance(archive, preview, store,
                            (done, total) -> publish(done, total));
                }
                protected void process(java.util.List<Integer> progress) {
                    int end = progress.size();
                    status.setText("Downloading files " + progress.get(end - 2) + " / " + progress.get(end - 1));
                }
                protected void done() {
                    busy.accept(false);
                    try { imported.accept(get()); }
                    catch (Exception error) {
                        status.setText(message("Import failed", error));
                        confirmEnabled(true);
                    }
                }
            }.execute();
        });
        confirm.setIcon(MockupChrome.icon("download", 20));
        confirm.setEnabled(false);
        cancel = LauncherStyle.button("Cancel", false, back);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(confirm);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, LauncherStyle.EDGE),
                BorderFactory.createEmptyBorder(12, 0, 0, 0)));
        footer.add(status);
        footer.add(buttons, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        new SwingWorker<ModpackPreview, Void>() {
            protected ModpackPreview doInBackground() throws Exception { return service.preview(archive); }
            protected void done() {
                try {
                    preview = get();
                    render(details, archive);
                    status.setText(" ");
                    confirm.setEnabled(true);
                } catch (Exception error) { status.setText(message("Could not read modpack", error)); }
            }
        }.execute();
    }

    private void confirmEnabled(boolean enabled) {
        confirm.setEnabled(enabled);
        cancel.setEnabled(enabled);
    }

    private void render(JPanel details, Path archive) {
        JPanel hero = LauncherStyle.card(new BorderLayout(18, 0), 18);
        JLabel icon = new JLabel(ContentIcons.local(archive, hero)) {
            @Override public Icon getIcon() { return ContentIcons.local(archive, this); }
        };
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        icon.setOpaque(true);
        icon.setBackground(new Color(54, 24, 82));
        icon.setPreferredSize(new Dimension(105, 105));
        hero.add(icon, BorderLayout.WEST);
        JPanel metadata = new JPanel(new GridLayout(3, 1, 0, 3));
        metadata.setOpaque(false);
        metadata.add(text(preview.suggestedInstanceName(), 34, true));
        boolean modrinth = archive.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".mrpack");
        metadata.add(text(modrinth ? "Modrinth  ·  .mrpack" : "Krovex  ·  .krovexpack", 17, false));
        String loader = preview.loaderName();
        if (loader != null && !loader.isBlank()) {
            loader = loader.substring(0, 1) + loader.substring(1).toLowerCase(java.util.Locale.ROOT);
        }
        metadata.add(text("Minecraft " + java.util.Objects.toString(preview.minecraftVersion(), "version unknown")
                + "    │    " + java.util.Objects.toString(loader, "Loader unknown"), 17, true));
        hero.add(metadata);
        append(details, hero, 10);
        append(details, text("Contents", 27, true), 10);
        for (String summary : preview.contentsSummary()) {
            JPanel row = LauncherStyle.card(new BorderLayout(12, 0), 14);
            var count = java.util.regex.Pattern.compile("^(\\d+)\\s+(.*)$").matcher(summary);
            if (count.matches()) {
                String description = count.group(2);
                boolean download = description.contains("download");
                String title = description.startsWith("disabled") ? "Disabled files" : download ? "Files to download"
                        : description.contains("override") ? "Bundled overrides" : "Bundled files";
                JPanel labels = new JPanel(new GridLayout(2, 1, 0, 3));
                labels.setOpaque(false);
                labels.add(text(title, 20, true));
                labels.add(text(description, 14, false));
                row.add(new JLabel(MockupChrome.icon(download ? "download" : "box", 24)), BorderLayout.WEST);
                row.add(labels);
                row.add(text(java.text.NumberFormat.getIntegerInstance().format(Long.parseLong(count.group(1))), 25, true), BorderLayout.EAST);
            } else row.add(text(summary, 16, true));
            append(details, row, 9);
        }
        for (String warning : preview.warnings()) {
            JTextArea line = new JTextArea(warning);
            line.setEditable(false);
            line.setOpaque(false);
            line.setLineWrap(true);
            line.setWrapStyleWord(true);
            append(details, line, 5);
        }
        details.revalidate();
        details.repaint();
    }

    private static void append(JPanel container, JComponent row, int gap) {
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        container.add(row);
        container.add(Box.createVerticalStrut(gap));
    }

    private static JLabel text(String value, int size, boolean bold) {
        JLabel label = new JLabel(value);
        label.putClientProperty("html.disable", true);
        label.setFont(label.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size));
        label.setForeground(bold ? LauncherStyle.INK : LauncherStyle.MUTED);
        return label;
    }

    private static String message(String prefix, Exception error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        return prefix + " — " + cause.getMessage();
    }
}
