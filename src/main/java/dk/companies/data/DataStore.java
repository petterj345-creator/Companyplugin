package dk.companies.data;

import dk.companies.Companies;
import dk.companies.model.*;
import org.bukkit.Material;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * SQLite-backed persistence. On enable: connect + create schema + load everything into memory.
 * Mutations write through to the DB and update the in-memory cache.
 */
public class DataStore {

    private final Companies plugin;
    private Connection conn;

    private final Map<UUID, Company> companies = new HashMap<>();
    private final Map<UUID, LicenseType> licenseTypes = new LinkedHashMap<>();

    public DataStore(Companies plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "companies.db");
        if (!dbFile.getParentFile().exists()) //noinspection ResultOfMethodCallIgnored
            dbFile.getParentFile().mkdirs();

        // Force-load the shaded driver
        try {
            Class.forName("dk.companies.lib.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite driver not found", e);
            }
        }
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
        }
        createSchema();
        loadAll();
    }

    private void createSchema() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS companies (
                    id TEXT PRIMARY KEY,
                    name TEXT UNIQUE NOT NULL,
                    owner TEXT NOT NULL,
                    balance REAL NOT NULL,
                    created_at INTEGER NOT NULL,
                    hiring INTEGER NOT NULL DEFAULT 0,
                    level INTEGER NOT NULL DEFAULT 1,
                    payout_percent REAL NOT NULL DEFAULT 10.0,
                    earnings_this_window REAL NOT NULL DEFAULT 0
                )""");
            // Migrations for old DBs lacking columns — ignore failures.
            try (Statement s2 = conn.createStatement()) {
                s2.execute("ALTER TABLE companies ADD COLUMN level INTEGER NOT NULL DEFAULT 1");
            } catch (SQLException ignored) {}
            try (Statement s2 = conn.createStatement()) {
                s2.execute("ALTER TABLE companies ADD COLUMN payout_percent REAL NOT NULL DEFAULT 10.0");
            } catch (SQLException ignored) {}
            try (Statement s2 = conn.createStatement()) {
                s2.execute("ALTER TABLE companies ADD COLUMN earnings_this_window REAL NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {}
            s.execute("""
                CREATE TABLE IF NOT EXISTS members (
                    company_id TEXT NOT NULL,
                    player TEXT NOT NULL,
                    role TEXT NOT NULL,
                    PRIMARY KEY (company_id, player),
                    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS contributions (
                    company_id TEXT NOT NULL,
                    player TEXT NOT NULL,
                    amount REAL NOT NULL DEFAULT 0,
                    PRIMARY KEY (company_id, player),
                    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS items_delivered (
                    company_id TEXT NOT NULL,
                    player TEXT NOT NULL,
                    amount INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (company_id, player),
                    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS unpaid_earnings (
                    company_id TEXT NOT NULL,
                    player TEXT NOT NULL,
                    amount REAL NOT NULL DEFAULT 0,
                    PRIMARY KEY (company_id, player),
                    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS applicants (
                    company_id TEXT NOT NULL,
                    player TEXT NOT NULL,
                    PRIMARY KEY (company_id, player),
                    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS license_types (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    icon_material TEXT NOT NULL,
                    price REAL NOT NULL,
                    valid_duration_millis INTEGER NOT NULL,
                    required_level INTEGER NOT NULL DEFAULT 1
                )""");
            try (Statement s2 = conn.createStatement()) {
                s2.execute("ALTER TABLE license_types ADD COLUMN required_level INTEGER NOT NULL DEFAULT 1");
            } catch (SQLException ignored) {}
            s.execute("""
                CREATE TABLE IF NOT EXISTS job_templates (
                    license_type_id TEXT NOT NULL,
                    idx INTEGER NOT NULL,
                    material TEXT NOT NULL,
                    base_amount INTEGER NOT NULL,
                    base_reward REAL NOT NULL,
                    job_duration_millis INTEGER NOT NULL,
                    PRIMARY KEY (license_type_id, idx),
                    FOREIGN KEY (license_type_id) REFERENCES license_types(id) ON DELETE CASCADE
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS licenses (
                    id TEXT PRIMARY KEY,
                    type_id TEXT NOT NULL,
                    company_id TEXT NOT NULL,
                    bought_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
                )""");
        }
    }

    private void loadAll() throws SQLException {
        try (Statement s = conn.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT * FROM license_types")) {
                while (rs.next()) {
                    LicenseType lt = new LicenseType(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("name"),
                            Material.valueOf(rs.getString("icon_material")),
                            rs.getDouble("price"),
                            rs.getLong("valid_duration_millis"));
                    try { lt.setRequiredLevel(rs.getInt("required_level")); } catch (SQLException ignored) {}
                    licenseTypes.put(lt.getId(), lt);
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT * FROM job_templates ORDER BY license_type_id, idx")) {
                while (rs.next()) {
                    LicenseType lt = licenseTypes.get(UUID.fromString(rs.getString("license_type_id")));
                    if (lt == null) continue;
                    lt.getJobs().add(new JobTemplate(
                            Material.valueOf(rs.getString("material")),
                            rs.getInt("base_amount"),
                            rs.getDouble("base_reward"),
                            rs.getLong("job_duration_millis")));
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT * FROM companies")) {
                while (rs.next()) {
                    Company c = new Company(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("name"),
                            UUID.fromString(rs.getString("owner")),
                            rs.getDouble("balance"),
                            rs.getLong("created_at"));
                    c.setHiring(rs.getInt("hiring") == 1);
                    try { c.setLevel(rs.getInt("level")); } catch (SQLException ignored) {}
                    try { c.setPayoutPercent(rs.getDouble("payout_percent")); } catch (SQLException ignored) {}
                    try { c.setEarningsThisWindow(rs.getDouble("earnings_this_window")); } catch (SQLException ignored) {}
                    companies.put(c.getId(), c);
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT * FROM members")) {
                while (rs.next()) {
                    UUID cid = UUID.fromString(rs.getString("company_id"));
                    Company c = companies.get(cid);
                    if (c != null) c.getMembers().put(
                            UUID.fromString(rs.getString("player")),
                            Role.valueOf(rs.getString("role")));
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT * FROM contributions")) {
                while (rs.next()) {
                    UUID cid = UUID.fromString(rs.getString("company_id"));
                    Company c = companies.get(cid);
                    if (c != null) c.getContributions().put(
                            UUID.fromString(rs.getString("player")),
                            rs.getDouble("amount"));
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT * FROM items_delivered")) {
                while (rs.next()) {
                    UUID cid = UUID.fromString(rs.getString("company_id"));
                    Company c = companies.get(cid);
                    if (c != null) c.getItemsDelivered().put(
                            UUID.fromString(rs.getString("player")),
                            rs.getLong("amount"));
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT * FROM unpaid_earnings")) {
                while (rs.next()) {
                    UUID cid = UUID.fromString(rs.getString("company_id"));
                    Company c = companies.get(cid);
                    if (c != null) c.getUnpaidEarnings().put(
                            UUID.fromString(rs.getString("player")),
                            rs.getDouble("amount"));
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT * FROM applicants")) {
                while (rs.next()) {
                    UUID cid = UUID.fromString(rs.getString("company_id"));
                    Company c = companies.get(cid);
                    if (c != null) c.getApplicants().add(UUID.fromString(rs.getString("player")));
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT * FROM licenses")) {
                while (rs.next()) {
                    UUID cid = UUID.fromString(rs.getString("company_id"));
                    Company c = companies.get(cid);
                    if (c == null) continue;
                    License l = new License(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("type_id")),
                            cid,
                            rs.getLong("bought_at"),
                            rs.getLong("expires_at"));
                    c.getLicenses().add(l);
                }
            }
        }
    }

    public void close() {
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (SQLException ignored) {}
    }

    // ───── lookups ─────
    public Collection<Company> getCompanies() { return companies.values(); }
    public Company getCompany(UUID id) { return companies.get(id); }
    public Company getCompanyByName(String n) {
        if (n == null) return null;
        for (Company c : companies.values()) if (c.getName().equalsIgnoreCase(n)) return c;
        return null;
    }
    public Company getCompanyOf(UUID player) {
        for (Company c : companies.values()) if (c.isMember(player)) return c;
        return null;
    }
    public Collection<LicenseType> getLicenseTypes() { return licenseTypes.values(); }
    public LicenseType getLicenseType(UUID id) { return licenseTypes.get(id); }

    // ───── mutations ─────
    public void saveCompany(Company c) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO companies(id,name,owner,balance,created_at,hiring,level,payout_percent,earnings_this_window) VALUES(?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, c.getId().toString());
            ps.setString(2, c.getName());
            ps.setString(3, c.getOwner().toString());
            ps.setDouble(4, c.getBalance());
            ps.setLong(5, c.getCreatedAt());
            ps.setInt(6, c.isHiring() ? 1 : 0);
            ps.setInt(7, c.getLevel());
            ps.setDouble(8, c.getPayoutPercent());
            ps.setDouble(9, c.getEarningsThisWindow());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("saveCompany: " + e.getMessage()); }
        companies.put(c.getId(), c);
    }

    public void deleteCompany(Company c) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM companies WHERE id = ?")) {
            ps.setString(1, c.getId().toString());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("deleteCompany: " + e.getMessage()); }
        companies.remove(c.getId());
    }

    public void saveMember(UUID companyId, UUID player, Role role) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO members(company_id,player,role) VALUES(?,?,?)")) {
            ps.setString(1, companyId.toString());
            ps.setString(2, player.toString());
            ps.setString(3, role.name());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("saveMember: " + e.getMessage()); }
    }

    public void deleteMember(UUID companyId, UUID player) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM members WHERE company_id = ? AND player = ?")) {
            ps.setString(1, companyId.toString());
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("deleteMember: " + e.getMessage()); }
    }

    public void saveContribution(UUID companyId, UUID player, double amount) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO contributions(company_id,player,amount) VALUES(?,?,?)")) {
            ps.setString(1, companyId.toString());
            ps.setString(2, player.toString());
            ps.setDouble(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("saveContribution: " + e.getMessage()); }
    }

    public void saveItemsDelivered(UUID companyId, UUID player, long amount) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO items_delivered(company_id,player,amount) VALUES(?,?,?)")) {
            ps.setString(1, companyId.toString());
            ps.setString(2, player.toString());
            ps.setLong(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("saveItemsDelivered: " + e.getMessage()); }
    }

    public void saveUnpaidEarnings(UUID companyId, UUID player, double amount) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO unpaid_earnings(company_id,player,amount) VALUES(?,?,?)")) {
            ps.setString(1, companyId.toString());
            ps.setString(2, player.toString());
            ps.setDouble(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("saveUnpaidEarnings: " + e.getMessage()); }
    }

    public void deleteUnpaidEarnings(UUID companyId, UUID player) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM unpaid_earnings WHERE company_id = ? AND player = ?")) {
            ps.setString(1, companyId.toString());
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("deleteUnpaidEarnings: " + e.getMessage()); }
    }

    public void saveApplicant(UUID companyId, UUID player) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO applicants(company_id,player) VALUES(?,?)")) {
            ps.setString(1, companyId.toString());
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("saveApplicant: " + e.getMessage()); }
    }

    public void deleteApplicant(UUID companyId, UUID player) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM applicants WHERE company_id = ? AND player = ?")) {
            ps.setString(1, companyId.toString());
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("deleteApplicant: " + e.getMessage()); }
    }

    public void saveLicenseType(LicenseType lt) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO license_types(id,name,icon_material,price,valid_duration_millis,required_level)
                VALUES(?,?,?,?,?,?)""")) {
            ps.setString(1, lt.getId().toString());
            ps.setString(2, lt.getName());
            ps.setString(3, lt.getIconMaterial().name());
            ps.setDouble(4, lt.getPrice());
            ps.setLong(5, lt.getValidDurationMillis());
            ps.setInt(6, lt.getRequiredLevel());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("saveLicenseType: " + e.getMessage()); }

        // Replace job templates wholesale.
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM job_templates WHERE license_type_id = ?")) {
            del.setString(1, lt.getId().toString());
            del.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("saveLicenseType (clear jobs): " + e.getMessage()); }

        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO job_templates(license_type_id,idx,material,base_amount,base_reward,job_duration_millis)
                VALUES(?,?,?,?,?,?)""")) {
            int i = 0;
            for (JobTemplate jt : lt.getJobs()) {
                ps.setString(1, lt.getId().toString());
                ps.setInt(2, i++);
                ps.setString(3, jt.getMaterial().name());
                ps.setInt(4, jt.getBaseAmount());
                ps.setDouble(5, jt.getBaseReward());
                ps.setLong(6, jt.getJobDurationMillis());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) { plugin.getLogger().warning("saveLicenseType (jobs): " + e.getMessage()); }

        licenseTypes.put(lt.getId(), lt);
    }

    public void deleteLicenseType(UUID id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM license_types WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("deleteLicenseType: " + e.getMessage()); }
        licenseTypes.remove(id);
    }

    public void saveLicense(License l) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO licenses(id,type_id,company_id,bought_at,expires_at) VALUES(?,?,?,?,?)")) {
            ps.setString(1, l.getId().toString());
            ps.setString(2, l.getTypeId().toString());
            ps.setString(3, l.getCompanyId().toString());
            ps.setLong(4, l.getBoughtAt());
            ps.setLong(5, l.getExpiresAt());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("saveLicense: " + e.getMessage()); }
    }

    public void deleteLicense(UUID id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM licenses WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("deleteLicense: " + e.getMessage()); }
    }
}
