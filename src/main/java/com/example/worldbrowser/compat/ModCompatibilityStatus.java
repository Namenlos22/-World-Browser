package com.example.worldbrowser.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum ModCompatibilityStatus {
    COMPATIBLE("✔", "worldbrowser.modlist.status.compatible", ChatFormatting.GREEN),
    VERSION_MISMATCH("⚠", "worldbrowser.modlist.status.version", ChatFormatting.GOLD),
    MISSING_IN_CURRENT("✖", "worldbrowser.modlist.status.missing", ChatFormatting.RED),
    EXTRA_IN_CURRENT("+", "worldbrowser.modlist.status.extra", ChatFormatting.GRAY);

    private final String symbol;
    private final String translationKey;
    private final ChatFormatting color;

    ModCompatibilityStatus(String symbol, String translationKey, ChatFormatting color) {
        this.symbol = symbol;
        this.translationKey = translationKey;
        this.color = color;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public String getSymbol() {
        return symbol;
    }

    public Component getFormattedComponent() {
        return Component.translatable(translationKey).withStyle(color);
    }
}
