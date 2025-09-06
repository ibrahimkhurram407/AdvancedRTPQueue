package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Complete player quit listener
 */
public class PlayerQuitListener implements Listener {
    private final AdvancedRTPQueue plugin;

    public PlayerQuitListener(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Handle queue disconnect
        plugin.getQueueHandler().handlePlayerDisconnect(player);

        // Handle cooldown disconnect
        plugin.getCooldownManager().handlePlayerDisconnect(player);

        // Cancel active teleport sessions
        if (plugin.getTeleportManager().hasActiveSession(player)) {
            plugin.getTeleportManager().cancelPlayerSession(player, "left");
        }

        // Clear message cache for this player
        MessageUtil.clearPlayerFromCache(player.getUniqueId());
    }
}