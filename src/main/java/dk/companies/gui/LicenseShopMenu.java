package dk.companies.gui;

import dk.companies.Companies;
import dk.companies.licenses.LicenseManager;
import dk.companies.model.Company;
import dk.companies.model.License;
import dk.companies.model.LicenseType;
import dk.companies.util.FormatUtil;
import dk.companies.util.ItemBuilder;
import dk.companies.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

public class LicenseShopMenu extends Menu {

    private final Companies plugin;
    private final Company company;
    private final List<LicenseType> typeBySlot = new ArrayList<>();
    private final List<License> licBySlot = new ArrayList<>();

    public LicenseShopMenu(Companies plugin, Player viewer, Company company) {
        super(viewer);
        this.plugin = plugin;
        this.company = company;
    }

    @Override public void open() {
        inv = Bukkit.createInventory(null, 54, MessageUtil.color("&8Licenses: " + company.getName()));
        border();

        // Header
        inv.setItem(4, ItemBuilder.of(Material.PAPER)
                .name("&bLicenses")
                .lore("&7Top row: licenses you can buy.",
                        "&7Bottom rows: licenses installed in your company.")
                .build());

        // Available license types (slots 9..17)
        int slot = 9;
        for (LicenseType lt : plugin.companies().data().getLicenseTypes()) {
            if (slot >= 18) break;
            typeBySlot.add(lt);
            String jobsSummary = lt.getJobs().isEmpty() ? "&cno jobs configured" : "&f" + lt.getJobs().size() + " job type(s)";
            inv.setItem(slot++, ItemBuilder.of(lt.getIconMaterial())
                    .name("&e" + lt.getName())
                    .lore("&7Rotation pool: " + jobsSummary,
                            "&7Lifetime: &f" + FormatUtil.duration(lt.getValidDurationMillis()),
                            "&7Price: &6" + FormatUtil.money(lt.getPrice()),
                            "",
                            "&8Click to buy with company funds.")
                    .glow(true)
                    .build());
        }

        // Installed licenses (slots 27..44)
        int s2 = 27;
        long now = System.currentTimeMillis();
        for (License l : company.getLicenses()) {
            if (s2 >= 45) break;
            LicenseType lt = plugin.companies().data().getLicenseType(l.getTypeId());
            if (lt == null) continue;
            licBySlot.add(l);
            long left = l.getExpiresAt() - now;
            inv.setItem(s2++, ItemBuilder.of(left > 0 ? lt.getIconMaterial() : Material.MAP)
                    .name((left > 0 ? "&a" : "&c") + lt.getName())
                    .lore("&7Valid for: &f" + FormatUtil.duration(left),
                            left > 0 ? "&aActive" : "&cExpired")
                    .build());
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.openInventory(inv);
            GuiListener.register(viewer, this);
        });
    }

    private void border() {
        for (int i = 0; i < 9; i++)
            inv.setItem(i, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        inv.setItem(18, ItemBuilder.of(Material.PURPLE_STAINED_GLASS_PANE).name("&7Installed").build());
        inv.setItem(26, ItemBuilder.of(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build());
        for (int i = 45; i < 54; i++)
            inv.setItem(i, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        // back
        inv.setItem(49, ItemBuilder.of(Material.ARROW).name("&7Back").build());
    }

    @Override public void handleClick(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 49) {
            new MainMenu(plugin, viewer, company).open();
            return;
        }
        if (slot >= 9 && slot < 18) {
            int idx = slot - 9;
            if (idx >= typeBySlot.size()) return;
            LicenseType lt = typeBySlot.get(idx);
            var r = plugin.licenses().buy(company, viewer.getUniqueId(), lt.getId());
            switch (r) {
                case OK -> MessageUtil.send(viewer, "&aPurchased license: &e" + lt.getName());
                case NOT_ENOUGH_FUNDS -> MessageUtil.send(viewer, "&cCompany cannot afford that.");
                case NO_PERMISSION -> MessageUtil.send(viewer, "&cNo permission.");
                case NO_TYPE -> MessageUtil.send(viewer, "&cThat license no longer exists.");
                case NO_JOBS_DEFINED -> MessageUtil.send(viewer, "&cThat license has no jobs configured yet.");
            }
            open();
        }
    }
}
