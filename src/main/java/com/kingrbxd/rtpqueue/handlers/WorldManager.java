package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorldManager {
    private final AdvancedRTPQueue plugin;
    private final Map<String, WorldSettings> worldSettings = new HashMap<>();
    private final String defaultWorldName;

    public WorldManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        this.defaultWorldName = plugin.getConfig().getString("teleport.default-world", "world");

        // Load default world settings
        WorldSettings defaultWorld = new WorldSettings(
                defaultWorldName,
                plugin.getConfig().getString("teleport.default-world-display-name", "Overworld"), // Use display name from config
                null,
                plugin.getConfig().getInt("teleport.min-x", -500),
                plugin.getConfig().getInt("teleport.max-x", 500),
                plugin.getConfig().getInt("teleport.min-z", -500),
                plugin.getConfig().getInt("teleport.max-z", 500),
                plugin.getConfig().getBoolean("teleport.safe-teleport", true),
                plugin.getConfig().getInt("teleport.max-teleport-attempts", 10)
        );
        worldSettings.put(defaultWorldName, defaultWorld);

        // Load additional worlds if enabled
        if (plugin.getConfig().getBoolean("teleport.other-worlds.enabled", false)) {
            ConfigurationSection worldsSection = plugin.getConfig().getConfigurationSection("teleport.other-worlds.worlds");
            if (worldsSection != null) {
                for (String key : worldsSection.getKeys(false)) {
                    ConfigurationSection worldSection = worldsSection.getConfigurationSection(key);
                    if (worldSection != null) {
                        String worldName = worldSection.getString("name");
                        String displayName = worldSection.getString("display-name", worldName);
                        String permission = worldSection.getString("permission", "rtpqueue.world." + key);
                        if ("none".equalsIgnoreCase(permission)) {
                            permission = null;
                        }

                        WorldSettings settings = new WorldSettings(
                                worldName,
                                displayName,
                                permission,
                                worldSection.getInt("min-x", -500),
                                worldSection.getInt("max-x", 500),
                                worldSection.getInt("min-z", -500),
                                worldSection.getInt("max-z", 500),
                                worldSection.getBoolean("safe-teleport", true),
                                worldSection.getInt("max-teleport-attempts", 10)
                        );

                        if (Bukkit.getWorld(worldName) != null) {
                            worldSettings.put(worldName, settings);
                            plugin.getLogger().info("Loaded teleport settings for world: " + displayName + " (" + worldName + ")");
                        } else {
                            plugin.getLogger().warning("World '" + worldName + "' not found, skipping");
                        }
                    }
                }
            }
        }
    }

    /**
     * Get settings for a specific world.
     *
     * @param worldName The name of the world
     * @return WorldSettings for the specified world, or default world if not found
     */
    public WorldSettings getWorldSettings(String worldName) {
        return worldSettings.getOrDefault(worldName, worldSettings.get(defaultWorldName));
    }

    /**
     * Get settings for the default world.
     *
     * @return WorldSettings for the default world
     */
    public WorldSettings getDefaultWorldSettings() {
        return worldSettings.get(defaultWorldName);
    }

    /**
     * Get a list of worlds the player has permission to use.
     *
     * @param player The player to check permissions for
     * @return List of accessible world settings
     */
    public List<WorldSettings> getAccessibleWorlds(Player player) {
        List<WorldSettings> accessible = new ArrayList<>();

        for (WorldSettings settings : worldSettings.values()) {
            if (settings.getPermission() == null || player.hasPermission(settings.getPermission())) {
                accessible.add(settings);
            }
        }

        return accessible;
    }

    /**
     * World settings class to store configuration for each world.
     */
    public static class WorldSettings {
        private final String name;
        private final String displayName;
        private final String permission;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final boolean safeTeleport;
        private final int maxTeleportAttempts;

        public WorldSettings(String name, String displayName, String permission,
                             int minX, int maxX, int minZ, int maxZ,
                             boolean safeTeleport, int maxTeleportAttempts) {
            this.name = name;
            this.displayName = displayName;
            this.permission = permission;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.safeTeleport = safeTeleport;
            this.maxTeleportAttempts = maxTeleportAttempts;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getPermission() {
            return permission;
        }

        public int getMinX() {
            return minX;
        }

        public int getMaxX() {
            return maxX;
        }

        public int getMinZ() {
            return minZ;
        }

        public int getMaxZ() {
            return maxZ;
        }

        public boolean isSafeTeleport() {
            return safeTeleport;
        }

        public int getMaxTeleportAttempts() {
            return maxTeleportAttempts;
        }

        public World getBukkitWorld() {
            return Bukkit.getWorld(name);
        }
    }
}