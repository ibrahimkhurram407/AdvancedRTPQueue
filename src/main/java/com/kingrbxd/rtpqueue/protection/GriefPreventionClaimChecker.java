package com.kingrbxd.rtpqueue.protection;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;

/**
 * Implementation of ClaimChecker for GriefPrevention
 */
public class GriefPreventionClaimChecker implements ClaimChecker {

    @Override
    public boolean isLocationClaimed(Location location) {
        try {
            // Direct API call to GriefPrevention
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
            return claim != null;
        } catch (Exception e) {
            // If any error occurs, assume unclaimed to avoid blocking teleport
            return false;
        }
    }
}