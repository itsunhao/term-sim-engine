package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 街区竞争对手状态（v1.10.0）。
 * heat: 经营热度 0~100（越高抢客越凶；≤15 连续 3 天歇业）
 * lowHeatDays: 热度 ≤15 的连续天数（≥3 触发歇业）
 * closed: 是否已歇业
 * closedSinceMin: 歇业发生的游戏分钟（用于 90 天后再开张）
 * spawnedSinceMin: 登场游戏分钟（登场初期 3 天热度减半，给玩家适应期）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompetitorState {
    private String id;
    private String name;
    private String icon;
    private double strength = 1.0;
    private double heat = 60;
    private int lowHeatDays = 0;
    private boolean closed = false;
    private long closedSinceMin = 0;
    private long spawnedSinceMin = 0;

    public CompetitorState() {
    }

    public CompetitorState(String id, String name, String icon, double strength, long spawnedSinceMin) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.strength = strength;
        this.heat = 60;
        this.spawnedSinceMin = spawnedSinceMin;
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

    public double getStrength() {
        return strength;
    }

    public void setStrength(double strength) {
        this.strength = strength;
    }

    public double getHeat() {
        return heat;
    }

    public void setHeat(double heat) {
        this.heat = heat;
    }

    public int getLowHeatDays() {
        return lowHeatDays;
    }

    public void setLowHeatDays(int lowHeatDays) {
        this.lowHeatDays = lowHeatDays;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public long getClosedSinceMin() {
        return closedSinceMin;
    }

    public void setClosedSinceMin(long closedSinceMin) {
        this.closedSinceMin = closedSinceMin;
    }

    public long getSpawnedSinceMin() {
        return spawnedSinceMin;
    }

    public void setSpawnedSinceMin(long spawnedSinceMin) {
        this.spawnedSinceMin = spawnedSinceMin;
    }
}
