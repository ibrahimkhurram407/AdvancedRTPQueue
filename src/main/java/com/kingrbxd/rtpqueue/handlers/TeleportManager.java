package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import com.kingrbxd.rtpqueue.utils.ParticleUtil;
import com.kingrbxd.rtpqueue.utils.TeleportEffects;
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
 * TeleportManager
 *
 * - Uses Bukkit world names consistently for caching/teleportation
 * - Keeps players marked in activeSessions until teleport fully completes
 * - Safer async location search with timeout and sensible fallbacks
 * - Additional debug/logging to help diagnose "no safe teleport location" situations
 */
public class TeleportManager {
    private final AdvancedRTPQueue plugin;
    private final Map<String, Queue<Location>> locationCache = new ConcurrentHashMap<>(); // key: bukkit world name
    private final Map<UUID, TeleportSession> activeSessions = new ConcurrentHashMap<>();   // playerUuid -> session
    private final Set<String> activeSearches = Collections.synchronizedSet(new HashSet<>()); // bukkit world names
    private final Random random = ThreadLocalRandom.current();

    public TeleportManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        preloadLocations();
    }

    /**
     * Start teleportation for matched players
     * worldKey is the configured key (e.g. "nether", "end" or default world key)
     */
    public void startTeleportation(List<Player> players, String worldKey) {
        if (players == null || players.isEmpty() || worldKey == null) return;

        TeleportSession session = new TeleportSession(UUID.randomUUID().toString(), players, worldKey);

        // Register session for each player (marks them as "in pre-teleport" so they cannot re-join)
        for (Player player : players) {
            activeSessions.put(player.getUniqueId(), session);
        }

        WorldManager.WorldSettings worldSettings = plugin.getWorldManager().getWorldSettings(worldKey);
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
                    "world", plugin.getWorldManager().getDisplayName(session.getWorldKey())
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
                    this.cancel();
                    return;
                }

                if (timeLeft > 0) {
                    sendCountdownMessages(session, timeLeft);
                    timeLeft--;
                } else {
                    executeTeleport(session, worldSettings);
                    this.cancel();
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
            // show subtle start effect each second so players see something during countdown
            TeleportEffects.playStart(plugin, player.getLocation());
        }
    }

    /**
     * Execute teleportation with improved location finding
     */
    private void executeTeleport(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        if (!isSessionValid(session)) return;
        if (worldSettings == null) {
            cancelSession(session, "invalid-world");
            return;
        }

        String bukkitWorldName = worldSettings.getBukkitWorldName();

        // Try cached location first (cache keyed by bukkit world name)
        Location cachedLocation = getCachedLocation(bukkitWorldName);
        if (cachedLocation != null) {
            teleportPlayersAsync(session, cachedLocation);
            return;
        }

        // Find location asynchronously
        findLocationAsync(session, worldSettings);
    }

    /**
     * Improved async location finding
     */
    private void findLocationAsync(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        if (worldSettings == null) {
            handleSearchFailed(session, null);
            return;
        }

        String bukkitWorldName = worldSettings.getBukkitWorldName();

        if (activeSearches.contains(bukkitWorldName)) {
            // Wait for existing search to complete and use cached result if available
            waitForLocationSearch(session, worldSettings);
            return;
        }

        activeSearches.add(bukkitWorldName);

        new BukkitRunnable() {
            private int attempts = 0;
            private final int maxAttempts = Math.max(1, worldSettings.getMaxTeleportAttempts());
            private final long startTime = System.currentTimeMillis();
            private final long timeout = plugin.getConfigManager().getInt("teleport.search-timeout", 30) * 1000L;

            @Override
            public void run() {
                try {
                    if (!isSessionValid(session)) {
                        cleanup();
                        return;
                    }

                    // check timeout
                    if (System.currentTimeMillis() - startTime > timeout) {
                        handleSearchTimeout(session, worldSettings);
                        cleanup();
                        return;
                    }

                    // check attempts
                    if (attempts >= maxAttempts) {
                        handleSearchFailed(session, worldSettings);
                        cleanup();
                        return;
                    }

                    // try multiple locations per tick for better chance
                    for (int i = 0; i < 5 && attempts < maxAttempts; i++) {
                        Location location = generateSafeLocationSync(worldSettings);
                        attempts++;
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
                    }
                } catch (Exception e) {
                    if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                        plugin.getLogger().warning("Error in location search: " + e.getMessage());
                    }
                    cleanup();
                }
            }

            private void cleanup() {
                activeSearches.remove(bukkitWorldName);
                this.cancel();
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 1L);
    }

    /**
     * Generate a candidate safe location synchronously (called from async search thread but accesses world sync APIs carefully).
     */
    private Location generateSafeLocationSync(WorldManager.WorldSettings worldSettings) {
        World world = worldSettings.getBukkitWorld();
        if (world == null) return null;

        int minX = Math.min(worldSettings.getMinX(), worldSettings.getMaxX());
        int maxX = Math.max(worldSettings.getMinX(), worldSettings.getMaxX());
        int minZ = Math.min(worldSettings.getMinZ(), worldSettings.getMaxZ());
        int maxZ = Math.max(worldSettings.getMinZ(), worldSettings.getMaxZ());

        if (maxX - minX < 0 || maxZ - minZ < 0) return null;

        int x = random.nextInt(maxX - minX + 1) + minX;
        int z = random.nextInt(maxZ - minZ + 1) + minZ;

        // If safe-teleport is OFF, don't require loaded chunks or ground.
        if (!plugin.getConfigManager().getBoolean("teleport.safe-teleport", true)) {
            int minY = plugin.getConfigManager().getInt("teleport.min-y", 60);
            int maxY = plugin.getConfigManager().getInt("teleport.max-y", 250);

            // Try to pick a sensible Y: use spawn Y if present, otherwise minY.
            int spawnY = world.getSpawnLocation() != null ? world.getSpawnLocation().getBlockY() : minY;
            int y = Math.min(maxY, Math.max(minY, spawnY));

            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().info("Unsafe mode: using raw location " + x + "," + y + "," + z + " in " + world.getName());
            }
            return new Location(world, x + 0.5, y, z + 0.5);
        }

        // ---- original safe path (requires chunk + ground) ----
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }

        int y = findSafeY(world, x, z, worldSettings);
        if (y == -1) return null;

        Location location = new Location(world, x + 0.5, y + 1, z + 0.5);

        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Testing location: " + x + "," + y + "," + z + " in " + world.getName());
        }

        return isSafeLocationDetailed(location) ? location : null;
    }


    /**
     * Find safe Y coordinate using surface detection
     */
    private int findSafeY(World world, int x, int z, WorldManager.WorldSettings worldSettings) {
        int minY = plugin.getConfigManager().getInt("teleport.min-y", 60);
        int maxY = plugin.getConfigManager().getInt("teleport.max-y", 250);

        try {
            int highestY = world.getHighestBlockYAt(x, z);
            int startY = Math.min(maxY, highestY);

            // search downward from surface
            for (int y = startY; y >= minY; y--) {
                Block ground = world.getBlockAt(x, y, z);
                Block feet = world.getBlockAt(x, y + 1, z);
                Block head = world.getBlockAt(x, y + 2, z);

                if (ground.getType().isSolid() &&
                        feet.getType().isAir() &&
                        head.getType().isAir() &&
                        !isUnsafeBlock(ground.getType())) {
                    return y;
                }
            }

            // fallback: search upward from minY
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

        return -1;
    }

    /**
     * Detailed safety checks
     */
    private boolean isSafeLocationDetailed(Location location) {
        if (!plugin.getConfigManager().getBoolean("teleport.safe-teleport", true)) {
            return true;
        }
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
            Block ground = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);

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

            if (isUnsafeBlock(ground.getType())) {
                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().info("Location unsafe: unsafe ground block (" + ground.getType() + ")");
                }
                return false;
            }

            if (hasUnsafeSurroundings(world, x, y, z)) {
                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().info("Location unsafe: dangerous surroundings");
                }
                return false;
            }

            if (plugin.getConfigManager().getBoolean("claim-protection.enabled") &&
                    plugin.getClaimProtectionHandler() != null &&
                    plugin.getClaimProtectionHandler().isLocationClaimed(location)) {
                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().info("Location unsafe: claimed area");
                }
                return false;
            }

            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().info("Location SAFE: " + x + "," + y + "," + z + " in " + world.getName());
            }
            return true;
        } catch (Exception e) {
            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().warning("Error checking location safety: " + e.getMessage());
            }
            return false;
        }
    }

    private boolean isUnsafeBlock(Material material) {
        List<String> unsafeBlocks = plugin.getConfigManager().getStringList("teleport.unsafe-blocks");
        String materialName = material.name();

        for (String unsafeBlock : unsafeBlocks) {
            if (materialName.equalsIgnoreCase(unsafeBlock)) {
                return true;
            }
        }

        return material == Material.LAVA ||
                material == Material.MAGMA_BLOCK ||
                material == Material.FIRE ||
                material == Material.SOUL_FIRE ||
                material == Material.CAMPFIRE ||
                material == Material.SOUL_CAMPFIRE ||
                material == Material.CACTUS;
    }

    private boolean hasUnsafeSurroundings(World world, int x, int y, int z) {
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
            private final int maxWait = 100; // ~5 seconds

            @Override
            public void run() {
                if (worldSettings == null) {
                    handleSearchFailed(session, null);
                    this.cancel();
                    return;
                }

                String bukkitWorldName = worldSettings.getBukkitWorldName();
                if (!activeSearches.contains(bukkitWorldName)) {
                    Location location = getCachedLocation(bukkitWorldName);
                    if (location != null) {
                        teleportPlayersAsync(session, location);
                    } else {
                        handleSearchFailed(session, worldSettings);
                    }
                    this.cancel();
                    return;
                }

                if (waitTime >= maxWait) {
                    handleSearchTimeout(session, worldSettings);
                    this.cancel();
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
        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("handleSearchFailed called for worldKey=" + (worldSettings != null ? worldSettings.getWorldKey() : "null"));
        }

        if (plugin.getConfigManager().getBoolean("teleport.allow-fallback-locations")) {
            Location fallback = getFallbackLocation(worldSettings);
            if (fallback != null) {
                teleportPlayersAsync(session, fallback);
                return;
            }
        }

        // If configured, force spawn fallback even if not fully 'safe'
        if (plugin.getConfigManager().getBoolean("teleport.force-spawn-on-failure", false)) {
            Location forced = getFallbackLocationForce(worldSettings);
            if (forced != null) {
                teleportPlayersAsync(session, forced);
                return;
            }
        }

        cancelSession(session, "no-safe-locations");
    }

    /**
     * Get fallback location (world spawn) — returns spawn + 1 if safe
     */
    private Location getFallbackLocation(WorldManager.WorldSettings worldSettings) {
        if (worldSettings == null) return null;
        World world = worldSettings.getBukkitWorld();
        if (world == null) {
            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().warning("getFallbackLocation: Bukkit world not loaded for " + worldSettings.getBukkitWorldName());
            }
            return null;
        }

        Location spawn = world.getSpawnLocation();
        if (spawn != null) {
            Location candidate = spawn.clone().add(0, 1, 0);
            if (isSafeLocationDetailed(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Force-return spawn even if some safety checks fail (final last-resort fallback).
     */
    private Location getFallbackLocationForce(WorldManager.WorldSettings worldSettings) {
        if (worldSettings == null) return null;
        World world = worldSettings.getBukkitWorld();
        if (world == null) return null;

        Location spawn = world.getSpawnLocation();
        if (spawn == null) return null;

        Location candidate = spawn.clone().add(0, 1, 0);
        // Return spawn even if not fully passing isSafeLocationDetailed (log when debug enabled)
        if (!isSafeLocationDetailed(candidate) && plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().warning("getFallbackLocationForce: returning spawn despite failing safety checks in world " + world.getName());
        }
        return candidate;
    }

    /**
     * Async teleportation using PaperLib. Important: DO NOT remove players from activeSessions here.
     * Keep them marked as "in session" until the teleport completes to prevent re-joining the queue.
     */
    private void teleportPlayersAsync(TeleportSession session, Location location) {
        List<Player> validPlayers = getValidPlayers(session);
        if (validPlayers.isEmpty()) return;

        if (location == null || location.getWorld() == null) {
            cancelSession(session, "no-safe-locations");
            return;
        }

        // Cache location for this world (use bukkit world name)
        cacheLocationForWorld(location.getWorld().getName());

        for (Player player : validPlayers) {
            // Remove from queue now (we don't want them to remain in queue while teleporting)
            plugin.getQueueHandler().removeFromQueue(player);

            Map<String, String> placeholders = Map.of(
                    "world", plugin.getWorldManager().getDisplayName(session.getWorldKey())
            );

            // Notify player pre-teleport and show starting particles/sound
            MessageUtil.sendMessage(player, "teleporting", placeholders);
            MessageUtil.playSound(player, "teleport-success");
            ParticleUtil.spawnConfiguredParticle(plugin, player.getLocation(), "teleport-start");

            // Async teleport; when finished execute post-teleport actions on main thread
            PaperLib.teleportAsync(player, location).thenRun(() -> {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            // Post-teleport effects
                            MessageUtil.sendMessage(player, "teleported");
                            MessageUtil.sendTitle(player, "teleported", "teleported");
                            ParticleUtil.spawnConfiguredParticle(plugin, location, "teleport-success");
                            MessageUtil.playSound(player, "teleport-success");

                            // Apply cooldown
                            plugin.getCooldownManager().setPostTeleportCooldown(player);

                            // Remove player from active session map now that teleport fully completed
                            activeSessions.remove(player.getUniqueId());
                            session.removePlayer(player.getUniqueId());

                            // Logging
                            if (plugin.getConfigManager().getBoolean("advanced.log-teleports")) {
                                plugin.getLogger().info("Successfully teleported " + player.getName() + " to " +
                                        location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                                        " in " + location.getWorld().getName());
                            }
                        } catch (Exception e) {
                            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                                plugin.getLogger().warning("Error finishing teleport for " + player.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                }.runTask(plugin);
            });
        }
    }

    private Location getCachedLocation(String bukkitWorldName) {
        Queue<Location> cached = locationCache.get(bukkitWorldName);
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
                // iterate configured valid world keys and convert to bukkit names
                for (String worldKey : plugin.getWorldManager().getValidWorldNames()) {
                    WorldManager.WorldSettings ws = plugin.getWorldManager().getWorldSettings(worldKey);
                    if (ws == null) continue;
                    cacheLocationForWorld(ws.getBukkitWorldName());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void cacheLocationForWorld(String bukkitWorldName) {
        if (bukkitWorldName == null || activeSearches.contains(bukkitWorldName)) return;

        int maxCached = plugin.getConfigManager().getInt("teleport.max-cached-locations", 10);
        Queue<Location> cached = locationCache.computeIfAbsent(bukkitWorldName, k -> new LinkedList<>());

        if (cached.size() >= maxCached) return;

        // create async task to generate a few safe locations
        new BukkitRunnable() {
            @Override
            public void run() {
                World w = Bukkit.getWorld(bukkitWorldName);
                if (w == null) return;
                // try to get worldSettings by bukkit name (fallback to any matching config entry)
                WorldManager.WorldSettings worldSettings = null;
                // find corresponding config key
                for (String key : plugin.getWorldManager().getValidWorldNames()) {
                    WorldManager.WorldSettings ws = plugin.getWorldManager().getWorldSettings(key);
                    if (ws != null && bukkitWorldName.equalsIgnoreCase(ws.getBukkitWorldName())) {
                        worldSettings = ws;
                        break;
                    }
                }
                if (worldSettings == null) return;

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
            // send specific cancel message if available or fallback to generic
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
        return player != null && activeSessions.containsKey(player.getUniqueId());
    }

    /**
     * Return the configured world key for a player's active session, or null if none.
     */
    public String getActiveSessionWorld(Player player) {
        if (player == null) return null;
        TeleportSession s = activeSessions.get(player.getUniqueId());
        return s != null ? s.getWorldKey() : null;
    }

    public void shutdown() {
        for (TeleportSession session : new ArrayList<>(activeSessions.values())) {
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
        private final String worldKey; // configured key (e.g. "nether", "end")
        private BukkitTask countdownTask;

        public TeleportSession(String sessionId, List<Player> players, String worldKey) {
            this.sessionId = sessionId;
            this.worldKey = worldKey;
            this.playerUUIDs = new HashSet<>();
            for (Player player : players) {
                this.playerUUIDs.add(player.getUniqueId());
            }
        }

        public String getSessionId() { return sessionId; }
        public Set<UUID> getPlayerUUIDs() { return new HashSet<>(playerUUIDs); }
        public String getWorldKey() { return worldKey; }
        public BukkitTask getCountdownTask() { return countdownTask; }
        public void setCountdownTask(BukkitTask countdownTask) { this.countdownTask = countdownTask; }
        public void removePlayer(UUID playerUUID) { playerUUIDs.remove(playerUUID); }
    }
}