package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 个人装扮系统配置（gameplay.wardrobe）。
 * slots: 可装扮部位（hat/top/pants/watch/shoes，顺序即展示顺序）
 * items: 全部装扮单品（每件含品牌/价格/声望加成）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WardrobeDef(
        java.util.List<String> slots,
        java.util.List<WardrobeItemDef> items
) {
    public WardrobeDef {
        if (slots == null) slots = java.util.List.of("hat", "top", "pants", "watch", "shoes");
        if (items == null) items = java.util.List.of();
    }
}
