package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;

public class CooldownManager {
    private final AdvancedRTPQueue plugin;
    private final Map<UUID, Long> queueCooldowns = new HashMap<>();
    private final Map<String, Long> worldQueueCooldowns = new HashMap<>();
    private final Map<UUID, Boolean> preTeleportPlayers = new HashMap<>();
    private final Map<UUID, Integer> teleportTasks = new HashMap<>();

    public CooldownManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if a player can join the queue (not on cooldown).
     *
     * @param player The player to check
     * @param worldName The world name for per-world cooldown check
     * @return true if player can join, false if on cooldown
     */
    public boolean canJoinQueue(Player player, String worldName) {
        if (player.hasPermission("rtpqueue.admin")) {
            return true; // Admins bypass cooldown
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Check per-world cooldown if enabled
        if (plugin.getConfig().getBoolean("cooldowns.per-world-cooldown.enabled", false) && worldName != null) {
            String key = uuid.toString() + ":" + worldName;
            if (worldQueueCooldowns.containsKey(key)) {
                int worldCooldown = plugin.getConfig().getInt("cooldowns.per-world-cooldown.worlds." + worldName, 60);

                // Skip if cooldown is disabled (-1)
                if (worldCooldown == -1) {
                    return true;
                }

                long cooldownTime = worldQueueCooldowns.get(key);
                if (now < cooldownTime) {
                    int timeLeft = (int) ((cooldownTime - now) / 1000);
                    String message = plugin.getConfig().getString("messages.cooldown-active", "&#ff9900⏱ &eOn cooldown. Try again in {time} seconds.");
                    message = message.replace("{time}", String.valueOf(timeLeft));
                    MessageUtil.sendMessage(player, message);
                    MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
                    return false;
                }
            }
        }

        // Check global cooldown
        int globalCooldown = plugin.getConfig().getInt("cooldowns.queue-join", 60);

        // Skip if cooldown is disabled (-1)
        if (globalCooldown == -1) {
            return true;
        }

        if (queueCooldowns.containsKey(uuid)) {
            long cooldownTime = queueCooldowns.get(uuid);

            if (now < cooldownTime) {
                int timeLeft = (int) ((cooldownTime - now) / 1000);
                String message = plugin.getConfig().getString("messages.cooldown-active", "&#ff9900⏱ &eOn cooldown. Try again in {time} seconds.");
                message = message.replace("{time}", String.valueOf(timeLeft));
                MessageUtil.sendMessage(player, message);
                MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
                return false;
            }
        }

        return true;
    }

    /**
     * Set a cooldown for a player after joining the queue.
     *
     * @param player The player to set cooldown for
     * @param worldName The world name for per-world cooldown
     */
    public void setQueueCooldown(Player player, String worldName) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        int globalCooldown = plugin.getConfig().getInt("cooldowns.queue-join", 60);

        // Set global cooldown if not disabled
        if (globalCooldown != -1) {
            queueCooldowns.put(uuid, now + (globalCooldown * 1000));
        }

        // Set per-world cooldown if enabled and not disabled
        if (plugin.getConfig().getBoolean("cooldowns.per-world-cooldown.enabled", false) && worldName != null) {
            String key = uuid.toString() + ":" + worldName;
            int worldCooldown = plugin.getConfig().getInt("cooldowns.per-world-cooldown.worlds." + worldName, 60);

            if (worldCooldown != -1) {
                worldQueueCooldowns.put(key, now + (worldCooldown * 1000));
            }
        }
    }

