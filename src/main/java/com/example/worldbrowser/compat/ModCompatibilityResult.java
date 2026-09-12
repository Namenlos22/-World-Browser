package com.example.worldbrowser.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

public class ModCompatibilityResult {
    private final List<ModCompatibilityEntry> entries;
    private final int missingCount;
    private final int mismatchCount;
    private final int compatibleCount;

    public ModCompatibilityResult(List<ModCompatibilityEntry> entries) {
        this.entries = entries != null ? entries : Collections.emptyList();
        int missing = 0;
        int mismatch = 0;
        int compatible = 0;
        for (ModCompatibilityEntry e : this.entries) {
            if (e.getStatus() == ModCompatibilityStatus.MISSING_IN_CURRENT) {
                missing++;
            } else if (e.getStatus() == ModCompatibilityStatus.VERSION_MISMATCH) {
                mismatch++;
            } else if (e.getStatus() == ModCompatibilityStatus.COMPATIBLE) {
                compatible++;
            }
        }
        this.missingCount = missing;
        this.mismatchCount = mismatch;
        this.compatibleCount = compatible;
    }

    public List<ModCompatibilityEntry> getEntries() {
        return entries;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public int getMismatchCount() {
        return mismatchCount;
    }

    public int getCompatibleCount() {
        return compatibleCount;
    }

    public boolean isFullyCompatible() {
        return missingCount == 0 && mismatchCount == 0;
    }

    public boolean hasMissingMods() {
        return missingCount > 0;
    }

    public Component getSummaryBadge() {
        if (entries.isEmpty()) {
            return Component.translatable("worldbrowser.badge.none").withStyle(ChatFormatting.DARK_GRAY);
        }
        if (missingCount > 0) {
            return Component.translatable("worldbrowser.badge.missing", missingCount).withStyle(ChatFormatting.RED);
        }
        if (mismatchCount > 0) {
            return Component.translatable("worldbrowser.badge.mismatch", mismatchCount).withStyle(ChatFormatting.GOLD);
        }
        return Component.translatable("worldbrowser.badge.compatible", compatibleCount).withStyle(ChatFormatting.GREEN);
    }
}
