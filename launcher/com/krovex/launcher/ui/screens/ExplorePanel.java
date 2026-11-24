package com.krovex.launcher.ui.screens;

import com.krovex.launcher.download.DownloadManager;
import com.krovex.launcher.model.Installation;
import com.krovex.launcher.network.HttpJson;
import com.krovex.launcher.ui.LauncherStyle;
import com.krovex.launcher.ui.LaunchBanner;
import com.krovex.theme.json.Json;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.*;

final class ExplorePanel extends JPanel {
    private static final String MODRINTH = "https://api.modrinth.com/v2";
    private static final String CURSEFORGE = "https://api.curseforge.com/v1";
    private static final int PAGE_SIZE = 20;
    private final Installation instance;
    private final Runnable refresh;
    private final JTextField query = new JTextField();
    private final JComboBox<ContentType> category = new JComboBox<>(ContentType.values());
    private final JComboBox<String> provider = new JComboBox<>(new String[]{"Modrinth", "CurseForge", "Both"});
    private final JPanel results = new ResultsList();
    private final JScrollPane scroll = new JScrollPane(results);
    private final JLabel status = new JLabel(" ");
    private final JPanel paging = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    private final JButton search = LauncherStyle.button("Search", true, () -> search(0, query.getText().strip()));
    private final Map<Project, JButton> resultButtons = new java.util.LinkedHashMap<>();
    private final Map<Project, Path> installed = new java.util.HashMap<>();
    private int page;
    private int total;
    private boolean busy;
    private final CardLayout views = new CardLayout();
    private ProjectDetailsView detailView;
    private Project detailProject;
    private String appliedQuery = "";
    private record Project(String provider, String id, String slug, String title, String description, String icon, ContentType type, String author) {}
    private record Result(List<Project> projects, int total, String notice) {}
    private record Download(String filename, String url, String sha1) {}
    private record InstallResult(Path destination, String message) {}

    private enum ContentType {
        MOD("Mods", "mod", 6), RESOURCE("Resource packs", "resourcepack", 12), SHADER("Shaders", "shader", 6552);
        final String label;
        final String modrinthType;
        final int curseClass;
        ContentType(String label, String modrinthType, int curseClass) {
            this.label = label;
            this.modrinthType = modrinthType;
            this.curseClass = curseClass;
        }
        @Override public String toString() { return label; }
        Path directory(Installation instance) {
            return switch (this) {
                case MOD -> instance.modsDirectory();
                case RESOURCE -> instance.resourcePacksDirectory();
                case SHADER -> instance.shaderPacksDirectory();
            };
        }
    }

