package com.kingrbxd.rtpqueue.placeholders;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Complete PlaceholderAPI manager
 */
public class PlaceholderManager {
    private final AdvancedRTPQueue plugin;
    private boolean placeholderAPIEnabled = false;
    private RTPQueueExpansion expansion;

    public PlaceholderManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        setupPlaceholderAPI();
    }

    /**
     * Setup PlaceholderAPI integration
     */
    public void setupPlaceholderAPI() {
        if (placeholderAPIEnabled && expansion != null) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {}
            placeholderAPIEnabled = false;
            expansion = null;
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                expansion = new RTPQueueExpansion(plugin);
                if (expansion.canRegister() && expansion.register()) {
                    placeholderAPIEnabled = true;
                    plugin.getLogger().info("Successfully hooked into PlaceholderAPI!");
                    plugin.getLogger().info("Available placeholders:");
                    plugin.getLogger().info("  %rtpqueue_in_queue% - Whether player is in queue");
                    plugin.getLogger().info("  %rtpqueue_current_world% - Current queue world");
                    plugin.getLogger().info("  %rtpqueue_total_players% - Total players in all queues");
                    plugin.getLogger().info("  %rtpqueue_required_players% - Required players to teleport");
                    plugin.getLogger().info("  %rtpqueue_remaining_needed% - Players needed for current queue");
                    plugin.getLogger().info("  %rtpqueue_world_count_<worldname>% - Players in specific world queue");
                    plugin.getLogger().info("  %rtpqueue_world_status_<worldname>% - Status of specific world queue");
                } else {
                    plugin.getLogger().warning("Failed to register PlaceholderAPI expansion");
                }
            } catch (Throwable e) {
                plugin.getLogger().warning("Failed to hook into PlaceholderAPI: " + e.getMessage());
            }
        } else {
            plugin.getLogger().info("PlaceholderAPI not found. Placeholders will not be available.");
        }
    }

    /**
     * Unregister the expansion
     */
    public void unregister() {
        if (!placeholderAPIEnabled || expansion == null) return;
        try {
            expansion.unregister();
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to unregister PlaceholderAPI expansion: " + t.getMessage());
        } finally {
            placeholderAPIEnabled = false;
            expansion = null;
        }
    }

    /**
     * PlaceholderAPI expansion class
     */
    public class RTPQueueExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
        private final AdvancedRTPQueue plugin;

        public RTPQueueExpansion(AdvancedRTPQueue plugin) {
            this.plugin = plugin;
        }

        @Override
        public String getIdentifier() {
            return "rtpqueue";
        }

        @Override
        public String getAuthor() {
            try {
                return String.join(", ", plugin.getDescription().getAuthors());
            } catch (Throwable t) {
                return "KingRBxD";
            }
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public boolean canRegister() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, String identifier) {
            if (player == null) {
                return "";
            }

            // Basic player status
            if (identifier.equals("in_queue")) {
                return plugin.getQueueHandler().isInQueue(player) ? "true" : "false";
            }

            if (identifier.equals("current_world")) {
                String world = plugin.getQueueHandler().getPlayerQueueWorld(player);
                if (world == null) return "none";
                return MessageUtil.colorize(plugin.getWorldManager().getDisplayName(world));
            }

            // Global queue info
            if (identifier.equals("total_players")) {
                int total = 0;
                Map<String, Integer> queueSizes = plugin.getQueueHandler().getAllQueueSizes();
                for (int size : queueSizes.values()) {
                    total += size;
                }
                return String.valueOf(total);
            }

            if (identifier.equals("required_players")) {
                return String.valueOf(plugin.getConfigManager().getInt("queue.required-players", 2));
            }

            if (identifier.equals("remaining_needed")) {
                if (!plugin.getQueueHandler().isInQueue(player)) return "0";

                String worldName = plugin.getQueueHandler().getPlayerQueueWorld(player);
                if (worldName == null) return "0";

                int required = plugin.getConfigManager().getInt("queue.required-players", 2);
                int current = plugin.getQueueHandler().getQueueSize(worldName);
                int remaining = Math.max(0, required - current);

                return String.valueOf(remaining);
            }

            // World-specific queue counts
            if (identifier.startsWith("world_count_")) {
                String worldName = identifier.substring("world_count_".length());
                return String.valueOf(plugin.getQueueHandler().getQueueSize(worldName));
            }

            // World queue status
            if (identifier.startsWith("world_status_")) {
                String worldName = identifier.substring("world_status_".length());
                int size = plugin.getQueueHandler().getQueueSize(worldName);
                int required = plugin.getConfigManager().getInt("queue.required-players", 2);

                if (size == 0) {
                    return "empty";
                } else if (size < required) {
                    return "waiting";
                } else {
                    return "ready";
                }
            }

            return "";
        }
    }
}