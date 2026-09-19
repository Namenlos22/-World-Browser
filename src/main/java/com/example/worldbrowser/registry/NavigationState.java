package com.example.worldbrowser.registry;

import com.example.worldbrowser.model.LauncherType;
import com.example.worldbrowser.model.ProfileInfo;
import net.minecraft.network.chat.Component;

public class NavigationState {
    public enum Level {
        ROOT,
        LAUNCHER,
        PROFILE
    }

    private static long lastNavigationMillis = 0L;

    public static synchronized boolean recordNavigation() {
        long now = System.currentTimeMillis();
        if (now - lastNavigationMillis < 350L) {
            return false;
        }
        lastNavigationMillis = now;
        return true;
    }

    public static synchronized boolean isNavigationCoolingDown() {
        return (System.currentTimeMillis() - lastNavigationMillis) < 350L;
    }

    private Level level = Level.ROOT;
    private LauncherType selectedLauncher = null;
    private ProfileInfo selectedProfile = null;

    public Level getLevel() {
        return level;
    }

    public LauncherType getSelectedLauncher() {
        return selectedLauncher;
    }

    public ProfileInfo getSelectedProfile() {
        return selectedProfile;
    }

    public boolean isRoot() {
        return level == Level.ROOT;
    }

    public boolean isLauncher() {
        return level == Level.LAUNCHER;
    }

    public boolean isProfile() {
        return level == Level.PROFILE;
    }

    public void goToRoot() {
        this.level = Level.ROOT;
        this.selectedLauncher = null;
        this.selectedProfile = null;
    }

    public void goToLauncher(LauncherType launcher) {
        this.level = Level.LAUNCHER;
        this.selectedLauncher = launcher;
        this.selectedProfile = null;
    }

    public void goToProfile(ProfileInfo profile) {
        if (profile == null) {
            goToRoot();
            return;
        }
        this.level = Level.PROFILE;
        this.selectedLauncher = profile.getLauncherType();
        this.selectedProfile = profile;
    }

    public void goBack() {
        if (level == Level.PROFILE) {
            if (selectedLauncher != null) {
                goToLauncher(selectedLauncher);
            } else {
                goToRoot();
            }
        } else if (level == Level.LAUNCHER) {
            goToRoot();
        }
    }

    public Component getPathHint() {
        if (isRoot()) {
            return Component.translatable("worldbrowser.path.all_launchers");
        }
        if (isLauncher() && selectedLauncher != null) {
            return selectedLauncher.getComponent();
        }
        if (isProfile() && selectedProfile != null) {
            Component launcherComp = selectedLauncher != null ? selectedLauncher.getComponent() : (selectedProfile.getLauncherType() != null ? selectedProfile.getLauncherType().getComponent() : Component.literal("Launcher"));
            return launcherComp.copy().append(" / " + selectedProfile.getDisplayName());
        }
        return Component.literal("World Browser");
    }

    public String getPathString() {
        if (isRoot()) {
            return "All Launchers";
        }
        if (isLauncher() && selectedLauncher != null) {
            return selectedLauncher.getDisplayName();
        }
        if (isProfile() && selectedProfile != null) {
            String launcherName = selectedLauncher != null ? selectedLauncher.getDisplayName() : (selectedProfile.getLauncherType() != null ? selectedProfile.getLauncherType().getDisplayName() : "Launcher");
            return launcherName + " / " + selectedProfile.getDisplayName();
        }
        return "World Browser";
    }

    public Component getBreadcrumbComponent() {
        return getPathHint();
    }
}
