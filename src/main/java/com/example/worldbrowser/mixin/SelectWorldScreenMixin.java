package com.example.worldbrowser.mixin;

import com.example.worldbrowser.gui.FolderNavButton;
import com.example.worldbrowser.gui.WorldBrowserScreenBridge;
import com.example.worldbrowser.model.ProfileInfo;
import com.example.worldbrowser.registry.NavigationState;
import com.example.worldbrowser.registry.WorldBrowserRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen implements WorldBrowserScreenBridge {

    @Shadow protected EditBox searchBox;
    @Shadow private WorldSelectionList list;

    @Unique private FolderNavButton worldbrowser_rootButton;
    @Unique private FolderNavButton worldbrowser_upButton;
    @Unique private FolderNavButton worldbrowser_currentButton;
    @Unique private FolderNavButton worldbrowser_clearLinksButton;

    protected SelectWorldScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void worldbrowser_onInitHead(CallbackInfo ci) {
        // Nur scannen wenn der Hintergrund-Scan noch nicht durch ist - sonst bloeckt
        // jedes Screen-Init den Render-Thread mit einem kompletten Profil-Rescan.
        if (!WorldBrowserRegistry.getInstance().isInitialized()) {
            WorldBrowserRegistry.getInstance().reload();
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void worldbrowser_onInitTail(CallbackInfo ci) {
        // Litematica-style navigation buttons: [ 📁/ ] [ 📁⮤ ] [ 📁+ ] [ 📁✕ ]
        this.worldbrowser_rootButton = new FolderNavButton(
                0, 0, 20, 20,
                FolderNavButton.Type.ROOT,
                btn -> {
                    WorldBrowserRegistry.getInstance().getNavigationState().goToRoot();
                    WorldBrowserRegistry.getInstance().saveConfig();
                    if (this.list != null) {
                        this.list.setScrollAmount(0.0);
                        this.list.reloadWorldList();
                    }
                    worldbrowser_updateSearchAndButtons();
                },
                Tooltip.create(Component.translatable("worldbrowser.button.root.tooltip"))
        );

        this.worldbrowser_upButton = new FolderNavButton(
                0, 0, 20, 20,
                FolderNavButton.Type.UP,
                btn -> {
                    WorldBrowserRegistry.getInstance().getNavigationState().goBack();
                    WorldBrowserRegistry.getInstance().saveConfig();
                    if (this.list != null) {
                        this.list.setScrollAmount(0.0);
                        this.list.reloadWorldList();
                    }
                    worldbrowser_updateSearchAndButtons();
                },
                Tooltip.create(Component.translatable("worldbrowser.button.up.tooltip"))
        );

        this.worldbrowser_currentButton = new FolderNavButton(
                0, 0, 20, 20,
                FolderNavButton.Type.CURRENT,
                btn -> {
                    ProfileInfo cur = WorldBrowserRegistry.getInstance().getCurrentProfile();
                    if (cur != null) {
                        WorldBrowserRegistry.getInstance().getNavigationState().goToProfile(cur);
                        WorldBrowserRegistry.getInstance().saveConfig();
                        if (this.list != null) {
                            this.list.setScrollAmount(0.0);
                            this.list.reloadWorldList();
                        }
                        worldbrowser_updateSearchAndButtons();
                    }
                },
                Tooltip.create(Component.translatable("worldbrowser.button.current.tooltip"))
        );

        this.worldbrowser_clearLinksButton = new FolderNavButton(
                0, 0, 20, 20,
                FolderNavButton.Type.CLEAR_RECENTS,
                btn -> {
                    WorldBrowserRegistry.getInstance().clearRecentExternalWorlds();
                    if (this.list != null) {
                        this.list.setScrollAmount(0.0);
                        this.list.reloadWorldList();
                    }
                    worldbrowser_updateSearchAndButtons();
                },
                Tooltip.create(Component.translatable("worldbrowser.button.clear_recents.tooltip"))
        );

        this.addRenderableWidget(this.worldbrowser_rootButton);
        this.addRenderableWidget(this.worldbrowser_upButton);
        this.addRenderableWidget(this.worldbrowser_currentButton);
        this.addRenderableWidget(this.worldbrowser_clearLinksButton);

        worldbrowser_layoutHeaderWidgets();
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void worldbrowser_onRepositionElements(CallbackInfo ci) {
        worldbrowser_layoutHeaderWidgets();
    }

    @Inject(method = "setInitialFocus", at = @At("HEAD"), cancellable = true)
    private void worldbrowser_onSetInitialFocus(CallbackInfo ci) {
        if (this.list != null) {
            this.setInitialFocus((GuiEventListener) this.list);
            ci.cancel();
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void worldbrowser_onClose(CallbackInfo ci) {
        WorldBrowserRegistry.getInstance().saveConfig();
    }

    @Override
    public void worldbrowser_updateSearchAndButtons() {
        worldbrowser_layoutHeaderWidgets();
    }

    @Unique
    private void worldbrowser_layoutHeaderWidgets() {
        if (this.searchBox == null) return;

        int rowY = this.searchBox.getY();
        int btnW = 20;
        int btnGap = 2;
        int searchGap = 4;
        int searchBoxW = 200;

        int totalButtonsW = (btnW * 4) + (btnGap * 3); // 86
        int totalRowW = totalButtonsW + searchGap + searchBoxW; // 290
        int startX = (this.width - totalRowW) / 2;

        if (this.worldbrowser_rootButton != null) {
            this.worldbrowser_rootButton.setX(startX);
            this.worldbrowser_rootButton.setY(rowY);
        }
        if (this.worldbrowser_upButton != null) {
            this.worldbrowser_upButton.setX(startX + btnW + btnGap);
            this.worldbrowser_upButton.setY(rowY);
        }
        if (this.worldbrowser_currentButton != null) {
            this.worldbrowser_currentButton.setX(startX + (btnW + btnGap) * 2);
            this.worldbrowser_currentButton.setY(rowY);
        }
        if (this.worldbrowser_clearLinksButton != null) {
            this.worldbrowser_clearLinksButton.setX(startX + (btnW + btnGap) * 3);
            this.worldbrowser_clearLinksButton.setY(rowY);
        }

        this.searchBox.setX(startX + totalButtonsW + searchGap);
        this.searchBox.setY(rowY);
        this.searchBox.setWidth(searchBoxW);

        // Update path hint & button active states
        NavigationState state = WorldBrowserRegistry.getInstance().getNavigationState();
        Component pathHint = state.getPathHint().copy().withStyle(ChatFormatting.GRAY);
        this.searchBox.setHint(pathHint);

        if (this.worldbrowser_rootButton != null) {
            this.worldbrowser_rootButton.active = !state.isRoot();
        }
        if (this.worldbrowser_upButton != null) {
            this.worldbrowser_upButton.active = !state.isRoot();
        }
        ProfileInfo cur = WorldBrowserRegistry.getInstance().getCurrentProfile();
        boolean isCurrentProfile = cur != null && state.isProfile() && cur.equals(state.getSelectedProfile());
        if (this.worldbrowser_currentButton != null) {
            this.worldbrowser_currentButton.active = !isCurrentProfile;
        }
        if (this.worldbrowser_clearLinksButton != null) {
            boolean hasRecents = !WorldBrowserRegistry.getInstance().getRecentExternalWorldPaths().isEmpty();
            this.worldbrowser_clearLinksButton.active = isCurrentProfile && hasRecents;
        }
    }
}
