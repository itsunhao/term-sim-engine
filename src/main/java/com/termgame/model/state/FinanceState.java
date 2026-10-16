package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 理财产品持仓（一笔 = 一个独立订单）。
 * buy_day: 购买日（第 N 天），due_day: 到期日（第 M 天，到期清晨自动兑付）
 * amount: 本金；annual_rate / period_days 用于结算利息（年化 × 期限 / 360 天）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinanceState {
    private String productId;
    private double amount;
    private int buyDay;
    private int dueDay;
    private double annualRate;
    private int periodDays;

    public FinanceState() {
    }

    public FinanceState(String productId, double amount, int buyDay, int dueDay, double annualRate, int periodDays) {
        this.productId = productId;
        this.amount = amount;
        this.buyDay = buyDay;
        this.dueDay = dueDay;
        this.annualRate = annualRate;
        this.periodDays = periodDays;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public int getBuyDay() { return buyDay; }
    public void setBuyDay(int buyDay) { this.buyDay = buyDay; }
    public int getDueDay() { return dueDay; }
    public void setDueDay(int dueDay) { this.dueDay = dueDay; }
    public double getAnnualRate() { return annualRate; }
    public void setAnnualRate(double annualRate) { this.annualRate = annualRate; }
    public int getPeriodDays() { return periodDays; }
    public void setPeriodDays(int periodDays) { this.periodDays = periodDays; }
}
