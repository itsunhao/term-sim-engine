package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 理财产品定义（gameplay.finance 段）。
 * period_days: 锁定期限（游戏日，到期清晨自动兑付本金+利息）
 * annual_rate: 年化收益率（0.15 = 15%，按 360 天计息）
 * min_amount: 起购金额（现金）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceDef(
        String id,
        String name,
        String icon,
        int periodDays,
        double annualRate,
        double minAmount,
        String desc
) {
    public FinanceDef {
        if (periodDays <= 0) periodDays = 30;
        if (annualRate <= 0) annualRate = 0.12;
        if (minAmount <= 0) minAmount = 100;
        if (desc == null) desc = "";
    }
}
