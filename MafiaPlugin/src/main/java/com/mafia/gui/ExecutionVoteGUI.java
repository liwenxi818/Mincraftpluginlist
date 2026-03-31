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

import java.util.UUID;

public class ExecutionVoteGUI {

    public static class Holder implements InventoryHolder {
        private final UUID voterId;
        private final UUID trialPlayerId;
        private Inventory inventory;

        public Holder(UUID voterId, UUID trialPlayerId) {
            this.voterId = voterId;
            this.trialPlayerId = trialPlayerId;
        }

        public UUID getVoterId() { return voterId; }
        public UUID getTrialPlayerId() { return trialPlayerId; }

        @Override
        public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inv) { this.inventory = inv; }
    }

    public static void open(Player voter, Player trialPlayer, MafiaGame game) {
        open(voter, trialPlayer.getUniqueId(), trialPlayer.getName(), game);
    }

    /** Overload for when the trial player is a bot (no Player object available). */
    public static void open(Player voter, UUID trialUUID, String trialName, MafiaGame game) {
        Holder holder = new Holder(voter.getUniqueId(), trialUUID);
        Inventory inv = Bukkit.createInventory(holder, 9, Component.text(trialName + "님을 처형?"));
        holder.setInventory(inv);

        ItemStack executeItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta execMeta = executeItem.getItemMeta();
        execMeta.displayName(Component.text("처형 (찬성)", NamedTextColor.GREEN, TextDecoration.BOLD));
        executeItem.setItemMeta(execMeta);
        inv.setItem(2, executeItem);

        ItemStack spareItem = new ItemStack(Material.RED_WOOL);
        ItemMeta spareMeta = spareItem.getItemMeta();
        spareMeta.displayName(Component.text("무죄 (반대)", NamedTextColor.RED, TextDecoration.BOLD));
        spareItem.setItemMeta(spareMeta);
        inv.setItem(6, spareItem);

        voter.openInventory(inv);
    }
}
