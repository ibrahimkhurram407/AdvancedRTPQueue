package com.kingrbxd.rtpqueue.utils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for handling messages and color codes.
 */
public class MessageUtil {
    // Pattern for RGB hex color codes (#RRGGBB)
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /**
     * Send a colored message to a player.
     *
     * @param player The player to send the message to
     * @param message The message to send
     */
    public static void sendMessage(Player player, String message) {
        if (player != null && message != null && !message.isEmpty()) {
            player.sendMessage(formatMessage(message));
        }
    }

    /**
     * Send a colored message to a command sender (player or console).
     *
     * @param sender The command sender to send the message to
     * @param message The message to send
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isEmpty()) {
            sender.sendMessage(formatMessage(message));
        }
    }

    /**
     * Send a message to the action bar of a player.
     *
     * @param player The player to send the action bar to
     * @param message The message to display
     */
    public static void sendActionBar(Player player, String message) {
        if (player != null && message != null && !message.isEmpty()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(formatMessage(message)));
        }
    }

    /**
     * Send a title and subtitle to a player.
     *
     * @param player The player to send the title to
     * @param title The main title text
     * @param subtitle The subtitle text
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        if (player != null) {
            String formattedTitle = title != null ? formatMessage(title) : "";
            String formattedSubtitle = subtitle != null ? formatMessage(subtitle) : "";

            player.sendTitle(formattedTitle, formattedSubtitle, 10, 70, 20);
        }
    }

    /**
     * Play a sound for a player.
     *
     * @param player The player to play the sound for
     * @param soundName The name of the sound to play
     */
    public static void playSound(Player player, String soundName) {
        if (player != null && soundName != null && !soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
            } catch (IllegalArgumentException e) {
                // Ignore invalid sounds
            }
        }
    }

    /**
     * Format a message with color codes and hex colors.
     *
     * @param message The message to format
     * @return The formatted message
     */
    public static String formatMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Replace hex color codes
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hexColor = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hexColor).toString());
        }

        matcher.appendTail(buffer);

        // Replace standard color codes
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}