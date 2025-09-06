package com.kingrbxd.rtpqueue.protection;

import org.bukkit.Location;

/**
 * Interface for checking if a location is within claimed land
 */
public interface ClaimChecker {

    /**
     * Check if a location is within claimed land
     *
     * @param location The location to check
     * @return true if the location is claimed, false otherwise
     */
    boolean isLocationClaimed(Location location);
}