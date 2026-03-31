package com.mafia.manager;

import com.mafia.MafiaPlugin;
import com.mafia.game.MafiaGame;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;

import java.util.List;

public class HouseManager {

    private static final int HOUSE_SPACING = 12; // distance between house origins
    private static final int GRID_COLS = 4;
    // House external footprint: 7 wide (x), 7 deep (z), 5 tall (y)
    // Origin is at the NW corner (min x, y=64, min z)

    public HouseManager(MafiaPlugin plugin) {
    }

    public void buildAllHouses(MafiaGame game) {
        List<Player> players = game.getAlivePlayers();
        World world = game.getGameWorld();
        int baseY = world.getHighestBlockYAt(0, 0);

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int originX = col * HOUSE_SPACING;
            int originZ = row * HOUSE_SPACING;

            buildHouse(world, originX, baseY, originZ, player.getName());

            // spawn point: inside house, center
            Location spawn = new Location(world, originX + 3.5, baseY + 1, originZ + 4.5);
            spawn.setYaw(180); // face door (south)
            game.getHouseSpawns().put(player.getUniqueId(), spawn);

            // entrance: just outside the door
            Location entrance = new Location(world, originX + 3.5, baseY, originZ + 6.5);
            game.getHouseEntrances().put(player.getUniqueId(), entrance);
        }
    }

    private void buildHouse(World world, int ox, int oy, int oz, String playerName) {
        // Clear area first
        for (int x = ox; x < ox + 7; x++)
            for (int y = oy; y < oy + 6; y++)
                for (int z = oz; z < oz + 7; z++)
                    world.getBlockAt(x, y, z).setType(Material.AIR);

        // Floor (y = oy)
        for (int x = ox; x < ox + 7; x++)
            for (int z = oz; z < oz + 7; z++)
                world.getBlockAt(x, oy, z).setType(Material.OAK_PLANKS);

        // Walls (y = oy+1 to oy+4), hollow
        for (int y = oy + 1; y <= oy + 4; y++) {
            for (int x = ox; x < ox + 7; x++) {
                world.getBlockAt(x, y, oz).setType(Material.OAK_PLANKS);       // north wall
                world.getBlockAt(x, y, oz + 6).setType(Material.OAK_PLANKS);   // south wall
            }
            for (int z = oz; z < oz + 7; z++) {
                world.getBlockAt(ox, y, z).setType(Material.OAK_PLANKS);        // west wall
                world.getBlockAt(ox + 6, y, z).setType(Material.OAK_PLANKS);   // east wall
            }
        }

        // Roof (y = oy+5)
        for (int x = ox; x < ox + 7; x++)
            for (int z = oz; z < oz + 7; z++)
                world.getBlockAt(x, oy + 5, z).setType(Material.OAK_PLANKS);

        // Windows on north wall (y+2, x=2 and x=4)
        org.bukkit.block.data.type.GlassPane paneData1 = (org.bukkit.block.data.type.GlassPane) Material.GLASS_PANE.createBlockData();
        paneData1.setFace(BlockFace.EAST, true);
        paneData1.setFace(BlockFace.WEST, true);
        world.getBlockAt(ox + 2, oy + 2, oz).setBlockData(paneData1, false);
        org.bukkit.block.data.type.GlassPane paneData2 = (org.bukkit.block.data.type.GlassPane) Material.GLASS_PANE.createBlockData();
        paneData2.setFace(BlockFace.EAST, true);
        paneData2.setFace(BlockFace.WEST, true);
        world.getBlockAt(ox + 4, oy + 2, oz).setBlockData(paneData2, false);

        // Door on south wall center (x=3, z=oz+6), y+1 and y+2
        // Place oak door: create BlockData first to avoid CraftBlockData cast failure
        Block doorBottom = world.getBlockAt(ox + 3, oy + 1, oz + 6);
        Door doorData = (Door) Material.OAK_DOOR.createBlockData();
        doorData.setFacing(BlockFace.SOUTH);
        doorData.setHalf(Door.Half.BOTTOM);
        doorBottom.setBlockData(doorData);
        Block doorTop = world.getBlockAt(ox + 3, oy + 2, oz + 6);
        Door doorTopData = (Door) Material.OAK_DOOR.createBlockData();
        doorTopData.setFacing(BlockFace.SOUTH);
        doorTopData.setHalf(Door.Half.TOP);
        doorTop.setBlockData(doorTopData);

        // Bed inside: foot at (ox+3, oy+1, oz+2), head at (ox+3, oy+1, oz+1), facing south
        Block bedFoot = world.getBlockAt(ox + 3, oy + 1, oz + 2);
        Block bedHead = world.getBlockAt(ox + 3, oy + 1, oz + 1);
        org.bukkit.block.data.type.Bed bedFootData = (org.bukkit.block.data.type.Bed) Material.RED_BED.createBlockData();
        bedFootData.setFacing(BlockFace.NORTH);
        bedFootData.setPart(org.bukkit.block.data.type.Bed.Part.FOOT);
        bedFoot.setBlockData(bedFootData, false);
        org.bukkit.block.data.type.Bed bedHeadData = (org.bukkit.block.data.type.Bed) Material.RED_BED.createBlockData();
        bedHeadData.setFacing(BlockFace.NORTH);
        bedHeadData.setPart(org.bukkit.block.data.type.Bed.Part.HEAD);
        bedHead.setBlockData(bedHeadData, false);

        // Sign above door outside with player name
        Block signBlock = world.getBlockAt(ox + 3, oy + 3, oz + 6);
        org.bukkit.block.data.type.WallSign signData = (org.bukkit.block.data.type.WallSign) Material.OAK_WALL_SIGN.createBlockData();
        signData.setFacing(BlockFace.SOUTH);
        signBlock.setBlockData(signData);
        org.bukkit.block.Sign signState = (org.bukkit.block.Sign) signBlock.getState();
        signState.line(1, net.kyori.adventure.text.Component.text(playerName));
        signState.update();
    }
}
