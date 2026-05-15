package dk.companies.gui;

import dk.companies.Companies;
import dk.companies.util.FormatUtil;
import dk.companies.util.ItemBuilder;
import dk.companies.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.function.Consumer;

/**
 * Admin-only menu for tweaking server-wide settings (withdraw tax %, earnings tax %, etc.).
 * Edits are written to {@code config.yml} immediately so they survive restarts.
 */
public class AdminSettingsMenu extends Menu {

    private final Companies plugin;

    public AdminSettingsMenu(Companies plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    @Override public void open() {
        if (!viewer.hasPermission("companies.admin")) {
            viewer.closeInventory();
            MessageUtil.send(viewer, "&cNo permission.");
            return;
        }
        FileConfiguration cfg = plugin.getConfig();

        inv = Bukkit.createInventory(null, 45, MessageUtil.color("&8Admin: Settings"));
        for (int i = 0; i < 9; i++)
            inv.setItem(i, ItemBuilder.of(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build());
        inv.setItem(4, ItemBuilder.of(Material.COMPARATOR)
                .name("&5Server-wide Settings")
                .lore("&7Changes save immediately to config.yml.").build());

        // Withdrawal tax %
        inv.setItem(10, ItemBuilder.of(Material.GOLD_NUGGET)
                .name("&eWithdrawal tax: &c"
                        + String.format("%.1f%%", cfg.getDouble("economy.withdraw-tax-percent", 10.0)))
                .lore("&7Tax taken when a company owner",
                        "&7or manager withdraws money.",
                        "&8Click to set in chat.").build());

        // Earnings tax %
        inv.setItem(12, ItemBuilder.of(Material.IRON_INGOT)
                .name("&eEarnings tax: &c"
                        + String.format("%.1f%%", cfg.getDouble("economy.earnings-tax-percent", 15.0)))
                .lore("&7Periodic tax on earnings from",
                        "&7completed jobs.",
                        "&8Click to set in chat.").build());

        // Earnings tax interval
        long mins = cfg.getLong("economy.earnings-tax-interval-minutes", 180);
        inv.setItem(14, ItemBuilder.of(Material.CLOCK)
                .name("&eEarnings tax interval: &f"
                        + FormatUtil.duration(mins * 60_000L))
                .lore("&7How often the earnings tax runs.",
                        "&8Click to set in chat (e.g. '3h', '90m').",
                        "&8(Requires plugin reload to take effect.)").build());

        // Min balance
        inv.setItem(16, ItemBuilder.of(Material.REDSTONE_BLOCK)
                .name("&eMin balance: &c"
                        + FormatUtil.money(cfg.getDouble("company.min-balance", -10000.0)))
                .lore("&7Companies below this balance",
                        "&7are auto-disbanded.",
                        "&8Click to set in chat.").build());

        // Creation cost
        inv.setItem(20, ItemBuilder.of(Material.EMERALD)
                .name("&eCompany creation cost: &a"
                        + FormatUtil.money(cfg.getDouble("economy.creation-cost", 25000.0)))
                .lore("&8Click to set in chat.").build());

        // Job rotation interval
        long rotMins = cfg.getLong("jobs.rotation-minutes", 180);
        inv.setItem(22, ItemBuilder.of(Material.REPEATER)
                .name("&eJob rotation interval: &f"
                        + FormatUtil.duration(rotMins * 60_000L))
                .lore("&7How often jobs auto-rotate.",
                        "&8Click to set in chat.",
                        "&8(Requires plugin reload to take effect.)").build());

        // Payout interval
        long payoutMins = cfg.getLong("payout.interval-minutes", 20);
        inv.setItem(24, ItemBuilder.of(Material.SUNFLOWER)
                .name("&ePayout interval: &f"
                        + FormatUtil.duration(payoutMins * 60_000L))
                .lore("&7How often contributors are paid.",
                        "&8Click to set in chat.",
                        "&8(Requires plugin reload to take effect.)").build());

        // Min payout %
        inv.setItem(30, ItemBuilder.of(Material.PAPER)
                .name("&eMin payout rate: &e"
                        + String.format("%.0f%%", cfg.getDouble("payout.min-percent", 10.0)))
                .lore("&7Lowest payout % an owner can set.",
                        "&8Click to set in chat.").build());

        // Back
        inv.setItem(36, ItemBuilder.of(Material.ARROW).name("&7Back").build());

        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.openInventory(inv);
            GuiListener.register(viewer, this);
        });
    }

