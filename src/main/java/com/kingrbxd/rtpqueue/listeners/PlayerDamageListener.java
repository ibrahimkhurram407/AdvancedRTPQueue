package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Complete player damage listener
 */
public class PlayerDamageListener implements Listener {
    private final AdvancedRTPQueue plugin;

    public PlayerDamageListener(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!plugin.getConfigManager().getBoolean("teleport.cancel-on-damage", false)) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Cancel active teleport session if player took damage
        if (plugin.getTeleportManager().hasActiveSession(player)) {
            plugin.getTeleportManager().cancelPlayerSession(player, "damage");
        }
    }
}