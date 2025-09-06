package com.kingrbxd.rtpqueue.utils;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

/**
 * ConfigMigrator
 *
 * Detects whether an existing config.yml is "old" and, if so, backs it up (config-old.yml or
 * config-old-<timestamp>.yml) and writes a fresh default config.yml from the plugin resource.
 *
 * Detection rules:
 *  - Required-fields rule: the user's config is considered "new" only if it contains all required keys.
 *    The required keys include at minimum "plugin.debug" and "plugin.prefix" per project requirement.
 *  - If the user's config is missing required keys, it will be considered old and migrated (backed up + replaced).
 *
 * Backwards-compatible API:
 *  - new ConfigMigrator(plugin).checkAndMigrate()
 *  - ConfigMigrator.checkAndMigrate(plugin)  (static convenience)
 *  - ConfigMigrator.migrateIfNeeded(plugin)
 */
public final class ConfigMigrator {
    private final AdvancedRTPQueue plugin;

    /**
     * Instance constructor that captures the plugin. Use this if you want to call the no-arg
     * instance method checkAndMigrate().
     */
    public ConfigMigrator(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    /**
     * Instance compatibility method. No-arg call for callers that constructed the migrator
     * with the plugin instance.
     */
    public void checkAndMigrate() {
        migrateIfNeeded(this.plugin);
    }

    /**
     * Backwards-compatible static convenience entrypoint.
     */
    public static void checkAndMigrate(AdvancedRTPQueue plugin) {
        migrateIfNeeded(plugin);
    }

    /**
     * Primary migration entry point. Call this early in onEnable().
     */
    public static void migrateIfNeeded(AdvancedRTPQueue plugin) {
        if (plugin == null) return;

        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                plugin.getLogger().warning("ConfigMigrator: failed to create data folder: " + dataFolder.getAbsolutePath());
            }

            File configFile = new File(dataFolder, "config.yml");
            // if there's no config we simply write the default
            if (!configFile.exists()) {
                plugin.getLogger().fine("ConfigMigrator: no existing config.yml to migrate.");
                plugin.saveDefaultConfig();
                return;
            }

            // Load user's config
            FileConfiguration userCfg = YamlConfiguration.loadConfiguration(configFile);

            // REQUIRED KEYS: the user's config must include these keys to be considered "new".
            List<String> requiredKeys = Arrays.asList(
                    "plugin.debug",
                    "plugin.prefix"
            );

            List<String> missingKeys = getMissingRequiredKeys(userCfg, requiredKeys);
            boolean missingRequired = !missingKeys.isEmpty();
            boolean isOld = missingRequired;

            if (plugin.getConfig() != null && plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().info("ConfigMigrator: missingRequired=" + missingRequired + " treatAsOld=" + isOld
                        + (missingRequired ? " missingKeys=" + missingKeys : ""));
            }

            if (!isOld) {
                plugin.getLogger().fine("ConfigMigrator: config.yml appears up-to-date; migration not required.");
                return;
            }

            // Backup existing config.yml: config-old.yml or config-old-<timestamp>.yml if previous backup exists
            File backup = new File(dataFolder, "config-old.yml");
            if (backup.exists()) {
                String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
                backup = new File(dataFolder, "config-old-" + ts + ".yml");
            }

            try {
                Files.move(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("ConfigMigrator: Existing config.yml moved to " + backup.getName());
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "ConfigMigrator: Failed to rename existing config.yml to " + backup.getName() + " - attempting copy", ex);
                try {
                    Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("ConfigMigrator: Existing config.yml copied to " + backup.getName() + " (original left in place)");
                    boolean deleted = configFile.delete();
                    if (!deleted) {
                        plugin.getLogger().warning("ConfigMigrator: Unable to delete original config.yml after copy. Original left in place.");
                    }
                } catch (IOException ex2) {
                    plugin.getLogger().log(Level.SEVERE, "ConfigMigrator: Failed to backup config.yml", ex2);
                    return;
                }
            }

            // Save fresh default config.yml
            plugin.saveDefaultConfig();
            plugin.getLogger().info("ConfigMigrator: New default config.yml has been created. Old config retained as " + backup.getName());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "ConfigMigrator failed: " + t.getMessage(), t);
        }
    }

    // ---- helpers ----

    private static List<String> getMissingRequiredKeys(FileConfiguration cfg, List<String> requiredKeys) {
        List<String> missing = new ArrayList<>();
        if (cfg == null) {
            missing.addAll(requiredKeys);
            return missing;
        }
        for (String key : requiredKeys) {
            if (!cfg.contains(key)) {
                missing.add(key);
            }
        }
        return missing;
    }
}