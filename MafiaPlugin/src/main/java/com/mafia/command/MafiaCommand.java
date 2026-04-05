package com.mafia.command;

import com.mafia.MafiaPlugin;
import com.mafia.game.MafiaGame;
import com.mafia.manager.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MafiaCommand implements CommandExecutor, TabCompleter {

    private final MafiaPlugin plugin;
    private final GameManager gm;

    public MafiaCommand(MafiaPlugin plugin) {
        this.plugin = plugin;
        this.gm = plugin.getGameManager();
        plugin.getCommand("mafia").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "join" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("플레이어만 사용 가능합니다.");
                    return true;
                }
                gm.joinLobby(player);
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) return true;
                if (!gm.leaveLobby(player)) {
                    player.sendMessage(Component.text("대기 중이 아닙니다.", NamedTextColor.RED));
                }
            }
            case "start" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("플레이어만 사용 가능합니다.");
                    return true;
                }
                gm.startGame(player);
            }
            case "test" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("플레이어만 사용 가능합니다.");
                    return true;
                }
                if (args.length >= 2) {
                    try {
                        int botCount = Integer.parseInt(args[1]);
                        if (botCount < 0) {
                            player.sendMessage(Component.text("봇 수는 0 이상이어야 합니다.", NamedTextColor.RED));
                            return true;
                        }
                        gm.startTestGame(player, botCount);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("봇 수는 숫자여야 합니다. 예: /mafia test 3", NamedTextColor.RED));
                    }
                } else {
                    gm.startTestGame(player, -1);
                }
            }
            case "end" -> {
                if (!sender.hasPermission("mafia.admin")) {
                    sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
                    return true;
                }
                gm.forceEnd();
                sender.sendMessage(Component.text("게임을 강제 종료했습니다.", NamedTextColor.YELLOW));
            }
            case "status" -> {
                MafiaGame game = gm.getCurrentGame();
                if (game == null) {
                    sender.sendMessage(Component.text("진행 중인 게임이 없습니다.", NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("대기 중: " + gm.getLobby().stream()
                            .map(p -> p.getName()).collect(Collectors.joining(", ")), NamedTextColor.AQUA));
                } else {
                    sender.sendMessage(Component.text("게임 상태: " + game.getState().name()
                            + (game.isTestMode() ? " [테스트]" : ""), NamedTextColor.AQUA));
                    sender.sendMessage(Component.text("생존: " + game.getAlivePlayers().size() + "명"
                            + (game.isTestMode() ? " + " + game.getAliveBotUUIDs().size() + "봇" : "")
                            + " / 사망: " + game.getDeadPlayers().size() + "명"
                            + (game.isTestMode() ? " + " + game.getDeadBotUUIDs().size() + "봇" : ""), NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("낮 수: " + game.getDayCount(), NamedTextColor.YELLOW));
                }
            }
            case "vote" -> {
                if (!sender.hasPermission("mafia.admin")) {
                    sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
                    return true;
                }
                MafiaGame game = gm.getCurrentGame();
                if (game == null) {
                    sender.sendMessage(Component.text("진행 중인 게임이 없습니다.", NamedTextColor.RED));
                    return true;
                }
                gm.startNomination();
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== 마피아 게임 명령어 ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/mafia join - 대기실 참가", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/mafia leave - 대기실 나가기", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/mafia start - 게임 시작 (4명 이상)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/mafia test [봇 수] - 테스트 모드 시작 (봇 수 미지정 시 최소 인원 자동 채움)", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/mafia status - 현재 상태", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/mafia end - 게임 강제 종료 (관리자)", NamedTextColor.RED));
        sender.sendMessage(Component.text("/mafia vote - 투표 즉시 시작 (관리자)", NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("join", "leave", "start", "test", "status", "end", "vote");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return Arrays.asList("1", "2", "3", "4", "5", "6", "7");
        }
        return List.of();
    }
}
