package dk.companies.economy;

import dk.companies.Companies;
import dk.companies.data.DataStore;
import dk.companies.model.Company;
import dk.companies.model.Role;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CompanyManager {

    private final Companies plugin;
    private final DataStore data;
    private final VaultHook vault;
    private final CompanyLevels levels;

    public CompanyManager(Companies plugin, DataStore data, VaultHook vault) {
        this.plugin = plugin;
        this.data = data;
        this.vault = vault;
        this.levels = new CompanyLevels(plugin);
    }

    public CompanyLevels levels() { return levels; }

    public enum CreateResult { OK, NAME_TAKEN, NAME_INVALID, ALREADY_IN_COMPANY, NOT_ENOUGH_MONEY }
    public enum TxResult { OK, NOT_ENOUGH_MONEY, BELOW_ZERO, NO_PERMISSION, FAILED }
    public enum UpgradeResult { OK, MAX_LEVEL, NOT_ENOUGH_FUNDS, NO_PERMISSION }

    public CreateResult create(Player owner, String name) {
        if (data.getCompanyOf(owner.getUniqueId()) != null) return CreateResult.ALREADY_IN_COMPANY;
        if (name == null || name.isBlank()) return CreateResult.NAME_INVALID;
        int max = plugin.getConfig().getInt("company.max-name-length", 24);
        if (name.length() > max || !name.matches("[A-Za-z0-9_ -]{2,}"))
            return CreateResult.NAME_INVALID;
        if (data.getCompanyByName(name) != null) return CreateResult.NAME_TAKEN;

        double cost = plugin.getConfig().getDouble("economy.creation-cost", 25000.0);
        if (!vault.has(owner, cost)) return CreateResult.NOT_ENOUGH_MONEY;
        if (!vault.withdraw(owner, cost)) return CreateResult.NOT_ENOUGH_MONEY;

        Company c = new Company(UUID.randomUUID(), name, owner.getUniqueId(), 0.0,
                System.currentTimeMillis());
        c.getMembers().put(owner.getUniqueId(), Role.OWNER);
        data.saveCompany(c);
        data.saveMember(c.getId(), owner.getUniqueId(), Role.OWNER);
        return CreateResult.OK;
    }

    public void disband(Company c, String reason) {
        for (UUID member : c.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                p.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(
                                "&8[&6Companies&8] &cYour company '" + c.getName()
                                        + "' has been disbanded: " + reason));
            }
        }
        data.deleteCompany(c);
    }

    public TxResult deposit(Player player, Company c, double amount) {
        if (amount <= 0) return TxResult.FAILED;
        if (!vault.has(player, amount)) return TxResult.NOT_ENOUGH_MONEY;
        if (!vault.withdraw(player, amount)) return TxResult.FAILED;
        c.setBalance(c.getBalance() + amount);
        c.addContribution(player.getUniqueId(), amount);
        data.saveCompany(c);
        data.saveContribution(c.getId(), player.getUniqueId(),
                c.getContributions().getOrDefault(player.getUniqueId(), 0.0));
        return TxResult.OK;
    }

    /** Withdraw amount; tax % is taken on top. The withdrawer receives (amount - tax). */
    public TxResult withdraw(Player player, Company c, double amount) {
        Role role = c.getRole(player.getUniqueId());
        if (role == null || !role.canWithdraw()) return TxResult.NO_PERMISSION;
        if (amount <= 0) return TxResult.FAILED;

        double minBal = plugin.getConfig().getDouble("company.min-balance", -10000.0);
        if (c.getBalance() - amount < minBal) return TxResult.BELOW_ZERO;

        double taxPct = plugin.getConfig().getDouble("economy.withdraw-tax-percent", 10.0);
        double tax = amount * (taxPct / 100.0);
        double payout = amount - tax;

        c.setBalance(c.getBalance() - amount);
        if (!vault.deposit(player, payout)) {
            c.setBalance(c.getBalance() + amount); // rollback
            return TxResult.FAILED;
        }
        data.saveCompany(c);
        return TxResult.OK;
    }

    /** Pay a salary from company balance directly to a player. */
    public TxResult paySalary(Player payer, Company c, OfflinePlayer target, double amount) {
        Role role = c.getRole(payer.getUniqueId());
        if (role == null || !role.canWithdraw()) return TxResult.NO_PERMISSION;
        if (amount <= 0) return TxResult.FAILED;

        double minBal = plugin.getConfig().getDouble("company.min-balance", -10000.0);
        if (c.getBalance() - amount < minBal) return TxResult.BELOW_ZERO;
        c.setBalance(c.getBalance() - amount);
        if (!vault.deposit(target, amount)) {
            c.setBalance(c.getBalance() + amount);
            return TxResult.FAILED;
        }
        data.saveCompany(c);
        return TxResult.OK;
    }

    /**
     * Earnings tax tick: takes a percentage of what the company has earned (from completed jobs)
     * since the last tick, then resets the window counter. Companies under min-balance get disbanded.
     */
    public void runEarningsTaxTick() {
        double taxPct = plugin.getConfig().getDouble("economy.earnings-tax-percent", 0.0);
        double minBal = plugin.getConfig().getDouble("company.min-balance", -10000.0);

        for (Company c : new java.util.ArrayList<>(data.getCompanies())) {
            double earnings = c.getEarningsThisWindow();
            if (earnings > 0 && taxPct > 0) {
                double tax = earnings * (taxPct / 100.0);
                c.setBalance(c.getBalance() - tax);
                Player owner = plugin.getServer().getPlayer(c.getOwner());
                if (owner != null) {
                    dk.companies.util.MessageUtil.send(owner,
                            "&7[" + c.getName() + "] Tax taken: &c"
                                    + dk.companies.util.FormatUtil.money(tax)
                                    + " &7(" + String.format("%.0f%%", taxPct)
                                    + " of " + dk.companies.util.FormatUtil.money(earnings) + " earned).");
                }
            }
            c.setEarningsThisWindow(0); // reset window even if no tax was due
            data.saveCompany(c);
        }
        // separate sweep: anyone under min-balance gets disbanded
        for (Company c : new java.util.ArrayList<>(data.getCompanies())) {
            if (c.getBalance() < minBal) disband(c, "balance below minimum");
        }
    }

    // ───── membership ─────
    public void apply(Player player, Company c) {
        c.getApplicants().add(player.getUniqueId());
        data.saveApplicant(c.getId(), player.getUniqueId());
    }

    public void cancelApplication(UUID player, Company c) {
        c.getApplicants().remove(player);
        data.deleteApplicant(c.getId(), player);
    }

    public boolean hire(Company c, UUID player) {
        int maxWorkers = plugin.getConfig().getInt("company.max-workers", 15);
        if (maxWorkers >= 0) {
            long workerCount = c.getMembers().values().stream()
                    .filter(r -> r != Role.OWNER).count();
            if (workerCount >= maxWorkers) return false;
        }
        c.getMembers().put(player, Role.WORKER);
        c.getApplicants().remove(player);
        data.saveMember(c.getId(), player, Role.WORKER);
        data.deleteApplicant(c.getId(), player);
        return true;
    }

    public void fire(Company c, UUID player) {
        if (player.equals(c.getOwner())) return;
        // Pay out any pending earnings as a final lump-sum.
        Double pending = c.getUnpaidEarnings().get(player);
        if (pending != null && pending > 0.009) {
            double minBal = plugin.getConfig().getDouble("company.min-balance", -10000.0);
            double payable = Math.min(pending, c.getBalance() - minBal);
            if (payable > 0) {
                if (vault.deposit(plugin.getServer().getOfflinePlayer(player), payable)) {
                    c.setBalance(c.getBalance() - payable);
                }
            }
        }
        c.getUnpaidEarnings().remove(player);
        c.getMembers().remove(player);
        data.deleteMember(c.getId(), player);
        data.deleteUnpaidEarnings(c.getId(), player);
        data.saveCompany(c);
    }

    public void promote(Company c, UUID player) {
        if (c.getRole(player) == Role.WORKER) {
            c.getMembers().put(player, Role.MANAGER);
            data.saveMember(c.getId(), player, Role.MANAGER);
        }
    }

    public void demote(Company c, UUID player) {
        if (c.getRole(player) == Role.MANAGER) {
            c.getMembers().put(player, Role.WORKER);
            data.saveMember(c.getId(), player, Role.WORKER);
        }
    }

    public DataStore data() { return data; }

    /** Spend company balance to bump the level by one. */
    public UpgradeResult upgrade(Player by, Company c) {
        var role = c.getRole(by.getUniqueId());
        if (role == null || !role.canManageWorkers()) return UpgradeResult.NO_PERMISSION;
        if (levels.atMax(c)) return UpgradeResult.MAX_LEVEL;
        double cost = levels.upgradeCost(c.getLevel());
        if (c.getBalance() < cost) return UpgradeResult.NOT_ENOUGH_FUNDS;
        c.setBalance(c.getBalance() - cost);
        c.setLevel(c.getLevel() + 1);
        data.saveCompany(c);
        return UpgradeResult.OK;
    }
}
