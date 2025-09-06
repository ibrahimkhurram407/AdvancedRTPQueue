package com.kingrbxd.rtpqueue.commands;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.CooldownManager;
import com.kingrbxd.rtpqueue.handlers.QueueHandler;
import com.kingrbxd.rtpqueue.handlers.TeleportHandler;
import com.kingrbxd.rtpqueue.handlers.WorldManager;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main command handler for the RTP Queue plugin.
 */
public class RTPQueueCommand implements CommandExecutor, TabCompleter {
    private final AdvancedRTPQueue plugin;

    public RTPQueueCommand(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Reload command - accessible from console
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rtpqueue.admin")) {
                MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.no-permission"));
                return true;
            }

            plugin.reloadConfig();
            MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.reload"));
            return true;
        }

        // Force queue command - accessible from console
        if (args.length >= 2 && args[0].equalsIgnoreCase("forcequeue")) {
            if (!sender.hasPermission("rtpqueue.admin")) {
                MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.no-permission"));
                return true;
            }

            String playerName = args[1];
            Player targetPlayer = Bukkit.getPlayerExact(playerName);

            if (targetPlayer == null || !targetPlayer.isOnline()) {
                MessageUtil.sendMessage(sender, "&#ff0000❌ &cPlayer not found or not online!");
                return true;
            }

            // Default world or specified world
            String worldName = plugin.getWorldManager().getDefaultWorldSettings().getName();
            if (args.length >= 3) {
                worldName = args[2];
                if (!plugin.getWorldManager().isValidWorld(worldName)) {
                    MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.invalid-world"));
                    return true;
                }
            }

            // Force add player to queue
            plugin.getQueueHandler().forceAddToQueue(targetPlayer, worldName);

            // Notify player they were added to queue
            MessageUtil.sendMessage(targetPlayer, plugin.getConfig().getString("messages.join-queue"));
            MessageUtil.playSound(targetPlayer, plugin.getConfig().getString("sounds.queue-join"));

            // Notify admin
            MessageUtil.sendMessage(sender, "&#00ff00✔ &aForced player " + targetPlayer.getName() +
                    " into queue for world: " + worldName);

            // Try to teleport if enough players
            TeleportHandler.tryTeleport(targetPlayer);

            return true;
        }

        // Player-only commands below
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, "&#ff5555⛔ &cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Cancel command
        if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
            return handleCancelQueue(player);
        }

        // World command
        if (args.length > 1 && args[0].equalsIgnoreCase("world")) {
            String worldName = args[1];
            return handleJoinWorldQueue(player, worldName);
        }

        // Default command - join queue for default world
        if (args.length == 0) {
            return handleJoinQueue(player);
        }

        // Unknown command
        MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.invalid-command"));
        return true;
    }

    /**
     * Handle joining the default world queue.
     *
     * @param player The player
     * @return true always
     */
    private boolean handleJoinQueue(Player player) {
        QueueHandler queueHandler = plugin.getQueueHandler();
        CooldownManager cooldownManager = plugin.getCooldownManager();
        WorldManager worldManager = plugin.getWorldManager();

        // Check if player is already in a teleport
        if (TeleportHandler.hasPendingTeleport(player)) {
            return true;
        }

        // Get default world
        WorldManager.WorldSettings defaultWorld = worldManager.getDefaultWorldSettings();
        String worldName = defaultWorld.getName();

        // Check if player has permission
        if (!player.hasPermission("rtpqueue.use")) {
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.no-permission"));
            return true;
        }

        // Check cooldown
        if (!cooldownManager.canJoinQueue(player, worldName)) {
            return true;
        }

        // Add to queue
        boolean added = queueHandler.addToQueue(player, worldName);
        if (added) {
            // Set cooldown
            cooldownManager.setQueueCooldown(player, worldName);

            // Send join message
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.join-queue"));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.queue-join"));

            // Check if queue is ready for teleport
            TeleportHandler.tryTeleport(player);

            // If not ready, send waiting message
            if (queueHandler.isInQueue(player)) {
                MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.waiting-for-opponent"));
            }
        }

        return true;
    }

    /**
     * Handle joining a specific world queue.
     *
     * @param player The player
     * @param worldName The world name
     * @return true always
     */
    private boolean handleJoinWorldQueue(Player player, String worldName) {
        QueueHandler queueHandler = plugin.getQueueHandler();
        CooldownManager cooldownManager = plugin.getCooldownManager();
        WorldManager worldManager = plugin.getWorldManager();

        // Check if player is already in a teleport
        if (TeleportHandler.hasPendingTeleport(player)) {
            return true;
        }

        // Check if world is valid
        if (!worldManager.isValidWorld(worldName)) {
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.invalid-world"));
            return true;
        }

        // Check if player has permission for this world
        WorldManager.WorldSettings worldSettings = worldManager.getWorldSettings(worldName);
        if (worldSettings.getPermission() != null && !worldSettings.getPermission().isEmpty() &&
                !player.hasPermission(worldSettings.getPermission())) {
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.no-permission"));
            return true;
        }

        // Check cooldown
        if (!cooldownManager.canJoinQueue(player, worldName)) {
            return true;
        }

        // Add to queue
        boolean added = queueHandler.addToQueue(player, worldName);
        if (added) {
            // Set cooldown
            cooldownManager.setQueueCooldown(player, worldName);

            // Send join message
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.join-queue"));
            MessageUtil.playSound(player, plugin.getConfig().getString("sounds.queue-join"));

            // Check if queue is ready for teleport
            TeleportHandler.tryTeleport(player);

            // If not ready, send waiting message
            if (queueHandler.isInQueue(player)) {
                MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.waiting-for-opponent"));
            }
        }

        return true;
    }

    /**
     * Handle cancelling queue.
     *
     * @param player The player
     * @return true always
     */
    private boolean handleCancelQueue(Player player) {
        QueueHandler queueHandler = plugin.getQueueHandler();

        // Check if player is in queue
        if (!queueHandler.isInQueue(player)) {
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.not-in-queue"));
            return true;
        }

        // Remove from queue
        queueHandler.removeFromQueue(player);
        MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.leave-queue"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("cancel");
            completions.add("world");

            if (sender.hasPermission("rtpqueue.admin")) {
                completions.add("reload");
                completions.add("forcequeue");
            }

            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("world")) {
                // Suggest valid worlds
                completions.addAll(plugin.getWorldManager().getValidWorldNames());
                return filterCompletions(completions, args[1]);
            }

            if (args[0].equalsIgnoreCase("forcequeue") && sender.hasPermission("rtpqueue.admin")) {
                // Suggest online players
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("forcequeue") && sender.hasPermission("rtpqueue.admin")) {
                // Suggest valid worlds
                completions.addAll(plugin.getWorldManager().getValidWorldNames());
                return filterCompletions(completions, args[2]);
            }
        }

        return completions;
    }

    /**
     * Filter completions based on partial input.
     *
     * @param completions The full list of completions
     * @param partial The partial input to filter by
     * @return Filtered list of completions
     */
    private List<String> filterCompletions(List<String> completions, String partial) {
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                .collect(Collectors.toList());
    }
}