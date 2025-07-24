package com.kingrbxd.rtpqueue.commands;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.QueueHandler;
import com.kingrbxd.rtpqueue.handlers.TeleportHandler;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class RTPQueueCommand implements CommandExecutor, TabCompleter {
    private final AdvancedRTPQueue plugin;
    private final QueueHandler queueHandler;

    public RTPQueueCommand(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        this.queueHandler = plugin.getQueueHandler();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            handleJoinQueue(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "cancel":
                handleLeaveQueue(player);
                break;

            case "reload":
                handleReload(player);
                break;

            default:
                MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.invalid-command"));
                MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
                break;
        }

        return true;
    }

    private void handleJoinQueue(Player player) {
        if (queueHandler.isInQueue(player)) {
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.already-in-queue"));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.error"));
        } else {
            queueHandler.addToQueue(player);
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.join-queue"));
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.waiting-for-opponent"));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.queue-join"));

            // ✅ Call teleport check when a player joins the queue
            TeleportHandler.tryTeleport(player);
        }
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
        plugin.reloadConfig();
        MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.reload"));
        MessageUtil.playSound(player, plugin.getConfig().getString("sounds.queue-cleared"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("cancel");
            if (sender.hasPermission("rtpqueue.admin")) {
                completions.add("reload");
            }
        }

        return completions;
    }
}
