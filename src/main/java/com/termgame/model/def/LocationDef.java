package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 地图地产（gameplay.map.locations[]）。
 * 不同地段客流量加成与房产价位不同；可申请房屋贷款购买并迁移店铺。
 * traffic_bonus: 客流量乘数（1.25 = 客流 +25%）
 * price: 房产总价
 * down_payment_ratio: 首付比例（0.4 = 首付 40%，其余由房屋贷款分期支付）
 * unlock_reputation: 购买所需最低声誉（0 = 无要求）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationDef(
        String id,
        String name,
        String icon,
        String desc,
        double trafficBonus,
        double price,
        double downPaymentRatio,
        int unlockReputation
) {
    public LocationDef {
        if (id == null || id.isBlank()) id = "loc_" + System.identityHashCode(this);
        if (name == null || name.isBlank()) name = id;
        if (icon == null || icon.isBlank()) icon = "🏪";
        if (trafficBonus <= 0) trafficBonus = 1.0;
        if (price < 0) price = 0;
        if (price <= 0) {
            downPaymentRatio = 0;   // 初始地产：无需首付/房贷
        } else if (downPaymentRatio <= 0) {
            downPaymentRatio = 0.4;
        } else if (downPaymentRatio > 1) {
            downPaymentRatio = 1.0;
        }
        if (unlockReputation < 0) unlockReputation = 0;
    }
}