    @Override public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        FileConfiguration cfg = plugin.getConfig();

        switch (raw) {
            case 36 -> new AdminMenu(plugin, viewer).open();

            case 10 -> promptPercent("withdrawal tax", v -> {
                cfg.set("economy.withdraw-tax-percent", v);
                plugin.saveConfig();
                MessageUtil.send(viewer, "&aWithdrawal tax set to &c" + String.format("%.1f%%", v));
                open();
            });
            case 12 -> promptPercent("earnings tax", v -> {
                cfg.set("economy.earnings-tax-percent", v);
                plugin.saveConfig();
                MessageUtil.send(viewer, "&aEarnings tax set to &c" + String.format("%.1f%%", v));
                open();
            });
            case 14 -> promptDuration("earnings tax interval", v -> {
                cfg.set("economy.earnings-tax-interval-minutes", v / 60_000L);
                plugin.saveConfig();
                MessageUtil.send(viewer, "&aEarnings tax interval set. &7Run &e/company reload &7or restart to apply.");
                open();
            });
            case 16 -> promptDouble("min balance (can be negative)", v -> {
                cfg.set("company.min-balance", v);
                plugin.saveConfig();
                MessageUtil.send(viewer, "&aMin balance set to &c" + FormatUtil.money(v));
                open();
            });
            case 20 -> promptDouble("company creation cost", v -> {
                if (v < 0) v = 0;
                cfg.set("economy.creation-cost", v);
                plugin.saveConfig();
                MessageUtil.send(viewer, "&aCreation cost set to &a" + FormatUtil.money(v));
                open();
            });
            case 22 -> promptDuration("job rotation interval", v -> {
                cfg.set("jobs.rotation-minutes", v / 60_000L);
                plugin.saveConfig();
                MessageUtil.send(viewer, "&aJob rotation interval set. &7Run &e/company reload &7or restart to apply.");
                open();
            });
            case 24 -> promptDuration("payout interval", v -> {
                cfg.set("payout.interval-minutes", v / 60_000L);
                plugin.saveConfig();
                MessageUtil.send(viewer, "&aPayout interval set. &7Run &e/company reload &7or restart to apply.");
                open();
            });
            case 30 -> promptPercent("minimum payout rate", v -> {
                cfg.set("payout.min-percent", v);
                plugin.saveConfig();
                MessageUtil.send(viewer, "&aMin payout rate set to &e" + String.format("%.0f%%", v));
                open();
            });
        }
    }

    // ─── chat input helpers ───
    private void promptPercent(String label, java.util.function.DoubleConsumer cb) {
        viewer.closeInventory();
        MessageUtil.send(viewer, "&eEnter the new " + label + " %% (0-100), or 'cancel':");
        ChatInput.prompt(viewer, text -> {
            if (text.equalsIgnoreCase("cancel")) { open(); return; }
            try {
                double v = Double.parseDouble(text.trim().replace("%", ""));
                if (v < 0) v = 0;
                if (v > 100) v = 100;
                cb.accept(v);
            } catch (NumberFormatException ex) {
                MessageUtil.send(viewer, "&cNot a number.");
                open();
            }
        });
    }

    private void promptDouble(String label, java.util.function.DoubleConsumer cb) {
        viewer.closeInventory();
        MessageUtil.send(viewer, "&eEnter the new " + label + ", or 'cancel':");
        ChatInput.prompt(viewer, text -> {
            if (text.equalsIgnoreCase("cancel")) { open(); return; }
            try { cb.accept(Double.parseDouble(text.trim())); }
            catch (NumberFormatException ex) {
                MessageUtil.send(viewer, "&cNot a number.");
                open();
            }
        });
    }

    private void promptDuration(String label, Consumer<Long> cb) {
        viewer.closeInventory();
        MessageUtil.send(viewer, "&eEnter the new " + label + " (e.g. 3h, 90m, 24h), or 'cancel':");
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
