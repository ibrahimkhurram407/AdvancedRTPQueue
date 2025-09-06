package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

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

    // Task references for location search tasks
    private static final Map<String, BukkitTask> searchTasks = new ConcurrentHashMap<>();

    /**
     * Try to teleport players from a queue if there are enough players.
     *
     * @param triggerPlayer The player who triggered this check
     */
    public static void tryTeleport(Player triggerPlayer) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        if (plugin == null || triggerPlayer == null) return;

        QueueHandler queueHandler = plugin.getQueueHandler();
        if (queueHandler == null) return;

        // Get the world this player is queued for
        String worldName = queueHandler.getPlayerQueueWorld(triggerPlayer);
        if (worldName == null) {
            return; // Player not in queue
        }

        // Check if this world's queue has enough players
        List<Player> playersToTeleport = queueHandler.getPlayersForTeleport(worldName);
        if (playersToTeleport == null || playersToTeleport.isEmpty()) {
            return; // Not enough players yet
        }

        // Get world settings
        WorldManager.WorldSettings worldSettings = plugin.getWorldManager().getWorldSettings(worldName);
        if (worldSettings == null) return;

        // Determine required players for a teleport
        int requiredPlayers = plugin.getConfig().getInt("queue.required-players", 2);

        // If the required players is exactly 2, delegate the two-player countdown to CooldownManager
        // This uses your existing startPreTeleportCountdown method and prevents duplicate logic.
        if (requiredPlayers == 2 && playersToTeleport.size() >= 2) {
            Player p1 = playersToTeleport.get(0);
            Player p2 = playersToTeleport.get(1);

            // Ensure both players are online
            if (p1 != null && p1.isOnline() && p2 != null && p2.isOnline()) {
                // Delegate to CooldownManager which will call back to tryTeleport when countdown finishes.
                if (plugin.getCooldownManager() != null) {
                    plugin.getCooldownManager().startPreTeleportCountdown(p1, p2);
                    return;
                }
            }
        }

        // Fallback / group behavior (requiredPlayers > 2 or no cooldown manager)
        // Get teleport countdown time
        int preTeleportTime = Math.max(0, plugin.getConfig().getInt("cooldowns.pre-teleport", 5));

        // Create a unique group ID for this teleport group
        String groupId = UUID.randomUUID().toString();
        List<UUID> playerUUIDs = new ArrayList<>();

        // Prepare all players for teleport
        for (Player player : playersToTeleport) {
            if (player == null || !player.isOnline()) continue;

            UUID playerUUID = player.getUniqueId();
            // Mark player as pending teleport
            teleportPending.put(playerUUID, true);

            // Add to teleport group
            playerUUIDs.add(playerUUID);
            playerToGroupMap.put(playerUUID, groupId);

            // Notify player that match was found (use processPlaceholders + display name)
            String template = plugin.getConfig().getString("messages.opponent-found", "Match found! Teleporting in {time} seconds...");
            Map<String, String> placeholders = Map.of(
                    "players", String.valueOf(playersToTeleport.size()),
                    "time", String.valueOf(preTeleportTime),
                    "player", player.getName(),
                    "world", worldSettings.getDisplayName()
            );
            MessageUtil.sendMessage(player, MessageUtil.processPlaceholders(template, placeholders));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.opponent-found", ""));
        }

        // Register the teleport group
        teleportGroups.put(groupId, playerUUIDs);

        // Create final copies for use in the runnable
        final List<Player> finalPlayersToTeleport = new ArrayList<>(playersToTeleport);
        final WorldManager.WorldSettings finalWorldSettings = worldSettings;

        // Use a cached location if available
        Location cachedLocation = safeLocationCache.remove(worldName);
        final AtomicBoolean locationFound = new AtomicBoolean(cachedLocation != null);

        // If no cached location is available, start searching for one asynchronously
        if (!locationFound.get()) {
            BukkitTask task = startLocationSearch(finalWorldSettings, locationFound);
            if (task != null) {
                // nothing else required here; searchTasks contains the task
            }
        }

        // Start teleport countdown or immediate teleport
        new BukkitRunnable() {
            private final long startTime = System.currentTimeMillis();
            private final long timeoutMs = Math.max(0, preTeleportTime) * 1000L;

            @Override
            public void run() {
                // Collect still valid players
                List<Player> stillValidPlayers = new ArrayList<>();
                for (Player player : finalPlayersToTeleport) {
                    if (player != null && player.isOnline() && teleportPending.getOrDefault(player.getUniqueId(), false)) {
                        stillValidPlayers.add(player);
                    }
                }

                // Check if we still have enough players
                int reqPlayers = plugin.getConfig().getInt("queue.required-players", 2);
                if (stillValidPlayers.size() < reqPlayers) {
                    // Not enough players, cancel teleport for everyone
                    for (Player player : stillValidPlayers) {
                        cancelPlayerTeleport(player, "not-enough-players");
                    }

                    // Stop any ongoing location search
                    if (searchTasks.containsKey(finalWorldSettings.getName())) {
                        BukkitTask st = searchTasks.remove(finalWorldSettings.getName());
                        try {
                            if (st != null) st.cancel();
                        } catch (Throwable ignored) {}
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
                        if (searchTasks.containsKey(finalWorldSettings.getName())) {
                            BukkitTask st = searchTasks.remove(finalWorldSettings.getName());
                            try {
                                if (st != null) st.cancel();
                            } catch (Throwable ignored) {}
                            activeSearches.remove(finalWorldSettings.getName());
                        }
                        return;
                    } else {
                        // Still waiting for location, do nothing this tick
                        return;
                    }
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

                // Start preparing the next location if none active
                if (!activeSearches.contains(finalWorldSettings.getName())) {
                    startLocationSearch(finalWorldSettings, new AtomicBoolean(false));
                }
            }
        }.runTaskLater(plugin, Math.max(0, preTeleportTime) * 20L);
    }

    /**
     * Start searching for a safe location in the background.
     * Runs asynchronously to avoid main thread blocking.
     *
     * @param worldSettings The world settings
     * @param resultFound An atomic boolean to update when a location is found
     * @return The BukkitTask for the search (may be null if already searching)
     */
    private static BukkitTask startLocationSearch(WorldManager.WorldSettings worldSettings, AtomicBoolean resultFound) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        if (plugin == null || worldSettings == null) return null;
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
            try {
                existingTask.cancel();
            } catch (Throwable ignored) {}
        }

        // Start a new task to find a location asynchronously
        BukkitTask task = new BukkitRunnable() {
            private final int MAX_ATTEMPTS = Math.max(1, worldSettings.getMaxTeleportAttempts());
            private final int BATCH_SIZE = 5; // Check 5 locations per iteration
            private int attempts = 0;
            private final long startTime = System.currentTimeMillis();
            private final long TIMEOUT_MS = 30_000L; // 30 second maximum search time

            @Override
            public void run() {
                try {
                    // Check if we should stop
                    if (attempts >= MAX_ATTEMPTS ||
                            System.currentTimeMillis() - startTime > TIMEOUT_MS ||
                            safeLocationCache.containsKey(worldName)) {

                        // Clean up markers
                        activeSearches.remove(worldName);
                        searchTasks.remove(worldName);
                        cancel();
                        return;
                    }

                    // Process a small batch of location checks off main thread
                    for (int i = 0; i < BATCH_SIZE && attempts < MAX_ATTEMPTS; i++) {
                        attempts++;

                        // Try to find a safe location without blocking main thread
                        Location location = findSingleSafeLocationAsyncSafe(worldSettings);

                        if (location != null) {
                            // schedule caching on main thread (safeLocationCache is read on main thread)
                            final Location found = location;
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    safeLocationCache.put(worldName, found);
                                    if (resultFound != null) resultFound.set(true);
                                    activeSearches.remove(worldName);
                                    searchTasks.remove(worldName);
                                }
                            }.runTask(plugin);

                            if (plugin.getConfig().getBoolean("debug", false)) {
                                plugin.getLogger().info("Found safe location at " +
                                        location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                                        " for " + worldName + " after " + attempts + " attempts (async)");
                            }

                            cancel();
                            return;
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Error during async location search: " + t.getMessage());
                    activeSearches.remove(worldName);
                    searchTasks.remove(worldName);
                    cancel();
                }
            }
        }.runTaskTimerAsynchronously(plugin, 1L, 1L);

        // Store the task reference
        searchTasks.put(worldName, task);

        return task;
    }

    /**
     * Attempt to find a single safe location in a manner safe for async execution.
     * This avoids blocking calls or operations that must be done on the main thread.
     *
     * @param worldSettings The world settings
     * @return A safe Location or null
     */
    private static Location findSingleSafeLocationAsyncSafe(WorldManager.WorldSettings worldSettings) {
        if (worldSettings == null) return null;
        World world = worldSettings.getBukkitWorld();
        if (world == null) return null;

        int minX = worldSettings.getMinX();
        int maxX = worldSettings.getMaxX();
        int minZ = worldSettings.getMinZ();
        int maxZ = worldSettings.getMaxZ();

        if (maxX <= minX || maxZ <= minZ) return null;

        int x = minX + random.nextInt(maxX - minX + 1);
        int z = minZ + random.nextInt(maxZ - minZ + 1);

        // Avoid forcing chunk loads here — skip if not loaded to keep async safe
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }

        int y;
        try {
            y = world.getHighestBlockYAt(x, z);
        } catch (Throwable t) {
            return null;
        }

        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) return null;

        Location location = new Location(world, x + 0.5, y + 1.0, z + 0.5);

        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        if (plugin == null) return null;

        // Check claim protection
        if (plugin.getClaimProtectionHandler() != null && plugin.getClaimProtectionHandler().isLocationClaimed(location)) {
            return null;
        }

        // If safety not required, return location
        if (!worldSettings.isSafeTeleport()) {
            return location;
        }

        // isSafeLocation uses read-only block queries — safe here because chunk is loaded
        if (isSafeLocation(location, plugin)) {
            return location;
        }

        return null;
    }

    /**
     * Check if a location is safe for teleportation.
     *
     * @param location The location to check
     * @param plugin   The plugin instance
     * @return True if the location is safe
     */
    private static boolean isSafeLocation(Location location, AdvancedRTPQueue plugin) {
        if (location == null) return false;
        World world = location.getWorld();
        if (world == null) return false;

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);

        if (!feet.getType().isAir() || !head.getType().isAir()) return false;

        Block ground = world.getBlockAt(x, y - 1, z);
        if (!ground.getType().isSolid()) return false;

        List<String> unsafeNames = plugin.getConfig().getStringList("teleport.unsafe-blocks");
        for (String nm : unsafeNames) {
            try {
                if (ground.getType().name().equalsIgnoreCase(nm)) {
                    return false;
                }
            } catch (Throwable ignored) {}
        }

        return true;
    }

    /**
     * Get an emergency location when no random location can be found.
     */
    private static Location getEmergencyLocation(WorldManager.WorldSettings worldSettings) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        if (plugin == null || worldSettings == null) return null;

        if (!plugin.getConfig().getBoolean("teleport.allow-fallback-locations", false)) return null;

        World world = worldSettings.getBukkitWorld();
        if (world == null) return null;

        Location spawn = world.getSpawnLocation();
        if (spawn != null) {
            spawn = spawn.clone().add(0, 1, 0);
            if (plugin.getClaimProtectionHandler() == null || !plugin.getClaimProtectionHandler().isLocationClaimed(spawn)) {
                return spawn;
            }
        }

        return null;
    }

    /**
     * Teleport a group of players to a specific location.
     */
    private static void teleportPlayersToLocation(List<Player> players, Location safeLocation) {
        if (players == null || players.isEmpty() || safeLocation == null) return;

        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        if (plugin == null) return;

        QueueHandler queueHandler = plugin.getQueueHandler();

        List<Player> validPlayers = new ArrayList<>();
        for (Player player : players) {
            if (player != null && player.isOnline() && teleportPending.getOrDefault(player.getUniqueId(), false)) {
                validPlayers.add(player);
            }
        }

        int requiredPlayers = plugin.getConfig().getInt("queue.required-players", 2);
        if (validPlayers.size() < requiredPlayers) {
            for (Player p : validPlayers) {
                cancelPlayerTeleport(p, "not-enough-players");
            }
            return;
        }

        World world = safeLocation.getWorld();
        if (world == null) {
            for (Player p : validPlayers) {
                cancelPlayerTeleport(p, "invalid-world");
            }
            return;
        }

        // Notify and teleport each player
        for (Player player : validPlayers) {
            // Clean up queue/pending before teleport
            if (queueHandler != null) {
                queueHandler.removeFromQueue(player);
            }
            teleportPending.remove(player.getUniqueId());

            // Remove from group mapping
            String groupId = playerToGroupMap.remove(player.getUniqueId());
            if (groupId != null) {
                List<UUID> grp = teleportGroups.get(groupId);
                if (grp != null) {
                    grp.remove(player.getUniqueId());
                    if (grp.isEmpty()) {
                        teleportGroups.remove(groupId);
                    }
                }
            }

            // Send world change notice if needed (use display name)
            String worldDisplay = plugin.getWorldManager() != null
                    ? plugin.getWorldManager().getWorldSettings(world.getName()).getDisplayName()
                    : MessageUtil.colorize(world.getName());

            String worldTemplate = plugin.getConfig().getString("messages.world-teleport", "Teleporting to {world}");
            Map<String, String> worldPlaceholders = Map.of(
                    "world", worldDisplay,
                    "player", player.getName()
            );

            if (!player.getWorld().getName().equals(world.getName())) {
                MessageUtil.sendMessage(player, MessageUtil.processPlaceholders(worldTemplate, worldPlaceholders));
            }

            // Title/sound
            String title = plugin.getConfig().getString("messages.teleporting-title", "");
            String subtitle = plugin.getConfig().getString("messages.teleporting-subtitle", "");
            MessageUtil.sendTitle(player,
                    MessageUtil.processPlaceholders(title, worldPlaceholders),
                    MessageUtil.processPlaceholders(subtitle, worldPlaceholders));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.teleport", ""));

            // Do teleport
            try {
                player.teleport(safeLocation);
                String doneTemplate = plugin.getConfig().getString("messages.teleported", "Teleported!");
                Map<String, String> donePlaceholders = Map.of(
                        "player", player.getName(),
                        "world", worldDisplay
                );
                MessageUtil.sendMessage(player, MessageUtil.processPlaceholders(doneTemplate, donePlaceholders));
            } catch (Throwable t) {
                cancelPlayerTeleport(player, "error");
            }
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Teleported " + validPlayers.size() + " players to " +
                    safeLocation.getBlockX() + "," + safeLocation.getBlockY() + "," + safeLocation.getBlockZ() +
                    " in world " + safeLocation.getWorld().getName());
        }
    }

    /**
     * Cancel teleport for a player with specific reason.
     */
    private static void cancelPlayerTeleport(Player player, String reason) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        if (plugin == null || player == null) return;

        UUID playerUUID = player.getUniqueId();

        // Remove from pending teleport
        teleportPending.remove(playerUUID);

        // Remove from queue
        if (plugin.getQueueHandler() != null) {
            plugin.getQueueHandler().removeFromQueue(player);
        }

        // Get cancel message based on reason and process placeholders
        String messageKey = "messages.cancel-" + reason;
        String raw = plugin.getConfig().getString(messageKey,
                plugin.getConfig().getString("messages.teleport-cancelled",
                        "&#ff6600⚠ &cTeleport cancelled!"));

        Map<String, String> placeholders = Map.of(
                "player", player.getName()
        );

        MessageUtil.sendMessage(player, MessageUtil.processPlaceholders(raw, placeholders));
        MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error", ""));
    }

    /**
     * Cancel teleport for an entire group when one player moves or disconnects.
     */
    public static void cancelTeleportGroup(Player initiator, String reason) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        if (plugin == null || initiator == null) return;

        UUID initiatorUUID = initiator.getUniqueId();

        // Get the group ID for this player
        String groupId = playerToGroupMap.get(initiatorUUID);
        if (groupId == null) {
            return; // Player not in a teleport group
        }

        // Get all players in this group
        List<UUID> groupMembers = teleportGroups.remove(groupId);
        if (groupMembers == null || groupMembers.isEmpty()) {
            return; // No group members found
        }

        // Build cancel message using placeholders (player + reason)
        String template = plugin.getConfig().getString("messages.group-cancel-" + reason,
                plugin.getConfig().getString("messages.group-teleport-cancelled",
                        "&#ff6600⚠ &cTeleport cancelled because {player} {reason}!"));

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
                    Map<String, String> placeholders = Map.of(
                            "player", initiator.getName(),
                            "reason", reason
                    );
                    MessageUtil.sendMessage(member, MessageUtil.processPlaceholders(template, placeholders));
                } else {
                    // For the initiator, use a different message (single cancel message)
                    String single = plugin.getConfig().getString("messages.cancel-" + reason,
                            plugin.getConfig().getString("messages.teleport-cancelled",
                                    "&#ff6600⚠ &cTeleport cancelled!"));
                    MessageUtil.sendMessage(member, MessageUtil.processPlaceholders(single, Map.of("player", initiator.getName())));
                }

                // Remove from queue
                if (plugin.getQueueHandler() != null) {
                    plugin.getQueueHandler().removeFromQueue(member);
                }

                // Play sound
                MessageUtil.playSound(member, plugin.getConfig().getString("sounds.error", ""));
            }
        }
    }

    /**
     * Check if a player has a pending teleport.
     */
    public static boolean hasPendingTeleport(Player player) {
        return player != null && teleportPending.getOrDefault(player.getUniqueId(), false);
    }

    /**
     * Cancel a pending teleport for a player.
     */
    public static void cancelPendingTeleport(Player player) {
        if (player == null) return;
        teleportPending.remove(player.getUniqueId());
    }

    /**
     * Shutdown helper to cancel search tasks and clear static caches on plugin disable.
     */
    public static void shutdown() {
        // Cancel all running search tasks
        for (BukkitTask t : new ArrayList<>(searchTasks.values())) {
            try {
                if (t != null) t.cancel();
            } catch (Throwable ignored) {}
        }
        searchTasks.clear();

        // Clear active search markers
        activeSearches.clear();

        // Clear safe location cache
        safeLocationCache.clear();

        // Clear pending teleports and groups
        teleportPending.clear();
        playerToGroupMap.clear();
        teleportGroups.clear();
    }
}