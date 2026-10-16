package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 个人装扮单品（gameplay.wardrobe.items[]）。
 * 玩家购买后穿戴在对应 slot 上，每件提供个人声望加成。
 * brand: 品牌（虚构：大众/轻奢/奢侈三档）
 * prestige: 穿戴后提供的个人声望值
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WardrobeItemDef(
        String id,
        String name,
        String brand,
        String icon,
        String slot,
        double price,
        int prestige,
        String desc
) {
    public WardrobeItemDef {
        if (id == null || id.isBlank()) id = "item_" + System.identityHashCode(this);
        if (name == null || name.isBlank()) name = id;
        if (brand == null || brand.isBlank()) brand = "街坊工坊";
        if (icon == null || icon.isBlank()) icon = "🧥";
        if (slot == null || slot.isBlank()) slot = "top";
        if (price < 0) price = 0;
        if (prestige < 0) prestige = 0;
    }
}
