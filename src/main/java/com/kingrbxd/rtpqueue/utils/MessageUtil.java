package com.kingrbxd.rtpqueue.utils;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtil {
    private static AdvancedRTPQueue plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Map<String, String> cachedMessages = new HashMap<>();

    /**
     * Initialize the message util with plugin instance
     *
     * @param pluginInstance The main plugin instance
     */
    public static void initialize(AdvancedRTPQueue pluginInstance) {
        plugin = pluginInstance;
    }

    /**
     * Clear all cached messages to force reloading from config
     */
    public static void clearCachedMessages() {
        cachedMessages.clear();
    }

    /**
     * Converts color codes and hex color codes in a string.
     *
     * @param message The message to colorize
     * @return The colorized message
     */
    public static String colorize(String message) {
        if (message == null) return "";

        // Check if message is already cached
        if (cachedMessages.containsKey(message)) {
            return cachedMessages.get(message);
        }

        // Convert hex colors (&#RRGGBB format)
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
        }

        matcher.appendTail(buffer);

        // Convert traditional color codes
        String colorized = ChatColor.translateAlternateColorCodes('&', buffer.toString());

        // Cache the result
        cachedMessages.put(message, colorized);

        return colorized;
    }

    /**
     * Sends a colorized message to a player.
     *
     * @param player  The player to send the message to
     * @param message The message to send
     */
    public static void sendMessage(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        player.sendMessage(colorize(message));
    }

    /**
     * Sends a title and subtitle to a player.
     *
     * @param player   The player to send the title to
     * @param title    The title to send
     * @param subtitle The subtitle to send
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        player.sendTitle(colorize(title), colorize(subtitle), 10, 40, 10);
    }

    /**
     * Sends an action bar message to a player.
     *
     * @param player  The player to send the action bar to
     * @param message The message to send
     */
    public static void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colorize(message)));
    }

    /**
     * Plays a sound for a player.
     *
     * @param player The player to play the sound for
     * @param sound  The sound to play
     */
    public static void playSound(Player player, String sound) {
        if (sound == null || sound.isEmpty()) return;

        try {
            Sound bukkitSound = Sound.valueOf(sound);
            player.playSound(player.getLocation(), bukkitSound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + sound);
        }
    }
}