package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;

public class QueueClearTask extends BukkitRunnable {
    private final AdvancedRTPQueue plugin;

    public QueueClearTask(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getQueueHandler().getQueueSize() > 0) {
            plugin.getQueueHandler().clearQueue();
            Bukkit.broadcastMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.queue-cleared", "&cThe RTP queue has been cleared!")));

            // Play sound to all players when queue is cleared
            Bukkit.getOnlinePlayers().forEach(player ->
                    MessageUtil.playSound(player, Sound.valueOf(plugin.getConfig().getString("sounds.queue-cleared", "ENTITY_ENDER_DRAGON_GROWL")))
            );
        }
    }
}