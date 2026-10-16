package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 员工培养晋升配置（v1.11.0，挂在 gameplay.staff.career）。
 * enabled: 是否启用培养玩法
 * expPerDay: 每日工作经验（天）
 * expPerAction: 每次成功补货/进货/揽客额外经验
 * levelThreshold: 每级所需经验（第 1 级满 threshold 升 2 级，2 级满升 3 级，封顶 3 级）
 * promoteCostMult: 晋升费用 = 当前月薪 × 该倍率
 * redpacketCost: 发红包费用（元），忠诚 +10（节日期间效果 ×2）
 * loyaltyPerPay: 发薪成功忠诚度（+）
 * loyaltyPerRaise / loyaltyPerCut: 涨薪/降薪忠诚度变化
 * loyaltyPromoteCost: 晋升消耗的忠诚度
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CareerDef(
        boolean enabled,
        int expPerDay,
        int expPerAction,
        int levelThreshold,
        double promoteCostMult,
        double redpacketCost,
        double loyaltyPerPay,
        double loyaltyPerRaise,
        double loyaltyPerCut,
        double loyaltyPromoteCost
) {
    public CareerDef {
        if (expPerDay <= 0) expPerDay = 5;
        if (expPerAction < 0) expPerAction = 1;
        if (levelThreshold <= 0) levelThreshold = 50;
        if (promoteCostMult < 1) promoteCostMult = 3;
        if (redpacketCost < 0) redpacketCost = 200;
        if (loyaltyPerPay == 0) loyaltyPerPay = 5;
        if (loyaltyPerRaise == 0) loyaltyPerRaise = 8;
        if (loyaltyPerCut == 0) loyaltyPerCut = -10;
        if (loyaltyPromoteCost == 0) loyaltyPromoteCost = 10;
    }

    public static final int MAX_LEVEL = 3;

    public int levelOf(int exp) {
        int lvl = 1 + exp / levelThreshold;
        return Math.min(MAX_LEVEL, lvl);
    }

    /** 升级所需总经验（到下一级） */
    public int expForNext(int exp) {
        int lvl = levelOf(exp);
        if (lvl >= MAX_LEVEL) return -1;
        return lvl * levelThreshold;
    }
}
