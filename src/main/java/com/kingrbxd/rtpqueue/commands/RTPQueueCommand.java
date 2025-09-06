package com.kingrbxd.rtpqueue.commands;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.WorldManager;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RTPQueueCommand
 *
 * Allowed command forms:
 *  - /rtpqueue                         -> join default world queue
 *  - /rtpqueue world <worldKey>        -> join specified world queue (worldKey is the config key, e.g. "nether")
 *  - /rtpqueue leave                   -> leave queue or cancel active teleport
 *  - /rtpqueue reload                  -> admin reload config
 *  - /rtpqueue clear                   -> admin clear all queues
 *
 * Note: direct shortcuts like /rtpqueue nether or /rtpqueue end are intentionally NOT supported.
 */
public class RTPQueueCommand implements CommandExecutor, TabCompleter {
    private final AdvancedRTPQueue plugin;

    public RTPQueueCommand(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("rtpqueue.use")) {
            MessageUtil.sendMessage(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            // Join default world queue
            return handleJoinQueue(player, null);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "world":
                // Required form: /rtpqueue world <worldKey>
                if (args.length < 2) {
                    MessageUtil.sendMessage(player, "invalid-command");
                    return true;
                }
                return handleJoinQueue(player, args[1]);
            case "leave":
                return handleLeaveQueue(player);
            case "reload":
                return handleReload(player);
            case "clear":
                return handleClear(player);
            default:
                // Any other first argument is invalid — we do NOT accept direct world keys like "/rtpqueue nether"
                MessageUtil.sendMessage(player, "invalid-command");
                return true;
        }
    }

    /**
     * Join the queue for the requested world.
     * worldInput is expected to be a world key as defined in the config (e.g. "nether", "end").
     * If null, joins the default world.
     */
    private boolean handleJoinQueue(Player player, String worldInput) {
        // Check basic join cooldown
        if (!plugin.getCooldownManager().canJoinQueue(player)) {
            return true; // cooldown manager already sends message
        }

        // Determine world key (null => default)
        String worldKey = worldInput;
        if (worldKey == null) {
            worldKey = plugin.getConfigManager().getString("teleport.default-world", "world");
        }

        // Validate that the world key exists in configured worlds and is available on the server
        WorldManager wm = plugin.getWorldManager();
        if (!wm.isValidWorld(worldKey)) {
            MessageUtil.sendMessage(player, "invalid-world", Collections.singletonMap("world", worldKey));
            return true;
        }

        // Check world-specific permission
        if (!hasWorldPermission(player, worldKey)) {
            MessageUtil.sendMessage(player, "no-permission-world", Collections.singletonMap("world", wm.getDisplayName(worldKey)));
            return true;
        }

        // Per-world cooldown check
        if (!plugin.getCooldownManager().canJoinWorldQueue(player, worldKey)) {
            return true;
        }

        // If already in a queue
        if (plugin.getQueueHandler().isInQueue(player)) {
            String current = plugin.getQueueHandler().getPlayerQueueWorld(player);

            // Same world -> already in queue
            if (worldKey.equals(current)) {
                MessageUtil.sendMessage(player, "already-in-queue");
                return true;
            }

            // Different world -> check switching config
            if (plugin.getConfigManager().getBoolean("queue.allow-world-switching", true)) {
                // Remove from old queue then add to new
                plugin.getQueueHandler().removeFromQueue(player);
                if (plugin.getQueueHandler().addToQueue(player, worldKey)) {
                    plugin.getCooldownManager().setQueueJoinCooldown(player);
                }
                return true;
            } else {
                // Switching disabled
                MessageUtil.sendMessage(player, "already-in-queue");
                return true;
            }
        }

        // Not in any queue -> add player
        if (plugin.getQueueHandler().addToQueue(player, worldKey)) {
            plugin.getCooldownManager().setQueueJoinCooldown(player);
        }
        return true;
    }

