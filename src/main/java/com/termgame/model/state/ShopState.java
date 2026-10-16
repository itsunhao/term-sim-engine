package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单家店铺的完整经营数据（v1.9.0 分店系统）。
 *
 * 每家店独立持有：库存、定价、装修（升级）、等级、雇员、当日经营数据、选址（客流量加成）。
 * 金钱（现金 / 存款 / 贷款 / 理财）为全局单一账户，不在此处。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopState {

    /** 店铺唯一 id："main" 为主店，其余为 "branch_1"、"branch_2"… */
    private String shopId;
    /** 店铺名称（主店取游戏名；分店可自定义） */
    private String name;
    /** 店铺图标 */
    private String icon;
    /** 地图位置 id（决定客流量加成） */
    private String locationId;
    /** 开店时的全局游戏分钟 */
    private long openedAtMin;

    private int shopLevel = 1;
    private List<String> upgradesOwned = new ArrayList<>();
    private Map<String, InventorySlot> inventory = new LinkedHashMap<>();
    private Map<String, Double> priceMultiplier = new LinkedHashMap<>();
    private Map<String, StaffState> staff = new LinkedHashMap<>();
    private int payrollDebtDays;

    private double dailyFootTraffic;
    private double todayRevenue;
    private double todayCost;
    private int todayItems;
    private int todayCustomers;
    private Map<String, Integer> todayPeriodFlow = new LinkedHashMap<>();
    private List<String> todayEvents = new ArrayList<>();
    private List<String> todayNotices = new ArrayList<>();

    public ShopState() {
    }

    public ShopState(String shopId, String name, String icon, String locationId, long openedAtMin) {
        this.shopId = shopId;
        this.name = name;
        this.icon = icon;
        this.locationId = locationId;
        this.openedAtMin = openedAtMin;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public long getOpenedAtMin() {
        return openedAtMin;
    }

    public void setOpenedAtMin(long openedAtMin) {
        this.openedAtMin = openedAtMin;
    }

    public int getShopLevel() {
        return shopLevel;
    }

    public void setShopLevel(int shopLevel) {
        this.shopLevel = shopLevel;
    }

    public List<String> getUpgradesOwned() {
        return upgradesOwned;
    }

    public void setUpgradesOwned(List<String> upgradesOwned) {
        this.upgradesOwned = upgradesOwned;
    }

    public Map<String, InventorySlot> getInventory() {
        return inventory;
    }

    public void setInventory(Map<String, InventorySlot> inventory) {
        this.inventory = inventory;
    }

    public Map<String, Double> getPriceMultiplier() {
        return priceMultiplier;
    }

    public void setPriceMultiplier(Map<String, Double> priceMultiplier) {
        this.priceMultiplier = priceMultiplier;
    }

    public Map<String, StaffState> getStaff() {
        return staff;
    }

    public void setStaff(Map<String, StaffState> staff) {
        this.staff = staff;
    }

    public int getPayrollDebtDays() {
        return payrollDebtDays;
    }

    public void setPayrollDebtDays(int payrollDebtDays) {
        this.payrollDebtDays = payrollDebtDays;
    }

    public double getDailyFootTraffic() {
        return dailyFootTraffic;
    }

    public void setDailyFootTraffic(double dailyFootTraffic) {
        this.dailyFootTraffic = dailyFootTraffic;
    }

    public double getTodayRevenue() {
        return todayRevenue;
    }

    public void setTodayRevenue(double todayRevenue) {
        this.todayRevenue = todayRevenue;
    }

    public double getTodayCost() {
        return todayCost;
    }

    public void setTodayCost(double todayCost) {
        this.todayCost = todayCost;
    }

    public int getTodayItems() {
        return todayItems;
    }

    public void setTodayItems(int todayItems) {
        this.todayItems = todayItems;
    }

    public int getTodayCustomers() {
        return todayCustomers;
    }

    public void setTodayCustomers(int todayCustomers) {
        this.todayCustomers = todayCustomers;
    }

    public Map<String, Integer> getTodayPeriodFlow() {
        return todayPeriodFlow;
    }

    public void setTodayPeriodFlow(Map<String, Integer> todayPeriodFlow) {
        this.todayPeriodFlow = todayPeriodFlow;
    }

    public List<String> getTodayEvents() {
        return todayEvents;
    }

    public void setTodayEvents(List<String> todayEvents) {
        this.todayEvents = todayEvents;
    }

    public List<String> getTodayNotices() {
        return todayNotices;
    }

    public void setTodayNotices(List<String> todayNotices) {
        this.todayNotices = todayNotices;
    }

    /** 今日毛利 */
    public double todayProfit() {
        return todayRevenue - todayCost;
    }
}
