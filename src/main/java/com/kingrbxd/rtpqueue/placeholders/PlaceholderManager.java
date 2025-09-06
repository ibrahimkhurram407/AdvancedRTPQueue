package com.kingrbxd.rtpqueue.placeholders;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.QueueHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Manages PlaceholderAPI integration.
 */
public class PlaceholderManager {
    private final AdvancedRTPQueue plugin;
    private boolean placeholderAPIEnabled = false;

    public PlaceholderManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        setupPlaceholderAPI();
    }

    /**
     * Set up PlaceholderAPI integration if available.
     */
    private void setupPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                if (new RTPQueueExpansion(plugin).canRegister()) {
                    if (new RTPQueueExpansion(plugin).register()) {
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
                        plugin.getLogger().warning("Failed to register PlaceholderAPI expansion.");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to hook into PlaceholderAPI: " + e.getMessage());
                plugin.getLogger().warning("Placeholders will not be available.");
            }
        } else {
            plugin.getLogger().info("PlaceholderAPI not found. Placeholders will not be available.");
        }
    }

    /**
     * Check if PlaceholderAPI is enabled.
     *
     * @return True if PlaceholderAPI is enabled
     */
    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }

    /**
     * Custom expansion for PlaceholderAPI.
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
            return plugin.getDescription().getAuthors().toString();
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

            QueueHandler queueHandler = plugin.getQueueHandler();

            // Basic player status
            // %rtpqueue_in_queue%
            if (identifier.equals("in_queue")) {
                return queueHandler.isInQueue(player) ? "true" : "false";
            }

            // %rtpqueue_current_world%
            if (identifier.equals("current_world")) {
                String world = queueHandler.getPlayerQueueWorld(player);
                return world != null ? world : "none";
            }

            // Global queue info
            // %rtpqueue_total_players%
            if (identifier.equals("total_players")) {
                int total = 0;
                Map<String, Integer> queueSizes = queueHandler.getAllQueueSizes();
                for (int size : queueSizes.values()) {
                    total += size;
                }
                return String.valueOf(total);
            }

            // %rtpqueue_required_players%
            if (identifier.equals("required_players")) {
                return String.valueOf(plugin.getConfig().getInt("queue.required-players", 2));
            }

            // %rtpqueue_remaining_needed%
            if (identifier.equals("remaining_needed")) {
                if (!queueHandler.isInQueue(player)) return "0";

                String worldName = queueHandler.getPlayerQueueWorld(player);
                if (worldName == null) return "0";

                int required = plugin.getConfig().getInt("queue.required-players", 2);
                int current = queueHandler.getQueueSize(worldName);
                int remaining = Math.max(0, required - current);

                return String.valueOf(remaining);
            }

            // World-specific queue counts
            // Format: %rtpqueue_world_count_<worldname>%
            if (identifier.startsWith("world_count_")) {
                String worldName = identifier.substring("world_count_".length());
                return String.valueOf(queueHandler.getQueueSize(worldName));
            }

            // Get status of specific world queue
            // %rtpqueue_world_status_<worldname>%
            if (identifier.startsWith("world_status_")) {
                String worldName = identifier.substring("world_status_".length());
                int size = queueHandler.getQueueSize(worldName);
                int required = plugin.getConfig().getInt("queue.required-players", 2);

                if (size == 0) {
                    return "empty";
                } else if (size < required) {
                    return "waiting";
                } else {
                    return "ready";
                }
            }

            // Return empty string for unknown placeholders
            return "";
        }
    }
}