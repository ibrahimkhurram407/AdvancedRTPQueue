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

public final class AdvancedRTPQueue extends JavaPlugin {
    private static AdvancedRTPQueue instance;
    private QueueHandler queueHandler;
    private CooldownManager cooldownManager;
    private WorldManager worldManager;
    private ClaimProtectionHandler claimProtectionHandler;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Initialize utils first
        MessageUtil.initialize(this);
        ConfigUtil.loadMessages();

        // Initialize handlers
        queueHandler = new QueueHandler();
        cooldownManager = new CooldownManager(this);
        worldManager = new WorldManager(this);
        claimProtectionHandler = new ClaimProtectionHandler(this);

        // Check for claim protection plugins
        claimProtectionHandler.setupProtection();

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);

        // Register main command
        registerCommand("rtpqueue", new RTPQueueCommand(this));

        // Start queue clearing task
        int clearInterval = getConfig().getInt("queue.clear-interval", 300);
        new QueueClearTask(this).runTaskTimer(this, clearInterval * 20L, clearInterval * 20L);

        // Start bStats
        new Metrics(this, 25138);

        getLogger().info("AdvancedRTPQueue v2.0 by KingRBxD enabled!");
    }

    @Override
    public void onDisable() {
        queueHandler.clearQueue();
        getLogger().info("AdvancedRTPQueue disabled!");
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