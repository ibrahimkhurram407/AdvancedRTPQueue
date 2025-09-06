package com.kingrbxd.rtpqueue.protection;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.TownBlock;
import org.bukkit.Location;

/**
 * Complete Towny claim checker
 */
public class TownyClaimChecker implements ClaimChecker {

    @Override
    public boolean isLocationClaimed(Location location) {
        if (location == null) {
            return false;
        }

        try {
            TownyAPI townyAPI = TownyAPI.getInstance();
            TownBlock townBlock = townyAPI.getTownBlock(location);

            return townBlock != null;
        } catch (Exception e) {
            return false;
        }
    }
}