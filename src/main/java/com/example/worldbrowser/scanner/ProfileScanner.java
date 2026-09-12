package com.example.worldbrowser.scanner;

import com.example.worldbrowser.WorldBrowser;
import com.example.worldbrowser.model.LauncherType;
import com.example.worldbrowser.model.ProfileInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProfileScanner {

    public static List<ProfileInfo> scanAllProfiles() {
        List<ProfileInfo> rawProfiles = new ArrayList<>();
        Path currentGameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();

        // 1. Scan Vanilla Minecraft
        scanVanilla(rawProfiles, currentGameDir);

        // 2. Scan Modrinth App
        scanModrinth(rawProfiles, currentGameDir);

        // 3. Scan CurseForge
        scanCurseForge(rawProfiles, currentGameDir);

        // 4. Ensure current profile is represented if not matched
        boolean hasCurrent = rawProfiles.stream().anyMatch(ProfileInfo::isCurrent);
        if (!hasCurrent) {
            Path currentSaves = currentGameDir.resolve("saves");
            Path currentMods = currentGameDir.resolve("mods");
            ProfileInfo currentProfile = new ProfileInfo(
                    LauncherType.CURRENT,
                    "current",
                    "Aktuelles Spielverzeichnis",
                    currentGameDir,
                    currentSaves,
                    currentMods,
                    null,
                    "aktuell",
                    true
            );
            rawProfiles.add(0, currentProfile);
        }

        // 5. Deduplicate profiles strictly by canonical game directory
        List<ProfileInfo> deduplicated = new ArrayList<>();
        Set<Path> seenDirs = new HashSet<>();

        // Prioritize current profile first
        for (ProfileInfo p : rawProfiles) {
            if (p.isCurrent()) {
                Path norm = p.getGameDir().toAbsolutePath().normalize();
                if (seenDirs.add(norm)) {
                    deduplicated.add(p);
                }
            }
        }

        for (ProfileInfo p : rawProfiles) {
            if (!p.isCurrent()) {
                Path norm = p.getGameDir().toAbsolutePath().normalize();
                if (seenDirs.add(norm)) {
                    deduplicated.add(p);
                }
            }
        }

        WorldBrowser.LOGGER.info("Gefundene Profile insgesamt (dedupliziert): {}", deduplicated.size());
        return deduplicated;
    }

    private static void scanVanilla(List<ProfileInfo> profiles, Path currentGameDir) {
        List<Path> minecraftDirs = getPossibleMinecraftDirs();
        Set<String> addedProfileKeys = new HashSet<>();

        for (Path mcDir : minecraftDirs) {
            if (!Files.isDirectory(mcDir)) continue;

            Path profilesJson = mcDir.resolve("launcher_profiles.json");
            if (!Files.exists(profilesJson)) {
                profilesJson = mcDir.resolve("launcher_profiles_microsoft_store.json");
            }

            boolean parsedAny = false;
            if (Files.exists(profilesJson)) {
                try (Reader reader = Files.newBufferedReader(profilesJson)) {
                    JsonElement rootElement = JsonParser.parseReader(reader);
                    if (rootElement.isJsonObject()) {
                        JsonObject rootObj = rootElement.getAsJsonObject();
                        if (rootObj.has("profiles") && rootObj.get("profiles").isJsonObject()) {
                            JsonObject profilesObj = rootObj.getAsJsonObject("profiles");
                            for (Map.Entry<String, JsonElement> entry : profilesObj.entrySet()) {
                                String key = entry.getKey();
                                if (!entry.getValue().isJsonObject()) continue;
                                JsonObject pObj = entry.getValue().getAsJsonObject();

                                String name = pObj.has("name") ? pObj.get("name").getAsString() : "";
                                String lastVersionId = pObj.has("lastVersionId") ? pObj.get("lastVersionId").getAsString() : "";
                                if (name.isBlank()) {
                                    name = !lastVersionId.isBlank() ? lastVersionId : key;
                                }

                                Path gameDir = mcDir;
                                if (pObj.has("gameDir")) {
                                    try {
                                        gameDir = Paths.get(pObj.get("gameDir").getAsString()).toAbsolutePath().normalize();
                                    } catch (Exception ignored) {
                                    }
                                }

                                Path savesDir = gameDir.resolve("saves");
                                Path modsDir = gameDir.resolve("mods");
                                boolean isCurrent = gameDir.equals(currentGameDir);

                                String profileKey = "vanilla:" + gameDir.toAbsolutePath().normalize();
                                if (addedProfileKeys.add(profileKey)) {
                                    ProfileInfo profile = new ProfileInfo(
                                            LauncherType.VANILLA,
                                            key,
                                            name,
                                            gameDir,
                                            savesDir,
                                            modsDir,
                                            null,
                                            lastVersionId,
                                            isCurrent
                                    );
                                    profiles.add(profile);
                                    parsedAny = true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    WorldBrowser.LOGGER.warn("Fehler beim Lesen von {}: {}", profilesJson, e.getMessage());
                }
            }

            if (!parsedAny && Files.isDirectory(mcDir.resolve("saves"))) {
                Path savesDir = mcDir.resolve("saves");
                Path modsDir = mcDir.resolve("mods");
                boolean isCurrent = mcDir.equals(currentGameDir);
                String profileKey = "vanilla:" + mcDir.toAbsolutePath().normalize();
                if (addedProfileKeys.add(profileKey)) {
                    ProfileInfo defaultProfile = new ProfileInfo(
                            LauncherType.VANILLA,
                            "default",
                            "Standard .minecraft",
                            mcDir,
                            savesDir,
                            modsDir,
                            null,
                            "Standard",
                            isCurrent
                    );
                    profiles.add(defaultProfile);
                }
            }
        }
    }

    private static void scanModrinth(List<ProfileInfo> profiles, Path currentGameDir) {
        List<Path> modrinthRoots = getPossibleModrinthDirs();
        Set<Path> seenProfileDirs = new HashSet<>();

        for (Path root : modrinthRoots) {
            if (!Files.isDirectory(root)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for (Path profileDir : stream) {
                    if (!Files.isDirectory(profileDir)) continue;

                    Path normDir = profileDir.toAbsolutePath().normalize();
                    if (!seenProfileDirs.add(normDir)) continue;

                    String profileName = profileDir.getFileName().toString();
                    Path savesDir = profileDir.resolve("saves");
                    Path modsDir = profileDir.resolve("mods");
                    Path iconPath = profileDir.resolve("icon.png");
                    boolean isCurrent = normDir.equals(currentGameDir);

                    ProfileInfo profile = new ProfileInfo(
                            LauncherType.MODRINTH,
                            profileName,
                            profileName,
                            profileDir,
                            savesDir,
                            modsDir,
                            Files.exists(iconPath) ? iconPath : null,
                            "Modrinth",
                            isCurrent
                    );
                    profiles.add(profile);
                }
            } catch (Exception e) {
                WorldBrowser.LOGGER.warn("Fehler beim Durchsuchen von Modrinth {}: {}", root, e.getMessage());
            }
        }
    }

    private static void scanCurseForge(List<ProfileInfo> profiles, Path currentGameDir) {
        List<Path> curseRoots = getPossibleCurseForgeDirs();
        Set<Path> seenProfileDirs = new HashSet<>();

        for (Path root : curseRoots) {
            if (!Files.isDirectory(root)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for (Path instanceDir : stream) {
                    if (!Files.isDirectory(instanceDir)) continue;

                    Path normDir = instanceDir.toAbsolutePath().normalize();
                    if (!seenProfileDirs.add(normDir)) continue;

                    String displayName = instanceDir.getFileName().toString();
                    String gameVersion = "CurseForge";

                    // Try to read minecraftinstance.json
                    Path metaFile = instanceDir.resolve("minecraftinstance.json");
                    if (Files.exists(metaFile)) {
                        try (Reader reader = Files.newBufferedReader(metaFile)) {
                            JsonElement json = JsonParser.parseReader(reader);
                            if (json.isJsonObject()) {
                                JsonObject obj = json.getAsJsonObject();
                                if (obj.has("name") && !obj.get("name").getAsString().isBlank()) {
                                    displayName = obj.get("name").getAsString();
                                }
                                if (obj.has("gameVersion")) {
                                    gameVersion = obj.get("gameVersion").getAsString();
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    Path savesDir = instanceDir.resolve("saves");
                    Path modsDir = instanceDir.resolve("mods");
                    boolean isCurrent = normDir.equals(currentGameDir);

                    ProfileInfo profile = new ProfileInfo(
                            LauncherType.CURSEFORGE,
                            instanceDir.getFileName().toString(),
                            displayName,
                            instanceDir,
                            savesDir,
                            modsDir,
                            null,
                            gameVersion,
                            isCurrent
                    );
                    profiles.add(profile);
                }
            } catch (Exception e) {
                WorldBrowser.LOGGER.warn("Fehler beim Durchsuchen von CurseForge {}: {}", root, e.getMessage());
            }
        }
    }

    private static void addUniqueDir(Set<Path> set, Path path) {
        if (path != null) {
            try {
                set.add(path.toAbsolutePath().normalize());
            } catch (Exception ignored) {
            }
        }
    }

    private static List<Path> getPossibleMinecraftDirs() {
        Set<Path> dirs = new LinkedHashSet<>();
        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            addUniqueDir(dirs, Paths.get(appdata, ".minecraft"));
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            addUniqueDir(dirs, Paths.get(userHome, ".minecraft"));
            addUniqueDir(dirs, Paths.get(userHome, "AppData", "Roaming", ".minecraft"));
            addUniqueDir(dirs, Paths.get(userHome, ".var", "app", "com.mojang.Minecraft", ".minecraft"));
            addUniqueDir(dirs, Paths.get(userHome, "Library", "Application Support", "minecraft"));
        }
        return new ArrayList<>(dirs);
    }

    private static List<Path> getPossibleModrinthDirs() {
        Set<Path> dirs = new LinkedHashSet<>();
        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            addUniqueDir(dirs, Paths.get(appdata, "ModrinthApp", "profiles"));
            addUniqueDir(dirs, Paths.get(appdata, "com.modrinth.theseus", "profiles"));
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            addUniqueDir(dirs, Paths.get(userHome, "AppData", "Roaming", "ModrinthApp", "profiles"));
            addUniqueDir(dirs, Paths.get(userHome, "AppData", "Roaming", "com.modrinth.theseus", "profiles"));
            addUniqueDir(dirs, Paths.get(userHome, ".config", "ModrinthApp", "profiles"));
            addUniqueDir(dirs, Paths.get(userHome, "Library", "Application Support", "ModrinthApp", "profiles"));
        }
        return new ArrayList<>(dirs);
    }

    private static List<Path> getPossibleCurseForgeDirs() {
        Set<Path> dirs = new LinkedHashSet<>();
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            addUniqueDir(dirs, Paths.get(userHome, "curseforge", "minecraft", "Instances"));
            addUniqueDir(dirs, Paths.get(userHome, "CurseForge", "minecraft", "Instances"));
            addUniqueDir(dirs, Paths.get(userHome, "Documents", "curseforge", "minecraft", "Instances"));
        }
        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            addUniqueDir(dirs, Paths.get(appdata, "curseforge", "minecraft", "Instances"));
        }
        return new ArrayList<>(dirs);
    }
}
