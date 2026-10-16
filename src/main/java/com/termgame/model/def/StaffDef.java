package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 可雇佣角色定义。
 * role: 语义标识（auto_buy / promoter / buyer / guard / runner / manager / cleaner ...）
 * salary: 月薪（每月发薪日按游戏月结算）
 * autoBuy: 自动进货策略（role=auto_buy 时使用）
 * suppressEvents: 被抑制的事件 id 列表（如 ["ev_thief"]，概率 ×(1 - suppress_strength×效率)）
 * params: 效果参数（效率 = 1 + (涨薪级 - 降薪级) × 0.15，clamp 0.55~1.75）
 *   - foot_traffic: 客流加成基准（如 0.06 = 客流 ×(1+6%×效率)）
 *   - buy_discount: 进货让价基准（如 0.08 = 进价 ×(1-8%×效率)，上限 0.2）
 *   - suppress_strength: 事件抑制强度（与 suppressEvents 配合）
 *   - delivery_orders: 外卖每日单量加成（如 2 = +2×效率 单）
 *   - review_bonus: 外卖好评概率加成（如 0.1 = 好评概率 +10%×效率）
 * skills: 晋升技能池（v1.11.0 员工培养，晋升时选择其一）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StaffDef(
        String id,
        String name,
        String icon,
        String role,
        double salary,
        String desc,
        AutoBuyDef autoBuy,
        List<String> suppressEvents,
        Map<String, Double> params,
        List<StaffSkillDef> skills
) {
    public StaffDef {
        if (suppressEvents == null) suppressEvents = List.of();
        if (params == null) params = Map.of();
        if (skills == null) skills = List.of();
        if (salary < 0) salary = 0;
    }
}
