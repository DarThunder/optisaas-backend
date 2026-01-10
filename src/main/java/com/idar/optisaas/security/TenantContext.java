package com.idar.optisaas.security;

public class TenantContext {
    private static final ThreadLocal<Long> CURRENT_BRANCH = new ThreadLocal<>();

    public static void setCurrentBranch(Long branchId) {
        CURRENT_BRANCH.set(branchId);
    }

    public static Long getCurrentBranch() {
        return CURRENT_BRANCH.get();
    }

    public static void clear() {
        CURRENT_BRANCH.remove();
    }
}