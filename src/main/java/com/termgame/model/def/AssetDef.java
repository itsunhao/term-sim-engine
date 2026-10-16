package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 个人资产（gameplay.assets[]）：车 / 住宅等大件。
 * category: 品类（vehicle 车 / house 住宅）——同品类只计入最高档加成，跨品类累加
 * prestige: 持有后提供的个人声望加成
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetDef(
        String id,
        String name,
        String icon,
        String category,
        double price,
        int prestige,
        String desc
) {
    public AssetDef {
        if (id == null || id.isBlank()) id = "asset_" + System.identityHashCode(this);
        if (name == null || name.isBlank()) name = id;
        if (icon == null || icon.isBlank()) icon = "🚗";
        if (category == null || category.isBlank()) category = "vehicle";
        if (price < 0) price = 0;
        if (prestige < 0) prestige = 0;
    }
}
