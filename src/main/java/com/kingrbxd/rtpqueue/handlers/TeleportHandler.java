package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
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

        Player opponent = queueHandler.findOpponent(player);

        if (opponent == null) {
            return; // No opponent found, do nothing.
        }

        // ✅ Ensure both players are online before teleporting
        if (!player.isOnline() || !opponent.isOnline()) {
            queueHandler.removeFromQueue(player);
            queueHandler.removeFromQueue(opponent);
            return;
        }

        teleportPlayers(player, opponent);
    }

    public static void teleportPlayers(Player player1, Player player2) {
        AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
        QueueHandler queueHandler = plugin.getQueueHandler();

        queueHandler.removeFromQueue(player1);
        queueHandler.removeFromQueue(player2);

        String worldName = plugin.getConfig().getString("teleport.world", "world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            MessageUtil.sendMessage(player1, plugin.getConfig().getString("messages.invalid-world"));
            MessageUtil.sendMessage(player2, plugin.getConfig().getString("messages.invalid-world"));
            return;
        }

        int minX = plugin.getConfig().getInt("teleport.min-x");
        int maxX = plugin.getConfig().getInt("teleport.max-x");
        int minZ = plugin.getConfig().getInt("teleport.min-z");
        int maxZ = plugin.getConfig().getInt("teleport.max-z");
        boolean safeTeleport = plugin.getConfig().getBoolean("teleport.safe-teleport", true);
        int maxAttempts = plugin.getConfig().getInt("teleport.max-teleport-attempts", 10);

        Set<Material> unsafeBlocks = new HashSet<>();
        for (String block : plugin.getConfig().getStringList("teleport.unsafe-blocks")) {
            try {
                unsafeBlocks.add(Material.valueOf(block.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid block in unsafe-blocks: " + block);
            }
        }

        Location teleportLocation = safeTeleport
                ? findSafeLocation(world, minX, maxX, minZ, maxZ, unsafeBlocks, maxAttempts)
                : getRandomLocation(world, minX, maxX, minZ, maxZ);

        if (teleportLocation == null) {
            MessageUtil.sendMessage(player1, plugin.getConfig().getString("messages.teleport-failed"));
            MessageUtil.sendMessage(player2, plugin.getConfig().getString("messages.teleport-failed"));
            return;
        }

        // ✅ Send messages before teleporting
        MessageUtil.sendTitle(player1, plugin.getConfig().getString("messages.teleporting-title"),
                plugin.getConfig().getString("messages.teleporting-subtitle"));
        MessageUtil.sendTitle(player2, plugin.getConfig().getString("messages.teleporting-title"),
                plugin.getConfig().getString("messages.teleporting-subtitle"));

        MessageUtil.sendMessage(player1, plugin.getConfig().getString("messages.opponent-found"));
        MessageUtil.sendMessage(player2, plugin.getConfig().getString("messages.opponent-found"));

        MessageUtil.playSound(player1, plugin.getConfig().getString("sounds.opponent-found"));
        MessageUtil.playSound(player2, plugin.getConfig().getString("sounds.opponent-found"));

        player1.teleport(teleportLocation);
        player2.teleport(teleportLocation);

        MessageUtil.sendMessage(player1, plugin.getConfig().getString("messages.teleported"));
        MessageUtil.sendMessage(player2, plugin.getConfig().getString("messages.teleported"));

        MessageUtil.playSound(player1, plugin.getConfig().getString("sounds.teleport"));
        MessageUtil.playSound(player2, plugin.getConfig().getString("sounds.teleport"));
    }

    private static Location findSafeLocation(World world, int minX, int maxX, int minZ, int maxZ, Set<Material> unsafeBlocks, int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            int x = random.nextInt(maxX - minX + 1) + minX;
            int z = random.nextInt(maxZ - minZ + 1) + minZ;
            int y = world.getHighestBlockYAt(x, z);
            Location location = new Location(world, x + 0.5, y + 1, z + 0.5);

            // ✅ Ensure the block players land on is safe
            Material blockBelow = world.getBlockAt(x, y - 1, z).getType();
            if (!unsafeBlocks.contains(blockBelow) && blockBelow.isSolid()) {
                return location;
            }
        }
        return null; // No safe location found
    }

    private static Location getRandomLocation(World world, int minX, int maxX, int minZ, int maxZ) {
        int x = random.nextInt(maxX - minX + 1) + minX;
        int z = random.nextInt(maxZ - minZ + 1) + minZ;
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }
}
