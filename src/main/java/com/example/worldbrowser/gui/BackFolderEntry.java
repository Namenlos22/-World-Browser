package com.example.worldbrowser.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;

public class BackFolderEntry extends WorldSelectionList.Entry {
    private final Minecraft minecraft;
    private final Component title;
    private final Runnable onBack;

    public BackFolderEntry(Minecraft minecraft, Component targetFolder, Runnable onBack) {
        this.minecraft = minecraft;
        this.title = Component.literal(".. ").append(targetFolder).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        this.onBack = onBack;
    }

    public BackFolderEntry(Minecraft minecraft, String targetName, Runnable onBack) {
        this(minecraft, Component.literal(targetName), onBack);
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
        extractor.fill(fx, fy + 10, fx + 28, fy + 26, 0xFFFFD54F);
        extractor.fill(fx + 1, fy + 11, fx + 27, fy + 12, 0xFFFFF9C4);

        extractor.fill(fx + 13, fy + 13, fx + 15, fy + 23, 0xFF37474F);
        extractor.fill(fx + 11, fy + 15, fx + 17, fy + 17, 0xFF37474F);
        extractor.fill(fx + 9, fy + 17, fx + 19, fy + 19, 0xFF37474F);

        extractor.fill(fx + 1, fy + 1, fx + 13, fy + 2, 0xFF795548);
        extractor.fill(fx + 1, fy + 5, fx + 29, fy + 6, 0xFF795548);
        extractor.fill(fx + 28, fy + 6, fx + 29, fy + 26, 0xFF795548);
        extractor.fill(fx + 1, fy + 26, fx + 29, fy + 27, 0xFF795548);
        extractor.fill(fx, fy + 10, fx + 1, fy + 26, 0xFF795548);

        int textX = x + 35;
        extractor.textRenderer().accept(textX, y + 13, title);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isHovered) {
        if (event.button() == 0 && isHovered) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            onBack.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            onBack.run();
            return true;
        }
        return super.keyPressed(event);
    }
}
