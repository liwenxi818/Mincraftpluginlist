package com.mafia.gui;

import com.mafia.game.MafiaGame;
import com.mafia.game.Role;
import net.kyori.adventure.text.format.TextDecoration;
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

public class NightActionGUI {

    public static class Holder implements InventoryHolder {
        private final UUID actorId;
        private final Role actorRole;
        private final List<UUID> targetIds = new ArrayList<>();
        private Inventory inventory;

        public Holder(UUID actorId, Role actorRole) {
            this.actorId = actorId;
            this.actorRole = actorRole;
        }

        public UUID getActorId() { return actorId; }
        public Role getActorRole() { return actorRole; }
        public List<UUID> getTargetIds() { return targetIds; }

        @Override
        public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inv) { this.inventory = inv; }
    }

    public static void open(Player actor, MafiaGame game, Role actorRole) {
        // Build target list: real players + bots (mafia excludes fellow mafia)
        List<UUID> targetUUIDs = new ArrayList<>();
        List<String> targetNames = new ArrayList<>();

        for (Player p : game.getAlivePlayers()) {
            if (actorRole == Role.MAFIA && game.getRole(p) == Role.MAFIA) continue;
            targetUUIDs.add(p.getUniqueId());
            targetNames.add(p.getName());
        }
        if (game.isTestMode()) {
            for (UUID botUUID : game.getAliveBotUUIDs()) {
                if (actorRole == Role.MAFIA && game.getRole(botUUID) == Role.MAFIA) continue;
                targetUUIDs.add(botUUID);
                targetNames.add(game.getBotName(botUUID));
            }
        }

        String title = switch (actorRole) {
            case MAFIA -> "마피아 - 처치 대상 선택";
            case DOCTOR -> "의사 - 보호 대상 선택";
            case POLICE -> "경찰 - 조사 대상 선택";
            default -> "타겟 선택";
        };

        // +1 slot for the pass button
        int itemCount = targetUUIDs.size() + 1;
        int size = Math.min(54, (int) Math.ceil(itemCount / 9.0) * 9);
        if (size == 0) size = 9;

        Material mat = switch (actorRole) {
            case MAFIA -> Material.RED_WOOL;
            case DOCTOR -> Material.LIME_WOOL;
            case POLICE -> Material.BLUE_WOOL;
            default -> Material.WHITE_WOOL;
        };

        Holder holder = new Holder(actor.getUniqueId(), actorRole);
        Inventory inv = Bukkit.createInventory(holder, size, Component.text(title));
        holder.setInventory(inv);

        for (int i = 0; i < targetUUIDs.size() && i < size; i++) {
            holder.getTargetIds().add(targetUUIDs.get(i));
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(targetNames.get(i), NamedTextColor.WHITE));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        // Pass button
        int passSlot = targetUUIDs.size();
        if (passSlot < size) {
            holder.getTargetIds().add(MafiaGame.PASS_UUID);
            ItemStack passItem = new ItemStack(Material.GRAY_WOOL);
            ItemMeta passMeta = passItem.getItemMeta();
            passMeta.displayName(Component.text("패스 (기권)", NamedTextColor.GRAY, TextDecoration.ITALIC));
            passItem.setItemMeta(passMeta);
            inv.setItem(passSlot, passItem);
        }

        actor.openInventory(inv);
    }
}
