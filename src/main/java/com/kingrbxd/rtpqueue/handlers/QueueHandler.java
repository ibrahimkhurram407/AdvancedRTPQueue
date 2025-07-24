package com.kingrbxd.rtpqueue.handlers;

import org.bukkit.entity.Player;
import java.util.*;

public class QueueHandler {
    private final Set<Player> queue = new HashSet<>();

    public void addToQueue(Player player) {
        queue.add(player);
    }

    public void removeFromQueue(Player player) {
        queue.remove(player);
    }

    public boolean isInQueue(Player player) {
        return queue.contains(player);
    }

    public void clearQueue() {
        queue.clear();
    }

    // ✅ FIX: Add getQueueSize() method
    public int getQueueSize() {
        return queue.size();
    }

    // ✅ If you need a list version, use this:
    public List<Player> getQueue() {
        return new ArrayList<>(queue); // Converts Set<Player> to List<Player>
    }

    public Player findOpponent(Player player) {
        for (Player queuedPlayer : queue) {
            if (!queuedPlayer.equals(player) && queuedPlayer.isOnline()) {
                return queuedPlayer;
            }
        }
        return null;
    }
}
