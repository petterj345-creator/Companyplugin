package dk.companies.gui;

import dk.companies.Companies;
import dk.companies.model.Company;
import dk.companies.util.ItemBuilder;
import dk.companies.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ApplicationsMenu extends Menu {

    private final Companies plugin;
    private final Company company;
    private final List<UUID> bySlot = new ArrayList<>();

    public ApplicationsMenu(Companies plugin, Player viewer, Company company) {
        super(viewer);
        this.plugin = plugin;
        this.company = company;
    }

    @Override public void open() {
        inv = Bukkit.createInventory(null, 54, MessageUtil.color("&8Applications: " + company.getName()));
        for (int i = 0; i < 9; i++)
            inv.setItem(i, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        inv.setItem(49, ItemBuilder.of(Material.ARROW).name("&7Back").build());

        int slot = 9;
        for (UUID id : company.getApplicants()) {
            if (slot >= 45) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
                    .name("&f" + (op.getName() == null ? id.toString() : op.getName()))
                    .lore("&7Left-click: &aaccept", "&7Right-click: &cdeny")
                    .build();
            if (head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(op);
                head.setItemMeta(sm);
            }
            inv.setItem(slot++, head);
            bySlot.add(id);
        }
        if (bySlot.isEmpty()) {
            inv.setItem(22, ItemBuilder.of(Material.BARRIER).name("&7No applicants").build());
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.openInventory(inv);
            GuiListener.register(viewer, this);
        });
    }

    @Override public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw == 49) { new MainMenu(plugin, viewer, company).open(); return; }
        if (raw < 9 || raw >= 45) return;
        int idx = raw - 9;
        if (idx >= bySlot.size()) return;
        UUID target = bySlot.get(idx);

        if (e.isLeftClick()) {
            boolean ok = plugin.companies().hire(company, target);
            if (ok) {
                Player online = Bukkit.getPlayer(target);
                if (online != null)
                    MessageUtil.send(online, "&aYou have been hired by &6" + company.getName());
                MessageUtil.send(viewer, "&aAccepted &f" + Bukkit.getOfflinePlayer(target).getName());
            } else {
                MessageUtil.send(viewer, "&cCould not hire (workers full?).");
            }
        } else if (e.isRightClick()) {
            plugin.companies().cancelApplication(target, company);
            MessageUtil.send(viewer, "&7Denied application.");
        }
        open();
    }
}
