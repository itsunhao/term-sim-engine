package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 竞争对手池（gameplay.competition.pool 段，v1.10.0）。
 * id: 对手唯一 id
 * name: 店名
 * icon: 展示图标
 * strength: 竞争力系数（影响抢客强度，0.8~1.2）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetitorPoolDef(
        String id,
        String name,
        String icon,
        double strength
) {
    public CompetitorPoolDef {
        if (strength <= 0) strength = 1.0;
    }
}
