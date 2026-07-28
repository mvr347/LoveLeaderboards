package dev.lovelace.loveleaderboards.listeners;

import dev.lovelace.loveleaderboards.gui.BaseGui;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BaseGui gui) {
            event.setCancelled(true);
            if (event.getClickedInventory().equals(event.getInventory())) {
                gui.handleClick(event);
            }
        }
    }
}
