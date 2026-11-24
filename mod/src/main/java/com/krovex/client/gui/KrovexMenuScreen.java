package com.krovex.client.gui;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.gui.screens.*;
import com.krovex.theme.KrovexTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NOT compiled here (see QuickPopupScreen's header note). The full Krovex menu: left
 * sidebar, a swappable content panel, and the world visible behind it at the user's
 * chosen background opacity. Sidebar order per section 3: Home, Modules, Mods,
 * Textures, Settings, Music, then GUI/HUD Editor directly above Theme, which stays
 * pinned at the very bottom even if the sidebar has to scroll.
 */
public final class KrovexMenuScreen extends Screen {

    private enum Tab { HOME, MODULES, MODS, TEXTURES, SETTINGS, SKY, VISUALS, MUSIC, HUD_EDITOR, THEME, SHOTS, ABOUT }

    private int left, top, panelWidth, panelHeight, sidebar, contentX, contentY, contentWidth, contentHeight;

    private final Map<Tab, KrovexSubPanel> panels = new LinkedHashMap<>();
    private Tab activeTab = Tab.HOME;
    private KrovexSubPanel activePanel;
    /** When the menu opened and when the tab last changed, for the short open / tab-change fades. */
    private final long openedAt = System.nanoTime();
    private long tabChangedAt;

    public KrovexMenuScreen() {
        super(Text.literal("Krovex Client"));
    }

    @Override
    protected void init() {
        panels.put(Tab.HOME, new HomePanel(this));
        panels.put(Tab.MODULES, new ModulesPanel(this));
        panels.put(Tab.MODS, new ModsPanel(this));
        panels.put(Tab.TEXTURES, new TexturesPanel(this));
        panels.put(Tab.SETTINGS, new SettingsPanel(this));
        panels.put(Tab.SKY, new SkyPanel(this));
        panels.put(Tab.VISUALS, new VisualsPanel(this));
        panels.put(Tab.MUSIC, new MusicPanel(this));
        panels.put(Tab.HUD_EDITOR, new HudEditorPanel(this));
        panels.put(Tab.THEME, new ThemePanel(this));

        panels.put(Tab.SHOTS, new ScreenshotsPanel());
        panels.put(Tab.ABOUT, new AboutPanel());
        panelHeight = Math.min(510, height - Math.max(14, height / 8));
        panelWidth = Math.min(width - Math.max(14, width / 12), Math.max(330, panelHeight * 17 / 10));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        sidebar = Math.clamp(panelWidth / 5, 78, 150);
        positionContent();
        addDrawableChild(MenuStyle.button("−", left + panelWidth - 45, top + 5, 17, 17, 0, this::close));
        addDrawableChild(MenuStyle.button("×", left + panelWidth - 23, top + 5, 17, 17, 0, this::close));
        switchTo(activeTab);
    }

    private boolean library() { return activeTab == Tab.HUD_EDITOR || activeTab == Tab.SHOTS; }

    private void positionContent() {
        contentX = left + (library() ? 10 : sidebar + 14);
        contentY = top + (library() ? 83 : 78);
        contentWidth = left + panelWidth - contentX - 12;
        contentHeight = top + panelHeight - contentY - 10;
    }

    private final java.util.List<net.minecraft.client.gui.Element> navChildren = new java.util.ArrayList<>();

    private void rebuildSidebar() {
        navChildren.forEach(this::remove);
        navChildren.clear();
        if (library()) {
            Tab[] links = {Tab.HOME, Tab.MODS, Tab.THEME, Tab.HUD_EDITOR, Tab.SHOTS, Tab.SETTINGS};
            int start = left + (panelWidth >= 480 ? 104 : 34);
            int item = (left + panelWidth - 47 - start) / links.length;
            for (int i = 0; i < links.length; i++) {
                Tab link = links[i];
                String label = switch (link) {
                    case MODS -> "Mods";
                    case THEME -> "Cosmetics";
                    case SHOTS -> item < 67 ? "Gallery" : "Screenshots";
                    default -> labelFor(link);
                };
                navChildren.add(addDrawableChild(MenuStyle.button(label, start + i * item, top + 4,
                        Math.max(14, item - 2), 20, link == activeTab ? MenuStyle.PURPLE : 0, () -> switchTo(link))));
            }
            return;
        }
        Tab[] order = {Tab.HOME, Tab.MODULES, Tab.MODS, Tab.TEXTURES, Tab.SETTINGS, Tab.HUD_EDITOR,
                       Tab.THEME, Tab.SHOTS, Tab.SKY, Tab.VISUALS, Tab.MUSIC, Tab.ABOUT};
        // while Modules is open its categories sit under it in the sidebar, each with its module count
        java.util.List<com.krovex.client.modules.ModuleCategory> categories = new java.util.ArrayList<>();
        if (activeTab == Tab.MODULES) {
            categories.add(null);       // "All"
            for (var category : com.krovex.client.modules.ModuleCategory.values())
                if (com.krovex.client.gui.screens.ModulesPanel.count(category) > 0) categories.add(category);
        }
        int subHeight = 15, subBlock = categories.isEmpty() ? 0 : categories.size() * subHeight + 5;
        int step = Math.clamp((panelHeight - 56 - subBlock) / order.length, 15, 27);
        int y = top + 36;
        for (Tab tab : order) {
            navChildren.add(addDrawableChild(MenuStyle.nav(labelFor(tab), iconFor(tab), left + 6, y,
                    sidebar - 12, step - 2, tab == activeTab, () -> switchTo(tab))));
            y += step;
            if (tab == Tab.MODULES && !categories.isEmpty()) {
                for (var category : categories) {
                    String name = category == null ? "All" : category.name().charAt(0) + category.name().substring(1).toLowerCase(java.util.Locale.ROOT);
                    if (category == com.krovex.client.modules.ModuleCategory.HUD) name = "Display";
                    if (category == com.krovex.client.modules.ModuleCategory.RENDERING) name = "Visuals";
                    navChildren.add(addDrawableChild(MenuStyle.subNav(name, com.krovex.client.gui.screens.ModulesPanel.count(category),
                            left + 20, y, sidebar - 28, subHeight - 1, com.krovex.client.gui.screens.ModulesPanel.category() == category, () -> {
                                com.krovex.client.gui.screens.ModulesPanel.setCategory(category);
                                refreshPanel();
                            })));
                    y += subHeight;
                }
                y += 5;
            }
        }
    }

    private static String iconFor(Tab tab) {
        return switch (tab) {
            case HOME -> "home";
            case MODULES -> "modules";
            case MODS -> "mods";
            case TEXTURES -> "textures";
            case SETTINGS -> "settings";
            case HUD_EDITOR -> "hud";
            case THEME -> "theme";
            case SHOTS -> "shots";
            case SKY -> "sky";
            case VISUALS -> "theme";
            case MUSIC -> "music";
            case ABOUT -> "about";
        };
    }

    private static String version() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("krovex-client")
                .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    public void showTab(String tab) { switchTo(Tab.valueOf(tab)); }

    private String labelFor(Tab tab) {
        return switch (tab) {
            case HOME -> "Home";
            case MODULES -> "Modules";
            case MODS -> "Installed Mods";
            case TEXTURES -> "Textures";
            case SETTINGS -> "Settings";
            case SKY -> "Sky";
            case VISUALS -> "Color Grading";
            case MUSIC -> "Music";
            case HUD_EDITOR -> "Profiles";
            case THEME -> "Themes";
            case SHOTS -> "Screenshots";
            case ABOUT -> "About";
        };
    }

    public void refreshPanel() { switchTo(activeTab); }

    private void switchTo(Tab tab) {
        // Remove the previous panel's widgets before the new one adds its own -- each
        // panel only ever owns the content area (SIDEBAR_WIDTH..width), so the sidebar
        // buttons added in init() are untouched by this.
        if (activePanel != null) activePanel.onHidden();
        for (var child : contentAreaChildren) this.remove(child);
        contentAreaChildren.clear();

        if (tab != activeTab) tabChangedAt = System.nanoTime();
        this.activeTab = tab;
        positionContent();
        rebuildSidebar();
        this.activePanel = panels.get(tab);
        if (this.activePanel != null) {
            this.activePanel.onShown();
            this.activePanel.createWidgets(this, contentX, contentY, contentWidth, contentHeight);
        }
    }

    private final java.util.List<net.minecraft.client.gui.Element> contentAreaChildren = new java.util.ArrayList<>();

    /** Panels call this (instead of {@code addDrawableChild} directly) so switching tabs cleans up correctly. */
    public <T extends net.minecraft.client.gui.Element & net.minecraft.client.gui.Drawable
            & net.minecraft.client.gui.Selectable> T addPanelWidget(T widget) {
        this.addDrawableChild(widget);
        contentAreaChildren.add(widget);
        return widget;
    }

    public void removePanelWidget(net.minecraft.client.gui.Element widget) {
        remove(widget);
        contentAreaChildren.remove(widget);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // opening: the world dims a little and the panel eases in from 96% size
        float open = Math.min(1f, (System.nanoTime() - openedAt) / 1.7e8f);
        open = 1f - (1f - open) * (1f - open) * (1f - open);
        context.fillGradient(0, 0, width, height, MenuStyle.withAlpha(0x0B0714, Math.round(0x34 * open)), MenuStyle.withAlpha(0x0B0714, Math.round(0x62 * open)));
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            if (open < 1f) {
                float scale = .96f + .04f * open;
                matrices.translate(width / 2f, height / 2f);
                matrices.scale(scale, scale);
                matrices.translate(-width / 2f, -height / 2f);
            }
            renderMenu(context, mouseX, mouseY, delta);
        } finally {
            matrices.popMatrix();
        }
    }

    private void renderMenu(DrawContext context, int mouseX, int mouseY, float delta) {
        int right = left + panelWidth, bottom = top + panelHeight;
        MenuStyle.glass(context, left, top, panelWidth, panelHeight, 12);
        context.fillGradient(left + 9, top + 2, right - 9, top + 28, 0x22FFFFFF, 0x06FFFFFF);
        for (int seg = 0; seg < 28; seg++) {   // accent line under the header: lime -> violet -> pink
            float t0 = seg / 28f;
            int color = t0 < .5f ? MenuStyle.blend(MenuStyle.LIME, MenuStyle.VIOLET, t0 * 2) : MenuStyle.blend(MenuStyle.VIOLET, MenuStyle.PINK, (t0 - .5f) * 2);
            int x0 = left + 3 + (right - left - 6) * seg / 28, x1 = left + 3 + (right - left - 6) * (seg + 1) / 28;
            context.fill(x0, top + 28, x1, top + 29, MenuStyle.withAlpha(color, 0xB0));
        }
        MenuStyle.logo(context, left + 8, top + 3, 22);
        if (!library() || panelWidth >= 480) MenuStyle.text(context, "KROVEX", left + 33, top + 7, MenuStyle.WHITE, 1.42f);
        if (library()) {
            MenuStyle.logo(context, contentX + 1, top + 39, 30);
            context.fill(contentX + 39, top + 39, contentX + 40, top + 70, MenuStyle.EDGE);
        } else {
            context.fillGradient(left + 2, top + 29, left + sidebar, bottom - 12, 0x3C000000, 0x18000000);
            context.fill(left + sidebar, top + 29, left + sidebar + 1, bottom - 5, MenuStyle.HAIRLINE);
            if (activeTab == Tab.MODULES) {       // the thin guide line the category entries hang from
                int guideTop = navChildren.size() > 2 ? ((net.minecraft.client.gui.widget.ClickableWidget) navChildren.get(2)).getY() : top + 90;
                int count = 0;
                for (var child : navChildren) if (child instanceof net.minecraft.client.gui.widget.ClickableWidget w && w.getX() == left + 20) count++;
                if (count > 0) context.fill(left + 14, guideTop + 2, left + 15, guideTop + count * 15 - 3, 0x40FFFFFF);
            }
            MenuStyle.disc(context, left + 14, bottom - 8, 2, MenuStyle.LIME);
            MenuStyle.text(context, "KROVEX  " + version(), left + 21, bottom - 12, MenuStyle.MUTED, .85f);
        }
        MenuStyle.heading(context, contentX + (library() ? 53 : 0), top + (library() ? 39 : 37), switch (activeTab) {
            case HOME -> "Welcome to KROVEX";
            case THEME -> "Theme Editor";
            default -> labelFor(activeTab);
        }, switch (activeTab) {
            case HOME -> "A modern, customizable in-game client.";
            case MODULES -> "Switch modules on and off, and open their settings.";
            case MODS -> "Manage your installed game mods, versions and dependencies.";
            case TEXTURES -> "Manage texture packs and the load order applied in-game.";
            case SETTINGS -> "Configure how KROVEX works in game.";
            case THEME -> "Create, tweak, and share your KROVEX themes.";
            case HUD_EDITOR -> "Save and switch between HUD layouts.";
            case SHOTS -> "View and open your in-game screenshots.";
            case MUSIC -> "Picks up the Spotify app automatically. No sign-in needed.";
            case SKY -> "Replace the sky with an animated preset.";
            case VISUALS -> "Saturation, contrast, warmth and shadows for the whole game view.";
            case ABOUT -> "Version and environment details.";
            default -> "";
        });
        if (activePanel != null) activePanel.render(context, contentX, contentY, contentWidth, contentHeight, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        float tabFade = tabChangedAt == 0 ? 1f : Math.min(1f, (System.nanoTime() - tabChangedAt) / 1.5e8f);
        if (tabFade < 1f) {       // a new tab fades in instead of popping
            int veil = Math.round(0xD8 * (1f - tabFade) * (1f - tabFade));
            context.fillGradient(contentX - 4, top + 31, right - 3, bottom - 4, MenuStyle.tint(MenuStyle.withAlpha(0x241A45, veil)), MenuStyle.tint(MenuStyle.withAlpha(0x170F2E, veil)));
        }
        if (activePanel != null) activePanel.renderOverlay(context, mouseX, mouseY);
        if (pendingKey != null) drawConflictDialog(context, mouseX, mouseY);
        if (confirmQuestion != null) drawConfirmDialog(context, mouseX, mouseY);
    }

    // ---- generic "are you sure?" question with Yes / No (used by Theme > Reset to default)
    private String confirmQuestion;
    private Runnable confirmYes;

    /** Opens the Yes / No popup; {@code onYes} runs only if the player confirms. Enter / Y = yes, Esc / N = no. */
    public void askConfirm(String question, Runnable onYes) {
        confirmQuestion = question;
        confirmYes = onYes;
    }

    private void answerConfirm(boolean yes) {
        Runnable action = confirmYes;
        confirmQuestion = null;
        confirmYes = null;
        if (yes && action != null) action.run();
    }

    private void drawConfirmDialog(DrawContext context, int mouseX, int mouseY) {
        int accent = MenuStyle.tint(MenuStyle.VIOLET);
        dialogW = Math.min(panelWidth - 24, 300);
        var lines = textRenderer.wrapLines(MenuStyle.lettering(Text.literal(confirmQuestion), false), dialogW - 24);
        dialogH = 16 + lines.size() * 11 + 10 + 20 + 12;
        dialogX = left + (panelWidth - dialogW) / 2;
        dialogY = top + (panelHeight - dialogH) / 2;
        context.fill(left + 2, top + 2, left + panelWidth - 2, top + panelHeight - 2, 0x90000000);
        MenuStyle.glow(context, dialogX, dialogY, dialogW, dialogH, 8, accent);
        MenuStyle.gradientCardRaw(context, dialogX, dialogY, dialogW, dialogH, 8, accent, 0xF61F1A33, 0xF6100D1C);
        int y = dialogY + 12;
        for (var line : lines) { context.drawText(textRenderer, line, dialogX + 12, y, 0xFFF4F0FF, false); y += 11; }
        int buttonY = dialogY + dialogH - 30, half = (dialogW - 34) / 2;
        boolean overYes = mouseX >= dialogX + 12 && mouseX < dialogX + 12 + half && mouseY >= buttonY && mouseY < buttonY + 20;
        boolean overNo = mouseX >= dialogX + 22 + half && mouseX < dialogX + 22 + 2 * half && mouseY >= buttonY && mouseY < buttonY + 20;
        MenuStyle.gradientCardRaw(context, dialogX + 12, buttonY, half, 20, 5, 0xFFEAFFA8, overYes ? 0xFFE3FF86 : 0xFFD4FF5C, overYes ? 0xFFB6F52F : 0xFFA4E818);
        MenuStyle.gradientCard(context, dialogX + 22 + half, buttonY, half, 20, 5, overNo ? MenuStyle.HOVER_EDGE : MenuStyle.EDGE, overNo ? 0xFF54447A : 0xFF3B3058, 0xFF2A2142);
        var yes = MenuStyle.lettering(Text.literal("Yes"), true);
        var no = MenuStyle.lettering(Text.literal("No"), true);
        context.drawText(textRenderer, yes, dialogX + 12 + (half - textRenderer.getWidth(yes)) / 2, buttonY + 6, 0xFF17101E, false);
        context.drawText(textRenderer, no, dialogX + 22 + half + (half - textRenderer.getWidth(no)) / 2, buttonY + 6, 0xFFFFFFFF, false);
    }

    /** Yes = 1, No = 2, elsewhere = 0 (for the confirm popup). */
    private int confirmButtonAt(double mouseX, double mouseY) {
        int buttonY = dialogY + dialogH - 30, half = (dialogW - 34) / 2;
        if (mouseY < buttonY || mouseY >= buttonY + 20) return 0;
        if (mouseX >= dialogX + 12 && mouseX < dialogX + 12 + half) return 1;
        if (mouseX >= dialogX + 22 + half && mouseX < dialogX + 22 + 2 * half) return 2;
        return 0;
    }

    // ---- key conflict confirmation: red box, the question, Yes / No
    private int dialogX, dialogY, dialogW, dialogH;

    private void drawConflictDialog(DrawContext context, int mouseX, int mouseY) {
        int red = 0xFFFF5C5C;
        dialogW = Math.min(panelWidth - 24, 340);
        var first = textRenderer.wrapLines(MenuStyle.lettering(Text.literal("This key is conflicted with " + conflictNames + "."), true), dialogW - 20);
        var second = textRenderer.wrapLines(MenuStyle.lettering(Text.literal("Are you sure you would like to set it to the same keybind as " + conflictNames + "?"), false), dialogW - 20);
        dialogH = 14 + (first.size() + second.size()) * 11 + 6 + 20 + 10;
        dialogX = left + (panelWidth - dialogW) / 2;
        dialogY = top + (panelHeight - dialogH) / 2;
        context.fill(left + 2, top + 2, left + panelWidth - 2, top + panelHeight - 2, 0x90000000);      // everything behind steps back
        MenuStyle.glow(context, dialogX, dialogY, dialogW, dialogH, 8, red);
        MenuStyle.gradientCardRaw(context, dialogX, dialogY, dialogW, dialogH, 8, red, 0xF63A1420, 0xF61E0C14);
        int y = dialogY + 10;
        for (var line : first) { context.drawText(textRenderer, line, dialogX + 10, y, red, false); y += 11; }
        y += 3;
        for (var line : second) { context.drawText(textRenderer, line, dialogX + 10, y, 0xFFFFE3E3, false); y += 11; }
        int buttonY = dialogY + dialogH - 28, half = (dialogW - 30) / 2;
        boolean overYes = mouseX >= dialogX + 10 && mouseX < dialogX + 10 + half && mouseY >= buttonY && mouseY < buttonY + 20;
        boolean overNo = mouseX >= dialogX + 20 + half && mouseX < dialogX + 20 + 2 * half && mouseY >= buttonY && mouseY < buttonY + 20;
        MenuStyle.gradientCardRaw(context, dialogX + 10, buttonY, half, 20, 5, red, overYes ? 0xFFFF7A7A : 0xFFE04848, overYes ? 0xFFE04848 : 0xFFB22F2F);
        MenuStyle.gradientCard(context, dialogX + 20 + half, buttonY, half, 20, 5, overNo ? MenuStyle.HOVER_EDGE : MenuStyle.EDGE, overNo ? 0xFF54447A : 0xFF3B3058, 0xFF2A2142);
        var yes = MenuStyle.lettering(Text.literal("Yes"), true);
        var no = MenuStyle.lettering(Text.literal("No"), true);
        context.drawText(textRenderer, yes, dialogX + 10 + (half - textRenderer.getWidth(yes)) / 2, buttonY + 6, 0xFFFFFFFF, false);
        context.drawText(textRenderer, no, dialogX + 20 + half + (half - textRenderer.getWidth(no)) / 2, buttonY + 6, 0xFFFFFFFF, false);
    }

    /** Yes = 1, No = 2, elsewhere = 0. */
    private int conflictButtonAt(double mouseX, double mouseY) {
        int buttonY = dialogY + dialogH - 28, half = (dialogW - 30) / 2;
        if (mouseY < buttonY || mouseY >= buttonY + 20) return 0;
        if (mouseX >= dialogX + 10 && mouseX < dialogX + 10 + half) return 1;
        if (mouseX >= dialogX + 20 + half && mouseX < dialogX + 20 + 2 * half) return 2;
        return 0;
    }

    private void bindCaptured(net.minecraft.client.util.InputUtil.Key key) {
        capturingKeyBinding.setBoundKey(key);
        net.minecraft.client.option.KeyBinding.updateKeysByCode();
        net.minecraft.client.MinecraftClient.getInstance().options.write();
        capturingButton.setMessage(Text.literal(capturingLabel + ": " + capturingKeyBinding.getBoundKeyLocalizedText().getString()));
        endCapture();
    }

    private void cancelCapture() {
        capturingButton.setMessage(capturingOriginalLabel);
        endCapture();
    }

    @Override public void removed() {
        if (activePanel != null) activePanel.onHidden();
        super.removed();
    }

    @Override public void renderBackground(DrawContext context, int x, int y, float delta) {}

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        assert this.client != null;
        this.client.setScreen(null); // Right Shift/Escape -> back to gameplay, mouse control restored
    }

    /** Jump straight here from Settings' "Show quick popup" OFF path on the next Right Shift press. */
    public static KrovexMenuScreen openDirectly() {
        return new KrovexMenuScreen();
    }

    private net.minecraft.client.option.KeyBinding capturingKeyBinding;
    private ButtonWidget capturingButton;
    private Text capturingOriginalLabel;
    private net.minecraft.client.util.InputUtil.Key pendingKey;
    private String conflictNames = "";

    /**
     * Starts rebind capture for a {@link net.minecraft.client.option.KeyBinding} from
     * within Theme (section 4's "rebind the Krovex menu key, synced with Minecraft
     * Controls"). The next key event this screen receives is bound to it via the same
     * {@code KeyBinding.setBoundKey}/{@code InputUtil.Keys.updateKeysByCode} calls
     * vanilla's own Controls screen uses, so both screens always agree on the binding.
     */
    public void beginKeyCapture(net.minecraft.client.option.KeyBinding binding, ButtonWidget sourceButton) {
        beginKeyCapture(binding, sourceButton, "Menu key");
    }

    private String capturingLabel = "Menu key";

    public void beginKeyCapture(net.minecraft.client.option.KeyBinding binding, ButtonWidget sourceButton, String label) {
        this.capturingLabel = label;
        this.capturingKeyBinding = binding;
        this.capturingButton = sourceButton;
        this.capturingOriginalLabel = sourceButton.getMessage();
        this.pendingKey = null;
        sourceButton.setMessage(Text.literal("Press a key\u2026 (Esc cancels)"));
    }

    private void endCapture() {
        capturingKeyBinding = null;
        capturingButton = null;
        capturingOriginalLabel = null;
        pendingKey = null;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (confirmQuestion != null) {          // the confirm popup is up: it owns the keyboard
            int code = input.key();
            if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || code == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) answerConfirm(true);
            else if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || code == org.lwjgl.glfw.GLFW.GLFW_KEY_N) answerConfirm(false);
            return true;
        }
        if (capturingKeyBinding != null) {
            if (pendingKey != null) {           // the Yes / No question is up: Enter or Y = yes, Esc or N = no
                int code = input.key();
                if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || code == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) bindCaptured(pendingKey);
                else if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || code == org.lwjgl.glfw.GLFW.GLFW_KEY_N) cancelCapture();
                return true;
            }
            if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {          // cancel: nothing changes
                cancelCapture();
                return true;
            }
            var key = net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(input.key());
            // Keybind Conflicts module: if another binding already uses this key, the button turns red, says what it
            // clashes with, and a Yes / No question decides whether to bind it anyway
            if (com.krovex.client.features.Features.on("keybind-conflicts")) {
                var others = com.krovex.client.features.KeybindConflicts.allUsing(key, capturingKeyBinding);
                if (!others.isEmpty()) {
                    pendingKey = key;
                    var names = new java.util.ArrayList<String>();
                    for (var other : others) names.add(com.krovex.client.features.KeybindConflicts.describe(other));
                    conflictNames = String.join(", ", names);
                    capturingButton.setMessage(Text.literal(key.getLocalizedText().getString() + " is conflicted with " + conflictNames)
                            .styled(style -> style.withColor(0xFF5C5C)));
                    return true;
                }
            }
            bindCaptured(key);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (confirmQuestion != null) {          // the popup takes every click
            int choice = confirmButtonAt(click.x(), click.y());
            if (choice == 1) answerConfirm(true);
            else if (choice == 2) answerConfirm(false);
            return true;
        }
        if (capturingKeyBinding != null && pendingKey != null) {       // the Yes / No question takes every click
            int choice = conflictButtonAt(click.x(), click.y());
            if (choice == 1) bindCaptured(pendingKey);
            else if (choice == 2) cancelCapture();
            return true;
        }
        if (activePanel != null && activePanel.mouseClicked(click.x(), click.y(), click.button())) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void onFilesDropped(java.util.List<java.nio.file.Path> paths) {
        if (activePanel != null) activePanel.filesDropped(paths);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (activePanel != null && activePanel.mouseScrolled(mouseX, mouseY, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        if (activePanel != null && activePanel.mouseDragged(click.x(), click.y(), click.button())) return true;
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (activePanel != null && activePanel.mouseReleased(click.x(), click.y(), click.button())) return true;
        return super.mouseReleased(click);
    }
}