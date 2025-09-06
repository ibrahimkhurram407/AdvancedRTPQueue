package com.kingrbxd.rtpqueue.utils;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import org.bukkit.Location;

/**
 * Simple facade for teleport-related effects so TeleportManager can call TeleportEffects.playStart/Success(...)
 *
 * Usage in TeleportManager:
 *   TeleportEffects.playStart(plugin, location);
 *   TeleportEffects.playSuccess(plugin, location);
 */
public final class TeleportEffects {
    private TeleportEffects() {}

    public static void playStart(AdvancedRTPQueue plugin, Location loc) {
        ParticleUtil.spawnConfiguredParticle(plugin, loc, "teleport-start");
    }

    public static void playSuccess(AdvancedRTPQueue plugin, Location loc) {
        ParticleUtil.spawnConfiguredParticle(plugin, loc, "teleport-success");
    }
}