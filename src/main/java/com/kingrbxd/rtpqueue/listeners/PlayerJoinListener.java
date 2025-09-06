package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Complete player join listener
 */
public class PlayerJoinListener implements Listener {
    private final AdvancedRTPQueue plugin;

    public PlayerJoinListener(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Auto-join queue if enabled
        if (plugin.getConfigManager().getBoolean("queue.auto-join-on-login", false)) {
            String defaultWorld = plugin.getConfigManager().getString("teleport.default-world", "world");

            // Add to queue after a short delay
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !plugin.getQueueHandler().isInQueue(player)) {
                    plugin.getQueueHandler().addToQueue(player, defaultWorld);
                }
            }, 40L); // 2 seconds delay
        }
    }
}