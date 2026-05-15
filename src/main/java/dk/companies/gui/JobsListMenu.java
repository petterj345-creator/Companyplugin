package dk.companies.gui;

import dk.companies.Companies;
import dk.companies.util.FormatUtil;
import dk.companies.util.ItemBuilder;
import dk.companies.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class JobsListMenu extends Menu {

    private final Companies plugin;
    private final AdminBuilderMenu.Draft draft;

    public JobsListMenu(Companies plugin, Player viewer, AdminBuilderMenu.Draft draft) {
        super(viewer);
        this.plugin = plugin;
        this.draft = draft;
    }

    @Override public void open() {
        inv = Bukkit.createInventory(null, 54, MessageUtil.color("&8Job rotation: " + draft.name));

        for (int i = 0; i < 9; i++)
            inv.setItem(i, ItemBuilder.of(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build());
        inv.setItem(4, ItemBuilder.of(Material.WRITABLE_BOOK)
                .name("&bRotation pool")
                .lore("&7The license picks one of these each tick.").build());

        int slot = 9;
        for (int i = 0; i < draft.jobs.size() && slot < 45; i++, slot++) {
            var jd = draft.jobs.get(i);
            Material icon = jd.material == null ? Material.BARRIER : jd.material;
            inv.setItem(slot, ItemBuilder.of(icon)
                    .name("&aJob " + (i + 1))
                    .lore("&7Material: &f" + (jd.material == null ? "&cnot set" : jd.material.name()),
                            "&7Amount: &f" + jd.baseAmount,
                            "&7Reward: &a" + FormatUtil.money(jd.baseReward),
                            "&7Duration: &f" + FormatUtil.duration(jd.jobDurationMillis),
                            "",
                            "&8Click to edit",
                            "&8Shift-click to delete")
                    .build());
        }
        // "Add new" button at end of list
        int addSlot = Math.min(9 + draft.jobs.size(), 44);
        if (draft.jobs.size() < 36) {
            inv.setItem(addSlot, ItemBuilder.of(Material.LIME_DYE)
                    .name("&a&l+ Add job")
                    .lore("&7Create a new job entry.").build());
        }

        inv.setItem(45, ItemBuilder.of(Material.ARROW).name("&7Back").build());

        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.openInventory(inv);
            GuiListener.register(viewer, this);
        });
    }

    @Override public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw == 45) {
            new AdminBuilderMenu(plugin, viewer, draft).open();
            return;
        }
        if (raw < 9 || raw >= 45) return;
        int idx = raw - 9;
        // Is it the "+ add" slot?
        if (idx == draft.jobs.size() && draft.jobs.size() < 36) {
            AdminBuilderMenu.JobDraft jd = new AdminBuilderMenu.JobDraft();
            draft.jobs.add(jd);
            new JobEditorMenu(plugin, viewer, draft, jd).open();
            return;
        }
        if (idx >= draft.jobs.size()) return;
        var jd = draft.jobs.get(idx);
        if (e.getClick().isShiftClick()) {
            draft.jobs.remove(idx);
            open();
            return;
        }
        new JobEditorMenu(plugin, viewer, draft, jd).open();
    }
}
