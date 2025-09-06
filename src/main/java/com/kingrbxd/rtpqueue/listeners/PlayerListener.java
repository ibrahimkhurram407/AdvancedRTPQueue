package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.TeleportHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Listener for player events.
 */
public class PlayerListener implements Listener {
    private final AdvancedRTPQueue plugin;

    public PlayerListener(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle player movement during teleport countdown.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Only check XZ movement (allow looking around)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Check if player has a pending teleport
        if (TeleportHandler.hasPendingTeleport(player)) {
            // Check if movement cancellation is enabled
            if (plugin.getConfig().getBoolean("teleport.cancel-on-move", true)) {
                // Cancel the teleport for the whole group
                TeleportHandler.cancelTeleportGroup(player, "moved");
            }
        }
    }

    /**
     * Handle player quitting during teleport countdown.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remove player from queue
        plugin.getQueueHandler().removeFromQueue(player);

        // Check if player has a pending teleport
        if (TeleportHandler.hasPendingTeleport(player)) {
            // Cancel the teleport for the whole group
            TeleportHandler.cancelTeleportGroup(player, "left");
        }
    }

    /**
     * Handle player teleportation during teleport countdown.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // Check if player has a pending teleport
        if (TeleportHandler.hasPendingTeleport(player)) {
            // Check if this is not our own teleport (which would be handled elsewhere)
            if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
                // Cancel the teleport for the whole group
                TeleportHandler.cancelTeleportGroup(player, "moved");
            }
        }
    }
}