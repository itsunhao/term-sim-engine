package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单日营业日报。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DayReport {

    private String dayLabel;        // 如 "第1年·春·第3天"
    private String weatherName;
    private String weatherIcon;
    private boolean closed;         // 是否休息

    private double footTraffic;     // 计算客流量
    private int customers;          // 实际进店顾客
    private int itemsSold;
    private double revenue;
    private double costOfGoods;
    private double profit;

    private Map<String, Integer> periodFlow = new LinkedHashMap<>(); // 早/午/晚 顾客数
    private List<String> expiries = new ArrayList<>();
    private List<String> events = new ArrayList<>();
    private List<NpcComment> npcComments = new ArrayList<>();
    private List<String> notices = new ArrayList<>();
    private double interestPaid;

    private double cashAfter;
    private double debtAfter;
    private double reputationAfter;
    private double netWorth;
    private boolean gameEnded;
    private String endType;
    private String endMessage;

    public String getDayLabel() {
        return dayLabel;
    }

    public void setDayLabel(String dayLabel) {
        this.dayLabel = dayLabel;
    }

    public String getWeatherName() {
        return weatherName;
    }

    public void setWeatherName(String weatherName) {
        this.weatherName = weatherName;
    }

    public String getWeatherIcon() {
        return weatherIcon;
    }

    public void setWeatherIcon(String weatherIcon) {
        this.weatherIcon = weatherIcon;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public double getFootTraffic() {
        return footTraffic;
    }

    public void setFootTraffic(double footTraffic) {
        this.footTraffic = footTraffic;
    }

    public int getCustomers() {
        return customers;
    }

    public void setCustomers(int customers) {
        this.customers = customers;
    }

    public int getItemsSold() {
        return itemsSold;
    }

    public void setItemsSold(int itemsSold) {
        this.itemsSold = itemsSold;
    }

    public double getRevenue() {
        return revenue;
    }

    public void setRevenue(double revenue) {
        this.revenue = revenue;
    }

    public double getCostOfGoods() {
        return costOfGoods;
    }

    public void setCostOfGoods(double costOfGoods) {
        this.costOfGoods = costOfGoods;
    }

    public double getProfit() {
        return profit;
    }

    public void setProfit(double profit) {
        this.profit = profit;
    }

    public Map<String, Integer> getPeriodFlow() {
        return periodFlow;
    }

    public void setPeriodFlow(Map<String, Integer> periodFlow) {
        this.periodFlow = periodFlow;
    }

    public List<String> getExpiries() {
        return expiries;
    }

    public void setExpiries(List<String> expiries) {
        this.expiries = expiries;
    }

    public List<String> getEvents() {
        return events;
    }

    public void setEvents(List<String> events) {
        this.events = events;
    }

    public List<NpcComment> getNpcComments() {
        return npcComments;
    }

    public void setNpcComments(List<NpcComment> npcComments) {
        this.npcComments = npcComments;
    }

    public List<String> getNotices() {
        return notices;
    }

    public void setNotices(List<String> notices) {
        this.notices = notices;
    }

    public double getInterestPaid() {
        return interestPaid;
    }

    public void setInterestPaid(double interestPaid) {
        this.interestPaid = interestPaid;
    }

    public double getCashAfter() {
        return cashAfter;
    }

    public void setCashAfter(double cashAfter) {
        this.cashAfter = cashAfter;
    }

    public double getDebtAfter() {
        return debtAfter;
    }

    public void setDebtAfter(double debtAfter) {
        this.debtAfter = debtAfter;
    }

    public double getReputationAfter() {
        return reputationAfter;
    }

    public void setReputationAfter(double reputationAfter) {
        this.reputationAfter = reputationAfter;
    }

    public double getNetWorth() {
        return netWorth;
    }

    public void setNetWorth(double netWorth) {
        this.netWorth = netWorth;
    }

    public boolean isGameEnded() {
        return gameEnded;
    }

    public void setGameEnded(boolean gameEnded) {
        this.gameEnded = gameEnded;
    }

    public String getEndType() {
        return endType;
    }

    public void setEndType(String endType) {
        this.endType = endType;
    }

    public String getEndMessage() {
        return endMessage;
    }

    public void setEndMessage(String endMessage) {
        this.endMessage = endMessage;
    }
}
