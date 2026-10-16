package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 装修（店铺升级）定义。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpgradeDef(
        String id,
        String name,
        String icon,
        int level,                  // 等级（必须从 1 递增）
        double cost,
        String description,
        double footTrafficBonus,    // 客流量加成（百分比，0.2 = +20%）
        int maxCustomersBonus,      // 单日客流上限加成
        int reputationBonus,        // 装修时立即获得的声誉
        List<String> unlockCategories, // 解锁的商品分类
        double autoReputationPerDay // 每日自动声誉增长
) {
    public UpgradeDef {
        if (level <= 0) level = 1;
    }
}
