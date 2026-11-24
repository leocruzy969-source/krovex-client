package com.krovex.client.gui;

import static com.krovex.client.gui.MenuStyle.lettering;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.KrovexConfigPaths;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Krovex's pause screen. It always stands in for vanilla's (unless Essential is installed), keeps
 * vanilla's own buttons and behaviour for the current game mode, and has two looks:
 *
 *  - Krovex look: a glass panel with a header, and every pause button restyled like the Krovex menu
 *    (that includes "Options..." and the rest of the game actions).
 *  - Classic look: exactly vanilla's.
 *
 * A switch in the bottom-left corner flips between them. It is shown in BOTH looks, so you can always
 * get back, the same way a mod is switched on and off in the launcher.
 */
public final class EscapeMenuScreen extends GameMenuScreen {
    private boolean krovexLook;
    private final List<ButtonWidget> restyled = new ArrayList<>();
    private int panelLeft, panelTop, panelRight, panelBottom, buttonsTop;

    public EscapeMenuScreen(boolean showMenu) {
        super(showMenu);
    }

    public static boolean shouldReplaceVanillaPauseScreen() {
        return !FabricLoader.getInstance().isModLoaded("essential");
    }

    @Override
    protected void init() {
        super.init(); // vanilla's own buttons, layout and behaviour for the current game mode
        krovexLook = KrovexClientMod.escapeMenuSettings().customPauseAppearanceEnabled();
        restyled.clear();
        if (krovexLook) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;
            for (Element element : children()) {
                if (!(element instanceof ButtonWidget button) || button.getMessage().getString().isEmpty()) continue;
                // Keep the real button (it still handles clicks, hover and focus) but make it
                // practically invisible; render() paints the Krovex style over it. Alpha must stay
                // above ~1.5% or Minecraft treats the label's colour as fully opaque.
                button.setAlpha(.02f);
                restyled.add(button);
                minX = Math.min(minX, button.getX());
                minY = Math.min(minY, button.getY());
                maxX = Math.max(maxX, button.getX() + button.getWidth());
                maxY = Math.max(maxY, button.getY() + button.getHeight());
            }
            if (!restyled.isEmpty()) {
                panelLeft = Math.max(4, minX - 16);
                panelRight = Math.min(width - 4, maxX + 16);
                panelTop = Math.max(4, Math.min(34, minY - 60));
                buttonsTop = minY;
                panelBottom = Math.min(height - 4, maxY + 16);
            }
        }
        addDrawableChild(MenuStyle.toggle("", 10, height - 26, 30, 16, krovexLook, () -> {
            var settings = KrovexClientMod.escapeMenuSettings();
            settings.setCustomPauseAppearanceEnabled(!krovexLook);
            settings.save(KrovexConfigPaths.escapeMenuSettingsFile());
            MinecraftClient.getInstance().setScreen(new EscapeMenuScreen(shouldShowMenu()));
        }));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        if (krovexLook) context.fill(0, 0, width, height, 0x6612091F);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (krovexLook && !restyled.isEmpty()) paintKrovexLook(context);
        // the on/off switch's label, same for both looks
        MenuStyle.text(context, "Krovex pause menu", 46, height - 22, MenuStyle.WHITE, 1);
    }

    private void paintKrovexLook(DrawContext c) {
        // glass panel; opaque enough that vanilla's "Game Menu" title behind it is fully hidden
        for (int i = 3; i > 0; i--) {
            MenuStyle.card(c, panelLeft - i, panelTop - i, panelRight - panelLeft + 2 * i,
                    panelBottom - panelTop + 2 * i, 10 + i, 0x157D42DC, 0);
        }
        MenuStyle.card(c, panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 10, 0xDDAD7AE3, 0xF21A1331);
        c.fillGradient(panelLeft + 8, panelTop + 2, panelRight - 8, panelTop + 40, 0x494D3667, 0x004D3667);
        MenuStyle.logo(c, panelLeft + 16, panelTop + 9, 24);
        MenuStyle.text(c, "KROVEX", panelLeft + 46, panelTop + 11, MenuStyle.WHITE, 1.5f);
        MenuStyle.text(c, "Game paused", panelLeft + 46, panelTop + 29, MenuStyle.MUTED, .95f);
        // player card in the space between the header and the buttons
        var mc = MinecraftClient.getInstance();
        int cardTop = panelTop + 48, cardBottom = buttonsTop - 10;
        if (mc.player != null && cardBottom - cardTop >= 28) {
            int cardH = Math.min(56, cardBottom - cardTop), head = Math.min(36, cardH - 12);
            MenuStyle.card(c, panelLeft + 16, cardTop, panelRight - panelLeft - 32, cardH, 8, MenuStyle.EDGE, MenuStyle.CARD);
            net.minecraft.client.gui.PlayerSkinDrawer.draw(c, mc.player.getSkin(), panelLeft + 24, cardTop + (cardH - head) / 2, head);
            MenuStyle.text(c, mc.getSession().getUsername(), panelLeft + 30 + head, cardTop + cardH / 2 - 11, MenuStyle.WHITE, 1.3f);
            MenuStyle.text(c, mc.isInSingleplayer() ? "Singleplayer" : "Multiplayer", panelLeft + 30 + head, cardTop + cardH / 2 + 4, MenuStyle.MUTED, .95f);
        }

        var font = MinecraftClient.getInstance().textRenderer;
        for (int i = 0; i < restyled.size(); i++) {
            ButtonWidget button = restyled.get(i);
            String name = button.getMessage().getString();
            boolean primary = i == 0, leaving = name.startsWith("Save and Quit") || name.startsWith("Disconnect");
            boolean hover = button.isHovered() || button.isFocused();
            int fill = !button.active ? MenuStyle.PANEL
                    : primary ? MenuStyle.LIME
                    : leaving ? (hover ? 0xFF8A3050 : 0xFF6B2540)
                    : hover ? 0xFF4B3A72 : 0xFF2B2145;
            int edge = hover && button.active ? 0xFFC17AFE : MenuStyle.EDGE;
            MenuStyle.card(c, button.getX(), button.getY(), button.getWidth(), button.getHeight(), 5, edge, fill);
            Text label = lettering(button.getMessage(), primary);
            int textWidth = font.getWidth(label);
            int color = primary && button.active ? 0xFF17101E : button.active ? MenuStyle.WHITE : MenuStyle.MUTED;
            c.enableScissor(button.getX() + 2, button.getY(), button.getX() + button.getWidth() - 2, button.getY() + button.getHeight());
            c.drawText(font, label, button.getX() + Math.max(2, (button.getWidth() - textWidth) / 2),
                    button.getY() + (button.getHeight() - 8) / 2, color, false);
            c.disableScissor();
        }
    }
}
