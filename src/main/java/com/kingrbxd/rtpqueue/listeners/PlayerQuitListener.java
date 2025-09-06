package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import com.kingrbxd.rtpqueue.handlers.TeleportHandler;
import com.kingrbxd.rtpqueue.handlers.CooldownManager;

/**
 * Cleans up pending teleports and cooldowns when players disconnect.
 */
public class PlayerQuitListener implements Listener {
    private final AdvancedRTPQueue plugin;
    private final CooldownManager cooldownManager;

    public PlayerQuitListener(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        this.cooldownManager = plugin.getCooldownManager();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Cancel any pre-teleport countdown for this player
        if (cooldownManager != null && cooldownManager.isInPreTeleport(player)) {
            cooldownManager.cancelPreTeleport(player);
        }

        // Cancel any pending teleport flag for this player
        if (TeleportHandler.hasPendingTeleport(player)) {
            TeleportHandler.cancelPendingTeleport(player);
        }

        // Cancel entire group so other members are notified
        TeleportHandler.cancelTeleportGroup(player, "left");

        // Fully clear any cooldowns/cached per-world cooldowns and task references for this player
        if (cooldownManager != null) {
            cooldownManager.clearCooldowns(player);
        }
    }
}