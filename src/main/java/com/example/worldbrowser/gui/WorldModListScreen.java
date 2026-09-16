package com.example.worldbrowser.gui;

import com.example.worldbrowser.compat.ModCompatibilityEntry;
import com.example.worldbrowser.compat.ModCompatibilityResult;
import com.example.worldbrowser.compat.ModCompatibilityStatus;
import com.example.worldbrowser.model.ProfileInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public class WorldModListScreen extends Screen {
    private final Screen parentScreen;
    private final Path worldDir;
    private final ProfileInfo profile;
    private final ModCompatibilityResult result;

    private ModSelectionList modSelectionList;
    private EditBox searchBox;

    public WorldModListScreen(Screen parentScreen, Path worldDir, ProfileInfo profile, ModCompatibilityResult result) {
        super(Component.translatable("worldbrowser.modlist.window_title").withStyle(ChatFormatting.BOLD));
        this.parentScreen = parentScreen;
        this.worldDir = worldDir;
        this.profile = profile;
        this.result = result;
    }

    @Override
    protected void init() {
        super.init();

        int listTop = 50;
        int listBottom = this.height - 45;
        int listHeight = listBottom - listTop;

        this.searchBox = new EditBox(this.font, this.width / 2 - 150, 26, 300, 18, Component.translatable("worldbrowser.modlist.search"));
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        this.modSelectionList = new ModSelectionList(this.minecraft, this.width, listHeight, listTop, 24);
        this.addRenderableWidget(this.modSelectionList);
        populateList("");

        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK, btn -> this.minecraft.setScreenAndShow(parentScreen))
                        .bounds(this.width / 2 - 100, this.height - 35, 200, 20)
                        .build()
        );
    }

    private void onSearchChanged(String query) {
        populateList(query != null ? query.trim().toLowerCase() : "");
    }

    private void populateList(String filter) {
        modSelectionList.clearEntries();
        for (ModCompatibilityEntry entry : result.getEntries()) {
            if (filter.isEmpty() 
                    || entry.getModName().toLowerCase().contains(filter) 
                    || entry.getModId().toLowerCase().contains(filter)) {
                modSelectionList.addModEntry(new ModEntry(this.minecraft, entry));
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float tickDelta) {
        extractor.fill(0, 0, this.width, this.height, 0xD0000000);
        super.extractRenderState(extractor, mouseX, mouseY, tickDelta);

        String profileName = profile != null ? profile.getDisplayName()
                : Component.translatable("worldbrowser.modlist.unknown_profile").getString();
        Component titleComp = Component.translatable("worldbrowser.modlist.title",
                        Component.literal(profileName).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.YELLOW);
        int titleWidth = this.font.width(titleComp);
        extractor.textRenderer().accept(this.width / 2 - titleWidth / 2, 8, titleComp);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parentScreen);
    }

    public static class ModSelectionList extends ObjectSelectionList<ModEntry> {
        public ModSelectionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        public void addModEntry(ModEntry entry) {
            this.addEntry(entry);
        }

        public void clearEntries() {
            super.clearEntries();
        }

        @Override
        public int getRowWidth() {
            return Math.min(420, this.width - 40);
        }
    }

    public static class ModEntry extends ObjectSelectionList.Entry<ModEntry> {
        private final Minecraft minecraft;
        private final ModCompatibilityEntry entry;

        public ModEntry(Minecraft minecraft, ModCompatibilityEntry entry) {
            this.minecraft = minecraft;
            this.entry = entry;
        }

        @Override
        public Component getNarration() {
            return Component.literal(entry.getModName());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
            int x = getContentX();
            int y = getContentY();
            int width = getContentWidth();
            int height = getContentHeight();

            if (isHovered) {
                extractor.fill(x, y, x + width, y + height, 0x20FFFFFF);
            }

            // Status symbol and name
            ModCompatibilityStatus status = entry.getStatus();
            Component nameComp = Component.literal(status.getSymbol() + " ")
                    .withStyle(status.getColor())
                    .append(Component.literal(entry.getModName()).withStyle(ChatFormatting.WHITE));
            extractor.textRenderer().accept(x + 5, y + 2, nameComp);

            // Offset badge from right edge to leave clearance for scrollbar
            Component badge = status.getFormattedComponent();
            int badgeWidth = minecraft.font.width(badge);
            int badgeX = x + width - badgeWidth - 14;

            // Restrict version details width to avoid overlapping the badge
            Component versionDetails;
            if (status == ModCompatibilityStatus.MISSING_IN_CURRENT) {
                versionDetails = Component.translatable("worldbrowser.modlist.entry.missing",
                                entry.getModId(), entry.getWorldVersion())
                        .withStyle(ChatFormatting.RED);
            } else if (status == ModCompatibilityStatus.VERSION_MISMATCH) {
                versionDetails = Component.translatable("worldbrowser.modlist.entry.mismatch",
                                entry.getModId(), entry.getWorldVersion(), entry.getCurrentVersion())
                        .withStyle(ChatFormatting.GOLD);
            } else if (status == ModCompatibilityStatus.EXTRA_IN_CURRENT) {
                versionDetails = Component.translatable("worldbrowser.modlist.entry.extra",
                                entry.getModId(), entry.getCurrentVersion())
                        .withStyle(ChatFormatting.GRAY);
            } else {
                versionDetails = Component.translatable("worldbrowser.modlist.entry.compatible",
                                entry.getModId(), entry.getCurrentVersion())
                        .withStyle(ChatFormatting.DARK_GRAY);
            }
            extractor.enableScissor(x + 18, y + 11, badgeX - 4, y + height);
            extractor.textRenderer().accept(x + 18, y + 13, versionDetails);
            extractor.disableScissor();

            // Show full details in tooltip when text is truncated
            int detailMaxWidth = badgeX - 4 - (x + 18);
            int nameMaxWidth = badgeX - 4 - (x + 5);
            if (isHovered && (minecraft.font.width(versionDetails) > detailMaxWidth
                    || minecraft.font.width(nameComp) > nameMaxWidth)) {
                extractor.setTooltipForNextFrame(minecraft.font,
                        java.util.List.of(nameComp, versionDetails), java.util.Optional.empty(), mouseX, mouseY);
            }

            extractor.textRenderer().accept(badgeX, y + 6, badge);
        }
    }
}
