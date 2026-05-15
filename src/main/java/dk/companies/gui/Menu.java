package dk.companies.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public abstract class Menu {

    protected final Player viewer;
    protected Inventory inv;

    protected Menu(Player viewer) { this.viewer = viewer; }

    public Inventory getInventory() { return inv; }

    /** Build & open. */
    public abstract void open();

    /** Called by GuiListener when this menu's inventory receives a click. */
    public abstract void handleClick(InventoryClickEvent e);

    /** Default: cancel raw move-from-player-inventory clicks. Override if your menu accepts items. */
    public boolean cancelByDefault() { return true; }

    public void handleClose(InventoryCloseEvent e) {}

    protected static boolean leftOnly(ClickType t) { return t == ClickType.LEFT; }

    protected static int slotFor(int row, int col) { return row * 9 + col; }

    protected static ItemStack airIfNull(ItemStack s) {
        return s == null ? new ItemStack(org.bukkit.Material.AIR) : s;
    }
}
