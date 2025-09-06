package com.kingrbxd.rtpqueue.utils;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Handles migration from old AdvancedRTPQueue config format to new format
 */
public class ConfigMigrator {
    private final AdvancedRTPQueue plugin;

    public ConfigMigrator(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if migration is needed and perform it
     */
    public boolean checkAndMigrate() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            return false; // Fresh install
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Check if this is the old format (has old-style structure)
        if (isOldFormat(config)) {
            plugin.getLogger().info("Detected old configuration format. Starting migration...");
            return performMigration(config);
        }

        return false; // Already new format
    }

    /**
     * Detect if config is in old format
     */
    private boolean isOldFormat(FileConfiguration config) {
        // Old format detection - check for old structure patterns
        return config.contains("queue.required-players") &&
                config.contains("teleport.world") &&
                !config.contains("plugin.debug") && // New format has this
                !config.contains("cooldowns.queue-join"); // New format has this
    }

    /**
     * Perform the actual migration
     */
    private boolean performMigration(FileConfiguration oldConfig) {
        try {
            // Backup old config
            backupOldConfig();

            // Create new config with migrated values
            FileConfiguration newConfig = new YamlConfiguration();

            migratePluginSettings(oldConfig, newConfig);
            migrateQueueSettings(oldConfig, newConfig);
            migrateCooldownSettings(oldConfig, newConfig);
            migrateTeleportSettings(oldConfig, newConfig);
            migrateClaimProtection(oldConfig, newConfig);
            migrateUISettings(oldConfig, newConfig);
            migrateTitles(oldConfig, newConfig);
            migrateMessages(oldConfig, newConfig);
            migrateSounds(oldConfig, newConfig);
            migrateParticles(oldConfig, newConfig);
            migrateAdvancedSettings(oldConfig, newConfig);

            // Save new config
            File newConfigFile = new File(plugin.getDataFolder(), "config.yml");
            newConfig.save(newConfigFile);

            plugin.getLogger().info("✅ Configuration migration completed successfully!");
            plugin.getLogger().info("📋 Your old config has been backed up to config-backup.yml");
            plugin.getLogger().info("🔧 Please review the new configuration for additional features!");

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Migration failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void backupOldConfig() throws IOException {
        File oldConfigFile = new File(plugin.getDataFolder(), "config.yml");
        File backupFile = new File(plugin.getDataFolder(), "config-backup.yml");

        if (oldConfigFile.exists()) {
            YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldConfigFile);
            oldConfig.save(backupFile);
        }
    }

    private void migratePluginSettings(FileConfiguration oldConfig, FileConfiguration newConfig) {
        newConfig.set("plugin.debug", false); // New feature, default to false
    }

    private void migrateQueueSettings(FileConfiguration oldConfig, FileConfiguration newConfig) {
        // Migrate queue settings
        newConfig.set("queue.required-players", oldConfig.getInt("queue.required-players", 2));
        newConfig.set("queue.clear-interval", oldConfig.getInt("queue.clear-interval", 300));
        newConfig.set("queue.allow-world-switching", true); // New feature
        newConfig.set("queue.auto-join-on-login", false); // New feature
    }

    private void migrateCooldownSettings(FileConfiguration oldConfig, FileConfiguration newConfig) {
        // New cooldown system - set sensible defaults
        newConfig.set("cooldowns.queue-join", 60);
        newConfig.set("cooldowns.queue-leave", 10);
        newConfig.set("cooldowns.pre-teleport", 5);
        newConfig.set("cooldowns.post-teleport", 120);

        // Per-world cooldowns (new feature)
        newConfig.set("cooldowns.per-world-cooldown.enabled", false);
        newConfig.set("cooldowns.per-world-cooldown.worlds.world", 120);
        newConfig.set("cooldowns.per-world-cooldown.worlds.world_nether", 180);
    }

    private void migrateTeleportSettings(FileConfiguration oldConfig, FileConfiguration newConfig) {
        // Migrate teleport settings
        String world = oldConfig.getString("teleport.world", "world");
        newConfig.set("teleport.default-world", world);
        newConfig.set("teleport.default-world-display-name", "&aOverworld");

        // Migrate boundaries
        newConfig.set("teleport.min-x", oldConfig.getInt("teleport.min-x", -500));
        newConfig.set("teleport.max-x", oldConfig.getInt("teleport.max-x", 500));
        newConfig.set("teleport.min-z", oldConfig.getInt("teleport.min-z", -500));
        newConfig.set("teleport.max-z", oldConfig.getInt("teleport.max-z", 500));
        newConfig.set("teleport.min-y", 60); // New setting
        newConfig.set("teleport.max-y", 250); // New setting

        // Migrate safety settings
        newConfig.set("teleport.safe-teleport", oldConfig.getBoolean("teleport.safe-teleport", true));
        newConfig.set("teleport.cancel-on-move", true); // New feature
        newConfig.set("teleport.cancel-on-damage", false); // New feature
        newConfig.set("teleport.cancel-on-chat", false); // New feature
        newConfig.set("teleport.max-attempts", oldConfig.getInt("teleport.max-teleport-attempts", 10));
        newConfig.set("teleport.search-timeout", 30); // New feature

        // Performance settings (new)
        newConfig.set("teleport.cache-safe-locations", true);
        newConfig.set("teleport.max-cached-locations", 10);
        newConfig.set("teleport.allow-fallback-locations", true);

        // Migrate unsafe blocks
        List<String> unsafeBlocks = oldConfig.getStringList("teleport.unsafe-blocks");
        if (unsafeBlocks.isEmpty()) {
            // Set defaults if empty
            unsafeBlocks = List.of("LAVA", "WATER", "CACTUS", "CAMPFIRE", "FIRE", "MAGMA_BLOCK",
                    "SOUL_FIRE", "SOUL_CAMPFIRE", "SWEET_BERRY_BUSH", "WITHER_ROSE",
                    "POWDER_SNOW", "END_PORTAL", "NETHER_PORTAL");
        }
        newConfig.set("teleport.unsafe-blocks", unsafeBlocks);

        // Other worlds (new feature)
        newConfig.set("teleport.other-worlds.enabled", false); // Default disabled for migration
    }

    private void migrateClaimProtection(FileConfiguration oldConfig, FileConfiguration newConfig) {
        // New feature - enable by default
        newConfig.set("claim-protection.enabled", true);
        newConfig.set("claim-protection.plugins.grief-prevention", true);
        newConfig.set("claim-protection.plugins.factions", true);
        newConfig.set("claim-protection.plugins.towny", true);
    }

    private void migrateUISettings(FileConfiguration oldConfig, FileConfiguration newConfig) {
        // New UI system
        newConfig.set("ui.action-bar.enabled", true);
        newConfig.set("ui.action-bar.update-interval", 20);
        newConfig.set("ui.action-bar.queue-wait", "&7Waiting for players... &e{current}&7/&e{required}");
        newConfig.set("ui.action-bar.countdown", "&6Teleporting in &c{time} &6seconds...");
    }

    private void migrateTitles(FileConfiguration oldConfig, FileConfiguration newConfig) {
        // Migrate titles (enhanced system)
        newConfig.set("titles.queue-joined.title", "&6Queue Joined");
        newConfig.set("titles.queue-joined.subtitle", "&7Waiting for &e{required} &7players");
        newConfig.set("titles.queue-joined.fade-in", 10);
        newConfig.set("titles.queue-joined.stay", 40);
        newConfig.set("titles.queue-joined.fade-out", 10);

        newConfig.set("titles.match-found.title", "&aMatch Found!");
        newConfig.set("titles.match-found.subtitle", "&7Teleporting in &c{time} &7seconds");
        newConfig.set("titles.match-found.fade-in", 5);
        newConfig.set("titles.match-found.stay", 30);
        newConfig.set("titles.match-found.fade-out", 10);

        newConfig.set("titles.countdown.title", "&c{time}");
        newConfig.set("titles.countdown.subtitle", "&7Get ready...");
        newConfig.set("titles.countdown.fade-in", 0);
        newConfig.set("titles.countdown.stay", 25);
        newConfig.set("titles.countdown.fade-out", 5);

        newConfig.set("titles.teleported.title", "&aTeleported!");
        newConfig.set("titles.teleported.subtitle", "&7Good luck exploring!");
        newConfig.set("titles.teleported.fade-in", 10);
        newConfig.set("titles.teleported.stay", 30);
        newConfig.set("titles.teleported.fade-out", 20);
    }

    private void migrateMessages(FileConfiguration oldConfig, FileConfiguration newConfig) {
        // Migrate messages with enhanced placeholders
        migrateMessage(oldConfig, newConfig, "join-queue", "messages.join-queue",
                "&a✔ You joined the &e{world} &aqueue! &7({current}/{required})");

        migrateMessage(oldConfig, newConfig, "leave-queue", "messages.leave-queue",
                "&c❌ You left the queue.");

        migrateMessage(oldConfig, newConfig, "already-in-queue", "messages.already-in-queue",
                "&e⚠ You're already in the queue!");

        migrateMessage(oldConfig, newConfig, "not-in-queue", "messages.not-in-queue",
                "&c⚠ You're not in the queue.");

        migrateMessage(oldConfig, newConfig, "opponent-found", "messages.match-found",
                "&a🎯 Match found! Teleporting in &c{time} &aseconds...");

        migrateMessage(oldConfig, newConfig, "teleported", "messages.teleported",
                "&a✅ Successfully teleported!");

        migrateMessage(oldConfig, newConfig, "no-permission", "messages.no-permission",
                "&c⛔ No permission.");

        migrateMessage(oldConfig, newConfig, "reload", "messages.reload-success",
                "&a🔄 Configuration reloaded!");

        migrateMessage(oldConfig, newConfig, "queue-cleared", "messages.queue-cleared",
                "&c🧹 All queues cleared.");

        migrateMessage(oldConfig, newConfig, "invalid-world", "messages.invalid-world",
                "&c⚠ Invalid world: &e{world}");

        migrateMessage(oldConfig, newConfig, "invalid-command", "messages.invalid-command",
                "&c❓ Usage: &e/rtpqueue [world <name>|leave|cancel]");

        migrateMessage(oldConfig, newConfig, "teleport-failed", "messages.teleport-failed",
                "&c❌ Teleport failed! No safe location found.");

        // Add new messages
        newConfig.set("messages.no-permission-world", "&c⛔ No permission for world &e{world}&c.");
        newConfig.set("messages.teleporting", "&6🚀 Teleporting to &e{world}&6...");
        newConfig.set("messages.reload-failed", "&c❌ Reload failed: &e{error}");
        newConfig.set("messages.cancelled-moved", "&c❌ Teleport cancelled - you moved!");
        newConfig.set("messages.cancelled-damage", "&c❌ Teleport cancelled - you took damage!");
        newConfig.set("messages.cancelled-chat", "&c❌ Teleport cancelled - you chatted!");
        newConfig.set("messages.cancelled-timeout", "&c❌ Teleport cancelled - location search timed out!");
        newConfig.set("messages.cancelled-shutdown", "&c❌ Teleport cancelled - server restarting!");
        newConfig.set("messages.cooldown-active", "&c⏰ Cooldown active! Wait &e{time}&c.");
        newConfig.set("messages.cooldown-queue-join", "&c⏰ Queue join cooldown! Wait &e{time}&c.");
        newConfig.set("messages.cooldown-post-teleport", "&c⏰ Post-teleport cooldown! Wait &e{time}&c.");
    }

    private void migrateMessage(FileConfiguration oldConfig, FileConfiguration newConfig,
                                String oldKey, String newKey, String defaultValue) {
        String message = oldConfig.getString("messages." + oldKey, defaultValue);
        newConfig.set(newKey, message);
    }

    private void migrateSounds(FileConfiguration oldConfig, FileConfiguration newConfig) {
        newConfig.set("sounds.enabled", true);

        // Migrate sounds with enhanced structure
        migrateSoundConfig(oldConfig, newConfig, "queue-join", "sounds.queue-join",
                "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0, 1.0);

        newConfig.set("sounds.queue-leave.sound", "ENTITY_ITEM_BREAK");
        newConfig.set("sounds.queue-leave.volume", 0.7);
        newConfig.set("sounds.queue-leave.pitch", 0.8);

        migrateSoundConfig(oldConfig, newConfig, "opponent-found", "sounds.match-found",
                "ENTITY_PLAYER_LEVELUP", 1.0, 1.2);

        newConfig.set("sounds.countdown.sound", "BLOCK_NOTE_BLOCK_PLING");
        newConfig.set("sounds.countdown.volume", 0.8);
        newConfig.set("sounds.countdown.pitch", 1.5);

        migrateSoundConfig(oldConfig, newConfig, "teleport", "sounds.teleport-success",
                "ENTITY_ENDERMAN_TELEPORT", 1.0, 1.0);

        migrateSoundConfig(oldConfig, newConfig, "error", "sounds.teleport-cancelled",
                "ENTITY_VILLAGER_NO", 0.8, 0.9);

        newConfig.set("sounds.error.sound", "BLOCK_ANVIL_LAND");
        newConfig.set("sounds.error.volume", 0.5);
        newConfig.set("sounds.error.pitch", 0.8);
    }

    private void migrateSoundConfig(FileConfiguration oldConfig, FileConfiguration newConfig,
                                    String oldKey, String newKey, String defaultSound,
                                    double defaultVolume, double defaultPitch) {
        String sound = oldConfig.getString("sounds." + oldKey, defaultSound);
        newConfig.set(newKey + ".sound", sound);
        newConfig.set(newKey + ".volume", defaultVolume);
        newConfig.set(newKey + ".pitch", defaultPitch);
    }

    private void migrateParticles(FileConfiguration oldConfig, FileConfiguration newConfig) {
        // New particle system
        newConfig.set("particles.enabled", true);
        newConfig.set("particles.teleport-start.particle", "PORTAL");
        newConfig.set("particles.teleport-start.count", 50);
        newConfig.set("particles.teleport-start.spread", 1.0);
        newConfig.set("particles.teleport-success.particle", "EXPLOSION_NORMAL");
        newConfig.set("particles.teleport-success.count", 20);
        newConfig.set("particles.teleport-success.spread", 0.5);
    }

    private void migrateAdvancedSettings(FileConfiguration oldConfig, FileConfiguration newConfig) {
        // New advanced settings
        newConfig.set("advanced.log-queue-actions", false);
        newConfig.set("advanced.log-teleports", true);
    }
}