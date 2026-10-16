package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 胜利条件：byYear 年末之前（含）净资产达到 target_net_worth 即胜利。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WinCondition(
        String type,             // "networth"
        double targetNetWorth,
        int byYear
) {}
