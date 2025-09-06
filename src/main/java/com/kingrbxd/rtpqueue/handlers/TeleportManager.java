package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import io.papermc.lib.PaperLib;
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
 * Professional teleport manager with BetterRTP-inspired async teleportation
 */
public class TeleportManager {
    private final AdvancedRTPQueue plugin;
    private final Map<String, Queue<Location>> locationCache = new ConcurrentHashMap<>();
    private final Map<UUID, TeleportSession> activeSessions = new ConcurrentHashMap<>();
    private final Random random = ThreadLocalRandom.current();

    public TeleportManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        preloadLocations();
    }

    /**
     * Start teleportation for matched players (BetterRTP inspired)
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
     * Execute teleportation with async location finding (BetterRTP style)
     */
    private void executeTeleport(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        if (!isSessionValid(session)) return;

        // Try cached location first
        Location cachedLocation = getCachedLocation(worldSettings.getName());
        if (cachedLocation != null) {
            teleportPlayersAsync(session, cachedLocation);
            return;
        }

        // Find location asynchronously
        findLocationAsync(session, worldSettings);
    }

    private void findLocationAsync(TeleportSession session, WorldManager.WorldSettings worldSettings) {
        new BukkitRunnable() {
            private int attempts = 0;
            private final int maxAttempts = worldSettings.getMaxTeleportAttempts();

            @Override
            public void run() {
                if (!isSessionValid(session)) {
                    cancel();
                    return;
                }

                if (attempts >= maxAttempts) {
                    handleTeleportFailure(session);
                    cancel();
                    return;
                }

                Location location = generateSafeLocation(worldSettings);
                if (location != null) {
                    // Schedule teleport on main thread
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            teleportPlayersAsync(session, location);
                        }
                    }.runTask(plugin);
                    cancel();
                    return;
                }

                attempts++;
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 2L);
    }

    /**
     * Generate safe location with comprehensive safety checks
     */
    private Location generateSafeLocation(WorldManager.WorldSettings worldSettings) {
        World world = worldSettings.getBukkitWorld();
        if (world == null) return null;

        // Generate random coordinates
        int x = random.nextInt(worldSettings.getMaxX() - worldSettings.getMinX() + 1) + worldSettings.getMinX();
        int z = random.nextInt(worldSettings.getMaxZ() - worldSettings.getMinZ() + 1) + worldSettings.getMinZ();

        // Check chunk loaded
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;

        int y;
        try {
            y = world.getHighestBlockYAt(x, z);
        } catch (Exception e) {
            return null;
        }

        // Apply Y limits
        int minY = plugin.getConfigManager().getInt("teleport.min-y", 60);
        int maxY = plugin.getConfigManager().getInt("teleport.max-y", 250);

        y = Math.max(minY, Math.min(maxY, y));

        Location location = new Location(world, x + 0.5, y + 1, z + 0.5);

        return isSafeLocation(location) ? location : null;
    }

    /**
     * Comprehensive safety check
     */
    private boolean isSafeLocation(Location location) {
        if (location == null || location.getWorld() == null) return false;

        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        try {
            // Check blocks
            Block ground = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);

            // Must have solid ground and air space
            if (!ground.getType().isSolid() || !feet.getType().isAir() || !head.getType().isAir()) {
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

            // Check claim protection
            if (plugin.getClaimProtectionHandler() != null &&
                    plugin.getClaimProtectionHandler().isLocationClaimed(location)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Async teleportation using PaperLib (BetterRTP style)
     */
    private void teleportPlayersAsync(TeleportSession session, Location location) {
        List<Player> validPlayers = getValidPlayers(session);
        if (validPlayers.isEmpty()) return;

        for (Player player : validPlayers) {
            // Remove from queue
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
                            plugin.getLogger().info("Teleported " + player.getName() + " to " +
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
            if (isSafeLocation(location)) {
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
        int maxCached = plugin.getConfigManager().getInt("teleport.max-cached-locations", 10);
        Queue<Location> cached = locationCache.computeIfAbsent(worldName, k -> new LinkedList<>());

        if (cached.size() >= maxCached) return;

        WorldManager.WorldSettings worldSettings = plugin.getWorldManager().getWorldSettings(worldName);
        if (worldSettings == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < 5 && cached.size() < maxCached; i++) {
                    Location location = generateSafeLocation(worldSettings);
                    if (location != null) {
                        cached.offer(location);
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void handleTeleportFailure(TeleportSession session) {
        List<Player> players = getValidPlayers(session);
        for (Player player : players) {
            MessageUtil.sendMessage(player, "teleport-failed");
            MessageUtil.playSound(player, "error");
        }
        cancelSession(session, "no-safe-locations");
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