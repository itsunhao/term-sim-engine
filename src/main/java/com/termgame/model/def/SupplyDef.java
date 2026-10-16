package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 供应链系统配置（gameplay.supply 段，v1.10.0）。
 * enabled: 是否启用供应链玩法
 * supplierName: 供应商名称（如 恒昌商贸行 / 鲜味源供应链）
 * tiers: 供应商等级列表（由低到高，按累计进货合作值升级）
 * exclusiveProducts: 最高等级解锁的独家商品 id 列表（高毛利新品）
 * creditTermMin: 赊账账期（游戏分钟；每 30 游戏天 = 43200 分钟一期）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SupplyDef(
        boolean enabled,
        String supplierName,
        List<SupplyTierDef> tiers,
        List<String> exclusiveProducts,
        int creditTermMin
) {
    public SupplyDef {
        if (supplierName == null) supplierName = "恒昌商贸行";
        if (tiers == null || tiers.isEmpty()) {
            tiers = List.of(
                    new SupplyTierDef("普通客户", 0, 1.0, 0),
                    new SupplyTierDef("稳定客户", 500, 0.98, 0),
                    new SupplyTierDef("金牌客户", 2000, 0.95, 1000),
                    new SupplyTierDef("战略伙伴", 6000, 0.88, 3000));
        }
        if (exclusiveProducts == null) exclusiveProducts = List.of();
        if (creditTermMin <= 0) creditTermMin = 43200;
    }

    /** 按累计合作值返回所处等级下标（0 起） */
    public int tierIndex(double points) {
        int idx = 0;
        for (int i = 0; i < tiers.size(); i++) {
            if (points >= tiers.get(i).reqPoints()) idx = i;
        }
        return idx;
    }

    public SupplyTierDef tier(int idx) {
        if (idx < 0) idx = 0;
        if (idx >= tiers.size()) idx = tiers.size() - 1;
        return tiers.get(idx);
    }

    /** 当前等级进货折扣 */
    public double discountAt(double points) {
        return tier(tierIndex(points)).discount();
    }

    /** 当前等级赊账额度（0 = 不支持赊账） */
    public double creditLimitAt(double points) {
        return tier(tierIndex(points)).creditLimit();
    }

    /** 是否达到最高等级（解锁独家商品） */
    public boolean isMaxTier(double points) {
        return points >= tiers.get(tiers.size() - 1).reqPoints();
    }
}
