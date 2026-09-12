package com.example.worldbrowser;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldBrowser implements ModInitializer {
	public static final String MOD_ID = "worldbrowser";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("World Browser initialisiert! Starte Profil- und Welten-Erkennung...");
		// Vorab-Scan im Hintergrund starten für sofortige Verfügbarkeit im Menü
		new Thread(() -> {
			com.example.worldbrowser.registry.WorldBrowserRegistry.getInstance().reload();
		}, "WorldBrowser-Scanner").start();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
