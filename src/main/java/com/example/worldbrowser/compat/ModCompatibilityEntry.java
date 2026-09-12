package com.example.worldbrowser.compat;

public class ModCompatibilityEntry {
    private final String modId;
    private final String modName;
    private final String worldVersion;
    private final String currentVersion;
    private final ModCompatibilityStatus status;

    public ModCompatibilityEntry(String modId, String modName, String worldVersion, String currentVersion, ModCompatibilityStatus status) {
        this.modId = modId;
        this.modName = modName;
        this.worldVersion = worldVersion;
        this.currentVersion = currentVersion;
        this.status = status;
    }

    public String getModId() {
        return modId;
    }

    public String getModName() {
        return modName;
    }

    public String getWorldVersion() {
        return worldVersion;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public ModCompatibilityStatus getStatus() {
        return status;
    }
}
