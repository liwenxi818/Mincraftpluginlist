package com.mafia.manager;

import com.mafia.MafiaPlugin;
import com.mafia.game.MafiaGame;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HouseManager {

    // House footprint: 9 wide x 9 deep x 6 tall (floor + 4 walls + roof)
    private static final int HOUSE_W = 9;
    private static final int HOUSE_D = 9;
    private static final int MIN_DIST = 30;  // minimum distance from campfire to house center
    private static final int HOUSE_GAP = 4;  // gap between houses on the same side

    public HouseManager(MafiaPlugin plugin) {}

    public void buildAllHouses(MafiaGame game) {
        List<Player> players = game.getAlivePlayers();
        List<UUID> botUUIDs = new ArrayList<>(game.getAliveBotUUIDs());
        World world = game.getGameWorld();
        int count = players.size() + botUUIDs.size();
        if (count == 0) return;

        // Campfire at world origin
        int cy0 = world.getHighestBlockYAt(0, 0);
        placeCampfire(world, 0, cy0 + 1, 0);
        buildDiscussionArea(world, 0, cy0, 0);

        // Distribute houses across 4 sides: [NORTH, SOUTH, EAST, WEST]
        // NORTH side: houses at z = -dist, door faces SOUTH (toward campfire)
        // SOUTH side: houses at z = +dist, door faces NORTH
        // EAST  side: houses at x = +dist, door faces WEST
        // WEST  side: houses at x = -dist, door faces EAST
        int base = count / 4;
        int rem  = count % 4;
        int[] sideCounts = {
            base + (rem > 0 ? 1 : 0),  // NORTH
            base + (rem > 1 ? 1 : 0),  // SOUTH
            base + (rem > 2 ? 1 : 0),  // EAST
            base                         // WEST
        };
        BlockFace[] sideFacings = { BlockFace.SOUTH, BlockFace.NORTH, BlockFace.WEST, BlockFace.EAST };

        // Distance: ensure houses on adjacent sides don't collide
        int maxSide = 0;
        for (int c : sideCounts) if (c > maxSide) maxSide = c;
        double halfSpread = (maxSide > 1) ? (maxSide - 1) * (HOUSE_W + HOUSE_GAP) / 2.0 : 0;
        int dist = (int) Math.max(MIN_DIST, halfSpread + HOUSE_D + HOUSE_GAP);

        // Flatten all participants in order
        List<UUID> allUUIDs = new ArrayList<>();
        players.forEach(p -> allUUIDs.add(p.getUniqueId()));
        allUUIDs.addAll(botUUIDs);

        int pIdx = 0;
        for (int s = 0; s < 4 && pIdx < allUUIDs.size(); s++) {
            int n = sideCounts[s];
            BlockFace facing = sideFacings[s];
            double totalWidth = (n - 1) * (HOUSE_W + HOUSE_GAP);

            for (int j = 0; j < n && pIdx < allUUIDs.size(); j++) {
                // Lateral offset along the side (perpendicular to the depth axis)
                int lat = (n == 1) ? 0
                        : (int) Math.round(-totalWidth / 2.0 + j * (HOUSE_W + HOUSE_GAP));

                int cx, cz;
                switch (s) {
                    case 0 -> { cx = lat;  cz = -dist; }  // NORTH
                    case 1 -> { cx = lat;  cz =  dist; }  // SOUTH
                    case 2 -> { cx =  dist; cz = lat;  }  // EAST
                    default-> { cx = -dist; cz = lat;  }  // WEST
                }

                UUID uuid = allUUIDs.get(pIdx++);
                String name = game.isBot(uuid) ? game.getBotName(uuid)
                        : players.stream().filter(p -> p.getUniqueId().equals(uuid))
                                .map(Player::getName).findFirst().orElse("Unknown");

                int groundY = world.getHighestBlockYAt(cx, cz);
                int ox = cx - HOUSE_W / 2;
                int oz = cz - HOUSE_D / 2;

                buildHouse(world, ox, groundY, oz, cx, cz, name, facing);
                game.getHouseSpawns().put(uuid,    spawnLoc(world, ox, groundY, oz, cx, cz, facing));
                game.getHouseEntrances().put(uuid, entranceLoc(world, ox, groundY, oz, cx, cz, facing));
            }
        }
    }

    // ---- Campfire ----

    private void placeCampfire(World world, int x, int y, int z) {
        org.bukkit.block.data.type.Campfire data =
                (org.bukkit.block.data.type.Campfire) Material.CAMPFIRE.createBlockData();
        data.setLit(true);
        world.getBlockAt(x, y, z).setBlockData(data);
    }

    /**
     * Builds a small discussion area around the campfire:
     * - 4 oak stair "chairs" facing the campfire (N/S/E/W at distance 3)
     * - 4 diagonal oak stair chairs at distance 3 (NE/NW/SE/SW)
     * - An OAK_WALL_SIGN on a post at the north side with "마피아 마을"
     */
    private void buildDiscussionArea(World world, int cx, int groundY, int cz) {
        // Floor circle: 5x5 stone brick platform (excluding corners)
        int[][] floorOffsets = {
            {-2,-1},{-2,0},{-2,1},
            {-1,-2},{-1,-1},{-1,0},{-1,1},{-1,2},
            {0,-2},{0,-1},{0,1},{0,2},
            {1,-2},{1,-1},{1,0},{1,1},{1,2},
            {2,-1},{2,0},{2,1}
        };
        for (int[] off : floorOffsets) {
            world.getBlockAt(cx + off[0], groundY, cz + off[1]).setType(Material.STONE_BRICKS);
        }

        // 4 cardinal chairs (stair facing the campfire center)
        // NORTH chair (z=-3): stair faces SOUTH toward campfire
        placeChair(world, cx,     groundY + 1, cz - 3, BlockFace.SOUTH);
        // SOUTH chair (z=+3): stair faces NORTH
        placeChair(world, cx,     groundY + 1, cz + 3, BlockFace.NORTH);
        // EAST  chair (x=+3): stair faces WEST
        placeChair(world, cx + 3, groundY + 1, cz,     BlockFace.WEST);
        // WEST  chair (x=-3): stair faces EAST
        placeChair(world, cx - 3, groundY + 1, cz,     BlockFace.EAST);

        // 4 diagonal chairs
        placeChair(world, cx - 2, groundY + 1, cz - 2, BlockFace.SOUTH);
        placeChair(world, cx + 2, groundY + 1, cz - 2, BlockFace.SOUTH);
        placeChair(world, cx - 2, groundY + 1, cz + 2, BlockFace.NORTH);
        placeChair(world, cx + 2, groundY + 1, cz + 2, BlockFace.NORTH);

        // Sign post: OAK_FENCE at (cx, groundY+1, cz-5), wall sign on south face
        world.getBlockAt(cx, groundY + 1, cz - 5).setType(Material.OAK_FENCE);
        world.getBlockAt(cx, groundY + 2, cz - 5).setType(Material.OAK_FENCE);
        Block signBlock = world.getBlockAt(cx, groundY + 3, cz - 5);
        WallSign signData = (WallSign) Material.OAK_WALL_SIGN.createBlockData();
        signData.setFacing(BlockFace.SOUTH);
        signBlock.setBlockData(signData);
        Sign sign = (Sign) signBlock.getState();
        sign.line(0, Component.text("==========="));
        sign.line(1, Component.text("마피아 마을"));
        sign.line(2, Component.text("토론 광장"));
        sign.line(3, Component.text("==========="));
        sign.update(true);
    }

    private void placeChair(World world, int x, int y, int z, BlockFace facing) {
        Stairs stair = (Stairs) Material.OAK_STAIRS.createBlockData();
        stair.setFacing(facing);
        world.getBlockAt(x, y, z).setBlockData(stair);
    }

    // ---- Spawn / Entrance locations ----

    private Location spawnLoc(World world, int ox, int groundY, int oz, int cx, int cz, BlockFace facing) {
        double x, z;
        float yaw;
        // Player spawns inside the house near the door, looking INTO the house (away from door)
        switch (facing) {
            case SOUTH -> { x = cx + 0.5; z = oz + HOUSE_D - 2.5; yaw = 180; } // door south, look north
            case NORTH -> { x = cx + 0.5; z = oz + 2.5;           yaw = 0;   } // door north, look south
            case EAST  -> { x = ox + HOUSE_W - 2.5; z = cz + 0.5; yaw = 90;  } // door east,  look west
            default    -> { x = ox + 2.5;            z = cz + 0.5; yaw = 270; } // door west,  look east
        }
        Location loc = new Location(world, x, groundY + 1, z);
        loc.setYaw(yaw);
        return loc;
    }

    private Location entranceLoc(World world, int ox, int groundY, int oz, int cx, int cz, BlockFace facing) {
        double x, z;
        // Just outside the door
        switch (facing) {
            case SOUTH -> { x = cx + 0.5; z = oz + HOUSE_D + 0.5; }
            case NORTH -> { x = cx + 0.5; z = oz - 0.5;            }
            case EAST  -> { x = ox + HOUSE_W + 0.5; z = cz + 0.5; }
            default    -> { x = ox - 0.5;            z = cz + 0.5; }
        }
        return new Location(world, x, groundY + 1, z);
    }

    // ---- House structure ----

    private void buildHouse(World world, int ox, int oy, int oz, int cx, int cz,
                            String playerName, BlockFace facing) {
        // Clear area (house volume + 1 layer above roof)
        for (int x = ox; x < ox + HOUSE_W; x++)
            for (int y = oy; y < oy + 7; y++)
                for (int z = oz; z < oz + HOUSE_D; z++)
                    world.getBlockAt(x, y, z).setType(Material.AIR);

        // Floor (y = oy)
        for (int x = ox; x < ox + HOUSE_W; x++)
            for (int z = oz; z < oz + HOUSE_D; z++)
                world.getBlockAt(x, oy, z).setType(Material.OAK_PLANKS);

        // Walls (y = oy+1 to oy+4, hollow)
        for (int y = oy + 1; y <= oy + 4; y++) {
            for (int x = ox; x < ox + HOUSE_W; x++) {
                world.getBlockAt(x, y, oz).setType(Material.OAK_PLANKS);
                world.getBlockAt(x, y, oz + HOUSE_D - 1).setType(Material.OAK_PLANKS);
            }
            for (int z = oz; z < oz + HOUSE_D; z++) {
                world.getBlockAt(ox,             y, z).setType(Material.OAK_PLANKS);
                world.getBlockAt(ox + HOUSE_W - 1, y, z).setType(Material.OAK_PLANKS);
            }
        }

        // Roof (y = oy+5)
        for (int x = ox; x < ox + HOUSE_W; x++)
            for (int z = oz; z < oz + HOUSE_D; z++)
                world.getBlockAt(x, oy + 5, z).setType(Material.OAK_PLANKS);

        // Directional interior elements (door, windows, bed, sign)
        switch (facing) {
            case SOUTH -> buildInteriorSouth(world, ox, oy, oz, cx, cz, playerName);
            case NORTH -> buildInteriorNorth(world, ox, oy, oz, cx, cz, playerName);
            case EAST  -> buildInteriorEast (world, ox, oy, oz, cx, cz, playerName);
            case WEST  -> buildInteriorWest (world, ox, oy, oz, cx, cz, playerName);
        }
    }

    // Door on SOUTH wall (oz+8), windows on NORTH wall (oz), bed near north wall
    private void buildInteriorSouth(World world, int ox, int oy, int oz, int cx, int cz, String name) {
        placePane(world, cx - 2, oy + 2, oz, BlockFace.EAST, BlockFace.WEST);
        placePane(world, cx + 2, oy + 2, oz, BlockFace.EAST, BlockFace.WEST);
        placeDoor(world, cx, oy + 1, oz + HOUSE_D - 1, BlockFace.SOUTH);
        // Bed: head at oz+1, foot at oz+2 → facing NORTH (foot-to-head = -Z)
        placeBed(world, cx, oy + 1, oz + 1, cx, oy + 1, oz + 2, BlockFace.NORTH);
        placeSign(world, cx, oy + 3, oz + HOUSE_D - 1, BlockFace.SOUTH, name);
    }

    // Door on NORTH wall (oz), windows on SOUTH wall (oz+8), bed near south wall
    private void buildInteriorNorth(World world, int ox, int oy, int oz, int cx, int cz, String name) {
        placePane(world, cx - 2, oy + 2, oz + HOUSE_D - 1, BlockFace.EAST, BlockFace.WEST);
        placePane(world, cx + 2, oy + 2, oz + HOUSE_D - 1, BlockFace.EAST, BlockFace.WEST);
        placeDoor(world, cx, oy + 1, oz, BlockFace.NORTH);
        // Bed: head at oz+7, foot at oz+6 → facing SOUTH (foot-to-head = +Z)
        placeBed(world, cx, oy + 1, oz + HOUSE_D - 2, cx, oy + 1, oz + HOUSE_D - 3, BlockFace.SOUTH);
        placeSign(world, cx, oy + 3, oz, BlockFace.NORTH, name);
    }

    // Door on EAST wall (ox+8), windows on WEST wall (ox), bed near west wall
    private void buildInteriorEast(World world, int ox, int oy, int oz, int cx, int cz, String name) {
        placePane(world, ox, oy + 2, cz - 2, BlockFace.NORTH, BlockFace.SOUTH);
        placePane(world, ox, oy + 2, cz + 2, BlockFace.NORTH, BlockFace.SOUTH);
        placeDoor(world, ox + HOUSE_W - 1, oy + 1, cz, BlockFace.EAST);
        // Bed: head at ox+1, foot at ox+2 → facing WEST (foot-to-head = -X)
        placeBed(world, ox + 1, oy + 1, cz, ox + 2, oy + 1, cz, BlockFace.WEST);
        placeSign(world, ox + HOUSE_W - 1, oy + 3, cz, BlockFace.EAST, name);
    }

    // Door on WEST wall (ox), windows on EAST wall (ox+8), bed near east wall
    private void buildInteriorWest(World world, int ox, int oy, int oz, int cx, int cz, String name) {
        placePane(world, ox + HOUSE_W - 1, oy + 2, cz - 2, BlockFace.NORTH, BlockFace.SOUTH);
        placePane(world, ox + HOUSE_W - 1, oy + 2, cz + 2, BlockFace.NORTH, BlockFace.SOUTH);
        placeDoor(world, ox, oy + 1, cz, BlockFace.WEST);
        // Bed: head at ox+7, foot at ox+6 → facing EAST (foot-to-head = +X)
        placeBed(world, ox + HOUSE_W - 2, oy + 1, cz, ox + HOUSE_W - 3, oy + 1, cz, BlockFace.EAST);
        placeSign(world, ox, oy + 3, cz, BlockFace.WEST, name);
    }

    // ---- Block placement helpers ----

    private void placePane(World world, int x, int y, int z, BlockFace f1, BlockFace f2) {
        org.bukkit.block.data.MultipleFacing pane =
                (org.bukkit.block.data.MultipleFacing) Material.GLASS_PANE.createBlockData();
        pane.setFace(f1, true);
        pane.setFace(f2, true);
        world.getBlockAt(x, y, z).setBlockData(pane, false);
    }

    private void placeDoor(World world, int x, int y, int z, BlockFace facing) {
        Block bottom = world.getBlockAt(x, y, z);
        Door botData = (Door) Material.OAK_DOOR.createBlockData();
        botData.setFacing(facing);
        botData.setHalf(Door.Half.BOTTOM);
        bottom.setBlockData(botData);

        Block top = world.getBlockAt(x, y + 1, z);
        Door topData = (Door) Material.OAK_DOOR.createBlockData();
        topData.setFacing(facing);
        topData.setHalf(Door.Half.TOP);
        top.setBlockData(topData);
    }

    /**
     * Places a two-block bed.
     * @param hx/hy/hz  position of the HEAD block
     * @param fx/fy/fz  position of the FOOT block
     * @param facing    direction from FOOT to HEAD
     */
    private void placeBed(World world, int hx, int hy, int hz, int fx, int fy, int fz, BlockFace facing) {
        org.bukkit.block.data.type.Bed headData =
                (org.bukkit.block.data.type.Bed) Material.RED_BED.createBlockData();
        headData.setFacing(facing);
        headData.setPart(org.bukkit.block.data.type.Bed.Part.HEAD);
        world.getBlockAt(hx, hy, hz).setBlockData(headData, false);

        org.bukkit.block.data.type.Bed footData =
                (org.bukkit.block.data.type.Bed) Material.RED_BED.createBlockData();
        footData.setFacing(facing);
        footData.setPart(org.bukkit.block.data.type.Bed.Part.FOOT);
        world.getBlockAt(fx, fy, fz).setBlockData(footData, false);
    }

    private void placeSign(World world, int x, int y, int z, BlockFace facing, String name) {
        org.bukkit.block.data.type.WallSign signData =
                (org.bukkit.block.data.type.WallSign) Material.OAK_WALL_SIGN.createBlockData();
        signData.setFacing(facing);
        Block signBlock = world.getBlockAt(x, y, z);
        signBlock.setBlockData(signData);
        org.bukkit.block.Sign signState = (org.bukkit.block.Sign) signBlock.getState();
        signState.line(1, Component.text(name));
        signState.update();
    }
}
