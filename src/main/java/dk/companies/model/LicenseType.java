package dk.companies.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Blueprint created by an admin. Players buy these from the company GUI;
 * buying produces a {@link License} instance installed into the company.
 *
 * <p>Each license type can have multiple {@link JobTemplate} entries. The license rotates
 * between them at each tick.</p>
 */
public class LicenseType {

    private final UUID id;
    private String name;
    private Material iconMaterial;        // display only — appears in the shop
    private double price;                 // shop price for the license
    private long validDurationMillis;     // total lifetime once bought
    private final List<JobTemplate> jobs = new ArrayList<>();

    public LicenseType(UUID id, String name, Material iconMaterial,
                       double price, long validDurationMillis) {
        this.id = id;
        this.name = name;
        this.iconMaterial = iconMaterial;
        this.price = price;
        this.validDurationMillis = validDurationMillis;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Material getIconMaterial() { return iconMaterial; }
    public void setIconMaterial(Material iconMaterial) { this.iconMaterial = iconMaterial; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public long getValidDurationMillis() { return validDurationMillis; }
    public void setValidDurationMillis(long validDurationMillis) { this.validDurationMillis = validDurationMillis; }
    public List<JobTemplate> getJobs() { return jobs; }
}
