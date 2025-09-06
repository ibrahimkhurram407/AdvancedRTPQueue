package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;

/**
 * Task to clear inactive queues after a certain time.
 */
public class QueueClearTask extends BukkitRunnable {
    private final AdvancedRTPQueue plugin;

    public QueueClearTask(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        QueueHandler queueHandler = plugin.getQueueHandler();
        Map<String, Integer> queueSizes = queueHandler.getWorldQueueSizes();

        if (queueSizes.isEmpty()) {
            return; // No queues to clear
        }

        // Clear each world queue that has players
        boolean anyCleared = false;
        for (Map.Entry<String, Integer> entry : queueSizes.entrySet()) {
            String worldName = entry.getKey();
            int queueSize = entry.getValue();

            // Only clear if queue doesn't have enough players
            int requiredPlayers = plugin.getConfig().getInt("queue.required-players", 2);
            if (queueSize > 0 && queueSize < requiredPlayers) {
                // Notify players in this queue before clearing
                for (Player player : queueHandler.getQueueForWorld(worldName)) {
                    MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.queue-cleared"));
                    MessageUtil.playSound(player, plugin.getConfig().getString("sounds.queue-cleared"));
                }

                // Clear this world's queue
                queueHandler.clearQueue(worldName);
                anyCleared = true;

                plugin.getLogger().info("Cleared inactive queue for world '" + worldName + "' with " + queueSize + " player(s)");
            }
        }

        if (anyCleared) {
            plugin.getLogger().info("Queue clear task ran - some queues were cleared");
        }
    }
}