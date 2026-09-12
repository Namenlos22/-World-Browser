package com.example.worldbrowser.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class WorldLockedWarningScreen extends Screen {
    private final Screen parentScreen;
    private final String worldName;

    public WorldLockedWarningScreen(Screen parentScreen, String worldName) {
        super(Component.translatable("worldbrowser.warning.locked.title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        this.parentScreen = parentScreen;
        this.worldName = worldName;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        // Title
        StringWidget titleWidget = new StringWidget(
                centerX - 150, 40, 300, 20,
                Component.translatable("worldbrowser.warning.locked.title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                this.font
        );
        this.addRenderableWidget(titleWidget);

        // Warning message
        Component message = Component.translatable("worldbrowser.warning.locked.message")
                .append("\n\n")
                .append(Component.translatable("worldbrowser.warning.locked.caution"));

        MultiLineTextWidget messageWidget = new MultiLineTextWidget(
                centerX - 160, 75,
                message.copy().withStyle(ChatFormatting.WHITE),
                this.font
        );
        messageWidget.setMaxWidth(320);
        this.addRenderableWidget(messageWidget);

        // Back button
        this.addRenderableWidget(
                Button.builder(Component.translatable("worldbrowser.warning.locked.back"), btn -> this.minecraft.setScreenAndShow(parentScreen))
                        .bounds(centerX - 100, this.height - 45, 200, 20)
                        .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float tickDelta) {
        extractor.fill(0, 0, this.width, this.height, 0xD0000000);
        super.extractRenderState(extractor, mouseX, mouseY, tickDelta);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parentScreen);
    }
}
