package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 商品定义。
 * unlockUpgradeId 非空时，需完成对应装修后才可进货/售卖。
 * seasonDemand / weatherDemand 为 季节id/天气id -> 需求倍率。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDef(
        String id,
        String name,
        String category,
        String icon,
        double wholesalePrice,      // 批发进价
        double baseRetailPrice,     // 建议零售价（实际售价 = 建议价 × 定价倍率）
        int popularity,             // 人气 1-100
        int shelfLifeDays,          // 保质期（天），到期自动丢弃
        String unlockUpgradeId,     // 需要解锁的装修 id，null 表示开局可用
        Map<String, Double> seasonDemand,
        Map<String, Double> weatherDemand
) {
    public ProductDef {
        if (shelfLifeDays <= 0) shelfLifeDays = 60;
        if (popularity <= 0) popularity = 50;
    }
}
