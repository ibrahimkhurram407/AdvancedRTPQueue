package com.kingrbxd.rtpqueue.utils;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static Plugin plugin;

    public static void initialize(Plugin pl) {
        plugin = pl;
    }

    /**
     * Sends a formatted message to a player with hex color support.
     */
    public static void sendMessage(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        player.sendMessage(colorize(message));
    }

    /**
     * Sends a formatted message to the console or a player.
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(colorize(message));
    }

    /**
     * Sends a title and subtitle to a player.
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        if (title == null) title = "";
        if (subtitle == null) subtitle = "";
        player.sendTitle(colorize(title), colorize(subtitle), 10, 40, 10);
    }

    /**
     * Sends an action bar message to a player.
     */
    public static void sendActionBar(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () ->
                player.spigot().sendMessage(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(colorize(message)))
        );
    }

    /**
     * Plays a sound for a player, supporting both Sound objects and sound names.
     */
    public static void playSound(Player player, Object soundInput) {
        if (soundInput == null) return;

        try {
            Sound sound;
            if (soundInput instanceof Sound) {
                sound = (Sound) soundInput; // ✅ Supports direct Sound objects
            } else if (soundInput instanceof String) {
                sound = Sound.valueOf(((String) soundInput).toUpperCase()); // ✅ Supports sound names from config.yml
            } else {
                System.out.println("[AdvancedRTPQueue] Invalid sound input: " + soundInput);
                return;
            }

            player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
        } catch (IllegalArgumentException e) {
            System.out.println("[AdvancedRTPQueue] Invalid sound: " + soundInput);
        }
    }

    /**
     * Converts hex color codes (&#RRGGBB) to Minecraft color codes.
     */
    public static String colorize(String message) {
        if (message == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            ChatColor hexColor = ChatColor.of("#" + hexCode);
            matcher.appendReplacement(buffer, hexColor.toString());
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