    ExplorePanel(Installation instance, Runnable refresh, Runnable back) {
        setLayout(views);
        JPanel catalog = new JPanel(new BorderLayout(10, 12));
        catalog.setOpaque(false);
        this.instance = instance;
        this.refresh = refresh;
        setBackground(LauncherStyle.BASE);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        JPanel controls = LauncherStyle.card(new BorderLayout(10, 12), 14);
        JPanel headline = new JPanel(new BorderLayout(10, 0));
        headline.setOpaque(false);
        JLabel heading = new JLabel("Explore content");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 34));
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        titleBlock.add(heading);
        JLabel subtitle = new JLabel("Find mods, resource packs, and shaders for your instance.");
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setForeground(LauncherStyle.INK);
        titleBlock.add(subtitle);
        headline.add(titleBlock, BorderLayout.WEST);
        JPanel banner = new LaunchBanner(111, .99f);
        banner.add(headline, BorderLayout.CENTER);

        JPanel fields = new JPanel(new BorderLayout(8, 0));
        fields.setOpaque(false);
        JPanel filters = new JPanel(new GridLayout(1, 2, 6, 0));
        filters.setOpaque(false);
        styleFilter(provider);
        styleFilter(category);
        filters.add(provider);
        filters.add(category);

        query.getAccessibleContext().setAccessibleName("Search projects");
        fields.add(query, BorderLayout.CENTER);

        query.setToolTipText("Search projects by name");

        JLabel context = new JLabel("Installing into " + instance.name() + "  ·  Minecraft "
                + instance.minecraftVersion() + "  ·  " + instance.loaderDisplayLabel());
        context.setForeground(LauncherStyle.MUTED);
        JPanel installContext = new JPanel(new BorderLayout(10, 0));
        installContext.setOpaque(false);
        installContext.add(LauncherStyle.button("Back to instance", false, back), BorderLayout.WEST);
        installContext.add(context, BorderLayout.CENTER);
        JPanel flatHeader = new JPanel(new BorderLayout(12, 0));
        flatHeader.setOpaque(false);
        JPanel limeRule = new JPanel();
        limeRule.setBackground(LauncherStyle.ACCENT);
        limeRule.setPreferredSize(new Dimension(5, 50));
        flatHeader.add(limeRule, BorderLayout.WEST);
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        controls.add(header, BorderLayout.CENTER);

        JToggleButton layout = new JToggleButton("Landscape", true);
        layout.setToolTipText("Switch between the landscape and compact Explore layouts");
        layout.setBackground(LauncherStyle.SURFACE);
        layout.setForeground(LauncherStyle.INK);
        layout.setBorder(BorderFactory.createCompoundBorder(LauncherStyle.outline(10),
                BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        installContext.add(layout, BorderLayout.EAST);
        Runnable arrange = () -> {
            boolean landscape = layout.isSelected();
            layout.setText(landscape ? "Landscape" : "Compact");
            header.removeAll();
            fields.removeAll();
            fields.add(query, BorderLayout.CENTER);
            JPanel filterBar = new JPanel(new BorderLayout(6, 0));
            filterBar.setOpaque(false);
            filterBar.add(filters, BorderLayout.CENTER);
            filterBar.add(search, BorderLayout.EAST);
            fields.add(filterBar, landscape ? BorderLayout.WEST : BorderLayout.EAST);
            JPanel headingPanel = landscape ? banner : flatHeader;
            headingPanel.add(headline, BorderLayout.CENTER);
            for (JPanel row : new JPanel[]{headingPanel, fields, installContext}) {
                row.setAlignmentX(LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            }
            header.add(headingPanel);
            header.add(Box.createVerticalStrut(10));
            header.add(landscape ? fields : installContext);
            header.add(Box.createVerticalStrut(8));
            header.add(landscape ? installContext : fields);
            header.revalidate();
            header.repaint();
        };
        layout.addActionListener(event -> arrange.run());
        arrange.run();
        catalog.add(controls, BorderLayout.NORTH);

        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setBackground(LauncherStyle.BASE);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        scroll.getViewport().setBackground(LauncherStyle.BASE);
        catalog.add(scroll, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        paging.setOpaque(false);
        paging.setLayout(new FlowLayout(FlowLayout.CENTER, 6, 0));
        status.setForeground(LauncherStyle.MUTED);
        footer.add(status, BorderLayout.WEST);
        footer.add(paging, BorderLayout.CENTER);
        catalog.add(footer, BorderLayout.SOUTH);
        add(catalog, "catalog");
        query.addActionListener(event -> search.doClick());
        provider.addActionListener(event -> search.doClick());
        category.addActionListener(event -> search.doClick());
        search.doClick();
    }

    private static <T> void styleFilter(JComboBox<T> filter) {
        filter.setBackground(LauncherStyle.SURFACE);

    }

    private void search(int next, String terms) {
        if (busy) return;
        ContentType type = (ContentType) category.getSelectedItem();
        String source = (String) provider.getSelectedItem();
        results.removeAll();
        resultButtons.clear();
        total = 0;
        results.revalidate();
        results.repaint();
        run("Searching " + source + "…", () -> {
            Result found = fetch(source, type, terms, next);
            return Map.entry(found, findInstalled(found.projects()));
        }, result -> {
            Result found = result.getKey();
            installed.clear();
            installed.putAll(result.getValue());
            page = next;
            appliedQuery = terms;
            total = found.total();
            renderResults(found.projects());
            status.setText(found.notice().isBlank()
                    ? (found.projects().isEmpty() ? "No matches for this instance." : String.format("%,d results", total))
                    : found.notice());
            SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
        });
    }

    private Result fetch(String source, ContentType type, String terms, int page) throws Exception {
        if (source.equals("Modrinth")) return modrinth(type, terms, page, PAGE_SIZE);
        if (source.equals("CurseForge")) return curseforge(type, terms, page, PAGE_SIZE);
        if (curseKey().isBlank()) {
            Result only = modrinth(type, terms, page, PAGE_SIZE);
            return new Result(only.projects(), only.total(), "CurseForge needs an API key. Showing Modrinth.");
        }
        int perSource = PAGE_SIZE / 2;
        Result modrinth = null;
        Result curse = null;
        try {
            modrinth = modrinth(type, terms, page, perSource);
        } catch (IOException error) {
            // Keep the other provider available in combined view.
        }
        try {
            curse = curseforge(type, terms, page, perSource);
        } catch (IOException error) {
            if (modrinth == null) throw error;
        }
        if (modrinth == null) return new Result(curse.projects(), curse.total() * 2, "Modrinth unavailable. Showing CurseForge.");
        if (curse == null) return new Result(modrinth.projects(), modrinth.total() * 2, "CurseForge unavailable. Showing Modrinth.");
        var merged = new ArrayList<>(modrinth.projects());
        merged.addAll(curse.projects());
        int pages = (Math.max(modrinth.total(), curse.total()) + perSource - 1) / perSource;
        int count = modrinth.total() + curse.total();
        return new Result(merged, pages * PAGE_SIZE, String.format("%,d results across both providers", count));
    }

    private Result modrinth(ContentType type, String terms, int page, int limit) throws Exception {
        var facets = new ArrayList<>(List.of(List.of("project_type:" + type.modrinthType),
                List.of("versions:" + instance.minecraftVersion())));
        if (type == ContentType.MOD) facets.add(List.of("categories:" + loader()));
        var response = HttpJson.getObject(MODRINTH + "/search?limit=" + limit + "&offset=" + page * limit
                + "&query=" + encode(terms) + "&facets=" + encode(Json.write(facets, false)));
        var items = objects(response.get("hits")).stream().map(hit -> new Project("Modrinth",
                string(hit, "project_id"), string(hit, "slug"), string(hit, "title"), string(hit, "description"), string(hit, "icon_url"), type, string(hit, "author"))).toList();
        return new Result(items, number(response.get("total_hits")), "");
    }

    private Result curseforge(ContentType type, String terms, int page, int limit) throws Exception {
        int offset = page * limit;
        if (offset >= 10000) return new Result(List.of(), 10000, "");
        var response = curseGet("/mods/search?gameId=432&classId=" + type.curseClass
                + "&gameVersion=" + encode(instance.minecraftVersion()) + curseLoader(type)
                + "&searchFilter=" + encode(terms) + "&index=" + offset + "&pageSize=" + limit);
        var items = objects(response.get("data")).stream().map(hit -> new Project("CurseForge",
                string(hit, "id"), string(hit, "slug"), string(hit, "name"), string(hit, "summary"),
                hit.get("logo") instanceof Map<?, ?> logo ? string(logo, "thumbnailUrl") : "", type, authors(hit))).toList();
        return new Result(items, response.get("pagination") instanceof Map<?, ?> pages
                ? Math.min(10000, number(pages.get("totalCount"))) : 0, "");
    }

    private void renderResults(List<Project> projects) {
        for (Project project : projects) {
            JPanel row = LauncherStyle.card(new BorderLayout(12, 0), 10);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
            row.setPreferredSize(new Dimension(600, 86));
            JLabel image = new JLabel() {
                @Override public Icon getIcon() { return ContentIcons.remote(project.icon(), this); }
            };
            image.setPreferredSize(new Dimension(ContentIcons.SIZE, ContentIcons.SIZE));
            row.add(image, BorderLayout.WEST);
            JPanel details = new JPanel(new GridLayout(3, 1));
            details.setOpaque(false);
            details.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 1, 0, 0, LauncherStyle.EDGE),
                    BorderFactory.createEmptyBorder(0, 11, 0, 0)));
            JLabel title = plain(project.title());
            title.setFont(title.getFont().deriveFont(Font.BOLD, 21f));
            details.add(title);
            JLabel description = plain(project.description());
            description.setToolTipText(project.description().startsWith("<") ? null : project.description());
            description.setForeground(LauncherStyle.MUTED);
            details.add(description);
            JLabel source = plain(project.provider() + "  ·  " + project.type().label + "  ·  "
                    + instance.minecraftVersion() + (project.type() == ContentType.MOD
                    ? "  ·  " + instance.loaderDisplayLabel() : ""));
            source.setForeground(LauncherStyle.MUTED);
            details.add(source);
            row.add(details, BorderLayout.CENTER);

            JButton plus = LauncherStyle.button(installed.containsKey(project) ? "✓ Installed" : "Install", true, () -> install(project));
            plus.getAccessibleContext().setAccessibleName("Install " + project.title());
            plus.setToolTipText("Install " + project.title());
            plus.setEnabled(!installed.containsKey(project));
            resultButtons.put(project, plus);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 3));
            actions.setOpaque(false);
            actions.setBorder(BorderFactory.createEmptyBorder(9, 0, 0, 0));
            JButton view = LauncherStyle.button("View", false, () -> view(project));
            view.getAccessibleContext().setAccessibleName("View " + project.title());
            actions.add(view);
            actions.add(plus);
            row.add(actions, BorderLayout.EAST);
            results.add(row);
            results.add(Box.createVerticalStrut(6));
        }
        results.add(Box.createVerticalGlue());
        results.revalidate();
        results.repaint();
    }

    private void view(Project project) {
        detailProject = project;
        var page = new ProjectDetailsView(project.title(),
                project.author().isBlank() ? project.provider() : project.provider() + "  ·  " + project.author(),
                project.description(), project.icon(), instance.name(), instance.minecraftVersion(),
                instance.loaderDisplayLabel(), this::backToResults, () -> install(project));
        detailView = page;
        add(page, "detail");
        views.show(this, "detail");
        page.installation(!busy, installed.containsKey(project), " ");
        loadDetail(page, () -> description(project), page::showDescription,
                () -> page.showDescription(new ProjectDetailsView.Description(
                        project.description() + "\n\nFull description could not be loaded.", false, List.of(), List.of(), List.of())));
        loadDetail(page, () -> versions(project),
                releases -> page.showVersions(releases,
                        releases.isEmpty() ? "No compatible versions found." : "Compatible with this instance"),
                () -> page.showVersions(List.of(), "Compatible versions could not be loaded."));
    }

    private void backToResults() {
        views.show(this, "catalog");
        remove(detailView);
        detailView = null;
        detailProject = null;
        revalidate();
        repaint();
    }

    private <T> void loadDetail(ProjectDetailsView page, Callable<T> load, Consumer<T> accept, Runnable failed) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return load.call(); }
            @Override protected void done() {
                if (detailView != page) return;
                try { accept.accept(get()); }
                catch (Exception error) { failed.run(); }
            }
        }.execute();
    }

    private ProjectDetailsView.Description description(Project project) throws Exception {
        if (project.provider().equals("Modrinth")) {
            var metadata = HttpJson.getObject(MODRINTH + "/project/" + encode(project.id()));
            String body = string(metadata, "body");
            var images = new java.util.LinkedHashSet<String>();
            var markdownImages = java.util.regex.Pattern.compile("!\\[[^]]*]\\((https://[^ )]+)\\)").matcher(body);
            while (markdownImages.find() && images.size() < 3) {
                String url = markdownImages.group(1);
                if (ContentIcons.canLoadRemote(url)) images.add(url);
            }
            objects(metadata.get("gallery")).stream().map(entry -> string(entry, "url"))
                    .filter(ContentIcons::canLoadRemote).limit(4).forEach(images::add);
            return new ProjectDetailsView.Description(body.isBlank() ? project.description() : body, false,
                    strings(metadata.get("categories")), strings(metadata.get("loaders")),
                    images.stream().limit(4).toList());
        }
        String endpoint = "/mods/" + encode(project.id());
        String body = string(curseGet(endpoint + "/description"), "data");
        List<String> tags = List.of();
        List<String> images = List.of();
        try {
            if (curseGet(endpoint).get("data") instanceof Map<?, ?> metadata) {
                tags = objects(metadata.get("categories")).stream().map(category -> string(category, "name")).toList();
                images = objects(metadata.get("screenshots")).stream().map(shot -> string(shot, "url"))
                        .filter(ContentIcons::canLoadRemote).limit(4).toList();
            }
        } catch (IOException ignored) {
            // The description is still available if optional metadata fails.
        }
        return new ProjectDetailsView.Description(body.isBlank() ? project.description() : body,
                !body.isBlank(), tags, List.of(), images);
    }

    private List<ProjectDetailsView.Version> versions(Project project) throws Exception {
        if (project.provider().equals("Modrinth")) {
            return modrinthVersions(project).stream().limit(40).map(version -> new ProjectDetailsView.Version(
                    string(version, "name"), releaseLine(string(version, "version_type"), string(version, "date_published")),
                    compatibility(strings(version.get("game_versions")), strings(version.get("loaders"))))).toList();
        }
        return curseFiles(project).stream().map(file -> new ProjectDetailsView.Version(
                string(file, "displayName"), releaseLine(switch (number(file.get("releaseType"))) {
                    case 2 -> "Beta"; case 3 -> "Alpha"; default -> "Release";
                }, string(file, "fileDate")), compatibility(strings(file.get("gameVersions")), List.of()))).toList();
    }

    private static String releaseLine(String type, String date) {
        return date.length() >= 10 ? type + "  ·  " + date.substring(0, 10) : type;
    }

    private static String compatibility(List<String> games, List<String> loaders) {
        var values = new ArrayList<String>();
        if (!games.isEmpty()) values.add(String.join(", ", games.stream().limit(8).toList()));
        if (!loaders.isEmpty()) values.add(String.join(", ", loaders));
        return String.join("  ·  ", values);
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static String authors(Map<?, ?> project) {
        return objects(project.get("authors")).stream().map(author -> string(author, "name"))
                .filter(name -> !name.isBlank()).limit(3).collect(java.util.stream.Collectors.joining(", "));
    }

    private void install(Project project) {
        if (installed.containsKey(project)) return;
        run("Installing " + project.title() + "…", () -> {
            Path existing = findInstalled(List.of(project)).get(project);
            if (existing != null) return new InstallResult(existing, "Already installed: " + project.title());
            Download file = project.provider().equals("Modrinth") ? modrinthFile(project) : curseFile(project);
            String name = file.filename();
            String lower = name.toLowerCase(Locale.ROOT);
            if (!name.matches("[^\\\\/:*?\"<>|]+\\.(?i:jar|zip)")
                    || (project.type() == ContentType.MOD && !lower.endsWith(".jar"))
                    || (project.type() != ContentType.MOD && !lower.endsWith(".zip"))
                    || !name.chars().allMatch(character -> character >= 32)) {
                throw new IOException("The provider did not return a supported file.");
            }
            if (!"https".equals(URI.create(file.url()).getScheme()) || !file.sha1().matches("(?i)[0-9a-f]{40}")) {
                throw new IOException("This file does not have an HTTPS download and SHA-1 checksum.");
            }
            Path folder = project.type().directory(instance);
            Files.createDirectories(folder);
            Path target = folder.resolve(name);
            if (Files.exists(target) || Files.exists(folder.resolve(name + ".disabled"))) {
                return new InstallResult(target, "Already installed: " + name);
            }
            Path temporary = Files.createTempDirectory(folder, ".krovex-download-");
            Path staging = temporary.resolve(name);
            try {
                new DownloadManager().download(file.url(), staging, file.sha1(), null);
                Files.move(staging, target);
            } finally {
                Files.deleteIfExists(staging);
                Files.deleteIfExists(temporary);
            }
            try {
                Path receipt = receipt(folder, target.getFileName().toString());
                Files.createDirectories(receipt.getParent());
                Files.writeString(receipt, project.provider() + ":" + project.id());
            } catch (IOException ignored) {
                // The installed archive can still be recognized by its metadata or filename.
            }
            ContentIcons.remember(target, project.icon());
            return new InstallResult(target, "Installed " + name);
        }, completed -> {
            installed.put(project, completed.destination());
            JButton button = resultButtons.get(project);
            if (button != null) button.setText("✓ Installed");
            status.setText(completed.message());
            refresh.run();
        });
    }

    void refreshInstalled() {
        if (busy || resultButtons.isEmpty()) return;
        var projects = new ArrayList<>(resultButtons.keySet());
        if (detailProject != null && !projects.contains(detailProject)) projects.add(detailProject);
        String previous = status.getText();
        run("Checking installed content…", () -> findInstalled(projects), matches -> {
            installed.clear();
            installed.putAll(matches);
            status.setText(previous);
        });
    }

    void contentDeleted(Path file) {
        String name = originalName(file.getFileName().toString());
        Path folder = file.getParent();
        if (folder.getFileName().toString().equals(".krovex-disabled")) folder = folder.getParent();
        try {
            Files.deleteIfExists(receipt(folder, name));
        } catch (IOException ignored) { }
        refreshInstalled();
    }

    private static Path receipt(Path folder, String filename) {
        return folder.resolve(".krovex-projects").resolve(filename + ".txt");
    }

    private static String originalName(String name) {
        return name.endsWith(".disabled") ? name.substring(0, name.length() - 9) : name;
    }

    private Map<Project, Path> findInstalled(List<Project> projects) {
        Map<Project, Path> matches = new java.util.HashMap<>();
        for (ContentType type : ContentType.values()) {
            var candidates = projects.stream().filter(project -> project.type() == type).toList();
            if (candidates.isEmpty()) continue;
            Path dir = type.directory(instance);
            for (Path folder : List.of(dir, dir.resolve(".krovex-disabled"))) {
                if (!Files.isDirectory(folder)) continue;
                try (var paths = Files.list(folder)) {
                    for (Path file : paths.filter(Files::isRegularFile).toList()) {
                        String name = originalName(file.getFileName().toString());
                        if (!name.toLowerCase(Locale.ROOT).endsWith(type == ContentType.MOD ? ".jar" : ".zip")) continue;
                        Path marker = receipt(dir, name);
                        String providerId = "";
                        try {
                            if (Files.isRegularFile(marker) && Files.size(marker) <= 512) {
                                providerId = Files.readString(marker);
                            }
                        } catch (IOException ignored) { }
                        var metadata = type == ContentType.MOD ? modNames(file) : java.util.Set.<String>of();
                        String stem = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
                        for (Project project : candidates) {
                            if (matches.containsKey(project)) continue;
                            String slug = normalized(project.slug());
                            String title = normalized(project.title());
                            if (providerId.equals(project.provider() + ":" + project.id())
                                    || (!slug.isEmpty() && metadata.contains(slug))
                                    || (!title.isEmpty() && metadata.contains(title))
                                    || filenameMatches(stem, project.slug())) {
                                matches.put(project, dir.resolve(name));
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // A missing or inaccessible folder is treated as having no readable content.
                }
            }
        }
        return matches;
    }

    private static boolean filenameMatches(String stem, String slug) {
        if (slug.isBlank()) return false;
        String fileKey = stem.replace('_', '-').replace(' ', '-');
        String slugKey = slug.toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        if (fileKey.equals(slugKey)) return true;
        if (!fileKey.startsWith(slugKey + "-")) return false;
        return fileKey.substring(slugKey.length() + 1).matches("(?:(?:fabric|quilt|forge|neoforge|mc)-)*(?:v?\\d.*)");
    }

    private static String normalized(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static java.util.Set<String> modNames(Path file) {
        var names = new java.util.HashSet<String>();
        try (var archive = new java.util.zip.ZipFile(file.toFile())) {
            for (String path : List.of("fabric.mod.json", "quilt.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")) {
                var entry = archive.getEntry(path);
                if (entry == null || entry.getSize() > 65536) continue;
                try (var input = archive.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(65537);
                    if (bytes.length > 65536) continue;
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    if (path.endsWith(".json")) {
                        Map<String, Object> metadata = Json.parseObject(text);
                        names.add(normalized(string(metadata, "id")));
                        names.add(normalized(string(metadata, "name")));
                        if (metadata.get("quilt_loader") instanceof Map<?, ?> quilt) {
                            names.add(normalized(string(quilt, "id")));
                            if (quilt.get("metadata") instanceof Map<?, ?> details) {
                                names.add(normalized(string(details, "name")));
                            }
                        }
                    } else {
                        var match = java.util.regex.Pattern.compile("(?m)^\\s*(?:modId|displayName)\\s*=\\s*[\\x22\\x27]([^\\x22\\x27]+)[\\x22\\x27]").matcher(text);
                        while (match.find()) names.add(normalized(match.group(1)));
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) { }
        return names;
    }

    private Download modrinthFile(Project project) throws Exception {
        var versions = modrinthVersions(project);
        if (versions.isEmpty()) throw new IOException("No compatible version is available.");
        var files = objects(versions.getFirst().get("files"));
        var file = files.stream().filter(item -> Boolean.TRUE.equals(item.get("primary"))).findFirst()
                .orElse(files.isEmpty() ? Map.of() : files.getFirst());
        return new Download(string(file, "filename"), string(file, "url"),
                file.get("hashes") instanceof Map<?, ?> hashes ? string(hashes, "sha1") : "");
    }

    private List<Map<?, ?>> modrinthVersions(Project project) throws Exception {
        String url = MODRINTH + "/project/" + encode(project.id()) + "/version?game_versions="
                + encode(Json.write(List.of(instance.minecraftVersion()), false));
        if (project.type() == ContentType.MOD) url += "&loaders=" + encode(Json.write(List.of(loader()), false));
        return objects(HttpJson.getArray(url));
    }

    private Download curseFile(Project project) throws Exception {
        var files = curseFiles(project);
        var file = files.stream().filter(item -> !string(item, "downloadUrl").isBlank()).findFirst()
                .orElseThrow(() -> new IOException("CurseForge has no compatible file available for third-party download."));
        String hash = objects(file.get("hashes")).stream().filter(entry -> number(entry.get("algo")) == 1)
                .map(entry -> string(entry, "value")).findFirst().orElse("");
        return new Download(string(file, "fileName"), string(file, "downloadUrl"), hash);
    }

    private List<Map<?, ?>> curseFiles(Project project) throws Exception {
        return objects(curseGet("/mods/" + encode(project.id()) + "/files?gameVersion="
                + encode(instance.minecraftVersion()) + curseLoader(project.type()) + "&pageSize=20").get("data"));
    }

    private String curseLoader(ContentType type) {
        if (type != ContentType.MOD) return "";
        int filter = switch (instance.loader()) {
            case FABRIC -> 4;
            case FORGE -> 1;
            case QUILT -> 5;
            case NEOFORGE -> 6;
            case VANILLA -> 0;
        };
        return "&modLoaderType=" + filter;
    }

    private static String curseKey() {
        String key = System.getenv("KROVEX_CURSEFORGE_API_KEY");
        return key == null ? "" : key.strip();
    }

    private static Map<String, Object> curseGet(String path) throws Exception {
        String key = curseKey();
        if (key.isBlank()) throw new IOException("CurseForge requires KROVEX_CURSEFORGE_API_KEY.");
        var request = HttpRequest.newBuilder(URI.create(CURSEFORGE + path)).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json").header("x-api-key", key).GET().build();
        var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException("CurseForge returned HTTP " + response.statusCode() + ".");
        return Json.parseObject(response.body());
    }

    private String loader() { return instance.loader().name().toLowerCase(Locale.ROOT); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String string(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }
    private static int number(Object value) { return value instanceof Number n ? n.intValue() : 0; }

    private static JLabel plain(String text) {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", true);
        label.setText(text);
        return label;
    }

    private static List<Map<?, ?>> objects(Object value) {
        var result = new ArrayList<Map<?, ?>>();
        if (value instanceof List<?> list) {
            for (Object item : list) if (item instanceof Map<?, ?> map) result.add(map);
        }
        return result;
    }

    private void updateControls() {
        search.setEnabled(!busy);
        provider.setEnabled(!busy);
        category.setEnabled(!busy);
        resultButtons.forEach((project, button) -> {
            boolean present = installed.containsKey(project);
            button.setText(present ? "✓ Installed" : "Install");
            button.setEnabled(!busy && !present);
        });
        paging.removeAll();
        int pages = (total + PAGE_SIZE - 1) / PAGE_SIZE;
        if (pages > 1) {
            if (page > 0) paging.add(LauncherStyle.button("Previous", false, () -> search(page - 1, appliedQuery)));
            int start = Math.max(0, Math.min(page - 2, pages - 5));
            for (int index = start; index < Math.min(start + 5, pages); index++) {
                int destination = index;
                JButton number = LauncherStyle.button(String.valueOf(index + 1), index == page,
                        () -> search(destination, appliedQuery));
                number.setEnabled(!busy && index != page);
                paging.add(number);
            }
            if (start + 5 < pages) {
                paging.add(new JLabel("…"));
                paging.add(LauncherStyle.button(String.valueOf(pages), false, () -> search(pages - 1, appliedQuery)));
            }
            if (page + 1 < pages) paging.add(LauncherStyle.button("Next", false, () -> search(page + 1, appliedQuery)));
        }
        paging.revalidate();
        paging.repaint();
    }

    private static final class ResultsList extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 22; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(22, visible.height - 22);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private void updateDetailInstall() {
        if (detailView != null) detailView.installation(!busy, installed.containsKey(detailProject), status.getText());
    }

    private <T> void run(String message, Callable<T> task, Consumer<T> complete) {
        if (busy) return;
        busy = true;
        updateControls();
        status.setText(message);
        updateDetailInstall();
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return task.call(); }
            @Override protected void done() {
                try {
                    complete.accept(get());
                } catch (Exception error) {
                    Throwable detail = error.getCause() == null ? error : error.getCause();
                    status.setText(detail instanceof IOException ? detail.getMessage() : "Could not complete the request.");
                } finally {
                    busy = false;
                    updateControls();
                    updateDetailInstall();
                }
            }
        }.execute();
    }
}
