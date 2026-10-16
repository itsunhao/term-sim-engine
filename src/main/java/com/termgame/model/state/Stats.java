package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 累计经营统计。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stats {

    private double totalRevenue;
    private double totalCost;
    private double totalProfit;
    private int customersServed;
    private int itemsSold;
    private int daysPlayed;
    private int expiredItems;
    private double bestDayRevenue;
    /** 累计借入金额（成就判定：还清贷款需曾借款） */
    private double totalBorrowed;
    /** 累计庆祝的节日次数（v1.11.0） */
    private int festivalCount;
    /** 累计节日战报奖金（v1.11.0，成就用） */
    private double festivalBonusEarned;

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }

    public double getTotalProfit() {
        return totalProfit;
    }

    public void setTotalProfit(double totalProfit) {
        this.totalProfit = totalProfit;
    }

    public int getCustomersServed() {
        return customersServed;
    }

    public void setCustomersServed(int customersServed) {
        this.customersServed = customersServed;
    }

    public int getItemsSold() {
        return itemsSold;
    }

    public void setItemsSold(int itemsSold) {
        this.itemsSold = itemsSold;
    }

    public int getDaysPlayed() {
        return daysPlayed;
    }

    public void setDaysPlayed(int daysPlayed) {
        this.daysPlayed = daysPlayed;
    }

    public int getExpiredItems() {
        return expiredItems;
    }

    public void setExpiredItems(int expiredItems) {
        this.expiredItems = expiredItems;
    }

    public double getBestDayRevenue() {
        return bestDayRevenue;
    }

    public void setBestDayRevenue(double bestDayRevenue) {
        this.bestDayRevenue = bestDayRevenue;
    }

    public double getTotalBorrowed() {
        return totalBorrowed;
    }

    public void setTotalBorrowed(double totalBorrowed) {
        this.totalBorrowed = totalBorrowed;
    }

    public int getFestivalCount() {
        return festivalCount;
    }

    public void setFestivalCount(int festivalCount) {
        this.festivalCount = festivalCount;
    }

    public double getFestivalBonusEarned() {
        return festivalBonusEarned;
    }

    public void setFestivalBonusEarned(double festivalBonusEarned) {
        this.festivalBonusEarned = festivalBonusEarned;
    }
}
