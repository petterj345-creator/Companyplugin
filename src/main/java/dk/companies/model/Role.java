package dk.companies.model;

public enum Role {
    OWNER,
    MANAGER,
    WORKER;

    public boolean canManageWorkers() {
        return this == OWNER || this == MANAGER;
    }

    public boolean canWithdraw() {
        return this == OWNER || this == MANAGER;
    }

    public boolean canBuyLicense() {
        return this == OWNER || this == MANAGER;
    }

    public boolean canDisband() {
        return this == OWNER;
    }
}
