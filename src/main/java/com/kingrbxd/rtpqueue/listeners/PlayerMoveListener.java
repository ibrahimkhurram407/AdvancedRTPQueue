package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Complete player move listener
 */
public class PlayerMoveListener implements Listener {
    private final AdvancedRTPQueue plugin;

    public PlayerMoveListener(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getConfigManager().getBoolean("teleport.cancel-on-move", true)) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player moved significantly (not just head movement)
        if (event.getFrom().distanceSquared(event.getTo()) < 0.01) {
            return;
        }

        // Cancel active teleport session if player moved
        if (plugin.getTeleportManager().hasActiveSession(player)) {
            plugin.getTeleportManager().cancelPlayerSession(player, "moved");
            event.setCancelled(false); // Allow the movement but cancel teleport
        }
    }
}