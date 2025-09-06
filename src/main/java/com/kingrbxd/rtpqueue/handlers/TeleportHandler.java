package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles teleportation of matched players.
 */
public class TeleportHandler {
    private static final Map<UUID, Boolean> teleportPending = new ConcurrentHashMap<>();
    private static final Random random = ThreadLocalRandom.current();

    // Track teleport groups so we can cancel everyone if one person moves
    private static final Map<String, List<UUID>> teleportGroups = new ConcurrentHashMap<>();
    private static final Map<UUID, String> playerToGroupMap = new ConcurrentHashMap<>();

    // Cache for safe locations to avoid repeated calculations
    private static final Map<String, Location> safeLocationCache = new ConcurrentHashMap<>();

    // Keep track of which worlds are currently being searched for locations
    private static final Set<String> activeSearches = Collections.synchronizedSet(new HashSet<>());

    // Task IDs for location search tasks
    private static final Map<String, BukkitTask> searchTasks = new ConcurrentHashMap<>();

    /**
     * Try to teleport players from a queue if there are enough players.
     *
     * @param triggerPlayer The player who triggered this check
     */
    public static void tryTeleport(Player triggerPlayer) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        QueueHandler queueHandler = plugin.getQueueHandler();

        // Get the world this player is queued for
        String worldName = queueHandler.getPlayerQueueWorld(triggerPlayer);
        if (worldName == null) {
            return; // Player not in queue
        }

        // Check if this world's queue has enough players
        List<Player> playersToTeleport = queueHandler.getPlayersForTeleport(worldName);
        if (playersToTeleport.isEmpty()) {
            return; // Not enough players yet
        }

        // Get world settings
        WorldManager.WorldSettings worldSettings = plugin.getWorldManager().getWorldSettings(worldName);

        // Get teleport cooldown time
        int preTeleportTime = plugin.getConfig().getInt("cooldowns.pre-teleport", 5);

        // Create a unique group ID for this teleport group
        String groupId = UUID.randomUUID().toString();
        List<UUID> playerUUIDs = new ArrayList<>();

        // Prepare all players for teleport
        for (Player player : playersToTeleport) {
            UUID playerUUID = player.getUniqueId();
            // Mark player as pending teleport
            teleportPending.put(playerUUID, true);

            // Add to teleport group
            playerUUIDs.add(playerUUID);
            playerToGroupMap.put(playerUUID, groupId);

            // Notify player that match was found
            String message = plugin.getConfig().getString("messages.opponent-found")
                    .replace("{time}", String.valueOf(preTeleportTime))
                    .replace("{players}", String.valueOf(playersToTeleport.size()));
            MessageUtil.sendMessage(player, message);
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.opponent-found"));
        }

        // Register the teleport group
        teleportGroups.put(groupId, playerUUIDs);

        // Create final copies for use in the runnable
        final List<Player> finalPlayersToTeleport = new ArrayList<>(playersToTeleport);
        final WorldManager.WorldSettings finalWorldSettings = worldSettings;

        // Use a cached location if available
        Location cachedLocation = safeLocationCache.remove(worldName);
        final AtomicBoolean locationFound = new AtomicBoolean(cachedLocation != null);
        final AtomicInteger searchTaskId = new AtomicInteger(-1);

        // If no cached location is available, start searching for one
        if (!locationFound.get()) {
            // Start finding a safe location during the countdown
            searchTaskId.set(startLocationSearch(worldSettings, locationFound).getTaskId());
        }

