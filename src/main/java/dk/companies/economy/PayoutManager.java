package dk.companies.economy;

import dk.companies.Companies;
import dk.companies.data.DataStore;
import dk.companies.model.Company;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Runs on a fixed timer. For every company, pays each contributor a configurable
 * percentage of their unpaid earnings (queued by {@link dk.companies.gui.JobsMenu}).
 * The money comes out of the company balance; if that would drop the company below
 * the min balance, the payout is skipped this cycle and the queue persists.
 */
public class PayoutManager {

    private final Companies plugin;
    private final DataStore data;
    private final VaultHook vault;

    public PayoutManager(Companies plugin, DataStore data, VaultHook vault) {
        this.plugin = plugin;
        this.data = data;
        this.vault = vault;
    }

    public void runPayoutTick() {
        double minBal = plugin.getConfig().getDouble("company.min-balance", -10000.0);

        for (Company c : new java.util.ArrayList<>(data.getCompanies())) {
            double pct = c.getPayoutPercent();
            if (pct <= 0) continue;
            if (c.getUnpaidEarnings().isEmpty()) continue;

            // Sum what we'd need to pay out.
            double totalNeeded = 0;
            for (double v : c.getUnpaidEarnings().values()) totalNeeded += v * (pct / 100.0);

            if (totalNeeded <= 0.009) continue;

            // If we can't afford the full payout, skip and warn the owner (queue persists).
            if (c.getBalance() - totalNeeded < minBal) {
                Player owner = Bukkit.getPlayer(c.getOwner());
                if (owner != null && owner.isOnline()) {
                    dk.companies.util.MessageUtil.send(owner,
                            "&c[" + c.getName() + "] &cPayout skipped this cycle: not enough funds to pay contributors.");
                }
                continue;
            }

            // Pay each contributor their share.
            for (Map.Entry<UUID, Double> e : new java.util.HashMap<>(c.getUnpaidEarnings()).entrySet()) {
                UUID playerId = e.getKey();
                double pending = e.getValue();
                if (pending <= 0) continue;

                double pay = pending * (pct / 100.0);
                double remaining = pending - pay;

                OfflinePlayer op = Bukkit.getOfflinePlayer(playerId);
                if (vault.deposit(op, pay)) {
                    c.setBalance(c.getBalance() - pay);
                    c.getUnpaidEarnings().put(playerId, remaining);
                    data.saveUnpaidEarnings(c.getId(), playerId, remaining);

                    Player p = Bukkit.getPlayer(playerId);
                    if (p != null && p.isOnline()) {
                        dk.companies.util.MessageUtil.send(p, "&a[" + c.getName() + "] &aPayout: &6"
                                + dk.companies.util.FormatUtil.money(pay)
                                + " &7(pending: &f" + dk.companies.util.FormatUtil.money(remaining) + "&7)");
                    }
                }
            }
            data.saveCompany(c);
        }
    }
}
