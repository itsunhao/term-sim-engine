package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 供应链供应商等级（gameplay.supply.tiers 段，v1.10.0）。
 * name: 等级名
 * reqPoints: 达到该等级所需的累计合作值（= 累计实付进货金额）
 * discount: 进货折扣（0.95 = 95 折）
 * creditLimit: 赊账额度上限（0 = 该等级不支持赊账）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SupplyTierDef(
        String name,
        int reqPoints,
        double discount,
        double creditLimit
) {
    public SupplyTierDef {
        if (reqPoints < 0) reqPoints = 0;
        if (discount <= 0 || discount > 1) discount = 1.0;
        if (creditLimit < 0) creditLimit = 0;
    }
}
