package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 节日庆典系统配置（gameplay.festival 段，v1.11.0）。
 * enabled: 是否启用节日玩法
 * prepWindowMin: 节日开始前可筹备的窗口（游戏分钟，默认 1440 = 提前 1 天）
 * festivals: 节日定义列表（按季节 + 季节内天数触发，每年循环）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FestivalDef(
        boolean enabled,
        int prepWindowMin,
        List<FestivalItemDef> festivals
) {
    public FestivalDef {
        if (prepWindowMin <= 0) prepWindowMin = 1440;
        if (festivals == null) festivals = List.of();
    }

    /** 按季节 + 季节内天数找到当天的节日（无则 null） */
    public FestivalItemDef bySeasonDay(String seasonId, int day) {
        for (FestivalItemDef f : festivals) {
            if (f.season().equals(seasonId) && f.day() == day) return f;
        }
        return null;
    }

    /** 下一个尚未到来的节日（从当前季节+天数往后找，用于预告） */
    public FestivalItemDef nextAfter(String seasonId, int day, List<String> seasonIds) {
        int curIdx = seasonIds.indexOf(seasonId);
        if (curIdx < 0) curIdx = 0;
        for (int skip = 0; skip < seasonIds.size(); skip++) {
            int idx = (curIdx + skip) % seasonIds.size();
            for (FestivalItemDef f : festivals) {
                if (f.season().equals(seasonIds.get(idx))) {
                    int fday = f.day();
                    boolean sameSeason = idx == curIdx;
                    if (!sameSeason || fday > day) {
                        return f;
                    }
                }
            }
        }
        return festivals.isEmpty() ? null : festivals.get(0);
    }
}
