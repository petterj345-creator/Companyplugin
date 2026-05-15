package dk.companies.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuiListener implements Listener {

    private static final Map<UUID, Menu> OPEN = new HashMap<>();

    public static void register(HumanEntity p, Menu m) { OPEN.put(p.getUniqueId(), m); }

    public static Menu of(HumanEntity p) { return OPEN.get(p.getUniqueId()); }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Menu m = OPEN.get(e.getWhoClicked().getUniqueId());
        if (m == null) return;
        Inventory top = e.getView().getTopInventory();
        if (top != m.getInventory()) return;
        if (m.cancelByDefault()) e.setCancelled(true);
        m.handleClick(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Menu m = OPEN.get(e.getWhoClicked().getUniqueId());
        if (m == null) return;
        if (e.getView().getTopInventory() != m.getInventory()) return;
        for (int slot : e.getRawSlots()) {
            if (slot < m.getInventory().getSize()) {
                if (m.cancelByDefault()) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Menu m = OPEN.remove(e.getPlayer().getUniqueId());
        if (m != null) m.handleClose(e);
    }
}
