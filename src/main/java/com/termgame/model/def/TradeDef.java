package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 贸易策略配置（gameplay.trade 段）：批发（wholesale）与外卖（delivery）二选一，
 * 按店铺类型配置其一。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TradeDef(
        WholesaleDef wholesale,
        DeliveryDef delivery
) {
    public TradeDef {
        if (wholesale == null) wholesale = new WholesaleDef(false, 50, 0.75, 60, "批发商");
        if (delivery == null) delivery = new DeliveryDef(false, 0, 5, 5, 0.8, 0.15, 0.1);
    }
}
