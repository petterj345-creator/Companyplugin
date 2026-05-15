package dk.companies.economy;

import dk.companies.Companies;
import dk.companies.model.Company;

public final class CompanyLevels {

    private final Companies plugin;

    public CompanyLevels(Companies plugin) { this.plugin = plugin; }

    public int maxLevel() {
        return plugin.getConfig().getInt("levels.max-level", 50);
    }

    public double upgradeCost(int currentLevel) {
        double base = plugin.getConfig().getDouble("levels.base-upgrade-cost", 50000.0);
        double growth = plugin.getConfig().getDouble("levels.cost-growth", 1.35);
        return base * Math.pow(growth, Math.max(0, currentLevel - 1));
    }

    public double rewardMultiplier(Company c) {
        double step = plugin.getConfig().getDouble("levels.reward-multiplier-per-level", 0.05);
        return 1.0 + (c.getLevel() - 1) * step;
    }

    public boolean atMax(Company c) {
        return c.getLevel() >= maxLevel();
    }
}
