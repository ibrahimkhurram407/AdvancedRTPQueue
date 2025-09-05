package com.kingrbxd.rtpqueue.protection;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.Faction;
import org.bukkit.Location;

/**
 * Implementation of ClaimChecker for SaberFactions and other MassiveCraft-based Factions plugins.
 * This should work with most modern Factions plugins that use the MassiveCraft API structure.
 */
public class FactionsClaimChecker implements ClaimChecker {

    @Override
    public boolean isLocationClaimed(Location location) {
        try {
            // Direct API call to Factions
            Board board = Board.getInstance();
            FLocation fLocation = new FLocation(location);
            Faction faction = board.getFactionAt(fLocation);

            // Consider claimed if not wilderness
            return faction != null && !faction.isWilderness();
        } catch (Exception e) {
            // If any error occurs, assume unclaimed to avoid blocking teleport
            return false;
        }
    }
}