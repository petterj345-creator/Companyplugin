package dk.companies.gui;

import dk.companies.Companies;
import dk.companies.util.FormatUtil;
import dk.companies.util.ItemBuilder;
import dk.companies.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class JobEditorMenu extends Menu {

    private final Companies plugin;
    private final AdminBuilderMenu.Draft parent;
    private final AdminBuilderMenu.JobDraft jd;

    public JobEditorMenu(Companies plugin, Player viewer,
                         AdminBuilderMenu.Draft parent, AdminBuilderMenu.JobDraft jd) {
        super(viewer);
        this.plugin = plugin;
        this.parent = parent;
        this.jd = jd;
    }

    @Override public boolean cancelByDefault() { return false; }

    @Override public void open() {
        inv = Bukkit.createInventory(null, 36, MessageUtil.color("&8Edit Job"));

        inv.setItem(10, ItemBuilder.of(jd.material == null ? Material.BARRIER : jd.material)
                .name(jd.material == null ? "&cMaterial: not set" : "&aMaterial: &f" + jd.material.name())
                .lore("&7Drag an item from your inventory",
                        "&7onto this slot to set it.").build());

        inv.setItem(12, ItemBuilder.of(Material.CHEST)
                .name("&eAmount required: &f" + jd.baseAmount)
                .lore("&8Click to set in chat.").build());

        inv.setItem(14, ItemBuilder.of(Material.EMERALD)
                .name("&eReward: &a" + FormatUtil.money(jd.baseReward))
                .lore("&8Click to set in chat.").build());

        inv.setItem(16, ItemBuilder.of(Material.CLOCK)
                .name("&eDuration: &f" + FormatUtil.duration(jd.jobDurationMillis))
                .lore("&8Click to set in chat (e.g. '3h', '90m').").build());

        inv.setItem(31, ItemBuilder.of(Material.EMERALD_BLOCK)
                .name("&a&lDone")
                .lore("&7Return to the job list.").build());
        inv.setItem(27, ItemBuilder.of(Material.ARROW).name("&7Back").build());

        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.openInventory(inv);
            GuiListener.register(viewer, this);
        });
    }

    @Override public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();

        if (raw == 10) {
            e.setCancelled(true);
            ItemStack cursor = e.getCursor();
            if (cursor != null && !cursor.getType().isAir() && cursor.getType() != Material.BARRIER) {
                jd.material = cursor.getType();
                open();
            }
            return;
        }
        if (raw >= inv.getSize()) {
            if (e.getClick().isShiftClick()) {
                e.setCancelled(true);
                ItemStack clicked = e.getCurrentItem();
                if (clicked != null && !clicked.getType().isAir()) {
                    jd.material = clicked.getType();
                    open();
                }
            }
            return;
        }

        e.setCancelled(true);
        switch (raw) {
            case 12 -> promptInt("Type the required amount:", v -> { jd.baseAmount = v; open(); });
            case 14 -> promptDouble("Type the reward:", v -> { jd.baseReward = v; open(); });
            case 16 -> promptDuration("Type the duration (e.g. 3h, 90m):",
                    v -> { jd.jobDurationMillis = v; open(); });
            case 27, 31 -> new JobsListMenu(plugin, viewer, parent).open();
        }
    }

    private void promptInt(String msg, java.util.function.IntConsumer cb) {
        viewer.closeInventory();
        MessageUtil.send(viewer, "&e" + msg);
        ChatInput.prompt(viewer, text -> {
            if (text.equalsIgnoreCase("cancel")) { open(); return; }
            try { cb.accept(Math.max(1, Integer.parseInt(text.trim()))); }
            catch (NumberFormatException ex) { MessageUtil.send(viewer, "&cNot a number."); open(); }
        });
    }

    private void promptDouble(String msg, java.util.function.DoubleConsumer cb) {
        viewer.closeInventory();
        MessageUtil.send(viewer, "&e" + msg);
        ChatInput.prompt(viewer, text -> {
            if (text.equalsIgnoreCase("cancel")) { open(); return; }
            try { cb.accept(Double.parseDouble(text.trim())); }
            catch (NumberFormatException ex) { MessageUtil.send(viewer, "&cNot a number."); open(); }
        });
    }

    private void promptDuration(String msg, java.util.function.LongConsumer cb) {
        viewer.closeInventory();
        MessageUtil.send(viewer, "&e" + msg);
        ChatInput.prompt(viewer, text -> {
            if (text.equalsIgnoreCase("cancel")) { open(); return; }
            Long ms = AdminBuilderMenu.parseDuration(text.trim());
            if (ms == null) {
                MessageUtil.send(viewer, "&cInvalid duration.");
                open();
                return;
            }
            cb.accept(ms);
        });
    }
}
