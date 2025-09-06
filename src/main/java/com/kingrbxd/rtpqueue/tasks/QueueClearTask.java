package com.kingrbxd.rtpqueue.tasks;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.QueueHandler;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Runnable task that clears inactive queues.
 * Extracted from inline runnable to a dedicated class for clarity and testability.
 */
public class QueueClearTask implements Runnable {
    private final AdvancedRTPQueue plugin;

    public QueueClearTask(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("QueueClearTask running...");
        }

        QueueHandler queueHandler = plugin.getQueueHandler();
        if (queueHandler == null) return;

        Map<String, Integer> queueSizes = queueHandler.getAllQueueSizes();
        if (queueSizes.isEmpty()) {
            return;
        }

        int requiredPlayers = plugin.getConfig().getInt("queue.required-players", 2);

        for (Map.Entry<String, Integer> entry : queueSizes.entrySet()) {
            String worldName = entry.getKey();
            int size = entry.getValue();

            // Clear world queue only if it's active but below the required players threshold
            if (size > 0 && size < requiredPlayers) {
                // Notify players in this queue
                for (java.util.UUID uuid : queueHandler.getPlayersInWorldQueue(worldName)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        MessageUtil.sendMessage(p, plugin.getConfig().getString("messages.queue-cleared", "&#ff6600⚠ &cQueue cleared due to inactivity."));
                        MessageUtil.playSound(p, plugin.getConfig().getString("sounds.queue-cleared", ""));
                    }
                }

                // Clear the queue for that world
                queueHandler.clearWorldQueue(worldName);

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Cleared world queue '" + worldName + "' (" + size + " players)");
                }

                // Optional: broadcast
                if (plugin.getConfig().getBoolean("broadcast-queue-clear", false)) {
                    Bukkit.getServer().broadcastMessage(plugin.getConfig().getString("messages.queue-cleared", "&#ff6600⚠ &cQueue cleared due to inactivity."));
                }
            }
        }
    }
}