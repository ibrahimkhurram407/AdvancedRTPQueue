package com.kingrbxd.rtpqueue.protection;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;

/**
 * Complete GriefPrevention claim checker
 */
public class GriefPreventionClaimChecker implements ClaimChecker {

    @Override
    public boolean isLocationClaimed(Location location) {
        if (location == null) {
            return false;
        }

        try {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
            return claim != null;
        } catch (Exception e) {
            return false;
        }
    }
}