package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles queue management for random teleport.
 */
public class QueueHandler {
    private final AdvancedRTPQueue plugin;
    private final Map<String, List<UUID>> worldQueues = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerWorldMap = new ConcurrentHashMap<>();

    public QueueHandler(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    /**
     * Add a player to a specific world queue.
     *
     * @param player The player to add
     * @param worldName The world name
     * @return true if added successfully, false if already in queue or other error
     */
    public boolean addToQueue(Player player, String worldName) {
        UUID playerUUID = player.getUniqueId();

        // Check if player is already in a queue
        String currentWorld = playerWorldMap.get(playerUUID);

        // If allow-world-switching is enabled and player is in a different world queue
        if (currentWorld != null && !currentWorld.equals(worldName)) {
            if (plugin.getConfig().getBoolean("queue.allow-world-switching", false)) {
                // Remove from current world queue
                removeFromQueue(player);
            } else {
                // Player is already in queue for a different world
                MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.already-in-queue"));
                return false;
            }
        } else if (currentWorld != null) {
            // Player is already in queue for this world
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.already-in-queue"));
            return false;
        }

        // Get or create queue for this world
        List<UUID> queue = worldQueues.computeIfAbsent(worldName, k -> new ArrayList<>());

        // Add player to queue
        queue.add(playerUUID);
        playerWorldMap.put(playerUUID, worldName);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Player " + player.getName() + " joined queue for world: " +
                    worldName + " (Queue size: " + queue.size() + ")");
        }

        return true;
    }

    /**
     * Force a player into a specific world queue (admin command).
     *
     * @param player The player to add
     * @param worldName The world name
     * @return true if added successfully
     */
    public boolean forceAddToQueue(Player player, String worldName) {
        UUID playerUUID = player.getUniqueId();

        // Always remove from any existing queue first
        removeFromQueue(player);

        // Get or create queue for this world
        List<UUID> queue = worldQueues.computeIfAbsent(worldName, k -> new ArrayList<>());

        // Add player to queue
        queue.add(playerUUID);
        playerWorldMap.put(playerUUID, worldName);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Player " + player.getName() + " was force-added to queue for world: " +
                    worldName + " (Queue size: " + queue.size() + ")");
        }

        return true;
    }

    /**
     * Remove a player from any queue.
     *
     * @param player The player to remove
     * @return true if removed, false if not in any queue
     */
    public boolean removeFromQueue(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Get the world this player is queued for
        String worldName = playerWorldMap.remove(playerUUID);
        if (worldName == null) {
            return false; // Player not in any queue
        }

        // Remove from that world's queue
        List<UUID> queue = worldQueues.get(worldName);
        if (queue != null) {
            queue.remove(playerUUID);

            // If queue is empty, remove it from the map
            if (queue.isEmpty()) {
                worldQueues.remove(worldName);
            }

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Player " + player.getName() + " left queue for world: " + worldName);
            }
        }

        return true;
    }

    /**
     * Check if a player is in any queue.
     *
     * @param player The player to check
     * @return true if in a queue
     */
    public boolean isInQueue(Player player) {
        return playerWorldMap.containsKey(player.getUniqueId());
    }

    /**
     * Get the world name a player is queued for.
     *
     * @param player The player to check
     * @return The world name or null if not in queue
     */
    public String getPlayerQueueWorld(Player player) {
        return playerWorldMap.get(player.getUniqueId());
    }

    /**
     * Get players that are ready to be teleported for a specific world.
     *
     * @param worldName The world name
     * @return List of players ready to teleport
     */
    public List<Player> getPlayersForTeleport(String worldName) {
        List<UUID> queue = worldQueues.get(worldName);
        if (queue == null) {
            return Collections.emptyList();
        }

        int requiredPlayers = plugin.getConfig().getInt("queue.required-players", 2);

        // Check if we have enough players
        if (queue.size() < requiredPlayers) {
            return Collections.emptyList();
        }

        // Get the first N players from the queue
        List<UUID> playersToTeleport = new ArrayList<>(queue.subList(0, requiredPlayers));
        List<Player> onlinePlayers = new ArrayList<>();

        // Remove these players from queue
        for (UUID uuid : playersToTeleport) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                onlinePlayers.add(player);
            }
        }

        // If we don't have enough online players, return empty list
        if (onlinePlayers.size() < requiredPlayers) {
            return Collections.emptyList();
        }

        // Remove these players from the queue
        queue.removeAll(playersToTeleport);

        // If queue is now empty, remove it
        if (queue.isEmpty()) {
            worldQueues.remove(worldName);
        }

        return onlinePlayers;
    }

    /**
     * Clear all queues.
     */
    public void clearAllQueues() {
        worldQueues.clear();
        playerWorldMap.clear();
    }

    /**
     * Clear queues for a specific world.
     *
     * @param worldName The world name
     */
    public void clearWorldQueue(String worldName) {
        List<UUID> queue = worldQueues.remove(worldName);
        if (queue != null) {
            // Remove all players from the player-world map
            for (UUID uuid : queue) {
                playerWorldMap.remove(uuid);
            }
        }
    }

    /**
     * Get the size of a specific world queue.
     *
     * @param worldName The world name
     * @return Queue size
     */
    public int getQueueSize(String worldName) {
        List<UUID> queue = worldQueues.get(worldName);
        return queue != null ? queue.size() : 0;
    }

    /**
     * Get a map of all world queues and their sizes.
     *
     * @return Map of world names to queue sizes
     */
    public Map<String, Integer> getAllQueueSizes() {
        Map<String, Integer> sizes = new HashMap<>();
        for (Map.Entry<String, List<UUID>> entry : worldQueues.entrySet()) {
            sizes.put(entry.getKey(), entry.getValue().size());
        }
        return sizes;
    }
}