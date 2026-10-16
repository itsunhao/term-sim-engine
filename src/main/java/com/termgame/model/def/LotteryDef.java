package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 幸运彩票玩法（gameplay.lottery）。
 * 每日一张次日开奖（cost/win_chance/prize_min/prize_max）；
 * 刮刮乐（scratch_*）即时开奖、不限购买次数。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LotteryDef(
        double cost,
        double winChance,
        int prizeMin,
        int prizeMax,
        double scratchCost,
        double scratchWinChance,
        int scratchPrizeMin,
        int scratchPrizeMax
) {
    public LotteryDef {
        if (cost <= 0) cost = 10;
        if (winChance <= 0) winChance = 0.08;
        if (prizeMin <= 0) prizeMin = 50;
        if (prizeMax < prizeMin) prizeMax = prizeMin;
        if (scratchCost <= 0) scratchCost = 5;
        if (scratchWinChance <= 0) scratchWinChance = 0.25;
        if (scratchPrizeMin <= 0) scratchPrizeMin = 2;
        if (scratchPrizeMax < scratchPrizeMin) scratchPrizeMax = scratchPrizeMin;
    }

    /** 兼容旧版：仅 4 参（普通彩票）时补默认刮刮乐 */
    public LotteryDef(double cost, double winChance, int prizeMin, int prizeMax) {
        this(cost, winChance, prizeMin, prizeMax, 0, 0, 0, 0);
    }
}
