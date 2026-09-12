package com.example.worldbrowser.registry;

import com.example.worldbrowser.WorldBrowser;
import com.example.worldbrowser.model.LauncherType;
import com.example.worldbrowser.model.ProfileInfo;
import com.example.worldbrowser.scanner.ProfileScanner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class WorldBrowserRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path GLOBAL_CONFIG_PATH = Path.of(System.getProperty("user.home"))
            .resolve(".worldbrowser")
            .resolve("worldbrowser_state.json");
    private static final Path LOCAL_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("worldbrowser.json");

    private static final WorldBrowserRegistry INSTANCE = new WorldBrowserRegistry();

    private final NavigationState navigationState = new NavigationState();
    private final List<ProfileInfo> profiles = new ArrayList<>();
    private final Map<String, RegisteredWorld> registeredWorldsById = new HashMap<>();
    private final Map<String, List<WrappedLevelSummary>> worldsByProfileKey = new HashMap<>();
    private final List<String> recentExternalWorldPaths = new ArrayList<>();
    private boolean hasUserClearedRecents = false;
    private final Set<String> confirmedIncompatibleWorldPaths = new HashSet<>();
    private ProfileInfo currentProfile = null;
    private boolean initialized = false;

    public static WorldBrowserRegistry getInstance() {
        return INSTANCE;
    }

    public NavigationState getNavigationState() {
        return navigationState;
    }

    public ProfileInfo getCurrentProfile() {
        return currentProfile;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public synchronized void reload() {
        profiles.clear();
        registeredWorldsById.clear();
        worldsByProfileKey.clear();

        // 1. Scan profiles (fast, deduplicated)
        List<ProfileInfo> scanned = ProfileScanner.scanAllProfiles();
        profiles.addAll(scanned);

        // Find current profile
        currentProfile = profiles.stream().filter(ProfileInfo::isCurrent).findFirst().orElse(null);

        WorldBrowser.LOGGER.info("WorldBrowserRegistry: {} Profile geladen.", profiles.size());

        // 2. Initialize navigation state based on startup rules
        if (!initialized) {
            initNavigationState();
            initialized = true;
        }
    }

    private void initNavigationState() {
        Path configToRead = Files.exists(GLOBAL_CONFIG_PATH) ? GLOBAL_CONFIG_PATH : (Files.exists(LOCAL_CONFIG_PATH) ? LOCAL_CONFIG_PATH : null);

        if (configToRead != null) {
            try (Reader reader = Files.newBufferedReader(configToRead)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                recentExternalWorldPaths.clear();
                if (json.has("recentExternalWorlds")) {
                    JsonArray arr = json.getAsJsonArray("recentExternalWorlds");
                    for (JsonElement el : arr) {
                        if (el.isJsonPrimitive()) {
                            recentExternalWorldPaths.add(el.getAsString());
                        }
                    }
                }

                if (json.has("hasUserClearedRecents")) {
                    hasUserClearedRecents = json.get("hasUserClearedRecents").getAsBoolean();
                }

                confirmedIncompatibleWorldPaths.clear();
                if (json.has("confirmedIncompatibleWorlds")) {
                    JsonArray arr = json.getAsJsonArray("confirmedIncompatibleWorlds");
                    for (JsonElement el : arr) {
                        if (el.isJsonPrimitive()) {
                            confirmedIncompatibleWorldPaths.add(el.getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                WorldBrowser.LOGGER.warn("Konnte Konfiguration {} nicht lesen: {}", configToRead, e.getMessage());
            }
        }

        // Standard: immer im aktuellen Profil starten
        if (currentProfile != null) {
            navigationState.goToProfile(currentProfile);
        } else {
            navigationState.goToRoot();
        }
        saveConfig();
    }

    public void saveConfig() {
        try {
            JsonObject json = new JsonObject();
            Path currentGameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
            json.addProperty("lastGameDir", currentGameDir.toString());
            json.addProperty("lastLevel", navigationState.getLevel().name());

            if (navigationState.getSelectedLauncher() != null) {
                json.addProperty("lastLauncher", navigationState.getSelectedLauncher().getId());
            }
            if (navigationState.getSelectedProfile() != null) {
                json.addProperty("lastProfileId", navigationState.getSelectedProfile().getProfileId());
            }

            JsonArray recentArray = new JsonArray();
            for (String path : recentExternalWorldPaths) {
                recentArray.add(path);
            }
            json.add("recentExternalWorlds", recentArray);
            json.addProperty("hasUserClearedRecents", hasUserClearedRecents);

            JsonArray confirmedArray = new JsonArray();
            for (String path : confirmedIncompatibleWorldPaths) {
                confirmedArray.add(path);
            }
            json.add("confirmedIncompatibleWorlds", confirmedArray);

            // Save to shared global config
            try {
                if (GLOBAL_CONFIG_PATH.getParent() != null) {
                    Files.createDirectories(GLOBAL_CONFIG_PATH.getParent());
                }
                try (Writer writer = Files.newBufferedWriter(GLOBAL_CONFIG_PATH)) {
                    GSON.toJson(json, writer);
                }
            } catch (Exception ignored) {
            }

            // Also save to instance local config
            try {
                if (LOCAL_CONFIG_PATH.getParent() != null) {
                    Files.createDirectories(LOCAL_CONFIG_PATH.getParent());
                }
                try (Writer writer = Files.newBufferedWriter(LOCAL_CONFIG_PATH)) {
                    GSON.toJson(json, writer);
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            WorldBrowser.LOGGER.warn("Konnte Konfiguration nicht speichern: {}", e.getMessage());
        }
    }

    public Path getWorldPath(String levelId) {
        if (levelId == null) return null;
        RegisteredWorld rw = registeredWorldsById.get(levelId);
        if (rw != null) {
            return rw.worldDir();
        }
        return null;
    }

    public RegisteredWorld getRegisteredWorld(String levelId) {
        if (levelId == null) return null;
        return registeredWorldsById.get(levelId);
    }

    public List<ProfileInfo> getProfiles() {
        return Collections.unmodifiableList(profiles);
    }

    public List<ProfileInfo> getProfilesForLauncher(LauncherType launcher) {
        List<ProfileInfo> result = new ArrayList<>();
        for (ProfileInfo p : profiles) {
            if (p.getLauncherType() == launcher) {
                result.add(p);
            }
        }
        return result;
    }

    public synchronized List<WrappedLevelSummary> getWorldsForProfile(ProfileInfo profile) {
        if (profile == null) return Collections.emptyList();
        String key = profile.getUniqueKey();
        Path savesDir = profile.getSavesDir();

        if (worldsByProfileKey.containsKey(key)) {
            // Cache mit Disk abgleichen - geloeschte Welten raus, neue rein (kompletter Rescan bei Abweichung)
            Set<String> onDisk = listWorldDirs(savesDir);
            List<WrappedLevelSummary> cached = worldsByProfileKey.get(key);
            boolean stale = cached.size() != onDisk.size()
                    || cached.stream().anyMatch(w -> !onDisk.contains(w.getOriginal().getLevelId()));
            if (!stale) {
                return cached;
            }
            unregisterMissingWorlds();
            worldsByProfileKey.remove(key);
        }

        List<WrappedLevelSummary> profileWorlds = new ArrayList<>();
        Set<String> seenLevelIds = new HashSet<>();

        if (savesDir != null && Files.isDirectory(savesDir)) {
            try {
                LevelStorageSource storageSource = LevelStorageSource.createDefault(savesDir);
                LevelStorageSource.LevelCandidates candidates = storageSource.findLevelCandidates();
                CompletableFuture<List<LevelSummary>> future = storageSource.loadLevelSummaries(candidates);
                List<LevelSummary> summaries = future.join();

                for (LevelSummary summary : summaries) {
                    String rawId = summary.getLevelId();
                    if (!seenLevelIds.add(rawId)) {
                        continue;
                    }

                    Path worldDir = savesDir.resolve(rawId);
                    String uniqueId;
                    if (profile.isCurrent()) {
                        // Current profile uses native ID (zero redirection, native compatibility with FastQuit and Vanilla)
                        uniqueId = rawId;
                    } else {
                        // External profile uses Windows-safe alphanumeric identifier (no colons)
                        String safeLauncher = profile.getLauncherType().getId().replaceAll("[^a-zA-Z0-9_.-]", "_");
                        String safeProfile = profile.getProfileId().replaceAll("[^a-zA-Z0-9_.-]", "_");
                        String safeWorld = rawId.replaceAll("[^a-zA-Z0-9_.-]", "_");
                        String baseId = "wb_" + safeLauncher + "_" + safeProfile + "_" + safeWorld;
                        uniqueId = baseId;
                        // Sanitized IDs can collide ("My World" vs "my_world") - never overwrite a different world's mapping
                        for (int suffix = 1; registeredWorldsById.containsKey(uniqueId)
                                && !registeredWorldsById.get(uniqueId).worldDir().equals(worldDir); suffix++) {
                            uniqueId = baseId + "_" + suffix;
                        }
                    }

                    WrappedLevelSummary wrapped = new WrappedLevelSummary(summary, uniqueId, profile, worldDir);

                    RegisteredWorld reg = new RegisteredWorld(uniqueId, wrapped, profile, worldDir);
                    registeredWorldsById.put(uniqueId, reg);
                    if (profile.isCurrent()) {
                        registeredWorldsById.putIfAbsent(rawId, reg);
                    }
                    registeredWorldsById.putIfAbsent(worldDir.toAbsolutePath().normalize().toString(), reg);

                    profileWorlds.add(wrapped);
                }
            } catch (Exception e) {
                WorldBrowser.LOGGER.warn("Konnte Welten für Profil {} nicht laden: {}", profile.getDisplayName(), e.getMessage());
            }
        }

        worldsByProfileKey.put(key, profileWorlds);
        return profileWorlds;
    }

    private void unregisterMissingWorlds() {
        registeredWorldsById.values().removeIf(rw -> !Files.exists(rw.worldDir().resolve("level.dat")));
        recentExternalWorldPaths.removeIf(p -> !Files.exists(Path.of(p)));
    }

    private static Set<String> listWorldDirs(Path savesDir) {
        Set<String> dirs = new HashSet<>();
        if (savesDir == null || !Files.isDirectory(savesDir)) return dirs;
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(savesDir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p) && Files.exists(p.resolve("level.dat"))) {
                    dirs.add(p.getFileName().toString());
                }
            }
        } catch (Exception ignored) {
        }
        return dirs;
    }

    public int getWorldCountForProfile(ProfileInfo profile) {
        if (profile == null || profile.getSavesDir() == null) return 0;
        String key = profile.getUniqueKey();
        if (worldsByProfileKey.containsKey(key)) {
            return worldsByProfileKey.get(key).size();
        }
        Path savesDir = profile.getSavesDir();
        if (!Files.isDirectory(savesDir)) return 0;

        int count = 0;
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(savesDir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p) && Files.exists(p.resolve("level.dat"))) {
                    count++;
                }
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    public int getWorldCountForLauncher(LauncherType launcher) {
        int count = 0;
        for (ProfileInfo p : getProfilesForLauncher(launcher)) {
            count += getWorldCountForProfile(p);
        }
        return count;
    }

    public synchronized boolean hasAnyWorlds() {
        for (ProfileInfo p : profiles) {
            if (getWorldCountForProfile(p) > 0) return true;
        }
        return false;
    }

    public synchronized void recordRecentWorld(Path worldDir, ProfileInfo profile) {
        if (worldDir == null) return;
        if (profile != null && profile.isCurrent()) {
            return;
        }
        hasUserClearedRecents = false;
        Path normalized = worldDir.toAbsolutePath().normalize();
        String pathStr = normalized.toString();
        recentExternalWorldPaths.remove(pathStr);
        recentExternalWorldPaths.add(0, pathStr);
        while (recentExternalWorldPaths.size() > 20) {
            recentExternalWorldPaths.remove(recentExternalWorldPaths.size() - 1);
        }
        saveConfig();
    }

    public synchronized void clearRecentExternalWorlds() {
        recentExternalWorldPaths.clear();
        hasUserClearedRecents = true;
        saveConfig();
    }

    public synchronized boolean isWorldCompatibilityConfirmed(Path worldDir) {
        if (worldDir == null) return false;
        return confirmedIncompatibleWorldPaths.contains(worldDir.toAbsolutePath().normalize().toString());
    }

    public synchronized void confirmIncompatibleWorld(Path worldDir) {
        if (worldDir == null) return;
        confirmedIncompatibleWorldPaths.add(worldDir.toAbsolutePath().normalize().toString());
        saveConfig();
    }

    public synchronized List<String> getRecentExternalWorldPaths() {
        return Collections.unmodifiableList(recentExternalWorldPaths);
    }

    public synchronized WrappedLevelSummary getOrLoadWorldByPath(Path worldDir) {
        if (worldDir == null || !Files.exists(worldDir)) return null;
        Path normalized = worldDir.toAbsolutePath().normalize();
        String normStr = normalized.toString();

        RegisteredWorld rw = registeredWorldsById.get(normStr);
        if (rw != null && rw.summary() != null) {
            return rw.summary();
        }

        // Find profile that contains this worldDir
        for (ProfileInfo p : profiles) {
            if (p.getSavesDir() != null) {
                Path savesNorm = p.getSavesDir().toAbsolutePath().normalize();
                if (normalized.startsWith(savesNorm)) {
                    List<WrappedLevelSummary> list = getWorldsForProfile(p);
                    for (WrappedLevelSummary w : list) {
                        if (w.getWorldDir().toAbsolutePath().normalize().equals(normalized)) {
                            return w;
                        }
                    }
                }
            }
        }
        return null;
    }

    private synchronized void seedRecentWorldsIfEmpty() {
        if (hasUserClearedRecents || !recentExternalWorldPaths.isEmpty()) return;

        List<WrappedLevelSummary> allExternal = new ArrayList<>();
        for (ProfileInfo p : profiles) {
            if (!p.isCurrent()) {
                allExternal.addAll(getWorldsForProfile(p));
            }
        }
        allExternal.sort((w1, w2) -> Long.compare(w2.getLastPlayed(), w1.getLastPlayed()));
        for (WrappedLevelSummary w : allExternal) {
            if (w.getLastPlayed() > 0) {
                recentExternalWorldPaths.add(w.getWorldDir().toAbsolutePath().normalize().toString());
                if (recentExternalWorldPaths.size() >= 5) break;
            }
        }
        if (!recentExternalWorldPaths.isEmpty()) {
            saveConfig();
        }
    }

    public synchronized List<WrappedLevelSummary> getWorldsForCurrentProfileWithRecents() {
        if (currentProfile == null) return Collections.emptyList();

        List<WrappedLevelSummary> result = new ArrayList<>(getWorldsForProfile(currentProfile));
        Set<String> existingPaths = new HashSet<>();
        for (WrappedLevelSummary w : result) {
            existingPaths.add(w.getWorldDir().toAbsolutePath().normalize().toString());
        }

        // Seed if empty
        if (recentExternalWorldPaths.isEmpty()) {
            seedRecentWorldsIfEmpty();
        }

        List<String> toRemove = new ArrayList<>();
        for (String pathStr : recentExternalWorldPaths) {
            Path p = Path.of(pathStr);
            if (!Files.exists(p)) {
                toRemove.add(pathStr);
                continue;
            }
            if (existingPaths.contains(p.toAbsolutePath().normalize().toString())) {
                continue;
            }

            WrappedLevelSummary summary = getOrLoadWorldByPath(p);
            if (summary != null) {
                result.add(summary);
                existingPaths.add(p.toAbsolutePath().normalize().toString());
            }
        }

        if (!toRemove.isEmpty()) {
            recentExternalWorldPaths.removeAll(toRemove);
            saveConfig();
        }

        // Sort by last played descending (vanilla order)
        result.sort((w1, w2) -> {
            int cmp = Long.compare(w2.getLastPlayed(), w1.getLastPlayed());
            if (cmp != 0) return cmp;
            return w1.getLevelName().compareToIgnoreCase(w2.getLevelName());
        });

        return result;
    }
}
