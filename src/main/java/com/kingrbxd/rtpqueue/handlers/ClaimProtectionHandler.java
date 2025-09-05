package com.kingrbxd.rtpqueue.handlers;

import com.kingrbxd.rtpqueue.AdvancedRTPQueue;
import com.kingrbxd.rtpqueue.protection.ClaimChecker;
import com.kingrbxd.rtpqueue.protection.FactionsClaimChecker;
import com.kingrbxd.rtpqueue.protection.GriefPreventionClaimChecker;
import com.kingrbxd.rtpqueue.protection.TownyClaimChecker;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles protection from teleporting players into claimed areas.
 * Supports GriefPrevention, Factions, and Towny.
 */
public class ClaimProtectionHandler {
    private final AdvancedRTPQueue plugin;
    private final List<ClaimChecker> claimCheckers = new ArrayList<>();

    public ClaimProtectionHandler(AdvancedRTPQueue plugin) {
        this.plugin = plugin;
    }

    /**
     * Setup hooks for claim protection plugins.
     */
    public void setupProtection() {
        // Clear existing checkers
        claimCheckers.clear();

        if (!plugin.getConfig().getBoolean("claim-protection.enabled", true)) {
            plugin.getLogger().info("Claim protection is disabled in config.");
            return;
        }

        // GriefPrevention support
        if (plugin.getConfig().getBoolean("claim-protection.plugins.grief-prevention", true)) {
            Plugin griefPlugin = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
            if (griefPlugin != null && griefPlugin.isEnabled()) {
                try {
                    ClaimChecker checker = new GriefPreventionClaimChecker();
                    claimCheckers.add(checker);
                    plugin.getLogger().info("Successfully hooked into GriefPrevention v" +
                            griefPlugin.getDescription().getVersion());
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to hook into GriefPrevention: " + t.getMessage());
                    plugin.getLogger().warning("This is likely due to an API change in GriefPrevention.");
                }
            } else if (plugin.getConfig().getBoolean("claim-protection.plugins.grief-prevention")) {
                plugin.getLogger().warning("GriefPrevention not found but enabled in config. Claim protection for GriefPrevention will be skipped.");
            }
        }

        // Factions support (primarily SaberFactions, but works with other MassiveCraft-based Factions)
        if (plugin.getConfig().getBoolean("claim-protection.plugins.factions", true)) {
            Plugin factionsPlugin = plugin.getServer().getPluginManager().getPlugin("Factions");
            if (factionsPlugin != null && factionsPlugin.isEnabled()) {
                try {
                    ClaimChecker checker = new FactionsClaimChecker();
                    claimCheckers.add(checker);
                    plugin.getLogger().info("Successfully hooked into Factions v" +
                            factionsPlugin.getDescription().getVersion() + " (" + factionsPlugin.getDescription().getName() + ")");
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to hook into Factions: " + t.getMessage());
                    plugin.getLogger().warning("This is likely due to an API change in the Factions plugin.");
                }
            } else if (plugin.getConfig().getBoolean("claim-protection.plugins.factions")) {
                plugin.getLogger().warning("Factions not found but enabled in config. Claim protection for Factions will be skipped.");
            }
        }

        // Towny support
        if (plugin.getConfig().getBoolean("claim-protection.plugins.towny", true)) {
            Plugin townyPlugin = plugin.getServer().getPluginManager().getPlugin("Towny");
            if (townyPlugin != null && townyPlugin.isEnabled()) {
                try {
                    ClaimChecker checker = new TownyClaimChecker();
                    claimCheckers.add(checker);
                    plugin.getLogger().info("Successfully hooked into Towny v" +
                            townyPlugin.getDescription().getVersion());
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to hook into Towny: " + t.getMessage());
                    plugin.getLogger().warning("This is likely due to an API change in Towny.");
                }
            } else if (plugin.getConfig().getBoolean("claim-protection.plugins.towny")) {
                plugin.getLogger().warning("Towny not found but enabled in config. Claim protection for Towny will be skipped.");
            }
        }

        // Summary of enabled protection plugins
        if (!claimCheckers.isEmpty()) {
            plugin.getLogger().info("Claim protection enabled for " + claimCheckers.size() + " plugin(s)");
        } else {
            if (plugin.getConfig().getBoolean("claim-protection.enabled", true)) {
                plugin.getLogger().warning("No claim protection plugins were found, but claim protection is enabled in config.");
                plugin.getLogger().warning("Players may be teleported into claimed areas.");
            }
        }
    }

    /**
     * Check if a location is in a claimed area.
     *
     * @param location The location to check
     * @return true if location is in a claimed area
     */
    public boolean isLocationClaimed(Location location) {
        if (!plugin.getConfig().getBoolean("claim-protection.enabled", true) || claimCheckers.isEmpty()) {
            return false;
        }

        // Check with all registered claim checkers
        for (ClaimChecker checker : claimCheckers) {
            try {
                if (checker.isLocationClaimed(location)) {
                    return true;
                }
            } catch (Exception e) {
                // Log and continue with other checkers if one fails
                plugin.getLogger().warning("Error checking claims with " + checker.getClass().getSimpleName() +
                        ": " + e.getMessage());
            }
        }

        return false;
    }
}