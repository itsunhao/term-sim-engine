package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 外卖策略配置（gameplay.trade.delivery 段）—— 饭馆等餐饮型店铺。
 * 与外卖平台合作：付开通费后每日自动接单（不占堂食客流，但占用厨房产能），
 * 平台抽成；每单有小概率获得好评/差评影响声誉。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryDef(
        boolean enabled,
        double setupCost,
        int ordersMin,
        int ordersMax,
        double priceRatio,
        double trafficPenalty,
        double reviewChance
) {
    public DeliveryDef {
        if (setupCost < 0) setupCost = 0;
        if (ordersMin <= 0) ordersMin = 5;
        if (ordersMax < ordersMin) ordersMax = ordersMin;
        if (priceRatio <= 0 || priceRatio > 1) priceRatio = 0.8;
        if (trafficPenalty < 0 || trafficPenalty > 1) trafficPenalty = 0.15;
        if (reviewChance <= 0) reviewChance = 0.1;
    }
}
