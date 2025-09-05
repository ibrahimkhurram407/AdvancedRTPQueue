package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class TeleportHandler {
    private static final Random random = new Random();

    public static void tryTeleport(Player player) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        QueueHandler queueHandler = plugin.getQueueHandler();
        CooldownManager cooldownManager = plugin.getCooldownManager();

        Player opponent = queueHandler.findOpponent(player);

        if (opponent == null) {
            return; // No opponent found, do nothing.
        }

        // Ensure both players are online before teleporting
        if (!player.isOnline() || !opponent.isOnline()) {
            queueHandler.removeFromQueue(player);
            queueHandler.removeFromQueue(opponent);
            return;
        }

        // Start pre-teleport cooldown
        cooldownManager.startPreTeleportCountdown(player, opponent);
    }

    public static void teleportPlayers(Player player1, Player player2) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        QueueHandler queueHandler = plugin.getQueueHandler();
        WorldManager worldManager = plugin.getWorldManager();
        ClaimProtectionHandler claimProtectionHandler = plugin.getClaimProtectionHandler();

        // Remove from queue
        queueHandler.removeFromQueue(player1);
        queueHandler.removeFromQueue(player2);

        // Get the world settings (using player1's current world or default)
        WorldManager.WorldSettings settings = worldManager.getWorldSettings(player1.getWorld().getName());
        World world = settings.getBukkitWorld();

        if (world == null) {
            MessageUtil.sendMessage(player1, plugin.getConfig().getString("messages.invalid-world"));
            MessageUtil.sendMessage(player2, plugin.getConfig().getString("messages.invalid-world"));
            return;
        }

        // Get teleport settings
        int minX = settings.getMinX();
        int maxX = settings.getMaxX();
        int minZ = settings.getMinZ();
        int maxZ = settings.getMaxZ();
        boolean safeTeleport = settings.isSafeTeleport();
        int maxAttempts = settings.getMaxTeleportAttempts();

        // Get unsafe blocks
        Set<Material> unsafeBlocks = new HashSet<>();
        for (String block : plugin.getConfig().getStringList("teleport.unsafe-blocks")) {
            try {
                unsafeBlocks.add(Material.valueOf(block.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid block in unsafe-blocks: " + block);
            }
        }

        // Find a safe location
        Location teleportLocation = findSafeLocation(world, minX, maxX, minZ, maxZ,
                unsafeBlocks, maxAttempts, claimProtectionHandler, safeTeleport);

        if (teleportLocation == null) {
            MessageUtil.sendMessage(player1, plugin.getConfig().getString("messages.teleport-failed"));
            MessageUtil.sendMessage(player2, plugin.getConfig().getString("messages.teleport-failed"));
            return;
        }

        // Send messages before teleporting
        String worldMessage = plugin.getConfig().getString("messages.world-teleport", "&#00ccff🌎 &bTeleporting to {world}...")
                .replace("{world}", settings.getDisplayName());

        MessageUtil.sendTitle(player1, plugin.getConfig().getString("messages.teleporting-title"),
                plugin.getConfig().getString("messages.teleporting-subtitle"));
        MessageUtil.sendTitle(player2, plugin.getConfig().getString("messages.teleporting-title"),
                plugin.getConfig().getString("messages.teleporting-subtitle"));

        MessageUtil.sendMessage(player1, worldMessage);
        MessageUtil.sendMessage(player2, worldMessage);

        MessageUtil.playSound(player1, plugin.getConfig().getString("sounds.teleport"));
        MessageUtil.playSound(player2, plugin.getConfig().getString("sounds.teleport"));

        // Teleport players
        player1.teleport(teleportLocation);
        player2.teleport(teleportLocation);

        MessageUtil.sendMessage(player1, plugin.getConfig().getString("messages.teleported"));
        MessageUtil.sendMessage(player2, plugin.getConfig().getString("messages.teleported"));
    }

    private static Location findSafeLocation(World world, int minX, int maxX, int minZ, int maxZ,
                                             Set<Material> unsafeBlocks, int maxAttempts,
                                             ClaimProtectionHandler claimProtectionHandler, boolean safeTeleport) {
        if (!safeTeleport) {
            return getRandomLocation(world, minX, maxX, minZ, maxZ, claimProtectionHandler);
        }

        for (int i = 0; i < maxAttempts; i++) {
            int x = random.nextInt(maxX - minX + 1) + minX;
            int z = random.nextInt(maxZ - minZ + 1) + minZ;
            int y = world.getHighestBlockYAt(x, z);
            Location location = new Location(world, x + 0.5, y + 1, z + 0.5);

            // Check if location is in a claim
            if (claimProtectionHandler.isLocationClaimed(location)) {
                continue;
            }

            // Ensure the block players land on is safe
            Material blockBelow = world.getBlockAt(x, y, z).getType();
            if (!unsafeBlocks.contains(blockBelow) && blockBelow.isSolid()) {
                // Check blocks at player height for safety
                Material blockAt = world.getBlockAt(x, y + 1, z).getType();
                Material blockAbove = world.getBlockAt(x, y + 2, z).getType();

                if (blockAt.isAir() && blockAbove.isAir()) {
                    return location;
                }
            }
        }
        return null; // No safe location found
    }

    private static Location getRandomLocation(World world, int minX, int maxX, int minZ, int maxZ,
                                              ClaimProtectionHandler claimProtectionHandler) {
        for (int i = 0; i < 10; i++) { // Try a few times to find unclaimed land
            int x = random.nextInt(maxX - minX + 1) + minX;
            int z = random.nextInt(maxZ - minZ + 1) + minZ;
            int y = world.getHighestBlockYAt(x, z);
            Location location = new Location(world, x + 0.5, y + 1, z + 0.5);

            // Check if location is in a claim
            if (!claimProtectionHandler.isLocationClaimed(location)) {
                return location;
            }
        }

        // If all attempts find claimed land, just return a random location
        int x = random.nextInt(maxX - minX + 1) + minX;
        int z = random.nextInt(maxZ - minZ + 1) + minZ;
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }
}