        // Start teleport countdown
        new BukkitRunnable() {
            private final long startTime = System.currentTimeMillis();
            private final long timeoutMs = preTeleportTime * 1000L;

            @Override
            public void run() {
                // Check if all players are still valid for teleport
                List<Player> stillValidPlayers = new ArrayList<>();
                for (Player player : finalPlayersToTeleport) {
                    if (player.isOnline() && teleportPending.containsKey(player.getUniqueId())) {
                        stillValidPlayers.add(player);
                    }
                }

                // Check if we still have enough players
                int requiredPlayers = plugin.getConfig().getInt("queue.required-players", 2);
                if (stillValidPlayers.size() < requiredPlayers) {
                    // Not enough players, cancel teleport for everyone
                    for (Player player : stillValidPlayers) {
                        cancelPlayerTeleport(player, "not-enough-players");
                    }

                    // Stop any ongoing location search
                    if (searchTaskId.get() != -1) {
                        Bukkit.getScheduler().cancelTask(searchTaskId.get());
                        activeSearches.remove(finalWorldSettings.getName());
                    }
                    return;
                }

                // If we still don't have a location, check if we've timed out
                if (!locationFound.get()) {
                    long elapsedMs = System.currentTimeMillis() - startTime;
                    if (elapsedMs >= timeoutMs) {
                        // We've waited long enough, use emergency location or cancel
                        Location emergency = getEmergencyLocation(finalWorldSettings);

                        if (emergency != null) {
                            // Use emergency location (spawn point, etc.)
                            teleportPlayersToLocation(stillValidPlayers, emergency);
                        } else {
                            // No location available, cancel teleport
                            for (Player player : stillValidPlayers) {
                                cancelPlayerTeleport(player, "no-safe-locations");
                            }
                        }

                        // Stop the search task
                        if (searchTaskId.get() != -1) {
                            Bukkit.getScheduler().cancelTask(searchTaskId.get());
                            activeSearches.remove(finalWorldSettings.getName());
                        }
                    }
                    return;
                }

                // We have a location, perform the teleport
                Location teleportLocation = cachedLocation;
                if (teleportLocation == null) {
                    teleportLocation = safeLocationCache.remove(finalWorldSettings.getName());
                }

                // If somehow we still don't have a location, cancel
                if (teleportLocation == null) {
                    for (Player player : stillValidPlayers) {
                        cancelPlayerTeleport(player, "no-safe-locations");
                    }
                    return;
                }

                // Teleport the players to the location
                teleportPlayersToLocation(stillValidPlayers, teleportLocation);

                // Start preparing the next location
                if (!activeSearches.contains(finalWorldSettings.getName())) {
                    startLocationSearch(finalWorldSettings, new AtomicBoolean(false));
                }
            }
        }.runTaskLater(plugin, preTeleportTime * 20L);
    }

    /**
     * Start searching for a safe location in the background.
     * This uses a batched approach to avoid lag.
     *
     * @param worldSettings The world settings
     * @param resultFound An atomic boolean to update when a location is found
     * @return The BukkitTask for the search
     */
    private static BukkitTask startLocationSearch(WorldManager.WorldSettings worldSettings, AtomicBoolean resultFound) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        String worldName = worldSettings.getName();

        // If there's already a search for this world, don't start another
        if (activeSearches.contains(worldName)) {
            return null;
        }

        // Mark this world as being searched
        activeSearches.add(worldName);

        // Cancel any existing task
        BukkitTask existingTask = searchTasks.remove(worldName);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Start a new task to find a location
        BukkitTask task = new BukkitRunnable() {
            private final int MAX_ATTEMPTS = worldSettings.getMaxTeleportAttempts();
            private final int BATCH_SIZE = 5; // Check 5 locations per tick
            private int attempts = 0;
            private final long startTime = System.currentTimeMillis();
            private final long TIMEOUT_MS = 30000; // 30 second maximum search time

            @Override
            public void run() {
                // Check if we should stop
                if (attempts >= MAX_ATTEMPTS ||
                        System.currentTimeMillis() - startTime > TIMEOUT_MS ||
                        safeLocationCache.containsKey(worldName)) {

                    // Clean up
                    activeSearches.remove(worldName);
                    searchTasks.remove(worldName);

                    // Log debug info if enabled
                    if (plugin.getConfig().getBoolean("debug", false)) {
                        if (safeLocationCache.containsKey(worldName)) {
                            plugin.getLogger().info("Found safe location for " + worldName +
                                    " after " + attempts + " attempts");
                        } else {
                            plugin.getLogger().info("Failed to find safe location for " +
                                    worldName + " after " + attempts + " attempts or timeout");
                        }
                    }

                    cancel();
                    return;
                }

                // Process a batch of location checks
                for (int i = 0; i < BATCH_SIZE && attempts < MAX_ATTEMPTS; i++) {
                    attempts++;

                    // Try to find a safe location
                    Location location = findSingleSafeLocation(worldSettings);

                    if (location != null) {
                        // Store the location in the cache
                        safeLocationCache.put(worldName, location);

                        // Update the found flag if provided
                        if (resultFound != null) {
                            resultFound.set(true);
                        }

                        // Stop searching
                        activeSearches.remove(worldName);
                        searchTasks.remove(worldName);

                        if (plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().info("Found safe location at " +
                                    location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                                    " for " + worldName);
                        }

                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // Store the task
        searchTasks.put(worldName, task);

        return task;
    }

    /**
     * Try to find a single safe location.
     * This method is meant to be fast and only check one location.
     *
     * @param worldSettings The world settings
     * @return A safe location or null if not found
     */
    private static Location findSingleSafeLocation(WorldManager.WorldSettings worldSettings) {
        World world = worldSettings.getBukkitWorld();
        if (world == null) return null;

        int minX = worldSettings.getMinX();
        int maxX = worldSettings.getMaxX();
        int minZ = worldSettings.getMinZ();
        int maxZ = worldSettings.getMaxZ();

        // Generate random coordinates
        int x = minX + random.nextInt(maxX - minX + 1);
        int z = minZ + random.nextInt(maxZ - minZ + 1);

        // Only check loaded chunks to avoid lag
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            // Try to load chunk quickly, but don't wait if it's taking too long
            try {
                CompletableFuture<Boolean> future = world.getChunkAtAsync(x >> 4, z >> 4)
                        .thenApply(chunk -> true);

                // Wait at most 50ms for the chunk to load
                if (!future.completeOnTimeout(false, 50, TimeUnit.MILLISECONDS).get()) {
                    return null; // Skip if loading takes too long
                }
            } catch (Exception e) {
                return null; // Skip on error
            }
        }

        // Try to get height using the height map (more efficient than getHighestBlockYAt)
        int y;
        try {
            // Use height map if available (1.13+)
            y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);

            // Fall back to regular method if height seems wrong
            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) {
                y = world.getHighestBlockYAt(x, z);
            }
        } catch (Exception e) {
            try {
                // Fall back to regular method
                y = world.getHighestBlockYAt(x, z);
            } catch (Exception e2) {
                return null; // Skip on error
            }
        }

        // Skip if height is invalid
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) {
            return null;
        }

        // Create location
        Location location = new Location(world, x + 0.5, y + 1, z + 0.5);

        // Check claim protection
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        if (plugin.getClaimProtectionHandler().isLocationClaimed(location)) {
            return null;
        }

        // Check if location is safe if safety is required
        if (!worldSettings.isSafeTeleport() || isSafeLocation(location)) {
            return location;
        }

        return null;
    }

    /**
     * Get an emergency location when no random location can be found.
     *
     * @param worldSettings The world settings
     * @return An emergency location or null if none available
     */
    private static Location getEmergencyLocation(WorldManager.WorldSettings worldSettings) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();

        // If the configuration doesn't allow fallbacks, return null
        if (!plugin.getConfig().getBoolean("teleport.allow-fallback-locations", false)) {
            return null;
        }

        World world = worldSettings.getBukkitWorld();
        if (world == null) return null;

        // Try world spawn if it's in a loaded chunk
        Location spawn = world.getSpawnLocation();
        if (world.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) {
            spawn.add(0, 1, 0); // Add 1 to Y to ensure player is above ground

            // Check if spawn is in a claim
            if (!plugin.getClaimProtectionHandler().isLocationClaimed(spawn)) {
                return spawn;
            }
        }

        return null;
    }

    /**
     * Check if a location is safe for teleportation.
     *
     * @param location The location to check
     * @return True if the location is safe
     */
    private static boolean isSafeLocation(Location location) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();

        // Get unsafe blocks from config
        List<String> unsafeBlockNames = plugin.getConfig().getStringList("teleport.unsafe-blocks");
        Set<Material> unsafeBlocks = new HashSet<>();

        for (String blockName : unsafeBlockNames) {
            try {
                Material material = Material.valueOf(blockName);
                unsafeBlocks.add(material);
            } catch (IllegalArgumentException e) {
                // Skip invalid materials
            }
        }

        World world = location.getWorld();
        if (world == null) return false;

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Check if there's enough space for a player (2 blocks high)
        Block feetBlock = world.getBlockAt(x, y, z);
        Block headBlock = world.getBlockAt(x, y + 1, z);

        if (!feetBlock.getType().isAir() || !headBlock.getType().isAir()) {
            return false;
        }

        // Check block below player
        Block groundBlock = world.getBlockAt(x, y - 1, z);
        Material groundType = groundBlock.getType();

        // Check if standing on an unsafe block
        return !unsafeBlocks.contains(groundType) && groundType.isSolid();
    }

    /**
     * Teleport a group of players to a specific location.
     *
     * @param players The players to teleport
     * @param safeLocation The location to teleport to
     */
    private static void teleportPlayersToLocation(List<Player> players, Location safeLocation) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        QueueHandler queueHandler = plugin.getQueueHandler();

        // Build list of players still online and in queue
        List<Player> validPlayers = new ArrayList<>();
        for (Player player : players) {
            if (player.isOnline() && teleportPending.containsKey(player.getUniqueId())) {
                validPlayers.add(player);
            }
        }

        // If there aren't enough valid players, abort teleport
        int requiredPlayers = plugin.getConfig().getInt("queue.required-players", 2);
        if (validPlayers.size() < requiredPlayers) {
            for (Player player : validPlayers) {
                cancelPlayerTeleport(player, "not-enough-players");
            }
            return;
        }

        // Make sure we have a location
        if (safeLocation == null) {
            for (Player player : validPlayers) {
                cancelPlayerTeleport(player, "no-safe-locations");
            }
            return;
        }

        World world = safeLocation.getWorld();
        if (world == null) {
            for (Player player : validPlayers) {
                cancelPlayerTeleport(player, "invalid-world");
            }
            return;
        }

        // Get world settings
        WorldManager.WorldSettings worldSettings = plugin.getWorldManager().getWorldSettings(world.getName());

        // Notify about world before teleport
        String worldMessage = plugin.getConfig().getString("messages.world-teleport")
                .replace("{world}", worldSettings != null ? worldSettings.getDisplayName() : world.getName());

        // Teleport all valid players to the SAME location
        for (Player player : validPlayers) {
            // Remove from queue and pending teleport
            queueHandler.removeFromQueue(player);
            UUID playerUUID = player.getUniqueId();
            teleportPending.remove(playerUUID);

            // Remove from teleport group
            String groupId = playerToGroupMap.remove(playerUUID);
            if (groupId != null && playerToGroupMap.values().stream().noneMatch(g -> g.equals(groupId))) {
                teleportGroups.remove(groupId);
            }

            // Send world message if it's not the player's current world
            if (!player.getWorld().getName().equals(world.getName())) {
                MessageUtil.sendMessage(player, worldMessage);
            }

            // Show teleport titles and play sound
            MessageUtil.sendTitle(player,
                    plugin.getConfig().getString("messages.teleporting-title"),
                    plugin.getConfig().getString("messages.teleporting-subtitle"));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.teleport"));

            // Teleport to the same location
            player.teleport(safeLocation);
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.teleported"));
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Teleported " + validPlayers.size() + " players to " +
                    safeLocation.getBlockX() + "," + safeLocation.getBlockY() + "," + safeLocation.getBlockZ());
        }
    }

    /**
     * Cancel teleport for a player with specific reason.
     *
     * @param player The player to cancel for
     * @param reason Reason key for message
     */
    private static void cancelPlayerTeleport(Player player, String reason) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        UUID playerUUID = player.getUniqueId();

        // Remove from pending teleport
        teleportPending.remove(playerUUID);

        // Remove from queue
        plugin.getQueueHandler().removeFromQueue(player);

        // Get cancel message based on reason
        String messageKey = "messages.cancel-" + reason;
        String message = plugin.getConfig().getString(messageKey,
                plugin.getConfig().getString("messages.teleport-cancelled",
                        "&#ff6600⚠ &cTeleport cancelled!"));

        MessageUtil.sendMessage(player, message);
        MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
    }

    /**
     * Cancel teleport for an entire group when one player moves or disconnects.
     *
     * @param initiator The player who caused the cancellation
     * @param reason The reason for cancellation
     */
    public static void cancelTeleportGroup(Player initiator, String reason) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        UUID initiatorUUID = initiator.getUniqueId();

        // Get the group ID for this player
        String groupId = playerToGroupMap.get(initiatorUUID);
        if (groupId == null) {
            return; // Player not in a teleport group
        }

        // Get all players in this group
        List<UUID> groupMembers = teleportGroups.get(groupId);
        if (groupMembers == null || groupMembers.isEmpty()) {
            return; // No group members found
        }

        // Remove the group
        teleportGroups.remove(groupId);

        // Get configurable messages
        String initiatorName = initiator.getName();
        String cancelMessage = plugin.getConfig().getString("messages.group-cancel-" + reason,
                        plugin.getConfig().getString("messages.group-teleport-cancelled",
                                "&#ff6600⚠ &cTeleport cancelled because {player} {reason}!"))
                .replace("{player}", initiatorName)
                .replace("{reason}", reason);

        // Cancel teleport for all members
        for (UUID memberUUID : groupMembers) {
            // Remove from mapping
            playerToGroupMap.remove(memberUUID);

            // Remove from pending teleport
            teleportPending.remove(memberUUID);

            // Notify player if online
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                // Don't tell the initiator it was their fault
                if (!memberUUID.equals(initiatorUUID)) {
                    MessageUtil.sendMessage(member, cancelMessage);
                } else {
                    // For the initiator, use a different message
                    MessageUtil.sendMessage(member, plugin.getConfig().getString("messages.cancel-" + reason,
                            plugin.getConfig().getString("messages.teleport-cancelled",
                                    "&#ff6600⚠ &cTeleport cancelled!")));
                }

                // Remove from queue
                plugin.getQueueHandler().removeFromQueue(member);

                // Play sound
                MessageUtil.playSound(member, plugin.getConfig().getString("sounds.error"));
            }
        }
    }

    /**
     * Check if a player has a pending teleport.
     *
     * @param player The player to check
     * @return True if teleport is pending
     */
    public static boolean hasPendingTeleport(Player player) {
        return teleportPending.getOrDefault(player.getUniqueId(), false);
    }

    /**
     * Cancel a pending teleport for a player.
     *
     * @param player The player to cancel for
     */
    public static void cancelPendingTeleport(Player player) {
        teleportPending.remove(player.getUniqueId());
    }
}