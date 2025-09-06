package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.Player;
import com.kingrbxd.rtpqueue.handlers.TeleportHandler;
import com.kingrbxd.rtpqueue.handlers.CooldownManager;

/**
 * Cancels pre-teleport/pending teleports when a player moves.
 */
public class PlayerMoveListener implements Listener {
    private final AdvancedRTPQueue plugin;
    private final CooldownManager cooldownManager;

    public PlayerMoveListener(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        this.cooldownManager = plugin.getCooldownManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Ignore look-only changes: require block coordinate change
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // 1) If the player is in a pre-teleport countdown, cancel it
        if (cooldownManager != null && cooldownManager.isInPreTeleport(player)) {
            cooldownManager.cancelPreTeleport(player);

            // Also cancel the whole teleport group so other players are notified
            TeleportHandler.cancelTeleportGroup(player, "moved");
            return;
        }

        // 2) If the player has a pending teleport (single-player pending flag), cancel it
        if (TeleportHandler.hasPendingTeleport(player)) {
            TeleportHandler.cancelPendingTeleport(player);

            // Notify player (messages/sounds handled inside cancelPendingTeleport or via config)
            // Optionally cancel player's group too if your flow requires that:
            TeleportHandler.cancelTeleportGroup(player, "moved");
        }
    }
}