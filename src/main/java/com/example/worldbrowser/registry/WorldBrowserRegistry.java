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
import java.nio.charset.StandardCharsets;
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

        // 1. Discover all profiles
        List<ProfileInfo> scanned = ProfileScanner.scanAllProfiles();
        profiles.addAll(scanned);

        currentProfile = profiles.stream().filter(ProfileInfo::isCurrent).findFirst().orElse(null);

        WorldBrowser.LOGGER.info("WorldBrowserRegistry: loaded {} profiles.", profiles.size());

        // 2. Initialize navigation state and load local/global configurations
        if (!initialized) {
            initNavigationState();
            initialized = true;
        }
    }

    public void prewarmProfileCounts() {
        for (ProfileInfo p : profiles) {
            getWorldCountForProfile(p);
            p.getModCount();
        }
    }

    public static String normalizePathKey(Path path) {
        if (path == null) return "";
        String s = path.toAbsolutePath().normalize().toString();
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            s = s.toLowerCase();
        }
        return s;
    }

    public static String normalizePathKey(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) return "";
        try {
            return normalizePathKey(Path.of(pathStr));
        } catch (Exception e) {
            return pathStr.trim().toLowerCase();
        }
    }

    private void initNavigationState() {
        // External world shortcuts are strictly profile-local
        if (Files.exists(LOCAL_CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(LOCAL_CONFIG_PATH, StandardCharsets.UTF_8)) {
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

                if (json.has("confirmedIncompatibleWorlds")) {
                    JsonArray arr = json.getAsJsonArray("confirmedIncompatibleWorlds");
                    for (JsonElement el : arr) {
                        if (el.isJsonPrimitive()) {
                            confirmedIncompatibleWorldPaths.add(el.getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                WorldBrowser.LOGGER.warn("Failed to read local configuration from {}: {}", LOCAL_CONFIG_PATH, e.getMessage());
            }
        }

        // Read global state if local configuration did not provide it
        if (confirmedIncompatibleWorldPaths.isEmpty() && Files.exists(GLOBAL_CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(GLOBAL_CONFIG_PATH, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("confirmedIncompatibleWorlds")) {
                    JsonArray arr = json.getAsJsonArray("confirmedIncompatibleWorlds");
                    for (JsonElement el : arr) {
                        if (el.isJsonPrimitive()) {
                            confirmedIncompatibleWorldPaths.add(el.getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                WorldBrowser.LOGGER.warn("Failed to read global configuration from {}: {}", GLOBAL_CONFIG_PATH, e.getMessage());
            }
        }

        // Start in current profile by default
        if (currentProfile != null) {
            navigationState.goToProfile(currentProfile);
        } else {
            navigationState.goToRoot();
        }
        saveConfig();
    }

    public void saveConfig() {
        try {
            Path currentGameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();

            // 1. Instance-local config (includes profile-local external world shortcuts)
            try {
                JsonObject localJson = new JsonObject();
                localJson.addProperty("lastGameDir", currentGameDir.toString());
                localJson.addProperty("lastLevel", navigationState.getLevel().name());

                if (navigationState.getSelectedLauncher() != null) {
                    localJson.addProperty("lastLauncher", navigationState.getSelectedLauncher().getId());
                }
                if (navigationState.getSelectedProfile() != null) {
                    localJson.addProperty("lastProfileId", navigationState.getSelectedProfile().getProfileId());
                }

                JsonArray recentArray = new JsonArray();
                for (String path : recentExternalWorldPaths) {
                    recentArray.add(path);
                }
                localJson.add("recentExternalWorlds", recentArray);
                localJson.addProperty("hasUserClearedRecents", hasUserClearedRecents);

                JsonArray confirmedArray = new JsonArray();
                for (String path : confirmedIncompatibleWorldPaths) {
                    confirmedArray.add(path);
                }
                localJson.add("confirmedIncompatibleWorlds", confirmedArray);

                if (LOCAL_CONFIG_PATH.getParent() != null) {
                    Files.createDirectories(LOCAL_CONFIG_PATH.getParent());
                }
                try (Writer writer = Files.newBufferedWriter(LOCAL_CONFIG_PATH, StandardCharsets.UTF_8)) {
                    GSON.toJson(localJson, writer);
                }
            } catch (Exception ignored) {
            }

            // 2. Global config (shared history only, NO shortcuts)
            try {
                JsonObject globalJson = new JsonObject();
                globalJson.addProperty("lastGameDir", currentGameDir.toString());
                globalJson.addProperty("lastLevel", navigationState.getLevel().name());

                if (navigationState.getSelectedLauncher() != null) {
                    globalJson.addProperty("lastLauncher", navigationState.getSelectedLauncher().getId());
                }
                if (navigationState.getSelectedProfile() != null) {
                    globalJson.addProperty("lastProfileId", navigationState.getSelectedProfile().getProfileId());
                }

                JsonArray confirmedArray = new JsonArray();
                for (String path : confirmedIncompatibleWorldPaths) {
                    confirmedArray.add(path);
                }
                globalJson.add("confirmedIncompatibleWorlds", confirmedArray);

                if (GLOBAL_CONFIG_PATH.getParent() != null) {
                    Files.createDirectories(GLOBAL_CONFIG_PATH.getParent());
                }
                try (Writer writer = Files.newBufferedWriter(GLOBAL_CONFIG_PATH, StandardCharsets.UTF_8)) {
                    GSON.toJson(globalJson, writer);
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            WorldBrowser.LOGGER.warn("Failed to save configuration: {}", e.getMessage());
        }
    }

    public Path getWorldPath(String levelId) {
        if (levelId == null) return null;
        RegisteredWorld rw = registeredWorldsById.get(levelId);
        if (rw != null) {
            return rw.worldDir();
        }
        String key = normalizePathKey(levelId);
        for (Map.Entry<String, RegisteredWorld> entry : registeredWorldsById.entrySet()) {
            if (normalizePathKey(entry.getKey()).equals(key)) {
                return entry.getValue().worldDir();
            }
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
            // Re-validate cache against disk to detect created, deleted, or modified worlds
            Map<String, Long> onDisk = listWorldDirsWithMtime(savesDir);
            List<WrappedLevelSummary> cached = worldsByProfileKey.get(key);
            boolean stale = cached.size() != onDisk.size()
                    || cached.stream().anyMatch(w -> {
                        Long mtime = onDisk.get(w.getOriginal().getLevelId());
                        return mtime == null || Math.abs(mtime - w.getLastPlayed()) > 2000;
                    });
            if (!stale) {
                profile.setCachedWorldCount(cached.size());
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
                        // Current profile uses native ID for vanilla and FastQuit compatibility
                        uniqueId = rawId;
                    } else {
                        // External profile uses Windows-safe alphanumeric identifier (no colons)
                        String safeLauncher = profile.getLauncherType().getId().replaceAll("[^a-zA-Z0-9_.-]", "_");
                        String safeProfile = profile.getProfileId().replaceAll("[^a-zA-Z0-9_.-]", "_");
                        String safeWorld = rawId.replaceAll("[^a-zA-Z0-9_.-]", "_");
                        String baseId = "wb_" + safeLauncher + "_" + safeProfile + "_" + safeWorld;
                        uniqueId = baseId;
                        // Avoid collisions between sanitized names
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
                    registeredWorldsById.putIfAbsent(normalizePathKey(worldDir), reg);

                    profileWorlds.add(wrapped);
                }
            } catch (Exception e) {
                WorldBrowser.LOGGER.warn("Failed to load worlds for profile {}: {}", profile.getDisplayName(), e.getMessage());
            }
        }

        worldsByProfileKey.put(key, profileWorlds);
        profile.setCachedWorldCount(profileWorlds.size());
        return profileWorlds;
    }

    private void unregisterMissingWorlds() {
        registeredWorldsById.values().removeIf(rw -> !Files.exists(rw.worldDir().resolve("level.dat")));
        recentExternalWorldPaths.removeIf(p -> !Files.exists(Path.of(p)));
    }

    private static Map<String, Long> listWorldDirsWithMtime(Path savesDir) {
        Map<String, Long> dirs = new HashMap<>();
        if (savesDir == null || !Files.isDirectory(savesDir)) return dirs;
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(savesDir)) {
            for (Path p : stream) {
                Path levelDat = p.resolve("level.dat");
                if (Files.isDirectory(p) && Files.exists(levelDat)) {
                    try {
                        dirs.put(p.getFileName().toString(), Files.getLastModifiedTime(levelDat).toMillis());
                    } catch (Exception ignored) {
                        dirs.put(p.getFileName().toString(), 0L);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return dirs;
    }

    public int getWorldCountForProfile(ProfileInfo profile) {
        if (profile == null || profile.getSavesDir() == null) return 0;
        int cached = profile.getCachedWorldCount();
        if (cached >= 0) {
            return cached;
        }

        String key = profile.getUniqueKey();
        if (worldsByProfileKey.containsKey(key)) {
            int count = worldsByProfileKey.get(key).size();
            profile.setCachedWorldCount(count);
            return count;
        }

        Path savesDir = profile.getSavesDir();
        if (!Files.isDirectory(savesDir)) {
            profile.setCachedWorldCount(0);
            return 0;
        }

        int count = 0;
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(savesDir)) {
            for (Path p : stream) {
                if (Files.isDirectory(p) && Files.exists(p.resolve("level.dat"))) {
                    count++;
                }
            }
        } catch (Exception ignored) {
        }
        profile.setCachedWorldCount(count);
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
        String pathKey = normalizePathKey(normalized);
        recentExternalWorldPaths.removeIf(p -> normalizePathKey(p).equals(pathKey));
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
        String key = normalizePathKey(worldDir);
        return confirmedIncompatibleWorldPaths.stream().anyMatch(p -> normalizePathKey(p).equals(key));
    }

    public synchronized void confirmIncompatibleWorld(Path worldDir) {
        if (worldDir == null) return;
        String key = normalizePathKey(worldDir);
        confirmedIncompatibleWorldPaths.removeIf(p -> normalizePathKey(p).equals(key));
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
        String normKey = normalizePathKey(normalized);

        RegisteredWorld rw = registeredWorldsById.get(normStr);
        if (rw != null && rw.summary() != null) {
            return rw.summary();
        }
        rw = registeredWorldsById.get(normKey);
        if (rw != null && rw.summary() != null) {
            return rw.summary();
        }

        // Find profile that contains this worldDir
        for (ProfileInfo p : profiles) {
            if (p.getSavesDir() != null) {
                Path savesNorm = p.getSavesDir().toAbsolutePath().normalize();
                if (normKey.startsWith(normalizePathKey(savesNorm))) {
                    List<WrappedLevelSummary> list = getWorldsForProfile(p);
                    for (WrappedLevelSummary w : list) {
                        if (normalizePathKey(w.getWorldDir()).equals(normKey)) {
                            return w;
                        }
                    }
                }
            }
        }
        return null;
    }

    public synchronized List<WrappedLevelSummary> getWorldsForCurrentProfileWithRecents() {
        if (currentProfile == null) return Collections.emptyList();

        List<WrappedLevelSummary> result = new ArrayList<>(getWorldsForProfile(currentProfile));
        Set<String> existingKeys = new HashSet<>();
        for (WrappedLevelSummary w : result) {
            existingKeys.add(normalizePathKey(w.getWorldDir()));
        }

        List<String> toRemove = new ArrayList<>();
        for (String pathStr : recentExternalWorldPaths) {
            Path p = Path.of(pathStr);
            if (!Files.exists(p)) {
                toRemove.add(pathStr);
                continue;
            }
            if (existingKeys.contains(normalizePathKey(p))) {
                continue;
            }

            WrappedLevelSummary summary = getOrLoadWorldByPath(p);
            if (summary != null) {
                result.add(summary);
                existingKeys.add(normalizePathKey(p));
            }
        }

        if (!toRemove.isEmpty()) {
            recentExternalWorldPaths.removeAll(toRemove);
            saveConfig();
        }

        // Sort descending by last played time
        result.sort((w1, w2) -> {
            int cmp = Long.compare(w2.getLastPlayed(), w1.getLastPlayed());
            if (cmp != 0) return cmp;
            return w1.getLevelName().compareToIgnoreCase(w2.getLevelName());
        });

        return result;
    }
}