    /**
     * Leave queue or cancel active teleport
     */
    private boolean handleLeaveQueue(Player player) {
        // If player is currently in an active teleport session, cancel it
        if (plugin.getTeleportManager().hasActiveSession(player)) {
            plugin.getTeleportManager().cancelPlayerSession(player, "cancelled");
            MessageUtil.sendMessage(player, "cancelled-moved");
            return true;
        }

        // Otherwise attempt to remove from queue
        if (!plugin.getQueueHandler().isInQueue(player)) {
            MessageUtil.sendMessage(player, "not-in-queue");
            return true;
        }

        if (!plugin.getCooldownManager().canLeaveQueue(player)) {
            return true;
        }

        if (plugin.getQueueHandler().removeFromQueue(player)) {
            plugin.getCooldownManager().setQueueLeaveCooldown(player);
        }
        return true;
    }

    /**
     * Admin reload
     */
    private boolean handleReload(Player player) {
        if (!player.hasPermission("rtpqueue.admin")) {
            MessageUtil.sendMessage(player, "no-permission");
            return true;
        }

        try {
            if (plugin.reloadPlugin()) {
                MessageUtil.sendMessage(player, "reload-success");
            } else {
                MessageUtil.sendMessage(player, "reload-failed", Collections.singletonMap("error", "Unknown error"));
            }
        } catch (Exception e) {
            MessageUtil.sendMessage(player, "reload-failed", Collections.singletonMap("error", e.getMessage()));
            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                e.printStackTrace();
            }
        }
        return true;
    }

    /**
     * Admin clear
     */
    private boolean handleClear(Player player) {
        if (!player.hasPermission("rtpqueue.admin")) {
            MessageUtil.sendMessage(player, "no-permission");
            return true;
        }

        plugin.getQueueHandler().clearAllQueues();
        MessageUtil.sendMessage(player, "queue-cleared");
        return true;
    }

    /**
     * World permission check. Default world allowed with basic rtpqueue.use permission.
     */
    private boolean hasWorldPermission(Player player, String worldKey) {
        if (player.hasPermission("rtpqueue.world.*")) return true;

        WorldManager wm = plugin.getWorldManager();
        WorldManager.WorldSettings settings = wm.getWorldSettings(worldKey);
        if (settings == null) return false;

        // If world has a specific permission configured, require it
        String configuredPerm = settings.getPermission();
        if (configuredPerm != null && !configuredPerm.isEmpty()) {
            return player.hasPermission(configuredPerm);
        }

        // Otherwise allow default world when player has basic use permission
        String defaultWorld = plugin.getConfigManager().getString("teleport.default-world", "world");
        if (worldKey.equals(defaultWorld) && player.hasPermission("rtpqueue.use")) return true;

        // Fallback: require "rtpqueue.world.<key>"
        return player.hasPermission("rtpqueue.world." + worldKey.toLowerCase(Locale.ROOT));
    }

    /**
     * Tab completion:
     *  - /rtpqueue <tab> -> suggests: world, leave, (reload, clear for admins)
     *  - /rtpqueue world <tab> -> suggests only configured world keys (e.g. nether, end) that are available
     *
     * Note: we do NOT suggest direct world shortcuts like /rtpqueue nether (user requested only /rtpqueue world <name>).
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player player = (Player) sender;

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            // base subcommands
            completions.add("world");
            completions.add("leave");
            if (player.hasPermission("rtpqueue.admin")) {
                completions.add("reload");
                completions.add("clear");
            }

            String partial = args[0].toLowerCase(Locale.ROOT);
            return completions.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("world")) {
            // Suggest configured world keys only, and only those that are valid and that the player can access
            Set<String> all = plugin.getWorldManager().getAllWorldNames();
            String partial = args[1].toLowerCase(Locale.ROOT);

            return all.stream()
                    .filter(key -> plugin.getWorldManager().isValidWorld(key))
                    .filter(key -> hasWorldPermission(player, key))
                    .map(String::toLowerCase)
                    .filter(k -> k.startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}