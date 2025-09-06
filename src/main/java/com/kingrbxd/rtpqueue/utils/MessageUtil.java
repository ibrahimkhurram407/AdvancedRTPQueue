package com.kingrbxd.rtpqueue.utils;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling messages with color codes and sounds.
 */
public class MessageUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    // Use concurrent maps for safety if accessed from multiple threads/tasks
    private static final Map<String, String> cachedColorMessages = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
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
        String cached = cachedColorMessages.get(message);
        if (cached != null) return cached;

        String original = message;

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

        // Cache based on original input
        cachedColorMessages.put(original, colorized);

        return colorized;
    }

    /**
     * Replace placeholders in a template string with the provided map, then colorize the result.
     * Supports tokens in the form {key} and %key% for convenience.
     *
     * Example:
     *   template = "Teleporting to {world} in {time}s..."
     *   placeholders = {"world":"&6Overworld", "time":"5"}
     *   => returns colorized string with the substituted values.
     *
     * @param template the message template (may contain {key} or %key% tokens)
     * @param placeholders map of tokens -> replacement values (null-safe)
     * @return processed and colorized string
     */
    public static String processPlaceholders(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) return "";
        String result = template;

        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                String key = e.getKey();
                String val = e.getValue() == null ? "" : e.getValue();

                // Replace {key}
                result = result.replace("{" + key + "}", val);
                // Replace %key% (common placeholder style)
                result = result.replace("%" + key + "%", val);
            }
        }

        // Finally colorize
        return colorize(result);
    }

    /**
     * Clear all cached colorized messages.
     * Useful when reloading config to ensure messages reflect the new config.
     */
    public static void clearCachedMessages() {
        cachedColorMessages.clear();
    }

    private static boolean isHexSupported() {
        try {
            ChatColor.class.getMethod("of", String.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Send a message to a CommandSender (console or player) with color codes.
     *
     * @param sender CommandSender
     * @param message Message to send
     */
    public static void sendMessage(org.bukkit.command.CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        sender.sendMessage(colorize(message));
    }

    /**
     * Send a throttled, colorized message to a Player.
     *
     * @param player Player to send message to
     * @param message Message to send
     */
    public static void sendMessage(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;

        // Throttle messages to prevent spam
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerUUID);

        if (lastTime != null && currentTime - lastTime < MESSAGE_THROTTLE_MS) {
            return;
        }

        lastMessageTime.put(playerUUID, currentTime);

        player.sendMessage(colorize(message));
    }

    /**
     * Send an action bar message to a player.
     * Uses Bungee TextComponent via spigot if available; falls back to normal chat.
     *
     * @param player Player
     * @param message Message
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;

        try {
            // Use spigot API to send action bar
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new TextComponent(colorize(message)));
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // Fallback: send as normal message
            player.sendMessage(colorize(message));
        } catch (Throwable t) {
            player.sendMessage(colorize(message));
        }
    }

    /**
     * Play a sound for a player.
     *
     * @param player Player to play sound for
     * @param soundName Name of the sound to play (case-sensitive enum name preferred)
     */
    public static void playSound(Player player, String soundName) {
        if (player == null || soundName == null || soundName.isEmpty()) {
            return;
        }

        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            // Try uppercase replacement (some config values might be lowercase)
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ex) {
                // Sound doesn't exist - log with debug only
                AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
                if (plugin != null && plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("Invalid sound: " + soundName);
                }
            }
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            // Older server doesn't have sounds or API differs; ignore silently
        } catch (Exception ex) {
            AdvancedRTPQueue plugin = AdvancedRTPQueue.getInstance();
            if (plugin != null && plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("Error playing sound '" + soundName + "': " + ex.getMessage());
            }
        }
    }

    /**
     * Send a title to a player (wrapper).
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        if (player == null) return;
        player.sendTitle(title != null ? colorize(title) : "", subtitle != null ? colorize(subtitle) : "", 10, 70, 20);
    }
}