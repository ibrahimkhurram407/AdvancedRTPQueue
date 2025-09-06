package com.kingrbxd.rtpqueue.commands;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Complete command handler for RTPQueue
 */
public class RTPQueueCommand implements CommandExecutor, TabCompleter {
    private final AdvancedRTPQueue plugin;

    public RTPQueueCommand(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check base permission
        if (!player.hasPermission("rtpqueue.use")) {
            MessageUtil.sendMessage(player, "no-permission");
            return true;
        }

        // Handle subcommands
        if (args.length == 0) {
            return handleJoinQueue(player, null);
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "join":
                return handleJoinQueue(player, args.length > 1 ? args[1] : null);

            case "leave":
            case "quit":
                return handleLeaveQueue(player);

            case "cancel":
                return handleCancelTeleport(player);

            case "world":
                if (args.length < 2) {
                    MessageUtil.sendMessage(player, "invalid-command");
                    return true;
                }
                return handleJoinQueue(player, args[1]);

            case "reload":
                return handleReload(player);

            case "clear":
                return handleClear(player);

            case "forcequeue":
                return handleForceQueue(player, args);

            default:
                // Try to interpret as world name
                return handleJoinQueue(player, subcommand);
        }
    }

    /**
     * Handle joining queue
     */
    private boolean handleJoinQueue(Player player, String worldName) {
        // Check cooldowns
        if (!plugin.getCooldownManager().canJoinQueue(player)) {
            return true;
        }

        // Use default world if none specified
        if (worldName == null) {
            worldName = plugin.getConfigManager().getString("teleport.default-world", "world");
        }

        // Check world-specific cooldowns
        if (!plugin.getCooldownManager().canJoinWorldQueue(player, worldName)) {
            return true;
        }

        // Add to queue
        if (plugin.getQueueHandler().addToQueue(player, worldName)) {
            plugin.getCooldownManager().setQueueJoinCooldown(player);
        }

        return true;
    }

    /**
     * Handle leaving queue
     */
    private boolean handleLeaveQueue(Player player) {
        if (!plugin.getQueueHandler().isInQueue(player)) {
            MessageUtil.sendMessage(player, "not-in-queue");
            return true;
        }

        // Check cooldowns
        if (!plugin.getCooldownManager().canLeaveQueue(player)) {
            return true;
        }

        if (plugin.getQueueHandler().removeFromQueue(player)) {
            plugin.getCooldownManager().setQueueLeaveCooldown(player);
        }

        return true;
    }

    /**
     * Handle canceling teleport
     */
    private boolean handleCancelTeleport(Player player) {
        if (plugin.getTeleportManager().hasActiveSession(player)) {
            plugin.getTeleportManager().cancelPlayerSession(player, "cancelled");
            MessageUtil.sendMessage(player, "cancelled-moved");
        } else if (plugin.getQueueHandler().isInQueue(player)) {
            return handleLeaveQueue(player);
        } else {
            MessageUtil.sendMessage(player, "not-in-queue");
        }

        return true;
    }

    /**
     * Handle reload command
     */
    private boolean handleReload(Player player) {
        if (!player.hasPermission("rtpqueue.reload")) {
            MessageUtil.sendMessage(player, "no-permission");
            return true;
        }

        try {
            if (plugin.reloadPlugin()) {
                MessageUtil.sendMessage(player, "reload-success");
            } else {
                MessageUtil.sendMessage(player, "reload-failed", Map.of("error", "Unknown error"));
            }
        } catch (Exception e) {
            MessageUtil.sendMessage(player, "reload-failed", Map.of("error", e.getMessage()));
        }

        return true;
    }

    /**
     * Handle clear command
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
     * Handle force queue command
     */
    private boolean handleForceQueue(Player player, String[] args) {
        if (!player.hasPermission("rtpqueue.force")) {
            MessageUtil.sendMessage(player, "no-permission");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("Usage: /rtpqueue forcequeue <player> [world]");
            return true;
        }

        String targetName = args[1];
        String worldName = args.length > 2 ? args[2] : plugin.getConfigManager().getString("teleport.default-world", "world");

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            MessageUtil.sendMessage(player, "player-not-found", Map.of("player", targetName));
            return true;
        }

        if (plugin.getQueueHandler().forcePlayerToQueue(target, worldName)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName());
            placeholders.put("world", plugin.getWorldManager().getDisplayName(worldName));

            MessageUtil.sendMessage(player, "force-queue-success", placeholders);
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName());
            placeholders.put("reason", "Invalid world or other error");

            MessageUtil.sendMessage(player, "force-queue-failed", placeholders);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Main subcommands
            List<String> subcommands = Arrays.asList("join", "leave", "cancel", "world", "status");

            // Admin commands
            if (player.hasPermission("rtpqueue.admin")) {
                subcommands = new ArrayList<>(subcommands);
                subcommands.addAll(Arrays.asList("reload", "clear"));
            }

            if (player.hasPermission("rtpqueue.force")) {
                subcommands = new ArrayList<>(subcommands);
                subcommands.add("forcequeue");
            }

            // Add world names
            subcommands = new ArrayList<>(subcommands);
            subcommands.addAll(plugin.getWorldManager().getAllWorldNames());

            String input = args[0].toLowerCase();
            for (String subcommand : subcommands) {
                if (subcommand.toLowerCase().startsWith(input)) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();

            if (subcommand.equals("world") || subcommand.equals("join")) {
                // World names
                String input = args[1].toLowerCase();
                for (String worldName : plugin.getWorldManager().getAllWorldNames()) {
                    if (worldName.toLowerCase().startsWith(input)) {
                        completions.add(worldName);
                    }
                }
            } else if (subcommand.equals("forcequeue") && player.hasPermission("rtpqueue.force")) {
                // Player names
                String input = args[1].toLowerCase();
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (onlinePlayer.getName().toLowerCase().startsWith(input)) {
                        completions.add(onlinePlayer.getName());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("forcequeue") && player.hasPermission("rtpqueue.force")) {
            // World names for forcequeue
            String input = args[2].toLowerCase();
            for (String worldName : plugin.getWorldManager().getAllWorldNames()) {
                if (worldName.toLowerCase().startsWith(input)) {
                    completions.add(worldName);
                }
            }
        }

        return completions;
    }
}