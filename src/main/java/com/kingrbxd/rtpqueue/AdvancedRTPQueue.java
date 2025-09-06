package com.kingrbxd.rtpqueue;

import com.kingrbxd.rtpqueue.commands.RTPQueueCommand;
import com.kingrbxd.rtpqueue.handlers.*;
import com.kingrbxd.rtpqueue.listeners.PlayerListener;
import com.kingrbxd.rtpqueue.placeholders.PlaceholderManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Main plugin class for AdvancedRTPQueue.
 */
public class AdvancedRTPQueue extends JavaPlugin {
    private static AdvancedRTPQueue instance;
    private QueueHandler queueHandler;
    private CooldownManager cooldownManager;
    private WorldManager worldManager;
    private ClaimProtectionHandler claimProtectionHandler;
    private PlaceholderManager placeholderManager;
    private BukkitTask clearTask;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize handlers
        queueHandler = new QueueHandler(this);
        cooldownManager = new CooldownManager(this);
        worldManager = new WorldManager(this);
        claimProtectionHandler = new ClaimProtectionHandler(this);

        // Register commands
        getCommand("rtpqueue").setExecutor(new RTPQueueCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Start queue clear task
        startClearTask();

        // Check for other hooks
        setupHooks();

        getLogger().info("AdvancedRTPQueue has been enabled!");
    }

    /**
     * Setup hooks with other plugins
     */
    private void setupHooks() {
        // Set up PlaceholderAPI
        placeholderManager = new PlaceholderManager(this);

        // Check for claim protection plugins
        if (getConfig().getBoolean("claim-protection.enabled", true)) {
            boolean anyHookFound = false;

            if (getConfig().getBoolean("claim-protection.plugins.grief-prevention", true)) {
                if (Bukkit.getPluginManager().getPlugin("GriefPrevention") != null) {
                    getLogger().info("GriefPrevention found - claim protection enabled.");
                    anyHookFound = true;
                } else {
                    getLogger().info("GriefPrevention not found - claim protection for this plugin disabled.");
                }
            }

            if (getConfig().getBoolean("claim-protection.plugins.factions", true)) {
                if (Bukkit.getPluginManager().getPlugin("Factions") != null) {
                    getLogger().info("Factions found - claim protection enabled.");
                    anyHookFound = true;
                } else {
                    getLogger().info("Factions not found - claim protection for this plugin disabled.");
                }
            }

            if (getConfig().getBoolean("claim-protection.plugins.towny", true)) {
                if (Bukkit.getPluginManager().getPlugin("Towny") != null) {
                    getLogger().info("Towny found - claim protection enabled.");
                    anyHookFound = true;
                } else {
                    getLogger().info("Towny not found - claim protection for this plugin disabled.");
                }
            }

            if (!anyHookFound) {
                getLogger().info("No claim protection plugins found. Players may be teleported to claimed areas.");
            }
        }
    }

    @Override
    public void onDisable() {
        // Cancel tasks
        if (clearTask != null) {
            clearTask.cancel();
        }

        // Clear queues
        queueHandler.clearAllQueues();

        getLogger().info("AdvancedRTPQueue has been disabled!");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();

        // Reload settings
        worldManager.loadWorldSettings();

        // Restart clear task with new interval
        if (clearTask != null) {
            clearTask.cancel();
        }
        startClearTask();
    }

    /**
     * Start the queue clear task.
     */
    private void startClearTask() {
        int clearInterval = getConfig().getInt("queue.clear-interval", 300);

        if (clearInterval > 0) {
            clearTask = getServer().getScheduler().runTaskTimer(this, () -> {
                // Clear all queues
                queueHandler.clearAllQueues();

                // Broadcast message if enabled
                if (getConfig().getBoolean("broadcast-queue-clear", false)) {
                    getServer().broadcastMessage(getConfig().getString("messages.queue-cleared"));
                }
            }, clearInterval * 20L, clearInterval * 20L);
        }
    }

    /**
     * Get the plugin instance.
     *
     * @return The plugin instance
     */
    public static AdvancedRTPQueue getInstance() {
        return instance;
    }

    /**
     * Get the queue handler.
     *
     * @return The queue handler
     */
    public QueueHandler getQueueHandler() {
        return queueHandler;
    }

    /**
     * Get the cooldown manager.
     *
     * @return The cooldown manager
     */
    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    /**
     * Get the world manager.
     *
     * @return The world manager
     */
    public WorldManager getWorldManager() {
        return worldManager;
    }

    /**
     * Get the claim protection handler.
     *
     * @return The claim protection handler
     */
    public ClaimProtectionHandler getClaimProtectionHandler() {
        return claimProtectionHandler;
    }

    /**
     * Get the placeholder manager.
     *
     * @return The placeholder manager
     */
    public PlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }

    /**
     * Check if PlaceholderAPI integration is enabled.
     *
     * @return True if PlaceholderAPI is hooked
     */
    public boolean isPlaceholderAPIEnabled() {
        return placeholderManager != null && placeholderManager.isPlaceholderAPIEnabled();
    }
}