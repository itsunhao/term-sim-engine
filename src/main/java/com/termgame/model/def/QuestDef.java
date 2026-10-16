package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * NPC 委托订单玩法（gameplay.quest）。
 * 每日清晨按概率生成一个委托：某 NPC 想订指定商品 N 件，限期内交货拿报酬。
 * chance: 每日生成概率 0~1
 * days: 交货期限（游戏日，从生成当天起算）
 * qty_min / qty_max: 委托数量区间
 * reward_ratio: 报酬 = 商品零售价合计 × 比例（如 1.35 = 比零售多赚 35%）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestDef(
        double chance,
        int days,
        int qtyMin,
        int qtyMax,
        double rewardRatio
) {
    public QuestDef {
        if (chance <= 0) chance = 0.5;
        if (days <= 0) days = 2;
        if (qtyMin <= 0) qtyMin = 5;
        if (qtyMax < qtyMin) qtyMax = qtyMin;
        if (rewardRatio <= 0) rewardRatio = 1.3;
    }
}
