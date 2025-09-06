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

/**
 * Complete teleport manager
 */
public class TeleportManager {
    private final AdvancedRTPQueue plugin;
    private final Map<String, Queue<Location>> locationCache = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> searchTasks = new ConcurrentHashMap<>();
    private final Set<String> activeSearches = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, TeleportSession> activeSessions = new ConcurrentHashMap<>();

    private final Random random = ThreadLocalRandom.current();

    public TeleportManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        preloadLocations();
    }

    /**
     * Start teleportation process for matched players
     */
    public void startTeleportation(List<Player> players, String worldName) {
        if (players == null || players.isEmpty()) {
            return;
        }

        // Create teleport session
        String sessionId = UUID.randomUUID().toString();
        TeleportSession session = new TeleportSession(sessionId, players, worldName);

        // Register session for each player
        for (Player player : players) {
            activeSessions.put(player.getUniqueId(), session);
        }

        // Get world settings
        WorldManager.WorldSettings worldSettings = plugin.getWorldManager().getWorldSettings(worldName);
        if (worldSettings == null) {
            cancelSession(session, "invalid-world");
            return;
        }

        // Notify players
        notifyPlayersMatchFound(session);

        // Start countdown
        startCountdown(session, worldSettings);
    }

    /**
     * Notify players that match was found
     */
    private void notifyPlayersMatchFound(TeleportSession session) {
        List<Player> players = getValidPlayers(session);
        if (players.isEmpty()) {
            return;
        }

        int countdown = plugin.getConfigManager().getInt("cooldowns.pre-teleport", 5);

        for (Player player : players) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("time", String.valueOf(countdown));
            placeholders.put("players", String.valueOf(players.size()));
            placeholders.put("world", plugin.getWorldManager().getDisplayName(session.getWorldName()));

            MessageUtil.sendMessage(player, "match-found", placeholders);
            MessageUtil.sendTitle(player, "match-found", "match-found", placeholders);
            MessageUtil.playSound(player, "match-found");
        }
    }

    /**
     * Start pre-teleport countdown
     */
    private void startCountdown(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        int countdown = plugin.getConfigManager().getInt("cooldowns.pre-teleport", 5);

        if (countdown <= 0) {
            executeTeleport(session, worldSettings);
            return;
        }

        BukkitTask countdownTask = new BukkitRunnable() {
            private int timeLeft = countdown;

            @Override
            public void run() {
                // Check if session is still valid
                if (!isSessionValid(session)) {
                    cancel();
                    return;
                }

                // Send countdown messages
                if (timeLeft > 0) {
                    sendCountdownMessages(session, timeLeft);
                    timeLeft--;
                } else {
                    // Execute teleport
                    executeTeleport(session, worldSettings);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        session.setCountdownTask(countdownTask);
    }

    /**
     * Send countdown messages to players
     */
    private void sendCountdownMessages(TeleportSession session, int timeLeft) {
        List<Player> players = getValidPlayers(session);

        for (Player player : players) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("time", String.valueOf(timeLeft));

            MessageUtil.sendActionBar(player, "countdown", placeholders);
            MessageUtil.sendTitle(player, "countdown", "countdown", placeholders);
            MessageUtil.playSound(player, "countdown");
        }
    }

    /**
     * Execute the actual teleportation
     */
    private void executeTeleport(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        if (!isSessionValid(session)) {
            return;
        }

        // Get or find a safe location
        Location targetLocation = getOrFindLocation(worldSettings);

        if (targetLocation == null) {
            // Start async location search
            startAsyncLocationSearch(session, worldSettings);
            return;
        }

        // Teleport all players
        teleportPlayers(session, targetLocation);
    }

    /**
     * Get cached location or find new one
     */
    private Location getOrFindLocation(WorldManager.WorldSettings worldSettings) {
        String worldName = worldSettings.getName();

        // Try to get from cache first
        Queue<Location> cached = locationCache.get(worldName);
        if (cached != null && !cached.isEmpty()) {
            Location location = cached.poll();
            if (isLocationStillSafe(location)) {
                return location;
            }
        }

        // Try to find immediately (synchronous)
        return findSafeLocationSync(worldSettings, 3);
    }

    /**
     * Start async location search
     */
    private void startAsyncLocationSearch(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        String worldName = worldSettings.getName();

        if (activeSearches.contains(worldName)) {
            // Wait for existing search
            waitForLocationSearch(session, worldSettings);
            return;
        }

        activeSearches.add(worldName);

        BukkitTask searchTask = new BukkitRunnable() {
            private int attempts = 0;
            private final int maxAttempts = worldSettings.getMaxTeleportAttempts();
            private final long timeout = plugin.getConfigManager().getInt("teleport.search-timeout", 30) * 1000L;
            private final long searchStartTime = System.currentTimeMillis();

            @Override
            public void run() {
                try {
                    // Check timeout
                    if (System.currentTimeMillis() - searchStartTime > timeout) {
                        handleSearchTimeout(session, worldSettings);
                        cleanup();
                        return;
                    }

                    // Check max attempts
                    if (attempts >= maxAttempts) {
                        handleSearchFailed(session, worldSettings);
                        cleanup();
                        return;
                    }

                    // Try to find location
                    Location location = findSafeLocationAsync(worldSettings);
                    if (location != null) {
                        // Found location, schedule teleport on main thread
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                teleportPlayers(session, location);
                            }
                        }.runTask(plugin);

                        cleanup();
                        return;
                    }

                    attempts++;
                } catch (Exception e) {
                    if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                        plugin.getLogger().warning("Error in async location search: " + e.getMessage());
                    }
                    cleanup();
                }
            }

            private void cleanup() {
                activeSearches.remove(worldName);
                searchTasks.remove(worldName);
                cancel();
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 2L);

        searchTasks.put(worldName, searchTask);
    }

    /**
     * Wait for existing location search
     */
    private void waitForLocationSearch(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        new BukkitRunnable() {
            private int waitTime = 0;
            private final int maxWait = 20; // 20 seconds

            @Override
            public void run() {
                if (!activeSearches.contains(worldSettings.getName())) {
                    // Search completed, try to get location
                    Location location = getOrFindLocation(worldSettings);
                    if (location != null) {
                        teleportPlayers(session, location);
                    } else {
                        handleSearchFailed(session, worldSettings);
                    }
                    cancel();
                    return;
                }

                if (waitTime >= maxWait) {
                    handleSearchTimeout(session, worldSettings);
                    cancel();
                    return;
                }

                waitTime++;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Handle search timeout
     */
    private void handleSearchTimeout(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        if (plugin.getConfigManager().getBoolean("teleport.allow-fallback-locations")) {
            Location fallback = getFallbackLocation(worldSettings);
            if (fallback != null) {
                teleportPlayers(session, fallback);
                return;
            }
        }

        cancelSession(session, "timeout");
    }

    /**
     * Handle search failure
     */
    private void handleSearchFailed(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        if (plugin.getConfigManager().getBoolean("teleport.allow-fallback-locations")) {
            Location fallback = getFallbackLocation(worldSettings);
            if (fallback != null) {
                teleportPlayers(session, fallback);
                return;
            }
        }

        cancelSession(session, "no-safe-locations");
    }

    /**
     * Teleport all players in session
     */
    private void teleportPlayers(TeleportSession session, Location location) {
        if (!isSessionValid(session)) {
            return;
        }

        List<Player> validPlayers = getValidPlayers(session);
        if (validPlayers.isEmpty()) {
            return;
        }

        // Remove players from queue and sessions
        for (Player player : validPlayers) {
            plugin.getQueueHandler().removeFromQueue(player);
            activeSessions.remove(player.getUniqueId());

            // Teleport player
            teleportPlayer(player, location);
        }

        // Cache additional locations for this world
        cacheLocationForWorld(location.getWorld().getName());
    }

    /**
     * Teleport individual player
     */
    private void teleportPlayer(Player player, Location location) {
        try {
            // Send teleporting messages
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("world", plugin.getWorldManager().getDisplayName(location.getWorld().getName()));

            MessageUtil.sendMessage(player, "teleporting", placeholders);
            MessageUtil.sendTitle(player, "teleported", "teleported");
            MessageUtil.playSound(player, "teleport-success");
            MessageUtil.spawnParticles(player, "teleport-start");

            // Teleport
            player.teleport(location);

            // Post-teleport effects
            new BukkitRunnable() {
                @Override
                public void run() {
                    MessageUtil.sendMessage(player, "teleported", placeholders);
                    MessageUtil.spawnParticles(player, "teleport-success");
                }
            }.runTaskLater(plugin, 10L);

            // Apply post-teleport cooldown
            if (plugin.getCooldownManager() != null) {
                plugin.getCooldownManager().setPostTeleportCooldown(player);
            }

            // Log if enabled
            if (plugin.getConfigManager().getBoolean("advanced.log-teleports")) {
                plugin.getLogger().info("Teleported " + player.getName() + " to " +
                        location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                        " in " + location.getWorld().getName());
            }

        } catch (Exception e) {
            MessageUtil.sendMessage(player, "teleport-failed");
            MessageUtil.playSound(player, "error");

            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().warning("Failed to teleport " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Find safe location synchronously
     */
    private Location findSafeLocationSync(WorldManager.WorldSettings worldSettings, int maxAttempts) {
        World world = worldSettings.getBukkitWorld();
        if (world == null) {
            return null;
        }

        for (int i = 0; i < maxAttempts; i++) {
            Location location = generateRandomLocation(worldSettings);
            if (location != null && isSafeLocation(location)) {
                return location;
            }
        }

        return null;
    }

    /**
     * Find safe location asynchronously
     */
    private Location findSafeLocationAsync(WorldManager.WorldSettings worldSettings) {
        World world = worldSettings.getBukkitWorld();
        if (world == null) {
            return null;
        }

        Location location = generateRandomLocation(worldSettings);
        if (location != null && isSafeLocationAsync(location)) {
            return location;
        }

        return null;
    }

    /**
     * Generate random location within world boundaries
     */
    private Location generateRandomLocation(WorldManager.WorldSettings worldSettings) {
        World world = worldSettings.getBukkitWorld();
        if (world == null) {
            return null;
        }

        int x = random.nextInt(worldSettings.getMaxX() - worldSettings.getMinX() + 1) + worldSettings.getMinX();
        int z = random.nextInt(worldSettings.getMaxZ() - worldSettings.getMinZ() + 1) + worldSettings.getMinZ();

        // Check if chunk is loaded (for async safety)
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }

        int y;
        try {
            y = world.getHighestBlockYAt(x, z);
        } catch (Exception e) {
            return null;
        }

        // Apply Y limits
        int minY = plugin.getConfigManager().getInt("teleport.min-y", 60);
        int maxY = plugin.getConfigManager().getInt("teleport.max-y", 250);

        if (y < minY) {
            y = minY;
        } else if (y > maxY) {
            y = maxY;
        }

        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    /**
     * Check if location is safe (main thread)
     */
    private boolean isSafeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        // Check claim protection
        if (plugin.getClaimProtectionHandler() != null &&
                plugin.getClaimProtectionHandler().isLocationClaimed(location)) {
            return false;
        }

        return checkLocationSafety(location);
    }

    /**
     * Check if location is safe (async safe)
     */
    private boolean isSafeLocationAsync(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        // Skip claim checking in async context
        return checkLocationSafety(location);
    }

    /**
     * Check basic location safety
     */
    private boolean checkLocationSafety(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        try {
            // Check if blocks are loaded
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return false;
            }

            Block ground = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);

            // Check if feet and head are air
            if (!feet.getType().isAir() || !head.getType().isAir()) {
                return false;
            }

            // Check if ground is solid
            if (!ground.getType().isSolid()) {
                return false;
            }

            // Check unsafe blocks
            List<String> unsafeBlocks = plugin.getConfigManager().getStringList("teleport.unsafe-blocks");
            String groundType = ground.getType().name();

            for (String unsafeBlock : unsafeBlocks) {
                if (groundType.equalsIgnoreCase(unsafeBlock)) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if cached location is still safe
     */
    private boolean isLocationStillSafe(Location location) {
        if (location == null) {
            return false;
        }

        return isSafeLocation(location);
    }

    /**
     * Get fallback location (spawn)
     */
    private Location getFallbackLocation(WorldManager.WorldSettings worldSettings) {
        World world = worldSettings.getBukkitWorld();
        if (world == null) {
            return null;
        }

        Location spawn = world.getSpawnLocation();
        if (spawn != null && isSafeLocation(spawn)) {
            return spawn.clone().add(0, 1, 0);
        }

        return null;
    }

    /**
     * Preload locations for all worlds
     */
    private void preloadLocations() {
        if (!plugin.getConfigManager().getBoolean("teleport.cache-safe-locations")) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (String worldName : plugin.getWorldManager().getValidWorldNames()) {
                    cacheLocationForWorld(worldName);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Cache locations for a specific world
     */
    private void cacheLocationForWorld(String worldName) {
        if (activeSearches.contains(worldName)) {
            return;
        }

        int maxCached = plugin.getConfigManager().getInt("teleport.max-cached-locations", 10);
        Queue<Location> cached = locationCache.computeIfAbsent(worldName, k -> new LinkedList<>());

        if (cached.size() >= maxCached) {
            return;
        }

        WorldManager.WorldSettings worldSettings = plugin.getWorldManager().getWorldSettings(worldName);
        if (worldSettings == null) {
            return;
        }

        activeSearches.add(worldName);

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 5 && cached.size() < maxCached; i++) {
                        Location location = findSafeLocationAsync(worldSettings);
                        if (location != null) {
                            cached.offer(location);
                        }
                    }
                } finally {
                    activeSearches.remove(worldName);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Cancel teleport session
     */
    public void cancelSession(TeleportSession session, String reason) {
        if (session == null) {
            return;
        }

        List<Player> players = getValidPlayers(session);

        // Cancel countdown task
        if (session.getCountdownTask() != null) {
            session.getCountdownTask().cancel();
        }

        // Remove from active sessions
        for (UUID playerUUID : session.getPlayerUUIDs()) {
            activeSessions.remove(playerUUID);
        }

        // Notify players
        for (Player player : players) {
            MessageUtil.sendMessage(player, "cancelled-" + reason);
            MessageUtil.playSound(player, "teleport-cancelled");
        }
    }

    /**
     * Cancel player's session
     */
    public void cancelPlayerSession(Player player, String reason) {
        TeleportSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        // Remove player from session
        session.removePlayer(player.getUniqueId());
        activeSessions.remove(player.getUniqueId());

        // If session becomes empty, cancel it
        if (session.getPlayerUUIDs().isEmpty()) {
            cancelSession(session, reason);
            return;
        }

        // Notify remaining players
        List<Player> remainingPlayers = getValidPlayers(session);
        for (Player remainingPlayer : remainingPlayers) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("reason", reason);

            MessageUtil.sendMessage(remainingPlayer, "group-cancelled", placeholders);
        }

        // Check if still enough players
        int requiredPlayers = plugin.getConfigManager().getInt("queue.required-players", 2);
        if (remainingPlayers.size() < requiredPlayers) {
            cancelSession(session, "not-enough");
        }
    }

    /**
     * Check if session is valid
     */
    private boolean isSessionValid(TeleportSession session) {
        if (session == null) {
            return false;
        }

        List<Player> validPlayers = getValidPlayers(session);
        int requiredPlayers = plugin.getConfigManager().getInt("queue.required-players", 2);

        return validPlayers.size() >= requiredPlayers;
    }

    /**
     * Get valid players from session
     */
    private List<Player> getValidPlayers(TeleportSession session) {
        List<Player> validPlayers = new ArrayList<>();

        for (UUID playerUUID : session.getPlayerUUIDs()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                validPlayers.add(player);
            }
        }

        return validPlayers;
    }

    /**
     * Get player's active session
     */
    public TeleportSession getPlayerSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    /**
     * Check if player has active session
     */
    public boolean hasActiveSession(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    /**
     * Shutdown teleport manager
     */
    public void shutdown() {
        // Cancel all active sessions
        for (TeleportSession session : activeSessions.values()) {
            cancelSession(session, "shutdown");
        }
        activeSessions.clear();

        // Cancel all search tasks
        for (BukkitTask task : searchTasks.values()) {
            task.cancel();
        }
        searchTasks.clear();

        // Clear caches
        locationCache.clear();
        activeSearches.clear();
    }

    /**
     * Teleport session class
     */
    public static class TeleportSession {
        private final String sessionId;
        private final Set<UUID> playerUUIDs;
        private final String worldName;
        private BukkitTask countdownTask;
        private final long createdTime;

        public TeleportSession(String sessionId, List<Player> players, String worldName) {
            this.sessionId = sessionId;
            this.worldName = worldName;
            this.playerUUIDs = new HashSet<>();
            this.createdTime = System.currentTimeMillis();

            for (Player player : players) {
                this.playerUUIDs.add(player.getUniqueId());
            }
        }

        public String getSessionId() { return sessionId; }
        public Set<UUID> getPlayerUUIDs() { return new HashSet<>(playerUUIDs); }
        public String getWorldName() { return worldName; }
        public BukkitTask getCountdownTask() { return countdownTask; }
        public void setCountdownTask(BukkitTask countdownTask) { this.countdownTask = countdownTask; }
        public long getCreatedTime() { return createdTime; }
        public void removePlayer(UUID playerUUID) { playerUUIDs.remove(playerUUID); }
        public boolean hasPlayer(UUID playerUUID) { return playerUUIDs.contains(playerUUID); }
        public int getPlayerCount() { return playerUUIDs.size(); }
    }
}