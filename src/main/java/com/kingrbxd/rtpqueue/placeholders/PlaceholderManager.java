package com.kingrbxd.rtpqueue.placeholders;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.QueueHandler;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Manages PlaceholderAPI integration.
 */
public class PlaceholderManager {
    private final AdvancedRTPQueue plugin;
    private boolean placeholderAPIEnabled = false;
    private RTPQueueExpansion expansion; // single expansion instance

    public PlaceholderManager(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        setupPlaceholderAPI();
    }

    /**
     * Set up PlaceholderAPI integration if available.
     * Safe to call multiple times (will unregister previous expansion before re-registering).
     */
    public void setupPlaceholderAPI() {
        // If already set up, unregister first (safe for reload)
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
                    plugin.getLogger().info("  %rtpqueue_current_world% - Current queue world (display-name)");
                    plugin.getLogger().info("  %rtpqueue_total_players% - Total players in all queues");
                    plugin.getLogger().info("  %rtpqueue_required_players% - Required players to teleport");
                    plugin.getLogger().info("  %rtpqueue_remaining_needed% - Players needed for current queue");
                    plugin.getLogger().info("  %rtpqueue_world_count_<worldname>% - Players in specific world queue");
                    plugin.getLogger().info("  %rtpqueue_world_status_<worldname>% - Status of specific world queue");
                } else {
                    plugin.getLogger().warning("Failed to register PlaceholderAPI expansion.");
                    placeholderAPIEnabled = false;
                    expansion = null;
                }
            } catch (Throwable e) {
                plugin.getLogger().warning("Failed to hook into PlaceholderAPI: " + e.getMessage());
                plugin.getLogger().warning("Placeholders will not be available.");
                placeholderAPIEnabled = false;
                expansion = null;
            }
        } else {
            plugin.getLogger().info("PlaceholderAPI not found. Placeholders will not be available.");
        }
    }

    /**
     * Unregister the expansion (called on plugin disable/reload).
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
     * Custom expansion for PlaceholderAPI.
     * Fully-qualified parent class reference used to avoid import issues when PAPI isn't present at compile-time.
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
            // Return a clean comma-separated list of authors
            try {
                java.util.List<String> authors = plugin.getDescription().getAuthors();
                if (authors == null || authors.isEmpty()) return "unknown";
                return String.join(", ", authors);
            } catch (Throwable t) {
                return "unknown";
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

            QueueHandler queueHandler = plugin.getQueueHandler();
            if (queueHandler == null) return "";

            // Basic player status
            // %rtpqueue_in_queue%
            if (identifier.equals("in_queue")) {
                return queueHandler.isInQueue(player) ? "true" : "false";
            }

            // %rtpqueue_current_world% -> use world display name from WorldManager (colorized)
            if (identifier.equals("current_world")) {
                String world = queueHandler.getPlayerQueueWorld(player);
                if (world == null) return "none";
                // Use WorldManager.getDisplayName if available, otherwise return raw name
                try {
                    String display = plugin.getWorldManager() != null
                            ? plugin.getWorldManager().getDisplayName(world)
                            : world;
                    return display == null ? "none" : MessageUtil.colorize(display);
                } catch (Throwable ignored) {
                    return world;
                }
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