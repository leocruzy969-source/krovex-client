package com.krovex.client.gui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;

/**
 * Swaps vanilla's pause screen for {@link EscapeMenuScreen} (which then picks the Krovex or classic look)
 * and gives the main Options screen the same look via {@link OptionsStyle}.
 */
public final class PauseMenuRegistration implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen.getClass() == OptionsScreen.class) OptionsStyle.apply(screen);
            if (screen.getClass() == GameMenuScreen.class && EscapeMenuScreen.shouldReplaceVanillaPauseScreen()) {
                boolean showMenu = ((GameMenuScreen) screen).shouldShowMenu();
                client.send(() -> {
                    if (client.currentScreen == screen) client.setScreen(new EscapeMenuScreen(showMenu));
                });
            }
        });
    }
}
