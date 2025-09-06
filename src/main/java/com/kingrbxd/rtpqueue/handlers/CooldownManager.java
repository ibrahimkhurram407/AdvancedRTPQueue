package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete cooldown manager with all features implemented
 */
public class CooldownManager {
    private final AdvancedRTPQueue plugin;
    private final Map<UUID, Long> queueJoinCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> queueLeaveCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> postTeleportCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> perWorldCooldowns = new ConcurrentHashMap<>();

    public CooldownManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if player can join queue
     */
    public boolean canJoinQueue(Player player) {
        if (player.hasPermission("rtpqueue.bypass.cooldown")) {
            return true;
        }

        // Check queue join cooldown
        if (hasQueueJoinCooldown(player)) {
            long remaining = getQueueJoinCooldownRemaining(player);

            Map<String, String> placeholders = Map.of("time", MessageUtil.formatTime((int) (remaining / 1000)));
            MessageUtil.sendMessage(player, "cooldown-queue-join", placeholders);
            return false;
        }

        // Check post teleport cooldown
        if (hasPostTeleportCooldown(player)) {
            long remaining = getPostTeleportCooldownRemaining(player);

            Map<String, String> placeholders = Map.of("time", MessageUtil.formatTime((int) (remaining / 1000)));
            MessageUtil.sendMessage(player, "cooldown-post-teleport", placeholders);
            return false;
        }

        return true;
    }

    /**
     * Check if player can join specific world queue
     */
    public boolean canJoinWorldQueue(Player player, String worldName) {
        if (player.hasPermission("rtpqueue.bypass.cooldown")) {
            return true;
        }

        // Check general cooldowns first
        if (!canJoinQueue(player)) {
            return false;
        }

        // Check per-world cooldown if enabled
        if (plugin.getConfigManager().getBoolean("cooldowns.per-world-cooldown.enabled")) {
            if (hasPerWorldCooldown(player, worldName)) {
                long remaining = getPerWorldCooldownRemaining(player, worldName);

                Map<String, String> placeholders = Map.of("time", MessageUtil.formatTime((int) (remaining / 1000)));
                MessageUtil.sendMessage(player, "cooldown-active", placeholders);
                return false;
            }
        }

        return true;
    }

    /**
     * Check if player can leave queue
     */
    public boolean canLeaveQueue(Player player) {
        if (player.hasPermission("rtpqueue.bypass.cooldown")) {
            return true;
        }

        if (hasQueueLeaveCooldown(player)) {
            long remaining = getQueueLeaveCooldownRemaining(player);

            Map<String, String> placeholders = Map.of("time", MessageUtil.formatTime((int) (remaining / 1000)));
            MessageUtil.sendMessage(player, "cooldown-active", placeholders);
            return false;
        }

        return true;
    }

