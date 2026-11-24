package com.krovex.launcher.ui.screens;

import com.krovex.launcher.ui.LauncherStyle;
import java.awt.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

final class ProjectDetailsView extends JPanel {
    record Description(String text, boolean html, List<String> tags, List<String> loaders, List<String> images) {}
    record Version(String title, String release, String compatibility) {}

    private final JTextPane description = document();
    private final JPanel reading = new ReadingPanel();
    private final JTextPane versions = document();
    private final JTextArea details = new JTextArea(7, 16);
    private final JButton install;
    private final JLabel status = new JLabel(" ");

    ProjectDetailsView(String title, String subtitle, String summary, String image, String instance,
                       String version, String loader, Runnable back, Runnable download) {
        super(new BorderLayout(12, 12));
        setBackground(LauncherStyle.BASE);
        JPanel header = LauncherStyle.card(new BorderLayout(12, 0), 12);
        JLabel logo = new JLabel() {
            @Override public Icon getIcon() { return ContentIcons.remote(image, this); }
        };
        logo.setPreferredSize(new Dimension(46, 46));
        header.add(logo, BorderLayout.WEST);
        JPanel caption = new JPanel(new GridLayout(3, 1, 0, 3));
        caption.setOpaque(false);
        JLabel heading = plain(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 23f));
        caption.add(heading);
        caption.add(plain(subtitle));
        JLabel synopsis = plain(summary);
        synopsis.setForeground(LauncherStyle.MUTED);
        caption.add(synopsis);
        header.add(caption, BorderLayout.CENTER);
        header.add(LauncherStyle.button("Back to results", false, back), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        var tabs = new JTabbedPane();
        reading.setBackground(LauncherStyle.SURFACE);
        reading.add(description, BorderLayout.CENTER);
        tabs.addTab("Description", scroll(reading));
        tabs.addTab("Versions", scroll(versions));
        add(tabs, BorderLayout.CENTER);

        JPanel rail = new JPanel();
        rail.setBackground(LauncherStyle.BASE);
        rail.setPreferredSize(new Dimension(186, 180));
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        JPanel destination = LauncherStyle.card(new GridLayout(0, 1, 0, 5), 10);
        destination.add(plain("Install destination"));
        destination.add(plain(instance));
        destination.add(plain(version + "  ·  " + loader));
        destination.setMaximumSize(new Dimension(Integer.MAX_VALUE, destination.getPreferredSize().height));
        destination.setAlignmentX(LEFT_ALIGNMENT);
        rail.add(destination);
        rail.add(Box.createVerticalStrut(10));
        install = LauncherStyle.button("Install", true, download);
        install.setAlignmentX(LEFT_ALIGNMENT);
        rail.add(install);
        rail.add(Box.createVerticalStrut(8));
        status.setAlignmentX(LEFT_ALIGNMENT);
        rail.add(status);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setForeground(LauncherStyle.MUTED);
        details.setBackground(LauncherStyle.BASE);
        details.setFont(UIManager.getFont("Label.font"));
        details.setAlignmentX(LEFT_ALIGNMENT);
        rail.add(details);
        rail.add(Box.createVerticalGlue());
        add(rail, BorderLayout.EAST);

        showDescription(new Description(summary + "\n\nLoading project description…", false, List.of(), List.of(), List.of()));
        showVersions(List.of(), "Loading compatible versions…");
    }

    void installation(boolean available, boolean installed, String message) {
        install.setEnabled(available && !installed);
        install.setText(installed ? "✓ Installed" : "Install");
        status.setText(message);
        status.setToolTipText(message);
    }

