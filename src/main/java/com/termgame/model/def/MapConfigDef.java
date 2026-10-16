package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 地图配置（gameplay.map）。
 * start_location_id: 初始地产 id（新游戏默认所在地）
 * locations: 全部可迁移地产（含初始）
 * loan: 房屋贷款参数（期数 + 月利率）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MapConfigDef(
        String startLocationId,
        java.util.List<LocationDef> locations,
        HouseLoanDef loan
) {
    public MapConfigDef {
        if (locations == null) locations = java.util.List.of();
        if (loan == null) loan = new HouseLoanDef(12, 0.02);
    }
}
