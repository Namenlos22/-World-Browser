package com.example.worldbrowser.model;

import net.minecraft.network.chat.Component;

public enum LauncherType {
    VANILLA("Vanilla Minecraft", "Standard .minecraft Launcher-Profile", "vanilla"),
    MODRINTH("Modrinth App", "Modrinth App Profile und Instanzen", "modrinth"),
    CURSEFORGE("CurseForge", "CurseForge Minecraft Instanzen", "curseforge"),
    CURRENT("Aktuelles Profil", "Das aktuell laufende Minecraft-Profil", "current");

    private final String displayName;
    private final String description;
    private final String id;

    LauncherType(String displayName, String description, String id) {
        this.displayName = displayName;
        this.description = description;
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getId() {
        return id;
    }

    public Component getComponent() {
        return Component.translatable("worldbrowser.launcher." + id);
    }
}
