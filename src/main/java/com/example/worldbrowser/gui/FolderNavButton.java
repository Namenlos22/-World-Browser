package com.example.worldbrowser.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class FolderNavButton extends Button {
    public enum Type {
        ROOT,
        UP,
        CURRENT,
        CLEAR_RECENTS
    }

    private final Type type;

    public FolderNavButton(int x, int y, int width, int height, Type type, OnPress onPress, Tooltip tooltip) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.type = type;
        this.setTooltip(tooltip);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float tickDelta) {
        int bx = getX() + (getWidth() - 14) / 2;
        int by = getY() + (getHeight() - 11) / 2;

        int outlineColor = this.active ? 0xFF795548 : 0xFF555555;
        int tabColor = this.active ? 0xFFFFCA28 : 0xFF888888;
        int bodyColor = this.active ? 0xFFFFD54F : 0xFF9E9E9E;
        int highlightColor = this.active ? 0xFFFFF9C4 : 0xFFBDBDBD;
        int symbolColor = this.active ? 0xFF212121 : 0xFF616161;

        extractor.fill(bx + 1, by, bx + 6, by + 1, outlineColor);
        extractor.fill(bx + 1, by + 1, bx + 6, by + 2, tabColor);

        extractor.fill(bx, by + 2, bx + 14, by + 11, outlineColor);
        extractor.fill(bx + 1, by + 3, bx + 13, by + 10, bodyColor);
        extractor.fill(bx + 1, by + 3, bx + 13, by + 4, highlightColor);

        switch (type) {
            case ROOT -> {
                extractor.fill(bx + 9, by + 4, bx + 11, by + 5, symbolColor);
                extractor.fill(bx + 7, by + 5, bx + 9, by + 6, symbolColor);
                extractor.fill(bx + 6, by + 6, bx + 8, by + 7, symbolColor);
                extractor.fill(bx + 5, by + 7, bx + 7, by + 8, symbolColor);
                extractor.fill(bx + 3, by + 8, bx + 5, by + 9, symbolColor);
            }
            case UP -> {
                extractor.fill(bx + 6, by + 5, bx + 8, by + 8, symbolColor);
                extractor.fill(bx + 8, by + 7, bx + 11, by + 9, symbolColor);
                extractor.fill(bx + 4, by + 5, bx + 6, by + 7, symbolColor);
                extractor.fill(bx + 3, by + 6, bx + 5, by + 7, symbolColor);
                extractor.fill(bx + 6, by + 4, bx + 8, by + 5, symbolColor);
            }
            case CURRENT -> {
                int greenOutline = this.active ? 0xFF1B5E20 : 0xFF555555;
                int greenFill = this.active ? 0xFF4CAF50 : 0xFF888888;
                extractor.fill(bx + 4, by + 6, bx + 10, by + 8, greenOutline);
                extractor.fill(bx + 6, by + 4, bx + 8, by + 10, greenOutline);
                extractor.fill(bx + 5, by + 6, bx + 9, by + 7, greenFill);
                extractor.fill(bx + 6, by + 5, bx + 7, by + 9, greenFill);
            }
            case CLEAR_RECENTS -> {
                int redOutline = this.active ? 0xFFB71C1C : 0xFF555555;
                int redFill = this.active ? 0xFFE53935 : 0xFF888888;
                extractor.fill(bx + 4, by + 4, bx + 6, by + 6, redOutline);
                extractor.fill(bx + 8, by + 4, bx + 10, by + 6, redOutline);
                extractor.fill(bx + 5, by + 6, bx + 9, by + 8, redOutline);
                extractor.fill(bx + 4, by + 8, bx + 6, by + 10, redOutline);
                extractor.fill(bx + 8, by + 8, bx + 10, by + 10, redOutline);
                extractor.fill(bx + 5, by + 5, bx + 6, by + 6, redFill);
                extractor.fill(bx + 8, by + 5, bx + 9, by + 6, redFill);
                extractor.fill(bx + 6, by + 6, bx + 8, by + 8, redFill);
                extractor.fill(bx + 5, by + 8, bx + 6, by + 9, redFill);
                extractor.fill(bx + 8, by + 8, bx + 9, by + 9, redFill);
            }
        }
    }
}

