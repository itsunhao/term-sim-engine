package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * NPC 社交互动选项配置（gameplay.social 段，按 option id 映射）。
 * 例如：chat（闲聊，免费）、gift（送小礼）、tea（请喝茶）、taste（请尝新菜）、dinner（请客吃饭）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SocialOptionDef(
        String name,
        String icon,
        double cost,
        int favor,
        int rep,
        long cooldownMin,
        String flavor
) {
    public SocialOptionDef {
        if (name == null) name = "互动";
        if (icon == null) icon = "💬";
        if (cooldownMin <= 0) cooldownMin = 30;
        if (flavor == null) flavor = "";
    }
}
