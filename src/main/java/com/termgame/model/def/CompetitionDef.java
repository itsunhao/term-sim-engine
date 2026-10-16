package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 街区竞争对手系统配置（gameplay.competition 段，v1.10.0）。
 * enabled: 是否启用竞争玩法
 * thresholdReputation: 声望达到该值后街区出现第一位竞争对手
 * maxCompetitors: 同时最多活跃的竞争对手数
 * pool: 对手池（按声望增长逐个登场）
 * adCost: 营销战费用（元）
 * charmCost: 口碑攻势费用（元，需一位街坊好感 ≥ 60 支持）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetitionDef(
        boolean enabled,
        int thresholdReputation,
        int maxCompetitors,
        List<CompetitorPoolDef> pool,
        double adCost,
        double charmCost
) {
    public CompetitionDef {
        if (thresholdReputation < 0) thresholdReputation = 40;
        if (maxCompetitors < 1) maxCompetitors = 1;
        if (pool == null) pool = List.of();
        if (adCost <= 0) adCost = 300;
        if (charmCost <= 0) charmCost = 200;
    }
}
