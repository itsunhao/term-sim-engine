package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 进行中的节日状态（v1.11.0，state.festival，null = 无节日）。
 * id/name/icon: 节日标识
 * startedMin / endMin: 开始与结束的游戏分钟
 * setup: 已投入筹备金（元）
 * festivalRevenue: 节日期间累计营业额
 * settled: 是否已结算
 * year: 触发的游戏年份（同一年不重复触发）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FestivalState {

    private String id;
    private String name;
    private String icon;
    private long startedMin;
    private long endMin;
    private double setup;
    private double festivalRevenue;
    private boolean settled;
    private int year;

    public FestivalState() {
    }

    public FestivalState(String id, String name, String icon, long startedMin, long endMin, int year) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.startedMin = startedMin;
        this.endMin = endMin;
        this.year = year;
    }

    /** 剩余分钟（负 = 已结束） */
    public long remainMin(long now) {
        return endMin - now;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public long getStartedMin() {
        return startedMin;
    }

    public void setStartedMin(long startedMin) {
        this.startedMin = startedMin;
    }

    public long getEndMin() {
        return endMin;
    }

    public void setEndMin(long endMin) {
        this.endMin = endMin;
    }

    public double getSetup() {
        return setup;
    }

    public void setSetup(double setup) {
        this.setup = setup;
    }

    public double getFestivalRevenue() {
        return festivalRevenue;
    }

    public void setFestivalRevenue(double festivalRevenue) {
        this.festivalRevenue = festivalRevenue;
    }

    public boolean isSettled() {
        return settled;
    }

    public void setSettled(boolean settled) {
        this.settled = settled;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }
}
