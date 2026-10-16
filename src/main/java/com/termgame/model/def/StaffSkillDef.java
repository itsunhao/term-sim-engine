package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 员工技能定义（v1.11.0，挂在 StaffDef.skills）。
 * 晋升时从角色技能池选择其一（pick_skill 命令/前端弹窗）。
 * params 支持的效果键：
 *   stock_bonus:     补货/自动进货数量 +N 件
 *   discount_bonus:  进货再让价 +X（如 0.03 = 进价再便宜 3%）
 *   price_bonus:     客单价 +X（如 0.02 = 顾客愿意多花 2%）
 *   traffic_bonus:   客流 +X（如 0.03 = 客流 +3%）
 *   suppress_bonus:  事件抑制强度额外 +X（加到角色 suppress_strength）
 *   delivery_bonus:  外卖每日单量 +N
 *   review_bonus:    外卖好评概率 +X（如 0.1）
 *   efficiency_bonus: 全员效率 +X（店长光环类技能，如 0.03）
 *   auto_buy_bonus:  清晨自动进货数量 +N
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StaffSkillDef(
        String id,
        String name,
        String icon,
        String desc,
        Map<String, Double> params
) {
    public StaffSkillDef {
        if (name == null || name.isBlank()) name = id;
        if (icon == null || icon.isBlank()) icon = "⭐";
        if (desc == null) desc = "";
        if (params == null) params = Map.of();
    }

    public double p(String key) {
        return params.getOrDefault(key, 0.0);
    }
}
