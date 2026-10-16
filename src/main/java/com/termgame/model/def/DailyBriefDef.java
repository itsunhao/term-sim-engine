package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 每日晨报条目。效果为当日 buff（可与天气/事件叠加）。
 * effects:
 *  - foot_traffic_mod: 客流倍率（1.2 = +20%）
 *  - sales_mod: 营业额倍率（1.15 = +15%）
 *  - restock_discount: 进货折扣（0.9 = 9 折）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyBriefDef(
        String id,
        String icon,
        String title,
        String flavor,
        double footTrafficMod,
        double salesMod,
        double restockDiscount,
        double weight
) {
    public DailyBriefDef {
        if (id == null || id.isBlank()) id = "brief_" + System.nanoTime();
        if (icon == null || icon.isBlank()) icon = "📰";
        if (title == null || title.isBlank()) title = "今日晨报";
        if (flavor == null) flavor = "";
        if (footTrafficMod <= 0) footTrafficMod = 1.0;
        if (salesMod <= 0) salesMod = 1.0;
        if (restockDiscount <= 0) restockDiscount = 1.0;
        if (weight <= 0) weight = 1.0;
    }
}
