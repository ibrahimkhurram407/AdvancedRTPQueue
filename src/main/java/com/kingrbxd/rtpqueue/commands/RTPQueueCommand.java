package com.kingrbxd.rtpqueue.commands;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.handlers.WorldManager;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RTPQueueCommand - handles /rtpqueue commands and tab-completion.
 *
 * This implementation:
 *  - blocks joining while player has an active teleport session
 *  - supports joining default world, named worlds (display-name resolution)
 *  - supports admin force: /rtpqueue force <player> [world]
 */
public class RTPQueueCommand implements CommandExecutor, TabCompleter {
    private final AdvancedRTPQueue plugin;

    public RTPQueueCommand(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isPlayerSender = sender instanceof Player;
        Player playerSender = isPlayerSender ? (Player) sender : null;

        if (args.length == 0) {
            if (!isPlayerSender) {
                sender.sendMessage("This command must be run by a player to join the queue. Use console for admin actions.");
                return true;
            }
            if (!playerSender.hasPermission("rtpqueue.use")) {
                MessageUtil.sendMessage(playerSender, "no-permission");
                return true;
            }
            return handleJoinQueue(playerSender, null);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "world":
                if (!isPlayerSender) {
                    sender.sendMessage("Only players may join queues.");
                    return true;
                }
                if (!playerSender.hasPermission("rtpqueue.use")) {
                    MessageUtil.sendMessage(playerSender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    MessageUtil.sendMessage(playerSender, "invalid-command");
                    return true;
                }
                return handleJoinQueue(playerSender, args[1]);

            case "leave":
                if (!isPlayerSender) {
                    sender.sendMessage("Only players can leave queues.");
                    return true;
                }
                if (!playerSender.hasPermission("rtpqueue.use")) {
                    MessageUtil.sendMessage(playerSender, "no-permission");
                    return true;
                }
                return handleLeaveQueue(playerSender);

            case "reload":
                if (!sender.hasPermission("rtpqueue.admin")) {
                    if (isPlayerSender) MessageUtil.sendMessage(playerSender, "no-permission");
                    else sender.sendMessage("You don't have permission to run that command.");
                    return true;
                }
                if (isPlayerSender) return handleReload(playerSender);
                try {
                    if (plugin.reloadPlugin()) {
                        sender.sendMessage("Configuration reloaded.");
                    } else {
                        sender.sendMessage("Reload failed.");
                    }
                } catch (Exception e) {
                    sender.sendMessage("Reload failed: " + e.getMessage());
                    if (plugin.getConfig().getBoolean("plugin.debug")) e.printStackTrace();
                }
                return true;

            case "clear":
                if (!sender.hasPermission("rtpqueue.admin")) {
                    if (isPlayerSender) MessageUtil.sendMessage(playerSender, "no-permission");
                    else sender.sendMessage("You don't have permission to run that command.");
                    return true;
                }
                plugin.getQueueHandler().clearAllQueues();
                if (isPlayerSender) {
                    MessageUtil.sendMessage(playerSender, "queue-cleared");
                } else {
                    sender.sendMessage("All queues cleared.");
                }
                return true;

            case "force":
                if (!sender.hasPermission("rtpqueue.force")) {
                    if (isPlayerSender) MessageUtil.sendMessage(playerSender, "no-permission");
                    else sender.sendMessage("You don't have permission to run that command.");
                    return true;
                }
                if (args.length < 2) {
                    if (isPlayerSender) MessageUtil.sendMessage(playerSender, "invalid-command");
                    else sender.sendMessage("Usage: /rtpqueue force <player> [world]");
                    return true;
                }
                String targetName = args[1];
                String worldInput = args.length >= 3 ? args[2] : null;
                return handleForce(sender, targetName, worldInput);

            default:
                if (isPlayerSender) {
                    MessageUtil.sendMessage(playerSender, "invalid-command");
                } else {
                    sender.sendMessage("Invalid command. Usage: /rtpqueue [world <name>|leave|reload|clear|force <player> [world]]");
                }
                return true;
        }
    }

    private boolean handleJoinQueue(Player player, String worldInput) {
        if (player == null) return true;

        // Block joins when the player is currently in a teleport countdown / session
        if (plugin.getTeleportManager().hasActiveSession(player)) {
            if (plugin.getConfig().contains("messages.cannot-join-while-teleporting")) {
                MessageUtil.sendMessage(player, "cannot-join-while-teleporting");
            } else {
                String sessionWorld = plugin.getTeleportManager().getActiveSessionWorld(player);
                Map<String, String> ph = new HashMap<String, String>();
                ph.put("world", sessionWorld != null ? plugin.getWorldManager().getDisplayName(sessionWorld) : "");
                MessageUtil.sendMessage(player, "teleporting", ph);
            }
            return true;
        }

        if (!plugin.getCooldownManager().canJoinQueue(player)) {
            return true; // cooldown manager already sends message
        }

        String worldKey = worldInput;
        if (worldKey == null) {
            worldKey = plugin.getConfig().getString("teleport.default-world", "world");
        } else {
            String resolved = plugin.getWorldManager().resolveKeyByDisplayName(worldKey);
            if (resolved != null) worldKey = resolved;
        }

        WorldManager wm = plugin.getWorldManager();
        if (!wm.isValidWorld(worldKey)) {
            Map<String, String> ph = new HashMap<String, String>();
            ph.put("world", worldKey);
            MessageUtil.sendMessage(player, "invalid-world", ph);
            return true;
        }

        if (!hasWorldPermission(player, worldKey)) {
            Map<String, String> ph = new HashMap<String, String>();
            ph.put("world", wm.getDisplayName(worldKey));
            MessageUtil.sendMessage(player, "no-permission-world", ph);
            return true;
        }

        if (!plugin.getCooldownManager().canJoinWorldQueue(player, worldKey)) {
            return true;
        }

        if (plugin.getQueueHandler().isInQueue(player)) {
            String current = plugin.getQueueHandler().getPlayerQueueWorld(player);
            if (worldKey.equals(current)) {
                MessageUtil.sendMessage(player, "already-in-queue");
                return true;
            }
            if (plugin.getConfig().getBoolean("queue.allow-world-switching", true)) {
                plugin.getQueueHandler().removeFromQueue(player);
                if (plugin.getQueueHandler().addToQueue(player, worldKey)) {
                    plugin.getCooldownManager().setQueueJoinCooldown(player);
                    plugin.getCooldownManager().setPerWorldCooldown(player, worldKey);
                }
                return true;
            } else {
                MessageUtil.sendMessage(player, "already-in-queue");
                return true;
            }
        }

        if (plugin.getQueueHandler().addToQueue(player, worldKey)) {
            plugin.getCooldownManager().setQueueJoinCooldown(player);
            plugin.getCooldownManager().setPerWorldCooldown(player, worldKey);
        }
        return true;
    }

    private boolean handleLeaveQueue(Player player) {
        if (player == null) return true;

        if (plugin.getTeleportManager().hasActiveSession(player)) {
            plugin.getTeleportManager().cancelPlayerSession(player, "cancelled");
            MessageUtil.sendMessage(player, "cancelled-moved");
            return true;
        }

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

    private boolean handleReload(Player player) {
        if (player == null) return true;
        if (!player.hasPermission("rtpqueue.admin")) {
            MessageUtil.sendMessage(player, "no-permission");
            return true;
        }

        try {
            if (plugin.reloadPlugin()) {
                MessageUtil.sendMessage(player, "reload-success");
            } else {
                Map<String, String> ph = new HashMap<String, String>();
                ph.put("error", "Unknown error");
                MessageUtil.sendMessage(player, "reload-failed", ph);
            }
        } catch (Exception e) {
            Map<String, String> ph = new HashMap<String, String>();
            ph.put("error", e.getMessage());
            MessageUtil.sendMessage(player, "reload-failed", ph);
            if (plugin.getConfig().getBoolean("plugin.debug")) e.printStackTrace();
        }
        return true;
    }

    /**
     * Force logic: add target to queue (default or specified world).
     */
    private boolean handleForce(CommandSender sender, String targetName, String worldInput) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            if (sender instanceof Player) {
                Map<String, String> ph = new HashMap<String, String>();
                ph.put("player", targetName);
                MessageUtil.sendMessage((Player) sender, "invalid-player", ph);
            } else {
                sender.sendMessage("Player '" + targetName + "' is not online.");
            }
            return true;
        }

        // determine world key
        String worldKey;
        if (worldInput == null) {
            worldKey = plugin.getConfig().getString("teleport.default-world", "world");
        } else {
            String resolved = plugin.getWorldManager().resolveKeyByDisplayName(worldInput);
            worldKey = resolved != null ? resolved : worldInput;
        }

        WorldManager wm = plugin.getWorldManager();
        if (!wm.isValidWorld(worldKey)) {
            if (sender instanceof Player) {
                Map<String, String> ph = new HashMap<String, String>();
                ph.put("world", worldInput != null ? worldInput : worldKey);
                MessageUtil.sendMessage((Player) sender, "invalid-world", ph);
            } else {
                sender.sendMessage("Invalid world: " + (worldInput != null ? worldInput : worldKey));
            }
            return true;
        }

        // Cancel any active session
        if (plugin.getTeleportManager().hasActiveSession(target)) {
            plugin.getTeleportManager().cancelPlayerSession(target, "force");
        }

        // If in queue already
        if (plugin.getQueueHandler().isInQueue(target)) {
            String current = plugin.getQueueHandler().getPlayerQueueWorld(target);
            if (worldKey.equals(current)) {
                Map<String, String> ph = new HashMap<String, String>();
                ph.put("player", target.getName());
                ph.put("world", wm.getDisplayName(worldKey));
                if (sender instanceof Player) MessageUtil.sendMessage((Player) sender, "force-player-already-in-queue", ph);
                else sender.sendMessage("Player " + target.getName() + " is already in queue for " + wm.getDisplayName(worldKey));
                return true;
            } else {
                if (plugin.getConfig().getBoolean("queue.allow-world-switching", true)) {
                    plugin.getQueueHandler().removeFromQueue(target);
                    boolean added = plugin.getQueueHandler().addToQueue(target, worldKey);
                    Map<String, String> ph = new HashMap<String, String>();
                    ph.put("player", target.getName());
                    ph.put("world", wm.getDisplayName(worldKey));
                    if (added) {
                        if (sender instanceof Player) MessageUtil.sendMessage((Player) sender, "force-switched-player", ph);
                        else sender.sendMessage("Moved " + target.getName() + " to the queue for " + wm.getDisplayName(worldKey));
                    } else {
                        if (sender instanceof Player) MessageUtil.sendMessage((Player) sender, "force-failed", ph);
                        else sender.sendMessage("Failed to add " + target.getName() + " to the queue for " + wm.getDisplayName(worldKey));
                    }
                    return true;
                } else {
                    if (sender instanceof Player) MessageUtil.sendMessage((Player) sender, "force-switch-disabled");
                    else sender.sendMessage("World switching for queued players is disabled.");
                    return true;
                }
            }
        }

        // Not in queue -> add them
        boolean added = plugin.getQueueHandler().addToQueue(target, worldKey);
        Map<String, String> ph = new HashMap<String, String>();
        ph.put("player", target.getName());
        ph.put("world", wm.getDisplayName(worldKey));
        if (added) {
            if (sender instanceof Player) MessageUtil.sendMessage((Player) sender, "force-added-player", ph);
            else sender.sendMessage("Added " + target.getName() + " to the queue for " + wm.getDisplayName(worldKey));
            // notify target
            MessageUtil.sendMessage(target, "forced-added", Collections.singletonMap("world", wm.getDisplayName(worldKey)));
        } else {
            if (sender instanceof Player) MessageUtil.sendMessage((Player) sender, "force-failed", ph);
            else sender.sendMessage("Failed to add " + target.getName() + " to the queue for " + wm.getDisplayName(worldKey));
        }
        return true;
    }

