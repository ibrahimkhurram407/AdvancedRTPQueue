package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete queue handler - streamlined without max-queue-size limit
 * Author: KingRBxD | Updated: 2025-09-06
 */
public class QueueHandler {
    private final AdvancedRTPQueue plugin;
    private final Map<String, List<UUID>> worldQueues = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerQueueWorld = new ConcurrentHashMap<>();

    public QueueHandler(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        initializeQueues();
    }

    /**
     * Initialize queues for all configured worlds
     */
    private void initializeQueues() {
        // Initialize default world queue
        String defaultWorld = plugin.getConfigManager().getString("teleport.default-world", "world");
        worldQueues.put(defaultWorld, Collections.synchronizedList(new ArrayList<>()));

        // Initialize other world queues if enabled
        if (plugin.getConfigManager().getBoolean("teleport.other-worlds.enabled")) {
            Set<String> configuredWorlds = plugin.getConfig().getConfigurationSection("teleport.other-worlds.worlds").getKeys(false);
            for (String worldKey : configuredWorlds) {
                String worldName = plugin.getConfig().getString("teleport.other-worlds.worlds." + worldKey + ".name");
                if (worldName != null && !worldName.isEmpty()) {
                    worldQueues.put(worldName, Collections.synchronizedList(new ArrayList<>()));
                }
            }
        }

        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Initialized queues for worlds: " + worldQueues.keySet());
        }
    }

    /**
     * Add player to queue
     */
    public boolean addToQueue(Player player, String worldName) {
        if (player == null || worldName == null) {
            return false;
        }

        // Check if player is already in a queue
        if (isInQueue(player)) {
            if (plugin.getConfigManager().getBoolean("queue.allow-world-switching")) {
                removeFromQueue(player);
            } else {
                MessageUtil.sendMessage(player, "already-in-queue", createPlaceholders(player));
                return false;
            }
        }

        // Check world validity
        if (!isValidWorld(worldName)) {
            MessageUtil.sendMessage(player, "invalid-world", Map.of("world", worldName));
            return false;
        }

        // Check permissions
        if (!hasWorldPermission(player, worldName)) {
            MessageUtil.sendMessage(player, "no-permission-world", Map.of("world", worldName));
            return false;
        }

        // Add to queue (no size limit check)
        List<UUID> queue = worldQueues.get(worldName);
        queue.add(player.getUniqueId());
        playerQueueWorld.put(player.getUniqueId(), worldName);

        // Send join message
        Map<String, String> placeholders = createQueuePlaceholders(worldName, queue.size());
        placeholders.put("world", plugin.getWorldManager().getDisplayName(worldName));

        MessageUtil.sendMessage(player, "join-queue", placeholders);
        MessageUtil.sendTitle(player, "queue-joined", "queue-joined", placeholders);
        MessageUtil.playSound(player, "queue-join");

        // Log if enabled
        if (plugin.getConfigManager().getBoolean("advanced.log-queue-actions")) {
            plugin.getLogger().info(player.getName() + " joined " + worldName + " queue (" + queue.size() + " players)");
        }

        // Check if queue is ready for teleportation
        checkQueueForTeleportation(worldName);

        return true;
    }

    /**
     * Remove player from queue
     */
    public boolean removeFromQueue(Player player) {
        if (player == null || !isInQueue(player)) {
            return false;
        }

        String worldName = playerQueueWorld.remove(player.getUniqueId());
        if (worldName != null) {
            List<UUID> queue = worldQueues.get(worldName);
            if (queue != null) {
                queue.remove(player.getUniqueId());

                MessageUtil.sendMessage(player, "leave-queue");
                MessageUtil.playSound(player, "queue-leave");

                if (plugin.getConfigManager().getBoolean("advanced.log-queue-actions")) {
                    plugin.getLogger().info(player.getName() + " left " + worldName + " queue (" + queue.size() + " players)");
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Check if player is in any queue
     */
    public boolean isInQueue(Player player) {
        return player != null && playerQueueWorld.containsKey(player.getUniqueId());
    }

    /**
     * Get the world name of the queue the player is in
     */
    public String getPlayerQueueWorld(Player player) {
        return player != null ? playerQueueWorld.get(player.getUniqueId()) : null;
    }

    /**
     * Get queue size for a world
     */
    public int getQueueSize(String worldName) {
        List<UUID> queue = worldQueues.get(worldName);
        return queue != null ? queue.size() : 0;
    }

    /**
     * Get all queue sizes
     */
    public Map<String, Integer> getAllQueueSizes() {
        Map<String, Integer> sizes = new HashMap<>();
        for (Map.Entry<String, List<UUID>> entry : worldQueues.entrySet()) {
            sizes.put(entry.getKey(), entry.getValue().size());
        }
        return sizes;
    }

    /**
     * Get players in a specific world queue
     */
    public Set<UUID> getPlayersInWorldQueue(String worldName) {
        List<UUID> queue = worldQueues.get(worldName);
        return queue != null ? new HashSet<>(queue) : new HashSet<>();
    }

    /**
     * Get player's position in queue
     */
    public int getPlayerPositionInQueue(Player player) {
        if (!isInQueue(player)) {
            return -1;
        }

        String worldName = getPlayerQueueWorld(player);
        List<UUID> queue = worldQueues.get(worldName);

        if (queue != null) {
            return queue.indexOf(player.getUniqueId()) + 1; // 1-based position
        }

        return -1;
    }

    /**
     * Clear queue for a specific world
     */
    public void clearWorldQueue(String worldName) {
        List<UUID> queue = worldQueues.get(worldName);
        if (queue != null) {
            // Remove players from tracking
            for (UUID playerUUID : new ArrayList<>(queue)) {
                playerQueueWorld.remove(playerUUID);
            }
            queue.clear();

            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().info("Cleared queue for world: " + worldName);
            }
        }
    }

    /**
     * Clear all queues
     */
    public void clearAllQueues() {
        for (String worldName : worldQueues.keySet()) {
            clearWorldQueue(worldName);
        }

        plugin.getLogger().info("All queues cleared");
    }

    /**
     * Force player into queue (admin command)
     */
    public boolean forcePlayerToQueue(Player target, String worldName) {
        if (target == null || worldName == null) {
            return false;
        }

        // Remove from current queue if in one
        if (isInQueue(target)) {
            removeFromQueue(target);
        }

        // Check world validity
        if (!isValidWorld(worldName)) {
            return false;
        }

        // Add to queue directly (bypassing all checks)
        List<UUID> queue = worldQueues.get(worldName);
        queue.add(target.getUniqueId());
        playerQueueWorld.put(target.getUniqueId(), worldName);

        // Notify player
        Map<String, String> placeholders = createQueuePlaceholders(worldName, queue.size());
        placeholders.put("world", plugin.getWorldManager().getDisplayName(worldName));

        MessageUtil.sendMessage(target, "join-queue", placeholders);
        MessageUtil.playSound(target, "queue-join");

        // Check if queue is ready
        checkQueueForTeleportation(worldName);

        return true;
    }

    /**
     * Check if queue is ready for teleportation
     */
    private void checkQueueForTeleportation(String worldName) {
        List<UUID> queue = worldQueues.get(worldName);
        if (queue == null) {
            return;
        }

        int requiredPlayers = plugin.getConfigManager().getInt("queue.required-players", 2);

        if (queue.size() >= requiredPlayers) {
            // Get required number of players
            List<Player> playersToTeleport = new ArrayList<>();

            for (int i = 0; i < requiredPlayers && i < queue.size(); i++) {
                UUID playerUUID = queue.get(i);
                Player player = Bukkit.getPlayer(playerUUID);

                if (player != null && player.isOnline()) {
                    playersToTeleport.add(player);
                } else {
                    // Remove offline player from queue
                    queue.remove(playerUUID);
                    playerQueueWorld.remove(playerUUID);
                    i--; // Adjust index
                }
            }

            // Check if we still have enough players
            if (playersToTeleport.size() >= requiredPlayers) {
                // Remove players from queue
                for (Player player : playersToTeleport) {
                    queue.remove(player.getUniqueId());
                    playerQueueWorld.remove(player.getUniqueId());
                }

                // Start teleportation process
                plugin.getTeleportManager().startTeleportation(playersToTeleport, worldName);

                // Check again for remaining players (recursive processing)
                if (queue.size() >= requiredPlayers) {
                    checkQueueForTeleportation(worldName);
                }
            }
        }
    }

    /**
     * Update action bars for all queued players
     */
    public void updateActionBars() {
        for (Map.Entry<UUID, String> entry : playerQueueWorld.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                String worldName = entry.getValue();
                int current = getQueueSize(worldName);
                int required = plugin.getConfigManager().getInt("queue.required-players", 2);
                int position = getPlayerPositionInQueue(player);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("current", String.valueOf(current));
                placeholders.put("required", String.valueOf(required));
                placeholders.put("position", String.valueOf(position));
                placeholders.put("world", plugin.getWorldManager().getDisplayName(worldName));
                placeholders.put("needed", String.valueOf(Math.max(0, required - current)));

                MessageUtil.sendActionBar(player, "queue-wait", placeholders);
            }
        }
    }

    /**
     * Get queue statistics for a player
     */
    public void sendQueueStats(Player player) {
        MessageUtil.sendMessage(player, "stats-header");

        // Total players across all queues
        int totalPlayers = 0;
        for (List<UUID> queue : worldQueues.values()) {
            totalPlayers += queue.size();
        }

        Map<String, String> totalPlaceholders = Map.of("total", String.valueOf(totalPlayers));
        MessageUtil.sendMessage(player, "stats-total-players", totalPlaceholders);

        // Player's current queue
        if (isInQueue(player)) {
            String worldName = getPlayerQueueWorld(player);
            int current = getQueueSize(worldName);
            int required = plugin.getConfigManager().getInt("queue.required-players", 2);
            int position = getPlayerPositionInQueue(player);

            Map<String, String> playerPlaceholders = new HashMap<>();
            playerPlaceholders.put("world", plugin.getWorldManager().getDisplayName(worldName));
            playerPlaceholders.put("current", String.valueOf(current));
            playerPlaceholders.put("required", String.valueOf(required));
            playerPlaceholders.put("position", String.valueOf(position));

            MessageUtil.sendMessage(player, "stats-your-world", playerPlaceholders);
        }

        // All world queues
        for (Map.Entry<String, List<UUID>> entry : worldQueues.entrySet()) {
            String worldName = entry.getKey();
            int current = entry.getValue().size();
            int required = plugin.getConfigManager().getInt("queue.required-players", 2);

            Map<String, String> worldPlaceholders = new HashMap<>();
            worldPlaceholders.put("world", plugin.getWorldManager().getDisplayName(worldName));
            worldPlaceholders.put("current", String.valueOf(current));
            worldPlaceholders.put("required", String.valueOf(required));

            MessageUtil.sendMessage(player, "stats-world-line", worldPlaceholders);
        }

        MessageUtil.sendMessage(player, "stats-footer");
    }

    /**
     * Get detailed queue information for admin purposes
     */
    public Map<String, Object> getQueueInformation() {
        Map<String, Object> info = new HashMap<>();

        // Overall statistics
        int totalPlayers = 0;
        Map<String, Integer> worldSizes = new HashMap<>();

        for (Map.Entry<String, List<UUID>> entry : worldQueues.entrySet()) {
            String worldName = entry.getKey();
            int size = entry.getValue().size();

            worldSizes.put(worldName, size);
            totalPlayers += size;
        }

        info.put("totalQueuedPlayers", totalPlayers);
        info.put("worldQueueSizes", worldSizes);
        info.put("activeWorlds", worldQueues.keySet());
        info.put("requiredPlayersPerGroup", plugin.getConfigManager().getInt("queue.required-players", 2));

        return info;
    }

    /**
     * Remove offline players from all queues (maintenance)
     */
    public int removeOfflinePlayers() {
        int removedCount = 0;

        for (Map.Entry<String, List<UUID>> entry : worldQueues.entrySet()) {
            List<UUID> queue = entry.getValue();
            Iterator<UUID> iterator = queue.iterator();

            while (iterator.hasNext()) {
                UUID playerUUID = iterator.next();
                Player player = Bukkit.getPlayer(playerUUID);

                if (player == null || !player.isOnline()) {
                    iterator.remove();
                    playerQueueWorld.remove(playerUUID);
                    removedCount++;
                }
            }
        }

        if (removedCount > 0 && plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Removed " + removedCount + " offline players from queues");
        }

        return removedCount;
    }

    /**
     * Check if world is valid and loaded
     */
    private boolean isValidWorld(String worldName) {
        return plugin.getWorldManager().isValidWorld(worldName);
    }

    /**
     * Check if player has permission for world
     */
    private boolean hasWorldPermission(Player player, String worldName) {
        if (player.hasPermission("rtpqueue.world.*")) {
            return true;
        }

        // Check for specific world permission
        String permission = "rtpqueue.world." + worldName.toLowerCase();
        if (player.hasPermission(permission)) {
            return true;
        }

        // Check configured world permission
        if (plugin.getConfigManager().getBoolean("teleport.other-worlds.enabled")) {
            Set<String> configuredWorlds = plugin.getConfig().getConfigurationSection("teleport.other-worlds.worlds").getKeys(false);
            for (String worldKey : configuredWorlds) {
                String configWorldName = plugin.getConfig().getString("teleport.other-worlds.worlds." + worldKey + ".name");
                if (worldName.equals(configWorldName)) {
                    String configPermission = plugin.getConfig().getString("teleport.other-worlds.worlds." + worldKey + ".permission");
                    return configPermission == null || player.hasPermission(configPermission);
                }
            }
        }

        // Default world is always accessible with base permission
        String defaultWorld = plugin.getConfigManager().getString("teleport.default-world", "world");
        return worldName.equals(defaultWorld);
    }

    /**
     * Create placeholders for player
     */
    private Map<String, String> createPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();
        String worldName = getPlayerQueueWorld(player);

        if (worldName != null) {
            placeholders.put("world", plugin.getWorldManager().getDisplayName(worldName));
            placeholders.put("current", String.valueOf(getQueueSize(worldName)));
            placeholders.put("required", String.valueOf(plugin.getConfigManager().getInt("queue.required-players", 2)));
            placeholders.put("position", String.valueOf(getPlayerPositionInQueue(player)));
        }

        return placeholders;
    }

    /**
     * Create queue placeholders
     */
    private Map<String, String> createQueuePlaceholders(String worldName, int currentSize) {
        Map<String, String> placeholders = new HashMap<>();
        int required = plugin.getConfigManager().getInt("queue.required-players", 2);

        placeholders.put("world", plugin.getWorldManager().getDisplayName(worldName));
        placeholders.put("current", String.valueOf(currentSize));
        placeholders.put("required", String.valueOf(required));
        placeholders.put("needed", String.valueOf(Math.max(0, required - currentSize)));

        return placeholders;
    }

    /**
     * Handle player disconnect
     */
    public void handlePlayerDisconnect(Player player) {
        if (isInQueue(player)) {
            String worldName = getPlayerQueueWorld(player);
            removeFromQueue(player);

            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().info("Removed " + player.getName() + " from " + worldName + " queue (disconnect)");
            }
        }
    }
}