package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 爆款热卖配置。每日打烊结算时按 chance 概率随机选中一件在售商品，
 * 未来 durationMin 游戏分钟内需求权重 ×demandMult。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HotItemDef(
        double chance,
        int durationMin,
        double demandMult
) {
    public HotItemDef {
        if (chance < 0 || chance > 1) chance = 0.3;
        if (durationMin <= 0) durationMin = 180;
        if (demandMult < 1.0) demandMult = 2.5;
    }
}
