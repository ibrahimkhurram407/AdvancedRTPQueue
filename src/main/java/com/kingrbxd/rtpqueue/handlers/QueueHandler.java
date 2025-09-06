package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * QueueHandler - manages per-world queues and player -> queue mapping.
 *
 * Added utility methods requested by other parts of the plugin:
 * - removeOfflinePlayers()
 * - getQueueInformation()
 * - getQueueInformation(String world)
 * - getPlayersInWorldQueue(String world)
 * - getOnlinePlayersInWorldQueue(String world)
 * - clearWorldQueue(String world)
 */
public class QueueHandler {
    private final AdvancedRTPQueue plugin;
    private final Map<String, Set<UUID>> worldQueues = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerWorldMap = new ConcurrentHashMap<>();

    public QueueHandler(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    /**
     * Add player to queue for the given world key.
     * Removes player from any existing queue first.
     */
    public boolean addToQueue(Player player, String worldName) {
        if (player == null || worldName == null) return false;

        // Ensure player is removed from any other queue
        removeFromQueue(player);

        Set<UUID> queue = worldQueues.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet());
        queue.add(player.getUniqueId());
        playerWorldMap.put(player.getUniqueId(), worldName);

        int currentPlayers = queue.size();
        int requiredPlayers = plugin.getConfigManager().getInt("queue.required-players", 2);
        int playersNeeded = Math.max(0, requiredPlayers - currentPlayers);

        Map<String, String> placeholders = Map.of(
                "world", plugin.getWorldManager().getDisplayName(worldName),
                "current", String.valueOf(currentPlayers),
                "required", String.valueOf(requiredPlayers),
                "needed", String.valueOf(playersNeeded)
        );

        MessageUtil.sendMessage(player, "join-queue", placeholders);
        MessageUtil.playSound(player, "queue-join");
        MessageUtil.sendTitle(player, "queue-joined", "queue-joined", placeholders);

        if (plugin.getConfigManager().getBoolean("advanced.log-queue-actions")) {
            plugin.getLogger().info(player.getName() + " joined " + worldName + " queue (" + currentPlayers + "/" + requiredPlayers + ")");
        }

        if (currentPlayers >= requiredPlayers) {
            startTeleportation(worldName);
        }

        return true;
    }

    /**
     * Remove player from whatever queue they are in.
     * Returns true if player was removed.
     */
    public boolean removeFromQueue(Player player) {
        if (player == null) return false;

        String worldName = playerWorldMap.remove(player.getUniqueId());
        if (worldName == null) return false;

        Set<UUID> queue = worldQueues.get(worldName);
        if (queue != null) {
            queue.remove(player.getUniqueId());
            if (queue.isEmpty()) {
                worldQueues.remove(worldName);
            }
        }

        // Send leave message only if player is online (some callers might call this for disconnects;
        // ensure callers expect the message). We send the message here as this method has historically done.
        MessageUtil.sendMessage(player, "leave-queue");
        MessageUtil.playSound(player, "queue-leave");

        if (plugin.getConfigManager().getBoolean("advanced.log-queue-actions")) {
            plugin.getLogger().info(player.getName() + " left " + worldName + " queue");
        }

        return true;
    }

    /**
     * Remove player from queues/state when they disconnect.
     * This is a silent cleanup: no messages or sounds are sent.
     * It also cancels any active teleport session for the player.
     *
     * Use this from PlayerQuitEvent / disconnect handlers.
     */
    public void handlePlayerDisconnect(Player player) {
        if (player == null) return;
        handlePlayerDisconnect(player.getUniqueId());
    }

