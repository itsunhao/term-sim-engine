package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 成就定义。type 决定判定口径，value 为阈值：
 *  - first_sale: 累计售出 ≥ value 件（value 通常 1）
 *  - revenue_day: 单日营业额 ≥ value（元）
 *  - profit_streak: 连续盈利 ≥ value 天
 *  - favor_any: 任一 NPC 好感 ≥ value
 *  - reputation: 声誉 ≥ value
 *  - upgrades: 完成装修 ≥ value 次
 *  - debt_clear: 还清全部贷款（value 忽略）
 *  - net_worth: 净资产 ≥ value（元）
 *  - days: 累计经营 ≥ value 天
 *  - hot_count: 累计经历爆款 ≥ value 次
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AchievementDef(
        String id,
        String icon,
        String name,
        String desc,
        String type,
        double value
) {
    public AchievementDef {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("成就缺少 id");
        }
        if (icon == null || icon.isBlank()) icon = "🏆";
        if (name == null || name.isBlank()) name = id;
        if (desc == null || desc.isBlank()) desc = "";
        if (type == null || type.isBlank()) type = "revenue_day";
        if (value < 0) value = 0;
    }
}
