package com.example.worldbrowser.gui;

import com.example.worldbrowser.WorldBrowser;
import com.example.worldbrowser.compat.ModCompatibilityResult;
import com.example.worldbrowser.compat.WorldBackupHelper;
import com.example.worldbrowser.model.ProfileInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public class WorldCompatibilityWarningScreen extends Screen {
    private final Screen parentScreen;
    private final String worldName;
    private final Path worldDir;
    private final ProfileInfo profile;
    private final ModCompatibilityResult result;
    private final Runnable onProceed;

    public WorldCompatibilityWarningScreen(Screen parentScreen, String worldName, Path worldDir, 
                                           ProfileInfo profile, ModCompatibilityResult result, Runnable onProceed) {
        super(Component.translatable("worldbrowser.warning.compat.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        this.parentScreen = parentScreen;
        this.worldName = worldName;
        this.worldDir = worldDir;
        this.profile = profile;
        this.result = result;
        this.onProceed = onProceed;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        // Title
        StringWidget titleWidget = new StringWidget(
                centerX - 150, 25, 300, 20,
                Component.translatable("worldbrowser.warning.compat.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                this.font
        );
        this.addRenderableWidget(titleWidget);

        // Warning text
        String pName = profile != null ? profile.getDisplayName() : "Unknown";
        Component intro = Component.translatable("worldbrowser.warning.compat.message", pName);
        Component details = Component.empty();
        if (result.hasMissingMods()) {
            details = details.copy().append("\n• ").append(Component.translatable("worldbrowser.badge.missing", result.getMissingCount()));
        }
        if (result.getMismatchCount() > 0) {
            details = details.copy().append("\n• ").append(Component.translatable("worldbrowser.badge.mismatch", result.getMismatchCount()));
        }

        MultiLineTextWidget messageWidget = new MultiLineTextWidget(
                centerX - 170, 50,
                intro.copy().append(details).withStyle(ChatFormatting.WHITE),
                this.font
        );
        messageWidget.setMaxWidth(340);
        this.addRenderableWidget(messageWidget);

        // Button: Details anzeigen
        this.addRenderableWidget(
                Button.builder(Component.translatable("worldbrowser.modlist.title", worldName), btn -> {
                    this.minecraft.setScreenAndShow(new WorldModListScreen(this, worldDir, profile, result));
                }).bounds(centerX - 150, this.height - 110, 300, 20).build()
        );

        // Button: Backup erstellen & Starten
        this.addRenderableWidget(
                Button.builder(Component.translatable("worldbrowser.warning.compat.backup_and_play").withStyle(ChatFormatting.GREEN), btn -> {
                    try {
                        Path backupPath = WorldBackupHelper.createBackup(worldDir, profile);
                        if (this.minecraft != null && this.minecraft.gui != null) {
                            SystemToast.add(this.minecraft.gui.toastManager(), SystemToast.SystemToastId.WORLD_BACKUP,
                                    Component.translatable("worldbrowser.warning.compat.backup_success", backupPath.getFileName().toString()),
                                    Component.empty());
                        }
                    } catch (Exception e) {
                        WorldBrowser.LOGGER.error("Fehler beim Erstellen des Backups: {}", e.getMessage());
                    }
                    onProceed.run();
                }).bounds(centerX - 150, this.height - 85, 300, 20).build()
        );

        // Button: Trotzdem starten
        this.addRenderableWidget(
                Button.builder(Component.translatable("worldbrowser.warning.compat.play_anyway").withStyle(ChatFormatting.RED), btn -> {
                    onProceed.run();
                }).bounds(centerX - 150, this.height - 60, 145, 20).build()
        );

        // Button: Abbrechen
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, btn -> this.minecraft.setScreenAndShow(parentScreen))
                        .bounds(centerX + 5, this.height - 60, 145, 20)
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
