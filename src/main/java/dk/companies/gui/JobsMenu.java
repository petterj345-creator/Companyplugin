package dk.companies.gui;

import dk.companies.Companies;
import dk.companies.licenses.LicenseManager;
import dk.companies.model.Company;
import dk.companies.model.Job;
import dk.companies.model.License;
import dk.companies.model.LicenseType;
import dk.companies.util.FormatUtil;
import dk.companies.util.ItemBuilder;
import dk.companies.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Top half: list current jobs (one slot per license).
 * Bottom half: 9 "delivery" slots where players drop items. On close, items are processed
 * (delivered to matching jobs) and any leftover is returned to the player.
 */
public class JobsMenu extends Menu {

    private static final int[] DELIVERY_SLOTS = {36, 37, 38, 39, 40, 41, 42, 43, 44};

    private final Companies plugin;
    private final Company company;

    public JobsMenu(Companies plugin, Player viewer, Company company) {
        super(viewer);
        this.plugin = plugin;
        this.company = company;
    }

    @Override public boolean cancelByDefault() { return false; } // we allow deposits into delivery slots

    @Override public void open() {
        // Refresh any expired/complete jobs so players don't wait for the full rotation interval.
        plugin.licenses().refreshDeadJobs(company);

        inv = Bukkit.createInventory(null, 54, MessageUtil.color("&8Jobs: " + company.getName()));
        decorate();

        // Job rows
        long now = System.currentTimeMillis();
        int s = 9;
        for (License l : company.getLicenses()) {
            if (s >= 27) break;
            LicenseType lt = plugin.companies().data().getLicenseType(l.getTypeId());
            if (lt == null) continue;
            Job j = l.getCurrentJob();
            if (j == null) {
                inv.setItem(s++, ItemBuilder.of(Material.MAP)
                        .name("&7" + lt.getName())
                        .lore("&8No active job — rotating soon.")
                        .build());
                continue;
            }
            inv.setItem(s++, ItemBuilder.of(j.getMaterial())
                    .name("&a" + lt.getName())
                    .lore("&7Required: &f" + j.getRequiredAmount() + " &b" + j.getMaterial().name(),
                            "&7Delivered: &f" + j.getDelivered() + "&7/" + j.getRequiredAmount(),
                            "&7Reward: &a" + FormatUtil.money(j.getReward()),
                            "&7Job expires in: &f" + FormatUtil.remaining(j.getExpiresAt()),
                            "&7License valid: &f" + FormatUtil.remaining(l.getExpiresAt()),
                            "",
                            "&8Drop matching items into delivery slots below.")
                    .build());
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            viewer.openInventory(inv);
            GuiListener.register(viewer, this);
        });
    }

    private void decorate() {
        for (int i = 0; i < 9; i++)
            inv.setItem(i, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        for (int i = 27; i < 36; i++)
            inv.setItem(i, ItemBuilder.of(Material.GREEN_STAINED_GLASS_PANE)
                    .name("&aDelivery slots below &7— drop items here").build());
        inv.setItem(45, ItemBuilder.of(Material.ARROW).name("&7Back").build());
        inv.setItem(53, ItemBuilder.of(Material.LIME_DYE)
                .name("&aDeliver now")
                .lore("&7Process the items in the delivery slots.").build());
        for (int i = 46; i < 53; i++)
            if (inv.getItem(i) == null)
                inv.setItem(i, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
    }

    private boolean isDeliverySlot(int raw) {
        for (int s : DELIVERY_SLOTS) if (s == raw) return true;
        return false;
    }

    @Override public void handleClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        boolean topClicked = raw < inv.getSize();

        if (topClicked && raw == 45) {
            e.setCancelled(true);
            new MainMenu(plugin, viewer, company).open();
            return;
        }
        if (topClicked && raw == 53) {
            e.setCancelled(true);
            processDeliveries();
            return;
        }

        if (topClicked) {
            // Only delivery slots accept items
            if (!isDeliverySlot(raw)) {
                e.setCancelled(true);
            }
            return;
        }

        // Player inventory clicked. Block shift-click of items into non-delivery
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // Try to place into a free delivery slot
            ItemStack moving = e.getCurrentItem();
            if (moving == null || moving.getType().isAir()) return;
            e.setCancelled(true);
            int placed = placeIntoDelivery(moving);
            if (placed > 0) {
                ItemStack copy = moving.clone();
                copy.setAmount(moving.getAmount() - placed);
                e.setCurrentItem(copy.getAmount() <= 0 ? null : copy);
            }
        }
    }

    /** Try to put as much of {@code stack} as possible into delivery slots. Returns amount placed. */
    private int placeIntoDelivery(ItemStack stack) {
        int placed = 0;
        int remaining = stack.getAmount();
        int max = stack.getMaxStackSize();
        for (int s : DELIVERY_SLOTS) {
            ItemStack cur = inv.getItem(s);
            if (cur == null || cur.getType().isAir()) {
                ItemStack put = stack.clone();
                put.setAmount(Math.min(remaining, max));
                inv.setItem(s, put);
                placed += put.getAmount();
                remaining -= put.getAmount();
            } else if (cur.isSimilar(stack)) {
                int can = max - cur.getAmount();
                if (can > 0) {
                    int add = Math.min(can, remaining);
                    cur.setAmount(cur.getAmount() + add);
                    inv.setItem(s, cur);
                    placed += add;
                    remaining -= add;
                }
            }
            if (remaining <= 0) break;
        }
        return placed;
    }

    private void processDeliveries() {
        int totalEarned = 0;
        double totalReward = 0;
        for (int s : DELIVERY_SLOTS) {
            ItemStack stack = inv.getItem(s);
            if (stack == null || stack.getType().isAir()) continue;
            LicenseManager.DeliveryResult r =
                    plugin.licenses().tryDeliver(company, stack.getType(), stack.getAmount());
            totalEarned += r.taken;
            if (r.completed) totalReward += r.job.getReward();
            if (r.leftover > 0) {
                ItemStack left = stack.clone();
                left.setAmount(r.leftover);
                inv.setItem(s, left);
            } else {
                inv.setItem(s, null);
            }
        }
        if (totalEarned > 0) {
            MessageUtil.send(viewer, "&aDelivered &f" + totalEarned + " &aitems. Job rewards earned: &6"
                    + FormatUtil.money(totalReward));
            company.addContribution(viewer.getUniqueId(), totalReward);
            plugin.companies().data().saveContribution(company.getId(), viewer.getUniqueId(),
                    company.getContributions().getOrDefault(viewer.getUniqueId(), 0.0));
            company.addItemsDelivered(viewer.getUniqueId(), totalEarned);
            plugin.companies().data().saveItemsDelivered(company.getId(), viewer.getUniqueId(),
                    company.getItemsDelivered().getOrDefault(viewer.getUniqueId(), 0L));
            // Accrue payout queue (paid out on the next payout tick).
            if (totalReward > 0) {
                company.addUnpaidEarnings(viewer.getUniqueId(), totalReward);
                plugin.companies().data().saveUnpaidEarnings(company.getId(), viewer.getUniqueId(),
                        company.getUnpaidEarnings().getOrDefault(viewer.getUniqueId(), 0.0));
            }
        } else {
            MessageUtil.send(viewer, "&cNothing in the delivery slots matched an active job.");
        }
        open();
    }

    @Override public void handleClose(InventoryCloseEvent e) {
        // Return leftover items to the player
        for (int s : DELIVERY_SLOTS) {
            ItemStack stack = inv.getItem(s);
            if (stack == null || stack.getType().isAir()) continue;
            var leftover = viewer.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(item ->
                        viewer.getWorld().dropItemNaturally(viewer.getLocation(), item));
            }
            inv.setItem(s, null);
        }
    }
}
