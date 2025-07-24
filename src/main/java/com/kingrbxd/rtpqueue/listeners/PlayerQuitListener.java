package com.kingrbxd.rtpqueue.listeners;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.QueueHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    private final QueueHandler queueHandler;

    public PlayerQuitListener() {
        this.queueHandler = AdvancedRTPQueue.getInstance().getQueueHandler();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        queueHandler.removeFromQueue(event.getPlayer());
    }
}
