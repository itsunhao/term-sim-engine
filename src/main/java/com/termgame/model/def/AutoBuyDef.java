package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 自动进货策略（role=auto_buy 员工）：每日清晨自动补货到目标数量。
 * target_qty: 每种在售商品的目标库存
 * max_spend_ratio: 单次补货花费上限 = 当前现金 × 比例
 * min_cash_keep: 保底留存现金（补货后现金不得低于此值）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AutoBuyDef(
        int targetQty,
        double maxSpendRatio,
        double minCashKeep
) {
    public AutoBuyDef {
        if (maxSpendRatio <= 0) maxSpendRatio = 0.3;
    }
}
