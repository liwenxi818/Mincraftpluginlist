package com.mafia.manager;

import com.mafia.MafiaPlugin;
import com.mafia.game.GameState;
import com.mafia.game.MafiaGame;
import com.mafia.game.Role;
import com.mafia.gui.ExecutionVoteGUI;
import com.mafia.gui.MayorElectionGUI;
import com.mafia.gui.NightActionGUI;
import com.mafia.gui.NominationGUI;
import com.mafia.util.ParticleUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager {

    private final MafiaPlugin plugin;
    private final WorldManager worldManager;
    private final HouseManager houseManager;
    private MafiaGame currentGame = null;
    private final List<Player> lobby = new ArrayList<>();

    private Scoreboard scoreboard;
    private Team mafiaTeam;

    public GameManager(MafiaPlugin plugin) {
        this.plugin = plugin;
        this.worldManager = new WorldManager(plugin);
        this.houseManager = new HouseManager(plugin);
    }

    // ---- Lobby ----

    public boolean joinLobby(Player player) {
        if (currentGame != null) {
            player.sendMessage(Component.text("게임이 이미 진행 중입니다.", NamedTextColor.RED));
            return false;
        }
        if (lobby.contains(player)) {
            player.sendMessage(Component.text("이미 대기 중입니다.", NamedTextColor.YELLOW));
            return false;
        }
        lobby.add(player);
        broadcast(Component.text(player.getName() + "님이 마피아 게임에 참가했습니다. (" + lobby.size() + "명)", NamedTextColor.AQUA));
        return true;
    }

    public boolean leaveLobby(Player player) {
        if (lobby.remove(player)) {
            broadcast(Component.text(player.getName() + "님이 대기에서 나갔습니다. (" + lobby.size() + "명)", NamedTextColor.GRAY));
            return true;
        }
        return false;
    }

    public List<Player> getLobby() { return lobby; }

    // ---- Game Start ----

    public void startGame(Player starter) {
        if (currentGame != null) {
            starter.sendMessage(Component.text("게임이 이미 진행 중입니다.", NamedTextColor.RED));
            return;
        }
        List<Player> players = new ArrayList<>(lobby);
        if (players.isEmpty()) {
            players.addAll(Bukkit.getOnlinePlayers());
        }
        if (players.size() < 4) {
            starter.sendMessage(Component.text("최소 4명이 필요합니다. 현재: " + players.size() + "명", NamedTextColor.RED));
            return;
        }

        broadcast(Component.text("=== 마피아 게임을 시작합니다! ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        World world = worldManager.createGameWorld();
        if (world == null) {
            starter.sendMessage(Component.text("월드 생성 실패!", NamedTextColor.RED));
            return;
        }

        currentGame = new MafiaGame(world, players);
        lobby.clear();

        assignRoles();
        setupScoreboard();
        houseManager.buildAllHouses(currentGame);

        for (Player p : currentGame.getAlivePlayers()) {
            Location spawn = currentGame.getHouseSpawns().get(p.getUniqueId());
            if (spawn != null) p.teleport(spawn);
            p.setGameMode(GameMode.ADVENTURE);
            applyGameBuffs(p);

            Role role = currentGame.getRole(p);
            p.sendMessage(Component.text("당신의 역할은 ", NamedTextColor.YELLOW)
                    .append(Component.text(role.getDisplayName(), role.getColor(), TextDecoration.BOLD))
                    .append(Component.text(" 입니다!", NamedTextColor.YELLOW)));

            if (role == Role.MAFIA) {
                String mafiaNames = currentGame.getMafiaPlayers().stream()
                        .map(Player::getName).collect(Collectors.joining(", "));
                p.sendMessage(Component.text("마피아 팀: " + mafiaNames, NamedTextColor.RED));
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::startDay, 60L);
    }

    public void startTestGame(Player starter, int requestedBots) {
        if (currentGame != null) {
            starter.sendMessage(Component.text("게임이 이미 진행 중입니다.", NamedTextColor.RED));
            return;
        }
        List<Player> players = new ArrayList<>(lobby);
        if (players.isEmpty()) {
            players.addAll(Bukkit.getOnlinePlayers());
        }
        if (players.isEmpty()) {
            starter.sendMessage(Component.text("최소 1명이 필요합니다.", NamedTextColor.RED));
            return;
        }

        broadcast(Component.text("=== [테스트] 마피아 게임을 시작합니다! ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        World world = worldManager.createGameWorld();
        if (world == null) {
            starter.sendMessage(Component.text("월드 생성 실패!", NamedTextColor.RED));
            return;
        }

        currentGame = new MafiaGame(world, players);
        currentGame.setTestMode(true);
        lobby.clear();

        // botCount < 0: auto-fill to minimum 4; otherwise use exact count
        int needed = (requestedBots < 0) ? Math.max(0, 4 - players.size()) : requestedBots;
        for (int i = 1; i <= needed; i++) {
            currentGame.addBot(UUID.randomUUID(), "BOT_" + i);
        }

        assignRoles();
        setupScoreboard();
        houseManager.buildAllHouses(currentGame);

        for (Player p : currentGame.getAlivePlayers()) {
            Location spawn = currentGame.getHouseSpawns().get(p.getUniqueId());
            if (spawn != null) p.teleport(spawn);
            p.setGameMode(GameMode.ADVENTURE);
            applyGameBuffs(p);

            Role role = currentGame.getRole(p);
            p.sendMessage(Component.text("[테스트] 당신의 역할은 ", NamedTextColor.YELLOW)
                    .append(Component.text(role.getDisplayName(), role.getColor(), TextDecoration.BOLD))
                    .append(Component.text(" 입니다!", NamedTextColor.YELLOW)));

            if (role == Role.MAFIA) {
                List<String> mafiaNames = new ArrayList<>();
                currentGame.getMafiaPlayers().forEach(mp -> mafiaNames.add(mp.getName()));
                currentGame.getAliveBotUUIDs().stream()
                        .filter(u -> currentGame.getRole(u) == Role.MAFIA)
                        .map(currentGame::getBotName)
                        .forEach(mafiaNames::add);
                p.sendMessage(Component.text("마피아 팀: " + String.join(", ", mafiaNames), NamedTextColor.RED));
            }
        }

        // Tell starter about bot roles for debugging
        starter.sendMessage(Component.text("[테스트] 봇 역할 정보:", NamedTextColor.GRAY));
        for (UUID botUUID : currentGame.getAliveBotUUIDs()) {
            Role botRole = currentGame.getRole(botUUID);
            starter.sendMessage(Component.text("  " + currentGame.getBotName(botUUID) + ": "
                    + botRole.getDisplayName(), botRole.getColor()));
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::startDay, 60L);
    }

    private void assignRoles() {
        // Assign to all participants: real players + bots
        List<UUID> allUUIDs = new ArrayList<>();
        currentGame.getAlivePlayers().forEach(p -> allUUIDs.add(p.getUniqueId()));
        allUUIDs.addAll(currentGame.getAliveBotUUIDs());

        Collections.shuffle(allUUIDs);
        Map<UUID, Role> roles = currentGame.getRoles();
        int n = allUUIDs.size();

        int mafiaCount, policeCount = 1, doctorCount = 0, jokerCount = 0;
        if (n <= 5) {
            mafiaCount = 1;
        } else if (n <= 7) {
            mafiaCount = 2; doctorCount = 1;
        } else if (n <= 10) {
            mafiaCount = 2; doctorCount = 1; jokerCount = 1;
        } else {
            mafiaCount = 3; doctorCount = 1; jokerCount = 1;
        }

        int idx = 0;
        for (int i = 0; i < mafiaCount; i++) roles.put(allUUIDs.get(idx++), Role.MAFIA);
        roles.put(allUUIDs.get(idx++), Role.POLICE);
        if (doctorCount > 0) roles.put(allUUIDs.get(idx++), Role.DOCTOR);
        if (jokerCount > 0) roles.put(allUUIDs.get(idx++), Role.JOKER);
        while (idx < allUUIDs.size()) roles.put(allUUIDs.get(idx++), Role.CIVILIAN);
    }

    private void setupScoreboard() {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        mafiaTeam = scoreboard.registerNewTeam("mafia");
        mafiaTeam.color(NamedTextColor.RED);
        mafiaTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);

        for (Player mafia : currentGame.getMafiaPlayers()) {
            mafiaTeam.addEntry(mafia.getName());
        }
        // Add bot mafia names to team (for nametag display)
        currentGame.getAliveBotUUIDs().stream()
                .filter(u -> currentGame.getRole(u) == Role.MAFIA)
                .map(currentGame::getBotName)
                .forEach(mafiaTeam::addEntry);

        for (Player mafia : currentGame.getMafiaPlayers()) {
            mafia.setScoreboard(scoreboard);
        }

        Objective obj = scoreboard.registerNewObjective("gameInfo", Criteria.DUMMY,
                Component.text("§6§l마피아 게임"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    private void updateScoreboard() {
        if (currentGame == null || scoreboard == null) return;
        Objective obj = scoreboard.getObjective("gameInfo");
        if (obj == null) return;
        // 기존 점수 초기화 후 재설정
        for (String entry : scoreboard.getEntries()) {
            if (!mafiaTeam.hasEntry(entry)) scoreboard.resetScores(entry);
        }
        int score = 6;
        obj.getScore("§e" + currentGame.getDayCount() + "일차").setScore(score--);
        obj.getScore("§f페이즈: " + currentGame.getState().name()).setScore(score--);
        if (currentGame.getMayorUUID() != null) {
            String mayorName = currentGame.getParticipantName(currentGame.getMayorUUID());
            obj.getScore("§6시장: " + mayorName).setScore(score--);
        }
        obj.getScore("§a생존: " + currentGame.getTotalAliveCount() + "명").setScore(score--);
        obj.getScore("§c사망: " + currentGame.getDeadPlayers().size() + "명").setScore(score--);

        // 모든 플레이어에게 스코어보드 적용
        for (Player p : currentGame.getAlivePlayers()) p.setScoreboard(scoreboard);
        for (Player p : currentGame.getDeadPlayers()) p.setScoreboard(scoreboard);
    }

    private void broadcastActionBar(Component msg) {
        for (Player p : currentGame.getAlivePlayers()) {
            p.sendActionBar(msg);
        }
        for (Player p : currentGame.getDeadPlayers()) {
            p.sendActionBar(msg);
        }
    }

    // ---- Day Phase ----

    public void startDay() {
        if (currentGame == null) return;
        currentGame.incrementDay();
        int day = currentGame.getDayCount();

        broadcast(Component.text("=== " + day + "일차 낮 ===", NamedTextColor.YELLOW, TextDecoration.BOLD));
        for (Player p : currentGame.getAlivePlayers()) {
            p.setGameMode(GameMode.ADVENTURE);
            applyGameBuffs(p);
        }
        updateScoreboard();
        cancelPhaseTask();

        if (day == 1) {
            startMayorElection();
        } else {
            startDayDiscussion();
        }
    }

    private void startMayorElection() {
        if (currentGame == null) return;
        currentGame.setState(GameState.DAY_MAYOR_ELECTION);

        broadcast(Component.text("=== 시장 선출 투표 ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        broadcast(Component.text("30초 내에 시장으로 선출할 플레이어를 선택하세요!", NamedTextColor.GOLD));

        for (Player p : currentGame.getAlivePlayers()) {
            MayorElectionGUI.open(p, currentGame);
        }

        // Bot auto-vote for mayor
        if (currentGame.isTestMode() && !currentGame.getAliveBotUUIDs().isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (currentGame == null || currentGame.getState() != GameState.DAY_MAYOR_ELECTION) return;
                List<UUID> allAlive = currentGame.getAllAliveUUIDs();
                Random rand = new Random();
                for (UUID botUUID : new ArrayList<>(currentGame.getAliveBotUUIDs())) {
                    List<UUID> targets = allAlive.stream()
                            .filter(u -> !u.equals(botUUID))
                            .collect(Collectors.toList());
                    if (!targets.isEmpty()) {
                        currentGame.getMayorVotes().put(botUUID, targets.get(rand.nextInt(targets.size())));
                    }
                }
            }, 40L);
        }

        cancelPhaseTask();
        BukkitTask task = new BukkitRunnable() {
            int remaining = 30;
            @Override
            public void run() {
                if (currentGame == null || currentGame.getState() != GameState.DAY_MAYOR_ELECTION) { cancel(); return; }
                if (remaining <= 0) { cancel(); resolveMayorElection(); return; }
                broadcastActionBar(Component.text("시장 선출 투표: ", NamedTextColor.GOLD)
                        .append(Component.text(remaining + "초", NamedTextColor.WHITE)));
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        currentGame.setPhaseTaskId(task.getTaskId());
    }

    private void resolveMayorElection() {
        if (currentGame == null) return;

        for (Player p : currentGame.getAlivePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof MayorElectionGUI.Holder) {
                p.closeInventory();
            }
        }

        Map<UUID, UUID> votes = currentGame.getMayorVotes();
        Map<UUID, Integer> voteCounts = new HashMap<>();
        for (UUID nominee : votes.values()) {
            if (!nominee.equals(MafiaGame.PASS_UUID)) voteCounts.merge(nominee, 1, Integer::sum);
        }

        if (voteCounts.isEmpty()) {
            broadcast(Component.text("투표 결과 시장이 선출되지 않았습니다.", NamedTextColor.GRAY));
        } else {
            int max = Collections.max(voteCounts.values());
            List<UUID> top = voteCounts.entrySet().stream()
                    .filter(e -> e.getValue() == max)
                    .map(Map.Entry::getKey)
                    .toList();
            if (top.size() > 1) {
                broadcast(Component.text("동점으로 시장이 선출되지 않았습니다.", NamedTextColor.GRAY));
            } else {
                UUID mayorUUID = top.get(0);
                currentGame.setMayorUUID(mayorUUID);
                String mayorName = currentGame.getParticipantName(mayorUUID);
                broadcast(Component.text("[시장] " + mayorName + "님이 시장으로 선출되었습니다! (" + max + "표)",
                        NamedTextColor.GOLD, TextDecoration.BOLD));
            }
        }

        updateScoreboard();
        // Day 1 has no discussion: go straight to night after mayor election
        startNight();
    }

    private void startDayDiscussion() {
        if (currentGame == null) return;
        currentGame.setState(GameState.DAY_DISCUSSION);
        broadcast(Component.text("자유롭게 이동하고 토론하세요. 2분 후 투표를 시작합니다.", NamedTextColor.YELLOW));

        cancelPhaseTask();
        BukkitTask task = new BukkitRunnable() {
            int remaining = 120;
            @Override
            public void run() {
                if (currentGame == null || currentGame.getState() != GameState.DAY_DISCUSSION) { cancel(); return; }
                if (remaining <= 0) { cancel(); startNomination(); return; }
                broadcastActionBar(Component.text("낮 토론: ", NamedTextColor.YELLOW)
                        .append(Component.text(remaining + "초", NamedTextColor.WHITE)));
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        currentGame.setPhaseTaskId(task.getTaskId());
    }

    public void startNomination() {
        if (currentGame == null) return;
        currentGame.setState(GameState.DAY_NOMINATION);
        currentGame.resetTribunalVotes();

        broadcast(Component.text("=== 심판대 투표 ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        broadcast(Component.text("30초 내에 심판대에 올릴 플레이어를 선택하세요!", NamedTextColor.GOLD));

        for (Player p : currentGame.getAlivePlayers()) {
            NominationGUI.open(p, currentGame);
        }

        // Bot auto-nomination
        if (currentGame.isTestMode() && !currentGame.getAliveBotUUIDs().isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (currentGame == null || currentGame.getState() != GameState.DAY_NOMINATION) return;
                List<UUID> allAlive = currentGame.getAllAliveUUIDs();
                Random rand = new Random();
                for (UUID botUUID : new ArrayList<>(currentGame.getAliveBotUUIDs())) {
                    List<UUID> targets = allAlive.stream()
                            .filter(u -> !u.equals(botUUID))
                            .collect(Collectors.toList());
                    if (!targets.isEmpty()) {
                        currentGame.getNominationVotes().put(botUUID, targets.get(rand.nextInt(targets.size())));
                    }
                }
            }, 40L);
        }

        updateScoreboard();
        cancelPhaseTask();
        BukkitTask task = new BukkitRunnable() {
            int remaining = 30;
            @Override
            public void run() {
                if (currentGame == null || currentGame.getState() != GameState.DAY_NOMINATION) { cancel(); return; }
                if (remaining <= 0) { cancel(); resolveNomination(); return; }
                broadcastActionBar(Component.text("지목 투표: ", NamedTextColor.GOLD)
                        .append(Component.text(remaining + "초", NamedTextColor.WHITE)));
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        currentGame.setPhaseTaskId(task.getTaskId());
    }

    private void resolveNomination() {
        if (currentGame == null) return;

        for (Player p : currentGame.getAlivePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof NominationGUI.Holder) {
                p.closeInventory();
            }
        }

        Map<UUID, UUID> votes = currentGame.getNominationVotes();
        Map<UUID, Integer> voteCounts = new HashMap<>();
        for (UUID nominee : votes.values()) {
            voteCounts.merge(nominee, 1, Integer::sum);
        }

        if (voteCounts.isEmpty()) {
            broadcast(Component.text("아무도 지목되지 않았습니다. 밤이 시작됩니다.", NamedTextColor.GRAY));
            startNight();
            return;
        }

        int max = Collections.max(voteCounts.values());
        List<UUID> topNominees = voteCounts.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();

        if (topNominees.size() > 1) {
            broadcast(Component.text("동점으로 심판대에 올릴 수 없습니다. 밤이 시작됩니다.", NamedTextColor.GRAY));
            startNight();
            return;
        }

        UUID trialUUID = topNominees.get(0);
        currentGame.setPlayerOnTrial(trialUUID);
        String trialName = currentGame.getParticipantName(trialUUID);

        broadcast(Component.text(trialName + "님이 심판대에 올랐습니다! (" + max + "표)", NamedTextColor.GOLD));
        broadcast(Component.text("30초간 최후의 변론을 진행하세요.", NamedTextColor.YELLOW));
        currentGame.setState(GameState.DAY_TRIAL);

        cancelPhaseTask();
        BukkitTask task = new BukkitRunnable() {
            int remaining = 30;
            @Override
            public void run() {
                if (currentGame == null || currentGame.getState() != GameState.DAY_TRIAL) { cancel(); return; }
                if (remaining <= 0) { cancel(); startExecutionVote(); return; }
                broadcastActionBar(Component.text("최후 변론: ", NamedTextColor.GOLD)
                        .append(Component.text(remaining + "초", NamedTextColor.WHITE)));
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        currentGame.setPhaseTaskId(task.getTaskId());
    }

    private void startExecutionVote() {
        if (currentGame == null) return;
        currentGame.setState(GameState.DAY_VOTE);

        UUID trialUUID = currentGame.getPlayerOnTrial();
        if (trialUUID == null) { startNight(); return; }

        String trialName = currentGame.getParticipantName(trialUUID);
        broadcast(Component.text("=== 처형 투표 ===", NamedTextColor.RED, TextDecoration.BOLD));
        broadcast(Component.text(trialName + "님을 처형하시겠습니까? 20초 내에 투표하세요.", NamedTextColor.RED));

        Optional<Player> trialPlayerOpt = currentGame.getAlivePlayer(trialUUID);
        for (Player p : currentGame.getAlivePlayers()) {
            if (!p.getUniqueId().equals(trialUUID)) {
                if (trialPlayerOpt.isPresent()) {
                    ExecutionVoteGUI.open(p, trialPlayerOpt.get(), currentGame);
                } else {
                    ExecutionVoteGUI.open(p, trialUUID, trialName, currentGame);
                }
            }
        }

        // Bot auto-vote on execution
        if (currentGame.isTestMode() && !currentGame.getAliveBotUUIDs().isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (currentGame == null || currentGame.getState() != GameState.DAY_VOTE) return;
                Random rand = new Random();
                for (UUID botUUID : new ArrayList<>(currentGame.getAliveBotUUIDs())) {
                    if (!botUUID.equals(trialUUID)) {
                        currentGame.getExecutionVotes().put(botUUID, rand.nextBoolean());
                    }
                }
                // Trigger early resolution if all voters have voted
                int totalVoters = currentGame.getTotalAliveCount() - 1;
                if (currentGame.getExecutionVotes().size() >= totalVoters) {
                    cancelPhaseTask();
                    resolveExecutionVote(true);
                }
            }, 40L);
        }

        cancelPhaseTask();
        BukkitTask task = new BukkitRunnable() {
            int remaining = 20;
            @Override
            public void run() {
                if (currentGame == null || currentGame.getState() != GameState.DAY_VOTE) { cancel(); return; }
                if (remaining <= 0) { cancel(); resolveExecutionVote(false); return; }
                broadcastActionBar(Component.text("처형 투표: ", NamedTextColor.RED)
                        .append(Component.text(remaining + "초", NamedTextColor.WHITE)));
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        currentGame.setPhaseTaskId(task.getTaskId());
    }

    public void resolveExecutionVote(boolean forced) {
        if (currentGame == null) return;
        if (currentGame.getState() != GameState.DAY_VOTE && !forced) return;

        for (Player p : currentGame.getAlivePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof ExecutionVoteGUI.Holder) {
                p.closeInventory();
            }
        }

        UUID trialUUID = currentGame.getPlayerOnTrial();
        if (trialUUID == null) { startNight(); return; }

        Map<UUID, Boolean> votes = currentGame.getExecutionVotes();
        long executeVotes = votes.values().stream().filter(v -> v).count();
        long spareVotes = votes.values().stream().filter(v -> !v).count();
        int voterCount = currentGame.getTotalAliveCount() - 1; // exclude trial player

        broadcast(Component.text("처형: " + executeVotes + "표 / 무죄: " + spareVotes + "표 / 총 유권자: " + voterCount + "명", NamedTextColor.YELLOW));

        boolean executed = executeVotes > voterCount / 2.0;

        if (executed) {
            String victimName = currentGame.getParticipantName(trialUUID);
            Role victimRole = currentGame.getRole(trialUUID);
            broadcast(Component.text(victimName + "님이 처형되었습니다! 역할: " + victimRole.getDisplayName(), NamedTextColor.RED));

            if (victimRole == Role.JOKER) {
                broadcast(Component.text("=== 조커 " + victimName + "님이 처형되어 단독 승리했습니다! ===", NamedTextColor.YELLOW, TextDecoration.BOLD));
                Optional<Player> jokerPlayer = currentGame.getAlivePlayer(trialUUID);
                endGame(Role.JOKER, jokerPlayer.orElse(null));
                return;
            }

            Optional<Player> victimPlayer = currentGame.getAlivePlayer(trialUUID);
            if (victimPlayer.isPresent()) {
                killPlayer(victimPlayer.get(), false);
            } else if (currentGame.isTestMode() && currentGame.isBotAlive(trialUUID)) {
                currentGame.killBot(trialUUID);
            }
            if (!checkWinConditions()) {
                startNight();
            }
        } else {
            broadcast(Component.text("과반수 미달로 처형이 취소되었습니다.", NamedTextColor.GREEN));
            startNight();
        }
    }

    // ---- Night Phase ----

    public void startNight() {
        if (currentGame == null) return;
        currentGame.setState(GameState.NIGHT_MAFIA);
        currentGame.resetNightTargets();

        broadcast(Component.text("=== 밤이 되었습니다 ===", NamedTextColor.DARK_BLUE, TextDecoration.BOLD));
        broadcast(Component.text("모든 플레이어가 집으로 돌아갑니다...", NamedTextColor.BLUE));

        for (Player p : currentGame.getAlivePlayers()) {
            Location home = currentGame.getHouseSpawns().get(p.getUniqueId());
            if (home != null) p.teleport(home);
        }

        currentGame.getGameWorld().setTime(18000);
        Bukkit.getScheduler().runTaskLater(plugin, this::startMafiaPhase, 40L);
    }

    private void startMafiaPhase() {
        if (currentGame == null) return;
        currentGame.setState(GameState.NIGHT_MAFIA);
        updateScoreboard();

        List<Player> mafias = currentGame.getMafiaPlayers();
        if (mafias.isEmpty() && currentGame.getAllAliveMafiaUUIDs().isEmpty()) {
            startDoctorPhase();
            return;
        }

        for (Player mafia : mafias) {
            mafia.sendMessage(Component.text("=== 마피아 행동 ===", NamedTextColor.RED, TextDecoration.BOLD));
            mafia.sendMessage(Component.text("제거할 플레이어를 선택하세요. (60초)", NamedTextColor.RED));
            NightActionGUI.open(mafia, currentGame, Role.MAFIA);
        }

        for (Player p : currentGame.getAlivePlayers()) {
            if (currentGame.getRole(p) != Role.MAFIA) {
                p.sendMessage(Component.text("마피아가 활동 중입니다...", NamedTextColor.DARK_GRAY));
            }
        }

        // Bot mafia auto-action
        if (currentGame.isTestMode()) {
            Set<UUID> botMafiaUUIDs = currentGame.getAllAliveMafiaUUIDs().stream()
                    .filter(currentGame::isBot)
                    .collect(Collectors.toSet());
            if (!botMafiaUUIDs.isEmpty()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (currentGame == null || currentGame.getState() != GameState.NIGHT_MAFIA) return;
                    Random rand = new Random();
                    List<UUID> targets = currentGame.getAllAliveUUIDs().stream()
                            .filter(u -> currentGame.getRole(u) != Role.MAFIA)
                            .collect(Collectors.toList());
                    if (targets.isEmpty()) return;
                    for (UUID botMafia : botMafiaUUIDs) {
                        currentGame.getMafiaVotes().put(botMafia, targets.get(rand.nextInt(targets.size())));
                    }
                    if (currentGame.getMafiaVotes().size() >= currentGame.getAllAliveMafiaUUIDs().size()) {
                        cancelPhaseTask();
                        resolveMafiaVotes();
                        startDoctorPhase();
                    }
                }, 40L);
            }
        }

        cancelPhaseTask();
        BukkitTask task = new BukkitRunnable() {
            int remaining = 60;
            @Override
            public void run() {
                if (currentGame == null || currentGame.getState() != GameState.NIGHT_MAFIA) { cancel(); return; }
                if (remaining <= 0) { cancel(); resolveMafiaVotes(); startDoctorPhase(); return; }
                broadcastActionBar(Component.text("마피아 행동: ", NamedTextColor.RED)
                    .append(Component.text(remaining + "초", NamedTextColor.WHITE)));
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        currentGame.setPhaseTaskId(task.getTaskId());
    }

    public void submitMafiaVote(Player mafia, UUID target) {
        if (currentGame == null || currentGame.getState() != GameState.NIGHT_MAFIA) return;
        if (currentGame.getRole(mafia) != Role.MAFIA) return;
        currentGame.getMafiaVotes().put(mafia.getUniqueId(), target); // may be PASS_UUID
        if (target.equals(MafiaGame.PASS_UUID)) {
            mafia.sendMessage(Component.text("행동을 건너뛰었습니다.", NamedTextColor.GRAY));
        } else {
            mafia.sendMessage(Component.text("타겟을 선택했습니다.", NamedTextColor.RED));
        }
        mafia.closeInventory();

        if (currentGame.getMafiaVotes().size() >= currentGame.getAllAliveMafiaUUIDs().size()) {
            cancelPhaseTask();
            resolveMafiaVotes();
            startDoctorPhase();
        }
    }

    private void resolveMafiaVotes() {
        Map<UUID, UUID> votes = currentGame.getMafiaVotes();
        if (votes.isEmpty()) {
            currentGame.setMafiaKillTarget(null);
            return;
        }
        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID target : votes.values()) {
            if (!target.equals(MafiaGame.PASS_UUID)) counts.merge(target, 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            currentGame.setMafiaKillTarget(null);
            return;
        }
        int max = Collections.max(counts.values());
        List<UUID> top = counts.entrySet().stream().filter(e -> e.getValue() == max).map(Map.Entry::getKey).toList();
        UUID chosen = top.get(new Random().nextInt(top.size()));
        currentGame.setMafiaKillTarget(chosen);
    }

    private void startDoctorPhase() {
        if (currentGame == null) return;
        currentGame.setState(GameState.NIGHT_DOCTOR);

        for (Player mafia : currentGame.getMafiaPlayers()) {
            if (mafia.getOpenInventory().getTopInventory().getHolder() instanceof NightActionGUI.Holder) {
                mafia.closeInventory();
            }
        }

        Optional<Player> doctorOpt = currentGame.getAlivePlayers().stream()
                .filter(p -> currentGame.getRole(p) == Role.DOCTOR)
                .findFirst();

        if (doctorOpt.isEmpty()) {
            // Check for bot doctor in test mode
            if (currentGame.isTestMode()) {
                UUID botDoctor = currentGame.getAliveBotUUIDs().stream()
                        .filter(u -> currentGame.getRole(u) == Role.DOCTOR)
                        .findFirst().orElse(null);
                if (botDoctor != null) {
                    cancelPhaseTask();
                    BukkitTask task = new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (currentGame == null) return;
                            List<UUID> targets = currentGame.getAllAliveUUIDs();
                            if (!targets.isEmpty()) {
                                currentGame.setDoctorSaveTarget(targets.get(new Random().nextInt(targets.size())));
                            }
                            startPolicePhase();
                        }
                    }.runTaskLater(plugin, 40L);
                    currentGame.setPhaseTaskId(task.getTaskId());
                    return;
                }
            }
            startPolicePhase();
            return;
        }

        Player doctor = doctorOpt.get();
        doctor.sendMessage(Component.text("=== 의사 행동 ===", NamedTextColor.GREEN, TextDecoration.BOLD));
        doctor.sendMessage(Component.text("보호할 플레이어를 선택하세요. (60초)", NamedTextColor.GREEN));
        NightActionGUI.open(doctor, currentGame, Role.DOCTOR);

        cancelPhaseTask();
        BukkitTask task = new BukkitRunnable() {
            int remaining = 60;
            @Override
            public void run() {
                if (currentGame == null || currentGame.getState() != GameState.NIGHT_DOCTOR) { cancel(); return; }
                if (remaining <= 0) { cancel(); doctor.closeInventory(); startPolicePhase(); return; }
                broadcastActionBar(Component.text("의사 행동: ", NamedTextColor.AQUA)
                    .append(Component.text(remaining + "초", NamedTextColor.WHITE)));
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        currentGame.setPhaseTaskId(task.getTaskId());
    }

    public void submitDoctorSave(Player doctor, UUID target) {
        if (currentGame == null || currentGame.getState() != GameState.NIGHT_DOCTOR) return;
        if (currentGame.getRole(doctor) != Role.DOCTOR) return;
        if (target.equals(MafiaGame.PASS_UUID)) {
            doctor.sendMessage(Component.text("행동을 건너뛰었습니다.", NamedTextColor.GRAY));
        } else {
            currentGame.setDoctorSaveTarget(target);
            doctor.sendMessage(Component.text("보호 대상을 선택했습니다.", NamedTextColor.GREEN));
        }
        doctor.closeInventory();
        cancelPhaseTask();
        startPolicePhase();
    }

    private void startPolicePhase() {
        if (currentGame == null) return;
        currentGame.setState(GameState.NIGHT_POLICE);

        Optional<Player> policeOpt = currentGame.getAlivePlayers().stream()
                .filter(p -> currentGame.getRole(p) == Role.POLICE)
                .findFirst();

        if (policeOpt.isEmpty()) {
            // Check for bot police in test mode
            if (currentGame.isTestMode()) {
                UUID botPolice = currentGame.getAliveBotUUIDs().stream()
                        .filter(u -> currentGame.getRole(u) == Role.POLICE)
                        .findFirst().orElse(null);
                if (botPolice != null) {
                    cancelPhaseTask();
                    BukkitTask task = new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (currentGame == null) return;
                            List<UUID> targets = currentGame.getAllAliveUUIDs().stream()
                                    .filter(u -> !u.equals(botPolice))
                                    .collect(Collectors.toList());
                            if (!targets.isEmpty()) {
                                currentGame.setPoliceCheckTarget(targets.get(new Random().nextInt(targets.size())));
                            }
                            resolveNight();
                        }
                    }.runTaskLater(plugin, 40L);
                    currentGame.setPhaseTaskId(task.getTaskId());
                    return;
                }
            }
            resolveNight();
            return;
        }

        Player police = policeOpt.get();
        police.sendMessage(Component.text("=== 경찰 행동 ===", NamedTextColor.BLUE, TextDecoration.BOLD));
        police.sendMessage(Component.text("조사할 플레이어를 선택하세요. (60초)", NamedTextColor.BLUE));
        NightActionGUI.open(police, currentGame, Role.POLICE);

        cancelPhaseTask();
        BukkitTask task = new BukkitRunnable() {
            int remaining = 60;
            @Override
            public void run() {
                if (currentGame == null || currentGame.getState() != GameState.NIGHT_POLICE) { cancel(); return; }
                if (remaining <= 0) { cancel(); police.closeInventory(); resolveNight(); return; }
                broadcastActionBar(Component.text("경찰 조사: ", NamedTextColor.BLUE)
                    .append(Component.text(remaining + "초", NamedTextColor.WHITE)));
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        currentGame.setPhaseTaskId(task.getTaskId());
    }

    public void submitPoliceCheck(Player police, UUID target) {
        if (currentGame == null || currentGame.getState() != GameState.NIGHT_POLICE) return;
        if (currentGame.getRole(police) != Role.POLICE) return;
        if (target.equals(MafiaGame.PASS_UUID)) {
            police.sendMessage(Component.text("행동을 건너뛰었습니다.", NamedTextColor.GRAY));
        } else {
            currentGame.setPoliceCheckTarget(target);
            police.sendMessage(Component.text("조사 대상을 선택했습니다.", NamedTextColor.BLUE));
        }
        police.closeInventory();
        cancelPhaseTask();
        resolveNight();
    }

    // ---- Night Resolution ----

    private void resolveNight() {
        if (currentGame == null) return;
        currentGame.setState(GameState.NIGHT_RESOLVE);

        currentGame.getGameWorld().setTime(6000);

        UUID killTarget = currentGame.getMafiaKillTarget();
        UUID saveTarget = currentGame.getDoctorSaveTarget();
        UUID checkTarget = currentGame.getPoliceCheckTarget();

        // Police result (works for real player police checking real player or bot targets)
        if (checkTarget != null) {
            Optional<Player> policeOpt = currentGame.getAlivePlayers().stream()
                    .filter(p -> currentGame.getRole(p) == Role.POLICE).findFirst();
            policeOpt.ifPresent(police -> {
                String targetName = currentGame.getParticipantName(checkTarget);
                Role targetRole = currentGame.getRole(checkTarget);
                police.sendMessage(Component.text("[경찰 조사 결과] ", NamedTextColor.BLUE)
                        .append(Component.text(targetName + "님은 ", NamedTextColor.WHITE))
                        .append(Component.text(targetRole.getDisplayName(), targetRole.getColor(), TextDecoration.BOLD))
                        .append(Component.text(" 입니다.", NamedTextColor.WHITE)));
                Location entrance = currentGame.getHouseEntrances().get(checkTarget);
                if (entrance != null) ParticleUtil.spawnFootprints(entrance);
            });
        }

        // Mafia kill
        if (killTarget != null) {
            boolean saved = killTarget.equals(saveTarget);
            if (saved) {
                broadcast(Component.text("=== 아침이 밝았습니다 ===", NamedTextColor.YELLOW, TextDecoration.BOLD));
                broadcast(Component.text("밤 사이 아무 일도 없었습니다. (의사가 누군가를 살렸습니다)", NamedTextColor.GREEN));
                Location entrance = currentGame.getHouseEntrances().get(killTarget);
                if (entrance != null) ParticleUtil.spawnFootprints(entrance);
            } else {
                broadcast(Component.text("=== 아침이 밝았습니다 ===", NamedTextColor.YELLOW, TextDecoration.BOLD));
                String victimName = currentGame.getParticipantName(killTarget);
                broadcast(Component.text(victimName + "님이 밤 사이 처치되었습니다!", NamedTextColor.RED));
                Location entrance = currentGame.getHouseEntrances().get(killTarget);
                if (entrance != null) ParticleUtil.spawnFootprints(entrance);

                Optional<Player> victimOpt = currentGame.getAlivePlayer(killTarget);
                if (victimOpt.isPresent()) {
                    killPlayer(victimOpt.get(), true);
                } else if (currentGame.isTestMode() && currentGame.isBotAlive(killTarget)) {
                    currentGame.killBot(killTarget);
                }
            }
        } else {
            broadcast(Component.text("=== 아침이 밝았습니다 ===", NamedTextColor.YELLOW, TextDecoration.BOLD));
            broadcast(Component.text("밤 사이 아무 일도 없었습니다.", NamedTextColor.GREEN));
        }

        if (!checkWinConditions()) {
            Bukkit.getScheduler().runTaskLater(plugin, this::startDay, 60L);
        }
    }

    // ---- Player Elimination ----

    public void killPlayer(Player player, boolean mafiaKill) {
        currentGame.getAlivePlayers().remove(player);
        currentGame.getDeadPlayers().add(player);
        player.setGameMode(GameMode.SPECTATOR);
        if (mafiaKill) {
            player.sendMessage(Component.text("당신은 마피아에게 처치되었습니다. 관전 모드로 전환됩니다.", NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("당신은 처형되었습니다. 관전 모드로 전환됩니다.", NamedTextColor.RED));
        }
    }

    // ---- Win Conditions ----

    private boolean checkWinConditions() {
        if (currentGame == null) return false;

        long mafiaCount = currentGame.countAliveByRole(Role.MAFIA);
        long nonMafiaCount = currentGame.getTotalAliveCount() - mafiaCount;

        if (mafiaCount == 0) {
            broadcast(Component.text("=== 시민팀 승리! 모든 마피아가 처치되었습니다! ===", NamedTextColor.AQUA, TextDecoration.BOLD));
            endGame(Role.CIVILIAN, null);
            return true;
        }
        if (mafiaCount >= nonMafiaCount) {
            broadcast(Component.text("=== 마피아 승리! 마피아가 시민 수와 같거나 많아졌습니다! ===", NamedTextColor.RED, TextDecoration.BOLD));
            endGame(Role.MAFIA, null);
            return true;
        }
        return false;
    }

    // ---- Game End ----

    public void endGame(Role winner, Player jokerWinner) {
        if (currentGame == null) return;
        cancelPhaseTask();
        currentGame.setState(GameState.ENDED);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(Component.text("게임 종료!", NamedTextColor.GOLD));
        }
        broadcast(Component.text("=== 역할 공개 ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        List<Player> allPlayers = new ArrayList<>();
        allPlayers.addAll(currentGame.getAlivePlayers());
        allPlayers.addAll(currentGame.getDeadPlayers());
        for (Player p : allPlayers) {
            Role role = currentGame.getRole(p);
            broadcast(Component.text(p.getName() + ": ", NamedTextColor.WHITE)
                    .append(Component.text(role.getDisplayName(), role.getColor())));
        }
        // Reveal bot roles
        if (currentGame.isTestMode()) {
            Set<UUID> allBotUUIDs = new LinkedHashSet<>();
            allBotUUIDs.addAll(currentGame.getAliveBotUUIDs());
            allBotUUIDs.addAll(currentGame.getDeadBotUUIDs());
            for (UUID botUUID : allBotUUIDs) {
                Role role = currentGame.getRole(botUUID);
                broadcast(Component.text(currentGame.getBotName(botUUID) + ": ", NamedTextColor.WHITE)
                        .append(Component.text(role.getDisplayName(), role.getColor())));
            }
        }

        World mainWorld = Bukkit.getWorlds().get(0);
        for (Player p : allPlayers) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(mainWorld.getSpawnLocation());
        }

        World gameWorld = currentGame.getGameWorld();
        currentGame = null;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            worldManager.deleteWorld(gameWorld);
            broadcast(Component.text("게임 월드가 삭제되었습니다.", NamedTextColor.GRAY));
        }, 60L);
    }

    public void handlePlayerQuit(Player player) {
        if (currentGame == null) return;
        if (!currentGame.isAlive(player.getUniqueId())) return;
        currentGame.getAlivePlayers().remove(player);
        broadcast(Component.text(player.getName() + "님이 게임에서 나갔습니다.", NamedTextColor.GRAY));
        updateScoreboard();
        checkWinConditions();
    }

    public void forceEnd() {
        if (currentGame != null) {
            endGame(null, null);
        }
    }

    // ---- Helpers ----

    private void cancelPhaseTask() {
        if (currentGame != null && currentGame.getPhaseTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(currentGame.getPhaseTaskId());
            currentGame.setPhaseTaskId(-1);
        }
    }

    /** Prevents hunger and grants permanent regeneration for alive players. */
    private void applyGameBuffs(Player p) {
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                Integer.MAX_VALUE / 2, 0, true, false, false));
    }

    private void broadcast(Component message) {
        if (currentGame == null) {
            Bukkit.broadcast(message);
            return;
        }
        List<Player> all = new ArrayList<>(currentGame.getAlivePlayers());
        all.addAll(currentGame.getDeadPlayers());
        for (Player p : all) p.sendMessage(message);
    }

    public MafiaGame getCurrentGame() { return currentGame; }
}