    /**
     * Set queue join cooldown
     */
    public void setQueueJoinCooldown(Player player) {
        int cooldownSeconds = plugin.getConfigManager().getInt("cooldowns.queue-join", 60);
        if (cooldownSeconds > 0 && !player.hasPermission("rtpqueue.bypass.cooldown")) {
            queueJoinCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));
        }
    }

    /**
     * Set queue leave cooldown
     */
    public void setQueueLeaveCooldown(Player player) {
        int cooldownSeconds = plugin.getConfigManager().getInt("cooldowns.queue-leave", 10);
        if (cooldownSeconds > 0 && !player.hasPermission("rtpqueue.bypass.cooldown")) {
            queueLeaveCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));
        }
    }

    /**
     * Set post teleport cooldown
     */
    public void setPostTeleportCooldown(Player player) {
        int cooldownSeconds = plugin.getConfigManager().getInt("cooldowns.post-teleport", 120);
        if (cooldownSeconds > 0 && !player.hasPermission("rtpqueue.bypass.cooldown")) {
            postTeleportCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));
        }
    }

    /**
     * Set per-world cooldown
     */
    public void setPerWorldCooldown(Player player, String worldName) {
        if (!plugin.getConfigManager().getBoolean("cooldowns.per-world-cooldown.enabled")) {
            return;
        }

        if (player.hasPermission("rtpqueue.bypass.cooldown")) {
            return;
        }

        String configPath = "cooldowns.per-world-cooldown.worlds." + worldName;
        int cooldownSeconds = plugin.getConfigManager().getInt(configPath, 0);

        if (cooldownSeconds > 0) {
            perWorldCooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .put(worldName, System.currentTimeMillis() + (cooldownSeconds * 1000L));
        }
    }

    /**
     * Check if player has queue join cooldown
     */
    public boolean hasQueueJoinCooldown(Player player) {
        Long cooldownEnd = queueJoinCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) {
            return false;
        }

        if (System.currentTimeMillis() >= cooldownEnd) {
            queueJoinCooldowns.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    /**
     * Check if player has queue leave cooldown
     */
    public boolean hasQueueLeaveCooldown(Player player) {
        Long cooldownEnd = queueLeaveCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) {
            return false;
        }

        if (System.currentTimeMillis() >= cooldownEnd) {
            queueLeaveCooldowns.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    /**
     * Check if player has post teleport cooldown
     */
    public boolean hasPostTeleportCooldown(Player player) {
        Long cooldownEnd = postTeleportCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) {
            return false;
        }

        if (System.currentTimeMillis() >= cooldownEnd) {
            postTeleportCooldowns.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    /**
     * Check if player has per-world cooldown
     */
    public boolean hasPerWorldCooldown(Player player, String worldName) {
        Map<String, Long> playerCooldowns = perWorldCooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return false;
        }

        Long cooldownEnd = playerCooldowns.get(worldName);
        if (cooldownEnd == null) {
            return false;
        }

        if (System.currentTimeMillis() >= cooldownEnd) {
            playerCooldowns.remove(worldName);
            if (playerCooldowns.isEmpty()) {
                perWorldCooldowns.remove(player.getUniqueId());
            }
            return false;
        }

        return true;
    }

    /**
     * Get remaining queue join cooldown in milliseconds
     */
    public long getQueueJoinCooldownRemaining(Player player) {
        Long cooldownEnd = queueJoinCooldowns.get(player.getUniqueId());
        return cooldownEnd != null ? Math.max(0, cooldownEnd - System.currentTimeMillis()) : 0;
    }

    /**
     * Get remaining queue leave cooldown in milliseconds
     */
    public long getQueueLeaveCooldownRemaining(Player player) {
        Long cooldownEnd = queueLeaveCooldowns.get(player.getUniqueId());
        return cooldownEnd != null ? Math.max(0, cooldownEnd - System.currentTimeMillis()) : 0;
    }

    /**
     * Get remaining post teleport cooldown in milliseconds
     */
    public long getPostTeleportCooldownRemaining(Player player) {
        Long cooldownEnd = postTeleportCooldowns.get(player.getUniqueId());
        return cooldownEnd != null ? Math.max(0, cooldownEnd - System.currentTimeMillis()) : 0;
    }

    /**
     * Get remaining per-world cooldown in milliseconds
     */
    public long getPerWorldCooldownRemaining(Player player, String worldName) {
        Map<String, Long> playerCooldowns = perWorldCooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return 0;
        }

        Long cooldownEnd = playerCooldowns.get(worldName);
        return cooldownEnd != null ? Math.max(0, cooldownEnd - System.currentTimeMillis()) : 0;
    }

    /**
     * Clear all cooldowns for a player
     */
    public void clearAllCooldowns(Player player) {
        UUID playerUUID = player.getUniqueId();
        queueJoinCooldowns.remove(playerUUID);
        queueLeaveCooldowns.remove(playerUUID);
        postTeleportCooldowns.remove(playerUUID);
        perWorldCooldowns.remove(playerUUID);

        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Cleared all cooldowns for " + player.getName());
        }
    }

    /**
     * Clear all cooldowns for all players
     */
    public void clearAllCooldowns() {
        queueJoinCooldowns.clear();
        queueLeaveCooldowns.clear();
        postTeleportCooldowns.clear();
        perWorldCooldowns.clear();

        plugin.getLogger().info("Cleared all cooldowns for all players");
    }

    /**
     * Handle player disconnect
     */
    public void handlePlayerDisconnect(Player player) {
        // Keep cooldowns when player disconnects for persistence
        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Keeping cooldowns for disconnected player: " + player.getName());
        }
    }

    /**
     * Clean up expired cooldowns (maintenance task)
     */
    public void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();

        // Clean queue join cooldowns
        queueJoinCooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTime);

        // Clean queue leave cooldowns
        queueLeaveCooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTime);

        // Clean post teleport cooldowns
        postTeleportCooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTime);

        // Clean per-world cooldowns
        perWorldCooldowns.entrySet().removeIf(playerEntry -> {
            playerEntry.getValue().entrySet().removeIf(worldEntry -> worldEntry.getValue() <= currentTime);
            return playerEntry.getValue().isEmpty();
        });

        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Cleaned up expired cooldowns");
        }
    }
}