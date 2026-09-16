package com.example.worldbrowser.mixin;

import com.example.worldbrowser.compat.ModCompatibilityResult;
import com.example.worldbrowser.gui.WorldCompatibilityWarningScreen;
import com.example.worldbrowser.gui.WorldLockedWarningScreen;
import com.example.worldbrowser.gui.WorldModListScreen;
import com.example.worldbrowser.lock.WorldLockHelper;
import com.example.worldbrowser.registry.RegisteredWorld;
import com.example.worldbrowser.registry.WorldBrowserRegistry;
import com.example.worldbrowser.registry.WrappedLevelSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldListEntryMixin {

    @Shadow @Final private LevelSummary summary;
    @Shadow @Final private Screen screen;
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private StringWidget idAndLastPlayedText;

    @Shadow public abstract void joinWorld();

    // Display actual world directory name for external worlds instead of internal redirected ID
    @Inject(method = "<init>", at = @At("TAIL"))
    private void worldbrowser_fixIdLine(WorldSelectionList list, WorldSelectionList selectionList, LevelSummary summary, CallbackInfo ci) {
        if (!(summary instanceof WrappedLevelSummary wrapped)
                || wrapped.getProfile() == null
                || wrapped.getProfile().isCurrent()) {
            return;
        }
        String id = wrapped.getOriginal().getLevelId();
        long lastPlayed = wrapped.getLastPlayed();
        if (lastPlayed != -1L) {
            id += " (" + WorldSelectionList.DATE_FORMAT.format(
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastPlayed), ZoneId.systemDefault())) + ")";
        }
        this.idAndLastPlayedText.setMessage(Component.literal(id).withColor(-8355712));
        if (this.minecraft.font.width(id) > this.idAndLastPlayedText.getWidth()) {
            this.idAndLastPlayedText.setTooltip(Tooltip.create(Component.literal(id).withColor(-8355712)));
        }
    }

    @Unique
    private static final ThreadLocal<Boolean> WORLDBROWSER_BYPASS = ThreadLocal.withInitial(() -> false);

    @Inject(method = "joinWorld", at = @At("HEAD"), cancellable = true)
    private void worldbrowser_onJoinWorld(CallbackInfo ci) {
        if (WORLDBROWSER_BYPASS.get()) {
            if (summary instanceof WrappedLevelSummary wrapped && !wrapped.getProfile().isCurrent()) {
                WorldBrowserRegistry.getInstance().recordRecentWorld(wrapped.getWorldDir(), wrapped.getProfile());
            }
            return;
        }

        RegisteredWorld rw = null;
        if (summary instanceof WrappedLevelSummary wrapped) {
            rw = new RegisteredWorld(wrapped.getLevelId(), wrapped, wrapped.getProfile(), wrapped.getWorldDir());
        } else {
            rw = WorldBrowserRegistry.getInstance().getRegisteredWorld(summary.getLevelId());
        }

        if (rw != null) {
            Path worldDir = rw.worldDir();

            // 1. Session lock check
            if (WorldLockHelper.isWorldLocked(worldDir)) {
                ci.cancel();
                this.minecraft.setScreenAndShow(new WorldLockedWarningScreen(this.screen, summary.getLevelName()));
                return;
            }

            // 2. Mod compatibility check
            ModCompatibilityResult compatResult = rw.summary().getCompatibilityResult();
            if (compatResult != null && !compatResult.isFullyCompatible()) {
                boolean alreadyConfirmed = WorldBrowserRegistry.getInstance().isWorldCompatibilityConfirmed(worldDir);
                if (!alreadyConfirmed) {
                    ci.cancel();
                    this.minecraft.setScreenAndShow(new WorldCompatibilityWarningScreen(
                            this.screen,
                            summary.getLevelName(),
                            worldDir,
                            rw.profile(),
                            compatResult,
                            () -> {
                                WorldBrowserRegistry.getInstance().confirmIncompatibleWorld(worldDir);
                                WORLDBROWSER_BYPASS.set(true);
                                try {
                                    this.joinWorld();
                                } finally {
                                    WORLDBROWSER_BYPASS.set(false);
                                }
                            }
                    ));
                    return;
                }
            }

            // Record as recent external world shortcut for current profile
            if (rw.profile() != null && !rw.profile().isCurrent()) {
                WorldBrowserRegistry.getInstance().recordRecentWorld(worldDir, rw.profile());
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void worldbrowser_onMouseClicked(MouseButtonEvent event, boolean isHovered, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0) {
            return;
        }

        WorldSelectionList.WorldListEntry self = (WorldSelectionList.WorldListEntry) (Object) this;
        int x = self.getContentX();
        int y = self.getContentY();
        int width = self.getContentWidth();

        WrappedLevelSummary wrapped = null;
        if (summary instanceof WrappedLevelSummary w) {
            wrapped = w;
        } else {
            RegisteredWorld rw = WorldBrowserRegistry.getInstance().getRegisteredWorld(summary.getLevelId());
            if (rw != null && rw.summary() != null) {
                wrapped = rw.summary();
            }
        }

        if (wrapped != null && !wrapped.isLocked()) {
            ModCompatibilityResult compat = wrapped.getCompatibilityResult();
            if (compat != null) {
                Component badge = compat.getSummaryBadge();
                int badgeWidth = this.minecraft.font.width(badge);
                int rightX = x + width - 8;

                int boxX1 = rightX - badgeWidth - 3;
                int boxX2 = rightX + 3;
                int boxY1 = y;
                int boxY2 = y + 14;

                double mx = event.x();
                double my = event.y();

                if (mx >= boxX1 && mx <= boxX2 && my >= boxY1 && my <= boxY2) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    this.minecraft.setScreenAndShow(new WorldModListScreen(
                            this.screen,
                            wrapped.getWorldDir(),
                            wrapped.getProfile(),
                            compat
                    ));
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    @Inject(method = "extractContent", at = @At("TAIL"))
    private void worldbrowser_onExtractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, boolean isHovered, float tickDelta, CallbackInfo ci) {
        WorldSelectionList.WorldListEntry self = (WorldSelectionList.WorldListEntry) (Object) this;
        int x = self.getContentX();
        int y = self.getContentY();
        int width = self.getContentWidth();

        if (summary instanceof WrappedLevelSummary wrapped) {
            boolean isExternal = wrapped.getProfile() != null && !wrapped.getProfile().isCurrent();

            if (isExternal) {
                extractor.fill(x, y + 1, x + 2, y + 33, 0xFFFFB300);

                extractor.fill(x + 23, y + 23, x + 27, y + 25, 0xFFFF8F00);
                extractor.fill(x + 22, y + 25, x + 32, y + 31, 0xFFFFB300);
                extractor.fill(x + 22, y + 27, x + 32, y + 31, 0xFFFFD54F);
                extractor.fill(x + 22, y + 31, x + 32, y + 32, 0xFF795548);
            }

            int rightX = x + width - 8;

            // Status badges (Locked or Mod Compatibility)
            if (wrapped.isLocked()) {
                Component badge = WorldLockHelper.getLockedBadge();
                int badgeWidth = this.minecraft.font.width(badge);
                rightX -= badgeWidth;
                extractor.textRenderer().accept(rightX, y + 2, badge);
                rightX -= 5;
            } else {
                ModCompatibilityResult compat = wrapped.getCompatibilityResult();
                if (compat != null) {
                    Component badge = compat.getSummaryBadge();
                    int badgeWidth = this.minecraft.font.width(badge);
                    rightX -= badgeWidth;

                    int boxX1 = rightX - 3;
                    int boxX2 = rightX + badgeWidth + 3;
                    int boxY1 = y + 1;
                    int boxY2 = y + 12;

                    boolean isBadgeHovered = isHovered && mouseX >= boxX1 && mouseX <= boxX2 && mouseY >= boxY1 && mouseY <= boxY2;

                    if (isBadgeHovered) {
                        extractor.fill(boxX1, boxY1, boxX2, boxY2, 0x40FFFFFF);
                        extractor.fill(boxX1, boxY1, boxX2, boxY1 + 1, 0x80FFFFFF);
                        extractor.fill(boxX1, boxY2 - 1, boxX2, boxY2, 0x80FFFFFF);
                        extractor.fill(boxX1, boxY1, boxX1 + 1, boxY2, 0x80FFFFFF);
                        extractor.fill(boxX2 - 1, boxY1, boxX2, boxY2, 0x80FFFFFF);
                        extractor.setTooltipForNextFrame(Component.translatable("worldbrowser.badge.compat.tooltip"), mouseX, mouseY);
                    } else {
                        extractor.fill(boxX1, boxY1, boxX2, boxY2, 0x25000000);
                    }

                    extractor.textRenderer().accept(rightX, y + 2, badge);
                    rightX -= 6;
                }
            }

            if (isExternal) {
                Component extBadge = Component.translatable("worldbrowser.badge.external")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                int extWidth = this.minecraft.font.width(extBadge);
                rightX -= extWidth;
                extractor.textRenderer().accept(rightX, y + 2, extBadge);

                if (isHovered && mouseX >= rightX && mouseX <= rightX + extWidth && mouseY >= y && mouseY <= y + 15) {
                    Component tooltipText = Component.translatable("worldbrowser.tooltip.origin_path",
                            wrapped.getWorldDir().toAbsolutePath().normalize().toString())
                            .withStyle(ChatFormatting.YELLOW);
                    extractor.setTooltipForNextFrame(tooltipText, mouseX, mouseY);
                }
            }
        }
    }
}