    /**
     * Same as handlePlayerDisconnect(Player) but accepts UUID (useful from async contexts).
     */
    public void handlePlayerDisconnect(UUID playerUuid) {
        if (playerUuid == null) return;

        // If player had an active teleport session, cancel it (best-effort when player is online)
        Player online = Bukkit.getPlayer(playerUuid);
        if (online != null) {
            if (plugin.getTeleportManager().hasActiveSession(online)) {
                plugin.getTeleportManager().cancelPlayerSession(online, "disconnect");
            }
        }

        // Silent removal from any queue
        String worldName = playerWorldMap.remove(playerUuid);
        if (worldName != null) {
            Set<UUID> queue = worldQueues.get(worldName);
            if (queue != null) {
                queue.remove(playerUuid);
                if (queue.isEmpty()) {
                    worldQueues.remove(worldName);
                }
            }

            if (plugin.getConfigManager().getBoolean("advanced.log-queue-actions")) {
                plugin.getLogger().info("Cleaned up disconnected player " + playerUuid + " from queue " + worldName);
            }
        }
    }

    /**
     * Start teleportation for required number of players from the queue.
     * Picks the first online players in the queue up to requiredPlayers.
     */
    private void startTeleportation(String worldName) {
        Set<UUID> queue = worldQueues.get(worldName);
        if (queue == null || queue.isEmpty()) return;

        int requiredPlayers = plugin.getConfigManager().getInt("queue.required-players", 2);

        List<Player> validPlayers = new ArrayList<>();
        Iterator<UUID> iterator = queue.iterator();

        while (iterator.hasNext() && validPlayers.size() < requiredPlayers) {
            UUID playerId = iterator.next();
            Player player = plugin.getServer().getPlayer(playerId);

            if (player != null && player.isOnline()) {
                validPlayers.add(player);
            } else {
                iterator.remove();
                playerWorldMap.remove(playerId);
            }
        }

        if (validPlayers.size() >= requiredPlayers) {
            List<Player> selectedPlayers = validPlayers.subList(0, requiredPlayers);

            for (Player player : selectedPlayers) {
                queue.remove(player.getUniqueId());
                playerWorldMap.remove(player.getUniqueId());
            }

            if (queue.isEmpty()) {
                worldQueues.remove(worldName);
            }

            plugin.getTeleportManager().startTeleportation(selectedPlayers, worldName);
        }
    }

