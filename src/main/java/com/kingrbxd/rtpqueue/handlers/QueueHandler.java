package com.kingrbxd.rtpqueue.handlers;

import org.bukkit.entity.Player;
import java.util.*;

public class QueueHandler {
    private final Set<Player> queue = new HashSet<>();
    private final Map<Player, String> worldQueue = new HashMap<>();

    public void addToQueue(Player player) {
        queue.add(player);
    }

    public void addToQueue(Player player, String worldName) {
        queue.add(player);
        if (worldName != null) {
            worldQueue.put(player, worldName);
        }
    }

    public void removeFromQueue(Player player) {
        queue.remove(player);
        worldQueue.remove(player);
    }

    public boolean isInQueue(Player player) {
        return queue.contains(player);
    }

    public String getQueueWorld(Player player) {
        return worldQueue.get(player);
    }

    public void clearQueue() {
        queue.clear();
        worldQueue.clear();
    }

    public int getQueueSize() {
        return queue.size();
    }

    public List<Player> getQueue() {
        return new ArrayList<>(queue);
    }

    public Player findOpponent(Player player) {
        String playerWorld = worldQueue.get(player);

        for (Player queuedPlayer : queue) {
            if (!queuedPlayer.equals(player) && queuedPlayer.isOnline()) {
                // If player is in a specific world queue, match with players in same world queue
                if (playerWorld != null) {
                    String opponentWorld = worldQueue.get(queuedPlayer);
                    if (playerWorld.equals(opponentWorld)) {
                        return queuedPlayer;
                    }
                } else {
                    // If not in specific world queue, match with any player not in a specific world queue
                    if (!worldQueue.containsKey(queuedPlayer)) {
                        return queuedPlayer;
                    }
                }
            }
        }
        return null;
    }
}