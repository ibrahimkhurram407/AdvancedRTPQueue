package com.kingrbxd.rtpqueue.utils;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigUtil {
    private static FileConfiguration config;

    public static void loadMessages() {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        config = plugin.getConfig();

        // Set defaults if missing
        config.addDefault("messages.join-queue", "&aYou have joined the RTP queue!");
        config.addDefault("messages.leave-queue", "&cYou have left the RTP queue!");
        config.addDefault("messages.already-in-queue", "&cYou are already in the queue!");
        config.addDefault("messages.not-in-queue", "&cYou are not in the queue!");
        config.addDefault("messages.teleporting-title", "&aTeleporting...");
        config.addDefault("messages.teleporting-subtitle", "&fYou have been teleported!");
        config.addDefault("messages.teleported", "&aYou have been randomly teleported!");
        config.addDefault("messages.no-permission", "&cYou don't have permission to use this command!");
        config.addDefault("messages.reload", "&aConfiguration reloaded successfully!");
        config.addDefault("messages.queue-cleared", "&cThe RTP queue has been cleared!");
        config.addDefault("messages.invalid-world", "&cThe configured teleportation world does not exist!");
        config.addDefault("messages.invalid-command", "&cInvalid command! Use /rtpqueue or /rtpqueue cancel.");

        config.addDefault("sounds.queue-join", "ENTITY_PLAYER_LEVELUP");
        config.addDefault("sounds.teleport", "ENTITY_ENDERMAN_TELEPORT");
        config.addDefault("sounds.error", "ENTITY_VILLAGER_NO");
        config.addDefault("sounds.queue-cleared", "ENTITY_ENDER_DRAGON_GROWL");

        plugin.saveConfig();
    }

    public static String getMessage(String key) {
        return config.getString("messages." + key, "&cMessage not found!");
    }
}
