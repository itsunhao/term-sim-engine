package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 失败条件：负债超过 debt_limit 且持续超过 loan_grace_days 天即破产。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoseCondition(
        String type,             // "bankruptcy"
        double debtLimit,
        int graceDays
) {}
