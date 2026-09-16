package com.example.worldbrowser;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldBrowser implements ModInitializer {
	public static final String MOD_ID = "worldbrowser";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("WorldBrowser initialized.");

		// Discover profiles and pre-warm folder counts in background
		Thread scannerThread = new Thread(() -> {
			com.example.worldbrowser.registry.WorldBrowserRegistry registry =
					com.example.worldbrowser.registry.WorldBrowserRegistry.getInstance();
			registry.reload();
			registry.prewarmProfileCounts();
		}, "WorldBrowser-Scanner");
		scannerThread.setDaemon(true);
		scannerThread.start();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
