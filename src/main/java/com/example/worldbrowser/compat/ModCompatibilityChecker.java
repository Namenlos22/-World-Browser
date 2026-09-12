package com.example.worldbrowser.compat;

import com.example.worldbrowser.WorldBrowser;
import com.example.worldbrowser.model.ModInfo;
import com.example.worldbrowser.model.ProfileInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModCompatibilityChecker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MODS_RECORD_FILE = "worldbrowser_mods.json";

    public static Map<String, ModInfo> getCurrentMods() {
        Map<String, ModInfo> currentMods = new HashMap<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = container.getMetadata();
            String id = meta.getId();
            String name = meta.getName();
            String version = meta.getVersion().getFriendlyString();
            currentMods.put(id.toLowerCase(), new ModInfo(id, name, version, ""));
        }
        return currentMods;
    }

    public static ModCompatibilityResult checkCompatibility(Path worldDir, ProfileInfo profile) {
        List<ModInfo> worldMods = loadOrSaveWorldMods(worldDir, profile);
        Map<String, ModInfo> currentMods = getCurrentMods();

        List<ModCompatibilityEntry> entries = new ArrayList<>();
        Set<String> processedCurrentIds = new HashSet<>();

        // Compare world mods against current mods
        for (ModInfo wm : worldMods) {
            String lowerId = wm.getId().toLowerCase();
            processedCurrentIds.add(lowerId);

            ModInfo currentMod = currentMods.get(lowerId);
            if (currentMod == null) {
                entries.add(new ModCompatibilityEntry(
                        wm.getId(),
                        wm.getName(),
                        wm.getVersion(),
                        "-",
                        ModCompatibilityStatus.MISSING_IN_CURRENT
                ));
            } else {
                boolean versionMatches = isVersionCompatible(wm.getVersion(), currentMod.getVersion());
                ModCompatibilityStatus status = versionMatches
                        ? ModCompatibilityStatus.COMPATIBLE
                        : ModCompatibilityStatus.VERSION_MISMATCH;
                entries.add(new ModCompatibilityEntry(
                        wm.getId(),
                        wm.getName(),
                        wm.getVersion(),
                        currentMod.getVersion(),
                        status
                ));
            }
        }

        // Add extra current mods
        for (Map.Entry<String, ModInfo> e : currentMods.entrySet()) {
            if (!processedCurrentIds.contains(e.getKey())) {
                ModInfo cm = e.getValue();
                // Filter out standard internal IDs for cleaner view
                if (isSystemMod(cm.getId())) continue;

                entries.add(new ModCompatibilityEntry(
                        cm.getId(),
                        cm.getName(),
                        "-",
                        cm.getVersion(),
                        ModCompatibilityStatus.EXTRA_IN_CURRENT
                ));
            }
        }

        // Sort entries: MISSING first, then VERSION_MISMATCH, then COMPATIBLE, then EXTRA
        entries.sort((a, b) -> Integer.compare(getStatusPriority(a.getStatus()), getStatusPriority(b.getStatus())));

        return new ModCompatibilityResult(entries);
    }

    private static int getStatusPriority(ModCompatibilityStatus s) {
        return switch (s) {
            case MISSING_IN_CURRENT -> 0;
            case VERSION_MISMATCH -> 1;
            case COMPATIBLE -> 2;
            case EXTRA_IN_CURRENT -> 3;
        };
    }

    private static boolean isVersionCompatible(String v1, String v2) {
        if (v1 == null || v2 == null) return true;
        if (v1.equalsIgnoreCase("unbekannt") || v2.equalsIgnoreCase("unbekannt")) return true;
        return v1.trim().equalsIgnoreCase(v2.trim());
    }

    private static boolean isSystemMod(String id) {
        // Die Mod-ID des Fabric Loaders ist "fabricloader" (ohne Bindestrich)
        return id.equals("minecraft") || id.equals("java") || id.startsWith("fabricloader");
    }

    public static List<ModInfo> loadOrSaveWorldMods(Path worldDir, ProfileInfo profile) {
        if (worldDir != null && Files.isDirectory(worldDir)) {
            Path recordFile = worldDir.resolve(MODS_RECORD_FILE);
            if (Files.exists(recordFile)) {
                try (Reader reader = Files.newBufferedReader(recordFile)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    // "format" fehlt in Dateien vom alten Parser (kaputte TOML-Werte) -> neu scannen
                    if (json.isJsonObject() && json.getAsJsonObject().has("mods") && json.getAsJsonObject().has("format")) {
                        JsonArray arr = json.getAsJsonObject().getAsJsonArray("mods");
                        List<ModInfo> list = new ArrayList<>();
                        for (JsonElement el : arr) {
                            if (!el.isJsonObject()) continue;
                            JsonObject obj = el.getAsJsonObject();
                            String id = obj.has("id") ? obj.get("id").getAsString() : "";
                            String name = obj.has("name") ? obj.get("name").getAsString() : id;
                            String version = obj.has("version") ? obj.get("version").getAsString() : "";
                            String file = obj.has("file") ? obj.get("file").getAsString() : "";
                            if (!id.isBlank()) {
                                list.add(new ModInfo(id, name, version, file));
                            }
                        }
                        if (!list.isEmpty()) {
                            return list;
                        }
                    }
                } catch (Exception e) {
                    WorldBrowser.LOGGER.warn("Konnte {} nicht lesen: {}", recordFile, e.getMessage());
                }
            }
        }

        // Fallback: use profile mods
        List<ModInfo> profileMods = profile != null ? profile.getMods() : List.of();

        // Save to world folder if possible
        if (worldDir != null && Files.isDirectory(worldDir) && !profileMods.isEmpty()) {
            saveWorldMods(worldDir, profileMods, profile != null ? profile.getDisplayName() : "Unbekannt");
        }

        return profileMods;
    }

    public static void saveWorldMods(Path worldDir, List<ModInfo> mods, String profileName) {
        if (worldDir == null || !Files.isDirectory(worldDir)) return;
        Path recordFile = worldDir.resolve(MODS_RECORD_FILE);
        try {
            JsonObject root = new JsonObject();
            root.addProperty("format", 1);
            root.addProperty("profile", profileName);
            root.addProperty("savedAt", System.currentTimeMillis());

            JsonArray arr = new JsonArray();
            for (ModInfo mod : mods) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", mod.getId());
                obj.addProperty("name", mod.getName());
                obj.addProperty("version", mod.getVersion());
                obj.addProperty("file", mod.getFileName());
                arr.add(obj);
            }
            root.add("mods", arr);

            try (Writer writer = Files.newBufferedWriter(recordFile)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            WorldBrowser.LOGGER.warn("Konnte Welt-Mods nicht speichern in {}: {}", recordFile, e.getMessage());
        }
    }
}
