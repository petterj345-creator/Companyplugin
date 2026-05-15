package dk.companies.gui;

import dk.companies.Companies;
import dk.companies.model.Company;
import dk.companies.util.FormatUtil;
import dk.companies.util.ItemBuilder;
import dk.companies.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

public class JobBoardMenu extends Menu {

    private final Companies plugin;
    private final List<Company> bySlot = new ArrayList<>();

    public JobBoardMenu(Companies plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    @Override public void open() {
        inv = Bukkit.createInventory(null, 54, MessageUtil.color("&8Job Board"));
        for (int i = 0; i < 9; i++)
            inv.setItem(i, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        inv.setItem(4, ItemBuilder.of(Material.OAK_SIGN).name("&eCompanies hiring")
                .lore("&7Click to apply.").build());

        int slot = 9;
        for (Company c : plugin.companies().data().getCompanies()) {
            if (!c.isHiring()) continue;
            if (c.isMember(viewer.getUniqueId())) continue;
            if (slot >= 45) break;
            bySlot.add(c);
            inv.setItem(slot++, ItemBuilder.of(Material.LIME_BANNER)
                    .name("&a" + c.getName())
                    .lore("&7Balance: &a" + FormatUtil.money(c.getBalance()),
                            "&7Members: &f" + c.getMembers().size(),
                            "&7Active licenses: &f" + c.getLicenses().size(),
                            "",
                            c.getApplicants().contains(viewer.getUniqueId())
                                    ? "&8You have already applied."
                                    : "&8Click to apply.")
                    .build());
        }
        if (bySlot.isEmpty()) {
            inv.setItem(22, ItemBuilder.of(Material.BARRIER).name("&7No companies are hiring.").build());
        }

        // Close button
        inv.setItem(49, ItemBuilder.of(Material.ARROW).name("&7Close").build());

        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.openInventory(inv);
            GuiListener.register(viewer, this);
        });
    }

    @Override public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw == 49) { viewer.closeInventory(); return; }
        if (raw < 9 || raw >= 45) return;
        int idx = raw - 9;
        if (idx >= bySlot.size()) return;
        Company c = bySlot.get(idx);
        if (c.getApplicants().contains(viewer.getUniqueId())) {
            MessageUtil.send(viewer, "&cAlready applied.");
            return;
        }
        if (plugin.companies().data().getCompanyOf(viewer.getUniqueId()) != null) {
            MessageUtil.send(viewer, "&cYou're already in a company.");
            return;
        }
        plugin.companies().apply(viewer, c);
        MessageUtil.send(viewer, "&aApplied to &6" + c.getName());
        open();
    }
}
