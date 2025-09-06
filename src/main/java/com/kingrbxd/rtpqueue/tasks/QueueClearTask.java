package com.kingrbxd.rtpqueue.tasks;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Complete queue clear task with offline player cleanup
 */
public class QueueClearTask implements Runnable {
    private final AdvancedRTPQueue plugin;

    public QueueClearTask(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Running queue maintenance task...");
        }

        // Clean up offline players from queues
        int removedOffline = plugin.getQueueHandler().removeOfflinePlayers();

        // Get current queue information
        Map<String, Object> queueInfo = plugin.getQueueHandler().getQueueInformation();

        if (plugin.getConfigManager().getBoolean("plugin.debug")) {
            plugin.getLogger().info("Queue maintenance completed:");
            plugin.getLogger().info("- Removed offline players: " + removedOffline);
            plugin.getLogger().info("- Total queued players: " + queueInfo.get("totalQueuedPlayers"));
            plugin.getLogger().info("- Active worlds: " + queueInfo.get("activeWorlds"));
        }

        Map<String, Integer> queueSizes = plugin.getQueueHandler().getAllQueueSizes();
        if (queueSizes.isEmpty()) {
            return;
        }

        int requiredPlayers = plugin.getConfigManager().getInt("queue.required-players", 2);

        for (Map.Entry<String, Integer> entry : queueSizes.entrySet()) {
            String worldName = entry.getKey();
            int size = entry.getValue();

            // Clear world queue only if it's active but below required players
            if (size > 0 && size < requiredPlayers) {
                // Notify players in this queue
                for (UUID uuid : plugin.getQueueHandler().getPlayersInWorldQueue(worldName)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        MessageUtil.sendMessage(player, "queue-cleared");
                        MessageUtil.playSound(player, "queue-leave");
                    }
                }

                // Clear the queue
                plugin.getQueueHandler().clearWorldQueue(worldName);

                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().info("Cleared world queue '" + worldName + "' (" + size + " players)");
                }
            }
        }

        // Clean up expired cooldowns
        plugin.getCooldownManager().cleanupExpiredCooldowns();
    }
}