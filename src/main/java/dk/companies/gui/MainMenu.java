package dk.companies.gui;

import dk.companies.Companies;
import dk.companies.model.Company;
import dk.companies.model.License;
import dk.companies.model.LicenseType;
import dk.companies.model.Role;
import dk.companies.util.FormatUtil;
import dk.companies.util.ItemBuilder;
import dk.companies.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

public class MainMenu extends Menu {

    private final Companies plugin;
    private final Company company;

    public MainMenu(Companies plugin, Player viewer, Company company) {
        super(viewer);
        this.plugin = plugin;
        this.company = company;
    }

    @Override public void open() {
        inv = Bukkit.createInventory(null, 54,
                MessageUtil.color("&8" + company.getName()));
        fillBorder();
        Role role = company.getRole(viewer.getUniqueId());

        // Info / balance
        var lvl = plugin.companies().levels();
        inv.setItem(slotFor(1, 4), ItemBuilder.of(Material.GOLD_INGOT)
                .name("&6&l" + company.getName())
                .lore("&7Level: &e" + company.getLevel() + (lvl.atMax(company) ? " &8(max)" : ""),
                        "&7Reward multiplier: &a" + String.format("%.2fx", lvl.rewardMultiplier(company)),
                        "&7Balance: &a" + FormatUtil.money(company.getBalance()),
                        "&7Members: &f" + company.getMembers().size(),
                        "&7Your role: &b" + (role == null ? "—" : role.name()),
                        "&7Active licenses: &f" + company.getLicenses().size(),
                        "&7Hiring: " + (company.isHiring() ? "&aOpen" : "&cClosed"))
                .build());

        // Deposit
        inv.setItem(slotFor(2, 1), ItemBuilder.of(Material.HOPPER)
                .name("&aDeposit money")
                .lore("&7Send your money to the company.",
                        "&8Click to enter amount in chat.")
                .build());

        // Withdraw
        boolean canWithdraw = role != null && role.canWithdraw();
        inv.setItem(slotFor(2, 2), ItemBuilder.of(Material.DROPPER)
                .name((canWithdraw ? "&e" : "&8") + "Withdraw money")
                .lore("&7Take money out.",
                        "&7Tax: &c" + plugin.getConfig().getDouble("economy.withdraw-tax-percent", 10.0) + "%",
                        canWithdraw ? "&8Click to enter amount in chat." : "&cNo permission.")
                .build());

        // Licenses
        inv.setItem(slotFor(2, 3), ItemBuilder.of(Material.PAPER)
                .name("&bLicenses")
                .lore("&7Buy & view active licenses.",
                        "&7Active: &f" + activeCount(),
                        "&8Click to open.")
                .build());

        // Jobs
        inv.setItem(slotFor(2, 4), ItemBuilder.of(Material.WRITABLE_BOOK)
                .name("&dCurrent jobs")
                .lore("&7See & deliver your active jobs.",
                        "&8Click to open.")
                .build());

        // Workers
        inv.setItem(slotFor(2, 5), ItemBuilder.of(Material.PLAYER_HEAD)
                .name("&fWorkers")
                .lore("&7Manage hires, see contributions.",
                        "&8Click to open.")
                .build());

        // Applications (only managers/owners)
        if (role != null && role.canManageWorkers()) {
            inv.setItem(slotFor(2, 6), ItemBuilder.of(Material.WRITTEN_BOOK)
                    .name("&6Applications &7(" + company.getApplicants().size() + ")")
                    .lore("&7Review pending join requests.",
                            "&8Click to open.")
                    .glow(!company.getApplicants().isEmpty())
                    .build());
        }

        // Hiring toggle (managers/owners)
        if (role != null && role.canManageWorkers()) {
            inv.setItem(slotFor(2, 7), ItemBuilder.of(
                            company.isHiring() ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(company.isHiring() ? "&aHiring: OPEN" : "&7Hiring: closed")
                    .lore("&7Public job board shows this company",
                            "&7when set to OPEN.",
                            "&8Click to toggle.")
                    .build());
        }

        // Disband (owner only)
        if (role == Role.OWNER) {
            inv.setItem(slotFor(4, 8), ItemBuilder.of(Material.BARRIER)
                    .name("&c&lDisband Company")
                    .lore("&7Permanently delete this company.",
                            "&8Shift-click to confirm.")
                    .build());
        }

        // Upgrade level (owner/manager)
        if (role != null && role.canManageWorkers()) {
            boolean max = lvl.atMax(company);
            double cost = max ? 0 : lvl.upgradeCost(company.getLevel());
            inv.setItem(slotFor(3, 4), ItemBuilder.of(max ? Material.NETHERITE_INGOT : Material.EXPERIENCE_BOTTLE)
                    .name(max ? "&6&lMax Level Reached"
                            : "&b&lUpgrade Company &7(Lvl " + company.getLevel()
                            + " → " + (company.getLevel() + 1) + ")")
                    .lore(max
                            ? new String[]{"&7Your company is at the maximum level."}
                            : new String[]{
                                    "&7Current multiplier: &a" + String.format("%.2fx", lvl.rewardMultiplier(company)),
                                    "&7Next multiplier:    &a" + String.format("%.2fx",
                                            1.0 + company.getLevel() * plugin.getConfig()
                                                    .getDouble("levels.reward-multiplier-per-level", 0.05)),
                                    "&7Cost: &6" + FormatUtil.money(cost),
                                    "",
                                    "&8Click to spend company funds and upgrade."
                            })
                    .glow(!max)
                    .build());
        }

        // Admin panel (permission)
        if (viewer.hasPermission("companies.admin")) {
            inv.setItem(slotFor(4, 0), ItemBuilder.of(Material.COMMAND_BLOCK)
                    .name("&5Admin Panel")
                    .lore("&7Manage license types.",
                            "&8Click to open.")
                    .build());
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.openInventory(inv);
            GuiListener.register(viewer, this);
        });
    }

