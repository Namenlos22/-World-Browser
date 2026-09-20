package com.example.worldbrowser.mixin;

import com.example.worldbrowser.gui.BackFolderEntry;
import com.example.worldbrowser.gui.FolderListEntry;
import com.example.worldbrowser.gui.WorldBrowserScreenBridge;
import com.example.worldbrowser.model.LauncherType;
import com.example.worldbrowser.model.ProfileInfo;
import com.example.worldbrowser.registry.NavigationState;
import com.example.worldbrowser.registry.WorldBrowserRegistry;
import com.example.worldbrowser.registry.WrappedLevelSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(WorldSelectionList.class)
public abstract class WorldSelectionListMixin extends ObjectSelectionList<WorldSelectionList.Entry> {

    @Shadow public abstract void reloadWorldList();
    @Shadow public abstract Screen getScreen();
    @Shadow private String filter;
    @Shadow private List<LevelSummary> currentlyDisplayedLevels;
    @Shadow @Final private WorldSelectionList.EntryType entryType;

    public WorldSelectionListMixin(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    // Vanilla redirects empty saves directly to Create New World screen; show browser if external worlds exist
    @Inject(method = "handleNewLevels", at = @At("HEAD"), cancellable = true)
    private void worldbrowser_onHandleNewLevels(List<LevelSummary> summaries, CallbackInfo ci) {
        if (summaries == null || !summaries.isEmpty()) return;
        if (this.entryType != WorldSelectionList.EntryType.SINGLEPLAYER) return;
        if (!WorldBrowserRegistry.getInstance().hasAnyWorlds()) return;

        ci.cancel();
        worldbrowser_fillBrowser(this.filter);
        this.currentlyDisplayedLevels = summaries;
    }

    // Empty saves reload returns the same empty list instance; repopulate explicitly to update UI
    @Inject(method = "reloadWorldList", at = @At("TAIL"))
    private void worldbrowser_onReloadWorldList(CallbackInfo ci) {
        if (this.entryType != WorldSelectionList.EntryType.SINGLEPLAYER) return;
        worldbrowser_fillBrowser(this.filter);
    }

    @Inject(method = "fillLevels", at = @At("HEAD"), cancellable = true)
    private void worldbrowser_onFillLevels(String filter, List<LevelSummary> summaries, CallbackInfo ci) {
        // Prevent folder entries from leaking into Realms upload screen
        if (this.entryType != WorldSelectionList.EntryType.SINGLEPLAYER) return;
        ci.cancel();
        worldbrowser_fillBrowser(filter);
    }

    @Unique
    private void worldbrowser_fillBrowser(String filter) {
        this.clearEntries();
        this.setScrollAmount(0.0);

        WorldBrowserRegistry registry = WorldBrowserRegistry.getInstance();
        NavigationState state = registry.getNavigationState();
        WorldSelectionList self = (WorldSelectionList) (Object) this;


        String cleanFilter = filter != null ? filter.trim().toLowerCase(java.util.Locale.ROOT) : "";

        // Filtered search across active level or entire launcher/registry
        if (!cleanFilter.isEmpty()) {
            if (state.isProfile()) {
                ProfileInfo profile = state.getSelectedProfile();
                List<WrappedLevelSummary> worlds = (profile != null && profile.isCurrent())
                        ? registry.getWorldsForCurrentProfileWithRecents()
                        : registry.getWorldsForProfile(profile);
                for (WrappedLevelSummary world : worlds) {
                    if (world.getLevelName().toLowerCase(java.util.Locale.ROOT).contains(cleanFilter) || world.getLevelId().toLowerCase(java.util.Locale.ROOT).contains(cleanFilter)) {
                        this.addEntry(self.new WorldListEntry(self, world));
                    }
                }
            } else {
                for (ProfileInfo profile : registry.getProfiles()) {
                    if (state.isLauncher() && profile.getLauncherType() != state.getSelectedLauncher()) {
                        continue;
                    }
                    for (WrappedLevelSummary world : registry.getWorldsForProfile(profile)) {
                        if (world.getLevelName().toLowerCase(java.util.Locale.ROOT).contains(cleanFilter) || world.getLevelId().toLowerCase(java.util.Locale.ROOT).contains(cleanFilter)) {
                            this.addEntry(self.new WorldListEntry(self, world));
                        }
                    }
                }
            }
            return;
        }

        // Root level: launcher selection
        if (state.isRoot()) {
            for (LauncherType launcher : LauncherType.values()) {
                int profileCount = registry.getProfilesForLauncher(launcher).size();
                int worldCount = registry.getWorldCountForLauncher(launcher);
                if (profileCount == 0 && worldCount == 0) continue;

                Component title = launcher.getComponent().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                Component subtitle = Component.translatable("worldbrowser.folder.stats_launcher", profileCount, worldCount)
                        .withStyle(ChatFormatting.GRAY);

                this.addEntry(new FolderListEntry(this.minecraft, title, subtitle, () -> {
                    state.goToLauncher(launcher);
                    registry.saveConfig();
                    this.setScrollAmount(0.0);
                    if (this.getScreen() instanceof WorldBrowserScreenBridge bridge) {
                        bridge.worldbrowser_updateSearchAndButtons();
                    }
                    this.reloadWorldList();
                }));
            }
        } 
        // Launcher level: profile selection
        else if (state.isLauncher()) {
            LauncherType launcher = state.getSelectedLauncher();
            this.addEntry(new BackFolderEntry(this.minecraft, Component.translatable("worldbrowser.path.all_launchers"), () -> {
                state.goToRoot();
                registry.saveConfig();
                this.setScrollAmount(0.0);
                if (this.getScreen() instanceof WorldBrowserScreenBridge bridge) {
                    bridge.worldbrowser_updateSearchAndButtons();
                }
                this.reloadWorldList();
            }));

            List<ProfileInfo> profiles = registry.getProfilesForLauncher(launcher);
            for (ProfileInfo profile : profiles) {
                int worldCount = registry.getWorldCountForProfile(profile);
                Component title = Component.literal(profile.getDisplayName())
                        .withStyle(profile.isCurrent() ? ChatFormatting.AQUA : ChatFormatting.YELLOW, ChatFormatting.BOLD);
                if (profile.isCurrent()) {
                    title = title.copy().append(Component.translatable("worldbrowser.folder.current_tag").withStyle(ChatFormatting.GREEN));
                }

                Component subtitle = Component.translatable("worldbrowser.folder.stats_profile", worldCount, profile.getModCount(), profile.getLastVersionId())
                        .withStyle(ChatFormatting.GRAY);

                this.addEntry(new FolderListEntry(this.minecraft, title, subtitle, () -> {
                    state.goToProfile(profile);
                    registry.saveConfig();
                    this.setScrollAmount(0.0);
                    if (this.getScreen() instanceof WorldBrowserScreenBridge bridge) {
                        bridge.worldbrowser_updateSearchAndButtons();
                    }
                    this.reloadWorldList();
                }));
            }
        } 
        // Profile level: world selection
        else if (state.isProfile()) {
            ProfileInfo profile = state.getSelectedProfile();
            Component backTarget = state.getSelectedLauncher() != null ? state.getSelectedLauncher().getComponent() : Component.translatable("worldbrowser.path.all_launchers");
            this.addEntry(new BackFolderEntry(this.minecraft, backTarget, () -> {
                state.goBack();
                registry.saveConfig();
                this.setScrollAmount(0.0);
                if (this.getScreen() instanceof WorldBrowserScreenBridge bridge) {
                    bridge.worldbrowser_updateSearchAndButtons();
                }
                this.reloadWorldList();
            }));

            List<WrappedLevelSummary> worlds = (profile != null && profile.isCurrent())
                    ? registry.getWorldsForCurrentProfileWithRecents()
                    : registry.getWorldsForProfile(profile);
            for (WrappedLevelSummary world : worlds) {
                this.addEntry(self.new WorldListEntry(self, world));
            }
        }

    }
}