    /**
     * Start pre-teleport countdown for players.
     *
     * @param player1 First player to teleport
     * @param player2 Second player to teleport
     * @return true if countdown started successfully
     */
    public boolean startPreTeleportCountdown(Player player1, Player player2) {
        int preTeleportTime = plugin.getConfig().getInt("cooldowns.pre-teleport", 5);

        // Get the world name from the queue handler
        String worldName = plugin.getQueueHandler().getPlayerQueueWorld(player1);
        if (worldName == null) {
            worldName = plugin.getWorldManager().getDefaultWorldSettings().getName();
        }

        // Get the world settings
        WorldManager.WorldSettings worldSettings = plugin.getWorldManager().getWorldSettings(worldName);

        // Create list of players
        final List<Player> playersToTeleport = new ArrayList<>();
        playersToTeleport.add(player1);
        playersToTeleport.add(player2);

        // Store for later use in runnable
        final WorldManager.WorldSettings finalWorldSettings = worldSettings;

        // Skip countdown if disabled with -1
        if (preTeleportTime <= 0) {
            // Directly teleport players
            TeleportHandler.teleportPlayersToWorld(playersToTeleport, finalWorldSettings);
            return true;
        }

        UUID uuid1 = player1.getUniqueId();
        UUID uuid2 = player2.getUniqueId();

        // Mark players as in pre-teleport
        preTeleportPlayers.put(uuid1, true);
        preTeleportPlayers.put(uuid2, true);

        // Send initial message
        String message = plugin.getConfig().getString("messages.opponent-found", "&#ff0000⚔ &cMatch found! Teleporting in {time} seconds...");
        message = message.replace("{time}", String.valueOf(preTeleportTime));
        MessageUtil.sendMessage(player1, message);
        MessageUtil.sendMessage(player2, message);
        MessageUtil.playSound(player1, plugin.getConfig().getString("sounds.opponent-found"));
        MessageUtil.playSound(player2, plugin.getConfig().getString("sounds.opponent-found"));

        // Start countdown task
        int taskId = new BukkitRunnable() {
            private int timeLeft = preTeleportTime;

            @Override
            public void run() {
                timeLeft--;

                // Check if both players are still online and in pre-teleport
                if (!player1.isOnline() || !player2.isOnline() ||
                        !preTeleportPlayers.containsKey(uuid1) || !preTeleportPlayers.containsKey(uuid2)) {
                    cancel();
                    preTeleportPlayers.remove(uuid1);
                    preTeleportPlayers.remove(uuid2);
                    teleportTasks.remove(uuid1);
                    teleportTasks.remove(uuid2);
                    return;
                }

                // Send countdown message
                if (timeLeft > 0) {
                    String countdownMsg = "&c" + timeLeft + "...";
                    MessageUtil.sendActionBar(player1, countdownMsg);
                    MessageUtil.sendActionBar(player2, countdownMsg);
                    MessageUtil.playSound(player1, "BLOCK_NOTE_BLOCK_PLING");
                    MessageUtil.playSound(player2, "BLOCK_NOTE_BLOCK_PLING");
                } else {
                    // Time's up, proceed with teleport
                    preTeleportPlayers.remove(uuid1);
                    preTeleportPlayers.remove(uuid2);
                    teleportTasks.remove(uuid1);
                    teleportTasks.remove(uuid2);

                    // Perform the teleport with correct method signature
                    TeleportHandler.teleportPlayersToWorld(playersToTeleport, finalWorldSettings);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();

        // Store task IDs for cancellation if needed
        teleportTasks.put(uuid1, taskId);
        teleportTasks.put(uuid2, taskId);

        return true;
    }

    /**
     * Cancel pre-teleport countdown for a player.
     *
     * @param player The player to cancel for
     */
    public void cancelPreTeleport(Player player) {
        UUID uuid = player.getUniqueId();

        if (preTeleportPlayers.containsKey(uuid)) {
            preTeleportPlayers.remove(uuid);

            // Cancel the task if it exists
            if (teleportTasks.containsKey(uuid)) {
                plugin.getServer().getScheduler().cancelTask(teleportTasks.get(uuid));
                teleportTasks.remove(uuid);
            }

            // Notify player
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.move-cancelled",
                    "&#ff6600⚠ &eTeleport cancelled! You moved during countdown."));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
        }
    }

    /**
     * Check if a player is in pre-teleport state.
     *
     * @param player The player to check
     * @return true if player is in pre-teleport
     */
    public boolean isInPreTeleport(Player player) {
        return preTeleportPlayers.containsKey(player.getUniqueId());
    }

    /**
     * Clear all cooldowns for a player.
     *
     * @param player The player to clear cooldowns for
     */
    public void clearCooldowns(Player player) {
        UUID uuid = player.getUniqueId();
        queueCooldowns.remove(uuid);

        // Clear any world-specific cooldowns
        worldQueueCooldowns.entrySet().removeIf(entry -> entry.getKey().startsWith(uuid.toString()));

        // Cancel pre-teleport if active
        if (preTeleportPlayers.containsKey(uuid)) {
            cancelPreTeleport(player);
        }
    }
}