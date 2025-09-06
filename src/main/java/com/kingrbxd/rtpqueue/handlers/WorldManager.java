package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete world manager with all features implemented
 */
public class WorldManager {
    private final AdvancedRTPQueue plugin;
    private final Map<String, WorldSettings> worldSettings = new ConcurrentHashMap<>();
    private final Map<String, String> displayNames = new ConcurrentHashMap<>();

    public WorldManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        loadWorldSettings();
    }

    /**
     * Load world settings from configuration
     */
    public void loadWorldSettings() {
        worldSettings.clear();
        displayNames.clear();

        // Load default world
        String defaultWorld = plugin.getConfigManager().getString("teleport.default-world", "world");
        String defaultDisplayName = plugin.getConfigManager().getString("teleport.default-world-display-name", "&aOverworld");

        WorldSettings defaultSettings = new WorldSettings(
                defaultWorld,
                defaultDisplayName,
                plugin.getConfigManager().getInt("teleport.min-x", -1000),
                plugin.getConfigManager().getInt("teleport.max-x", 1000),
                plugin.getConfigManager().getInt("teleport.min-z", -1000),
                plugin.getConfigManager().getInt("teleport.max-z", 1000),
                plugin.getConfigManager().getInt("teleport.min-y", 60),
                plugin.getConfigManager().getInt("teleport.max-y", 250),
                plugin.getConfigManager().getBoolean("teleport.safe-teleport", true),
                plugin.getConfigManager().getInt("teleport.max-teleport-attempts", 15),
                null
        );

        worldSettings.put(defaultWorld, defaultSettings);
        displayNames.put(defaultWorld, defaultDisplayName);

        // Load other worlds if enabled
        if (plugin.getConfigManager().getBoolean("teleport.other-worlds.enabled")) {
            loadOtherWorlds();
        }

        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Loaded settings for " + worldSettings.size() + " worlds");
        }
    }

    /**
     * Load other world configurations
     */
    private void loadOtherWorlds() {
        if (!plugin.getConfig().contains("teleport.other-worlds.worlds")) {
            return;
        }

        Set<String> worldKeys = plugin.getConfig().getConfigurationSection("teleport.other-worlds.worlds").getKeys(false);

        for (String worldKey : worldKeys) {
            String basePath = "teleport.other-worlds.worlds." + worldKey + ".";

            String worldName = plugin.getConfig().getString(basePath + "name");
            String displayName = plugin.getConfig().getString(basePath + "display-name", worldName);
            String permission = plugin.getConfig().getString(basePath + "permission");

            if (worldName == null || worldName.isEmpty()) {
                continue;
            }

            WorldSettings settings = new WorldSettings(
                    worldName,
                    displayName,
                    plugin.getConfig().getInt(basePath + "min-x", -500),
                    plugin.getConfig().getInt(basePath + "max-x", 500),
                    plugin.getConfig().getInt(basePath + "min-z", -500),
                    plugin.getConfig().getInt(basePath + "max-z", 500),
                    plugin.getConfig().getInt(basePath + "min-y", 60),
                    plugin.getConfig().getInt(basePath + "max-y", 250),
                    plugin.getConfig().getBoolean(basePath + "safe-teleport", true),
                    plugin.getConfig().getInt(basePath + "max-teleport-attempts", 15),
                    permission
            );

            worldSettings.put(worldName, settings);
            displayNames.put(worldName, displayName);
        }
    }

    /**
     * Get world settings for a world
     */
    public WorldSettings getWorldSettings(String worldName) {
        return worldSettings.get(worldName);
    }

    /**
     * Get display name for a world
     */
    public String getDisplayName(String worldName) {
        return displayNames.getOrDefault(worldName, worldName);
    }

    /**
     * Check if world is valid and configured
     */
    public boolean isValidWorld(String worldName) {
        return worldSettings.containsKey(worldName) && Bukkit.getWorld(worldName) != null;
    }

    /**
     * Get all valid world names
     */
    public Set<String> getValidWorldNames() {
        Set<String> validWorlds = new HashSet<>();
        for (String worldName : worldSettings.keySet()) {
            if (Bukkit.getWorld(worldName) != null) {
                validWorlds.add(worldName);
            }
        }
        return validWorlds;
    }

    /**
     * Get all configured world names
     */
    public Set<String> getAllWorldNames() {
        return new HashSet<>(worldSettings.keySet());
    }

    /**
     * World settings class
     */
    public static class WorldSettings {
        private final String name;
        private final String displayName;
        private final int minX, maxX, minZ, maxZ, minY, maxY;
        private final boolean safeteleport;
        private final int maxTeleportAttempts;
        private final String permission;

        public WorldSettings(String name, String displayName, int minX, int maxX, int minZ, int maxZ,
                             int minY, int maxY, boolean safeteleport, int maxTeleportAttempts, String permission) {
            this.name = name;
            this.displayName = displayName;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.minY = minY;
            this.maxY = maxY;
            this.safeteleport = safeteleport;
            this.maxTeleportAttempts = maxTeleportAttempts;
            this.permission = permission;
        }

        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public int getMinX() { return minX; }
        public int getMaxX() { return maxX; }
        public int getMinZ() { return minZ; }
        public int getMaxZ() { return maxZ; }
        public int getMinY() { return minY; }
        public int getMaxY() { return maxY; }
        public boolean isSafeTelepor() { return safeteleport; }
        public int getMaxTeleportAttempts() { return maxTeleportAttempts; }
        public String getPermission() { return permission; }

        public World getBukkitWorld() {
            return Bukkit.getWorld(name);
        }
    }
}