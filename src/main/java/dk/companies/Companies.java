package dk.companies;

import dk.companies.commands.CompanyCommand;
import dk.companies.data.DataStore;
import dk.companies.economy.CompanyManager;
import dk.companies.economy.PayoutManager;
import dk.companies.economy.VaultHook;
import dk.companies.gui.ChatInput;
import dk.companies.gui.GuiListener;
import dk.companies.licenses.LicenseManager;
import dk.companies.util.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class Companies extends JavaPlugin {

    private DataStore data;
    private VaultHook vault;
    private CompanyManager companies;
    private LicenseManager licenses;
    private PayoutManager payouts;

    private BukkitTask rotationTask;
    private BukkitTask taxTask;
    private BukkitTask payoutTask;

    // Wall-clock millis of the next scheduled fire for each task, for GUI display.
    private long nextTaxAt;
    private long nextPayoutAt;
    private long nextRotationAt;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MessageUtil.setPrefix(getConfig().getString("messages.prefix", "&8[&6Companies&8] &r"));

        vault = new VaultHook(this);
        if (!vault.setup()) {
            getLogger().severe("Vault economy not found. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        data = new DataStore(this);
        try {
            data.connect();
        } catch (Exception e) {
            getLogger().severe("Failed to open database: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        companies = new CompanyManager(this, data, vault);
        licenses = new LicenseManager(this, data, companies);
        payouts = new PayoutManager(this, data, vault);

        // Roll a job for every license that doesn't have one (happens after first reload).
        for (var c : data.getCompanies()) {
            for (var l : c.getLicenses()) {
                if (l.getCurrentJob() == null) {
                    var t = data.getLicenseType(l.getTypeId());
                    if (t != null) licenses.rollJob(c, l, t);
                }
            }
        }

        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new ChatInput(this), this);

        var cmd = getCommand("company");
        if (cmd != null) {
            CompanyCommand exec = new CompanyCommand(this);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }

        // Scheduled tasks
        long rotMinutes = Math.max(1, getConfig().getLong("jobs.rotation-minutes", 180));
        long taxMinutes = Math.max(1, getConfig().getLong("economy.earnings-tax-interval-minutes", 180));
        long payoutMinutes = Math.max(1, getConfig().getLong("payout.interval-minutes", 20));

        long now = System.currentTimeMillis();
        nextRotationAt = now + rotMinutes * 60_000L;
        nextTaxAt = now + taxMinutes * 60_000L;
        nextPayoutAt = now + payoutMinutes * 60_000L;

        rotationTask = getServer().getScheduler().runTaskTimer(this, () -> {
            licenses.rotateAll();
            nextRotationAt = System.currentTimeMillis() + rotMinutes * 60_000L;
        }, rotMinutes * 60L * 20L, rotMinutes * 60L * 20L);

        taxTask = getServer().getScheduler().runTaskTimer(this, () -> {
            companies.runEarningsTaxTick();
            nextTaxAt = System.currentTimeMillis() + taxMinutes * 60_000L;
        }, taxMinutes * 60L * 20L, taxMinutes * 60L * 20L);

        payoutTask = getServer().getScheduler().runTaskTimer(this, () -> {
            payouts.runPayoutTick();
            nextPayoutAt = System.currentTimeMillis() + payoutMinutes * 60_000L;
        }, payoutMinutes * 60L * 20L, payoutMinutes * 60L * 20L);

        getLogger().info("Companies enabled. Loaded " + data.getCompanies().size()
                + " companies, " + data.getLicenseTypes().size() + " license types.");
    }

    @Override
    public void onDisable() {
        if (rotationTask != null) rotationTask.cancel();
        if (taxTask != null) taxTask.cancel();
        if (payoutTask != null) payoutTask.cancel();
        if (data != null) data.close();
    }

    public DataStore data() { return data; }
    public VaultHook vault() { return vault; }
    public CompanyManager companies() { return companies; }
    public LicenseManager licenses() { return licenses; }
    public PayoutManager payouts() { return payouts; }

    public long getNextTaxAt() { return nextTaxAt; }
    public long getNextPayoutAt() { return nextPayoutAt; }
    public long getNextRotationAt() { return nextRotationAt; }
}
