package com.mafia;

import com.mafia.command.MafiaCommand;
import com.mafia.listener.MafiaListener;
import com.mafia.manager.GameManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MafiaPlugin extends JavaPlugin {

    private static MafiaPlugin instance;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;
        gameManager = new GameManager(this);
        getCommand("mafia").setExecutor(new MafiaCommand(this));
        getServer().getPluginManager().registerEvents(new MafiaListener(this), this);
        getLogger().info("MafiaPlugin enabled!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.forceEnd();
        getLogger().info("MafiaPlugin disabled!");
    }

    public static MafiaPlugin getInstance() { return instance; }
    public GameManager getGameManager() { return gameManager; }
}
