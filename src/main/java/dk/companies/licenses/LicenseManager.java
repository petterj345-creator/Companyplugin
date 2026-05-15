package dk.companies.licenses;

import dk.companies.Companies;
import dk.companies.data.DataStore;
import dk.companies.economy.CompanyManager;
import dk.companies.model.*;

import java.util.*;

public class LicenseManager {

    private final Companies plugin;
    private final DataStore data;
    private final CompanyManager companies;
    private final Random rng = new Random();

    public LicenseManager(Companies plugin, DataStore data, CompanyManager companies) {
        this.plugin = plugin;
        this.data = data;
        this.companies = companies;
    }

    public enum BuyResult { OK, NO_TYPE, NOT_ENOUGH_FUNDS, NO_PERMISSION, NO_JOBS_DEFINED, LEVEL_TOO_LOW }

    public BuyResult buy(Company c, UUID buyer, UUID typeId) {
        Role role = c.getRole(buyer);
        if (role == null || !role.canBuyLicense()) return BuyResult.NO_PERMISSION;
        LicenseType type = data.getLicenseType(typeId);
        if (type == null) return BuyResult.NO_TYPE;
        if (type.getJobs().isEmpty()) return BuyResult.NO_JOBS_DEFINED;
        if (c.getLevel() < type.getRequiredLevel()) return BuyResult.LEVEL_TOO_LOW;
        if (c.getBalance() < type.getPrice()) return BuyResult.NOT_ENOUGH_FUNDS;

        c.setBalance(c.getBalance() - type.getPrice());
        long now = System.currentTimeMillis();
        License lic = new License(UUID.randomUUID(), type.getId(), c.getId(),
                now, now + type.getValidDurationMillis());
        c.getLicenses().add(lic);
        rollJob(c, lic, type);
        data.saveCompany(c);
        data.saveLicense(lic);
        return BuyResult.OK;
    }

    /** Roll a new random job for the license, picking from the type's templates. */
    public void rollJob(Company company, License lic, LicenseType type) {
        if (type.getJobs().isEmpty()) { lic.setCurrentJob(null); return; }
        JobTemplate jt = type.getJobs().get(rng.nextInt(type.getJobs().size()));

        double amtMin = plugin.getConfig().getDouble("jobs.amount-min-multiplier", 0.8);
        double amtMax = plugin.getConfig().getDouble("jobs.amount-max-multiplier", 1.2);
        double rwdMin = plugin.getConfig().getDouble("jobs.reward-min-multiplier", 0.9);
        double rwdMax = plugin.getConfig().getDouble("jobs.reward-max-multiplier", 1.2);
        double levelMult = companies.levels().rewardMultiplier(company);

        int amount = Math.max(1, (int) Math.round(
                jt.getBaseAmount() * (amtMin + rng.nextDouble() * (amtMax - amtMin))));
        double reward = jt.getBaseReward()
                * (rwdMin + rng.nextDouble() * (rwdMax - rwdMin))
                * levelMult;

        Job job = new Job(jt.getMaterial(), amount, reward,
                System.currentTimeMillis() + jt.getJobDurationMillis());
        lic.setCurrentJob(job);
    }

    /**
     * Replaces any expired/complete jobs on a company's licenses with freshly rolled ones.
     * Called when a player opens the Jobs GUI so they don't have to wait for the full
     * rotation interval after finishing a job.
     */
    public void refreshDeadJobs(Company company) {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (License l : company.getLicenses()) {
            if (l.getExpiresAt() < now) continue;
            Job j = l.getCurrentJob();
            if (j == null || j.isExpired() || j.isComplete()) {
                LicenseType type = data.getLicenseType(l.getTypeId());
                if (type != null) {
                    rollJob(company, l, type);
                    changed = true;
                }
            }
        }
        if (changed) data.saveCompany(company);
    }

    /** Called by scheduler. Rotates jobs and prunes expired licenses. */
    public void rotateAll() {
        long now = System.currentTimeMillis();
        for (Company c : data.getCompanies()) {
            Iterator<License> it = c.getLicenses().iterator();
            boolean changed = false;
            while (it.hasNext()) {
                License l = it.next();
                if (l.getExpiresAt() < now) {
                    it.remove();
                    data.deleteLicense(l.getId());
                    changed = true;
                    continue;
                }
                LicenseType type = data.getLicenseType(l.getTypeId());
                if (type == null) continue;
                rollJob(c, l, type);
            }
            if (changed) data.saveCompany(c);
        }
    }

    public DeliveryResult tryDeliver(Company c, org.bukkit.Material material, int amount) {
        long now = System.currentTimeMillis();
        for (License l : c.getLicenses()) {
            if (l.getExpiresAt() < now) continue;
            Job j = l.getCurrentJob();
            if (j == null || j.isExpired() || j.isComplete()) continue;
            if (j.getMaterial() != material) continue;

            int needed = j.getRemaining();
            int take = Math.min(needed, amount);
            j.addDelivered(take);
            int leftover = amount - take;

            DeliveryResult r = new DeliveryResult();
            r.taken = take;
            r.leftover = leftover;
            r.license = l;
            r.job = j;
            r.completed = j.isComplete();
            if (r.completed) {
                c.setBalance(c.getBalance() + j.getReward());
                c.addEarningsThisWindow(j.getReward());
                data.saveCompany(c);
                LicenseType type = data.getLicenseType(l.getTypeId());
                if (type != null) rollJob(c, l, type);
            }
            return r;
        }
        DeliveryResult r = new DeliveryResult();
        r.taken = 0;
        r.leftover = amount;
        return r;
    }

    public CompanyManager companies() { return companies; }

    public static class DeliveryResult {
        public int taken;
        public int leftover;
        public License license;
        public Job job;
        public boolean completed;
    }
}
