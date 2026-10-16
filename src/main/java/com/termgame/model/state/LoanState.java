package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 一笔贷款。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoanState {

    private String loanDefId;
    private String name;
    private double principal;    // 剩余本金
    private double monthlyRate;  // 月利率
    private int dayTaken;        // 借款时的全局天数

    public LoanState() {
    }

    public LoanState(String loanDefId, String name, double principal, double monthlyRate, int dayTaken) {
        this.loanDefId = loanDefId;
        this.name = name;
        this.principal = principal;
        this.monthlyRate = monthlyRate;
        this.dayTaken = dayTaken;
    }

    public String getLoanDefId() {
        return loanDefId;
    }

    public void setLoanDefId(String loanDefId) {
        this.loanDefId = loanDefId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrincipal() {
        return principal;
    }

    public void setPrincipal(double principal) {
        this.principal = principal;
    }

    public double getMonthlyRate() {
        return monthlyRate;
    }

    public void setMonthlyRate(double monthlyRate) {
        this.monthlyRate = monthlyRate;
    }

    public int getDayTaken() {
        return dayTaken;
    }

    public void setDayTaken(int dayTaken) {
        this.dayTaken = dayTaken;
    }
}
