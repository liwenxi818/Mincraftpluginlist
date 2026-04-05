package com.mafia.listener;

import com.mafia.MafiaPlugin;
import com.mafia.game.GameState;
import com.mafia.game.MafiaGame;
import com.mafia.game.Role;
import com.mafia.gui.ExecutionVoteGUI;
import com.mafia.gui.MayorElectionGUI;
import com.mafia.gui.NightActionGUI;
import com.mafia.gui.NominationGUI;
import com.mafia.manager.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;

public class MafiaListener implements Listener {

    private final MafiaPlugin plugin;
    private final GameManager gm;

    public MafiaListener(MafiaPlugin plugin) {
        this.plugin = plugin;
        this.gm = plugin.getGameManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var topHolder = event.getView().getTopInventory().getHolder();
        if (topHolder == null) return;
        if (!event.getView().getTopInventory().equals(event.getClickedInventory())) {
            if (topHolder instanceof NightActionGUI.Holder || topHolder instanceof NominationGUI.Holder
                    || topHolder instanceof ExecutionVoteGUI.Holder || topHolder instanceof MayorElectionGUI.Holder) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0) return;

        MafiaGame game = gm.getCurrentGame();
        if (game == null) return;

        if (topHolder instanceof NightActionGUI.Holder holder) {
            handleNightAction(player, holder, slot, game);
        } else if (topHolder instanceof NominationGUI.Holder holder) {
            handleNomination(player, holder, slot, game);
        } else if (topHolder instanceof ExecutionVoteGUI.Holder holder) {
            handleExecutionVote(player, holder, slot, game);
        } else if (topHolder instanceof MayorElectionGUI.Holder holder) {
            handleMayorElection(player, holder, slot, game);
        }
    }

    private void handleNightAction(Player player, NightActionGUI.Holder holder, int slot, MafiaGame game) {
        List<UUID> targets = holder.getTargetIds();
        if (slot >= targets.size()) return;
        UUID targetId = targets.get(slot);

        Role actorRole = holder.getActorRole();
        switch (actorRole) {
            case MAFIA -> gm.submitMafiaVote(player, targetId);
            case DOCTOR -> gm.submitDoctorSave(player, targetId);
            case POLICE -> gm.submitPoliceCheck(player, targetId);
            default -> {}
        }
    }

    private void handleNomination(Player player, NominationGUI.Holder holder, int slot, MafiaGame game) {
        List<UUID> candidates = holder.getCandidateIds();
        if (slot >= candidates.size()) return;
        UUID nominee = candidates.get(slot);

        if (game.getState() != GameState.DAY_NOMINATION) return;

        if (nominee.equals(MafiaGame.PASS_UUID)) {
            player.sendMessage(Component.text("투표를 기권했습니다.", NamedTextColor.GRAY));
            player.closeInventory();
            return;
        }

        game.getNominationVotes().put(player.getUniqueId(), nominee);
        String nomineeName = game.getParticipantName(nominee);
        player.sendMessage(Component.text(nomineeName + "님을 지목했습니다.", NamedTextColor.YELLOW));
        player.closeInventory();
    }

    private void handleMayorElection(Player player, MayorElectionGUI.Holder holder, int slot, MafiaGame game) {
        List<UUID> candidates = holder.getCandidateIds();
        if (slot >= candidates.size()) return;
        UUID nominee = candidates.get(slot);

        if (game.getState() != GameState.DAY_MAYOR_ELECTION) return;

        if (nominee.equals(MafiaGame.PASS_UUID)) {
            player.sendMessage(Component.text("시장 투표를 기권했습니다.", NamedTextColor.GRAY));
            player.closeInventory();
            return;
        }

        game.getMayorVotes().put(player.getUniqueId(), nominee);
        String nomineeName = game.getParticipantName(nominee);
        player.sendMessage(Component.text(nomineeName + "님을 시장 후보로 선택했습니다.", NamedTextColor.YELLOW));
        player.closeInventory();
    }

    private void handleExecutionVote(Player player, ExecutionVoteGUI.Holder holder, int slot, MafiaGame game) {
        if (game.getState() != GameState.DAY_VOTE) return;

        if (slot == 4) {
            player.sendMessage(Component.text("투표를 기권했습니다.", NamedTextColor.GRAY));
            player.closeInventory();
            return;
        }

        Boolean vote = null;
        if (slot == 2) vote = true;
        if (slot == 6) vote = false;
        if (vote == null) return;

        game.getExecutionVotes().put(player.getUniqueId(), vote);
        String msg = vote ? "처형에 투표했습니다." : "무죄에 투표했습니다.";
        player.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
        player.closeInventory();

        // Trigger early resolution if all eligible voters have voted (real players + bots)
        int voterCount = game.getTotalAliveCount() - 1; // exclude trial player
        if (game.getExecutionVotes().size() >= voterCount) {
            gm.resolveExecutionVote(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (gm.getCurrentGame() != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (gm.getCurrentGame() != null) {
            gm.handlePlayerQuit(player);
        }
        gm.getLobby().remove(player);
    }
}
