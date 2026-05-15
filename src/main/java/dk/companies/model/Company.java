package dk.companies.model;

import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime model of a company; persisted by {@link dk.companies.data.DataStore}. */
public class Company {

    private final UUID id;
    private String name;
    private UUID owner;
    private double balance;
    private final long createdAt;
    private int level = 1;

    /** member uuid -> role */
    private final Map<UUID, Role> members = new ConcurrentHashMap<>();
    /** member uuid -> total deposited/contributed (lifetime) */
    private final Map<UUID, Double> contributions = new ConcurrentHashMap<>();
    /** member uuid -> total items delivered to jobs (lifetime) */
    private final Map<UUID, Long> itemsDelivered = new ConcurrentHashMap<>();
    /** active licenses installed into this company */
    private final List<License> licenses = new ArrayList<>();
    /** currently open job applications (by player uuid) */
    private final Set<UUID> applicants = ConcurrentHashMap.newKeySet();
    /** whether the company is publicly looking for workers */
    private boolean hiring;

    public Company(UUID id, String name, UUID owner, double balance, long createdAt) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public long getCreatedAt() { return createdAt; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, level); }
    public Map<UUID, Role> getMembers() { return members; }
    public Map<UUID, Double> getContributions() { return contributions; }
    public Map<UUID, Long> getItemsDelivered() { return itemsDelivered; }
    public List<License> getLicenses() { return licenses; }
    public Set<UUID> getApplicants() { return applicants; }
    public boolean isHiring() { return hiring; }
    public void setHiring(boolean hiring) { this.hiring = hiring; }

    public Role getRole(UUID player) {
        return members.get(player);
    }

    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }

    public void addContribution(UUID player, double amount) {
        contributions.merge(player, amount, Double::sum);
    }

    public void addItemsDelivered(UUID player, long amount) {
        itemsDelivered.merge(player, amount, Long::sum);
    }

    public License getLicenseFor(Material material) {
        long now = System.currentTimeMillis();
        for (License l : licenses) {
            if (l.getMaterial() == material && l.getExpiresAt() > now) return l;
        }
        return null;
    }
}
