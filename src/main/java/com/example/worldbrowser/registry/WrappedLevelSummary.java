package com.example.worldbrowser.registry;

import com.example.worldbrowser.lock.WorldLockHelper;
import com.example.worldbrowser.model.ProfileInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.LevelVersion;

import java.nio.file.Path;

public class WrappedLevelSummary extends LevelSummary {
    private final LevelSummary original;
    private final String customLevelId;
    private final ProfileInfo profile;
    private final Path worldDir;
    private com.example.worldbrowser.compat.ModCompatibilityResult cachedCompatResult;
    private Boolean cachedLocked = null;

    public WrappedLevelSummary(LevelSummary original, String customLevelId, ProfileInfo profile, Path worldDir) {
        super(
                original.getSettings(),
                original.levelVersion(),
                customLevelId,
                original.requiresManualConversion(),
                original.requiresFileFixing(),
                original.isLocked() || WorldLockHelper.isWorldLocked(worldDir),
                original.isExperimental(),
                original.getIcon()
        );
        this.original = original;
        this.customLevelId = customLevelId;
        this.profile = profile;
        this.worldDir = worldDir;
    }

    public LevelSummary getOriginal() {
        return original;
    }

    public ProfileInfo getProfile() {
        return profile;
    }

    public Path getWorldDir() {
        return worldDir;
    }

    public com.example.worldbrowser.compat.ModCompatibilityResult getCompatibilityResult() {
        if (cachedCompatResult == null) {
            cachedCompatResult = com.example.worldbrowser.compat.ModCompatibilityChecker.checkCompatibility(worldDir, profile);
        }
        return cachedCompatResult;
    }

    @Override
    public String getLevelId() {
        return customLevelId;
    }

    @Override
    public String getLevelName() {
        return original.getLevelName();
    }

    @Override
    public Path getIcon() {
        return original.getIcon();
    }

    @Override
    public boolean requiresManualConversion() {
        return original.requiresManualConversion();
    }

    @Override
    public boolean requiresFileFixing() {
        return original.requiresFileFixing();
    }

    @Override
    public boolean isExperimental() {
        return original.isExperimental();
    }

    @Override
    public long getLastPlayed() {
        return original.getLastPlayed();
    }

    @Override
    public int compareTo(LevelSummary other) {
        return original.compareTo(other instanceof WrappedLevelSummary w ? w.original : other);
    }

    @Override
    public LevelSettings getSettings() {
        return original.getSettings();
    }

    @Override
    public GameType getGameMode() {
        return original.getGameMode();
    }

    @Override
    public boolean isHardcore() {
        return original.isHardcore();
    }

    @Override
    public boolean hasCommands() {
        return original.hasCommands();
    }

    @Override
    public MutableComponent getWorldVersionName() {
        return original.getWorldVersionName();
    }

    @Override
    public LevelVersion levelVersion() {
        return original.levelVersion();
    }

    @Override
    public boolean shouldBackup() {
        return original.shouldBackup();
    }

    @Override
    public boolean isDowngrade() {
        return original.isDowngrade();
    }

    @Override
    public BackupStatus backupStatus() {
        return original.backupStatus();
    }

    @Override
    public boolean isLocked() {
        if (cachedLocked == null) {
            cachedLocked = original.isLocked() || WorldLockHelper.isWorldLocked(worldDir);
        }
        return cachedLocked;
    }

    @Override
    public boolean isDisabled() {
        return original.isDisabled() || isLocked();
    }

    @Override
    public boolean isCompatible() {
        return original.isCompatible();
    }

    @Override
    public Component getInfo() {
        return original.getInfo();
    }

    @Override
    public Component primaryActionMessage() {
        if (isLocked()) {
            return WorldLockHelper.getLockedBadge();
        }
        return original.primaryActionMessage();
    }

    @Override
    public boolean primaryActionActive() {
        // Nicht !isLocked() - sonst sind gelockte Welten nicht interagierbar und der
        // Locked-Warndialog in WorldListEntryMixin.joinWorld wird nie erreicht.
        return original.primaryActionActive();
    }

    @Override
    public boolean canUpload() {
        return original.canUpload();
    }

    @Override
    public boolean canEdit() {
        return original.canEdit();
    }

    @Override
    public boolean canRecreate() {
        return original.canRecreate();
    }

    @Override
    public boolean canDelete() {
        return original.canDelete();
    }
}
