package com.kingrbxd.rtpqueue.utils;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling messages with color codes and sounds.
 */
public class MessageUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Map<String, String> cachedColorMessages = new HashMap<>();
    private static final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private static final long MESSAGE_THROTTLE_MS = 50; // Minimum time between messages to a player

    /**
     * Translate color codes in a message.
     *
     * @param message Message to translate
     * @return Translated message
     */
    public static String colorize(String message) {
        if (message == null) {
            return "";
        }

        // Check cache first
        if (cachedColorMessages.containsKey(message)) {
            return cachedColorMessages.get(message);
        }

        // Process hex colors (&#RRGGBB) if supported
        if (isHexSupported()) {
            Matcher matcher = HEX_PATTERN.matcher(message);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String hexColor = matcher.group(1);
                matcher.appendReplacement(sb, ChatColor.of("#" + hexColor).toString());
            }

            matcher.appendTail(sb);
            message = sb.toString();
        }

        // Process standard color codes
        String colorized = ChatColor.translateAlternateColorCodes('&', message);

        // Cache the result
        cachedColorMessages.put(message, colorized);

        return colorized;
    }

    /**
     * Clear all cached colorized messages.
     * Useful when reloading config to ensure messages reflect the new config.
     */
    public static void clearCachedMessages() {
        cachedColorMessages.clear();
    }

    /**
     * Check if hex color codes are supported.
     *
     * @return true if supported
     */
    private static boolean isHexSupported() {
        try {
            // Check if ChatColor.of method exists (1.16+)
            ChatColor.class.getMethod("of", String.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Send a message to a player with color codes.
     *
     * @param player Player to send message to
     * @param message Message to send
     */
    public static void sendMessage(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }

        // Throttle messages to prevent spam
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerUUID);

        if (lastTime != null && currentTime - lastTime < MESSAGE_THROTTLE_MS) {
            return;
        }

        lastMessageTime.put(playerUUID, currentTime);

        // Send the colorized message
        player.sendMessage(colorize(message));
    }

    /**
     * Play a sound for a player.
     *
     * @param player Player to play sound for
     * @param soundName Name of the sound to play
     */
    public static void playSound(Player player, String soundName) {
        if (player == null || soundName == null || soundName.isEmpty()) {
            return;
        }

        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            // Sound doesn't exist - log with debug only
            if (AdvancedRTPQueue.getInstance().getConfig().getBoolean("debug", false)) {
                AdvancedRTPQueue.getInstance().getLogger().warning("Invalid sound: " + soundName);
            }
        }
    }

    /**
     * Send a title message to a player.
     *
     * @param player Player to send title to
     * @param title Title text
     * @param subtitle Subtitle text
     * @param fadeIn Fade in time in ticks
     * @param stay Stay time in ticks
     * @param fadeOut Fade out time in ticks
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) {
            return;
        }

        player.sendTitle(
                title != null ? colorize(title) : "",
                subtitle != null ? colorize(subtitle) : "",
                fadeIn,
                stay,
                fadeOut
        );
    }

    /**
     * Process placeholders in a message.
     *
     * @param message Message with placeholders
     * @param replacements Map of placeholder to replacement
     * @return Processed message
     */
    public static String processPlaceholders(String message, Map<String, String> replacements) {
        if (message == null || replacements == null) {
            return message;
        }

        String processed = message;

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return processed;
    }
}