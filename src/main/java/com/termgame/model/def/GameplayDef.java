package com.termgame.model.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 玩法扩展配置（gameplay 段）。
 * daily_briefs: 每日清晨晨报池（必出一条）
 * promo: 主动限时促销（广告费换限时客流）
 * hot_item: 爆款热卖（随机商品限时走红）
 * social: NPC 社交互动选项（option id → 定义）
 * trade: 贸易策略（小卖部批发 / 饭馆外卖）
 * staff: 雇佣系统（可雇佣角色池 + 发薪周期）
 * quest: NPC 委托订单（限时交货赚报酬）
 * lottery: 幸运彩票（次日开奖）
 * map: 地图地产（不同地段客流/房价，房屋贷款购买迁移）
 * wardrobe: 个人装扮（品牌单品，穿戴加个人声望）
 * assets: 个人资产（车/住宅，持有加个人声望）
 * finance: 银行理财产品（定期兑付本金+利息）
 * branching: 分店系统（可开分店、独立库存/定价/装修、全局金钱账户）
 * supply: 供应链系统（供应商等级/进货折扣/赊账/独家商品）
 * competition: 街区竞争对手（抢客流，可价格战/营销战/口碑攻势）
 * festival: 节日庆典（季节节日需求/筹备/战报，v1.11.0）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GameplayDef(
        List<DailyBriefDef> dailyBriefs,
        PromoDef promo,
        HotItemDef hotItem,
        Map<String, SocialOptionDef> social,
        TradeDef trade,
        StaffConfigDef staff,
        QuestDef quest,
        LotteryDef lottery,
        MapConfigDef map,
        WardrobeDef wardrobe,
        List<AssetDef> assets,
        List<FinanceDef> finance,
        BranchDef branching,
        SupplyDef supply,
        CompetitionDef competition,
        FestivalDef festival
) {
    public GameplayDef {
        if (dailyBriefs == null) dailyBriefs = List.of();
        if (promo == null) promo = new PromoDef(200, 240, 1.5);
        if (hotItem == null) hotItem = new HotItemDef(0.3, 180, 2.5);
        if (social == null) social = Map.of();
        if (trade == null) trade = new TradeDef(null, null);
        if (staff == null) staff = new StaffConfigDef(30, Map.of(), new CareerDef(false, 5, 1, 50, 3, 200, 5, 8, -10, 10));
        if (quest == null) quest = new QuestDef(0, 2, 5, 15, 1.3);
        if (lottery == null) lottery = new LotteryDef(0, 0.08, 50, 200);
        if (map == null) map = new MapConfigDef(null, List.of(), new HouseLoanDef(12, 0.02));
        if (wardrobe == null) wardrobe = new WardrobeDef(List.of(), List.of());
        if (assets == null) assets = List.of();
        if (finance == null) finance = List.of();
        if (branching == null) branching = new BranchDef(false, 1, 2000, 1.6, 0);
        if (supply == null) supply = new SupplyDef(false, null, null, List.of(), 43200);
        if (competition == null) competition = new CompetitionDef(false, 0, 0, List.of(), 300, 200);
        if (festival == null) festival = new FestivalDef(false, 1440, List.of());
    }

    /** 按权重随机抽一条晨报（池空返回 null） */
    public DailyBriefDef rollBrief(java.util.Random random) {
        if (dailyBriefs.isEmpty()) return null;
        double total = dailyBriefs.stream().mapToDouble(DailyBriefDef::weight).sum();
        if (total <= 0) return dailyBriefs.get(0);
        double roll = random.nextDouble() * total;
        double acc = 0;
        for (DailyBriefDef b : dailyBriefs) {
            acc += b.weight();
            if (roll < acc) return b;
        }
        return dailyBriefs.get(dailyBriefs.size() - 1);
    }
}
