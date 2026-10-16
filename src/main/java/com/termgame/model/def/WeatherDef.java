package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 天气定义。seasonWeight 为 季节id -> 出现权重（0 表示该季节不会出现）。
 * demand 为 商品id -> 需求倍率。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeatherDef(
        String id,
        String name,
        String icon,
        Map<String, Integer> seasonWeight,
        double footTrafficModifier,
        String flavor,
        Map<String, Double> demand
) {
    public WeatherDef {
        if (footTrafficModifier <= 0) footTrafficModifier = 1.0;
    }
}
