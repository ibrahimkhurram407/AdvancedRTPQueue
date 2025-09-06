package com.kingrbxd.rtpqueue.protection;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.Faction;
import org.bukkit.Location;

/**
 * Complete Factions claim checker
 */
public class FactionsClaimChecker implements ClaimChecker {

    @Override
    public boolean isLocationClaimed(Location location) {
        if (location == null) {
            return false;
        }

        try {
            Board board = Board.getInstance();
            FLocation fLocation = new FLocation(location);
            Faction faction = board.getFactionAt(fLocation);

            return faction != null && !faction.isWilderness();
        } catch (Exception e) {
            return false;
        }
    }
}