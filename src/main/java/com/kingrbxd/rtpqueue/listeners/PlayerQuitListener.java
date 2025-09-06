package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.TeleportHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Check if player has pending teleport
        if (TeleportHandler.hasPendingTeleport(player)) {
            // Cancel teleport for the entire group
            TeleportHandler.cancelTeleportGroup(player, "left");
        } else {
            // Just remove from queue
            AdvancedRTPQueue.getInstance().getQueueHandler().removeFromQueue(player);
        }
    }
}