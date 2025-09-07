package com.kingrbxd.rtpqueue.utils;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MessageUtil - updated to support hex colors (#RRGGBB) in config strings.
 *
 * Supported color formats:
 *  - Legacy & codes (e.g. &aHello)
 *  - Hex codes anywhere in text as #RRGGBB (e.g. "This is #ff0000red")
 *  - Hex codes prefixed with ampersand like &#RRGGBB (e.g. "This is &#ff0000red")
 *  - Also supports tags like <#ff0000> if used (the regex looks for #RRGGBB anywhere)
 *
 * Behavior:
 *  - Translates '&' legacy codes first, then replaces hex tokens with the platform ChatColor representation.
 *  - Uses reflection to call org.bukkit.ChatColor.of(...) when available at runtime so this class compiles
 *    against older Bukkit/Spigot API versions that don't expose ChatColor.of at compile time.
 *  - Falls back to net.md_5.bungee.api.ChatColor.of(...) if Bukkit's method isn't present at runtime.
 */
public final class MessageUtil {
    private static AdvancedRTPQueue plugin;
    // Accept both "#RRGGBB" and "&#RRGGBB" (the optional leading '&' is consumed so it won't remain in the output)
    private static final Pattern HEX_PATTERN = Pattern.compile("&?#([A-Fa-f0-9]{6})");

    private MessageUtil() { /* static helper */ }

    public static void initialize(AdvancedRTPQueue pl) {
        plugin = pl;
    }

    /**
     * Optional no-op for compatibility with callers expecting a clearCache method.
     */
    public static void clearCache() {
        // kept for compatibility
    }

    /**
     * Clear any per-player UI (action-bar/title) state.
     */
    public static void clearPlayerFromCache(Player player) {
        if (player == null || plugin == null) return;
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        } catch (Throwable ignored) {}
        try {
            player.sendTitle("", "", 0, 1, 0);
        } catch (Throwable ignored) {}
    }

    private static String getPrefix() {
        if (plugin == null) return "";
        String p = plugin.getConfig().getString("plugin.prefix", "");
        return colorize(p);
    }

    /**
     * Colorize a message:
     *  - translate legacy '&' codes
     *  - convert hex codes (#RRGGBB or &#RRGGBB) to platform ChatColor sequences
     *
     * Implementation note: org.bukkit.ChatColor.of(...) may not exist at compile time depending on the API version
     * you're compiling against. To avoid a compile-time error we call Bukkit's ChatColor.of via reflection and fall
     * back to Bungee's ChatColor.of when necessary.
     */
    public static String colorize(String input) {
        if (input == null) return "";
        // first translate legacy & codes
        String colored = ChatColor.translateAlternateColorCodes('&', input);

        // find any hex sequences and replace with ChatColor.of("#RRGGBB").toString()
        Matcher matcher = HEX_PATTERN.matcher(colored);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = "#" + matcher.group(1);
            String repl = toChatColorString(hex);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Attempt to obtain the ChatColor representation for a hex color.
     *
     * Strategy:
     * 1. Try org.bukkit.ChatColor.of(String) via reflection (so code compiles against older API).
     * 2. If that isn't present, try org.bukkit.ChatColor.of(java.awt.Color) via reflection.
     * 3. If Bukkit's ChatColor.of isn't available at runtime, fall back to net.md_5.bungee.api.ChatColor.of(hex).
     * 4. If everything fails, return empty string (strip color token).
     */
    private static String toChatColorString(String hex) {
        if (hex == null) return "";

        // Try Bukkit's ChatColor.of(String)
        try {
            java.lang.reflect.Method m = ChatColor.class.getMethod("of", String.class);
            Object chatColor = m.invoke(null, hex);
            if (chatColor != null) return chatColor.toString();
        } catch (NoSuchMethodException nsme) {
            // try next possibility below
        } catch (Throwable ignored) {
            // reflection invoke failed for some reason; try alternative strategies
        }

        // Try Bukkit's ChatColor.of(java.awt.Color)
        try {
            java.lang.reflect.Method m2 = ChatColor.class.getMethod("of", java.awt.Color.class);
            java.awt.Color awt = java.awt.Color.decode(hex);
            Object chatColor = m2.invoke(null, awt);
            if (chatColor != null) return chatColor.toString();
        } catch (NoSuchMethodException nsme) {
            // not present; fall through
        } catch (Throwable ignored) {
            // ignore and try bungee
        }

        // Fallback to Bungee ChatColor.of (commonly available at runtime on Spigot/Paper)
        try {
            return net.md_5.bungee.api.ChatColor.of(hex).toString();
        } catch (Throwable ignored) {
            // give up and return empty string so the token is removed
        }

        return "";
    }

    private static String getMessageRaw(String key) {
        if (plugin == null || key == null) return key;
        String path = "messages." + key;
        String msg = plugin.getConfig().getString(path, null);
        return msg;
    }

    private static String getActionBarRaw(String key) {
        if (plugin == null || key == null) return null;
        String path = "ui.action-bar." + key;
        String msg = plugin.getConfig().getString(path, null);
        return msg;
    }

    private static String getTitlePart(String key, String part) {
        if (plugin == null || key == null) return null;
        String path = "titles." + key + "." + part;
        return plugin.getConfig().getString(path, null);
    }

    private static String applyPlaceholders(String msg, Map<String, String> placeholders) {
        if (msg == null) return "";
        if (placeholders == null || placeholders.isEmpty()) return msg;
        String out = msg;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", Objects.toString(e.getValue(), ""));
        }
        return out;
    }

    public static void sendMessage(Player player, String key) {
        sendMessage(player, key, null);
    }

    public static void sendMessage(Player player, String key, Map<String, String> placeholders) {
        if (player == null || plugin == null) return;
        String raw = getMessageRaw(key);
        if (raw == null) raw = key;
        String withPlaceholders = applyPlaceholders(raw, placeholders);
        String full = getPrefix() + colorize(withPlaceholders);
        player.sendMessage(full);
    }

    public static void sendActionBar(Player player, String key, Map<String, String> placeholders) {
        if (player == null || plugin == null) return;

        String raw = getActionBarRaw(key);
        if (raw == null) raw = getMessageRaw(key);
        if (raw == null) return;

        String out = colorize(applyPlaceholders(raw, placeholders));
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(out));
        } catch (Throwable ignored) {
            player.sendMessage(out);
        }
    }

    public static void sendActionBar(Player player, String key) {
        sendActionBar(player, key, null);
    }

    public static void sendTitle(Player player, String titleKey, String subtitleKey, Map<String, String> placeholders) {
        if (player == null || plugin == null) return;

        String titleRaw = getTitlePart(titleKey, "title");
        String subtitleRaw = getTitlePart(subtitleKey, "subtitle");

        if (titleRaw == null) titleRaw = getMessageRaw(titleKey);
        if (subtitleRaw == null) subtitleRaw = getMessageRaw(subtitleKey);

        if (titleRaw == null && subtitleRaw == null) return;

        String title = colorize(applyPlaceholders(titleRaw != null ? titleRaw : "", placeholders));
        String subtitle = colorize(applyPlaceholders(subtitleRaw != null ? subtitleRaw : "", placeholders));

        int fadeIn = plugin.getConfig().getInt("titles." + titleKey + ".fade-in", 10);
        int stay = plugin.getConfig().getInt("titles." + titleKey + ".stay", 40);
        int fadeOut = plugin.getConfig().getInt("titles." + titleKey + ".fade-out", 10);

        try {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        } catch (Throwable ignored) {
            if (!title.isEmpty()) player.sendMessage(title);
            if (!subtitle.isEmpty()) player.sendMessage(subtitle);
        }
    }

    public static void sendTitle(Player player, String titleKey, String subtitleKey) {
        sendTitle(player, titleKey, subtitleKey, null);
    }

    public static void playSound(Player player, String soundKey) {
        if (player == null || plugin == null) return;
        if (!plugin.getConfig().getBoolean("sounds.enabled", true)) return;

        String base = "sounds." + soundKey;
        String soundName = plugin.getConfig().getString(base + ".sound", null);
        if (soundName == null) return;

        try {
            Sound s = Sound.valueOf(soundName);
            float volume = (float) plugin.getConfig().getDouble(base + ".volume", 1.0);
            float pitch = (float) plugin.getConfig().getDouble(base + ".pitch", 1.0);
            player.playSound(player.getLocation(), s, volume, pitch);
        } catch (IllegalArgumentException ex) {
            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().warning("Unknown sound in config: " + soundName + " for key: " + soundKey);
            }
        } catch (Throwable ignored) {}
    }

    public static void spawnParticles(Player player, String particleKey) {
        if (player == null || plugin == null) return;
        try {
            ParticleUtil.spawnConfiguredParticle(plugin, player.getLocation(), particleKey);
        } catch (Throwable t) {
            if (plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().warning("Failed to spawn particles for key " + particleKey + ": " + t.getMessage());
            }
        }
    }

    public static String formatTime(long totalSeconds) {
        if (totalSeconds <= 0) return "0s";

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d");
            if (hours > 0) sb.append(" ").append(hours).append("h");
            return sb.toString();
        }
        if (hours > 0) {
            sb.append(hours).append("h");
            if (minutes > 0) sb.append(" ").append(minutes).append("m");
            return sb.toString();
        }
        if (minutes > 0) {
            sb.append(minutes).append("m");
            if (seconds > 0) sb.append(" ").append(seconds).append("s");
            return sb.toString();
        }
        sb.append(seconds).append("s");
        return sb.toString();
    }

    public static String formatTimeMillis(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        return formatTime(seconds);
    }

    public static void clearPlayerFromCache(UUID uuid) {
        if (uuid == null || plugin == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) clearPlayerFromCache(player);
    }
}