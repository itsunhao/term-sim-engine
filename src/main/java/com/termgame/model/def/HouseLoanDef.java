package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 房屋贷款参数（gameplay.map.loan）。
 * 购买新地产时：首付外的剩余房款走房屋贷款，按月供分期偿还。
 * months: 还款期数（每 30 游戏天一期）
 * monthly_rate: 月利率（0.02 = 2%，按剩余本金计息）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HouseLoanDef(
        int months,
        double monthlyRate
) {
    public HouseLoanDef {
        if (months <= 0) months = 12;
        if (monthlyRate < 0) monthlyRate = 0.02;
    }
}
