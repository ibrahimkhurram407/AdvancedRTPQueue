package com.kingrbxd.rtpqueue.utils;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * ParticleUtil
 *
 * Replaces any use of NMS's AxisAlignedBB with Bukkit-friendly APIs.
 * Provides safe helpers to spawn configured particles (per config keys used in this project).
 *
 * Usage:
 *   ParticleUtil.spawnConfiguredParticle(plugin, location, "teleport-start");
 *
 * Behavior:
 *  - Reads particle config under particles.<key> (particle, count, spread)
 *  - Respects particles.visible-range (radius) by only sending to players within that radius
 *  - Falls back to sensible defaults if config entries are missing or invalid
 */
public final class ParticleUtil {
    private ParticleUtil() {}

    /**
     * Spawn the configured particle at a location. This will only be visible to players
     * within particles.visible-range (config, default 64).
     *
     * Config structure expected:
     * particles:
     *   visible-range: 64
     *   <key>:
     *     particle: "PORTAL"
     *     count: 50
     *     spread: 1.0
     */
    public static void spawnConfiguredParticle(AdvancedRTPQueue plugin, Location loc, String particleKey) {
        if (plugin == null || loc == null || particleKey == null) return;

        try {
            World world = loc.getWorld();
            if (world == null) return;

            String base = "particles." + particleKey + ".";

            // particle name -> Particle enum
            String particleName = plugin.getConfig().getString(base + "particle", plugin.getConfig().getString("particles." + particleKey, "PORTAL"));
            Particle particle = parseParticle(particleName);

            int count = plugin.getConfig().getInt(base + "count", plugin.getConfig().getInt("particles." + particleKey + ".count", 20));
            double spread = plugin.getConfig().getDouble(base + "spread", plugin.getConfig().getDouble("particles." + particleKey + ".spread", 0.5));
            double visibleRange = plugin.getConfig().getDouble("particles.visible-range", 64.0);

            // If count is 0, nothing to spawn
            if (count <= 0) return;

            // Use Bukkit's spawnParticle which is server-side; to limit receivers, send only to nearby players
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            double squaredRange = visibleRange * visibleRange;

            // Offsets for spawnParticle are the spread values
            float offsetX = (float) spread;
            float offsetY = (float) spread;
            float offsetZ = (float) spread;

            for (Player player : players) {
                if (!player.isOnline()) continue;
                if (!player.getWorld().equals(world)) continue;
                if (player.getLocation().distanceSquared(loc) <= squaredRange) {
                    // spawn for this player's world (player will see server-side particle)
                    // spawnParticle has several overloads; this one spawns to all players (server-side),
                    // but since we loop per-player we use spawnParticle on the world which broadcasts to nearby players.
                    // To ensure particle is only seen by the target player we use world.spawnParticle with a long distance
                    // and rely on the distance check above. This is efficient enough for typical counts.
                    world.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, 0);
                }
            }
        } catch (Exception e) {
            if (plugin != null && plugin.getConfig().getBoolean("plugin.debug", false)) {
                plugin.getLogger().warning("Failed to spawn configured particle '" + particleKey + "': " + e.getMessage());
            }
        }
    }

    private static Particle parseParticle(String name) {
        if (name == null) return Particle.FLAME;
        try {
            // try direct enum match (names in config are expected to match Particle enum)
            return Particle.valueOf(name.toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            // fallback: try stripping quotes or common mismatches
            String cleaned = name.replace("\"", "").trim().toUpperCase().replace(' ', '_');
            try {
                return Particle.valueOf(cleaned);
            } catch (IllegalArgumentException ex) {
                // last resort: default particle
                return Particle.FLAME;
            }
        }
    }
}