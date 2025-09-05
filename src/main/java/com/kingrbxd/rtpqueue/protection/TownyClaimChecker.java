package com.kingrbxd.rtpqueue.protection;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.TownBlock;
import org.bukkit.Location;

/**
 * Implementation of ClaimChecker for Towny
 */
public class TownyClaimChecker implements ClaimChecker {

    @Override
    public boolean isLocationClaimed(Location location) {
        try {
            // Direct API call to Towny
            TownyAPI townyAPI = TownyAPI.getInstance();
            TownBlock townBlock = townyAPI.getTownBlock(location);

            // If townBlock exists, the land is claimed
            return townBlock != null;
        } catch (Exception e) {
            // If any error occurs, assume unclaimed to avoid blocking teleport
            return false;
        }
    }
}