    /**
     * Update action bars for all queued players.
     */
    public void updateActionBars() {
        if (!plugin.getConfigManager().getBoolean("ui.action-bar.enabled")) return;

        for (Map.Entry<String, Set<UUID>> entry : worldQueues.entrySet()) {
            String worldName = entry.getKey();
            Set<UUID> queue = entry.getValue();

            int currentPlayers = queue.size();
            int requiredPlayers = plugin.getConfigManager().getInt("queue.required-players", 2);
            int playersNeeded = Math.max(0, requiredPlayers - currentPlayers);

            Map<String, String> placeholders = Map.of(
                    "world", plugin.getWorldManager().getDisplayName(worldName),
                    "current", String.valueOf(currentPlayers),
                    "required", String.valueOf(requiredPlayers),
                    "needed", String.valueOf(playersNeeded)
            );

            for (UUID playerId : queue) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    MessageUtil.sendActionBar(player, "queue-wait", placeholders);
                }
            }
        }
    }

    // ---- New utility methods requested by other code ----

    /**
     * Remove offline players from all queues.
     * Useful to keep queues clean when run periodically or on server events.
     */
    public int removeOfflinePlayers() {
        int removed = 0;
        Set<UUID> toRemove = new HashSet<>();

        // Collect offline players from playerWorldMap
        for (UUID uuid : playerWorldMap.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                toRemove.add(uuid);
            }
        }

        // Remove them silently
        for (UUID uuid : toRemove) {
            String world = playerWorldMap.remove(uuid);
            if (world != null) {
                Set<UUID> queue = worldQueues.get(world);
                if (queue != null) {
                    queue.remove(uuid);
                    if (queue.isEmpty()) {
                        worldQueues.remove(world);
                    }
                }
                removed++;
                if (plugin.getConfigManager().getBoolean("advanced.log-queue-actions")) {
                    plugin.getLogger().info("Removed offline player " + uuid + " from queue " + world);
                }
            }
        }

        return removed;
    }

    /**
     * Returns aggregate queue information for all active queues.
     * Structure: Map<worldKey, Map<String, Object>> where inner map contains:
     * - "size" -> Integer
     * - "players" -> List<UUID>
     * - "onlinePlayers" -> List<Player>
     */
    public Map<String, Map<String, Object>> getQueueInformation() {
        Map<String, Map<String, Object>> info = new HashMap<>();
        for (String world : worldQueues.keySet()) {
            info.put(world, getQueueInformation(world));
        }
        return Collections.unmodifiableMap(info);
    }

    /**
     * Returns queue information for a single world (or empty map if none).
     */
    public Map<String, Object> getQueueInformation(String worldName) {
        Map<String, Object> info = new HashMap<>();
        Set<UUID> queue = worldQueues.get(worldName);
        if (queue == null) {
            info.put("size", 0);
            info.put("players", Collections.emptyList());
            info.put("onlinePlayers", Collections.emptyList());
            return Collections.unmodifiableMap(info);
        }

        List<UUID> players = new ArrayList<>(queue);
        List<Player> onlinePlayers = players.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .collect(Collectors.toList());

        info.put("size", players.size());
        info.put("players", Collections.unmodifiableList(players));
        info.put("onlinePlayers", Collections.unmodifiableList(onlinePlayers));
        return Collections.unmodifiableMap(info);
    }

    /**
     * Returns a copy of the UUIDs in the queue for the given world key.
     */
    public List<UUID> getPlayersInWorldQueue(String worldName) {
        Set<UUID> queue = worldQueues.get(worldName);
        if (queue == null) return Collections.emptyList();
        return new ArrayList<>(queue);
    }

    /**
     * Returns online Player objects currently in the given world's queue.
     */
    public List<Player> getOnlinePlayersInWorldQueue(String worldName) {
        return getPlayersInWorldQueue(worldName).stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .collect(Collectors.toList());
    }

    /**
     * Clear a specific world's queue. Notifies online players that they left the queue
     * and removes their mappings.
     */
    public void clearWorldQueue(String worldName) {
        Set<UUID> queue = worldQueues.remove(worldName);
        if (queue == null || queue.isEmpty()) return;

        for (UUID uuid : new HashSet<>(queue)) {
            playerWorldMap.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                MessageUtil.sendMessage(player, "leave-queue");
                MessageUtil.playSound(player, "queue-leave");
            }
        }

        if (plugin.getConfigManager().getBoolean("advanced.log-queue-actions")) {
            plugin.getLogger().info("Cleared queue for world " + worldName);
        }
    }

    // ---- Existing getters / utilities ----

    public boolean isInQueue(Player player) {
        return player != null && playerWorldMap.containsKey(player.getUniqueId());
    }

    public String getPlayerQueueWorld(Player player) {
        return player != null ? playerWorldMap.get(player.getUniqueId()) : null;
    }

    public int getQueueSize(String worldName) {
        Set<UUID> queue = worldQueues.get(worldName);
        return queue != null ? queue.size() : 0;
    }

    public int getTotalQueuedPlayers() {
        return playerWorldMap.size();
    }

    /**
     * Returns a map of worldKey -> queue size for all currently tracked queues.
     * Use this when you need to show sizes of every active queue.
     */
    public Map<String, Integer> getAllQueueSizes() {
        Map<String, Integer> sizes = new HashMap<>();
        for (Map.Entry<String, Set<UUID>> e : worldQueues.entrySet()) {
            sizes.put(e.getKey(), e.getValue() != null ? e.getValue().size() : 0);
        }
        return Collections.unmodifiableMap(sizes);
    }

    /**
     * Returns a copy of active world keys that have queues.
     */
    public Set<String> getActiveWorlds() {
        return new HashSet<>(worldQueues.keySet());
    }

    public void clearAllQueues() {
        // Notify online players and clear mappings
        for (String world : new HashSet<>(worldQueues.keySet())) {
            clearWorldQueue(world);
        }
        // ensure maps are empty
        worldQueues.clear();
        playerWorldMap.clear();
    }
}