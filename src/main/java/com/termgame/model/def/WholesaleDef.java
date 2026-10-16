package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 批发策略配置（gameplay.trade.wholesale 段）—— 小卖部等零售型店铺。
 * 玩家可将库存按低于零售的比例批量卖给下游摊贩，快速回笼现金。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WholesaleDef(
        boolean enabled,
        int minQty,
        double priceRatio,
        long cooldownMin,
        String brokerName
) {
    public WholesaleDef {
        if (minQty <= 0) minQty = 50;
        if (priceRatio <= 0 || priceRatio > 1) priceRatio = 0.75;
        if (cooldownMin <= 0) cooldownMin = 60;
        if (brokerName == null) brokerName = "批发商";
    }
}
