package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Handles teleportation of matched players.
 */
public class TeleportHandler {
    private static final Map<UUID, Boolean> teleportPending = new HashMap<>();
    private static final Random random = new Random();

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

        // Prepare all players for teleport
        for (Player player : playersToTeleport) {
            // Mark player as pending teleport
            teleportPending.put(player.getUniqueId(), true);

            // Notify player that match was found
            String message = plugin.getConfig().getString("messages.opponent-found")
                    .replace("{time}", String.valueOf(preTeleportTime));
            MessageUtil.sendMessage(player, message);
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.opponent-found"));
        }

        // Start teleport countdown
        new BukkitRunnable() {
            @Override
            public void run() {
                teleportPlayers(playersToTeleport, worldSettings);
            }
        }.runTaskLater(plugin, preTeleportTime * 20L);
    }

    /**
     * Teleport a group of players to random locations in a world.
     *
     * @param players The players to teleport
     * @param worldSettings The world settings to use
     */
    private static void teleportPlayers(List<Player> players, WorldManager.WorldSettings worldSettings) {
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
                teleportPending.remove(player.getUniqueId());
                MessageUtil.sendMessage(player, "&#ff6600⚠ &cTeleport cancelled - player(s) left queue!");
            }
            return;
        }

        // Get the world
        World world = worldSettings.getBukkitWorld();
        if (world == null) {
            for (Player player : validPlayers) {
                teleportPending.remove(player.getUniqueId());
                MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.invalid-world"));
            }
            return;
        }

        // Notify about world before teleport
        String worldMessage = plugin.getConfig().getString("messages.world-teleport")
                .replace("{world}", worldSettings.getDisplayName());

        // Teleport each valid player to a random location
        for (Player player : validPlayers) {
            // Remove from queue and pending teleport
            queueHandler.removeFromQueue(player);
            teleportPending.remove(player.getUniqueId());

            // Send world message if it's not the player's current world
            if (!player.getWorld().getName().equals(world.getName())) {
                MessageUtil.sendMessage(player, worldMessage);
            }

            // Show teleport titles and play sound
            MessageUtil.sendTitle(player,
                    plugin.getConfig().getString("messages.teleporting-title"),
                    plugin.getConfig().getString("messages.teleporting-subtitle"));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.teleport"));

            // Find a safe location and teleport
            Location safeLocation = findSafeLocation(worldSettings);
            if (safeLocation != null) {
                player.teleport(safeLocation);
                MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.teleported"));
            } else {
                MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.teleport-failed"));
                MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
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

    /**
     * Find a safe location within the world boundaries.
     *
     * @param worldSettings The world settings
     * @return A safe location or null if none found
     */
    private static Location findSafeLocation(WorldManager.WorldSettings worldSettings) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        World world = worldSettings.getBukkitWorld();

        if (world == null) {
            return null;
        }

        int minX = worldSettings.getMinX();
        int maxX = worldSettings.getMaxX();
        int minZ = worldSettings.getMinZ();
        int maxZ = worldSettings.getMaxZ();
        boolean safeTeleport = worldSettings.isSafeTeleport();
        int maxAttempts = worldSettings.getMaxTeleportAttempts();

        // Get unsafe blocks from config
        List<String> unsafeBlockNames = plugin.getConfig().getStringList("teleport.unsafe-blocks");
        Set<Material> unsafeBlocks = new HashSet<>();

        for (String blockName : unsafeBlockNames) {
            try {
                Material material = Material.valueOf(blockName);
                unsafeBlocks.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in unsafe-blocks: " + blockName);
            }
        }

        // Try to find a safe location
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);

            // Get highest block at this x,z coordinate
            int y = world.getHighestBlockYAt(x, z);

            Location location = new Location(world, x + 0.5, y + 1, z + 0.5);

            // Skip if location is in claimed land
            if (plugin.getClaimProtectionHandler().isLocationClaimed(location)) {
                plugin.getLogger().info("Location at " + x + "," + y + "," + z + " is claimed, finding another...");
                continue;
            }

            // If safe teleport is disabled, return this location
            if (!safeTeleport) {
                return location;
            }

            // Check if location is safe
            if (isSafeLocation(location, unsafeBlocks)) {
                return location;
            }
        }

        // No safe location found
        return null;
    }

    /**
     * Check if a location is safe for teleportation.
     *
     * @param location The location to check
     * @param unsafeBlocks List of unsafe block types
     * @return True if the location is safe
     */
    private static boolean isSafeLocation(Location location, Set<Material> unsafeBlocks) {
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
}