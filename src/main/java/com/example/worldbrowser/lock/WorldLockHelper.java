package com.example.worldbrowser.lock;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.DirectoryLock;

import java.nio.file.Files;
import java.nio.file.Path;

public class WorldLockHelper {

    public static boolean isWorldLocked(Path worldDir) {
        if (worldDir == null || !Files.isDirectory(worldDir)) {
            return false;
        }

        Path lockFile = worldDir.resolve("session.lock");
        if (!Files.exists(lockFile)) {
            return false;
        }

        // Use Minecraft's native DirectoryLock.isLocked
        try {
            return DirectoryLock.isLocked(worldDir);
        } catch (Exception e) {
            // Only return true if actually locked, not on transient read issues
            return false;
        }
    }

    public static Component getLockedBadge() {
        return Component.translatable("worldbrowser.badge.locked").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
    }
}
