package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 随机事件定义。
 * effects:
 *  - cash_delta_min / cash_delta_max: 立即现金变动（可负）
 *  - reputation_delta: 立即声誉变动
 *  - foot_traffic_flat: 当日客流量 +N（可为负）
 *  - foot_traffic_mod / buff_days: 客流量倍率 buff（持续 buff_days 天，含当天）
 *  - sales_mod / buff_days: 营业额倍率 buff
 *  - restock_discount / buff_days: 进货折扣 buff（0.7 = 7折）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventDef(
        String id,
        String name,
        String icon,
        double chance,               // 每日触发概率 0-1
        String flavor,               // 事件描述
        double cashDeltaMin,
        double cashDeltaMax,
        int reputationDelta,
        int footTrafficFlat,
        double footTrafficMod,
        double salesMod,
        double restockDiscount,
        int buffDays
) {
    public EventDef {
        if (chance < 0 || chance > 1) chance = 0.05;
        if (footTrafficMod <= 0) footTrafficMod = 1.0;
        if (salesMod <= 0) salesMod = 1.0;
        if (restockDiscount <= 0) restockDiscount = 1.0;
        if (buffDays < 0) buffDays = 0;
    }
}
