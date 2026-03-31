package com.mafia.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

public class MafiaGame {

    private final List<Player> alivePlayers = new ArrayList<>();
    private final List<Player> deadPlayers = new ArrayList<>();
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Map<UUID, Location> houseSpawns = new HashMap<>();
    private final Map<UUID, Location> houseEntrances = new HashMap<>();
    private GameState state = GameState.WAITING;
    private World gameWorld;
    private int dayCount = 0;

    // Test mode bot support
    private boolean testMode = false;
    private final Map<UUID, String> botNames = new LinkedHashMap<>(); // UUID -> "BOT_1"
    private final Set<UUID> aliveBotUUIDs = new LinkedHashSet<>();
    private final Set<UUID> deadBotUUIDs = new LinkedHashSet<>();

    // Night targets
    private UUID mafiaKillTarget = null;
    private UUID doctorSaveTarget = null;
    private UUID policeCheckTarget = null;
    private final Map<UUID, UUID> mafiaVotes = new HashMap<>();

    // Tribunal
    private UUID playerOnTrial = null;
    private final Map<UUID, UUID> nominationVotes = new HashMap<>();
    private final Map<UUID, Boolean> executionVotes = new HashMap<>();

    private int phaseTaskId = -1;

    public MafiaGame(World gameWorld, List<Player> players) {
        this.gameWorld = gameWorld;
        this.alivePlayers.addAll(players);
    }

    public List<Player> getAlivePlayers() { return alivePlayers; }
    public List<Player> getDeadPlayers() { return deadPlayers; }
    public Map<UUID, Role> getRoles() { return roles; }
    public Map<UUID, Location> getHouseSpawns() { return houseSpawns; }
    public Map<UUID, Location> getHouseEntrances() { return houseEntrances; }
    public GameState getState() { return state; }
    public void setState(GameState state) { this.state = state; }
    public World getGameWorld() { return gameWorld; }
    public int getDayCount() { return dayCount; }
    public void incrementDay() { dayCount++; }

    public Role getRole(UUID uuid) { return roles.getOrDefault(uuid, Role.CIVILIAN); }
    public Role getRole(Player p) { return getRole(p.getUniqueId()); }

    // ---- Bot support ----

    public boolean isTestMode() { return testMode; }
    public void setTestMode(boolean testMode) { this.testMode = testMode; }

    public void addBot(UUID uuid, String name) {
        botNames.put(uuid, name);
        aliveBotUUIDs.add(uuid);
    }

    public boolean isBot(UUID uuid) { return botNames.containsKey(uuid); }
    public String getBotName(UUID uuid) { return botNames.getOrDefault(uuid, "BOT"); }
    public Set<UUID> getAliveBotUUIDs() { return aliveBotUUIDs; }
    public Set<UUID> getDeadBotUUIDs() { return deadBotUUIDs; }
    public boolean isBotAlive(UUID uuid) { return aliveBotUUIDs.contains(uuid); }

    public void killBot(UUID uuid) {
        aliveBotUUIDs.remove(uuid);
        deadBotUUIDs.add(uuid);
    }

    /** Total alive count including bots. */
    public int getTotalAliveCount() {
        return alivePlayers.size() + aliveBotUUIDs.size();
    }

    /** All alive UUIDs (real players + bots). */
    public List<UUID> getAllAliveUUIDs() {
        List<UUID> uuids = new ArrayList<>();
        alivePlayers.forEach(p -> uuids.add(p.getUniqueId()));
        uuids.addAll(aliveBotUUIDs);
        return uuids;
    }

    /** All alive mafia UUIDs (real players + bots). */
    public Set<UUID> getAllAliveMafiaUUIDs() {
        Set<UUID> set = new HashSet<>();
        alivePlayers.stream()
                .filter(p -> roles.getOrDefault(p.getUniqueId(), Role.CIVILIAN) == Role.MAFIA)
                .forEach(p -> set.add(p.getUniqueId()));
        aliveBotUUIDs.stream()
                .filter(uuid -> roles.getOrDefault(uuid, Role.CIVILIAN) == Role.MAFIA)
                .forEach(set::add);
        return set;
    }

    /** Display name for any participant UUID (player or bot). */
    public String getParticipantName(UUID uuid) {
        if (botNames.containsKey(uuid)) return botNames.get(uuid);
        for (Player p : alivePlayers) if (p.getUniqueId().equals(uuid)) return p.getName();
        for (Player p : deadPlayers) if (p.getUniqueId().equals(uuid)) return p.getName();
        return "Unknown";
    }

    // ---- Existing methods ----

    public UUID getMafiaKillTarget() { return mafiaKillTarget; }
    public void setMafiaKillTarget(UUID target) { mafiaKillTarget = target; }
    public UUID getDoctorSaveTarget() { return doctorSaveTarget; }
    public void setDoctorSaveTarget(UUID target) { doctorSaveTarget = target; }
    public UUID getPoliceCheckTarget() { return policeCheckTarget; }
    public void setPoliceCheckTarget(UUID target) { policeCheckTarget = target; }
    public Map<UUID, UUID> getMafiaVotes() { return mafiaVotes; }

    public UUID getPlayerOnTrial() { return playerOnTrial; }
    public void setPlayerOnTrial(UUID uuid) { playerOnTrial = uuid; }
    public Map<UUID, UUID> getNominationVotes() { return nominationVotes; }
    public Map<UUID, Boolean> getExecutionVotes() { return executionVotes; }

    public int getPhaseTaskId() { return phaseTaskId; }
    public void setPhaseTaskId(int id) { phaseTaskId = id; }

    /** True if UUID is alive (real player or bot). */
    public boolean isAlive(UUID uuid) {
        return alivePlayers.stream().anyMatch(p -> p.getUniqueId().equals(uuid))
                || aliveBotUUIDs.contains(uuid);
    }

    public Optional<Player> getAlivePlayer(UUID uuid) {
        return alivePlayers.stream().filter(p -> p.getUniqueId().equals(uuid)).findFirst();
    }

    public List<Player> getMafiaPlayers() {
        return alivePlayers.stream()
                .filter(p -> roles.getOrDefault(p.getUniqueId(), Role.CIVILIAN) == Role.MAFIA)
                .toList();
    }

    /** Count alive participants (real + bots) with given role. */
    public long countAliveByRole(Role role) {
        long playerCount = alivePlayers.stream()
                .filter(p -> roles.getOrDefault(p.getUniqueId(), Role.CIVILIAN) == role)
                .count();
        long botCount = aliveBotUUIDs.stream()
                .filter(uuid -> roles.getOrDefault(uuid, Role.CIVILIAN) == role)
                .count();
        return playerCount + botCount;
    }

    public void resetNightTargets() {
        mafiaKillTarget = null;
        doctorSaveTarget = null;
        policeCheckTarget = null;
        mafiaVotes.clear();
    }

    public void resetTribunalVotes() {
        nominationVotes.clear();
        executionVotes.clear();
        playerOnTrial = null;
    }
}
