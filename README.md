# World Browser

Browse worlds from your Vanilla, Modrinth App, and CurseForge profiles directly inside Minecraft's singleplayer menu.

World Browser turns the standard world list into a folder-style browser. It discovers common launcher installations, groups worlds by launcher and profile, compares the mods used by each world with the currently running instance, and warns before opening a world that may be unsafe.

> [!WARNING]
> Opening a world with a different Minecraft version or mod set can still damage it. Compatibility checks and backups reduce risk, but cannot guarantee that a world is safe to load.
>
> ## Downloads

- [Modrinth](https://modrinth.com/mod/world-browser)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/world-browser)

## Features

- Browse worlds from Vanilla Minecraft, Modrinth App, and CurseForge profiles.
- Navigate launcher and profile folders without leaving the singleplayer screen.
- Search for worlds across the currently selected launcher or profile.
- See which external profile a world belongs to.
- Compare recorded world mods with the mods in the active instance.
- Inspect missing mods, version mismatches, compatible mods, and extra installed mods.
- Detect an active `session.lock` and block simultaneous access to a world.
- Create a timestamped ZIP backup before loading a potentially incompatible world.
- Keep recently opened external worlds available from the current profile.
- English and German translations included.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.5 or newer
- Fabric API 0.160.0+26.2 or a compatible release
- Java 25 or newer
- Client-side installation

## Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Install Fabric API.
3. Place the World Browser JAR in the instance's `mods` folder.
4. Start Minecraft and open **Singleplayer**.


## Usage

Open the normal singleplayer menu. The controls beside the search field let you:

- open the launcher root,
- move one folder level up,
- return to the current profile, and
- clear recent external-world shortcuts.



If World Browser detects missing or mismatched mods, it asks whether you want to:

- create a backup and continue,
- continue without a backup, or
- cancel.

A world with an active `session.lock` cannot be opened through World Browser. Close the other Minecraft instance first.

## Supported launcher locations

World Browser scans common profile locations used by:

- the official Minecraft launcher,
- Modrinth App, and
- CurseForge.

Custom launcher directories outside the detected locations are not currently configurable.

## Files created by World Browser

World Browser stores navigation and recent-world state locally:

- `~/.worldbrowser/worldbrowser_state.json`
- `<instance>/config/worldbrowser.json`

When mod information is recorded for a world, it uses:

- `<world>/worldbrowser_mods.json`

Backups are written as timestamped ZIP files to the relevant profile's `backups` directory.

## Compatibility notes

- Mod compatibility is based on mod IDs and exact version strings.
- A matching mod list does not guarantee compatibility between Minecraft versions, loaders, configs, datapacks, or changed mod behavior.
- Opening a world from another profile runs it with the currently active Minecraft instance and its installed mods.
- Always keep independent backups of important worlds.

## Building from source

Clone the repository and run:

```powershell
.\gradlew.bat build
```

The remapped mod JAR will be created in `build/libs`.

## Reporting issues

When reporting a problem, include:

- Minecraft, Fabric Loader, Fabric API, and World Browser versions,
- the launcher and operating system,
- relevant log output, and
- steps that reproduce the issue.


