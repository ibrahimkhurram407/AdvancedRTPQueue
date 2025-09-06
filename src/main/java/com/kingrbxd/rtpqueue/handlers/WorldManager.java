package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * FIXED: World manager with proper world name handling
 *
 * Added helper methods to support tab-completion using world display names and to resolve
 * a display name back to the configured world key.
 */
public class WorldManager {
    private final AdvancedRTPQueue plugin;
    private final Map<String, WorldSettings> worldSettings = new HashMap<>();

    public WorldManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        loadWorldSettings();
    }

    public void loadWorldSettings() {
        worldSettings.clear();

        // Load default world
        String defaultWorld = plugin.getConfigManager().getString("teleport.default-world", "world");
        String defaultDisplayName = plugin.getConfigManager().getString("teleport.default-world-display-name", "&aOverworld");

        WorldSettings defaultSettings = new WorldSettings(
                defaultWorld, // config key (for default we use the bukkit world name as the key)
                defaultWorld, // bukkit world name
                stripColorCodes(defaultDisplayName), // FIXED: Strip color codes for tab completion
                defaultDisplayName,
                null, // No special permission for default world
                plugin.getConfigManager().getInt("teleport.min-x", -1000),
                plugin.getConfigManager().getInt("teleport.max-x", 1000),
                plugin.getConfigManager().getInt("teleport.min-z", -1000),
                plugin.getConfigManager().getInt("teleport.max-z", 1000),
                plugin.getConfigManager().getInt("teleport.min-y", 60),
                plugin.getConfigManager().getInt("teleport.max-y", 250),
                plugin.getConfigManager().getInt("teleport.max-attempts", 100)
        );

        worldSettings.put(defaultWorld, defaultSettings);

        // Load other worlds
        if (plugin.getConfigManager().getBoolean("teleport.other-worlds.enabled", false)) {
            ConfigurationSection othersSection = plugin.getConfig().getConfigurationSection("teleport.other-worlds.worlds");
            if (othersSection != null) {
                for (String key : othersSection.getKeys(false)) {
                    ConfigurationSection worldSection = othersSection.getConfigurationSection(key);
                    if (worldSection != null) {
                        String worldName = worldSection.getString("name");
                        String displayName = worldSection.getString("display-name", "&7" + key);

                        if (worldName != null) {
                            WorldSettings settings = new WorldSettings(
                                    key, // config key (e.g. "nether")
                                    worldName, // bukkit world name
                                    stripColorCodes(displayName), // FIXED: Strip color codes for tab completion
                                    displayName,
                                    worldSection.getString("permission"),
                                    worldSection.getInt("min-x", -500),
                                    worldSection.getInt("max-x", 500),
                                    worldSection.getInt("min-z", -500),
                                    worldSection.getInt("max-z", 500),
                                    worldSection.getInt("min-y", 30),
                                    worldSection.getInt("max-y", 120),
                                    worldSection.getInt("max-attempts", 50)
                            );

                            // Use the config key name (nether, end) as the identifier
                            worldSettings.put(key, settings);
                        }
                    }
                }
            }
        }

        plugin.getLogger().info("Loaded " + worldSettings.size() + " world configurations");
    }

    /**
     * Strip color codes for tab completion
     */
    private String stripColorCodes(String text) {
        if (text == null) return "";
        return text.replaceAll("&[0-9a-fk-or]", "").replaceAll("&#[0-9a-fA-F]{6}", "");
    }

    /**
     * Normalize a display name for comparison (strip color codes and trim).
     */
    private String normalizeDisplayName(String displayName) {
        if (displayName == null) return "";
        return stripColorCodes(displayName).trim();
    }

    /**
     * Check if world is valid and available (server has the bukkit world configured).
     * worldName here is the config key (e.g. "nether", "end", or the default bukkit name).
     */
    public boolean isValidWorld(String worldName) {
        if (!worldSettings.containsKey(worldName)) return false;

        WorldSettings settings = worldSettings.get(worldName);
        World bukkitWorld = plugin.getServer().getWorld(settings.getBukkitWorldName());
        return bukkitWorld != null;
    }

    /**
     * Get world settings by key name (nether, end, etc.)
     */
    public WorldSettings getWorldSettings(String worldName) {
        return worldSettings.get(worldName);
    }

    /**
     * Get display name for world (with color codes, as configured)
     */
    public String getDisplayName(String worldName) {
        WorldSettings settings = worldSettings.get(worldName);
        return settings != null ? settings.getDisplayName() : worldName;
    }

    /**
     * Get all available world keys (keys like 'nether', 'end', not bukkit names).
     */
    public Set<String> getAllWorldNames() {
        Set<String> availableWorlds = new HashSet<>();
        for (Map.Entry<String, WorldSettings> entry : worldSettings.entrySet()) {
            // Only include worlds that actually exist on the server
            if (isValidWorld(entry.getKey())) {
                availableWorlds.add(entry.getKey());
            }
        }
        return availableWorlds;
    }

    /**
     * Return a set of clean display names suitable for tab-completion.
     * These are the color-stripped display names from config (e.g. "The Nether", "The End").
     *
     * Note: callers that use display names for tab completion must resolve them back to the config key
     * using resolveKeyByDisplayName(...) before performing world lookups.
     */
    public Set<String> getTabCompleteWorldDisplayNames() {
        Set<String> tabNames = new HashSet<>();
        for (Map.Entry<String, WorldSettings> entry : worldSettings.entrySet()) {
            if (isValidWorld(entry.getKey())) {
                WorldSettings settings = entry.getValue();
                String clean = settings.getCleanDisplayName();
                if (clean != null && !clean.isEmpty()) {
                    tabNames.add(clean);
                } else {
                    tabNames.add(entry.getKey());
                }
            }
        }
        return tabNames;
    }

    /**
     * Resolve a user-provided display name (tab-completion value) back to the configured world key.
     * Comparison is case-insensitive and color codes are ignored.
     *
     * Returns:
     *  - the config key (e.g. "nether") if a display name match is found
     *  - the input value if it exactly matches a config key
     *  - null if no match found
     */
    public String resolveKeyByDisplayName(String input) {
        if (input == null) return null;
        String normalized = normalizeDisplayName(input);

        // direct key match
        if (worldSettings.containsKey(input)) return input;

        // try match on clean display name
        for (Map.Entry<String, WorldSettings> e : worldSettings.entrySet()) {
            WorldSettings s = e.getValue();
            if (s == null) continue;
            String clean = normalizeDisplayName(s.getCleanDisplayName());
            if (clean.equalsIgnoreCase(normalized)) {
                return e.getKey();
            }
            // also accept full displayName (with colors stripped)
            String fullClean = normalizeDisplayName(s.getDisplayName());
            if (fullClean.equalsIgnoreCase(normalized)) {
                return e.getKey();
            }
            // also accept the configured bukkit world name
            if (s.getBukkitWorldName().equalsIgnoreCase(input)) {
                return e.getKey();
            }
        }

        return null;
    }

    public Set<String> getValidWorldNames() {
        return getAllWorldNames();
    }

    /**
     * World settings data class
     */
    public static class WorldSettings {
        private final String worldKey; // config key (e.g. "nether", or for default the bukkit world name)
        private final String bukkitWorldName;
        private final String cleanDisplayName;
        private final String displayName;
        private final String permission;
        private final int minX, maxX, minZ, maxZ, minY, maxY;
        private final int maxTeleportAttempts;

        public WorldSettings(String worldKey, String bukkitWorldName, String cleanDisplayName, String displayName, String permission,
                             int minX, int maxX, int minZ, int maxZ, int minY, int maxY, int maxTeleportAttempts) {
            this.worldKey = worldKey;
            this.bukkitWorldName = bukkitWorldName;
            this.cleanDisplayName = cleanDisplayName;
            this.displayName = displayName;
            this.permission = permission;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.minY = minY;
            this.maxY = maxY;
            this.maxTeleportAttempts = maxTeleportAttempts;
        }

        // Getters
        public String getWorldKey() { return worldKey; }
        public String getBukkitWorldName() { return bukkitWorldName; }
        public String getName() { return bukkitWorldName; }
        public String getCleanDisplayName() { return cleanDisplayName; }
        public String getDisplayName() { return displayName; }
        public String getPermission() { return permission; }
        public int getMinX() { return minX; }
        public int getMaxX() { return maxX; }
        public int getMinZ() { return minZ; }
        public int getMaxZ() { return maxZ; }
        public int getMinY() { return minY; }
        public int getMaxY() { return maxY; }
        public int getMaxTeleportAttempts() { return maxTeleportAttempts; }

        public World getBukkitWorld() {
            return org.bukkit.Bukkit.getWorld(bukkitWorldName);
        }
    }
}