package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 雇佣系统配置（gameplay.staff）。
 * pay_interval_days: 发薪周期（游戏日）。默认 30 天 = 1 游戏月，
 *   每逢 (dayNumber - 1) % pay_interval_days == 0 且 dayNumber > 1 的清晨发薪。
 * roles: 可雇佣角色（staff id → 定义）
 * career: 员工培养晋升配置（v1.11.0，缺省关闭）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StaffConfigDef(
        int payIntervalDays,
        Map<String, StaffDef> roles,
        CareerDef career
) {
    public StaffConfigDef {
        if (payIntervalDays <= 0) payIntervalDays = 30;
        if (roles == null) roles = Map.of();
        if (career == null) career = new CareerDef(false, 5, 1, 50, 3, 200, 5, 8, -10, 10);
    }
}
