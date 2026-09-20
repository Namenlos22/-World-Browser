package com.example.worldbrowser.scanner;

import com.example.worldbrowser.WorldBrowser;
import com.example.worldbrowser.model.ModInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ModScanner {

    public static List<ModInfo> scanMods(Path modsDir) {
        List<ModInfo> mods = new ArrayList<>();
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            return mods;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jarPath : stream) {
                if (!Files.isRegularFile(jarPath)) continue;
                try {
                    ModInfo mod = parseJar(jarPath);
                    if (mod != null) {
                        mods.add(mod);
                    }
                } catch (Exception e) {
                    // Fallback to filename
                    String fileName = jarPath.getFileName().toString();
                    String simpleName = fileName.endsWith(".jar") ? fileName.substring(0, fileName.length() - 4) : fileName;
                    mods.add(new ModInfo(simpleName.toLowerCase(java.util.Locale.ROOT), simpleName, "unknown", fileName));
                }
            }
        } catch (Exception e) {
            WorldBrowser.LOGGER.warn("Failed to scan mods in {}: {}", modsDir, e.getMessage());
        }

        return mods;
    }

    private static ModInfo parseJar(Path jarPath) {
        String fileName = jarPath.getFileName().toString();
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            // 1. Fabric mod
            JarEntry fabricEntry = jarFile.getJarEntry("fabric.mod.json");
            if (fabricEntry != null) {
                try (InputStream is = jarFile.getInputStream(fabricEntry);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    JsonElement jsonElement = JsonParser.parseReader(reader);
                    if (jsonElement.isJsonObject()) {
                        JsonObject obj = jsonElement.getAsJsonObject();
                        String id = obj.has("id") ? obj.get("id").getAsString() : null;
                        String name = obj.has("name") ? obj.get("name").getAsString() : id;
                        String version = obj.has("version") ? obj.get("version").getAsString() : "unknown";
                        if (id != null) {
                            return new ModInfo(id, name, version, fileName);
                        }
                    }
                }
            }

            // 2. NeoForge / Forge mods.toml
            JarEntry forgeEntry = jarFile.getJarEntry("META-INF/neoforge.mods.toml");
            if (forgeEntry == null) {
                forgeEntry = jarFile.getJarEntry("META-INF/mods.toml");
            }
            if (forgeEntry != null) {
                try (InputStream is = jarFile.getInputStream(forgeEntry);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    String modId = null;
                    String displayName = null;
                    String version = null;
                    String fallbackVersion = null;
                    boolean inModsBlock = false;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("[")) {
                            // Only evaluate first [[mods]] block to avoid mixing attributes across entries
                            if (inModsBlock) break;
                            inModsBlock = line.startsWith("[[mods]]");
                            continue;
                        }
                        if (!inModsBlock) {
                            if (fallbackVersion == null && line.startsWith("version")) {
                                fallbackVersion = extractTomlValue(line);
                            }
                            continue;
                        }
                        if (modId == null && line.startsWith("modId")) {
                            modId = extractTomlValue(line);
                        } else if (displayName == null && line.startsWith("displayName")) {
                            displayName = extractTomlValue(line);
                        } else if (version == null && line.startsWith("version")) {
                            version = extractTomlValue(line);
                        }
                    }
                    if (version == null) version = fallbackVersion;
                    if (modId != null) {
                        return new ModInfo(modId, displayName != null ? displayName : modId, version != null ? version : "unknown", fileName);
                    }
                }
            }

            // 3. Legacy mcmod.info
            JarEntry mcmodEntry = jarFile.getJarEntry("mcmod.info");
            if (mcmodEntry != null) {
                try (InputStream is = jarFile.getInputStream(mcmodEntry);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    JsonElement jsonElement = JsonParser.parseReader(reader);
                    if (jsonElement.isJsonArray() && !jsonElement.getAsJsonArray().isEmpty()) {
                        JsonObject obj = jsonElement.getAsJsonArray().get(0).getAsJsonObject();
                        String modId = obj.has("modid") ? obj.get("modid").getAsString() : null;
                        String name = obj.has("name") ? obj.get("name").getAsString() : modId;
                        String version = obj.has("version") ? obj.get("version").getAsString() : "unknown";
                        if (modId != null) {
                            return new ModInfo(modId, name, version, fileName);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback: derive from filename
        String simpleName = fileName.endsWith(".jar") ? fileName.substring(0, fileName.length() - 4) : fileName;
        return new ModInfo(simpleName.toLowerCase(java.util.Locale.ROOT), simpleName, "unknown", fileName);
    }

    private static String extractTomlValue(String line) {
        int eq = line.indexOf('=');
        if (eq == -1) return null;
        String val = line.substring(eq + 1).trim();
        if (val.isEmpty()) return null;

        if (val.startsWith("\"\"\"") && val.endsWith("\"\"\"") && val.length() >= 6) {
            val = val.substring(3, val.length() - 3).trim();
        } else if (val.startsWith("'''") && val.endsWith("'''") && val.length() >= 6) {
            val = val.substring(3, val.length() - 3).trim();
        } else {
            char first = val.charAt(0);
            if (first == '"' || first == '\'') {
                // Extract quoted value, ignoring trailing inline comments
                int end = val.indexOf(first, 1);
                val = end > 0 ? val.substring(1, end) : val.substring(1);
            } else {
                // Strip trailing comment from unquoted value
                int hash = val.indexOf('#');
                if (hash >= 0) val = val.substring(0, hash).trim();
            }
        }
        // Ignore unresolved template placeholders like ${file.jarVersion}
        if (val.isEmpty() || (val.startsWith("${") && val.endsWith("}"))) return null;
        return val;
    }
}
