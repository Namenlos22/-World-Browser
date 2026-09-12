package com.example.worldbrowser.model;

import java.util.Objects;

public class ModInfo {
    private final String id;
    private final String name;
    private final String version;
    private final String fileName;

    public ModInfo(String id, String name, String version, String fileName) {
        this.id = id != null ? id : "unknown";
        this.name = name != null && !name.isBlank() ? name : this.id;
        this.version = version != null && !version.isBlank() ? version : "unbekannt";
        this.fileName = fileName != null ? fileName : "";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModInfo modInfo = (ModInfo) o;
        return Objects.equals(id, modInfo.id) && Objects.equals(version, modInfo.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }

    @Override
    public String toString() {
        return name + " (" + id + " v" + version + ")";
    }
}
