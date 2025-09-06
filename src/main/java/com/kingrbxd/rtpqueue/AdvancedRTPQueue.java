package com.kingrbxd.rtpqueue;

import com.kingrbxd.rtpqueue.commands.RTPQueueCommand;
import com.kingrbxd.rtpqueue.handlers.*;
import com.kingrbxd.rtpqueue.listeners.*;
import com.kingrbxd.rtpqueue.placeholders.PlaceholderManager;
import com.kingrbxd.rtpqueue.tasks.QueueClearTask;
import com.kingrbxd.rtpqueue.utils.ConfigManager;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Main plugin class for AdvancedRTPQueue v3.0
 * Complete implementation with all features
 */
public class AdvancedRTPQueue extends JavaPlugin {
    private static AdvancedRTPQueue instance;

    // Core handlers
    private QueueHandler queueHandler;
    private CooldownManager cooldownManager;
    private WorldManager worldManager;
    private ClaimProtectionHandler claimProtectionHandler;
    private TeleportManager teleportManager;

    // Integration managers
    private PlaceholderManager placeholderManager;
    private ConfigManager configManager;

    // Tasks
    private BukkitTask clearTask;
    private BukkitTask actionBarTask;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Starting AdvancedRTPQueue v" + getDescription().getVersion() + "...");

        // Initialize configuration
        if (!initializeConfig()) {
            disablePlugin("Failed to initialize configuration");
            return;
        }

        // Initialize core systems
        if (!initializeCore()) {
            disablePlugin("Failed to initialize core systems");
            return;
        }

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        // Setup integrations
        setupIntegrations();

        // Setup metrics
        setupMetrics();

        // Start tasks
        startTasks();

        getLogger().info("AdvancedRTPQueue v" + getDescription().getVersion() + " enabled successfully!");
        getLogger().info("Features: Enhanced teleportation, claim protection, PlaceholderAPI, extensive customization");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling AdvancedRTPQueue...");

        // Cancel all tasks
        cancelTasks();

        // Shutdown handlers
        shutdownHandlers();

        // Cleanup integrations
        cleanupIntegrations();

        getLogger().info("AdvancedRTPQueue disabled successfully!");
        instance = null;
    }

    private void disablePlugin(String reason) {
        getLogger().severe(reason + "! Disabling plugin...");
        getServer().getPluginManager().disablePlugin(this);
    }

    /**
     * Initialize configuration system
     */
    private boolean initializeConfig() {
        try {
            saveDefaultConfig();
            configManager = new ConfigManager(this);
            MessageUtil.initialize(this);
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to initialize configuration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initialize core plugin systems
     */
    private boolean initializeCore() {
        try {
            // Initialize managers in dependency order
            worldManager = new WorldManager(this);
            queueHandler = new QueueHandler(this);
            cooldownManager = new CooldownManager(this);
            claimProtectionHandler = new ClaimProtectionHandler(this);
            teleportManager = new TeleportManager(this);

            getLogger().info("Core systems initialized successfully");
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to initialize core systems: " + e.getMessage());
            if (configManager != null && configManager.getBoolean("plugin.debug")) {
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * Register plugin commands
     */
    private void registerCommands() {
        RTPQueueCommand command = new RTPQueueCommand(this);
        getCommand("rtpqueue").setExecutor(command);
        getCommand("rtpqueue").setTabCompleter(command);

        if (configManager.getBoolean("plugin.debug")) {
            getLogger().info("Commands registered successfully");
        }
    }

    /**
     * Register event listeners
     */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);

        if (configManager.getBoolean("plugin.debug")) {
            getLogger().info("Event listeners registered successfully");
        }
    }

    /**
     * Setup integrations with other plugins
     */
    private void setupIntegrations() {
        // PlaceholderAPI
        placeholderManager = new PlaceholderManager(this);

        // Claim protection
        claimProtectionHandler.setupProtection();

        getLogger().info("Plugin integrations setup completed");
    }

    /**
     * Setup bStats metrics
     */
    private void setupMetrics() {
        try {
            int pluginId = 25138;
            new Metrics(this, pluginId);
            getLogger().info("bStats metrics initialized successfully");
        } catch (Exception e) {
            getLogger().warning("Failed to initialize bStats metrics: " + e.getMessage());
        }
    }

    /**
     * Start scheduled tasks
     */
    private void startTasks() {
        // Queue clear task
        int clearInterval = configManager.getInt("queue.clear-interval");
        if (clearInterval > 0) {
            clearTask = getServer().getScheduler().runTaskTimer(
                    this, new QueueClearTask(this),
                    clearInterval * 20L, clearInterval * 20L
            );

            if (configManager.getBoolean("plugin.debug")) {
                getLogger().info("Queue clear task started (interval: " + clearInterval + "s)");
            }
        }

        // Action bar task
        if (configManager.getBoolean("ui.action-bar.enabled")) {
            int updateInterval = configManager.getInt("ui.action-bar.update-interval", 20);
            actionBarTask = getServer().getScheduler().runTaskTimer(
                    this, this::updateActionBars, 20L, updateInterval
            );

            if (configManager.getBoolean("plugin.debug")) {
                getLogger().info("Action bar task started");
            }
        }
    }

    /**
     * Update action bars for queued players
     */
    private void updateActionBars() {
        if (queueHandler != null) {
            queueHandler.updateActionBars();
        }
    }

    /**
     * Cancel all running tasks
     */
    private void cancelTasks() {
        if (clearTask != null) {
            clearTask.cancel();
            clearTask = null;
        }

        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
    }

    /**
     * Shutdown all handlers
     */
    private void shutdownHandlers() {
        if (teleportManager != null) {
            teleportManager.shutdown();
        }

        if (queueHandler != null) {
            queueHandler.clearAllQueues();
        }

        if (cooldownManager != null) {
            cooldownManager.clearAllCooldowns();
        }
    }

    /**
     * Cleanup integrations
     */
    private void cleanupIntegrations() {
        if (placeholderManager != null) {
            placeholderManager.unregister();
            placeholderManager = null;
        }
    }

    /**
     * Reload plugin configuration and systems
     */
    public boolean reloadPlugin() {
        try {
            getLogger().info("Reloading AdvancedRTPQueue...");

            // Reload config
            reloadConfig();
            configManager.reload();
            MessageUtil.clearCache();

            // Reload world settings
            if (worldManager != null) {
                worldManager.loadWorldSettings();
            }

            // Restart tasks
            cancelTasks();
            startTasks();

            // Re-setup integrations
            if (claimProtectionHandler != null) {
                claimProtectionHandler.setupProtection();
            }

            if (placeholderManager != null) {
                placeholderManager.setupPlaceholderAPI();
            }

            getLogger().info("Plugin reloaded successfully");
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to reload plugin: " + e.getMessage());
            if (configManager.getBoolean("plugin.debug")) {
                e.printStackTrace();
            }
            return false;
        }
    }

    // Getters
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

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }
}