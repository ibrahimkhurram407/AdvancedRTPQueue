package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete cooldown manager with all features implemented.
 *
 * This class keeps per-player cooldown state in memory and provides helpers
 * to check/set/clean cooldowns. The handlePlayerDisconnect(Player) method
 * now uses the Player parameter (logs debug info) to avoid "variable never used"
 * warnings in IDEs and to provide useful runtime information when plugin.debug is enabled.
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
     * Check if player can join queue (global checks).
     */
    public boolean canJoinQueue(Player player) {
        if (player == null) return false;
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
     * Check if player can join a specific world queue (includes per-world cooldowns).
     */
    public boolean canJoinWorldQueue(Player player, String worldName) {
        if (player == null) return false;
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
     * Check if player can leave queue.
     */
    public boolean canLeaveQueue(Player player) {
        if (player == null) return false;
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
     * Set queue join cooldown for the player.
     */
    public void setQueueJoinCooldown(Player player) {
        if (player == null) return;
        int cooldownSeconds = plugin.getConfigManager().getInt("cooldowns.queue-join", 60);
        if (cooldownSeconds > 0 && !player.hasPermission("rtpqueue.bypass.cooldown")) {
            queueJoinCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));
        }
    }

    /**
     * Set queue leave cooldown for the player.
     */
    public void setQueueLeaveCooldown(Player player) {
        if (player == null) return;
        int cooldownSeconds = plugin.getConfigManager().getInt("cooldowns.queue-leave", 10);
        if (cooldownSeconds > 0 && !player.hasPermission("rtpqueue.bypass.cooldown")) {
            queueLeaveCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));
        }
    }

    /**
     * Set post-teleport cooldown for the player.
     */
    public void setPostTeleportCooldown(Player player) {
        if (player == null) return;
        int cooldownSeconds = plugin.getConfigManager().getInt("cooldowns.post-teleport", 120);
        if (cooldownSeconds > 0 && !player.hasPermission("rtpqueue.bypass.cooldown")) {
            postTeleportCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));
        }
    }

    /**
     * Set per-world cooldown for the player (records expiration time for the given world).
     * worldName should be the identifier used by the queue system (config key or bukkit name per your design).
     */
    public void setPerWorldCooldown(Player player, String worldName) {
        if (player == null || worldName == null) return;
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
     * Check if player has an active queue join cooldown.
     */
    public boolean hasQueueJoinCooldown(Player player) {
        if (player == null) return false;
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
     * Check if player has an active queue leave cooldown.
     */
    public boolean hasQueueLeaveCooldown(Player player) {
        if (player == null) return false;
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
     * Check if player has an active post-teleport cooldown.
     */
    public boolean hasPostTeleportCooldown(Player player) {
        if (player == null) return false;
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
     * Check if player has an active per-world cooldown for the given world.
     */
    public boolean hasPerWorldCooldown(Player player, String worldName) {
        if (player == null || worldName == null) return false;
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
     * Get remaining queue join cooldown in milliseconds.
     */
    public long getQueueJoinCooldownRemaining(Player player) {
        if (player == null) return 0;
        Long cooldownEnd = queueJoinCooldowns.get(player.getUniqueId());
        return cooldownEnd != null ? Math.max(0, cooldownEnd - System.currentTimeMillis()) : 0;
    }

    /**
     * Get remaining queue leave cooldown in milliseconds.
     */
    public long getQueueLeaveCooldownRemaining(Player player) {
        if (player == null) return 0;
        Long cooldownEnd = queueLeaveCooldowns.get(player.getUniqueId());
        return cooldownEnd != null ? Math.max(0, cooldownEnd - System.currentTimeMillis()) : 0;
    }

    /**
     * Get remaining post teleport cooldown in milliseconds.
     */
    public long getPostTeleportCooldownRemaining(Player player) {
        if (player == null) return 0;
        Long cooldownEnd = postTeleportCooldowns.get(player.getUniqueId());
        return cooldownEnd != null ? Math.max(0, cooldownEnd - System.currentTimeMillis()) : 0;
    }

    /**
     * Get remaining per-world cooldown in milliseconds.
     */
    public long getPerWorldCooldownRemaining(Player player, String worldName) {
        if (player == null || worldName == null) return 0;
        Map<String, Long> playerCooldowns = perWorldCooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return 0;
        }

        Long cooldownEnd = playerCooldowns.get(worldName);
        return cooldownEnd != null ? Math.max(0, cooldownEnd - System.currentTimeMillis()) : 0;
    }

    /**
     * Clear all cooldowns for a single player.
     */
    public void clearAllCooldowns(Player player) {
        if (player == null) return;
        UUID playerUUID = player.getUniqueId();
        queueJoinCooldowns.remove(playerUUID);
        queueLeaveCooldowns.remove(playerUUID);
        postTeleportCooldowns.remove(playerUUID);
        perWorldCooldowns.remove(playerUUID);

        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Cleared all cooldowns for " + player.getName() + " (" + playerUUID + ")");
        }
    }

    /**
     * Clear all cooldowns for all players.
     */
    public void clearAllCooldowns() {
        queueJoinCooldowns.clear();
        queueLeaveCooldowns.clear();
        postTeleportCooldowns.clear();
        perWorldCooldowns.clear();

        plugin.getLogger().info("Cleared all cooldowns for all players");
    }

    /**
     * Handle player disconnect.
     *
     * We intentionally KEEP cooldowns across disconnects (so players can't bypass cooldowns by relogging).
     * This method now uses the Player parameter (logs debug information) to avoid IDE warnings.
     */
    public void handlePlayerDisconnect(Player player) {
        if (player == null) return;

        // Keep cooldowns for disconnected players (do not remove them).
        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Player disconnected — preserving cooldowns for: " + player.getName()
                    + " (" + player.getUniqueId() + ")");
        }
    }

    /**
     * Optional convenience: handle disconnect by UUID (callers who don't have a Player instance can use this).
     */
    public void handlePlayerDisconnect(UUID playerUuid) {
        if (playerUuid == null) return;
        // Nothing to remove — this method mirrors the Player variant but accepts UUIDs.
        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Player UUID disconnected — preserving cooldowns for: " + playerUuid);
        }
    }

    /**
     * Clean up expired cooldowns (maintenance task).
     */
    public void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();

        // Clean queue join cooldowns
        queueJoinCooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTime);

        // Clean queue leave cooldowns
        queueLeaveCooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTime);

        // Clean post teleport cooldowns
        postTeleportCooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTime);

        // Clean per-world cooldowns (remove expired world entries and players with no remaining entries)
        perWorldCooldowns.entrySet().removeIf(playerEntry -> {
            playerEntry.getValue().entrySet().removeIf(worldEntry -> worldEntry.getValue() <= currentTime);
            return playerEntry.getValue().isEmpty();
        });

        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Cleaned up expired cooldowns");
        }
    }
}