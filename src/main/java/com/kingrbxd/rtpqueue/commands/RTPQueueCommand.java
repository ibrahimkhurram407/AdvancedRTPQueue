package com.kingrbxd.rtpqueue.commands;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Streamlined command handler - no duplicate or broken commands
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
            return handleJoinQueue(player, null);
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "world":
                if (args.length < 2) {
                    MessageUtil.sendMessage(player, "invalid-command");
                    return true;
                }
                return handleJoinQueue(player, args[1]);

            case "leave":
                return handleLeaveQueue(player);

            case "cancel":
                return handleCancelTeleport(player);

            case "reload":
                return handleReload(player);

            case "clear":
                return handleClear(player);

            default:
                // Try as world name
                return handleJoinQueue(player, subcommand);
        }
    }

    private boolean handleJoinQueue(Player player, String worldName) {
        if (!plugin.getCooldownManager().canJoinQueue(player)) {
            return true;
        }

        if (worldName == null) {
            worldName = plugin.getConfigManager().getString("teleport.default-world", "world");
        }

        if (!plugin.getCooldownManager().canJoinWorldQueue(player, worldName)) {
            return true;
        }

        if (plugin.getQueueHandler().addToQueue(player, worldName)) {
            plugin.getCooldownManager().setQueueJoinCooldown(player);
        }

        return true;
    }

    private boolean handleLeaveQueue(Player player) {
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

    private boolean handleReload(Player player) {
        if (!player.hasPermission("rtpqueue.admin")) {
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

    private boolean handleClear(Player player) {
        if (!player.hasPermission("rtpqueue.admin")) {
            MessageUtil.sendMessage(player, "no-permission");
            return true;
        }

        plugin.getQueueHandler().clearAllQueues();
        MessageUtil.sendMessage(player, "queue-cleared");

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
            List<String> subcommands = Arrays.asList("world", "leave", "cancel");

            if (player.hasPermission("rtpqueue.admin")) {
                subcommands = new ArrayList<>(subcommands);
                subcommands.addAll(Arrays.asList("reload", "clear"));
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
        } else if (args.length == 2 && args[0].equalsIgnoreCase("world")) {
            String input = args[1].toLowerCase();
            for (String worldName : plugin.getWorldManager().getAllWorldNames()) {
                if (worldName.toLowerCase().startsWith(input)) {
                    completions.add(worldName);
                }
            }
        }

        return completions;
    }
}