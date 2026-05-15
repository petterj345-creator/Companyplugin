package dk.companies.model;

import org.bukkit.Material;

/** A single rotating delivery job for an active license. */
public class Job {

    private final Material material;
    private final int requiredAmount;
    private int delivered;
    private final double reward;
    private final long expiresAt;

    public Job(Material material, int requiredAmount, double reward, long expiresAt) {
        this.material = material;
        this.requiredAmount = requiredAmount;
        this.delivered = 0;
        this.reward = reward;
        this.expiresAt = expiresAt;
    }

    public Material getMaterial() { return material; }
    public int getRequiredAmount() { return requiredAmount; }
    public int getDelivered() { return delivered; }
    public void addDelivered(int amount) { this.delivered += amount; }
    public double getReward() { return reward; }
    public long getExpiresAt() { return expiresAt; }

    public boolean isComplete() { return delivered >= requiredAmount; }
    public boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    public int getRemaining() { return Math.max(0, requiredAmount - delivered); }
}
