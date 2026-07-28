package dev.lovelace.loveleaderboards.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class BaseGui implements InventoryHolder {
    protected Inventory inventory;

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public abstract void handleClick(InventoryClickEvent event);

    public void open(org.bukkit.entity.Player player) {
        if (inventory != null && player != null) {
            player.openInventory(inventory);
        }
    }
}
