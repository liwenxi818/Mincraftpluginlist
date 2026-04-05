package com.mafia.gui;

import com.mafia.game.MafiaGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

public class MayorElectionGUI {

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

        // +1 slot for the pass button
        int itemCount = candidateUUIDs.size() + 1;
        int size = Math.min(54, (int) Math.ceil(itemCount / 9.0) * 9);
        if (size == 0) size = 9;

        Holder holder = new Holder(voter.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, size, Component.text("시장 선출 투표"));
        holder.setInventory(inv);

        for (int i = 0; i < candidateUUIDs.size() && i < size; i++) {
            holder.getCandidateIds().add(candidateUUIDs.get(i));
            ItemStack item = new ItemStack(Material.YELLOW_WOOL);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(candidateNames.get(i), NamedTextColor.WHITE));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        // Pass button
        int passSlot = candidateUUIDs.size();
        if (passSlot < size) {
            holder.getCandidateIds().add(MafiaGame.PASS_UUID);
            ItemStack passItem = new ItemStack(Material.GRAY_WOOL);
            ItemMeta passMeta = passItem.getItemMeta();
            passMeta.displayName(Component.text("패스 (기권)", NamedTextColor.GRAY, TextDecoration.ITALIC));
            passItem.setItemMeta(passMeta);
            inv.setItem(passSlot, passItem);
        }

        voter.openInventory(inv);
    }
}
