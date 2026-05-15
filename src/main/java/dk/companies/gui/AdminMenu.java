package dk.companies.gui;

import dk.companies.Companies;
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
import java.util.UUID;

public class AdminMenu extends Menu {

    private final Companies plugin;
    private final List<LicenseType> bySlot = new ArrayList<>();

    public AdminMenu(Companies plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    @Override public void open() {
        if (!viewer.hasPermission("companies.admin")) {
            viewer.closeInventory();
            MessageUtil.send(viewer, "&cNo permission.");
            return;
        }
        inv = Bukkit.createInventory(null, 54, MessageUtil.color("&8Admin: License Types"));
        for (int i = 0; i < 9; i++)
            inv.setItem(i, ItemBuilder.of(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build());
        inv.setItem(4, ItemBuilder.of(Material.COMMAND_BLOCK)
                .name("&5Admin Panel")
                .lore("&7Manage license blueprints.").build());

        int slot = 9;
        for (LicenseType lt : plugin.companies().data().getLicenseTypes()) {
            if (slot >= 45) break;
            bySlot.add(lt);
            inv.setItem(slot++, ItemBuilder.of(lt.getIconMaterial())
                    .name("&e" + lt.getName())
                    .lore("&7Icon material: &f" + lt.getIconMaterial().name(),
                            "&7Job templates: &f" + lt.getJobs().size(),
                            "&7License price: &6" + FormatUtil.money(lt.getPrice()),
                            "&7Lifetime: &f" + FormatUtil.duration(lt.getValidDurationMillis()),
                            "",
                            "&8Click to edit",
                            "&8Shift-click to delete")
                    .build());
        }

        inv.setItem(49, ItemBuilder.of(Material.NETHER_STAR)
                .name("&aCreate new license type")
                .lore("&7Opens the builder.")
                .glow(true).build());
        inv.setItem(45, ItemBuilder.of(Material.ARROW).name("&7Back").build());

        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.openInventory(inv);
            GuiListener.register(viewer, this);
        });
    }

    @Override public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw == 45) {
            // back to MainMenu if member of a company, else close
            var c = plugin.companies().data().getCompanyOf(viewer.getUniqueId());
            if (c != null) new MainMenu(plugin, viewer, c).open();
            else viewer.closeInventory();
            return;
        }
        if (raw == 49) {
            new AdminBuilderMenu(plugin, viewer, new AdminBuilderMenu.Draft()).open();
            return;
        }
        if (raw >= 9 && raw < 45) {
            int idx = raw - 9;
            if (idx >= bySlot.size()) return;
            LicenseType lt = bySlot.get(idx);
            if (e.getClick().isShiftClick()) {
                plugin.companies().data().deleteLicenseType(lt.getId());
                MessageUtil.send(viewer, "&cDeleted license type: " + lt.getName());
                open();
            } else {
                // edit existing
                AdminBuilderMenu.Draft d = new AdminBuilderMenu.Draft();
                d.editingId = lt.getId();
                d.name = lt.getName();
                d.iconMaterial = lt.getIconMaterial();
                d.price = lt.getPrice();
                d.validDurationMillis = lt.getValidDurationMillis();
                for (dk.companies.model.JobTemplate jt : lt.getJobs()) {
                    AdminBuilderMenu.JobDraft jd = new AdminBuilderMenu.JobDraft();
                    jd.material = jt.getMaterial();
                    jd.baseAmount = jt.getBaseAmount();
                    jd.baseReward = jt.getBaseReward();
                    jd.jobDurationMillis = jt.getJobDurationMillis();
                    d.jobs.add(jd);
                }
                new AdminBuilderMenu(plugin, viewer, d).open();
            }
        }
    }
}
