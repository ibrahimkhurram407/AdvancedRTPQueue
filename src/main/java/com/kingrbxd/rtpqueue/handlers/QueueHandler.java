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

    public boolean addToQueue(Player player, String worldName) {
        if (player == null || worldName == null) return false;
        UUID playerUUID = player.getUniqueId();

        // Check if player is already in a queue
        String currentWorld = playerWorldMap.get(playerUUID);

        // If allow-world-switching is enabled and player is in a different world queue
        if (currentWorld != null && !currentWorld.equalsIgnoreCase(worldName)) {
            if (plugin.getConfig().getBoolean("queue.allow-world-switching", false)) {
                // Remove from current world queue
                removeFromQueue(player);
            } else {
                MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.already-in-queue", "You are already in a queue."));
                return false;
            }
        } else if (currentWorld != null) {
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.already-in-queue", "You are already in a queue."));
            return false;
        }

        // Get or create queue for this world
        List<UUID> queue = worldQueues.computeIfAbsent(worldName, k -> Collections.synchronizedList(new ArrayList<>()));

        // Add player to queue
        queue.add(playerUUID);
        playerWorldMap.put(playerUUID, worldName);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Player " + player.getName() + " joined queue for world: " +
                    worldName + " (Queue size: " + queue.size() + ")");
        }

        return true;
    }

    public boolean forceAddToQueue(Player player, String worldName) {
        if (player == null || worldName == null) return false;
        // Always remove from any existing queue first
        removeFromQueue(player);

        List<UUID> queue = worldQueues.computeIfAbsent(worldName, k -> Collections.synchronizedList(new ArrayList<>()));

        queue.add(player.getUniqueId());
        playerWorldMap.put(player.getUniqueId(), worldName);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Player " + player.getName() + " was force-added to queue for world: " +
                    worldName + " (Queue size: " + queue.size() + ")");
        }

        return true;
    }

    public boolean removeFromQueue(Player player) {
        if (player == null) return false;
        UUID playerUUID = player.getUniqueId();

        String worldName = playerWorldMap.remove(playerUUID);
        if (worldName == null) {
            return false; // Player not in any queue
        }

        List<UUID> queue = worldQueues.get(worldName);
        if (queue != null) {
            queue.remove(playerUUID);
            if (queue.isEmpty()) {
                worldQueues.remove(worldName);
            }

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Player " + player.getName() + " left queue for world: " + worldName);
            }
        }

        return true;
    }

    public boolean isInQueue(Player player) {
        if (player == null) return false;
        return playerWorldMap.containsKey(player.getUniqueId());
    }

    public String getPlayerQueueWorld(Player player) {
        if (player == null) return null;
        return playerWorldMap.get(player.getUniqueId());
    }

    public List<Player> getPlayersForTeleport(String worldName) {
        List<UUID> queue = worldQueues.get(worldName);
        if (queue == null) {
            return Collections.emptyList();
        }

        int requiredPlayers = plugin.getConfig().getInt("queue.required-players", 2);

        if (queue.size() < requiredPlayers) {
            return Collections.emptyList();
        }

        // Get the first N players from the queue
        List<UUID> playersToTeleportUUIDs = new ArrayList<>(queue.subList(0, Math.min(requiredPlayers, queue.size())));
        List<Player> onlinePlayers = new ArrayList<>();

        for (UUID uuid : playersToTeleportUUIDs) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                onlinePlayers.add(player);
            }
        }

        if (onlinePlayers.size() < requiredPlayers) {
            return Collections.emptyList();
        }

        // Remove these players from the queue map & playerWorldMap
        queue.removeAll(playersToTeleportUUIDs);
        for (UUID u : playersToTeleportUUIDs) {
            playerWorldMap.remove(u);
        }

        if (queue.isEmpty()) {
            worldQueues.remove(worldName);
        }

        return onlinePlayers;
    }

    public void clearAllQueues() {
        worldQueues.clear();
        playerWorldMap.clear();
    }

    public void clearWorldQueue(String worldName) {
        List<UUID> queue = worldQueues.remove(worldName);
        if (queue != null) {
            for (UUID uuid : queue) {
                playerWorldMap.remove(uuid);
            }
        }
    }

    public int getQueueSize(String worldName) {
        List<UUID> queue = worldQueues.get(worldName);
        return queue != null ? queue.size() : 0;
    }

    public Map<String, Integer> getAllQueueSizes() {
        Map<String, Integer> sizes = new HashMap<>();
        for (Map.Entry<String, List<UUID>> entry : worldQueues.entrySet()) {
            sizes.put(entry.getKey(), entry.getValue().size());
        }
        return sizes;
    }

    public List<UUID> getPlayersInWorldQueue(String worldName) {
        List<UUID> queue = worldQueues.get(worldName);
        return queue != null ? new ArrayList<>(queue) : new ArrayList<>();
    }
}