package com.termgame.model.state;

/**
 * 房屋贷款（进行中）。
 * locationId: 贷款购买的地产
 * principal: 剩余本金
 * monthlyPayment: 固定月供（等额本息近似：含息总额均摊到 months 期）
 * monthsLeft: 剩余期数（每 30 游戏天一期）
 * overdueDays: 连续逾期期数（逾期扣声誉，欠款继续计息）
 */
public class HouseLoanState {

    private String locationId;
    private double principal;
    private double monthlyPayment;
    private int monthsLeft;
    private int overdueDays;

    public HouseLoanState() {
    }

    public HouseLoanState(String locationId, double principal, double monthlyPayment, int monthsLeft) {
        this.locationId = locationId;
        this.principal = principal;
        this.monthlyPayment = monthlyPayment;
        this.monthsLeft = monthsLeft;
        this.overdueDays = 0;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public double getPrincipal() {
        return principal;
    }

    public void setPrincipal(double principal) {
        this.principal = principal;
    }

    public double getMonthlyPayment() {
        return monthlyPayment;
    }

    public void setMonthlyPayment(double monthlyPayment) {
        this.monthlyPayment = monthlyPayment;
    }

    public int getMonthsLeft() {
        return monthsLeft;
    }

    public void setMonthsLeft(int monthsLeft) {
        this.monthsLeft = monthsLeft;
    }

    public int getOverdueDays() {
        return overdueDays;
    }

    public void setOverdueDays(int overdueDays) {
        this.overdueDays = overdueDays;
    }
}
