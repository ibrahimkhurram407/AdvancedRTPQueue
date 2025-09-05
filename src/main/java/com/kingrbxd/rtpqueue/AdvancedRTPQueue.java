package com.kingrbxd.rtpqueue;

import org.bstats.bukkit.Metrics;
import com.kingrbxd.rtpqueue.commands.RTPQueueCommand;
import com.kingrbxd.rtpqueue.handlers.*;
import com.kingrbxd.rtpqueue.listeners.PlayerMoveListener;
import com.kingrbxd.rtpqueue.listeners.PlayerQuitListener;
import com.kingrbxd.rtpqueue.utils.ConfigUtil;
import com.kingrbxd.rtpqueue.utils.MessageUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class AdvancedRTPQueue extends JavaPlugin {
    private static AdvancedRTPQueue instance;
    private QueueHandler queueHandler;
    private CooldownManager cooldownManager;
    private WorldManager worldManager;
    private ClaimProtectionHandler claimProtectionHandler;
    private BukkitTask queueClearTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Initialize utils first
        MessageUtil.initialize(this);
        loadConfiguration();

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);

        // Register main command
        registerCommand("rtpqueue", new RTPQueueCommand(this));

        // Start bStats
        new Metrics(this, 25138);

        getLogger().info("AdvancedRTPQueue v2.0 by KingRBxD enabled!");
    }

    /**
     * Load or reload all configuration and initialize handlers
     */
    private void loadConfiguration() {
        // Load messages
        ConfigUtil.loadMessages();

        // Initialize or reinitialize handlers
        if (queueHandler == null) {
            queueHandler = new QueueHandler();
        }

        if (cooldownManager == null) {
            cooldownManager = new CooldownManager(this);
        }

        if (worldManager == null) {
            worldManager = new WorldManager(this);
        } else {
            worldManager.reload();
        }

        if (claimProtectionHandler == null) {
            claimProtectionHandler = new ClaimProtectionHandler(this);
        }

        // Check for claim protection plugins
        claimProtectionHandler.setupProtection();

        // Set up queue clearing task
        setupQueueClearTask();
    }

    /**
     * Set up the queue clear task (cancels existing task if running)
     */
    private void setupQueueClearTask() {
        // Cancel existing task if it exists
        if (queueClearTask != null) {
            queueClearTask.cancel();
        }

        // Start queue clearing task with updated interval
        int clearInterval = getConfig().getInt("queue.clear-interval", 300);
        queueClearTask = new QueueClearTask(this).runTaskTimer(this, clearInterval * 20L, clearInterval * 20L);
        getLogger().info("Queue clear task scheduled: every " + clearInterval + " seconds");
    }

    @Override
    public void onDisable() {
        if (queueHandler != null) {
            queueHandler.clearQueue();
        }

        if (queueClearTask != null) {
            queueClearTask.cancel();
        }

        getLogger().info("AdvancedRTPQueue disabled!");
    }

    /**
     * Reloads the plugin configuration and all handlers.
     */
    public void reload() {
        // Reload config file
        reloadConfig();

        // Reload all configuration and handlers
        loadConfiguration();

        getLogger().info("AdvancedRTPQueue configuration reloaded.");
        getLogger().info("Note: For some settings like cooldowns and message formats, a server restart is recommended.");
    }

    /**
     * Registers a command with an executor and tab completer.
     *
     * @param name     The command name.
     * @param executor The command executor (must also implement TabCompleter if needed).
     */
    private void registerCommand(String name, Object executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor((org.bukkit.command.CommandExecutor) executor);
            if (executor instanceof org.bukkit.command.TabCompleter) {
                command.setTabCompleter((org.bukkit.command.TabCompleter) executor);
            }
        } else {
            getLogger().warning("Failed to register command: " + name);
        }
    }

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