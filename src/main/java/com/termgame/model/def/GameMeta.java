package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 游戏全局参数。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GameMeta(
        String currency,        // 货币名称，如 "元"
        double startCash,       // 初始资金
        int startReputation,    // 初始声誉
        int maxReputation,      // 声誉上限
        int daysPerSeason,      // 每季天数
        String startSeason,     // 起始季节 id
        int baseFootTraffic,    // 基础客流量（人/天）
        double priceMin,        // 定价倍率下限
        double priceMax,        // 定价倍率上限
        double defaultPriceMultiplier, // 默认定价倍率
        double debtLimit,       // 负债上限（超过即面临破产）
        int loanGraceDays,      // 负债超限宽限天数
        double bankAnnualRate   // 银行存款年化利率（游戏年 = 4 季 × daysPerSeason 天）
) {
    public GameMeta {
        if (currency == null || currency.isBlank()) currency = "元";
        if (startSeason == null || startSeason.isBlank()) startSeason = "spring";
        if (daysPerSeason <= 0) daysPerSeason = 30;
        if (maxReputation <= 0) maxReputation = 100;
        if (debtLimit <= 0) debtLimit = 30000;
        if (loanGraceDays <= 0) loanGraceDays = 7;
        if (defaultPriceMultiplier <= 0) defaultPriceMultiplier = 1.0;
        if (bankAnnualRate < 0) bankAnnualRate = 0.12;
    }
}
