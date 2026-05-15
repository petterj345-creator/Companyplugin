package dk.companies.model;

import org.bukkit.Material;

/**
 * One entry in a {@link LicenseType}'s rotation pool. Each license type can have multiple
 * templates; each rotation tick picks one (randomly) and produces a {@link Job}.
 */
public class JobTemplate {

    private Material material;
    private int baseAmount;
    private double baseReward;
    private long jobDurationMillis;

    public JobTemplate(Material material, int baseAmount, double baseReward, long jobDurationMillis) {
        this.material = material;
        this.baseAmount = baseAmount;
        this.baseReward = baseReward;
        this.jobDurationMillis = jobDurationMillis;
    }

    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }
    public int getBaseAmount() { return baseAmount; }
    public void setBaseAmount(int baseAmount) { this.baseAmount = baseAmount; }
    public double getBaseReward() { return baseReward; }
    public void setBaseReward(double baseReward) { this.baseReward = baseReward; }
    public long getJobDurationMillis() { return jobDurationMillis; }
    public void setJobDurationMillis(long jobDurationMillis) { this.jobDurationMillis = jobDurationMillis; }
}
