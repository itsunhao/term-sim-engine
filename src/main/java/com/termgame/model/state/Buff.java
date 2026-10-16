package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 临时 buff（来自随机事件）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Buff {

    public enum Type {
        FOOT_TRAFFIC_MOD,   // 客流量倍率
        FOOT_TRAFFIC_FLAT,  // 客流量 +N
        SALES_MOD,          // 营业额倍率
        RESTOCK_DISCOUNT,   // 进货折扣（0.7 = 7 折）
        REPUTATION_DAY      // 每日声誉变动
    }

    private Type type;
    private double value;
    private int daysRemaining;

    public Buff() {
    }

    public Buff(Type type, double value, int daysRemaining) {
        this.type = type;
        this.value = value;
        this.daysRemaining = daysRemaining;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public int getDaysRemaining() {
        return daysRemaining;
    }

    public void setDaysRemaining(int daysRemaining) {
        this.daysRemaining = daysRemaining;
    }
}
