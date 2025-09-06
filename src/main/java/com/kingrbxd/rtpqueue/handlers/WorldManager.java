package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * FIXED: World manager with proper world name handling
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
                defaultWorld,
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
                                    worldName,
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

                            // FIXED: Use the key name (nether, end) instead of bukkit world name
                            worldSettings.put(key, settings);
                        }
                    }
                }
            }
        }

        plugin.getLogger().info("Loaded " + worldSettings.size() + " world configurations");
    }

    /**
     * FIXED: Strip color codes for tab completion
     */
    private String stripColorCodes(String text) {
        if (text == null) return "";
        return text.replaceAll("&[0-9a-fk-or]", "").replaceAll("&#[0-9a-fA-F]{6}", "");
    }

    /**
     * Check if world is valid and available
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
     * Get display name for world
     */
    public String getDisplayName(String worldName) {
        WorldSettings settings = worldSettings.get(worldName);
        return settings != null ? settings.getDisplayName() : worldName;
    }

    /**
     * FIXED: Get all available world names (keys like 'nether', 'end', not bukkit names)
     */
    public Set<String> getAllWorldNames() {
        Set<String> availableWorlds = new HashSet<>();
        for (Map.Entry<String, WorldSettings> entry : worldSettings.entrySet()) {
            // Only include worlds that actually exist
            if (isValidWorld(entry.getKey())) {
                availableWorlds.add(entry.getKey());
            }
        }
        return availableWorlds;
    }

    /**
     * FIXED: Get world names for tab completion (clean names without color codes)
     */
    public Set<String> getTabCompleteWorldNames() {
        Set<String> tabNames = new HashSet<>();
        for (Map.Entry<String, WorldSettings> entry : worldSettings.entrySet()) {
            if (isValidWorld(entry.getKey())) {
                WorldSettings settings = entry.getValue();
                tabNames.add(settings.getCleanDisplayName()); // Clean name for tab completion
            }
        }
        return tabNames;
    }

    public Set<String> getValidWorldNames() {
        return getAllWorldNames();
    }

    /**
     * World settings data class
     */
    public static class WorldSettings {
        private final String bukkitWorldName;
        private final String cleanDisplayName;
        private final String displayName;
        private final String permission;
        private final int minX, maxX, minZ, maxZ, minY, maxY;
        private final int maxTeleportAttempts;

        public WorldSettings(String bukkitWorldName, String cleanDisplayName, String displayName, String permission,
                             int minX, int maxX, int minZ, int maxZ, int minY, int maxY, int maxTeleportAttempts) {
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