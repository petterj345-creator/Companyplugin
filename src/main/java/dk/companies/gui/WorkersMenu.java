package dk.companies.gui;

import dk.companies.Companies;
import dk.companies.model.Company;
import dk.companies.model.Role;
import dk.companies.util.FormatUtil;
import dk.companies.util.ItemBuilder;
import dk.companies.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class WorkersMenu extends Menu {

    private final Companies plugin;
    private final Company company;
    private final List<UUID> bySlot = new ArrayList<>();

    public WorkersMenu(Companies plugin, Player viewer, Company company) {
        super(viewer);
        this.plugin = plugin;
        this.company = company;
    }

    @Override public void open() {
        inv = Bukkit.createInventory(null, 54, MessageUtil.color("&8Workers: " + company.getName()));
        for (int i = 0; i < 9; i++)
            inv.setItem(i, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        inv.setItem(49, ItemBuilder.of(Material.ARROW).name("&7Back").build());

        // Sort by role first, then by contribution
        List<UUID> members = new ArrayList<>(company.getMembers().keySet());
        members.sort((a, b) -> {
            Role ra = company.getRole(a);
            Role rb = company.getRole(b);
            int byRole = Integer.compare(ra.ordinal(), rb.ordinal());
            if (byRole != 0) return byRole;
            double ca = company.getContributions().getOrDefault(a, 0.0);
            double cb = company.getContributions().getOrDefault(b, 0.0);
            return Double.compare(cb, ca);
        });

        int slot = 9;
        for (UUID id : members) {
            if (slot >= 45) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            Role role = company.getRole(id);
            double contrib = company.getContributions().getOrDefault(id, 0.0);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(op);
                head.setItemMeta(sm);
            }
            head = ItemBuilder.of(Material.PLAYER_HEAD).name("&f" + (op.getName() == null ? id.toString() : op.getName()))
                    .lore("&7Role: &b" + role.name(),
                            "&7Contributed: &a" + FormatUtil.money(contrib),
                            "",
                            viewer.hasPermission("companies.use") && canManage()
                                    ? "&8Left-click: pay salary"
                                    : "",
                            canManage() && role != Role.OWNER ? "&8Right-click: promote/demote" : "",
                            canManage() && role != Role.OWNER ? "&8Shift-click: fire" : "")
                    .build();
            // we lose the skull texture from ItemBuilder; re-apply
            if (head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(op);
                head.setItemMeta(sm);
            }
            inv.setItem(slot, head);
            bySlot.add(id);
            slot++;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.openInventory(inv);
            GuiListener.register(viewer, this);
        });
    }

    private boolean canManage() {
        Role r = company.getRole(viewer.getUniqueId());
        return r != null && r.canManageWorkers();
    }

    @Override public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw == 49) { new MainMenu(plugin, viewer, company).open(); return; }
        if (raw < 9 || raw >= 45) return;
        int idx = raw - 9;
        if (idx >= bySlot.size()) return;
        UUID target = bySlot.get(idx);
        Role role = company.getRole(target);
        if (role == null) return;

        if (e.getClick().isShiftClick() && canManage() && role != Role.OWNER) {
            plugin.companies().fire(company, target);
            MessageUtil.send(viewer, "&cFired " + Bukkit.getOfflinePlayer(target).getName());
            open();
            return;
        }
        if (e.isRightClick() && canManage() && role != Role.OWNER) {
            if (role == Role.WORKER) plugin.companies().promote(company, target);
            else if (role == Role.MANAGER) plugin.companies().demote(company, target);
            open();
            return;
        }
        if (e.isLeftClick() && canManage()) {
            // Pay salary prompt
            viewer.closeInventory();
            MessageUtil.send(viewer, "&eEnter salary amount for &f" + Bukkit.getOfflinePlayer(target).getName() + " &e(or 'cancel'):");
            ChatInput.prompt(viewer, text -> {
                if (text.equalsIgnoreCase("cancel")) { open(); return; }
                try {
                    double v = Double.parseDouble(text);
                    var r = plugin.companies().paySalary(viewer, company, Bukkit.getOfflinePlayer(target), v);
                    switch (r) {
                        case OK -> MessageUtil.send(viewer, "&aPaid &6" + FormatUtil.money(v) + " &ato "
                                + Bukkit.getOfflinePlayer(target).getName());
                        case NO_PERMISSION -> MessageUtil.send(viewer, "&cNo permission.");
                        case BELOW_ZERO -> MessageUtil.send(viewer, "&cWould drop below minimum balance.");
                        default -> MessageUtil.send(viewer, "&cFailed.");
                    }
                } catch (NumberFormatException ex) {
                    MessageUtil.send(viewer, "&cNot a number.");
                }
                open();
            });
        }
    }
}
