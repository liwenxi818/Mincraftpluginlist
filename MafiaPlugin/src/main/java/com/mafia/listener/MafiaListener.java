package com.mafia.listener;

import com.mafia.MafiaPlugin;
import com.mafia.game.GameState;
import com.mafia.game.MafiaGame;
import com.mafia.game.Role;
import com.mafia.gui.ExecutionVoteGUI;
import com.mafia.gui.NightActionGUI;
import com.mafia.gui.NominationGUI;
import com.mafia.manager.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
            if (topHolder instanceof NightActionGUI.Holder || topHolder instanceof NominationGUI.Holder || topHolder instanceof ExecutionVoteGUI.Holder) {
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

        game.getNominationVotes().put(player.getUniqueId(), nominee);
        String nomineeName = game.getParticipantName(nominee);
        player.sendMessage(Component.text(nomineeName + "님을 지목했습니다.", NamedTextColor.YELLOW));
        player.closeInventory();
    }

    private void handleExecutionVote(Player player, ExecutionVoteGUI.Holder holder, int slot, MafiaGame game) {
        if (game.getState() != GameState.DAY_VOTE) return;

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
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        MafiaGame game = gm.getCurrentGame();
        if (game == null) return;
        if (game.isAlive(player.getUniqueId())) {
            game.getAlivePlayers().remove(player);
            if (gm.getCurrentGame() == null) return;
            player.sendMessage(Component.text(player.getName() + "님이 게임에서 나갔습니다.", NamedTextColor.GRAY));
        }
        gm.getLobby().remove(player);
    }
}
