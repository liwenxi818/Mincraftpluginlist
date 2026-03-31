package com.mafia.manager;

import com.mafia.MafiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Level;

public class WorldManager {

    private final MafiaPlugin plugin;
    private static final String WORLD_NAME = "mafia_world";

    public WorldManager(MafiaPlugin plugin) {
        this.plugin = plugin;
    }

    public World createGameWorld() {
        // Delete old world if exists
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) {
            deleteWorld(existing);
        }

        WorldCreator creator = new WorldCreator(WORLD_NAME);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:dirt\",\"height\":3},{\"block\":\"minecraft:grass_block\",\"height\":1}],\"biome\":\"minecraft:plains\"}");

        World world = creator.createWorld();
        if (world != null) {
            world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setTime(6000); // noon
        }
        return world;
    }

    public void deleteWorld(World world) {
        String name = world.getName();
        File folder = world.getWorldFolder();
        Bukkit.unloadWorld(world, false);
        deleteDirectory(folder);
        plugin.getLogger().info("Deleted world: " + name);
    }

    private void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        try {
            Files.walk(dir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete world folder: " + dir.getPath(), e);
        }
    }
}
