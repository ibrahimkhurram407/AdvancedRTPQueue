package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.TeleportHandler;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMoveListener implements Listener {
    private final AdvancedRTPQueue plugin;

    public PlayerMoveListener(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Skip if movement cancellation is disabled
        if (!plugin.getConfig().getBoolean("teleport.cancel-on-move", true)) {
            return;
        }

        Player player = event.getPlayer();

        // Skip if player doesn't have a pending teleport
        if (!TeleportHandler.hasPendingTeleport(player)) {
            return;
        }

        // Get from/to locations
        Location from = event.getFrom();
        Location to = event.getTo();

        // Skip if only looking around (head movement)
        if (to != null && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        // Cancel teleport for the entire group
        TeleportHandler.cancelTeleportGroup(player, "moved");
    }
}