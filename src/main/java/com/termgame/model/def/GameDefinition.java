package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 游戏定义 —— 一个 JSON 游戏安装文件的完整内容。
 * 引擎所有逻辑都以此驱动，实现"安装即玩"。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GameDefinition(
        String id,
        String name,
        String version,
        String description,
        String author,
        String icon,
        GameMeta meta,
        List<SeasonDef> seasons,
        List<WeatherDef> weathers,
        List<ProductDef> products,
        List<NpcDef> npcs,
        List<UpgradeDef> upgrades,
        List<LoanDef> loans,
        List<EventDef> events,
        GameplayDef gameplay,
        List<AchievementDef> achievements,
        WinCondition win,
        LoseCondition lose
) {

    public GameDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("游戏定义缺少 id");
        }
        if (meta == null) {
            throw new IllegalArgumentException("游戏 [" + id + "] 缺少 meta 配置");
        }
        if (seasons == null || seasons.isEmpty()) {
            throw new IllegalArgumentException("游戏 [" + id + "] 至少需要一个季节");
        }
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("游戏 [" + id + "] 至少需要一件商品");
        }
        if (npcs == null || npcs.isEmpty()) {
            throw new IllegalArgumentException("游戏 [" + id + "] 至少需要一个 NPC");
        }
        if (upgrades == null || upgrades.isEmpty()) {
            throw new IllegalArgumentException("游戏 [" + id + "] 至少需要一个装修方案");
        }
        if (loans == null || loans.isEmpty()) {
            throw new IllegalArgumentException("游戏 [" + id + "] 至少需要一个贷款产品");
        }
    }

    /** 返回去除随机事件的副本（测试用，消除小偷等随机扰动） */
    public GameDefinition withNoEvents() {
        return new GameDefinition(id, name, version, description, author, icon, meta, seasons, weathers, products,
                npcs, upgrades, loans, java.util.List.of(), gameplay, achievements, win, lose);
    }

    public SeasonDef season(String id) {
        return seasons.stream().filter(s -> s.id().equals(id)).findFirst().orElse(seasons.get(0));
    }

    public ProductDef product(String id) {
        return products.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    public UpgradeDef upgrade(String id) {
        if (upgrades == null) return null;
        return upgrades.stream().filter(u -> u.id().equals(id)).findFirst().orElse(null);
    }

    public NpcDef npc(String id) {
        if (npcs == null) return null;
        return npcs.stream().filter(n -> n.id().equals(id)).findFirst().orElse(null);
    }

    public LoanDef loan(String id) {
        return loans.stream().filter(l -> l.id().equals(id)).findFirst().orElse(null);
    }

    /** 成就定义（可为空） */
    public java.util.List<AchievementDef> achievementsOrEmpty() {
        return achievements == null ? java.util.List.of() : achievements;
    }

    /** 季节顺序（用于跨季推进） */
    public int seasonIndex(String seasonId) {
        for (int i = 0; i < seasons.size(); i++) {
            if (seasons.get(i).id().equals(seasonId)) return i;
        }
        return 0;
    }

    /** 保留定义顺序的类别清单 */
    public List<String> categoryList() {
        java.util.LinkedHashSet<String> cats = new java.util.LinkedHashSet<>();
        for (ProductDef p : products) {
            cats.add(p.category());
        }
        return List.copyOf(cats);
    }
}
