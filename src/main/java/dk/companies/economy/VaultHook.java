package dk.companies.economy;

import dk.companies.Companies;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private final Companies plugin;
    private Economy economy;

    public VaultHook(Companies plugin) { this.plugin = plugin; }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy get() { return economy; }

    public double balance(OfflinePlayer p) { return economy.getBalance(p); }
    public boolean has(OfflinePlayer p, double v) { return economy.has(p, v); }

    public boolean withdraw(OfflinePlayer p, double v) {
        return economy.withdrawPlayer(p, v).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer p, double v) {
        return economy.depositPlayer(p, v).transactionSuccess();
    }
}
