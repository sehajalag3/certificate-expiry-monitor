package com.example.certmonitor.service;

public final class DashboardCounts {

    private final long total;
    private final long valid;
    private final long expiringSoon;
    private final long expired;

    public DashboardCounts(long total, long valid, long expiringSoon, long expired) {
        this.total = total;
        this.valid = valid;
        this.expiringSoon = expiringSoon;
        this.expired = expired;
    }

    public long getTotal() {
        return total;
    }

    public long getValid() {
        return valid;
    }

    public long getExpiringSoon() {
        return expiringSoon;
    }

    public long getExpired() {
        return expired;
    }

    public int getValidPercent() {
        return percent(valid);
    }

    public int getExpiringSoonPercent() {
        return percent(expiringSoon);
    }

    public int getExpiredPercent() {
        return percent(expired);
    }

    private int percent(long value) {
        if (total == 0) {
            return 0;
        }
        return (int) Math.round(value * 100.0 / total);
    }
}