    void showDescription(Description project) {
        description.setText("");
        String text = project.html() ? htmlText(project.text()) : project.text();
        for (String line : text.split("\\R", -1)) {
            String value = line.stripTrailing();
            boolean heading = value.matches("^#{1,6}\\s+.+");
            if (heading) value = value.replaceFirst("^#{1,6}\\s+", "");
            if (!project.html()) {
                value = value.replaceAll("!\\[([^]]*)]\\([^)]+\\)", "$1")
                        .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                        .replaceFirst("^[-*]\\s+", "• ");
            }
            append(description, value + "\n", heading);
        }
        description.setCaretPosition(0);
        Component old = ((BorderLayout) reading.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        if (old != null) reading.remove(old);
        if (!project.images().isEmpty()) {
            JPanel gallery = new JPanel();
            gallery.setLayout(new BoxLayout(gallery, BoxLayout.Y_AXIS));
            gallery.setBackground(LauncherStyle.SURFACE);
            gallery.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
            gallery.add(plain("Project previews"));
            for (String link : project.images()) {
                JLabel preview = new JLabel("Loading preview…");
                preview.setAlignmentX(LEFT_ALIGNMENT);
                gallery.add(Box.createVerticalStrut(8));
                gallery.add(preview);
                ContentIcons.loadPreview(link, preview);
            }
            reading.add(gallery, BorderLayout.SOUTH);
        }
        details.setText((project.loaders().isEmpty() ? "" : "Loaders\n" + String.join(", ", project.loaders()) + "\n\n")
                + (project.tags().isEmpty() ? "" : "Tags\n" + String.join(", ", project.tags())));
        reading.revalidate();
        reading.repaint();
    }

    void showVersions(List<Version> releases, String message) {
        versions.setText("");
        if (!message.isBlank()) append(versions, message + "\n", false);
        for (Version release : releases) {
            append(versions, release.title() + "\n", true);
            append(versions, release.release() + "  ·  " + release.compatibility() + "\n\n", false);
        }
        versions.setCaretPosition(0);
    }

    private static final class ReadingPanel extends JPanel implements Scrollable {
        ReadingPanel() { super(new BorderLayout(0, 10)); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(18, visible.height - 18);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static JScrollPane scroll(Component pane) {
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(LauncherStyle.outline(10));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private static JLabel plain(String text) {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", true);
        label.setText(text);
        return label;
    }

    private static JTextPane document() {
        var pane = new JTextPane();
        pane.setEditable(false);
        pane.setBackground(LauncherStyle.SURFACE);
        pane.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        return pane;
    }

    private static void append(JTextPane pane, String value, boolean heading) {
        var style = new SimpleAttributeSet();
        StyleConstants.setFontFamily(style, "Segoe UI");
        StyleConstants.setFontSize(style, heading ? 17 : 14);
        StyleConstants.setBold(style, heading);
        StyleConstants.setForeground(style, heading ? LauncherStyle.INK : LauncherStyle.MUTED);
        StyleConstants.setSpaceBelow(style, heading ? 5 : 2);
        StyledDocument document = pane.getStyledDocument();
        try {
            int start = document.getLength();
            document.insertString(start, value, style);
            document.setParagraphAttributes(start, value.length(), style, false);
        } catch (BadLocationException error) {
            throw new IllegalStateException(error);
        }
    }

    static String htmlText(String markup) {
        StringBuilder text = new StringBuilder();
        try {
            new ParserDelegator().parse(new StringReader(markup), new HTMLEditorKit.ParserCallback() {
                @Override public void handleText(char[] data, int pos) { text.append(data); }
                @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                    if (tag == HTML.Tag.LI) text.append("\n• ");
                    if (tag == HTML.Tag.H1 || tag == HTML.Tag.H2 || tag == HTML.Tag.H3) text.append("\n## ");
                }
                @Override public void handleEndTag(HTML.Tag tag, int pos) {
                    if (tag == HTML.Tag.P || tag == HTML.Tag.DIV || tag == HTML.Tag.LI
                            || tag == HTML.Tag.H1 || tag == HTML.Tag.H2 || tag == HTML.Tag.H3) text.append("\n");
                }
                @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                    if (tag == HTML.Tag.BR) text.append("\n");
                }
            }, true);
        } catch (IOException ignored) {
            return "";
        }
        return text.toString().strip();
    }
}

