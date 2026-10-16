package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 分店系统配置（gameplay.branching，v1.9.0）。
 * enabled: 是否开放开分店
 * max_shops: 最多同时经营的店铺总数（含主店）
 * base_open_cost: 开第 2 家店的基础费用（第 N 家 = base × growth^(N-1)，N 为已开店数）
 * cost_growth: 每多开一家店的费用增长倍率
 * require_reputation: 开分店所需最低个人声望
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BranchDef(
        boolean enabled,
        int maxShops,
        double baseOpenCost,
        double costGrowth,
        int requireReputation
) {
    public BranchDef {
        if (maxShops < 1) maxShops = 1;
        if (baseOpenCost <= 0) baseOpenCost = 2000;
        if (costGrowth <= 1.0) costGrowth = 1.6;
        if (requireReputation < 0) requireReputation = 0;
    }
}
