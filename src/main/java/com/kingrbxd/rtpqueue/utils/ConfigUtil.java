package com.kingrbxd.rtpqueue.utils;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Utility class to handle config operations
 */
public class ConfigUtil {

    private static AdvancedRTPQueue plugin;

    /**
     * Initialize the config util with plugin instance
     *
     * @param pluginInstance The main plugin instance
     */
    public static void initialize(AdvancedRTPQueue pluginInstance) {
        plugin = pluginInstance;
    }

    /**
     * Load messages from config into MessageUtil
     */
    public static void loadMessages() {
        if (plugin == null) {
            plugin = AdvancedRTPQueue.getInstance();
        }

        FileConfiguration config = plugin.getConfig();

        // If messages section exists, reload all messages
        if (config.contains("messages")) {
            plugin.getLogger().info("Loading messages from config...");
            MessageUtil.clearCachedMessages();
        }
    }

    /**
     * Get a value from config with default
     *
     * @param path Path in config
     * @param defaultValue Default value if not found
     * @return The value from config or default
     */
    public static String getString(String path, String defaultValue) {
        if (plugin == null) {
            plugin = AdvancedRTPQueue.getInstance();
        }
        return plugin.getConfig().getString(path, defaultValue);
    }

    /**
     * Get boolean value from config with default
     *
     * @param path Path in config
     * @param defaultValue Default value if not found
     * @return The value from config or default
     */
    public static boolean getBoolean(String path, boolean defaultValue) {
        if (plugin == null) {
            plugin = AdvancedRTPQueue.getInstance();
        }
        return plugin.getConfig().getBoolean(path, defaultValue);
    }

    /**
     * Get int value from config with default
     *
     * @param path Path in config
     * @param defaultValue Default value if not found
     * @return The value from config or default
     */
    public static int getInt(String path, int defaultValue) {
        if (plugin == null) {
            plugin = AdvancedRTPQueue.getInstance();
        }
        return plugin.getConfig().getInt(path, defaultValue);
    }
}