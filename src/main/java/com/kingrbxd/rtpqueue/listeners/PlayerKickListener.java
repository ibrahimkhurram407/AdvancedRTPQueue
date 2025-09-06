package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.entity.Player;
import com.kingrbxd.rtpqueue.handlers.TeleportHandler;
import com.kingrbxd.rtpqueue.handlers.CooldownManager;

/**
 * Ensures cooldowns and pending teleports are cleared when a player is kicked.
 */
public class PlayerKickListener implements Listener {
    private final AdvancedRTPQueue plugin;
    private final CooldownManager cooldownManager;

    public PlayerKickListener(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        this.cooldownManager = plugin.getCooldownManager();
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();

        // Cancel pre-teleport if active
        if (cooldownManager != null && cooldownManager.isInPreTeleport(player)) {
            cooldownManager.cancelPreTeleport(player);
        }

        // Cancel any pending teleport flag for this player
        if (TeleportHandler.hasPendingTeleport(player)) {
            TeleportHandler.cancelPendingTeleport(player);
        }

        // Cancel entire group so other members are notified
        TeleportHandler.cancelTeleportGroup(player, "left");

        // Fully clear cooldowns for this player
        if (cooldownManager != null) {
            cooldownManager.clearCooldowns(player);
        }
    }
}