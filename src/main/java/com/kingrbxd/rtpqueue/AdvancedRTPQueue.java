package com.kingrbxd.rtpqueue;

import com.kingrbxd.rtpqueue.commands.RTPQueueCommand;
import com.kingrbxd.rtpqueue.handlers.*;
import com.kingrbxd.rtpqueue.listeners.PlayerMoveListener;
import com.kingrbxd.rtpqueue.listeners.PlayerQuitListener;
import com.kingrbxd.rtpqueue.placeholders.PlaceholderManager;
import com.kingrbxd.rtpqueue.tasks.QueueClearTask;
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
        if (getCommand("rtpqueue") != null) {
            RTPQueueCommand cmd = new RTPQueueCommand(this);
            getCommand("rtpqueue").setExecutor(cmd);
            getCommand("rtpqueue").setTabCompleter(cmd);
        } else {
            getLogger().warning("Command 'rtpqueue' not defined in plugin.yml");
        }

        // Register listeners (use dedicated move/quit listeners to avoid duplication)
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);

        // Setup hooks (PlaceholderAPI, claim protection, etc.)
        setupHooks();

        // Start queue clear task using a dedicated QueueClearTask
        startClearTask();

        getLogger().info("AdvancedRTPQueue has been enabled!");
    }

    /**
     * Setup hooks with other plugins
     */
    private void setupHooks() {
        // PlaceholderAPI manager — PlaceholderManager handles checking if PAPI exists internally
        placeholderManager = new PlaceholderManager(this);

        // Setup claim protection hooks (GriefPrevention, Factions, Towny)
        claimProtectionHandler.setupProtection();
    }

    @Override
    public void onDisable() {
        // Cancel scheduled clear task
        if (clearTask != null) {
            clearTask.cancel();
            clearTask = null;
        }

        // Unregister PlaceholderAPI expansion if registered
        if (placeholderManager != null) {
            placeholderManager.unregister();
            placeholderManager = null;
        }

        // Ensure TeleportHandler cancels background tasks and clears static caches
        TeleportHandler.shutdown();

        // Clear queues
        if (queueHandler != null) {
            queueHandler.clearAllQueues();
        }

        getLogger().info("AdvancedRTPQueue has been disabled!");
        instance = null;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();

        // Reload settings
        if (worldManager != null) {
            worldManager.loadWorldSettings();
        }

        // Restart clear task with new interval
        if (clearTask != null) {
            clearTask.cancel();
            clearTask = null;
        }
        startClearTask();

        // Re-setup protection hooks (in case config changed)
        if (claimProtectionHandler != null) {
            claimProtectionHandler.setupProtection();
        }

        // Re-setup placeholder integration in case PAPI state changed or config updated
        if (placeholderManager != null) {
            placeholderManager.setupPlaceholderAPI();
        }

        // Clear cached messages
        reloadMessages();
    }

    /**
     * Start the queue clear task by scheduling a QueueClearTask runnable.
     */
    private void startClearTask() {
        int clearInterval = getConfig().getInt("queue.clear-interval", 300);

        if (clearInterval > 0) {
            QueueClearTask taskRunnable = new QueueClearTask(this);
            clearTask = getServer().getScheduler().runTaskTimer(this, taskRunnable, clearInterval * 20L, clearInterval * 20L);

            if (getConfig().getBoolean("debug", false)) {
                getLogger().info("QueueClearTask scheduled every " + clearInterval + " seconds");
            }
        } else {
            if (getConfig().getBoolean("debug", false)) {
                getLogger().info("Queue clear disabled (queue.clear-interval <= 0)");
            }
        }
    }

    /**
     * Reload message cache.
     */
    private void reloadMessages() {
        getLogger().info("Loading messages from config...");
        com.kingrbxd.rtpqueue.utils.MessageUtil.clearCachedMessages();
    }

    /**
     * Get plugin instance.
     *
     * @return instance
     */
    public static AdvancedRTPQueue getInstance() {
        return instance;
    }

    public QueueHandler getQueueHandler() {
        return queueHandler;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public ClaimProtectionHandler getClaimProtectionHandler() {
        return claimProtectionHandler;
    }
}