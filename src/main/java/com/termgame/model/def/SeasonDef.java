package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 季节定义。demand 为 商品id -> 需求倍率。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeasonDef(
        String id,
        String name,
        String icon,
        double footTrafficModifier,
        String description,
        Map<String, Double> demand
) {
    public SeasonDef {
        if (footTrafficModifier <= 0) footTrafficModifier = 1.0;
    }
}
