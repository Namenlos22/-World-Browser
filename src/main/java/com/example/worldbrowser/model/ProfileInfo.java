package com.example.worldbrowser.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ProfileInfo {
    private final LauncherType launcherType;
    private final String profileId;
    private final String displayName;
    private final Path gameDir;
    private final Path savesDir;
    private final Path modsDir;
    private final Path iconPath;
    private final String lastVersionId;
    private final boolean isCurrent;
    private List<ModInfo> mods = null;

    public ProfileInfo(LauncherType launcherType, String profileId, String displayName, 
                       Path gameDir, Path savesDir, Path modsDir, Path iconPath, 
                       String lastVersionId, boolean isCurrent) {
        this.launcherType = launcherType;
        this.profileId = profileId;
        this.displayName = displayName != null && !displayName.isBlank() ? displayName : profileId;
        this.gameDir = gameDir;
        this.savesDir = savesDir;
        this.modsDir = modsDir;
        this.iconPath = iconPath;
        this.lastVersionId = lastVersionId != null ? lastVersionId : "";
        this.isCurrent = isCurrent;
    }

    public LauncherType getLauncherType() {
        return launcherType;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Path getGameDir() {
        return gameDir;
    }

    public Path getSavesDir() {
        return savesDir;
    }

    public Path getModsDir() {
        return modsDir;
    }

    public Path getIconPath() {
        return iconPath;
    }

    public String getLastVersionId() {
        return lastVersionId;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    private volatile int cachedModCount = -1;
    private volatile int cachedWorldCount = -1;

    public List<ModInfo> getMods() {
        if (mods == null) {
            mods = com.example.worldbrowser.scanner.ModScanner.scanMods(modsDir);
            cachedModCount = mods.size();
        }
        return Collections.unmodifiableList(mods);
    }

    public int getModCount() {
        if (cachedModCount >= 0) {
            return cachedModCount;
        }
        if (mods != null) {
            cachedModCount = mods.size();
            return cachedModCount;
        }
        if (modsDir == null || !java.nio.file.Files.isDirectory(modsDir)) {
            cachedModCount = 0;
            return 0;
        }
        try (java.nio.file.DirectoryStream<Path> stream = java.nio.file.Files.newDirectoryStream(modsDir, "*.jar")) {
            int count = 0;
            for (Path ignored : stream) {
                count++;
            }
            cachedModCount = count;
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getCachedWorldCount() {
        return cachedWorldCount;
    }

    public void setCachedWorldCount(int count) {
        this.cachedWorldCount = count;
    }

    public void setCachedModCount(int count) {
        this.cachedModCount = count;
    }

    public void setMods(List<ModInfo> mods) {
        this.mods = mods != null ? new ArrayList<>(mods) : null;
        this.cachedModCount = this.mods != null ? this.mods.size() : -1;
    }

    public String getUniqueKey() {
        return launcherType.getId() + ":" + profileId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileInfo that = (ProfileInfo) o;
        return launcherType == that.launcherType && Objects.equals(profileId, that.profileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(launcherType, profileId);
    }
}
