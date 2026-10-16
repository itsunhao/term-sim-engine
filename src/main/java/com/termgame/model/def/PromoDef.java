package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 主动限时促销配置（promo action）。
 * 玩家支付 cost 现金，未来 durationMin 游戏分钟内客流 ×trafficMult。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromoDef(
        double cost,
        int durationMin,
        double trafficMult
) {
    public PromoDef {
        if (cost < 0) cost = 0;
        if (durationMin <= 0) durationMin = 240;
        if (trafficMult < 1.0) trafficMult = 1.5;
    }
}
