package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 贷款产品定义。monthlyRate 为月利率（0.06 = 6%），按天计息（月利率/30）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoanDef(
        String id,
        String name,
        String icon,
        double maxAmount,
        double monthlyRate,
        String description
) {}
