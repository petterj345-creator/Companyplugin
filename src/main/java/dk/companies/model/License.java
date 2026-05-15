package dk.companies.model;

import org.bukkit.Material;

import java.util.UUID;

/** An installed/active license inside a company. Generated when a company buys a {@link LicenseType}. */
public class License {

    private final UUID id;
    private final UUID typeId;
    private final UUID companyId;
    private final long boughtAt;
    private long expiresAt;
    private Job currentJob; // may be null until first rotation

    public License(UUID id, UUID typeId, UUID companyId, long boughtAt, long expiresAt) {
        this.id = id;
        this.typeId = typeId;
        this.companyId = companyId;
        this.boughtAt = boughtAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getTypeId() { return typeId; }
    public UUID getCompanyId() { return companyId; }
    public long getBoughtAt() { return boughtAt; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    public Job getCurrentJob() { return currentJob; }
    public void setCurrentJob(Job job) { this.currentJob = job; }

    /** Convenience: material of the underlying type (cached via Job too). */
    public Material getMaterial() {
        return currentJob != null ? currentJob.getMaterial() : null;
    }
}
