package com.krovex.client.gui.screens;

import com.krovex.client.gui.MenuStyle;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public final class AboutPanel implements KrovexSubPanel {
    public void createWidgets(Screen host, int x, int y, int w, int h) {}

    public void render(DrawContext c, int x, int y, int w, int h, int mx, int my, float delta) {
        var loader = FabricLoader.getInstance();
        String version = loader.getModContainer("krovex-client").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        String minecraft = loader.getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("1.21.11");
        String fabric = loader.getModContainer("fabricloader").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");

        MenuStyle.card(c, x, y + 6, w, 74, 9, MenuStyle.EDGE, MenuStyle.CARD);
        MenuStyle.gradientCard(c, x + 10, y + 16, 54, 54, 10, 0xFFC17AFE, 0xFF4A2A8C, 0xFF21103F);
        MenuStyle.logo(c, x + 18, y + 24, 38);
        MenuStyle.text(c, "KROVEX GAME CLIENT", x + 76, y + 18, MenuStyle.WHITE, 1.5f);
        MenuStyle.text(c, "A custom client for Minecraft Java Edition " + minecraft + " on Fabric.", x + 76, y + 40, MenuStyle.MUTED, 1);
        var pillFont = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        int pillWidth = pillFont.getWidth("Version " + version) + 16;
        MenuStyle.box(c, x + 76, y + 55, pillWidth, 17, 8, 0x55C4FF42);
        MenuStyle.text(c, "Version " + version, x + 84, y + 59, MenuStyle.LIME, .9f);

        String[][] rows = {
                {"Client version", version},
                {"Minecraft", minecraft},
                {"Fabric Loader", fabric},
                {"Java", System.getProperty("java.version", "?")},
                {"Mods loaded", Integer.toString(loader.getAllMods().size())}};
        int top = y + 90;
        MenuStyle.card(c, x, top, w, rows.length * 20 + 16, 9, MenuStyle.EDGE, MenuStyle.PANEL);
        MenuStyle.text(c, "Environment", x + 12, top + 8, MenuStyle.WHITE, 1.16f);
        for (int i = 0; i < rows.length; i++) {
            int ry = top + 26 + i * 18;
            if (i > 0) c.fill(x + 10, ry - 4, x + w - 10, ry - 3, 0x30FFFFFF);
            MenuStyle.text(c, rows[i][0], x + 14, ry, MenuStyle.MUTED, 1);
            MenuStyle.text(c, rows[i][1], x + w / 2, ry, MenuStyle.WHITE, 1);
        }
    }
}
