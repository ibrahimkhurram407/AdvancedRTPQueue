package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.protection.*;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete claim protection handler
 */
public class ClaimProtectionHandler {
    private final AdvancedRTPQueue plugin;
    private final List<ClaimChecker> claimCheckers = new ArrayList<>();
    private boolean enabled;

    public ClaimProtectionHandler(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfigManager().getBoolean("claim-protection.enabled", true);
    }

    /**
     * Setup protection integrations
     */
    public void setupProtection() {
        claimCheckers.clear();

        if (!enabled) {
            plugin.getLogger().info("Claim protection is disabled");
            return;
        }

        // GriefPrevention
        if (plugin.getConfigManager().getBoolean("claim-protection.plugins.grief-prevention", true)) {
            if (plugin.getServer().getPluginManager().getPlugin("GriefPrevention") != null) {
                try {
                    claimCheckers.add(new GriefPreventionClaimChecker());
                    plugin.getLogger().info("Hooked into GriefPrevention for claim protection");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to hook into GriefPrevention: " + e.getMessage());
                }
            }
        }

        // Factions
        if (plugin.getConfigManager().getBoolean("claim-protection.plugins.factions", true)) {
            if (plugin.getServer().getPluginManager().getPlugin("Factions") != null) {
                try {
                    claimCheckers.add(new FactionsClaimChecker());
                    plugin.getLogger().info("Hooked into Factions for claim protection");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to hook into Factions: " + e.getMessage());
                }
            }
        }

        // Towny
        if (plugin.getConfigManager().getBoolean("claim-protection.plugins.towny", true)) {
            if (plugin.getServer().getPluginManager().getPlugin("Towny") != null) {
                try {
                    claimCheckers.add(new TownyClaimChecker());
                    plugin.getLogger().info("Hooked into Towny for claim protection");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to hook into Towny: " + e.getMessage());
                }
            }
        }

        if (claimCheckers.isEmpty()) {
            plugin.getLogger().info("No claim protection plugins found or enabled");
        } else {
            plugin.getLogger().info("Loaded " + claimCheckers.size() + " claim protection integrations");
        }
    }

    /**
     * Check if location is claimed
     */
    public boolean isLocationClaimed(Location location) {
        if (!enabled || location == null) {
            return false;
        }

        for (ClaimChecker checker : claimCheckers) {
            try {
                if (checker.isLocationClaimed(location)) {
                    return true;
                }
            } catch (Exception e) {
                if (plugin.getConfigManager().getBoolean("plugin.debug")) {
                    plugin.getLogger().warning("Error checking claims with " + checker.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }

        return false;
    }

    /**
     * Check if protection is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set protection enabled state
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}