package com.kingrbxd.rtpqueue.utils;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Complete message utility with all features implemented
 */
public class MessageUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Map<String, String> messageCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static AdvancedRTPQueue plugin;
    private static String prefix;

    private static final long MESSAGE_THROTTLE_MS = 100;

    public static void initialize(AdvancedRTPQueue pluginInstance) {
        plugin = pluginInstance;
        loadPrefix();
    }

    private static void loadPrefix() {
        if (plugin != null) {
            prefix = colorize(plugin.getConfig().getString("ui.prefix", "&8[&6RTP Queue&8] &r"));
        }
    }

    public static void clearCache() {
        messageCache.clear();
        loadPrefix();
    }

    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        String cached = messageCache.get(message);
        if (cached != null) {
            return cached;
        }

        String result = message;

        // Process hex colors
        if (supportsHexColors()) {
            Matcher matcher = HEX_PATTERN.matcher(result);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String hexColor = matcher.group(1);
                try {
                    matcher.appendReplacement(sb, ChatColor.of("#" + hexColor).toString());
                } catch (Exception e) {
                    matcher.appendReplacement(sb, "&" + hexColor.charAt(0));
                }
            }

            matcher.appendTail(sb);
            result = sb.toString();
        }

        // Process standard color codes
        result = ChatColor.translateAlternateColorCodes('&', result);

        messageCache.put(message, result);
        return result;
    }

    public static String processPlaceholders(String message, Map<String, String> placeholders) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        String result = message;

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue() != null ? entry.getValue() : "";

                result = result.replace("{" + key + "}", value);
                result = result.replace("%" + key + "%", value);
            }
        }

        return colorize(result);
    }

    public static void sendMessage(Player player, String messageKey) {
        sendMessage(player, messageKey, null);
    }

    public static void sendMessage(Player player, String messageKey, Map<String, String> placeholders) {
        if (player == null || messageKey == null) {
            return;
        }

        // Check throttling
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(playerUUID);

        if (lastTime != null && currentTime - lastTime < MESSAGE_THROTTLE_MS) {
            return;
        }

        lastMessageTime.put(playerUUID, currentTime);

        String message = getMessage(messageKey);
        if (message.isEmpty()) {
            return;
        }

        // Process placeholders
        message = processPlaceholders(message, placeholders);

        // Add prefix if not disabled for this message type
        if (shouldAddPrefix(messageKey)) {
            message = prefix + message;
        }

        player.sendMessage(message);
    }

    public static void sendActionBar(Player player, String messageKey) {
        sendActionBar(player, messageKey, null);
    }

    public static void sendActionBar(Player player, String messageKey, Map<String, String> placeholders) {
        if (player == null || messageKey == null) {
            return;
        }

        String message = getConfigMessage(messageKey);
        if (message.isEmpty()) {
            return;
        }

        message = processPlaceholders(message, placeholders);

        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } catch (Exception e) {
            // Fallback to chat message
            player.sendMessage(message);
        }
    }

    public static void sendTitle(Player player, String titleKey, String subtitleKey) {
        sendTitle(player, titleKey, subtitleKey, null);
    }

    public static void sendTitle(Player player, String titleKey, String subtitleKey, Map<String, String> placeholders) {
        if (player == null) {
            return;
        }

        String title = "";
        String subtitle = "";

        if (titleKey != null) {
            String configPath = "titles." + titleKey + ".title";
            title = processPlaceholders(plugin.getConfig().getString(configPath, ""), placeholders);
        }

        if (subtitleKey != null) {
            String configPath = "titles." + subtitleKey + ".subtitle";
            subtitle = processPlaceholders(plugin.getConfig().getString(configPath, ""), placeholders);
        }

        // Get title timing from config
        String timingPath = "titles." + titleKey + ".";
        int fadeIn = plugin.getConfig().getInt(timingPath + "fade-in", 10);
        int stay = plugin.getConfig().getInt(timingPath + "stay", 40);
        int fadeOut = plugin.getConfig().getInt(timingPath + "fade-out", 10);

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    public static void playSound(Player player, String soundKey) {
        if (player == null || soundKey == null || !plugin.getConfig().getBoolean("sounds.enabled", true)) {
            return;
        }

        String configPath = "sounds." + soundKey + ".";
        String soundName = plugin.getConfig().getString(configPath + "sound", "");

        if (soundName.isEmpty()) {
            return;
        }

        double volume = plugin.getConfig().getDouble(configPath + "volume", 1.0);
        double pitch = plugin.getConfig().getDouble(configPath + "pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase().replace(".", "_"));
            player.playSound(player.getLocation(), sound, (float) volume, (float) pitch);
        } catch (IllegalArgumentException e) {
            // Try with exact name
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, (float) volume, (float) pitch);
            } catch (IllegalArgumentException ex) {
                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().warning("Invalid sound: " + soundName);
                }
            }
        }
    }

    public static void spawnParticles(Player player, String particleKey) {
        if (player == null || particleKey == null || !plugin.getConfig().getBoolean("particles.enabled", true)) {
            return;
        }

        String configPath = "particles." + particleKey + ".";
        String particleName = plugin.getConfig().getString(configPath + "particle", "");

        if (particleName.isEmpty()) {
            return;
        }

        int count = plugin.getConfig().getInt(configPath + "count", 10);
        double spread = plugin.getConfig().getDouble(configPath + "spread", 1.0);

        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase());
            player.getWorld().spawnParticle(particle, player.getLocation(), count, spread, spread, spread);
        } catch (IllegalArgumentException e) {
            if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                plugin.getLogger().warning("Invalid particle: " + particleName);
            }
        }
    }

    private static String getMessage(String key) {
        if (plugin == null) {
            return "";
        }

        return plugin.getConfig().getString("messages." + key, "");
    }

    private static String getConfigMessage(String key) {
        if (plugin == null) {
            return "";
        }

        return plugin.getConfig().getString("ui.action-bar." + key, "");
    }

    private static boolean shouldAddPrefix(String messageKey) {
        if (plugin == null) {
            return true;
        }

        return !plugin.getConfig().getStringList("ui.no-prefix-messages").contains(messageKey);
    }

    private static boolean supportsHexColors() {
        try {
            ChatColor.class.getMethod("of", String.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static void clearPlayerFromCache(UUID playerUUID) {
        lastMessageTime.remove(playerUUID);
    }

    public static String formatTime(int seconds) {
        if (seconds <= 0) {
            return "0s";
        }

        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;

        if (minutes > 0) {
            return minutes + "m " + remainingSeconds + "s";
        } else {
            return remainingSeconds + "s";
        }
    }

    public static String createProgressBar(int current, int max, int length) {
        if (max <= 0) {
            return "";
        }

        double percentage = (double) current / max;
        int completed = (int) (percentage * length);

        String completedColor = plugin.getConfig().getString("ui.progress-bar.completed-color", "&a");
        String remainingColor = plugin.getConfig().getString("ui.progress-bar.remaining-color", "&7");
        String character = plugin.getConfig().getString("ui.progress-bar.character", "▌");

        StringBuilder bar = new StringBuilder();
        bar.append(colorize(completedColor));

        for (int i = 0; i < completed; i++) {
            bar.append(character);
        }

        bar.append(colorize(remainingColor));

        for (int i = completed; i < length; i++) {
            bar.append(character);
        }

        return bar.toString();
    }
}