    private int activeCount() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (License l : company.getLicenses()) if (l.getExpiresAt() > now) count++;
        return count;
    }

    private void fillBorder() {
        for (int i = 0; i < inv.getSize(); i++) {
            int r = i / 9, c = i % 9;
            if (r == 0 || r == 5 || c == 0 || c == 8) {
                inv.setItem(i, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
            }
        }
    }

    @Override public void handleClick(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        Role role = company.getRole(viewer.getUniqueId());

        if (slot == slotFor(2, 1)) {
            promptDeposit();
        } else if (slot == slotFor(2, 2) && role != null && role.canWithdraw()) {
            promptWithdraw();
        } else if (slot == slotFor(2, 3)) {
            new LicenseShopMenu(plugin, viewer, company).open();
        } else if (slot == slotFor(2, 4)) {
            new JobsMenu(plugin, viewer, company).open();
        } else if (slot == slotFor(2, 5)) {
            new WorkersMenu(plugin, viewer, company).open();
        } else if (slot == slotFor(2, 6) && role != null && role.canManageWorkers()) {
            new ApplicationsMenu(plugin, viewer, company).open();
        } else if (slot == slotFor(2, 7) && role != null && role.canManageWorkers()) {
            company.setHiring(!company.isHiring());
            plugin.companies().data().saveCompany(company);
            open();
        } else if (slot == slotFor(4, 8) && role == Role.OWNER && e.getClick().isShiftClick()) {
            plugin.companies().disband(company, "owner disbanded");
            viewer.closeInventory();
            MessageUtil.send(viewer, "&cCompany disbanded.");
        } else if (slot == slotFor(3, 4) && role != null && role.canManageWorkers()) {
            var r = plugin.companies().upgrade(viewer, company);
            switch (r) {
                case OK -> {
                    MessageUtil.send(viewer, "&aCompany upgraded to level &e" + company.getLevel() + "&a!");
                    open();
                }
                case MAX_LEVEL -> MessageUtil.send(viewer, "&cAlready at max level.");
                case NOT_ENOUGH_FUNDS -> MessageUtil.send(viewer, "&cCompany doesn't have enough funds.");
                case NO_PERMISSION -> MessageUtil.send(viewer, "&cNo permission.");
            }
        } else if (slot == slotFor(4, 0) && viewer.hasPermission("companies.admin")) {
            new AdminMenu(plugin, viewer).open();
        }
    }

    private void promptDeposit() {
        viewer.closeInventory();
        MessageUtil.send(viewer, "&eType the amount to deposit (or 'cancel'):");
        ChatInput.prompt(viewer, text -> {
            if (text.equalsIgnoreCase("cancel")) { open(); return; }
            try {
                double v = Double.parseDouble(text);
                var r = plugin.companies().deposit(viewer, company, v);
                switch (r) {
                    case OK -> MessageUtil.send(viewer, "&aDeposited " + FormatUtil.money(v) + ".");
                    case NOT_ENOUGH_MONEY -> MessageUtil.send(viewer, "&cYou don't have that much.");
                    default -> MessageUtil.send(viewer, "&cFailed.");
                }
            } catch (NumberFormatException ex) {
                MessageUtil.send(viewer, "&cNot a number.");
            }
            open();
        });
    }

    private void promptWithdraw() {
        viewer.closeInventory();
        MessageUtil.send(viewer, "&eType the amount to withdraw (or 'cancel'):");
        ChatInput.prompt(viewer, text -> {
            if (text.equalsIgnoreCase("cancel")) { open(); return; }
            try {
                double v = Double.parseDouble(text);
                var r = plugin.companies().withdraw(viewer, company, v);
                double pct = plugin.getConfig().getDouble("economy.withdraw-tax-percent", 10.0);
                switch (r) {
                    case OK -> MessageUtil.send(viewer, "&aWithdrew " + FormatUtil.money(v)
                            + " &7(after " + pct + "% tax: &a" + FormatUtil.money(v * (1 - pct/100)) + "&7)");
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
