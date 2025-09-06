package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages world settings for RTP.
 */
public class WorldManager {
    private final AdvancedRTPQueue plugin;
    private final Map<String, WorldSettings> worldSettingsMap = new HashMap<>();
    private WorldSettings defaultWorldSettings;

    public WorldManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        loadWorldSettings();
    }

    /**
     * Load world settings from config.
     */
    public void loadWorldSettings() {
        worldSettingsMap.clear();

        // Load default world settings
        String defaultWorldName = plugin.getConfig().getString("teleport.default-world", "world");
        String defaultWorldDisplayName = plugin.getConfig().getString("teleport.default-world-display-name", "Overworld");

        int minX = plugin.getConfig().getInt("teleport.min-x", -500);
        int maxX = plugin.getConfig().getInt("teleport.max-x", 500);
        int minZ = plugin.getConfig().getInt("teleport.min-z", -500);
        int maxZ = plugin.getConfig().getInt("teleport.max-z", 500);

        boolean safeTeleport = plugin.getConfig().getBoolean("teleport.safe-teleport", true);
        int maxAttempts = plugin.getConfig().getInt("teleport.max-teleport-attempts", 10);

        defaultWorldSettings = new WorldSettings(
                defaultWorldName,
                defaultWorldDisplayName,
                null, // No permission needed for default world
                minX,
                maxX,
                minZ,
                maxZ,
                safeTeleport,
                maxAttempts
        );

        worldSettingsMap.put(defaultWorldName, defaultWorldSettings);

        // Load other worlds if enabled
        if (plugin.getConfig().getBoolean("teleport.other-worlds.enabled", false)) {
            ConfigurationSection worldsSection = plugin.getConfig().getConfigurationSection("teleport.other-worlds.worlds");

            if (worldsSection != null) {
                for (String key : worldsSection.getKeys(false)) {
                    String worldName = worldsSection.getString(key + ".name");
                    String displayName = worldsSection.getString(key + ".display-name", worldName);
                    String permission = worldsSection.getString(key + ".permission");

                    int worldMinX = worldsSection.getInt(key + ".min-x", minX);
                    int worldMaxX = worldsSection.getInt(key + ".max-x", maxX);
                    int worldMinZ = worldsSection.getInt(key + ".min-z", minZ);
                    int worldMaxZ = worldsSection.getInt(key + ".max-z", maxZ);

                    boolean worldSafeTeleport = worldsSection.getBoolean(key + ".safe-teleport", safeTeleport);
                    int worldMaxAttempts = worldsSection.getInt(key + ".max-teleport-attempts", maxAttempts);

                    WorldSettings settings = new WorldSettings(
                            worldName,
                            displayName,
                            permission,
                            worldMinX,
                            worldMaxX,
                            worldMinZ,
                            worldMaxZ,
                            worldSafeTeleport,
                            worldMaxAttempts
                    );

                    worldSettingsMap.put(worldName, settings);
                }
            }
        }
    }

    /**
     * Get settings for a specific world.
     *
     * @param worldName The world name
     * @return World settings or default if not found
     */
    public WorldSettings getWorldSettings(String worldName) {
        return worldSettingsMap.getOrDefault(worldName, defaultWorldSettings);
    }

    /**
     * Get default world settings.
     *
     * @return Default world settings
     */
    public WorldSettings getDefaultWorldSettings() {
        return defaultWorldSettings;
    }

    /**
     * Check if a world name is valid and configured for RTP.
     *
     * @param worldName The world name to check
     * @return True if the world is valid for RTP
     */
    public boolean isValidWorld(String worldName) {
        // Check if the world is in our settings map
        if (worldSettingsMap.containsKey(worldName)) {
            return true;
        }

        // Check if it's the default world
        if (defaultWorldSettings.getName().equalsIgnoreCase(worldName)) {
            return true;
        }

        // Check if the world exists on the server but isn't configured
        World world = Bukkit.getWorld(worldName);
        return world != null;
    }

    /**
     * Get a list of all valid world names for RTP.
     *
     * @return List of valid world names
     */
    public List<String> getValidWorldNames() {
        return new ArrayList<>(worldSettingsMap.keySet());
    }

    /**
     * World settings class.
     */
    public class WorldSettings {
        private final String name;
        private final String displayName;
        private final String permission;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final boolean safeTeleport;
        private final int maxTeleportAttempts;

        public WorldSettings(String name, String displayName, String permission, int minX, int maxX, int minZ, int maxZ, boolean safeTeleport, int maxTeleportAttempts) {
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