package com.krovex.client.gui;

import com.krovex.client.KrovexClientMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class QuickPopupScreen extends Screen {
    public QuickPopupScreen() { super(Text.literal("Krovex Client")); }

    @Override protected void init() {
        String key = KrovexClientMod.KROVEX_MENU_KEY.getBoundKeyLocalizedText().getString();
        addDrawableChild(MenuStyle.button(key, width / 2 - 42, height / 2, 84, 23, MenuStyle.PURPLE,
                () -> client.setScreen(new KrovexMenuScreen())));
    }

    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        int x = width / 2 - 86, y = height / 2 - 54;
        MenuStyle.glass(c, x, y, 172, 110, 12);
        c.fillGradient(x + 10, y + 2, x + 162, y + 35, 0x494D3667, 0x004D3667);
        MenuStyle.logo(c, x + 32, y + 7, 25);
        MenuStyle.text(c, "KROVEX", x + 61, y + 13, MenuStyle.WHITE, 1.65f);
        MenuStyle.text(c, "Open Menu", x + 61, y + 35, MenuStyle.MUTED, 1);
        MenuStyle.text(c, "Press to open the KROVEX menu", x + 16, y + 90, MenuStyle.MUTED, .92f);
        super.render(c, mouseX, mouseY, delta);
    }

    @Override public void renderBackground(DrawContext context, int x, int y, float delta) {}
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (client != null) client.setScreen(null); }
    public void handleKrovexKeyPressedWhileOpen() { close(); }
}

