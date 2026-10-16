package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * NPC 定义。
 * period 为该 NPC 常出现的时段（morning / noon / evening / any）。
 * comments 为 好感档位id -> 台词列表；档位：cold / normal / regular / loyal。
 * special 为 特殊场景 -> 台词列表，场景：missing（喜欢的货缺货）/ renovated（装修后）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NpcDef(
        String id,
        String name,
        String icon,
        String title,
        String personality,
        double visitChance,         // 每日来访概率
        double priceSensitivity,    // 价格敏感度 0-1，越高越在意价格
        String period,              // morning / noon / evening / any
        List<String> favoriteCategories,
        List<String> favoriteProducts,
        double budget,              // 平均消费预算
        Map<String, List<String>> comments,
        Map<String, List<String>> special
) {
    public NpcDef {
        if (period == null || period.isBlank()) period = "any";
        if (visitChance <= 0) visitChance = 0.2;
        if (priceSensitivity < 0 || priceSensitivity > 1) priceSensitivity = 0.6;
    }
}