    private boolean hasWorldPermission(Player player, String worldKey) {
        if (player == null) return false;
        if (player.hasPermission("rtpqueue.world.*")) return true;

        WorldManager wm = plugin.getWorldManager();
        WorldManager.WorldSettings settings = wm.getWorldSettings(worldKey);
        if (settings == null) return false;

        String configuredPerm = settings.getPermission();
        if (configuredPerm != null && !configuredPerm.isEmpty()) {
            return player.hasPermission(configuredPerm);
        }

        String defaultWorld = plugin.getConfig().getString("teleport.default-world", "world");
        if (worldKey.equals(defaultWorld) && player.hasPermission("rtpqueue.use")) return true;

        return player.hasPermission("rtpqueue.world." + worldKey.toLowerCase(Locale.ROOT));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isPlayer = sender instanceof Player;
        List<String> completions = new ArrayList<String>();
        if (args.length == 1) {
            completions.add("world");
            completions.add("leave");
            if (sender.hasPermission("rtpqueue.admin")) {
                completions.add("reload");
                completions.add("clear");
            }
            if (sender.hasPermission("rtpqueue.force")) {
                completions.add("force");
            }

            final String partial = args[0].toLowerCase(Locale.ROOT);
            return completions.stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("world")) {
                final String partial = args[1].toLowerCase(Locale.ROOT);
                Set<String> displayNames = plugin.getWorldManager().getTabCompleteWorldDisplayNames();
                return displayNames.stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                        .sorted()
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("force")) {
                final String partial = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                        .sorted()
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("force")) {
            final String partial = args[2].toLowerCase(Locale.ROOT);
            Set<String> displayNames = plugin.getWorldManager().getTabCompleteWorldDisplayNames();
            return displayNames.stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}