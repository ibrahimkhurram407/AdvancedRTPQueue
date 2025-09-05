package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.CooldownManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMoveListener implements Listener {
    private final AdvancedRTPQueue plugin;
    private final CooldownManager cooldownManager;

    public PlayerMoveListener(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        this.cooldownManager = plugin.getCooldownManager();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if cancel-on-move is enabled
        if (!plugin.getConfig().getBoolean("cooldowns.cancel-on-move", true)) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player is in pre-teleport countdown
        if (cooldownManager.isInPreTeleport(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            // Only cancel if player changed block position (not just looking around)
            if (to != null && (from.getBlockX() != to.getBlockX() ||
                    from.getBlockY() != to.getBlockY() ||
                    from.getBlockZ() != to.getBlockZ())) {
                cooldownManager.cancelPreTeleport(player);
            }
        }
    }
}