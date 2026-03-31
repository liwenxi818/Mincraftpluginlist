package com.mafia.gui;

import com.mafia.game.MafiaGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NominationGUI {

    public static class Holder implements InventoryHolder {
        private final UUID voterId;
        private final List<UUID> candidateIds = new ArrayList<>();
        private Inventory inventory;

        public Holder(UUID voterId) { this.voterId = voterId; }
        public UUID getVoterId() { return voterId; }
        public List<UUID> getCandidateIds() { return candidateIds; }

        @Override
        public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inv) { this.inventory = inv; }
    }

    public static void open(Player voter, MafiaGame game) {
        // Build candidate list: real players (except self) + bots
        List<UUID> candidateUUIDs = new ArrayList<>();
        List<String> candidateNames = new ArrayList<>();

        for (Player p : game.getAlivePlayers()) {
            if (!p.getUniqueId().equals(voter.getUniqueId())) {
                candidateUUIDs.add(p.getUniqueId());
                candidateNames.add(p.getName());
            }
        }
        if (game.isTestMode()) {
            for (UUID botUUID : game.getAliveBotUUIDs()) {
                candidateUUIDs.add(botUUID);
                candidateNames.add(game.getBotName(botUUID));
            }
        }

        int size = candidateUUIDs.isEmpty() ? 9 : Math.min(54, (int) Math.ceil(candidateUUIDs.size() / 9.0) * 9);

        Holder holder = new Holder(voter.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, size, Component.text("심판대에 올릴 플레이어 선택"));
        holder.setInventory(inv);

        for (int i = 0; i < candidateUUIDs.size() && i < size; i++) {
            holder.getCandidateIds().add(candidateUUIDs.get(i));
            ItemStack item = new ItemStack(Material.ORANGE_WOOL);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(candidateNames.get(i), NamedTextColor.WHITE));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        voter.openInventory(inv);
    }
}
