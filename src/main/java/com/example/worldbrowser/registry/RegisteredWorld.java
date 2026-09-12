package com.example.worldbrowser.registry;

import com.example.worldbrowser.model.ProfileInfo;

import java.nio.file.Path;

public record RegisteredWorld(String uniqueId, WrappedLevelSummary summary, ProfileInfo profile, Path worldDir) {
}
