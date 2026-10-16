package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 单个节日定义（v1.11.0）。
 * id / name / icon: 节日标识与展示
 * season: 触发的季节（spring / summer / autumn / winter）
 * day: 季节内第几天触发（1 起）
 * durationMin: 持续时长（游戏分钟，默认 1440 = 1 天）
 * demand: 节日商品需求倍率（商品 id → 倍率，节日期间该商品更抢手）
 * setupCap: 筹备金投入上限（元）
 * rewardMult: 筹备回报倍率（节日战报奖金 = 筹备投入 × rewardMult；筹备满额另按节日营业额抽成）
 * eventRate: 节日期间每日随机小事件的概率（0~1）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FestivalItemDef(
        String id,
        String name,
        String icon,
        String season,
        int day,
        int durationMin,
        Map<String, Double> demand,
        double setupCap,
        double rewardMult,
        double eventRate
) {
    public FestivalItemDef {
        if (name == null || name.isBlank()) name = id;
        if (icon == null || icon.isBlank()) icon = "🎉";
        if (season == null || season.isBlank()) season = "spring";
        if (day <= 0) day = 1;
        if (durationMin <= 0) durationMin = 1440;
        if (demand == null) demand = Map.of();
        if (setupCap < 0) setupCap = 0;
        if (rewardMult < 0) rewardMult = 1.0;
        if (eventRate < 0) eventRate = 0.1;
    }
}
