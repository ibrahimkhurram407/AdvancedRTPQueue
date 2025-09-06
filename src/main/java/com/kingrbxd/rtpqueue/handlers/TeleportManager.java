package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fixed TeleportManager with proper safe location finding
 */
public class TeleportManager {
    private final AdvancedRTPQueue plugin;
    private final Map<String, Queue<Location>> locationCache = new ConcurrentHashMap<>();
    private final Map<UUID, TeleportSession> activeSessions = new ConcurrentHashMap<>();
    private final Set<String> activeSearches = Collections.synchronizedSet(new HashSet<>());
    private final Random random = ThreadLocalRandom.current();

    public TeleportManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        preloadLocations();
    }

    /**
     * Start teleportation for matched players
     */
    public void startTeleportation(List<Player> players, String worldName) {
        if (players == null || players.isEmpty()) return;

        TeleportSession session = new TeleportSession(UUID.randomUUID().toString(), players, worldName);

        // Register session
        for (Player player : players) {
            activeSessions.put(player.getUniqueId(), session);
        }

        WorldManager.WorldSettings worldSettings = plugin.getWorldManager().getWorldSettings(worldName);
        if (worldSettings == null) {
            cancelSession(session, "invalid-world");
            return;
        }

        notifyPlayersMatchFound(session);
        startCountdown(session, worldSettings);
    }

    private void notifyPlayersMatchFound(TeleportSession session) {
        List<Player> players = getValidPlayers(session);
        if (players.isEmpty()) return;

        int countdown = plugin.getConfigManager().getInt("cooldowns.pre-teleport", 5);

        for (Player player : players) {
            Map<String, String> placeholders = Map.of(
                    "time", String.valueOf(countdown),
                    "players", String.valueOf(players.size()),
                    "world", plugin.getWorldManager().getDisplayName(session.getWorldName())
            );

            MessageUtil.sendMessage(player, "match-found", placeholders);
            MessageUtil.sendTitle(player, "match-found", "match-found", placeholders);
            MessageUtil.playSound(player, "match-found");
        }
    }

    private void startCountdown(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        int countdown = plugin.getConfigManager().getInt("cooldowns.pre-teleport", 5);

        if (countdown <= 0) {
            executeTeleport(session, worldSettings);
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            private int timeLeft = countdown;

            @Override
            public void run() {
                if (!isSessionValid(session)) {
                    cancel();
                    return;
                }

                if (timeLeft > 0) {
                    sendCountdownMessages(session, timeLeft);
                    timeLeft--;
                } else {
                    executeTeleport(session, worldSettings);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        session.setCountdownTask(task);
    }

    private void sendCountdownMessages(TeleportSession session, int timeLeft) {
        List<Player> players = getValidPlayers(session);
        Map<String, String> placeholders = Map.of("time", String.valueOf(timeLeft));

        for (Player player : players) {
            MessageUtil.sendActionBar(player, "countdown", placeholders);
            MessageUtil.sendTitle(player, "countdown", "countdown", placeholders);
            MessageUtil.playSound(player, "countdown");
        }
    }

    /**
     * Execute teleportation with improved location finding
     */
    private void executeTeleport(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        if (!isSessionValid(session)) return;

        // Try cached location first
        Location cachedLocation = getCachedLocation(worldSettings.getName());
        if (cachedLocation != null) {
            teleportPlayersAsync(session, cachedLocation);
            return;
        }

        // Find location with better logic
        findLocationAsync(session, worldSettings);
    }

    /**
     * FIXED: Improved async location finding
     */
    private void findLocationAsync(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        String worldName = worldSettings.getName();

        if (activeSearches.contains(worldName)) {
            // Wait for existing search
            waitForLocationSearch(session, worldSettings);
            return;
        }

        activeSearches.add(worldName);

        new BukkitRunnable() {
            private int attempts = 0;
            private final int maxAttempts = worldSettings.getMaxTeleportAttempts();
            private final long startTime = System.currentTimeMillis();
            private final long timeout = plugin.getConfigManager().getInt("teleport.search-timeout", 30) * 1000L;

            @Override
            public void run() {
                try {
                    if (!isSessionValid(session)) {
                        cleanup();
                        return;
                    }

                    // Check timeout
                    if (System.currentTimeMillis() - startTime > timeout) {
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

                    // FIXED: Try multiple locations per tick for better success rate
                    for (int i = 0; i < 5 && attempts < maxAttempts; i++) {
                        Location location = generateSafeLocationSync(worldSettings);
                        if (location != null) {
                            // Found safe location, teleport on main thread
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    teleportPlayersAsync(session, location);
                                }
                            }.runTask(plugin);
                            cleanup();
                            return;
                        }
                        attempts++;
                    }

                } catch (Exception e) {
                    if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                        plugin.getLogger().warning("Error in location search: " + e.getMessage());
                    }
                    cleanup();
                }
            }

            private void cleanup() {
                activeSearches.remove(worldName);
                cancel();
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 1L); // Faster search (every tick)
    }

    /**
     * FIXED: Improved safe location generation with better Y-level handling
     */
    private Location generateSafeLocationSync(WorldManager.WorldSettings worldSettings) {
        World world = worldSettings.getBukkitWorld();
        if (world == null) return null;

        // Generate random coordinates
        int x = random.nextInt(worldSettings.getMaxX() - worldSettings.getMinX() + 1) + worldSettings.getMinX();
        int z = random.nextInt(worldSettings.getMaxZ() - worldSettings.getMinZ() + 1) + worldSettings.getMinZ();

        // Ensure chunk is loaded (synchronous check)
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }

        // FIXED: Better Y-level calculation
        int y = findSafeY(world, x, z, worldSettings);
        if (y == -1) return null;

        Location location = new Location(world, x + 0.5, y + 1, z + 0.5);

        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Testing location: " + x + "," + y + "," + z + " in " + world.getName());
        }

        return isSafeLocationDetailed(location) ? location : null;
    }

    /**
     * FIXED: Find safe Y coordinate with proper surface detection
     */
    private int findSafeY(World world, int x, int z, WorldManager.WorldSettings worldSettings) {
        int minY = plugin.getConfigManager().getInt("teleport.min-y", 60);
        int maxY = plugin.getConfigManager().getInt("teleport.max-y", 250);

        try {
            // Start from highest block and work down
            int highestY = world.getHighestBlockYAt(x, z);

            // Clamp to our limits
            int startY = Math.min(maxY, highestY);

            // Search downward for safe spot
            for (int y = startY; y >= minY; y--) {
                Block ground = world.getBlockAt(x, y, z);
                Block feet = world.getBlockAt(x, y + 1, z);
                Block head = world.getBlockAt(x, y + 2, z);

                // Check if this is a valid surface
                if (ground.getType().isSolid() &&
                        feet.getType().isAir() &&
                        head.getType().isAir() &&
                        !isUnsafeBlock(ground.getType())) {
                    return y;
                }
            }

            // If no surface found, try going up from minY
            for (int y = minY; y <= startY; y++) {
                Block ground = world.getBlockAt(x, y - 1, z);
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);

                if (ground.getType().isSolid() &&
                        feet.getType().isAir() &&
                        head.getType().isAir() &&
                        !isUnsafeBlock(ground.getType())) {
                    return y - 1;
                }
            }

        } catch (Exception e) {
            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().warning("Error finding Y at " + x + "," + z + ": " + e.getMessage());
            }
        }

        return -1; // No safe Y found
    }

    /**
     * FIXED: More detailed safety check with better logging
     */
    private boolean isSafeLocationDetailed(Location location) {
        if (location == null || location.getWorld() == null) {
            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().info("Location check failed: null location or world");
            }
            return false;
        }

        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        try {
            // Check blocks
            Block ground = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);

            // Basic safety checks
            if (!ground.getType().isSolid()) {
                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().info("Location unsafe: no solid ground (" + ground.getType() + ")");
                }
                return false;
            }

            if (!feet.getType().isAir()) {
                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().info("Location unsafe: feet not air (" + feet.getType() + ")");
                }
                return false;
            }

            if (!head.getType().isAir()) {
                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().info("Location unsafe: head not air (" + head.getType() + ")");
                }
                return false;
            }

            // Check unsafe blocks
            if (isUnsafeBlock(ground.getType())) {
                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().info("Location unsafe: unsafe ground block (" + ground.getType() + ")");
                }
                return false;
            }

            // Check surrounding for lava/water
            if (hasUnsafeSurroundings(world, x, y, z)) {
                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().info("Location unsafe: dangerous surroundings");
                }
                return false;
            }

            // Check claim protection (only if enabled)
            if (plugin.getConfigManager().getBoolean("claim-protection.enabled") &&
                    plugin.getClaimProtectionHandler() != null &&
                    plugin.getClaimProtectionHandler().isLocationClaimed(location)) {
                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().info("Location unsafe: claimed area");
                }
                return false;
            }

            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().info("Location SAFE: " + x + "," + y + "," + z);
            }
            return true;

        } catch (Exception e) {
            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().warning("Error checking location safety: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Check if material is unsafe for teleportation
     */
    private boolean isUnsafeBlock(Material material) {
        List<String> unsafeBlocks = plugin.getConfigManager().getStringList("teleport.unsafe-blocks");
        String materialName = material.name();

        for (String unsafeBlock : unsafeBlocks) {
            if (materialName.equalsIgnoreCase(unsafeBlock)) {
                return true;
            }
        }

        // Additional hardcoded unsafe blocks
        return material == Material.LAVA ||
                material == Material.MAGMA_BLOCK ||
                material == Material.FIRE ||
                material == Material.SOUL_FIRE ||
                material == Material.CAMPFIRE ||
                material == Material.SOUL_CAMPFIRE ||
                material == Material.CACTUS;
    }

    /**
     * Check for dangerous blocks in surrounding area
     */
    private boolean hasUnsafeSurroundings(World world, int x, int y, int z) {
        // Check 3x3 area around the location
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    Material type = block.getType();

                    if (type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void waitForLocationSearch(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        new BukkitRunnable() {
            private int waitTime = 0;
            private final int maxWait = 100; // 5 seconds

            @Override
            public void run() {
                if (!activeSearches.contains(worldSettings.getName())) {
                    // Search completed, try to get location
                    Location location = getCachedLocation(worldSettings.getName());
                    if (location != null) {
                        teleportPlayersAsync(session, location);
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
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void handleSearchTimeout(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        if (plugin.getConfigManager().getBoolean("teleport.allow-fallback-locations")) {
            Location fallback = getFallbackLocation(worldSettings);
            if (fallback != null) {
                teleportPlayersAsync(session, fallback);
                return;
            }
        }
        cancelSession(session, "timeout");
    }

    private void handleSearchFailed(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        if (plugin.getConfigManager().getBoolean("teleport.allow-fallback-locations")) {
            Location fallback = getFallbackLocation(worldSettings);
            if (fallback != null) {
                teleportPlayersAsync(session, fallback);
                return;
            }
        }
        cancelSession(session, "no-safe-locations");
    }

    /**
     * Get fallback location (world spawn)
     */
    private Location getFallbackLocation(WorldManager.WorldSettings worldSettings) {
        World world = worldSettings.getBukkitWorld();
        if (world == null) return null;

        Location spawn = world.getSpawnLocation();
        if (spawn != null) {
            // Make sure spawn is safe
            if (isSafeLocationDetailed(spawn.clone().add(0, 1, 0))) {
                return spawn.clone().add(0, 1, 0);
            }
        }
        return null;
    }

    /**
     * Async teleportation using PaperLib
     */
    private void teleportPlayersAsync(TeleportSession session, Location location) {
        List<Player> validPlayers = getValidPlayers(session);
        if (validPlayers.isEmpty()) return;

        for (Player player : validPlayers) {
            // Remove from queue and session
            plugin.getQueueHandler().removeFromQueue(player);
            activeSessions.remove(player.getUniqueId());

            // Send pre-teleport messages
            Map<String, String> placeholders = Map.of(
                    "world", plugin.getWorldManager().getDisplayName(location.getWorld().getName())
            );

            MessageUtil.sendMessage(player, "teleporting", placeholders);
            MessageUtil.playSound(player, "teleport-success");
            MessageUtil.spawnParticles(player, "teleport-start");

            // Async teleport using PaperLib
            PaperLib.teleportAsync(player, location).thenRun(() -> {
                // Post-teleport effects (main thread)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        MessageUtil.sendMessage(player, "teleported", placeholders);
                        MessageUtil.sendTitle(player, "teleported", "teleported");
                        MessageUtil.spawnParticles(player, "teleport-success");

                        // Apply cooldown
                        plugin.getCooldownManager().setPostTeleportCooldown(player);

                        // Log if enabled
                        if (plugin.getConfigManager().getBoolean("advanced.log-teleports")) {
                            plugin.getLogger().info("Successfully teleported " + player.getName() + " to " +
                                    location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                                    " in " + location.getWorld().getName());
                        }
                    }
                }.runTask(plugin);
            });
        }

        // Cache additional locations for this world
        cacheLocationForWorld(location.getWorld().getName());
    }

    private Location getCachedLocation(String worldName) {
        Queue<Location> cached = locationCache.get(worldName);
        if (cached != null && !cached.isEmpty()) {
            Location location = cached.poll();
            if (isSafeLocationDetailed(location)) {
                return location;
            }
        }
        return null;
    }

    private void preloadLocations() {
        if (!plugin.getConfigManager().getBoolean("teleport.cache-safe-locations")) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (String worldName : plugin.getWorldManager().getValidWorldNames()) {
                    cacheLocationForWorld(worldName);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void cacheLocationForWorld(String worldName) {
        if (activeSearches.contains(worldName)) return;

        int maxCached = plugin.getConfigManager().getInt("teleport.max-cached-locations", 10);
        Queue<Location> cached = locationCache.computeIfAbsent(worldName, k -> new LinkedList<>());

        if (cached.size() >= maxCached) return;

        WorldManager.WorldSettings worldSettings = plugin.getWorldManager().getWorldSettings(worldName);
        if (worldSettings == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < 3 && cached.size() < maxCached; i++) {
                    Location location = generateSafeLocationSync(worldSettings);
                    if (location != null) {
                        cached.offer(location);
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void cancelSession(TeleportSession session, String reason) {
        if (session == null) return;

        List<Player> players = getValidPlayers(session);

        if (session.getCountdownTask() != null) {
            session.getCountdownTask().cancel();
        }

        for (UUID playerUUID : session.getPlayerUUIDs()) {
            activeSessions.remove(playerUUID);
        }

        for (Player player : players) {
            MessageUtil.sendMessage(player, "cancelled-" + reason);
            MessageUtil.playSound(player, "teleport-cancelled");
        }
    }

    public void cancelPlayerSession(Player player, String reason) {
        TeleportSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        session.removePlayer(player.getUniqueId());
        activeSessions.remove(player.getUniqueId());

        if (session.getPlayerUUIDs().isEmpty()) {
            cancelSession(session, reason);
            return;
        }

        int requiredPlayers = plugin.getConfigManager().getInt("queue.required-players", 2);
        if (getValidPlayers(session).size() < requiredPlayers) {
            cancelSession(session, "not-enough");
        }
    }

    private boolean isSessionValid(TeleportSession session) {
        if (session == null) return false;

        List<Player> validPlayers = getValidPlayers(session);
        int requiredPlayers = plugin.getConfigManager().getInt("queue.required-players", 2);

        return validPlayers.size() >= requiredPlayers;
    }

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

    public boolean hasActiveSession(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public void shutdown() {
        for (TeleportSession session : activeSessions.values()) {
            cancelSession(session, "shutdown");
        }
        activeSessions.clear();
        locationCache.clear();
        activeSearches.clear();
    }

    /**
     * Teleport session data class
     */
    public static class TeleportSession {
        private final String sessionId;
        private final Set<UUID> playerUUIDs;
        private final String worldName;
        private BukkitTask countdownTask;

        public TeleportSession(String sessionId, List<Player> players, String worldName) {
            this.sessionId = sessionId;
            this.worldName = worldName;
            this.playerUUIDs = new HashSet<>();

            for (Player player : players) {
                this.playerUUIDs.add(player.getUniqueId());
            }
        }

        public String getSessionId() { return sessionId; }
        public Set<UUID> getPlayerUUIDs() { return new HashSet<>(playerUUIDs); }
        public String getWorldName() { return worldName; }
        public BukkitTask getCountdownTask() { return countdownTask; }
        public void setCountdownTask(BukkitTask countdownTask) { this.countdownTask = countdownTask; }
        public void removePlayer(UUID playerUUID) { playerUUIDs.remove(playerUUID); }
    }
}