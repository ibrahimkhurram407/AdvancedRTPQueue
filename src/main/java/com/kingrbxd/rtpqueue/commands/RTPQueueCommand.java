package com.kingrbxd.rtpqueue.commands;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.CooldownManager;
import com.kingrbxd.rtpqueue.handlers.QueueHandler;
import com.kingrbxd.rtpqueue.handlers.TeleportHandler;
import com.kingrbxd.rtpqueue.handlers.WorldManager;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RTPQueueCommand implements CommandExecutor, TabCompleter {
    private final AdvancedRTPQueue plugin;
    private final QueueHandler queueHandler;
    private final CooldownManager cooldownManager;
    private final WorldManager worldManager;

    public RTPQueueCommand(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        this.queueHandler = plugin.getQueueHandler();
        this.cooldownManager = plugin.getCooldownManager();
        this.worldManager = plugin.getWorldManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            handleJoinQueue(player, null);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "cancel":
                handleLeaveQueue(player);
                break;

            case "reload":
                handleReload(player);
                break;

            case "world":
                if (args.length < 2) {
                    MessageUtil.sendMessage(player, "&cUsage: /rtpqueue world <worldname>");
                    return true;
                }
                handleWorldQueue(player, args[1]);
                break;

            default:
                MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.invalid-command"));
                MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
                break;
        }

        return true;
    }

    private void handleJoinQueue(Player player, String worldName) {
        // Check if player is already in queue
        if (queueHandler.isInQueue(player)) {
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.already-in-queue"));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
            return;
        }

        // Check cooldown before joining queue
        if (!cooldownManager.canJoinQueue(player, worldName)) {
            return; // Message already sent by cooldownManager
        }

        // Set cooldown
        cooldownManager.setQueueCooldown(player, worldName);

        // Add to queue
        queueHandler.addToQueue(player, worldName);

        // Send messages
        MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.join-queue"));
        MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.waiting-for-opponent"));
        MessageUtil.playSound(player, plugin.getConfig().getString("sounds.queue-join"));

        // Try to teleport
        TeleportHandler.tryTeleport(player);
    }

    private void handleWorldQueue(Player player, String worldName) {
        // Check if specified world exists and player has permission
        List<WorldManager.WorldSettings> accessibleWorlds = worldManager.getAccessibleWorlds(player);
        WorldManager.WorldSettings targetWorld = null;

        for (WorldManager.WorldSettings world : accessibleWorlds) {
            if (world.getName().equalsIgnoreCase(worldName) ||
                    world.getDisplayName().equalsIgnoreCase(worldName)) {
                targetWorld = world;
                break;
            }
        }

        if (targetWorld == null) {
            MessageUtil.sendMessage(player, "&cWorld not found or you don't have permission to use it.");
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
            return;
        }

        // Join queue with specific world
        handleJoinQueue(player, targetWorld.getName());
    }

    private void handleLeaveQueue(Player player) {
        if (queueHandler.isInQueue(player)) {
            queueHandler.removeFromQueue(player);
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.leave-queue"));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.queue-cleared"));
        } else {
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.not-in-queue"));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
        }
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("rtpqueue.admin")) {
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.no-permission"));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
            return;
        }

        // Use the centralized reload method
        plugin.reload();

        MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.reload"));
        MessageUtil.sendMessage(player, "&e⚠ For complete configuration changes, a server restart is recommended.");
        MessageUtil.playSound(player, plugin.getConfig().getString("sounds.queue-cleared"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            completions.add("cancel");

            // Add "world" option if multi-world is enabled
            if (plugin.getConfig().getBoolean("teleport.other-worlds.enabled", false)) {
                completions.add("world");
            }

            if (sender.hasPermission("rtpqueue.admin")) {
                completions.add("reload");
            }

            return filterCompletions(completions, args[0]);
        }

        // Tab complete for worlds
        if (args.length == 2 && args[0].equalsIgnoreCase("world")) {
            List<WorldManager.WorldSettings> accessibleWorlds = worldManager.getAccessibleWorlds(player);
            for (WorldManager.WorldSettings world : accessibleWorlds) {
                completions.add(world.getDisplayName());
            }
            return filterCompletions(completions, args[1]);
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> completions, String arg) {
        if (arg.isEmpty()) {
            return completions;
        }

        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(arg.toLowerCase()))
                .collect(Collectors.toList());
    }
}