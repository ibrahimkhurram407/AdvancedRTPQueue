package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the player queue for random teleportation.
 */
public class QueueHandler {
    // Map of world names to their respective queues
    private final Map<String, List<Player>> worldQueues = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerWorldMap = new ConcurrentHashMap<>();

    /**
     * Add a player to the queue for a specific world.
     *
     * @param player    The player to add
     * @param worldName The world they want to teleport in (null for default world)
     */
    public void addToQueue(Player player, String worldName) {
        // If no world is specified, use the default world
        if (worldName == null) {
            WorldManager worldManager = AdvancedRTPQueue.getInstance().getWorldManager();
            worldName = worldManager.getDefaultWorldSettings().getName();
        }

        // Get or create the queue for this world
        List<Player> worldQueue = worldQueues.computeIfAbsent(worldName, k -> new ArrayList<>());

        // Add player to the appropriate world queue
        worldQueue.add(player);

        // Track which world this player is queued for
        playerWorldMap.put(player.getUniqueId(), worldName);

        AdvancedRTPQueue.getInstance().getLogger().info(
                "Player " + player.getName() + " joined queue for world: " + worldName +
                        " (Queue size: " + worldQueue.size() + ")"
        );
    }

    /**
     * Remove a player from any queue they're in.
     *
     * @param player The player to remove
     */
    public void removeFromQueue(Player player) {
        UUID playerId = player.getUniqueId();

        // Check which world queue the player is in
        String worldName = playerWorldMap.get(playerId);
        if (worldName != null) {
            List<Player> worldQueue = worldQueues.get(worldName);
            if (worldQueue != null) {
                worldQueue.remove(player);

                // If queue is empty, remove it
                if (worldQueue.isEmpty()) {
                    worldQueues.remove(worldName);
                }

                AdvancedRTPQueue.getInstance().getLogger().info(
                        "Player " + player.getName() + " left queue for world: " + worldName
                );
            }

            // Remove player from tracking
            playerWorldMap.remove(playerId);
        }
    }

    /**
     * Check if a player is in any queue.
     *
     * @param player The player to check
     * @return True if the player is in a queue
     */
    public boolean isInQueue(Player player) {
        return playerWorldMap.containsKey(player.getUniqueId());
    }

    /**
     * Get all players in a specific world's queue.
     *
     * @param worldName The name of the world
     * @return List of players in that world's queue
     */
    public List<Player> getQueueForWorld(String worldName) {
        return worldQueues.getOrDefault(worldName, new ArrayList<>());
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
     * Get players that can be teleported from a specific world's queue.
     *
     * @param worldName The world name to check
     * @return List of players ready for teleport
     */
    public List<Player> getPlayersForTeleport(String worldName) {
        List<Player> queue = worldQueues.getOrDefault(worldName, new ArrayList<>());
        int requiredPlayers = AdvancedRTPQueue.getInstance().getConfig().getInt("queue.required-players", 2);

        // If queue has enough players, return them
        if (queue.size() >= requiredPlayers) {
            return new ArrayList<>(queue.subList(0, requiredPlayers));
        }

        return new ArrayList<>();
    }

    /**
     * Clear all queues or a specific world's queue.
     *
     * @param worldName Optional world name (null to clear all queues)
     */
    public void clearQueue(String worldName) {
        if (worldName == null) {
            // Clear all queues
            worldQueues.clear();
            playerWorldMap.clear();
            AdvancedRTPQueue.getInstance().getLogger().info("All queues cleared");
        } else {
            // Clear only the specified world queue
            List<Player> worldQueue = worldQueues.remove(worldName);
            if (worldQueue != null) {
                // Remove all players in this world queue from tracking
                for (Player player : worldQueue) {
                    playerWorldMap.remove(player.getUniqueId());
                }
                AdvancedRTPQueue.getInstance().getLogger().info("Queue cleared for world: " + worldName);
            }
        }
    }

    /**
     * Clear all queues.
     */
    public void clearQueue() {
        clearQueue(null);
    }

    /**
     * Get the total number of players across all queues.
     *
     * @return Total queued player count
     */
    public int getTotalQueueSize() {
        int total = 0;
        for (List<Player> queue : worldQueues.values()) {
            total += queue.size();
        }
        return total;
    }

    /**
     * Get a map of world names to their queue sizes.
     *
     * @return Map of world names to queue sizes
     */
    public Map<String, Integer> getWorldQueueSizes() {
        Map<String, Integer> sizes = new HashMap<>();
        for (Map.Entry<String, List<Player>> entry : worldQueues.entrySet()) {
            sizes.put(entry.getKey(), entry.getValue().size());
        }
        return sizes;
    }
}