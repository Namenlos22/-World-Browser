package com.example.worldbrowser.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import com.example.worldbrowser.registry.NavigationState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.LevelSummary;

public class FolderListEntry extends WorldSelectionList.Entry {
    private static final int KEY_SPACE = 32;
    private static final int KEY_ENTER = 257;
    private static final int KEY_KP_ENTER = 335;
    private final Minecraft minecraft;
    private final Component title;
    private final Component subtitle;
    private final Runnable onOpen;

    public FolderListEntry(Minecraft minecraft, Component title, Component subtitle, Runnable onOpen) {
        this.minecraft = minecraft;
        this.title = title;
        this.subtitle = subtitle;
        this.onOpen = onOpen;
    }

    @Override
    public LevelSummary getLevelSummary() {
        return null;
    }

    @Override
    public void close() {
    }

    @Override
    public Component getNarration() {
        return title;
    }

    @Override
    public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
        int x = getContentX();
        int y = getContentY();

        int fx = x + 2;
        int fy = y + 2;

        extractor.fill(fx + 2, fy + 2, fx + 12, fy + 8, 0xFFFFA000);
        extractor.fill(fx + 2, fy + 6, fx + 28, fy + 26, 0xFFFFB300);
        extractor.fill(fx + 6, fy + 4, fx + 24, fy + 12, 0xFFF5F5F5);
        extractor.fill(fx + 8, fy + 6, fx + 20, fy + 7, 0xFF9E9E9E);
        extractor.fill(fx + 8, fy + 9, fx + 16, fy + 10, 0xFF9E9E9E);
        extractor.fill(fx, fy + 10, fx + 28, fy + 26, 0xFFFFD54F);
        extractor.fill(fx + 1, fy + 11, fx + 27, fy + 12, 0xFFFFF9C4);
        extractor.fill(fx, fy + 25, fx + 28, fy + 26, 0xFFC79100);
        extractor.fill(fx, fy + 10, fx + 1, fy + 25, 0xFFC79100);
        extractor.fill(fx + 27, fy + 10, fx + 28, fy + 25, 0xFFC79100);
        extractor.fill(fx + 1, fy + 1, fx + 13, fy + 2, 0xFF795548);
        extractor.fill(fx + 1, fy + 5, fx + 29, fy + 6, 0xFF795548);
        extractor.fill(fx + 28, fy + 6, fx + 29, fy + 26, 0xFF795548);
        extractor.fill(fx + 1, fy + 26, fx + 29, fy + 27, 0xFF795548);
        extractor.fill(fx, fy + 10, fx + 1, fy + 26, 0xFF795548);

        if (isHovered) {
            extractor.fill(x - 2, y - 2, x + getContentWidth() + 2, y + getContentHeight() + 2, 0x15FFFFFF);
        }
        if (isFocused()) {
            extractor.outline(x - 2, y - 2, getContentWidth() + 4, getContentHeight() + 4, 0x80FFFFFF);
        }

        int textX = x + 35;
        extractor.textRenderer().accept(textX, y + 4, title);
        if (subtitle != null) {
            extractor.textRenderer().accept(textX, y + 17, subtitle);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (!NavigationState.recordNavigation()) {
                return true;
            }
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            System.out.println("[WorldBrowser] Opened folder: " + title.getString());
            onOpen.run();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == KEY_ENTER || event.key() == KEY_KP_ENTER || event.key() == KEY_SPACE) {
            if (!NavigationState.recordNavigation()) {
                return true;
            }
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            System.out.println("[WorldBrowser] Opened folder (key): " + title.getString());
            onOpen.run();
            return true;
        }
        return super.keyPressed(event);
    }
}
