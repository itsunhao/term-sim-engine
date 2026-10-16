package com.termgame.engine;

import com.termgame.model.def.AchievementDef;
import com.termgame.model.def.AssetDef;
import com.termgame.model.def.AutoBuyDef;
import com.termgame.model.def.BranchDef;
import com.termgame.model.def.CompetitionDef;
import com.termgame.model.def.CompetitorPoolDef;
import com.termgame.model.def.GameplayDef;
import com.termgame.model.def.DailyBriefDef;
import com.termgame.model.def.DeliveryDef;
import com.termgame.model.def.EventDef;
import com.termgame.model.def.FinanceDef;
import com.termgame.model.def.GameDefinition;
import com.termgame.model.def.GameMeta;
import com.termgame.model.def.HotItemDef;
import com.termgame.model.def.HouseLoanDef;
import com.termgame.model.def.LoanDef;
import com.termgame.model.def.LocationDef;
import com.termgame.model.def.LotteryDef;
import com.termgame.model.def.MapConfigDef;
import com.termgame.model.def.NpcDef;
import com.termgame.model.def.ProductDef;
import com.termgame.model.def.PromoDef;
import com.termgame.model.def.QuestDef;
import com.termgame.model.def.SeasonDef;
import com.termgame.model.def.SocialOptionDef;
import com.termgame.model.def.SupplyDef;
import com.termgame.model.def.SupplyTierDef;
import com.termgame.model.def.CareerDef;
import com.termgame.model.def.FestivalDef;
import com.termgame.model.def.FestivalItemDef;
import com.termgame.model.def.StaffConfigDef;
import com.termgame.model.def.StaffSkillDef;
import com.termgame.model.def.StaffDef;
import com.termgame.model.def.TradeDef;
import com.termgame.model.state.FestivalState;
import com.termgame.model.def.UpgradeDef;
import com.termgame.model.def.WardrobeDef;
import com.termgame.model.def.WardrobeItemDef;
import com.termgame.model.def.WeatherDef;
import com.termgame.model.def.WholesaleDef;
import com.termgame.model.state.Buff;
import com.termgame.model.state.CompetitorState;
import com.termgame.model.state.DayReport;
import com.termgame.model.state.FinanceState;
import com.termgame.model.state.GameState;
import com.termgame.model.state.GameDate;
import com.termgame.model.state.GameState;
import com.termgame.model.state.HouseLoanState;
import com.termgame.model.state.InventorySlot;
import com.termgame.model.state.ShopState;
import com.termgame.model.state.LoanState;
import com.termgame.model.state.NpcComment;
import com.termgame.model.state.NpcState;
import com.termgame.model.state.QuestState;
import com.termgame.model.state.StaffState;
import com.termgame.model.state.Stats;
import com.termgame.util.GameException;
import com.termgame.util.Money;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 模拟经营核心引擎（实时时间流模型）。
 *
 * 时间规则：
 *  - 现实 1 秒 = 游戏 1 分钟，时间持续流动（前端轮询 /state 触发推进）。
 *  - 显示格式 HH:MM，不显示年月日。1 游戏日 = 24 分钟现实时间。
 *  - 营业时段 06:00-24:00，客流按小时系数持续到来；00:00-06:00 打烊无客流。
 *  - 跨天（00:00）结算：日报、贷款利息入账、库存保质期、NPC 评价、声誉漂移、胜负判定；
 *    随后抽取新一天天气与随机事件，重算当日客流基准。
 *  - 暂停（pause/resume）：暂停期间时间不再流逝（lastTickAt 持续刷新）。
 */
@Service
public class GameEngine {

    private static final int MINUTES_PER_DAY = 1440;
    private static final int OPEN_START = 360;          // 06:00
    private static final long MAX_TICK_MINUTES = 2880;  // 单次最多补算 2 个游戏日（约 48 现实分钟，1.0×）
    private static final double HOUR_FACTOR_SUM = 21.4; // 营业时段系数和（用于把日客流折算为每分钟速率）
    /** 支持的实时时间倍速档位 */
    private static final java.util.Set<Double> TIME_SPEEDS =
            java.util.Set.of(0.5, 1.0, 1.5, 2.0, 3.0, 5.0);

    private final Random random = new Random();

    // ------------------------------------------------------------
    // 新建游戏
    // ------------------------------------------------------------

    public GameState newGame(GameDefinition def, String saveName) {
        return newGame(def, saveName, System.currentTimeMillis());
    }

    public GameState newGame(GameDefinition def, String saveName, long nowMillis) {
        GameState s = new GameState();
        s.setGameId(def.id());
        s.setSaveName(saveName);
        s.setCash(Money.round(def.meta().startCash()));
        s.setDebt(0);
        s.setMinutes(480);                   // 第 1 天 08:00（08:00 = 第 480 分钟）
        s.setLastTickAt(nowMillis);
        s.setPaused(false);
        GameDate d = new GameDate(1, def.meta().startSeason(), 1, 1);
        s.setDate(d);
        s.setWeatherId(firstWeather(def, d.getSeasonId()));
        s.setReputation(def.meta().startReputation());   // 必须先设声誉，computeFootTraffic 依赖
        s.setCurrentLocationId(mapOf(def) == null ? null : mapOf(def).startLocationId());
        s.ensureShops();                     // v1.9.0：创建主店
        ShopState mainShop = s.getShops().get("main");
        mainShop.setName(def.name());
        mainShop.setIcon(def.icon());
        mainShop.setLocationId(s.getCurrentLocationId());
        s.setDailyFootTraffic(computeFootTraffic(def, s, mainShop));
        s.setShopLevel(1);
        for (var npc : def.npcs()) {
            NpcState ns = new NpcState();
            ns.setFavorability(30 + random.nextInt(20));
            s.getNpcs().put(npc.id(), ns);
        }
        // 默认按建议价销售
        for (var p : def.products()) {
            s.getPriceMultiplier().put(p.id(), def.meta().defaultPriceMultiplier());
        }
        initDay(s);
        return s;
    }

    private void initDay(GameState s) {
        initDay(s.currentShop());
    }

    private void initDay(ShopState shop) {
        shop.getTodayPeriodFlow().put("早", 0);
        shop.getTodayPeriodFlow().put("午", 0);
        shop.getTodayPeriodFlow().put("晚", 0);
    }

    /** 载入/恢复存档时的兼容处理（旧版存档迁移 + 字段兜底） */
    public void resume(GameDefinition def, GameState s) {
        s.ensureShops();   // v1.9.0：旧档迁移 / 新档兜底（必须在任何店铺数据访问之前）
        if (s.getLastTickAt() <= 0) {
            // 旧版存档：从 GameDate 推算分钟（旧档第 N 天 08:00）
            if (s.getDate() != null && s.getDate().getDayNumber() > 0 && s.getMinutes() == 0) {
                s.setMinutes((long) (s.getDate().getDayNumber() - 1) * MINUTES_PER_DAY + 480L);
            }
            s.setLastTickAt(System.currentTimeMillis());
            s.setPaused(false);
        }
        if (s.getTodayPeriodFlow() == null) s.setTodayPeriodFlow(new LinkedHashMap<>());
        if (s.getTodayEvents() == null) s.setTodayEvents(new ArrayList<>());
        if (s.getTodayNotices() == null) s.setTodayNotices(new ArrayList<>());
        // 新字段兜底：倍速 / 小数累积
        if (s.getTimeSpeed() <= 0 || !TIME_SPEEDS.contains(s.getTimeSpeed())) s.setTimeSpeed(1.0);
        if (Double.isNaN(s.getTickFraction()) || s.getTickFraction() < 0) s.setTickFraction(0);
        if (s.getDailyFootTraffic() <= 0 && !s.isEnded()) {
            s.setDailyFootTraffic(computeFootTraffic(def, s, s.currentShop()));
        }
        if (s.getTodayPeriodFlow().isEmpty()) initDay(s);
        // 玩法扩展兜底：促销 / 爆款 / 成就 / 晨报
        if (s.getPromoMult() < 1.0 || Double.isNaN(s.getPromoMult())) s.setPromoMult(1.0);
        if (s.getAchievements() == null) s.setAchievements(new LinkedHashMap<>());
        if (s.getHotCount() < 0) s.setHotCount(0);
        // 社交 / 贸易字段兜底
        if (s.getSocialCooldowns() == null) s.setSocialCooldowns(new LinkedHashMap<>());
        if (s.getTradeCooldownUntil() < 0) s.setTradeCooldownUntil(0);
        if (s.getDeliveryTotalOrders() < 0) s.setDeliveryTotalOrders(0);
        if (s.getDeliveryTotalRevenue() < 0) s.setDeliveryTotalRevenue(0);
        if (s.getNpcVisitBonus() < 0) s.setNpcVisitBonus(0);
        // 雇佣系统字段兜底
        if (s.getStaff() == null) s.setStaff(new LinkedHashMap<>());
        if (s.getPayrollDebtDays() < 0) s.setPayrollDebtDays(0);
        // v1.5.0 字段兜底：实时日志 / 银行 / 委托 / 彩票
        if (s.getLiveLog() == null) s.setLiveLog(new ArrayList<>());
        if (Double.isNaN(s.getBankBalance()) || s.getBankBalance() < 0) s.setBankBalance(0);
        if (Double.isNaN(s.getBankInterestEarned()) || s.getBankInterestEarned() < 0) s.setBankInterestEarned(0);
        if (s.getQuestsCompleted() < 0) s.setQuestsCompleted(0);
        if (s.getQuestsFailed() < 0) s.setQuestsFailed(0);
        if (s.getLotteryTicketDay() < 0) s.setLotteryTicketDay(0);
        // v1.6.0 字段兜底：地图地产
        MapConfigDef map = mapOf(def);
        if (s.getCurrentLocationId() == null || locationOf(def, s.getCurrentLocationId()) == null) {
            s.setCurrentLocationId(map == null ? null : map.startLocationId());
        }
        ShopState mainShop = s.getShops().get("main");
        if (mainShop != null && mainShop.getLocationId() == null) {
            mainShop.setLocationId(s.getCurrentLocationId());
        }
        if (s.getRelocations() < 0) s.setRelocations(0);
        // v1.7.0 字段兜底：个人形象
        if (s.getOwnedItems() == null) s.setOwnedItems(new java.util.ArrayList<>());
        if (s.getWornItems() == null) s.setWornItems(new java.util.HashMap<>());
        if (s.getOwnedAssets() == null) s.setOwnedAssets(new java.util.ArrayList<>());
        if (s.getPrestigeInteractions() < 0) s.setPrestigeInteractions(0);
        // v1.8.0 字段兜底：理财持仓 / 刮刮乐
        if (s.getFinances() == null) s.setFinances(new java.util.ArrayList<>());
        if (s.getScratchPurchases() < 0) s.setScratchPurchases(0);
        if (s.getScratchLastWin() < 0 && s.getScratchPurchases() == 0) s.setScratchLastWin(-1);
        // v1.10.0 字段兜底：供应链 / 口碑 / 街区竞争
        if (Double.isNaN(s.getSupplyPoints()) || s.getSupplyPoints() < 0) s.setSupplyPoints(0);
        if (Double.isNaN(s.getCreditUsed()) || s.getCreditUsed() < 0) s.setCreditUsed(0);
        if (s.getCreditDueMin() < 0) s.setCreditDueMin(0);
        if (s.getCompetitors() == null) s.setCompetitors(new java.util.ArrayList<>());
        if (s.getCompeteCooldownUntil() < 0) s.setCompeteCooldownUntil(0);
        if (s.getPriceWarUntilMin() < 0) s.setPriceWarUntilMin(0);
        if (s.getAdWarUntilMin() < 0) s.setAdWarUntilMin(0);
        if (s.getSatisfactionHistory() == null) s.setSatisfactionHistory(new java.util.ArrayList<>());
        if (Double.isNaN(s.getSatisfactionScore()) || s.getSatisfactionScore() < 0) s.setSatisfactionScore(70);
        if (s.getRatingTier() < 0) s.setRatingTier(0);
        if (s.getTodayExpiredQty() < 0) s.setTodayExpiredQty(0);

        // 过期状态清理
        if (s.getPromoUntilMin() > 0 && s.getMinutes() >= s.getPromoUntilMin()) {
            s.setPromoUntilMin(0);
            s.setPromoMult(1.0);
        }
        if (s.getHotUntilMin() > 0 && s.getMinutes() >= s.getHotUntilMin()) {
            s.setHotUntilMin(0);
            s.setHotProductId(null);
        }
    }

    private String firstWeather(GameDefinition def, String seasonId) {
        return def.weathers().stream()
                .filter(w -> w.seasonWeight().getOrDefault(seasonId, 0) > 0)
                .findFirst()
                .map(WeatherDef::id)
                .orElse(def.weathers().get(0).id());
    }

    // ------------------------------------------------------------
    // 时间推进（tick）
    // ------------------------------------------------------------

    /**
     * 按现实时间推进游戏时间，返回本次推进中产生的日报（可能为空）。
     * 暂停或已结束时只刷新时间戳，不推进。
     */
    public List<DayReport> tick(GameDefinition def, GameState s, long nowMillis) {
        if (s.isEnded() || s.isPaused()) {
            s.setLastTickAt(nowMillis);
            return List.of();
        }
        long elapsedMs = nowMillis - s.getLastTickAt();
        if (elapsedMs <= 0) return List.of();
        // 按倍速折算游戏分钟：现实秒 × 倍速 + 上次不足 1 分钟的小数部分
        double raw = elapsedMs / 1000.0 * s.getTimeSpeed() + s.getTickFraction();
        long whole = (long) raw;
        s.setTickFraction(raw - whole);
        long elapsedMin = Math.min(whole, MAX_TICK_MINUTES);
        if (elapsedMin <= 0) return List.of();
        s.setLastTickAt(nowMillis);

        List<DayReport> reports = new ArrayList<>();
        long remaining = elapsedMin;
        while (remaining > 0) {
            int remainToday = MINUTES_PER_DAY - s.minuteOfDay();
            long step = Math.min(remainToday, remaining);
            if (step <= 0) break;
            // 限时促销 / 爆款到期检查
            expireFlashStates(def, s, step);
            // 当日利息（按分钟连续累计，跨天时一次性入账）
            double accrual = s.getDebt() * maxLoanRate(s) / 30.0 * (step / (double) MINUTES_PER_DAY);
            if (accrual > 0) s.setTodayInterest(s.getTodayInterest() + accrual);
            // 实时自动补货员：库存为 0 的商品即时补货（营业中每分钟检查）
            realtimeRestock(def, s);
            simulatePeriod(def, s, (int) step);
            s.setMinutes(s.getMinutes() + step);
            remaining -= step;
            if (s.minuteOfDay() == 0) {
                reports.add(settleDay(def, s));
            }
        }
        return reports;
    }

    /** 限时促销 / 爆款按游戏分钟到期清理；促销到期后重算客流基准 */
    private void expireFlashStates(GameDefinition def, GameState s, long step) {
        long next = s.getMinutes() + step;
        if (s.getPromoUntilMin() > 0 && next >= s.getPromoUntilMin()) {
            s.setPromoUntilMin(0);
            s.setPromoMult(1.0);
            if (!s.isEnded()) {
                for (ShopState sh : s.getShops().values()) {
                    sh.setDailyFootTraffic(computeFootTraffic(def, s, sh));
                }
            }
        }
        if (s.getHotUntilMin() > 0 && next >= s.getHotUntilMin()) {
            s.setHotUntilMin(0);
            s.setHotProductId(null);
        }
    }

    /** 打烊休息：快进到次日 06:00，跨天正常结算 */
    public DayReport rest(GameDefinition def, GameState s) {
        checkNotEnded(s);
        s.setTodayClosed(true);
        int remain = MINUTES_PER_DAY - s.minuteOfDay();
        // 促销 / 爆款若在休息期间到期，先清理（避免次日错误带入客流加成）
        expireFlashStates(def, s, remain);
        double accrual = s.getDebt() * maxLoanRate(s) / 30.0 * (remain / (double) MINUTES_PER_DAY);
        if (accrual > 0) s.setTodayInterest(s.getTodayInterest() + accrual);
        s.setMinutes(s.getMinutes() + remain);   // 推进到 00:00
        DayReport r = settleDay(def, s);
        s.setMinutes(s.getMinutes() + OPEN_START); // 继续到 06:00
        return r;
    }

    /** 逐分钟模拟营业时段客流与购买 */
    private void simulatePeriod(GameDefinition def, GameState s, int minutes) {
        int mod = s.minuteOfDay();
        double salesMod = buffValue(s, Buff.Type.SALES_MOD, 1.0);
        // v1.9.0：所有已开店铺同时营业；仅当前店输出实时终端日志
        for (Map.Entry<String, ShopState> entry : s.getShops().entrySet()) {
            boolean isCurrent = entry.getKey().equals(s.getCurrentShopId());
            simulateShopPeriod(def, s, entry.getValue(), isCurrent, minutes, mod, salesMod);
        }
    }

    private void simulateShopPeriod(GameDefinition def, GameState s, ShopState shop, boolean isCurrent,
                                    int minutes, int mod, double salesMod) {
        double shopTraffic = shop.getDailyFootTraffic();
        // v1.10.0：街区竞争抢客 + 价格战/营销战 + 满意度回头客（作用于本分钟客流）
        double trafficFactor = 1.0 - competitionSteal(def, s);
        if (s.getPriceWarUntilMin() > s.getMinutes()) trafficFactor += 0.25;  // 价格战冲量
        if (s.getAdWarUntilMin() > s.getMinutes()) trafficFactor += 0.10;     // 营销战引流
        double satFactor = 1.0 + (s.getSatisfactionScore() - 60) / 200.0;     // 满意度回头客 ±20%
        if (s.getRatingTier() == 0) satFactor *= 0.95;                        // 低口碑客流惩罚
        // v1.11.0：节日客流加成（筹备度越高气氛越旺）
        trafficFactor *= 1.0 + festivalTrafficBonus(def, s);
        for (int m = 0; m < minutes; m++) {
            int minute = (mod + m) % MINUTES_PER_DAY;
            int hour = minute / 60;
            double factor = hourFactor(hour);
            if (factor <= 0) continue;
            double rate = shopTraffic * factor / HOUR_FACTOR_SUM * trafficFactor * satFactor;
            int n = (int) rate;
            if (random.nextDouble() < rate - n) n++;
            // 该分钟成交汇总（购买记录实时终端流，仅当前店显示）
            StringBuilder minuteDetail = new StringBuilder();
            double minuteRevenue = 0;
            int minuteItems = 0;
            for (int i = 0; i < n; i++) {
                BuyResult br = simulateOneCustomer(def, s, shop, salesMod);
                if (br.spent > 0) {
                    shop.setTodayRevenue(Money.round(shop.getTodayRevenue() + br.revenue));
                    shop.setTodayCost(Money.round(shop.getTodayCost() + br.cogs));
                    shop.setTodayItems(shop.getTodayItems() + br.items);
                    shop.setTodayCustomers(shop.getTodayCustomers() + 1);
                    s.setCash(Money.round(s.getCash() + br.revenue));
                    String period = periodOf(hour);
                    shop.getTodayPeriodFlow().merge(period, 1, Integer::sum);
                    Stats st = s.getStats();
                    st.setTotalRevenue(Money.round(st.getTotalRevenue() + br.revenue));
                    st.setTotalCost(Money.round(st.getTotalCost() + br.cogs));
                    st.setTotalProfit(Money.round(st.getTotalProfit() + (br.revenue - br.cogs)));
                    st.setCustomersServed(st.getCustomersServed() + 1);
                    st.setItemsSold(st.getItemsSold() + br.items);
                    if (isCurrent) {
                        minuteRevenue += br.revenue;
                        minuteItems += br.items;
                        if (minuteDetail.length() > 0) minuteDetail.append("、");
                        minuteDetail.append(br.details);
                    }
                }
            }
            if (isCurrent && minuteItems > 0) {
                pushLive(s, "📈 " + String.format("%02d:%02d", hour, minute % 60) + " 卖出 "
                        + minuteDetail + "，进账 " + Money.fmt(minuteRevenue));
            }
            // v1.11.0：节日期间累计营业额（结算战报用）
            if (festivalActive(s)) {
                FestivalState fs = s.getFestival();
                fs.setFestivalRevenue(Money.round(fs.getFestivalRevenue() + minuteRevenue));
            }
        }
    }

    // ------------------------------------------------------------
    // 跨天结算
    // ------------------------------------------------------------

    private DayReport settleDay(GameDefinition def, GameState s) {
        DayReport r = new DayReport();
        r.setDayLabel("第" + (s.dayOf() - 1) + "天");
        r.setClosed(s.isTodayClosed());
        WeatherDef w = weather(def, s.getWeatherId());
        r.setWeatherName(w.name());
        r.setWeatherIcon(w.icon());
        // v1.9.0：日报汇总全部已开店铺（每店今日营收/客流/事件合并）
        double totRev = 0, totCost = 0;
        int totItems = 0, totCust = 0;
        Map<String, Integer> totFlow = new LinkedHashMap<>();
        List<String> totEvents = new ArrayList<>(), totNotices = new ArrayList<>();
        for (ShopState sh : s.getShops().values()) {
            totRev += sh.getTodayRevenue();
            totCost += sh.getTodayCost();
            totItems += sh.getTodayItems();
            totCust += sh.getTodayCustomers();
            sh.getTodayPeriodFlow().forEach((k, v) -> totFlow.merge(k, v, Integer::sum));
            totEvents.addAll(sh.getTodayEvents());
            totNotices.addAll(sh.getTodayNotices());
        }
        r.setFootTraffic(Money.round(s.getDailyFootTraffic() * 10) / 10.0);
        r.setCustomers(totCust);
        r.setItemsSold(totItems);
        r.setRevenue(Money.round(totRev));
        r.setCostOfGoods(Money.round(totCost));
        r.setProfit(Money.round(totRev - totCost));
        r.setPeriodFlow(totFlow);
        r.setInterestPaid(Money.round(s.getTodayInterest()));
        if (r.getInterestPaid() > 0) {
            s.setDebt(Money.round(s.getDebt() + r.getInterestPaid()));
        }
        r.getEvents().addAll(totEvents);
        r.getNotices().addAll(totNotices);

        // 外卖订单结算（餐饮型店铺，占用当日库存；先于隔夜过期处理）
        deliveryOrders(def, s, r);
        // NPC 委托到期结算（期限内的订单今天交货判定；先于过期处理）
        questSettle(def, s, r);
        // 库存过期（隔夜损耗）
        expireInventory(def, s, r);
        // 幸运彩票开奖（买票的次日清晨）
        lotterySettle(def, s, r);
        // 银行存款日利息（复利）
        bankSettle(def, s, r);
        // NPC 当日来访与评价
        simulateNpcs(def, s, r);
        // 声誉漂移
        driftReputation(def, s, r);
        // buff 到期
        s.getBuffs().removeIf(b -> {
            b.setDaysRemaining(b.getDaysRemaining() - 1);
            return b.getDaysRemaining() <= 0;
        });
        // 胜负判定
        checkEnd(def, s, r);

        r.setCashAfter(s.getCash());
        r.setDebtAfter(s.getDebt());
        r.setReputationAfter(Money.round(s.getReputation()));
        r.setNetWorth(netWorth(def, s));

        Stats st = s.getStats();
        st.setDaysPlayed(st.getDaysPlayed() + 1);
        st.setBestDayRevenue(Math.max(st.getBestDayRevenue(), r.getRevenue()));

        s.getDayHistory().add(r);
        if (s.getDayHistory().size() > 40) {
            s.getDayHistory().remove(0);
        }

        // 重置当日累计，进入新的一天（所有店铺）
        s.setTodayInterest(0);
        s.setTodayClosed(false);
        s.setTodayExpiredQty(0);
        for (ShopState sh : s.getShops().values()) {
            sh.setTodayRevenue(0);
            sh.setTodayCost(0);
            sh.setTodayItems(0);
            sh.setTodayCustomers(0);
            sh.getTodayEvents().clear();
            sh.getTodayNotices().clear();
            sh.getTodayPeriodFlow().clear();
            initDay(sh);
        }
        s.setDeliveryTodayOrders(0);
        s.setDeliveryTodayRevenue(0);
        s.setDeliveryTodayCost(0);
        s.setNpcVisitBonus(0);

        // 推进日期，抽取新一天天气与事件，重算客流基准
        advanceDate(def, s);
        // 雇佣系统：发薪（月首）→ 自动补货 → 离职风险
        paySalaries(def, s, r);
        staffAutoBuy(def, s, r);
        staffTurnover(def, s, r);
        // 房屋贷款月供（每 30 天一期，与发薪同节奏）
        houseLoanSettle(def, s, r);
        // 理财产品：到期自动兑付本金+利息
        financeSettle(def, s, r);
        // NPC 委托：生成新委托（无进行中委托时按概率）
        rollQuest(def, s, r);
        // 个人形象：声望越高 NPC 越可能主动上门互动
        rollPrestigeInteractions(def, s, r);
        // v1.10.0：满意度结算（基于刚过去的一天的价格/新鲜/服务表现）
        satisfactionSettle(def, s, r);
        // v1.10.0：赊账到期自动扣款（或罚息顺延）
        creditSettle(def, s, r);
        // v1.10.0：街区竞争（对手登场 / 热度变化 / 歇业 / 卷土重来）
        competitionSettle(def, s, r);
        // v1.11.0：节日庆典（开幕 / 闭幕结算 / 每日小事件）
        festivalTick(def, s, r);
        // v1.11.0：员工培养（每日经验 / 等级 / 忠诚微调）
        careerTick(def, s, r);
        s.setWeatherId(rollWeather(def, s.getDate().getSeasonId()));
        rollEvents(def, s);
        // 玩法扩展：今日晨报 / 爆款热卖 / 成就检查
        rollDailyBrief(def, s);
        rollHotItem(def, s);
        checkAchievements(def, s);
        for (ShopState sh : s.getShops().values()) {
            sh.setDailyFootTraffic(computeFootTraffic(def, s, sh));
        }
        // NPC 社交福利：高好感街坊带客/宣传（作用于新一天）
        npcSocialPerks(def, s, r);
        return r;
    }

    // ------------------------------------------------------------
    // 玩家操作分发
    // ------------------------------------------------------------

    /** 无副作用进度的操作返回提示（进货/定价/贷款/还款/装修/暂停/继续/营业信息/促销/社交/贸易） */
    public String prepareAct(GameDefinition def, GameState s, String action,
                             String productId, Integer qty, Double multiplier,
                             Double amount, String loanId, String upgradeId,
                             String subaction, String npcId) {
        checkNotEnded(s);
        return switch (action == null ? "" : action) {
            case "buy" -> buy(def, s, productId, qty);
            case "price" -> setPrice(def, s, productId, multiplier);
            case "allPrice", "all_price" -> setAllPrice(def, s, multiplier);
            case "speed" -> setSpeed(s, multiplier);
            case "loan" -> takeLoan(def, s, loanId);
            case "repay" -> repay(s, amount);
            case "renovate" -> renovate(def, s, upgradeId);
            case "promo" -> promo(def, s);
            case "social" -> social(def, s, npcId, subaction);
            case "trade" -> trade(def, s, subaction, productId, qty);
            case "hire" -> hire(def, s, npcId);
            case "fire" -> fire(def, s, npcId);
            case "raise" -> adjustSalary(def, s, npcId, true);
            case "cut" -> adjustSalary(def, s, npcId, false);
            case "deposit" -> bankDeposit(def, s, amount);
            case "withdraw" -> bankWithdraw(def, s, amount);
            case "lottery" -> buyLottery(def, s);
            case "scratch" -> buyScratch(def, s);
            case "finance" -> financeView(def, s);
            case "financebuy" -> financeBuy(def, s, npcId);
            case "financeredeem" -> financeRedeem(def, s, npcId);
            case "housebuy" -> houseBuy(def, s, npcId);
            case "map" -> mapView(def, s);
            case "wear" -> wearItem(def, s, npcId);
            case "buyitem" -> buyItem(def, s, npcId);
            case "buyasset" -> buyAsset(def, s, npcId);
            case "wardrobe" -> wardrobeView(def, s);
            case "open_branch" -> openBranch(def, s, npcId, productId);
            case "switch_shop" -> switchShop(s, npcId);
            case "supply" -> supplyView(def, s);
            case "buy_credit" -> buyCredit(def, s, productId, qty);
            case "compete" -> competeView(def, s);
            case "price_war" -> priceWar(def, s);
            case "ad_war" -> adWar(def, s);
            case "charm_off" -> charmOff(def, s);
            case "festival" -> festivalView(def, s);
            case "festival_setup" -> festivalSetup(def, s, amount);
            case "promote" -> promoteStaff(def, s, npcId);
            case "pick_skill" -> pickSkill(def, s, npcId, subaction);
            case "redpacket" -> redpacket(def, s, npcId);
            case "pause" -> {
                s.setPaused(true);
                s.setLastTickAt(System.currentTimeMillis());
                yield "⏸ 时间已暂停，客流与时间都停住了。";
            }
            case "resume" -> {
                s.setPaused(false);
                s.setLastTickAt(System.currentTimeMillis());
                yield "▶ 时间继续流动，店铺恢复营业。";
            }
            case "open" -> "⏱ 当前时间 " + s.clockText() + "，店铺营业中（06:00-24:00）。";
            default -> throw new GameException("未知操作：" + action);
        };
    }

    // ------------------------------------------------------------
    // 分店系统（v1.9.0）
    // ------------------------------------------------------------

    /**
     * 开分店：新店拥有独立的库存/定价/装修/雇员/选址（客流），
     * 现金、存款、贷款、理财等金钱全部使用全局唯一账户。
     * 费用 = 基础开店费 × 增长倍率^(已开店数-1)，逐店递增。
     */
    private String openBranch(GameDefinition def, GameState s, String locationId, String name) {
        GameplayDef gp = def.gameplay();
        BranchDef br = gp == null || gp.branching() == null ? new BranchDef(false, 1, 2000, 1.6, 0) : gp.branching();
        if (!br.enabled()) throw new GameException("当前游戏未开放开分店功能。");
        int existing = s.getShops().size();
        if (existing >= br.maxShops()) {
            throw new GameException("最多可同时经营 " + br.maxShops() + " 家店，已满员。");
        }
        if (s.getReputation() < br.requireReputation()) {
            throw new GameException("声望不足：开分店需要声望 ≥ " + br.requireReputation()
                    + "（当前 " + (int) s.getReputation() + "）。先经营好主店、多与街坊互动提升声望吧。");
        }
        double cost = Money.round(br.baseOpenCost() * Math.pow(br.costGrowth(), existing - 1));
        if (s.getCash() < cost) {
            throw new GameException("开店资金不足：开这家分店需要 " + Money.fmt(cost)
                    + " " + def.meta().currency() + "（当前现金 " + Money.fmt(s.getCash())
                    + "）。可先存款/贷款周转或继续营业攒钱。");
        }
        String loc = resolveBranchLocation(def, s, locationId);
        s.setCash(Money.round(s.getCash() - cost));
        String shopId = "branch_" + existing;
        String shopName = name == null || name.isBlank() ? "分店" + existing : name.trim();
        ShopState shop = new ShopState(shopId, shopName, "🏬", loc, s.getMinutes());
        for (ProductDef p : def.products()) {
            shop.getPriceMultiplier().put(p.id(), def.meta().defaultPriceMultiplier());
        }
        shop.setDailyFootTraffic(computeFootTraffic(def, s, shop));
        s.getShops().put(shopId, shop);
        s.setCurrentShopId(shopId);
        LocationDef l = locationOf(def, loc);
        pushLive(s, "🏬 新店开业！「" + shopName + "」落户" + (l == null ? "新址" : l.name())
                + "（开店费 " + Money.fmt(cost) + "），库存、定价、装修均为全新账本。");
        return "🏬 「" + shopName + "」开业成功！位置：" + (l == null ? "默认街角" : l.icon() + " " + l.name())
                + "，客流加成 " + (l == null ? "普通" : "×" + l.trafficBonus())
                + "。已切换到新店——它的库存/定价/装修与老店互不影响，现金/存款/贷款共用同一账户。";
    }

    /** 分店选址：优先用指定且未被占用的位置；否则自动挑第一个空闲位置 */
    private String resolveBranchLocation(GameDefinition def, GameState s, String preferred) {
        MapConfigDef map = mapOf(def);
        if (map == null || map.locations().isEmpty()) return null;
        if (preferred != null && locationOf(def, preferred) != null) {
            boolean taken = s.getShops().values().stream()
                    .anyMatch(sh -> preferred.equals(sh.getLocationId()));
            if (!taken) return preferred;
        }
        for (LocationDef loc : map.locations()) {
            String id = loc.id();
            boolean taken = s.getShops().values().stream()
                    .anyMatch(sh -> id.equals(sh.getLocationId()));
            if (!taken) return id;
        }
        return map.locations().get(0).id();
    }

    /** 切换当前查看/经营的店铺（前端店铺切换器） */
    private String switchShop(GameState s, String shopId) {
        if (shopId == null || !s.getShops().containsKey(shopId)) {
            throw new GameException("店铺不存在：" + shopId);
        }
        s.setCurrentShopId(shopId);
        ShopState sh = s.getShops().get(shopId);
        return "🗂 已切换到「" + sh.getIcon() + " " + sh.getName()
                + "」——货架、定价、装修、雇员已切换为该店账本。";
    }

    private void checkNotEnded(GameState s) {
        if (s.isEnded()) {
            throw new GameException("本局游戏已结束：" + s.getEndMessage());
        }
    }

    // ------------------------------------------------------------
    // 雇佣系统：招聘 / 解雇 / 涨薪 / 降薪 / 发薪 / 自动进货 / 离职
    // ------------------------------------------------------------

    private StaffConfigDef staffCfg(GameDefinition def) {
        return def.gameplay() == null ? null : def.gameplay().staff();
    }

    private String hire(GameDefinition def, GameState s, String staffId) {
        StaffConfigDef cfg = staffCfg(def);
        if (cfg == null || cfg.roles() == null || cfg.roles().isEmpty()) {
            throw new GameException("本游戏未配置可雇佣角色");
        }
        StaffDef role = cfg.roles().get(staffId);
        if (role == null) throw new GameException("未知角色：" + staffId);
        if (s.getStaff().containsKey(staffId)) {
            throw new GameException(role.icon() + " " + role.name() + " 已在职");
        }
        s.getStaff().put(staffId, new StaffState(role.id(), role.salary(), 0, 0, s.getDate().getDayNumber()));
        return "✅ 已雇佣 " + role.icon() + " " + role.name() + "（月薪 " + Money.fmt(role.salary())
                + " " + def.meta().currency() + "，每 " + cfg.payIntervalDays() + " 天发薪一次）。" + role.desc();
    }

    private String fire(GameDefinition def, GameState s, String staffId) {
        StaffState st = s.getStaff().remove(staffId);
        if (st == null) throw new GameException("该员工不在职（先雇佣再解雇）");
        StaffDef role = roleOf(def, staffId);
        return "🚪 已解雇 " + (role == null ? staffId : role.icon() + " " + role.name())
                + "。当月工资照发，江湖再见。";
    }

    private String adjustSalary(GameDefinition def, GameState s, String staffId, boolean raise) {
        StaffState st = s.getStaff().get(staffId);
        if (st == null) throw new GameException("该员工不在职，无法" + (raise ? "涨薪" : "降薪"));
        StaffDef role = roleOf(def, staffId);
        String who = role == null ? staffId : role.icon() + " " + role.name();
        if (raise) {
            if (st.getRaiseLevel() >= 5) throw new GameException(who + " 已是顶薪（涨薪 5 次封顶）");
            st.setRaiseLevel(st.getRaiseLevel() + 1);
            st.setSalary(Money.round(Math.ceil(st.getSalary() * 1.15 / 10) * 10));
            CareerDef cd = careerCfg(def);
            if (cd != null && cd.enabled()) st.setLoyalty(st.getLoyalty() + cd.loyaltyPerRaise());
            return "💹 给 " + who + " 涨薪 → 月薪 " + Money.fmt(st.getSalary())
                    + "（效率 ×" + String.format("%.2f", st.efficiency()) + "）。干劲十足！";
        }
        st.setCutLevel(st.getCutLevel() + 1);
        st.setSalary(Money.round(Math.floor(st.getSalary() * 0.9 / 10) * 10));
        CareerDef cd = careerCfg(def);
        if (cd != null && cd.enabled()) st.setLoyalty(st.getLoyalty() + cd.loyaltyPerCut());
        String warn = st.getCutLevel() >= 2 || st.getLoyalty() < 20 ? " 员工心生去意，可能离职…" : "";
        return "📉 给 " + who + " 降薪 → 月薪 " + Money.fmt(st.getSalary())
                + "（效率 ×" + String.format("%.2f", st.efficiency()) + "）。" + warn;
    }

    private StaffDef roleOf(GameDefinition def, String staffId) {
        StaffConfigDef cfg = staffCfg(def);
        return cfg == null || cfg.roles() == null ? null : cfg.roles().get(staffId);
    }

    /** 员工 params 数值加成汇总：sum(params[key] × 效率)，从 defVal 起累计，封顶 cap */
    private double staffParamSum(GameDefinition def, GameState s, String key, double defVal, double cap) {
        return staffParamSum(def, s.getStaff(), key, defVal, cap);
    }

    private double staffParamSum(GameDefinition def, Map<String, StaffState> staff, String key, double defVal, double cap) {
        StaffConfigDef cfg = staffCfg(def);
        if (cfg == null || staff.isEmpty()) return defVal;
        double sum = defVal;
        double aura = staffAura(def, staff);   // v1.11.0：店长光环（晋升骨干全员加成）
        for (Map.Entry<String, StaffState> e : staff.entrySet()) {
            StaffDef role = cfg.roles().get(e.getKey());
            if (role == null || role.params() == null) continue;
            Double v = role.params().get(key);
            if (v == null) continue;
            sum += v * e.getValue().efficiency() * aura;
        }
        return Math.min(sum, cap);
    }

    /** 某事件被员工抑制的强度（0~1+），rollEvents 时概率 ×(1 - 强度) */
    private double suppressStrength(GameDefinition def, GameState s, String eventId) {
        StaffConfigDef cfg = staffCfg(def);
        if (cfg == null || s.getStaff().isEmpty()) return 0;
        double sum = 0;
        for (Map.Entry<String, StaffState> e : s.getStaff().entrySet()) {
            StaffDef role = cfg.roles().get(e.getKey());
            if (role == null || role.suppressEvents() == null) continue;
            if (!role.suppressEvents().contains(eventId)) continue;
            double strength = role.params().getOrDefault("suppress_strength", 0.5)
                    + staffSkillValue(def, e.getValue(), "suppress_bonus");   // v1.11.0 技能加成
            sum += strength * e.getValue().efficiency();
        }
        return sum;
    }

    /** 每月发薪日：结算所有在编员工月薪 */
    private void paySalaries(GameDefinition def, GameState s, DayReport r) {
        StaffConfigDef cfg = staffCfg(def);
        if (cfg == null) return;
        int day = s.getDate().getDayNumber();
        if (day <= 1 || (day - 1) % cfg.payIntervalDays() != 0) return;
        // v1.9.0：逐店发薪（现金为全局唯一账户，现金够的店发、不够的欠薪）
        double totalPaid = 0;
        int paidStaff = 0;
        for (ShopState sh : s.getShops().values()) {
            if (sh.getStaff().isEmpty()) continue;
            double shopTotal = 0;
            for (StaffState st : sh.getStaff().values()) shopTotal += st.getSalary();
            if (s.getCash() >= shopTotal) {
                s.setCash(Money.round(s.getCash() - shopTotal));
                sh.setPayrollDebtDays(0);
                totalPaid += shopTotal;
                paidStaff += sh.getStaff().size();
                CareerDef cd = careerCfg(def);
                if (cd != null && cd.enabled()) {
                    for (StaffState st : sh.getStaff().values()) {
                        st.setLoyalty(st.getLoyalty() + cd.loyaltyPerPay());
                    }
                }
            } else {
                s.setCash(0);
                sh.setPayrollDebtDays(sh.getPayrollDebtDays() + 1);
                CareerDef cd = careerCfg(def);
                if (cd != null && cd.enabled()) {
                    for (StaffState st : sh.getStaff().values()) {
                        st.setLoyalty(st.getLoyalty() - 15);
                    }
                }
                r.getNotices().add("🚨 发薪日资金不足！「" + sh.getName() + "」工资被拖欠，员工士气大跌，可能离职…");
            }
        }
        if (paidStaff > 0) {
            r.getNotices().add("💰 发薪日：向全部 " + paidStaff + " 位员工发放月薪 "
                    + Money.fmt(totalPaid) + " " + def.meta().currency());
        }
    }

    /** 实时自动补货员（role=realtime_restock）：营业中每分钟检查，库存为 0 的商品即时补货 */
    private void realtimeRestock(GameDefinition def, GameState s) {
        StaffConfigDef cfg = staffCfg(def);
        if (cfg == null || s.getStaff().isEmpty() || hourFactor(s.minuteOfDay() / 60) <= 0) return;
        StaffDef restocker = null;
        double eff = 1.0;
        for (Map.Entry<String, StaffState> e : s.getStaff().entrySet()) {
            StaffDef role = cfg.roles().get(e.getKey());
            if (role != null && "realtime_restock".equals(role.role())) {
                restocker = role;
                eff = e.getValue().efficiency();
                break;
            }
        }
        if (restocker == null) return;
        // 规则2：不设冷却，按页面刷新频率检查（每次 tick 均检查；倍速下跟随推进步长，频率与刷新一致）
        StaffState restockerState = null;
        for (Map.Entry<String, StaffState> e0 : s.getStaff().entrySet()) {
            if (e0.getKey().equals(restocker.id())) { restockerState = e0.getValue(); break; }
        }
        int targetQty = Math.max(1, (int) Math.round((restocker.autoBuy() == null ? 10 : restocker.autoBuy().targetQty())
                * clamp(0.6, eff, 1.4)) + (int) Math.round(staffSkillValue(def, restockerState, "stock_bonus")));
        double minKeep = restocker.autoBuy() == null ? 0 : restocker.autoBuy().minCashKeep();
        double cash0 = s.getCash();   // 本轮补货的起始现金：循环内只读，最后统一扣款一次
        double totalCost = 0;
        int bought = 0;
        StringBuilder detail = new StringBuilder();
        for (ProductDef p : def.products()) {
            if (p.unlockUpgradeId() != null && !s.getUpgradesOwned().contains(p.unlockUpgradeId())) continue;
            InventorySlot slot = s.getInventory().get(p.id());
            int have = slot == null ? 0 : slot.getQty();
            if (have > 0) continue; // 只补库存为 0 的商品
            double unit = p.wholesalePrice();
            // 规则1：现金满足 → 补 targetQty(10)；现金不足 → 补最大可买数量
            double remaining = cash0 - totalCost;   // 剩余可支配现金（累计一次，绝不双重扣减）
            int maxByCash = (int) Math.floor(remaining / unit);   // 剩余现金可买
            if (maxByCash <= 0) continue;                        // 连 1 件都买不起，本商品跳过
            int maxByKeep = (int) Math.floor((remaining - minKeep) / unit); // 保底预留后可买
            if (maxByKeep < 0) maxByKeep = Integer.MAX_VALUE;    // 补完低于保底/现金已低于保底：救急不设限，仍按剩余现金补
            int can = Math.min(targetQty, Math.min(maxByCash, maxByKeep));
            if (can <= 0) continue;
            InventorySlot sl = s.getInventory().computeIfAbsent(p.id(),
                    k -> new InventorySlot(p.id(), 0, p.shelfLifeDays()));
            sl.setQty(sl.getQty() + can);          // 规则3：先补库存
            sl.setShelfLifeRemaining(p.shelfLifeDays());
            totalCost += Money.round(unit * can);  // 仅累计成本，不在此扣款
            bought += can;
            if (detail.length() > 0) detail.append("、");
            detail.append(p.icon()).append(p.name()).append("×").append(can);
        }
        if (bought > 0) {
            // 规则4：整轮只扣款一次，金额 = Σ(补货量 × 批发价)，多商品并发补货也不会有任何重复/冲突扣费
            s.setCash(Money.round(cash0 - totalCost));
            CareerDef cd = careerCfg(def);
            if (cd != null && cd.enabled() && restockerState != null) {
                restockerState.setExp(restockerState.getExp() + cd.expPerAction());
                int lvl = cd.levelOf(restockerState.getExp());
                if (lvl > restockerState.getLevel()) restockerState.setLevel(lvl);
            }
            pushLive(s, "🧺 实时补货：" + restocker.name() + " 见货架空，补上 " + detail
                    + "（-" + Money.fmt(totalCost) + " " + def.meta().currency() + "）");
        }
    }

    /** 每日清晨：auto_buy 员工自动补货 */
    private void staffAutoBuy(GameDefinition def, GameState s, DayReport r) {
        StaffConfigDef cfg = staffCfg(def);
        if (cfg == null) return;
        // v1.9.0：逐店自动补货（每店自己的员工/库存/装修；现金全局唯一账户）
        for (ShopState sh : s.getShops().values()) {
            if (sh.getStaff().isEmpty()) continue;
            for (Map.Entry<String, StaffState> e : sh.getStaff().entrySet()) {
                StaffDef role = cfg.roles().get(e.getKey());
                if (role == null || role.autoBuy() == null) continue;
                double eff = e.getValue().efficiency();
                AutoBuyDef ab = role.autoBuy();
                int targetQty = (int) Math.round(ab.targetQty() * clamp(0.8, eff, 1.4))
                        + (int) Math.round(staffSkillValue(def, e.getValue(), "auto_buy_bonus"));
                double spendCap = s.getCash() * ab.maxSpendRatio() * clamp(0.8, eff, 1.4);
                double spent = 0;
                int bought = 0;
                for (ProductDef p : def.products()) {
                    if (spent >= spendCap) break;
                    if (p.unlockUpgradeId() != null && !sh.getUpgradesOwned().contains(p.unlockUpgradeId())) continue;
                    InventorySlot slot = sh.getInventory().get(p.id());
                    int have = slot == null ? 0 : slot.getQty();
                    if (have >= targetQty) continue;
                    int need = targetQty - have;
                    double unit = p.wholesalePrice();
                    int can = (int) Math.min(need, Math.floor((spendCap - spent) / unit));
                    if (can <= 0) continue;
                    double remainAfter = s.getCash() - spent - unit * can;
                    if (remainAfter < ab.minCashKeep()) {
                        int allowed = (int) Math.floor((s.getCash() - spent - ab.minCashKeep()) / unit);
                        can = Math.min(can, Math.max(0, allowed));
                        if (can <= 0) continue;
                    }
                    InventorySlot sl = sh.getInventory().computeIfAbsent(p.id(),
                            k -> new InventorySlot(p.id(), 0, p.shelfLifeDays()));
                    sl.setQty(sl.getQty() + can);
                    sl.setShelfLifeRemaining(p.shelfLifeDays());
                    spent += unit * can;
                    bought += can;
                }
                if (bought > 0) {
                    s.setCash(Money.round(s.getCash() - spent));
                    CareerDef cd = careerCfg(def);
                    if (cd != null && cd.enabled()) {
                        e.getValue().setExp(e.getValue().getExp() + cd.expPerAction());
                    }
                    r.getNotices().add("🧺 「" + sh.getName() + "」" + role.name() + " 自动补货 " + bought + " 件，花费 "
                            + Money.fmt(spent) + " " + def.meta().currency());
                } else {
                    r.getNotices().add("🧺 「" + sh.getName() + "」" + role.name() + " 盘点完毕：库存充足或资金紧张，今日未补货。");
                }
            }
        }
    }

    /** 每日清晨：降薪过狠 / 欠薪 → 员工离职风险 */
    private void staffTurnover(GameDefinition def, GameState s, DayReport r) {
        StaffConfigDef cfg = staffCfg(def);
        if (cfg == null) return;
        // v1.9.0：逐店评估离职风险（欠薪按店独立统计）
        for (ShopState sh : s.getShops().values()) {
            if (sh.getStaff().isEmpty()) continue;
            var it = sh.getStaff().entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                StaffState st = e.getValue();
                double risk = (st.getCutLevel() >= 2 ? 0.08 : 0) + sh.getPayrollDebtDays() * 0.10
                        + (st.getLoyalty() < 20 ? 0.12 : 0);   // v1.11.0 忠诚过低也可能离职
                if (risk > 0 && random.nextDouble() < risk) {
                    StaffDef role = cfg.roles().get(e.getKey());
                    it.remove();
                    r.getNotices().add("🚪 「" + sh.getName() + "」" + (role == null ? st.getStaffId() : role.icon() + " " + role.name())
                            + " 离职了（" + (sh.getPayrollDebtDays() > 0 ? "被拖欠工资" : "薪资没诚意") + "）。快去招新人吧！");
                }
            }
        }
    }

    /** 员工数量（成就用） */
    private int staffCount(GameDefinition def, GameState s) {
        return s.getStaff().size();
    }

    // ------------------------------------------------------------
    // 银行（存款 / 取款 / 日利息）
    // ------------------------------------------------------------

    private String bankDeposit(GameDefinition def, GameState s, double amount) {
        if (amount <= 0) throw new GameException("存款金额必须大于 0");
        double cash = s.getCash();
        if (amount > cash) {
            throw new GameException("现金不足：需要 " + Money.fmt(amount)
                    + " " + def.meta().currency() + "，当前 " + Money.fmt(cash));
        }
        s.setCash(Money.round(cash - amount));
        s.setBankBalance(Money.round(s.getBankBalance() + amount));
        return "🏦 存入 " + Money.fmt(amount) + " " + def.meta().currency()
                + " → 银行存款 " + Money.fmt(s.getBankBalance())
                + "（年化利率 " + String.format("%.1f%%", def.meta().bankAnnualRate() * 100)
                + "，每日复利计息）";
    }

    private String bankWithdraw(GameDefinition def, GameState s, double amount) {
        if (amount <= 0) throw new GameException("取款金额必须大于 0");
        double bal = s.getBankBalance();
        if (amount > bal) {
            throw new GameException("存款不足：需要 " + Money.fmt(amount)
                    + " " + def.meta().currency() + "，当前存款 " + Money.fmt(bal));
        }
        s.setBankBalance(Money.round(bal - amount));
        s.setCash(Money.round(s.getCash() + amount));
        return "🏦 取出 " + Money.fmt(amount) + " " + def.meta().currency()
                + " → 银行存款 " + Money.fmt(s.getBankBalance());
    }

    /** 每日清晨银行结算：存款 × 年化利率 ÷ 游戏年天数（120 天）→ 复利计入存款 */
    private void bankSettle(GameDefinition def, GameState s, DayReport r) {
        double bal = s.getBankBalance();
        if (bal <= 0) return;
        double yearDays = 4.0 * def.meta().daysPerSeason();
        double interest = Money.round(bal * def.meta().bankAnnualRate() / yearDays);
        if (interest <= 0) return;
        s.setBankBalance(Money.round(bal + interest));
        s.setBankInterestEarned(Money.round(s.getBankInterestEarned() + interest));
        r.getNotices().add("🏦 存款利息 +" + Money.fmt(interest) + "（复利，存款余额 "
                + Money.fmt(s.getBankBalance()) + "）");
    }

    // ------------------------------------------------------------
    // 幸运彩票（每日一张，次日清晨开奖）
    // ------------------------------------------------------------

    private String buyLottery(GameDefinition def, GameState s) {
        LotteryDef lot = def.gameplay() == null ? null : def.gameplay().lottery();
        if (lot == null || lot.cost() <= 0) {
            throw new GameException("本游戏未开通彩票玩法");
        }
        int today = s.getDate().getDayNumber();
        if (s.getLotteryTicketDay() == today) {
            throw new GameException("今天已经买过彩票了，明天再来");
        }
        if (s.getLotteryTicketDay() > 0) {
            throw new GameException("还有一张彩票未开奖，明日开奖后可再购买");
        }
        double cost = lot.cost();
        if (cost > s.getCash()) {
            throw new GameException("现金不足：彩票 " + Money.fmt(cost)
                    + " " + def.meta().currency() + "，当前 " + Money.fmt(s.getCash()));
        }
        s.setCash(Money.round(s.getCash() - cost));
        s.setLotteryTicketDay(today);
        return "🎰 花 " + Money.fmt(cost) + " 买了张彩票（中奖概率 "
                + String.format("%.0f%%", lot.winChance() * 100)
                + "，奖金 " + lot.prizeMin() + "~" + lot.prizeMax() + "），明日清晨开奖，祝好运！";
    }

    /** 每日清晨开奖（买票的次日）：中奖 → 现金入账；未中 → 结果 0 */
    private void lotterySettle(GameDefinition def, GameState s, DayReport r) {
        LotteryDef lot = def.gameplay() == null ? null : def.gameplay().lottery();
        int ticketDay = s.getLotteryTicketDay();
        if (ticketDay <= 0 || lot == null) return;
        if (ticketDay != s.getDate().getDayNumber()) return; // 只结算买票当天（结算日 = 昨天）
        s.setLotteryTicketDay(0);
        if (random.nextDouble() < lot.winChance()) {
            int prize = lot.prizeMin() + random.nextInt(lot.prizeMax() - lot.prizeMin() + 1);
            s.setCash(Money.round(s.getCash() + prize));
            s.setLotteryLastPrize(prize);
            r.getNotices().add("🎰 昨日彩票开奖：恭喜中奖 +" + prize + " " + def.meta().currency() + "！");
        } else {
            s.setLotteryLastPrize(0);
            r.getNotices().add("🎰 昨日彩票开奖：很遗憾，没有中奖。");
        }
    }

    // ------------------------------------------------------------
    // 刮刮乐（即时开奖，不限购买次数）
    // ------------------------------------------------------------

    /** 刮刮乐：即买即刮，无购买上限 */
    private String buyScratch(GameDefinition def, GameState s) {
        LotteryDef lot = def.gameplay() == null ? null : def.gameplay().lottery();
        if (lot == null || lot.scratchCost() <= 0) {
            throw new GameException("本游戏未开通刮刮乐玩法");
        }
        double cost = lot.scratchCost();
        if (cost > s.getCash()) {
            throw new GameException("现金不足：刮刮乐 " + Money.fmt(cost)
                    + " " + def.meta().currency() + "，当前 " + Money.fmt(s.getCash()));
        }
        s.setCash(Money.round(s.getCash() - cost));
        s.setScratchPurchases(s.getScratchPurchases() + 1);
        if (random.nextDouble() < lot.scratchWinChance()) {
            int prize = lot.scratchPrizeMin()
                    + random.nextInt(lot.scratchPrizeMax() - lot.scratchPrizeMin() + 1);
            s.setCash(Money.round(s.getCash() + prize));
            s.setScratchLastWin(prize);
            pushLive(s, "🎫 刮刮乐中奖 +" + prize + " " + def.meta().currency() + "！（成本 "
                    + Money.fmt(cost) + "）");
            return "🎫 刮开涂层：恭喜中奖 +" + prize + " " + def.meta().currency() + "！";
        }
        s.setScratchLastWin(0);
        pushLive(s, "🎫 刮刮乐：" + Money.fmt(cost) + " 没中，再接再厉。");
        return "🎫 刮开涂层：很遗憾，谢谢参与（-" + Money.fmt(cost) + " " + def.meta().currency() + "）。";
    }

    // ------------------------------------------------------------
    // 理财产品（定期锁定，到期兑付本金+利息）
    // ------------------------------------------------------------

    private String financeView(GameDefinition def, GameState s) {
        if (def.gameplay() == null || def.gameplay().finance() == null
                || def.gameplay().finance().isEmpty()) {
            throw new GameException("本游戏未开通理财产品");
        }
        StringBuilder sb = new StringBuilder("💹 理财产品一览：\n");
        for (FinanceDef f : def.gameplay().finance()) {
            sb.append("  ").append(f.icon()).append(" ").append(f.name())
                    .append("：期限 ").append(f.periodDays()).append(" 天，年化 ")
                    .append(String.format("%.1f%%", f.annualRate() * 100))
                    .append("，起购 ").append(Money.fmt(f.minAmount()))
                    .append("，到期收益约 ")
                    .append(Money.fmt(f.minAmount() * f.annualRate() * f.periodDays() / 360.0))
                    .append("\n");
        }
        if (!s.getFinances().isEmpty()) {
            sb.append("持有 ").append(s.getFinances().size()).append(" 笔：\n");
            for (FinanceState fs : s.getFinances()) {
                FinanceDef f = financeOf(def, fs.getProductId());
                double interest = fs.getAmount() * fs.getAnnualRate() * fs.getPeriodDays() / 360.0;
                sb.append("  ").append(f == null ? fs.getProductId() : f.icon() + " " + f.name())
                        .append("：本金 ").append(Money.fmt(fs.getAmount()))
                        .append("，剩 ").append(Math.max(0, fs.getDueDay() - s.getDate().getDayNumber()))
                        .append(" 天到期，预估收益 ").append(Money.fmt(interest)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private FinanceDef financeOf(GameDefinition def, String id) {
        if (def.gameplay() == null || def.gameplay().finance() == null) return null;
        for (FinanceDef f : def.gameplay().finance()) {
            if (f.id().equals(id)) return f;
        }
        return null;
    }

    /** 购买理财产品：扣本金，生成持仓（到期日 = 今天 + 期限） */
    private String financeBuy(GameDefinition def, GameState s, String productId) {
        FinanceDef f = financeOf(def, productId);
        if (f == null) throw new GameException("理财产品不存在：" + productId);
        if (f.minAmount() > s.getCash()) {
            throw new GameException("现金不足：该产品起购 " + Money.fmt(f.minAmount())
                    + "，当前 " + Money.fmt(s.getCash()));
        }
        double amount = s.getCash() >= f.minAmount() * 2 ? Math.min(s.getCash(), f.minAmount() * 20) : f.minAmount();
        s.setCash(Money.round(s.getCash() - amount));
        int day = s.getDate().getDayNumber();
        FinanceState fs = new FinanceState(f.id(), amount, day, day + f.periodDays(), f.annualRate(), f.periodDays());
        s.getFinances().add(fs);
        pushLive(s, "💹 买入理财 " + f.icon() + " " + f.name() + " " + Money.fmt(amount)
                + "，" + f.periodDays() + " 天后兑付。");
        return "💹 已买入 " + f.icon() + " " + f.name() + "（" + Money.fmt(amount)
                + "，" + f.periodDays() + " 天，年化 " + String.format("%.1f%%", f.annualRate() * 100)
                + "），到期自动兑付本金+利息。";
    }

    /** 提前赎回：仅返还本金（利息归零），未到期理财可随时赎回 */
    private String financeRedeem(GameDefinition def, GameState s, String idxStr) {
        if (s.getFinances().isEmpty()) throw new GameException("当前没有理财持仓");
        int idx;
        try {
            idx = Integer.parseInt(idxStr);
        } catch (NumberFormatException e) {
            throw new GameException("用法：financeredeem <序号>（finance 查看序号）");
        }
        if (idx < 0 || idx >= s.getFinances().size()) throw new GameException("持仓序号不存在：" + idx);
        FinanceState fs = s.getFinances().remove(idx);
        FinanceDef f = financeOf(def, fs.getProductId());
        s.setCash(Money.round(s.getCash() + fs.getAmount()));
        int left = Math.max(0, fs.getDueDay() - s.getDate().getDayNumber());
        pushLive(s, "💸 提前赎回理财" + (f == null ? "" : " " + f.icon() + " " + f.name())
                + "，返还本金 " + Money.fmt(fs.getAmount()) + "（利息归零，距到期还剩 " + left + " 天）。");
        return "💸 已提前赎回" + (f == null ? "" : " " + f.icon() + " " + f.name())
                + "，返还本金 " + Money.fmt(fs.getAmount()) + "，利息归零。";
    }

    /** 每日清晨：到期理财自动兑付（本金 + 利息 = 本金 × 年化 × 期限/360） */
    private void financeSettle(GameDefinition def, GameState s, DayReport r) {
        if (s.getFinances().isEmpty()) return;
        int day = s.getDate().getDayNumber();
        var it = s.getFinances().iterator();
        while (it.hasNext()) {
            FinanceState fs = it.next();
            if (fs.getDueDay() > day) continue;
            double interest = Money.round(fs.getAmount() * fs.getAnnualRate() * fs.getPeriodDays() / 360.0);
            double total = Money.round(fs.getAmount() + interest);
            s.setCash(Money.round(s.getCash() + total));
            FinanceDef f = financeOf(def, fs.getProductId());
            it.remove();
            r.getNotices().add("💹 理财到期：" + (f == null ? fs.getProductId() : f.icon() + " " + f.name())
                    + " 兑付 " + Money.fmt(total) + "（本金 " + Money.fmt(fs.getAmount())
                    + " + 收益 " + Money.fmt(interest) + "）");
        }
    }

    // ------------------------------------------------------------
    // 地图地产（房屋贷款购买 + 迁移）
    // ------------------------------------------------------------

    private MapConfigDef mapOf(GameDefinition def) {
        return def.gameplay() == null ? null : def.gameplay().map();
    }

    private LocationDef locationOf(GameDefinition def, String id) {
        MapConfigDef map = mapOf(def);
        if (map == null || map.locations() == null) return null;
        for (LocationDef loc : map.locations()) {
            if (loc.id().equals(id)) return loc;
        }
        return null;
    }

    private String mapView(GameDefinition def, GameState s) {
        MapConfigDef map = mapOf(def);
        if (map == null || map.locations() == null || map.locations().isEmpty()) {
            throw new GameException("本游戏未配置地图");
        }
        LocationDef cur = locationOf(def, s.getCurrentLocationId());
        StringBuilder sb = new StringBuilder();
        sb.append("📍 当前：").append(cur == null ? "?" : cur.icon() + " " + cur.name())
          .append("（客流 ×").append(String.format("%.2f", cur == null ? 1 : cur.trafficBonus())).append("）\n");
        for (LocationDef loc : map.locations()) {
            boolean owned = loc.id().equals(s.getCurrentLocationId());
            sb.append(owned ? "▶ " : "  ").append(loc.icon()).append(" ").append(loc.name())
              .append(owned ? "（所在）" : loc.price() <= 0 ? "（初始地产）" : "　价格 " + Money.fmt(loc.price()) + " 元 / 首付 "
                      + Math.round(loc.downPaymentRatio() * 100) + "%")
              .append("　客流 ×").append(String.format("%.2f", loc.trafficBonus()))
              .append("\n");
        }
        HouseLoanState hl = s.getHouseLoan();
        if (hl != null) {
            sb.append("🏠 房贷进行中：月供 ").append(Money.fmt(hl.getMonthlyPayment()))
              .append(" 元 ×").append(hl.getMonthsLeft()).append(" 期（剩余本金 ")
              .append(Money.fmt(hl.getPrincipal())).append("）");
        }
        return sb.toString();
    }

    private String houseBuy(GameDefinition def, GameState s, String locationId) {
        MapConfigDef map = mapOf(def);
        if (map == null || map.locations() == null || map.locations().isEmpty()) {
            throw new GameException("本游戏未配置地图");
        }
        if (locationId == null) throw new GameException("用法：housebuy <地产id>（用 map 查看）");
        LocationDef loc = locationOf(def, locationId);
        if (loc == null) throw new GameException("未知地产：" + locationId);
        if (loc.id().equals(s.getCurrentLocationId())) {
            throw new GameException("你已经在这家店经营了（" + loc.name() + "）");
        }
        boolean occupiedByOther = s.getShops().values().stream()
                .anyMatch(sh -> !sh.getShopId().equals(s.getCurrentShopId()) && loc.id().equals(sh.getLocationId()));
        if (occupiedByOther) {
            throw new GameException("该地段已被你的另一家分店经营，请先切换过去或选择其他地段。");
        }
        if (s.getHouseLoan() != null) {
            throw new GameException("还有房屋贷款未还清（剩 " + s.getHouseLoan().getMonthsLeft()
                    + " 期），还清后才能再买新地产");
        }
        if (s.getReputation() < loc.unlockReputation()) {
            throw new GameException("声誉不足：购买【" + loc.name() + "】需要声誉 "
                    + loc.unlockReputation() + "，当前 " + (int) s.getReputation());
        }
        double down = Money.round(loc.price() * loc.downPaymentRatio());
        if (s.getCash() < down) {
            throw new GameException("首付不足：需要 " + Money.fmt(down) + " " + def.meta().currency()
                    + "（总价 " + Money.fmt(loc.price()) + " 的 "
                    + Math.round(loc.downPaymentRatio() * 100) + "%），当前 " + Money.fmt(s.getCash()));
        }
        HouseLoanDef loan = map.loan() == null ? new HouseLoanDef(12, 0.02) : map.loan();
        double principal = Money.round(loc.price() - down);
        double monthly = principal <= 0 ? 0
                : Money.round((principal + principal * loan.monthlyRate() * loan.months()) / loan.months());
        s.setCash(Money.round(s.getCash() - down));
        if (principal > 0) {
            s.setHouseLoan(new HouseLoanState(loc.id(), principal, monthly, loan.months()));
        }
        s.setCurrentLocationId(loc.id());
        s.setRelocations(s.getRelocations() + 1);
        // 商品库存自动随店迁移（库存本就存在存档中）
        int stock = s.getInventory().values().stream().mapToInt(v -> v.getQty()).sum();
        String loanTxt = principal > 0
                ? "，房贷 " + Money.fmt(principal) + " 分 " + loan.months() + " 期月供 "
                        + Money.fmt(monthly) + " 元（每 30 天一期）"
                : "，全额付清无房贷";
        pushLive(s, "🏠 乔迁新居：" + loc.icon() + " " + loc.name() + "（客流 ×"
                + String.format("%.2f", loc.trafficBonus()) + "）！全部 " + stock + " 件库存已随店迁移。");
        return "🏠 乔迁新居成功：" + loc.icon() + " " + loc.name()
                + "，客流 ×" + String.format("%.2f", loc.trafficBonus())
                + "；首付 " + Money.fmt(down) + loanTxt + "。全部库存已自动转移到新店铺！";
    }

    /** 每月（每 30 游戏天）清晨偿还房屋贷款；现金不足 → 逾期扣声誉，欠款继续计息 */
    private void houseLoanSettle(GameDefinition def, GameState s, DayReport r) {
        HouseLoanState hl = s.getHouseLoan();
        if (hl == null) return;
        if ((s.getDate().getDayNumber() - 1) % 30 != 0 || s.getDate().getDayNumber() <= 1) return;
        if (hl.getMonthsLeft() <= 0) {
            s.setHouseLoan(null);
            return;
        }
        double pay = hl.getMonthlyPayment();
        if (s.getCash() >= pay) {
            double interest = Money.round(hl.getPrincipal() * (mapOf(def) == null || mapOf(def).loan() == null
                    ? 0.02 : mapOf(def).loan().monthlyRate()));
            s.setCash(Money.round(s.getCash() - pay));
            hl.setPrincipal(Math.max(0, Money.round(hl.getPrincipal() + interest - pay)));
            hl.setOverdueDays(0);
            r.getNotices().add("🏠 房贷月供已还 " + Money.fmt(pay) + "（剩余 " + hl.getMonthsLeft() + " 期）");
            if (hl.getPrincipal() <= 0) {
                r.getNotices().add("🎉 房屋贷款全部还清！新店完全属于你了。");
                s.setHouseLoan(null);
                return;
            }
        } else {
            hl.setOverdueDays(hl.getOverdueDays() + 1);
            s.setCash(0);
            s.setReputation(Math.max(0, s.getReputation() - 3));
            r.getNotices().add("⚠️ 房贷逾期：现金不足月供 " + Money.fmt(pay) + "，声誉 -3，欠款继续计息");
        }
        hl.setMonthsLeft(hl.getMonthsLeft() - 1);
    }

    // ------------------------------------------------------------
    // 个人形象（装扮 / 资产 / 个人声望 / NPC 主动互动）
    // ------------------------------------------------------------

    private WardrobeDef wardrobeOf(GameDefinition def) {
        return def.gameplay() == null ? null : def.gameplay().wardrobe();
    }

    private WardrobeItemDef itemOf(GameDefinition def, String id) {
        WardrobeDef w = wardrobeOf(def);
        if (w == null || w.items() == null) return null;
        for (WardrobeItemDef it : w.items()) {
            if (it.id().equals(id)) return it;
        }
        return null;
    }

    private AssetDef assetOf(GameDefinition def, String id) {
        if (def.gameplay() == null || def.gameplay().assets() == null) return null;
        for (AssetDef a : def.gameplay().assets()) {
            if (a.id().equals(id)) return a;
        }
        return null;
    }

    /** 个人声望 = 穿戴装扮加成之和 + 同品类资产取最高档（跨品类累加） */
    public double computePrestige(GameDefinition def, GameState s) {
        double p = 0;
        for (String itemId : s.getWornItems().values()) {
            WardrobeItemDef it = itemOf(def, itemId);
            if (it != null) p += it.prestige();
        }
        if (def.gameplay() != null && def.gameplay().assets() != null) {
            java.util.Map<String, Integer> bestByCat = new java.util.HashMap<>();
            for (String aid : s.getOwnedAssets()) {
                AssetDef a = assetOf(def, aid);
                if (a == null) continue;
                bestByCat.merge(a.category(), a.prestige(), Math::max);
            }
            for (int v : bestByCat.values()) p += v;
        }
        return p;
    }

    /** 声望档位：0-19 平民 / 20-49 体面 / 50-99 知名 / 100-199 名流 / 200+ 传奇 */
    public int prestigeLevel(double prestige) {
        if (prestige >= 200) return 5;
        if (prestige >= 100) return 4;
        if (prestige >= 50) return 3;
        if (prestige >= 20) return 2;
        if (prestige >= 10) return 1;
        return 0;
    }

    public static String prestigeLevelName(int level) {
        return switch (level) {
            case 1 -> "崭露头角";
            case 2 -> "体面人士";
            case 3 -> "街坊名人";
            case 4 -> "城中名流";
            case 5 -> "传奇人物";
            default -> "街坊平民";
        };
    }

    private String wardrobeView(GameDefinition def, GameState s) {
        WardrobeDef w = wardrobeOf(def);
        if (w == null || w.items() == null || w.items().isEmpty()) {
            throw new GameException("本游戏未配置形象系统");
        }
        double prestige = computePrestige(def, s);
        StringBuilder sb = new StringBuilder();
        sb.append("🧍 个人声望 ").append((int) prestige).append("（")
          .append(prestigeLevelName(prestigeLevel(prestige))).append("）\n");
        for (String slot : w.slots()) {
            String worn = s.getWornItems().get(slot);
            WardrobeItemDef it = worn == null ? null : itemOf(def, worn);
            sb.append(slotName(slot)).append("：")
              .append(it == null ? "（未穿戴）" : it.icon() + " " + it.name() + "（+" + it.prestige() + "）")
              .append("\n");
        }
        return sb.toString();
    }

    private String slotName(String slot) {
        return switch (slot) {
            case "hat" -> "🎩 帽子";
            case "top" -> "👔 上衣";
            case "pants" -> "👖 裤子";
            case "watch" -> "⌚ 手表";
            case "shoes" -> "👟 鞋子";
            default -> "🧾 " + slot;
        };
    }

    private String buyItem(GameDefinition def, GameState s, String itemId) {
        if (itemId == null) throw new GameException("用法：buyitem <装扮id>（wardrobe 查看）");
        WardrobeItemDef it = itemOf(def, itemId);
        if (it == null) throw new GameException("未知装扮：" + itemId);
        if (s.getOwnedItems().contains(itemId)) throw new GameException("已拥有该装扮，直接穿戴即可");
        if (s.getCash() < it.price()) {
            throw new GameException("现金不足：需要 " + Money.fmt(it.price()) + " " + def.meta().currency()
                    + "（当前 " + Money.fmt(s.getCash()) + "）");
        }
        s.setCash(Money.round(s.getCash() - it.price()));
        s.getOwnedItems().add(itemId);
        s.getWornItems().put(it.slot(), itemId);   // 购买即穿戴
        pushLive(s, "🧍 购入 " + it.icon() + " " + it.brand() + "「" + it.name() + "」并上身，个人声望 +" + it.prestige());
        return "🛍 已购入并穿戴 " + it.icon() + " " + it.brand() + "「" + it.name() + "」，个人声望 +" + it.prestige()
                + "（当前声望 " + (int) computePrestige(def, s) + "）";
    }

    private String wearItem(GameDefinition def, GameState s, String itemId) {
        if (itemId == null) throw new GameException("用法：wear <装扮id>");
        WardrobeItemDef it = itemOf(def, itemId);
        if (it == null) throw new GameException("未知装扮：" + itemId);
        if (!s.getOwnedItems().contains(itemId)) {
            throw new GameException("尚未购买「" + it.name() + "」，先用 buyitem 购买");
        }
        s.getWornItems().put(it.slot(), itemId);
        return "🧍 已换装：" + it.icon() + " " + it.name() + "（+声望 " + it.prestige()
                + "，当前声望 " + (int) computePrestige(def, s) + "）";
    }

    private String buyAsset(GameDefinition def, GameState s, String assetId) {
        if (assetId == null) throw new GameException("用法：buyasset <资产id>（wardrobe 查看）");
        AssetDef a = assetOf(def, assetId);
        if (a == null) throw new GameException("未知资产：" + assetId);
        if (s.getOwnedAssets().contains(assetId)) throw new GameException("已拥有该资产");
        if (s.getCash() < a.price()) {
            throw new GameException("现金不足：需要 " + Money.fmt(a.price()) + " " + def.meta().currency()
                    + "（当前 " + Money.fmt(s.getCash()) + "）");
        }
        s.setCash(Money.round(s.getCash() - a.price()));
        s.getOwnedAssets().add(assetId);
        pushLive(s, "🧍 购入资产 " + a.icon() + " " + a.name() + "，个人声望 +" + a.prestige());
        return "🏷 购入 " + a.icon() + " " + a.name() + "，个人声望 +" + a.prestige()
                + "（当前声望 " + (int) computePrestige(def, s) + "）";
    }

    /** 每日清晨 NPC 主动互动：声望越高越容易触发，内容随档位升级 */
    private void rollPrestigeInteractions(GameDefinition def, GameState s, DayReport r) {
        if (s.getDate().getDayNumber() == s.getPrestigeInteractDay()) return; // 每天最多一次
        int level = prestigeLevel(computePrestige(def, s));
        if (level < 1) return;
        double chance = Math.min(0.15 + level * 0.12, 0.65);
        if (random.nextDouble() >= chance) return;
        if (def.npcs() == null || def.npcs().isEmpty()) return;
        NpcDef npc = def.npcs().get(random.nextInt(def.npcs().size()));
        String name = npc.name();
        s.setPrestigeInteractDay(s.getDate().getDayNumber());
        s.setPrestigeInteractions(s.getPrestigeInteractions() + 1);
        String flavor;
        switch (level) {
            case 5 -> {
                int gift = 80 + random.nextInt(121);   // 80~200 元豪礼
                s.setCash(Money.round(s.getCash() + gift));
                flavor = "「老板这身派头，全城找不出第二个！这算是我的一点心意，请务必收下！」（送礼 "
                        + Money.fmt(gift) + " " + def.meta().currency() + "，好感 +4）";
                addFavor(s, npc.id(), 4);
            }
            case 4 -> {
                s.setReputation(Math.min(100, s.getReputation() + 1));
                flavor = "「能和您合个影吗？回头我挂在店里，沾沾您的贵气！」（声誉 +1，好感 +3）";
                addFavor(s, npc.id(), 3);
            }
            case 3 -> {
                int guests = 3 + random.nextInt(6);     // 带 3~8 位朋友光顾
                s.getBuffs().add(new Buff(Buff.Type.FOOT_TRAFFIC_FLAT, guests, 1));
                flavor = "「我这就带几个老伙计上您店里坐坐，给您捧场！」（今日客流 +" + guests + "，好感 +2）";
                addFavor(s, npc.id(), 2);
            }
            case 2 -> {
                int treat = 5 + random.nextInt(16);     // 送 5~20 元小礼
                s.setCash(Money.round(s.getCash() + treat));
                flavor = "「老板今天真精神！刚买的点心请您尝尝。」（送礼 " + Money.fmt(treat) + " "
                        + def.meta().currency() + "，好感 +2）";
                addFavor(s, npc.id(), 2);
            }
            default -> {
                flavor = "「哟，老板这身打扮真体面！这条街上就数您最风光。」（好感 +1）";
                addFavor(s, npc.id(), 1);
            }
        }
        String line = "💬 " + npc.name() + " 主动找上门：" + flavor;
        pushLive(s, line);
        r.getNotices().add(line);
    }

    private void addFavor(GameState s, String npcId, int delta) {
        NpcState st = s.getNpcs().get(npcId);
        if (st != null) st.setFavorability(Math.min(100, st.getFavorability() + delta));
    }

    /** 形象溢价：声望越高顾客越愿意为体面买单（客单价上限 +15%） */
    public double prestigePriceMult(GameDefinition def, GameState s) {
        double p = computePrestige(def, s);
        return 1.0 + Math.min(0.15, p * 0.0008);
    }

    // ------------------------------------------------------------
    // 实时日志流（购买记录 / 随机事件 → 前端终端滚动显示）
    // ------------------------------------------------------------

    private void pushLive(GameState s, String msg) {
        List<String> log = s.getLiveLog();
        log.add(msg);
        while (log.size() > 40) log.remove(0);
    }

    // ------------------------------------------------------------
    // NPC 委托订单（限时交货赚报酬）
    // ------------------------------------------------------------

    /** 委托到期结算：库存足够 → 交货拿报酬 + 发布者好感；不足 → 失败扣声誉 */
    private void questSettle(GameDefinition def, GameState s, DayReport r) {
        QuestState q = s.getActiveQuest();
        if (q == null) return;
        if (q.getDeadlineDay() > s.getDate().getDayNumber()) return;
        s.setActiveQuest(null);
        ProductDef p = def.product(q.getProductId());
        InventorySlot slot = s.getInventory().get(q.getProductId());
        int have = slot == null ? 0 : slot.getQty();
        if (p != null && have >= q.getQty()) {
            slot.setQty(have - q.getQty());
            s.setCash(Money.round(s.getCash() + q.getReward()));
            s.setQuestsCompleted(s.getQuestsCompleted() + 1);
            if (q.getNpcId() != null) {
                NpcState ns = s.getNpcs().computeIfAbsent(q.getNpcId(), k -> new NpcState());
                ns.setFavorability(clamp(ns.getFavorability() + 5, 0, 100));
            }
            r.getNotices().add("✅ 委托完成：" + q.getFlavor() + "，报酬 +" + Money.fmt(q.getReward()));
        } else {
            s.setQuestsFailed(s.getQuestsFailed() + 1);
            s.setReputation(Math.max(0, s.getReputation() - 2));
            r.getNotices().add("❌ 委托失败：" + q.getFlavor() + "（库存不足），声誉 -2");
        }
    }

    /** 每日清晨：无进行中委托时按概率生成新委托（随机 NPC 发布 + 随机已解锁商品） */
    private void rollQuest(GameDefinition def, GameState s, DayReport r) {
        QuestDef quest = def.gameplay() == null ? null : def.gameplay().quest();
        if (quest == null || quest.chance() <= 0) return;
        if (s.getActiveQuest() != null) return;
        if (random.nextDouble() >= quest.chance()) return;
        List<ProductDef> candidates = new ArrayList<>();
        for (ProductDef p : def.products()) {
            if (p.unlockUpgradeId() == null || s.getUpgradesOwned().contains(p.unlockUpgradeId())) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) return;
        ProductDef p = candidates.get(random.nextInt(candidates.size()));
        int qty = quest.qtyMin() + random.nextInt(Math.max(1, quest.qtyMax() - quest.qtyMin() + 1));
        double reward = Money.round(p.baseRetailPrice() * qty * quest.rewardRatio());
        NpcDef npc = def.npcs() == null || def.npcs().isEmpty()
                ? null : def.npcs().get(random.nextInt(def.npcs().size()));
        String npcId = npc == null ? null : npc.id();
        String flavor = (npc == null ? "一位神秘顾客" : npc.icon() + " " + npc.name())
                + "想订 " + p.icon() + p.name() + "×" + qty
                + "（" + quest.days() + " 天内送到，报酬 " + Money.fmt(reward) + "）";
        s.setActiveQuest(new QuestState(p.id(), qty, reward,
                s.getDate().getDayNumber() + quest.days(), npcId, flavor));
        r.getNotices().add("📋 新委托：" + flavor);
        pushLive(s, "📋 " + flavor);
    }



    private String buy(GameDefinition def, GameState s, String productId, Integer qty) {
        if (productId == null || qty == null || qty <= 0) {
            throw new GameException("进货数量必须大于 0");
        }
        ProductDef p = def.product(productId);
        if (p == null) throw new GameException("未知商品：" + productId);
        if (p.unlockUpgradeId() != null && !s.getUpgradesOwned().contains(p.unlockUpgradeId())) {
            throw new GameException("【" + p.name() + "】尚未解锁，先完成对应装修吧");
        }
        // v1.10.0 供应链：独家商品需战略伙伴等级
        SupplyDef sp = supplyOf(def);
        if (sp != null && sp.enabled() && sp.exclusiveProducts().contains(productId)
                && !sp.isMaxTier(s.getSupplyPoints())) {
            throw new GameException("【" + p.name() + "】是 " + sp.supplierName()
                    + " 的战略伙伴专供，先累计合作值到最高等级（supply 查看）");
        }
        double discount = buffValue(s, Buff.Type.RESTOCK_DISCOUNT, 1.0);
        double buyerDisc = staffParamSum(def, s, "buy_discount", 0, 0.2) + staffSkillSum(def, s, "discount_bonus");
        // v1.10.0 供应链：供应商等级折扣（随累计合作值提升）
        double supplyDisc = supplyDiscount(def, s);
        double unitCost = Money.round(p.wholesalePrice() * discount * (1 - buyerDisc) * supplyDisc);
        double total = Money.round(unitCost * qty);
        if (total > s.getCash()) {
            throw new GameException("现金不足：需要 " + Money.fmt(total) + " " + def.meta().currency()
                    + "，当前 " + Money.fmt(s.getCash()));
        }
        s.setCash(Money.round(s.getCash() - total));
        // v1.10.0 供应链：实付金额计入合作值（供应商升级）
        s.setSupplyPoints(Money.round(s.getSupplyPoints() + total));
        InventorySlot slot = s.getInventory().computeIfAbsent(productId,
                k -> new InventorySlot(productId, 0, p.shelfLifeDays()));
        slot.setQty(slot.getQty() + qty);
        slot.setShelfLifeRemaining(p.shelfLifeDays());
        String discountText = discount < 1.0 ? "（供应商特惠 " + (int) Math.round((1 - discount) * 100) + " 折）" : "";
        String buyerText = buyerDisc > 0 ? "（采购员让价 " + (int) Math.round(buyerDisc * 100) + "%）" : "";
        String supplyText = supplyDisc < 1.0 ? "（" + supplyName(def, s) + " " + (int) Math.round((1 - supplyDisc) * 100) + " 折）" : "";
        return "已进货 " + p.icon() + " " + p.name() + " ×" + qty + "，单价 "
                + Money.fmt(unitCost) + " " + def.meta().currency() + discountText + buyerText + supplyText
                + "，共 " + Money.fmt(total);
    }

    private String setPrice(GameDefinition def, GameState s, String productId, Double multiplier) {
        if (productId == null || multiplier == null) throw new GameException("参数缺失");
        ProductDef p = def.product(productId);
        if (p == null) throw new GameException("未知商品：" + productId);
        double m = clamp(multiplier, def.meta().priceMin(), def.meta().priceMax());
        s.getPriceMultiplier().put(productId, Money.round(m * 10) / 10.0);
        return p.name() + " 定价倍率 → " + s.getPriceMultiplier().get(productId)
                + "（售价 " + Money.fmt(p.baseRetailPrice() * s.getPriceMultiplier().get(productId)) + " " + def.meta().currency() + "）";
    }

    private String setAllPrice(GameDefinition def, GameState s, Double multiplier) {
        if (multiplier == null) throw new GameException("参数缺失");
        double m = clamp(multiplier, def.meta().priceMin(), def.meta().priceMax());
        for (var p : def.products()) {
            s.getPriceMultiplier().put(p.id(), Money.round(m * 10) / 10.0);
        }
        return "全部商品定价倍率 → " + m;
    }

    private String setSpeed(GameState s, Double speed) {
        if (speed == null || !TIME_SPEEDS.contains(speed)) {
            throw new GameException("不支持的倍速：" + speed + "（可选 0.5 / 1.0 / 1.5 / 2.0 / 3.0 / 5.0）");
        }
        s.setTimeSpeed(speed);
        s.setTickFraction(0);
        return "▶ 时间倍速 → " + speed + "×（现实 1 秒 = 游戏 " + speed + " 分钟）";
    }

    private String takeLoan(GameDefinition def, GameState s, String loanId) {
        if (loanId == null) throw new GameException("请选择贷款产品");
        LoanDef loan = def.loan(loanId);
        if (loan == null) throw new GameException("未知贷款产品：" + loanId);
        double limit = def.meta().debtLimit();
        if (s.getDebt() >= limit) {
            throw new GameException("负债已达上限（" + Money.fmtInt(limit) + "），无法继续借款");
        }
        double amount = Math.min(loan.maxAmount(), limit - s.getDebt());
        s.setCash(Money.round(s.getCash() + amount));
        s.setDebt(Money.round(s.getDebt() + amount));
        s.getStats().setTotalBorrowed(s.getStats().getTotalBorrowed() + amount);
        s.getLoans().add(new LoanState(loan.id(), loan.name(), amount, loan.monthlyRate(), s.getDate().getDayNumber()));
        return "已借入 " + loan.icon() + " " + loan.name() + " " + Money.fmt(amount) + " "
                + def.meta().currency() + "（月利率 " + (int) (loan.monthlyRate() * 100) + "%，按游戏日计息）";
    }

    private String repay(GameState s, Double amount) {
        if (s.getDebt() <= 0) return "当前没有负债";
        if (amount == null || amount <= 0) throw new GameException("还款金额必须大于 0");
        if (amount > s.getCash()) throw new GameException("现金不足，无法还款 " + Money.fmt(amount));
        double pay = Math.min(amount, s.getDebt());
        s.setCash(Money.round(s.getCash() - pay));
        s.setDebt(Money.round(s.getDebt() - pay));
        // 按时间顺序结清贷款本金
        double remain = pay;
        var it = s.getLoans().listIterator();
        while (it.hasNext()) {
            LoanState loan = it.next();
            if (loan.getPrincipal() <= 0) continue;
            double paid = Math.min(loan.getPrincipal(), remain);
            loan.setPrincipal(Money.round(loan.getPrincipal() - paid));
            remain = Money.round(remain - paid);
            if (loan.getPrincipal() <= 0) it.remove();
            if (remain <= 0) break;
        }
        return "已还款 " + Money.fmt(pay) + "，剩余负债 " + Money.fmt(s.getDebt());
    }

    private String renovate(GameDefinition def, GameState s, String upgradeId) {
        if (upgradeId == null) throw new GameException("请选择装修方案");
        UpgradeDef up = def.upgrade(upgradeId);
        if (up == null) throw new GameException("未知装修方案：" + upgradeId);
        if (s.getUpgradesOwned().contains(upgradeId)) {
            throw new GameException("该装修已完成");
        }
        if (s.getShopLevel() != up.level() - 1) {
            throw new GameException("请先完成上一级装修");
        }
        if (up.cost() > s.getCash()) {
            throw new GameException("现金不足：装修需要 " + Money.fmt(up.cost()) + " " + def.meta().currency());
        }
        s.setCash(Money.round(s.getCash() - up.cost()));
        s.getUpgradesOwned().add(upgradeId);
        s.setShopLevel(Math.max(s.getShopLevel(), up.level()));
        s.setReputation(clamp(s.getReputation() + up.reputationBonus(), 0, def.meta().maxReputation()));
        StringBuilder sb = new StringBuilder("完成装修 " + up.icon() + " " + up.name()
                + "（-" + Money.fmt(up.cost()) + " " + def.meta().currency() + "）");
        if (up.unlockCategories() != null && !up.unlockCategories().isEmpty()) {
            sb.append("，解锁分类：").append(String.join("、", up.unlockCategories()));
        }
        if (up.reputationBonus() > 0) sb.append("，声誉 +").append(up.reputationBonus());
        return sb.toString();
    }

    /** 主动限时促销：花广告费换未来 N 游戏分钟客流倍率 */
    private String promo(GameDefinition def, GameState s) {
        PromoDef p = def.gameplay() == null ? null : def.gameplay().promo();
        if (p == null || p.cost() <= 0) {
            throw new GameException("本游戏未配置限时促销玩法");
        }
        if (s.getPromoUntilMin() > s.getMinutes()) {
            long remain = s.getPromoUntilMin() - s.getMinutes();
            throw new GameException("促销仍在进行中（剩 " + remain + " 分钟），结束后才能再次发起");
        }
        if (p.cost() > s.getCash()) {
            throw new GameException("现金不足：促销广告费需要 " + Money.fmt(p.cost()) + " " + def.meta().currency());
        }
        s.setCash(Money.round(s.getCash() - p.cost()));
        s.setPromoUntilMin(s.getMinutes() + p.durationMin());
        s.setPromoMult(p.trafficMult());
        // 立即重算客流基准（含促销倍率），促销当天即刻生效（全部店铺）
        for (ShopState sh : s.getShops().values()) {
            sh.setDailyFootTraffic(computeFootTraffic(def, s, sh));
        }
        return "📣 限时促销已开始！花了 " + Money.fmt(p.cost()) + " " + def.meta().currency()
                + " 打广告，接下来 " + p.durationMin() + " 游戏分钟客流量 ×" + p.trafficMult()
                + "。记得备足货！";
    }

    // ------------------------------------------------------------
    // NPC 社交 / 贸易策略（批发 / 外卖）
    // ------------------------------------------------------------

    /** 与 NPC 互动：chat 闲聊 / gift 送小礼 / tea 请喝茶 等（由游戏文件 social 段定义） */
    private String social(GameDefinition def, GameState s, String npcId, String sub) {
        Map<String, SocialOptionDef> opts = def.gameplay() == null ? Map.of() : def.gameplay().social();
        if (opts.isEmpty()) throw new GameException("本游戏未配置 NPC 社交玩法");
        NpcDef n = def.npc(npcId);
        if (n == null) throw new GameException("未知街坊：" + npcId);
        SocialOptionDef o = sub == null ? null : opts.get(sub);
        if (o == null) {
            throw new GameException("未知互动方式：" + sub + "（可用：" + String.join(" / ", opts.keySet()) + "）");
        }
        long now = s.getMinutes();
        long cd = s.getSocialCooldowns().getOrDefault(npcId, 0L);
        if (cd > now) {
            throw new GameException(n.name() + " 刚才互动过，休息 " + (cd - now) + " 分钟后再来吧。");
        }
        if (o.cost() > 0) {
            if (s.getCash() < o.cost()) {
                throw new GameException("现金不足：" + o.name() + "需要 " + Money.fmt(o.cost()) + " " + def.meta().currency());
            }
            s.setCash(Money.round(s.getCash() - o.cost()));
        }
        NpcState st = s.getNpcs().get(npcId);
        st.setFavorability(Math.min(100, st.getFavorability() + o.favor()));
        if (o.rep() != 0) {
            s.setReputation(Math.max(0, Math.min(100, s.getReputation() + o.rep())));
        }
        s.getSocialCooldowns().put(npcId, now + o.cooldownMin());
        String flavor = (o.flavor() != null && !o.flavor().isBlank()) ? o.flavor() : n.name() + " 和你聊得很开心";
        return o.icon() + " " + flavor + "（" + n.name() + " 好感 +" + o.favor()
                + (o.rep() != 0 ? "，声誉 " + (o.rep() > 0 ? "+" : "") + o.rep() : "") + "，冷却 " + o.cooldownMin() + " 分钟）";
    }

    /** 贸易：wholesale 批发（小卖部）/ delivery 外卖（饭馆） */
    private String trade(GameDefinition def, GameState s, String sub, String productId, Integer qty) {
        TradeDef trade = def.gameplay() == null ? null : def.gameplay().trade();
        if (trade == null) throw new GameException("本游戏未配置贸易玩法");
        if (sub == null) throw new GameException("贸易格式：trade wholesale <商品id> <数量> 或 trade delivery on|off");
        if ("wholesale".equalsIgnoreCase(sub)) {
            WholesaleDef w = trade.wholesale();
            if (w == null || !w.enabled()) throw new GameException("本游戏未启用批发玩法");
            if (productId == null || qty == null || qty <= 0) {
                throw new GameException("批发格式：trade wholesale <商品id> <数量>（至少 " + w.minQty() + " 件起批）");
            }
            if (qty < w.minQty()) {
                throw new GameException(w.brokerName() + " 要求 " + w.minQty() + " 件起批（当前 " + qty + " 件）");
            }
            long now = s.getMinutes();
            if (s.getTradeCooldownUntil() > now) {
                throw new GameException(w.brokerName() + " 正在清点货款，剩 " + (s.getTradeCooldownUntil() - now) + " 分钟再来。");
            }
            ProductDef p = def.product(productId);
            if (p == null) throw new GameException("未知商品：" + productId);
            InventorySlot slot = s.getInventory().get(productId);
            int have = slot == null ? 0 : slot.getQty();
            if (have < qty) {
                throw new GameException(p.name() + " 库存不足（需 " + qty + " 件，现有 " + have + " 件）");
            }
            slot.setQty(slot.getQty() - qty);
            double income = qty * p.baseRetailPrice() * w.priceRatio();
            s.setCash(Money.round(s.getCash() + income));
            s.setTradeCooldownUntil(now + w.cooldownMin());
            s.getTodayEvents().add("🏷 批发：将 " + qty + " 件" + p.name() + "批发给 " + w.brokerName()
                    + "，收入 " + Money.fmt(income));
            return "🏷 批发成交：把 " + qty + " 件" + p.name() + "批发给 " + w.brokerName()
                    + "，入账 " + Money.fmt(income) + " " + def.meta().currency()
                    + "（下次批发冷却 " + w.cooldownMin() + " 分钟）";
        }
        if ("delivery".equalsIgnoreCase(sub)) {
            DeliveryDef dlv = trade.delivery();
            if (dlv == null || !dlv.enabled()) throw new GameException("本游戏未启用外卖玩法");
            if (productId == null) {
                if (s.isDeliveryOpen()) {
                    return "🛵 外卖平台运营中：今日 " + s.getDeliveryTodayOrders() + " 单 / 营收 "
                            + Money.fmt(s.getDeliveryTodayRevenue()) + " " + def.meta().currency()
                            + "；累计 " + s.getDeliveryTotalOrders() + " 单 / " + Money.fmt(s.getDeliveryTotalRevenue());
                }
                return "🛵 外卖平台已关闭。开通需 " + Money.fmt(dlv.setupCost()) + " " + def.meta().currency()
                        + "（trade delivery on 开通；开通后每日自动接 " + dlv.ordersMin() + "~" + dlv.ordersMax() + " 单）";
            }
            boolean on = "on".equalsIgnoreCase(productId);
            if (on && !s.isDeliveryOpen()) {
                if (s.getCash() < dlv.setupCost()) {
                    throw new GameException("现金不足：开通外卖需 " + Money.fmt(dlv.setupCost()) + " " + def.meta().currency());
                }
                s.setCash(Money.round(s.getCash() - dlv.setupCost()));
                s.setDeliveryOpen(true);
                return "🛵 外卖平台已开通！每日自动接入 " + dlv.ordersMin() + "~" + dlv.ordersMax() + " 单（平台抽成后按 ×"
                        + dlv.priceRatio() + " 结算），厨房产能被占用，堂食客流 ×" + (1 - dlv.trafficPenalty())
                        + "。打烊后自动结算。";
            }
            if (!on && s.isDeliveryOpen()) {
                s.setDeliveryOpen(false);
                return "🛵 外卖平台已关闭，堂食客流恢复。";
            }
            return s.isDeliveryOpen() ? "🛵 外卖平台已在运营中。" : "🛵 外卖平台本就处于关闭状态。";
        }
        throw new GameException("未知贸易操作：" + sub + "（可选：wholesale / delivery）");
    }

    // ------------------------------------------------------------
    // 日期 / 天气
    // ------------------------------------------------------------

    private void advanceDate(GameDefinition def, GameState s) {
        GameMeta meta = def.meta();
        GameDate d = s.getDate();
        d.setDayNumber(d.getDayNumber() + 1);
        int nextDay = d.getDay() + 1;
        if (nextDay > meta.daysPerSeason()) {
            d.setDay(1);
            int idx = def.seasonIndex(d.getSeasonId());
            String nextSeason = def.seasons().get((idx + 1) % def.seasons().size()).id();
            if (def.seasonIndex(nextSeason) == 0) {
                d.setYear(d.getYear() + 1);
            }
            d.setSeasonId(nextSeason);
        } else {
            d.setDay(nextDay);
        }
    }

    private String rollWeather(GameDefinition def, String seasonId) {
        int total = 0;
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (WeatherDef w : def.weathers()) {
            int wt = w.seasonWeight().getOrDefault(seasonId, 0);
            if (wt > 0) {
                weights.put(w.id(), wt);
                total += wt;
            }
        }
        if (total == 0) return def.weathers().get(0).id();
        int roll = random.nextInt(total);
        int acc = 0;
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            acc += e.getValue();
            if (roll < acc) return e.getKey();
        }
        return weights.keySet().iterator().next();
    }

    private WeatherDef weather(GameDefinition def, String id) {
        return def.weathers().stream().filter(x -> x.id().equals(id)).findFirst().orElse(def.weathers().get(0));
    }

    // ------------------------------------------------------------
    // 客流量与顾客购买
    // ------------------------------------------------------------

    private double computeFootTraffic(GameDefinition def, GameState s, ShopState shop) {
        if (s.isEnded()) return 0;
        GameMeta meta = def.meta();
        SeasonDef season = def.season(s.getDate().getSeasonId());
        WeatherDef w = weather(def, s.getWeatherId());

        double base = meta.baseFootTraffic();
        base *= season.footTrafficModifier();
        base *= w.footTrafficModifier();
        // 装修加成（每店独立）
        double upgradeMod = 1.0;
        int capacityBonus = 0;
        for (String uid : shop.getUpgradesOwned()) {
            UpgradeDef up = def.upgrade(uid);
            if (up == null) continue;
            upgradeMod += up.footTrafficBonus();
            capacityBonus += up.maxCustomersBonus();
        }
        base *= upgradeMod;
        // 声誉影响：声誉越高客流越多
        double repFactor = 0.6 + (s.getReputation() / Math.max(1, def.meta().maxReputation())) * 0.8;
        base *= repFactor;
        // buff
        base *= buffValue(s, Buff.Type.FOOT_TRAFFIC_MOD, 1.0);
        base += buffFlat(s, Buff.Type.FOOT_TRAFFIC_FLAT);
        // 地图地产：地段客流量加成（每店选址独立）
        LocationDef loc = locationOf(def, shop.getLocationId());
        if (loc != null && loc.trafficBonus() > 1.0) base *= loc.trafficBonus();
        // 随机波动 ±15%
        base *= 0.85 + random.nextDouble() * 0.3;
        double capped = Math.min(base, 25 + capacityBonus);
        // 主动限时促销：广告引流可突破平日客流上限
        if (s.getPromoUntilMin() > s.getMinutes() && s.getPromoMult() > 1.0) {
            capped *= s.getPromoMult();
        }
        // 外卖平台占用厨房产能：堂食客流下降
        if (s.isDeliveryOpen() && def.gameplay() != null && def.gameplay().trade() != null) {
            DeliveryDef dlv = def.gameplay().trade().delivery();
            if (dlv != null && dlv.enabled() && dlv.trafficPenalty() > 0) {
                capped *= (1 - dlv.trafficPenalty());
            }
        }
        // 促销员揽客：客流 ×(1 + Σ foot_traffic×效率)（该店自己的员工）
        double promoterBoost = staffParamSum(def, shop.getStaff(), "foot_traffic", 0, 0.4);
        if (promoterBoost > 0) {
            capped *= (1 + promoterBoost);
        }
        return Math.max(0, capped);
    }

    /** 单个顾客购买（作用于指定店铺的库存/定价） */
    private BuyResult simulateOneCustomer(GameDefinition def, GameState s, ShopState shop, double salesMod) {
        BuyResult result = new BuyResult();
        // 个人声望溢价：声望越高，来的顾客越体面、预算越足，也愿意为牌子多花钱（上限 +15%）
        double prestigeMult = prestigePriceMult(def, s);
        // v1.10.0 口碑：满意度越高顾客越愿意为口碑买单（±10%），且高评级有客单价加成
        double satMult = 1.0 + (s.getSatisfactionScore() - 60) / 400.0;
        double tierMult = s.getRatingTier() == 3 ? 1.08 : s.getRatingTier() == 2 ? 1.04 : 1.0;
        double budget = (3 + random.nextDouble() * 9) * prestigeMult * satMult * tierMult;
        // v1.11.0：节日气氛让顾客更愿意花钱（+10% 预算），销售技能拉高客单价
        if (festivalActive(s)) budget *= 1.10;
        budget *= 1.0 + staffSkillSum(def, s, "price_bonus");
        int want = 1 + (random.nextDouble() < 0.45 ? 1 : 0) + (random.nextDouble() < 0.15 ? 1 : 0);

        for (int n = 0; n < want; n++) {
            ProductDef pick = weightedPick(def, s, shop);
            if (pick == null) break;
            InventorySlot slot = shop.getInventory().get(pick.id());
            if (slot == null || slot.getQty() <= 0) break;

            double retail = Money.round(pick.baseRetailPrice() * effectivePriceMult(s, shop, pick.id()) * prestigeMult);
            int qty = 1 + (random.nextDouble() < 0.25 ? 1 : 0);
            // 顾客预算限制
            int affordable = (int) Math.floor(budget / retail);
            qty = Math.min(qty, affordable);
            if (qty <= 0) continue;
            qty = Math.min(qty, slot.getQty());
            if (qty <= 0) continue;

            double lineTotal = Money.round(retail * qty);
            slot.setQty(slot.getQty() - qty);
            budget -= lineTotal;
            result.revenue += lineTotal * salesMod;
            result.cogs += Money.round(pick.wholesalePrice() * qty);
            result.items += qty;
            result.spent += lineTotal;
            if (result.details.length() > 0) result.details.append("、");
            result.details.append(pick.icon()).append(pick.name()).append("×").append(qty);
        }
        result.revenue = Money.round(result.revenue);
        result.cogs = Money.round(result.cogs);
        return result;
    }

    /** 按需求权重随机挑选一种在售商品（该店库存） */
    private ProductDef weightedPick(GameDefinition def, GameState s, ShopState shop) {
        double total = 0;
        Map<String, Double> weights = new LinkedHashMap<>();
        SeasonDef season = def.season(s.getDate().getSeasonId());
        WeatherDef w = weather(def, s.getWeatherId());
        for (var e : shop.getInventory().entrySet()) {
            InventorySlot slot = e.getValue();
            if (slot.getQty() <= 0) continue;
            ProductDef p = def.product(e.getKey());
            if (p == null) continue;
            double wgt = demandWeight(p, season, w, priceOf(shop, p.id()));
            // 爆款热卖：该商品需求权重放大
            if (p.id().equals(s.getHotProductId()) && s.getHotUntilMin() > s.getMinutes()) {
                HotItemDef h = def.gameplay() == null ? null : def.gameplay().hotItem();
                double mult = h == null ? 2.5 : h.demandMult();
                wgt *= mult;
            }
            // v1.11.0：节日特定商品需求暴涨
            if (festivalActive(s)) {
                FestivalState fs = s.getFestival();
                FestivalDef fd = festivalCfg(def);
                FestivalItemDef f = fd == null ? null : festivalById(fd, fs.getId());
                if (f != null) {
                    wgt *= f.demand().getOrDefault(p.id(), 1.0);
                }
            }
            if (wgt > 0) {
                weights.put(p.id(), wgt);
                total += wgt;
            }
        }
        if (total <= 0) return null;
        double roll = random.nextDouble() * total;
        double acc = 0;
        for (var e : weights.entrySet()) {
            acc += e.getValue();
            if (roll < acc) return def.product(e.getKey());
        }
        return def.product(weights.keySet().iterator().next());
    }

    /**
     * 需求权重 = 人气 × 季节需求 × 天气需求 × 价格弹性。
     * 价格弹性：倍率 1.0 时需求最高；定价越高顾客越少，但单件利润更高。
     */
    private double demandWeight(ProductDef p, SeasonDef season, WeatherDef w, double priceMult) {
        double d = p.popularity() / 100.0;
        d *= season.demand() == null ? 1.0 : season.demand().getOrDefault(p.id(), 1.0);
        d *= w.demand() == null ? 1.0 : w.demand().getOrDefault(p.id(), 1.0);
        if (p.seasonDemand() != null) {
            d *= p.seasonDemand().getOrDefault(season.id(), 1.0);
        }
        if (p.weatherDemand() != null) {
            d *= p.weatherDemand().getOrDefault(w.id(), 1.0);
        }
        double priceFactor = 1.15 - (priceMult - 1.0) * 0.55;
        priceFactor = clamp(priceFactor, 0.25, 1.6);
        d *= priceFactor;
        return Math.max(0.0001, d);
    }

    private double priceOf(ShopState shop, String productId) {
        double m = shop.getPriceMultiplier().getOrDefault(productId, 1.0);
        return m;
    }

    /** 当前店售价倍率（价格战期间全店 ×0.9） */
    private double effectivePriceMult(GameState s, ShopState shop, String productId) {
        double m = priceOf(shop, productId);
        if (s.getPriceWarUntilMin() > s.getMinutes()) m *= 0.9;
        return m;
    }

    private double hourFactor(int hour) {
        if (hour < 6) return 0;        // 00:00-06:00 打烊
        if (hour < 8) return 1.0;      // 早市
        if (hour < 11) return 1.2;
        if (hour < 14) return 1.6;     // 午高峰
        if (hour < 17) return 1.1;
        if (hour < 20) return 1.5;     // 晚高峰
        return 0.8;                    // 20:00-24:00
    }

    private String periodOf(int hour) {
        if (hour < 11) return "早";
        if (hour < 17) return "午";
        return "晚";
    }

    /** 单个顾客购买结果（可变） */
    private static class BuyResult {
        double revenue;
        double cogs;
        int items;
        double spent;
        /** 购买明细，如 "🥤可乐×2"（用于实时日志） */
        StringBuilder details = new StringBuilder();
    }

    // ------------------------------------------------------------
    // NPC 来访 / 好感度 / 评价
    // ------------------------------------------------------------

    private void simulateNpcs(GameDefinition def, GameState s, DayReport r) {
        for (NpcDef npc : def.npcs()) {
            NpcState ns = s.getNpcs().computeIfAbsent(npc.id(), k -> new NpcState());
            double chance = npc.visitChance();
            if (r.isClosed()) chance *= 0.35;
            if (random.nextDouble() >= chance) continue;

            ns.setVisits(ns.getVisits() + 1);
            ns.setLastVisitDay(s.getDate().getDayNumber());
            int before = (int) Math.round(ns.getFavorability());

            boolean favStocked = favoriteStocked(def, s, npc);
            int delta = 0;
            StringBuilder reason = new StringBuilder();

            // 喜欢的货是否在架（幅度 ±3，让好感变化可见）
            if (favStocked) {
                delta += 3;
                reason.append("喜欢的货有备");
            } else {
                delta -= 3;
                reason.append("喜欢的货缺货");
            }
            // 价格敏感度
            double avgMult = averageFavPrice(s, npc);
            if (avgMult >= 0) {
                double tolerant = 1.05 + (1 - npc.priceSensitivity()) * 0.2;
                double harsh = 1.30 + (1 - npc.priceSensitivity()) * 0.25;
                if (avgMult <= tolerant) {
                    delta += 1;
                    reason.append("，价格公道");
                } else if (avgMult >= harsh) {
                    delta -= 3;
                    reason.append("，嫌贵");
                }
            }
            // 店面
            if (s.getShopLevel() >= 4) {
                delta += 1;
                reason.append("，店面敞亮");
            } else if (s.getShopLevel() == 1 && ns.getVisits() > 3) {
                delta -= 1;
                reason.append("，店面简陋");
            }
            // 商品丰富度
            int stockedCats = stockedCategoryCount(def, s);
            int totalCats = def.categoryList().size();
            if (stockedCats >= totalCats - 1) {
                delta += 1;
                reason.append("，货全");
            }

            ns.setFavorability(clamp(ns.getFavorability() + delta, 0, 100));
            int after = (int) Math.round(ns.getFavorability());

            String comment = pickNpcComment(def, npc, before, after, favStocked);
            ns.setLastComment(comment);

            NpcComment nc = new NpcComment();
            nc.setNpcId(npc.id());
            nc.setNpcName(npc.name());
            nc.setIcon(npc.icon());
            nc.setPeriod(npc.period());
            nc.setFavorBefore(before);
            nc.setFavorAfter(after);
            nc.setComment(comment);
            r.getNpcComments().add(nc);
        }
    }

    /** 外卖订单结算（饭馆等餐饮型店铺）：打烊时按平台策略生成订单，占用当日库存 */
    private void deliveryOrders(GameDefinition def, GameState s, DayReport r) {
        TradeDef trade = def.gameplay() == null ? null : def.gameplay().trade();
        if (trade == null) return;
        DeliveryDef dlv = trade.delivery();
        if (dlv == null || !dlv.enabled() || !s.isDeliveryOpen()) return;
        // 有库存的在售商品
        List<ProductDef> avail = new ArrayList<>();
        for (ProductDef p : def.products()) {
            InventorySlot slot = s.getInventory().get(p.id());
            if (slot != null && slot.getQty() > 0) avail.add(p);
        }
        if (avail.isEmpty()) {
            r.getNotices().add("🛵 外卖平台今日无单可接：后厨没菜了。");
            return;
        }
        int target = dlv.ordersMin() + random.nextInt(Math.max(1, dlv.ordersMax() - dlv.ordersMin() + 1));
        // 骑手加成：每日单量 +Σ delivery_orders×效率
        target += (int) Math.round(staffParamSum(def, s, "delivery_orders", 0, 20)
                + staffSkillSum(def, s, "delivery_bonus"));
        int placed = 0;
        double rev = 0, cost = 0;
        int items = 0;
        for (int i = 0; i < target; i++) {
            ProductDef p = avail.get(random.nextInt(avail.size()));
            int want = 1 + random.nextInt(3);
            InventorySlot slot = s.getInventory().get(p.id());
            int take = Math.min(want, slot.getQty());
            if (take <= 0) continue;
            slot.setQty(slot.getQty() - take);
            rev += take * p.baseRetailPrice() * dlv.priceRatio();
            cost += take * p.wholesalePrice();
            items += take;
            placed++;
        }
        if (placed == 0) return;
        rev = Money.round(rev);
        cost = Money.round(cost);
        double profit = Money.round(rev - cost);
        s.setCash(Money.round(s.getCash() + profit));
        s.setDeliveryTodayOrders(placed);
        s.setDeliveryTodayRevenue(rev);
        s.setDeliveryTodayCost(cost);
        s.setDeliveryTotalOrders(s.getDeliveryTotalOrders() + placed);
        s.setDeliveryTotalRevenue(Money.round(s.getDeliveryTotalRevenue() + rev));
        // 计入当日营业额（堂食 + 外卖）与统计
        s.setTodayRevenue(Money.round(s.getTodayRevenue() + rev));
        s.setTodayCost(Money.round(s.getTodayCost() + cost));
        s.setTodayItems(s.getTodayItems() + items);
        s.setTodayCustomers(s.getTodayCustomers() + placed);
        Stats st = s.getStats();
        st.setTotalRevenue(Money.round(st.getTotalRevenue() + rev));
        st.setTotalCost(Money.round(st.getTotalCost() + cost));
        st.setTotalProfit(Money.round(st.getTotalProfit() + profit));
        st.setItemsSold(st.getItemsSold() + items);
        st.setCustomersServed(st.getCustomersServed() + placed);
        // 平台评价（好评 / 差评；店长把关可扩大好评概率）
        double reviewBonus = staffParamSum(def, s, "review_bonus", 0, 0.4)
                + staffSkillSum(def, s, "review_bonus");
        double goodChance = Math.min(1, dlv.reviewChance() * 2 + reviewBonus);
        double roll = random.nextDouble();
        if (roll < dlv.reviewChance()) {
            s.setReputation(Math.max(0, s.getReputation() - 1));
            s.setDeliveryReviews(s.getDeliveryReviews() - 1);
            r.getNotices().add("🛵 外卖收到一条差评：出餐太慢。（声誉 -1）");
        } else if (roll < goodChance) {
            s.setReputation(Math.min(100, s.getReputation() + 1));
            s.setDeliveryReviews(s.getDeliveryReviews() + 1);
            r.getNotices().add("🛵 外卖收到一条好评：包装用心！（声誉 +1）");
        }
        r.getNotices().add("🛵 外卖平台今日接入 " + placed + " 单（" + items + " 份菜），营收 "
                + Money.fmt(rev) + "，食材成本 " + Money.fmt(cost) + "，净利 " + Money.fmt(profit) + "。");
    }

    /** NPC 社交福利：好感 ≥50 可能带客上门，好感 ≥80 可能帮忙宣传（作用于新一天） */
    private void npcSocialPerks(GameDefinition def, GameState s, DayReport r) {
        if (s.getNpcs().isEmpty()) return;
        // 个人声望放大街坊福利：每档 +5% 概率（最高翻倍）
        double prestigeBoost = 1.0 + Math.min(1.0, prestigeLevel(computePrestige(def, s)) * 0.2);
        int bonus = 0;
        int rep = 0;
        for (Map.Entry<String, NpcState> e : s.getNpcs().entrySet()) {
            NpcState ns = e.getValue();
            NpcDef npc = def.npc(e.getKey());
            String name = npc == null ? e.getKey() : npc.name();
            double fav = ns.getFavorability();
            if (fav >= 80 && random.nextDouble() < Math.min(0.75, 0.15 * prestigeBoost)) {
                rep++;
                r.getNotices().add("📢 " + name + " 逢人便夸你家东西好，替你打了免费广告！（声誉 +1）");
            } else if (fav >= 50 && random.nextDouble() < Math.min(0.85, 0.25 * prestigeBoost)) {
                int k = 2 + random.nextInt(3);
                bonus += k;
                r.getNotices().add("👫 好感福利：" + name + " 带了 " + k + " 位朋友来光顾！");
            }
        }
        if (rep > 0) {
            s.setReputation(Math.min(100, s.getReputation() + rep));
            r.setReputationAfter(Money.round(s.getReputation()));
        }
        if (bonus > 0) {
            s.setNpcVisitBonus(bonus);
            s.setDailyFootTraffic(s.getDailyFootTraffic() + bonus);
            r.getNotices().add("✨ 街坊带客 +" + bonus + " 客流（今日有效）");
        }
    }

    private boolean favoriteStocked(GameDefinition def, GameState s, NpcDef npc) {
        if (npc.favoriteProducts() != null) {
            for (String pid : npc.favoriteProducts()) {
                InventorySlot slot = s.getInventory().get(pid);
                if (slot != null && slot.getQty() > 0) return true;
            }
        }
        if (npc.favoriteCategories() != null) {
            for (String cat : npc.favoriteCategories()) {
                for (var e : s.getInventory().entrySet()) {
                    ProductDef p = def.product(e.getKey());
                    if (p != null && p.category().equals(cat) && e.getValue().getQty() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private double averageFavPrice(GameState s, NpcDef npc) {
        List<Double> mults = new ArrayList<>();
        if (npc.favoriteProducts() != null) {
            for (String pid : npc.favoriteProducts()) {
                mults.add(priceOf(s.currentShop(), pid));
            }
        }
        if (mults.isEmpty()) return -1;
        return mults.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
    }

    private int stockedCategoryCount(GameDefinition def, GameState s) {
        java.util.Set<String> cats = new java.util.HashSet<>();
        for (var e : s.getInventory().entrySet()) {
            ProductDef p = def.product(e.getKey());
            if (p != null && e.getValue().getQty() > 0) {
                cats.add(p.category());
            }
        }
        return cats.size();
    }

    private String pickNpcComment(GameDefinition def, NpcDef npc, int before, int after, boolean favStocked) {
        String tier = favorTier(before);
        String tierAfter = favorTier(after);
        // 缺货抱怨优先
        if (!favStocked && npc.special() != null && npc.special().get("missing") != null
                && !npc.special().get("missing").isEmpty()) {
            List<String> lines = npc.special().get("missing");
            return lines.get(random.nextInt(lines.size()));
        }
        // 好感提升时优先用高一档台词
        String useTier = tierAfter;
        if (tierIndex(tierAfter) > tierIndex(tier) && npc.comments().containsKey(tierAfter)) {
            useTier = tierAfter;
        }
        List<String> lines = npc.comments().getOrDefault(useTier,
                npc.comments().getOrDefault("normal", List.of("……")));
        return lines.get(random.nextInt(lines.size()));
    }

    private String favorTier(double fav) {
        if (fav >= 80) return "loyal";
        if (fav >= 60) return "regular";
        if (fav >= 35) return "normal";
        return "cold";
    }

    private int tierIndex(String tier) {
        return switch (tier) {
            case "loyal" -> 3;
            case "regular" -> 2;
            case "normal" -> 1;
            default -> 0;
        };
    }

    // ------------------------------------------------------------
    // 库存过期
    // ------------------------------------------------------------

    private void expireInventory(GameDefinition def, GameState s, DayReport r) {
        List<String> removed = new ArrayList<>();
        int expiredCount = 0;
        // v1.9.0：逐店清理过期库存
        for (ShopState sh : s.getShops().values()) {
            var it = sh.getInventory().entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                InventorySlot slot = e.getValue();
                if (slot.getQty() <= 0) {
                    it.remove();
                    continue;
                }
                slot.setShelfLifeRemaining(slot.getShelfLifeRemaining() - 1);
                if (slot.getShelfLifeRemaining() <= 0) {
                    ProductDef p = def.product(e.getKey());
                    removed.add((p == null ? e.getKey() : p.name()) + "【" + sh.getName() + "】");
                    expiredCount += slot.getQty();
                    it.remove();
                }
            }
        }
        if (!removed.isEmpty()) {
            s.getStats().setExpiredItems(s.getStats().getExpiredItems() + expiredCount);
            s.setTodayExpiredQty(s.getTodayExpiredQty() + expiredCount);
            r.getExpiries().add("有商品过期被丢弃：" + String.join("、", removed)
                    + "（共 " + expiredCount + " 件）");
        }
    }

    // ------------------------------------------------------------
    // 随机事件
    // ------------------------------------------------------------

    private void rollEvents(GameDefinition def, GameState s) {
        for (EventDef ev : def.events()) {
            double chance = ev.chance() * (1 - suppressStrength(def, s, ev.id()));
            if (random.nextDouble() < chance) {
                applyEvent(def, s, ev);
            }
        }
    }

    private void applyEvent(GameDefinition def, GameState s, EventDef ev) {
        StringBuilder sb = new StringBuilder();
        sb.append(ev.icon()).append(" ").append(ev.name()).append("：").append(ev.flavor());
        List<String> effects = new ArrayList<>();
        if (ev.cashDeltaMin() != 0 || ev.cashDeltaMax() != 0) {
            double min = Math.min(ev.cashDeltaMin(), ev.cashDeltaMax());
            double max = Math.max(ev.cashDeltaMin(), ev.cashDeltaMax());
            double delta = Money.round(min + random.nextDouble() * (max - min));
            s.setCash(Math.max(0, Money.round(s.getCash() + delta)));
            effects.add((delta >= 0 ? "现金 +" : "现金 ") + Money.fmt(delta) + " " + def.meta().currency());
        }
        if (ev.reputationDelta() != 0) {
            s.setReputation(clamp(s.getReputation() + ev.reputationDelta(), 0, def.meta().maxReputation()));
            effects.add("声誉 " + (ev.reputationDelta() > 0 ? "+" : "") + ev.reputationDelta());
        }
        if (ev.footTrafficFlat() != 0) {
            s.getBuffs().add(new Buff(Buff.Type.FOOT_TRAFFIC_FLAT, ev.footTrafficFlat(), 1));
            effects.add("今日客流量 " + (ev.footTrafficFlat() > 0 ? "+" : "") + ev.footTrafficFlat() + " 人");
        }
        if (ev.footTrafficMod() != 1.0) {
            s.getBuffs().add(new Buff(Buff.Type.FOOT_TRAFFIC_MOD, ev.footTrafficMod(), Math.max(1, ev.buffDays())));
            effects.add("客流量 ×" + ev.footTrafficMod() + "（" + Math.max(1, ev.buffDays()) + " 天）");
        }
        if (ev.salesMod() != 1.0) {
            s.getBuffs().add(new Buff(Buff.Type.SALES_MOD, ev.salesMod(), Math.max(1, ev.buffDays())));
            effects.add("营业额 ×" + ev.salesMod() + "（" + Math.max(1, ev.buffDays()) + " 天）");
        }
        if (ev.restockDiscount() != 1.0) {
            s.getBuffs().add(new Buff(Buff.Type.RESTOCK_DISCOUNT, ev.restockDiscount(), Math.max(1, ev.buffDays())));
            effects.add("进货 " + (int) Math.round(ev.restockDiscount() * 10) + " 折（" + Math.max(1, ev.buffDays()) + " 天）");
        }
        if (!effects.isEmpty()) {
            sb.append("  [").append(String.join("，", effects)).append("]");
        }
        s.getTodayEvents().add(sb.toString());
        // 随机事件发生时推送到实时终端流（如小偷光顾）
        pushLive(s, "🔔 " + sb);
    }

    // ------------------------------------------------------------
    // 玩法扩展：今日晨报 / 爆款热卖 / 成就
    // ------------------------------------------------------------

    /** 每日清晨必出一条晨报（效果复用 buff，持续当天） */
    private void rollDailyBrief(GameDefinition def, GameState s) {
        if (def.gameplay() == null) return;
        DailyBriefDef b = def.gameplay().rollBrief(random);
        if (b == null) return;
        s.setDailyBrief(b.title());
        StringBuilder sb = new StringBuilder(b.icon()).append(" 今日晨报：").append(b.title());
        if (b.flavor() != null && !b.flavor().isBlank()) sb.append("（").append(b.flavor()).append("）");
        List<String> effects = new ArrayList<>();
        if (b.footTrafficMod() != 1.0) {
            s.getBuffs().add(new Buff(Buff.Type.FOOT_TRAFFIC_MOD, b.footTrafficMod(), 1));
            effects.add("客流量 ×" + b.footTrafficMod());
        }
        if (b.salesMod() != 1.0) {
            s.getBuffs().add(new Buff(Buff.Type.SALES_MOD, b.salesMod(), 1));
            effects.add("营业额 ×" + b.salesMod());
        }
        if (b.restockDiscount() != 1.0) {
            s.getBuffs().add(new Buff(Buff.Type.RESTOCK_DISCOUNT, b.restockDiscount(), 1));
            effects.add("进货 " + (int) Math.round(b.restockDiscount() * 10) + " 折");
        }
        if (!effects.isEmpty()) sb.append("  [").append(String.join("，", effects)).append("]");
        s.getTodayEvents().add(sb.toString());
    }

    /** 爆款热卖：概率随机选中一件有库存的在售商品，限时需求翻倍 */
    private void rollHotItem(GameDefinition def, GameState s) {
        if (def.gameplay() == null || def.gameplay().hotItem() == null) return;
        HotItemDef h = def.gameplay().hotItem();
        if (random.nextDouble() >= h.chance()) return;
        List<String> candidates = new ArrayList<>();
        for (var e : s.getInventory().entrySet()) {
            if (e.getValue().getQty() > 0 && def.product(e.getKey()) != null) {
                candidates.add(e.getKey());
            }
        }
        if (candidates.isEmpty()) return;
        String pid = candidates.get(random.nextInt(candidates.size()));
        ProductDef p = def.product(pid);
        s.setHotProductId(pid);
        s.setHotUntilMin(s.getMinutes() + h.durationMin());
        s.setHotCount(s.getHotCount() + 1);
        s.getTodayEvents().add("🔥 爆款热卖：" + p.icon() + " " + p.name() + " 突然走红！"
                + " 接下来 " + h.durationMin() + " 分钟需求量 ×" + h.demandMult() + "，赶紧补货/提价。");
    }

    /** 成就检查：遍历定义，未解锁的按类型判定；达标则解锁并在终端庆祝 */
    private void checkAchievements(GameDefinition def, GameState s) {
        if (def.achievementsOrEmpty().isEmpty()) return;
        Stats st = s.getStats();
        double netWorth = netWorth(def, s);
        // 连续盈利天数（从近到远）
        int profitStreak = 0;
        for (int i = s.getDayHistory().size() - 1; i >= 0; i--) {
            if (s.getDayHistory().get(i).getProfit() > 0) profitStreak++;
            else break;
        }
        double maxFavor = s.getNpcs().values().stream()
                .mapToDouble(n -> n.getFavorability()).max().orElse(0);
        for (AchievementDef a : def.achievementsOrEmpty()) {
            if (s.getAchievements().containsKey(a.id())) continue;
            boolean ok = switch (a.type()) {
                case "first_sale" -> st.getItemsSold() >= (int) a.value();
                case "revenue_day" -> st.getBestDayRevenue() >= a.value();
                case "profit_streak" -> profitStreak >= (int) a.value();
                case "favor_any" -> maxFavor >= a.value();
                case "reputation" -> s.getReputation() >= a.value();
                case "upgrades" -> s.getUpgradesOwned().size() >= (int) a.value();
                case "debt_clear" -> s.getDebt() <= 0 && st.getTotalBorrowed() > 0;
                case "net_worth" -> netWorth >= a.value();
                case "days" -> st.getDaysPlayed() >= (int) a.value();
                case "hot_count" -> s.getHotCount() >= (int) a.value();
                case "staff_count" -> staffCount(def, s) >= (int) a.value();
                case "bank_interest" -> s.getBankInterestEarned() >= a.value();
                case "quest_done" -> s.getQuestsCompleted() >= (int) a.value();
                case "relocate" -> s.getRelocations() >= (int) a.value();
                case "prestige" -> computePrestige(def, s) >= a.value();
                case "festival_count" -> st.getFestivalCount() >= (int) a.value();
                case "festival_bonus" -> st.getFestivalBonusEarned() >= a.value();
                case "staff_promote" -> s.getStaff().values().stream().anyMatch(StaffState::isPromoted);
                case "asset" -> s.getOwnedAssets().size() >= (int) a.value();
                case "supply_max_tier" -> supplyMaxTier(def, s);
                case "rating_tier" -> s.getRatingTier() >= (int) a.value();
                case "competitor_closed" -> s.getCompetitors().stream().anyMatch(CompetitorState::isClosed);
                default -> false;
            };
            if (ok) {
                s.getAchievements().put(a.id(), true);
                s.getTodayEvents().add("🏆 成就解锁：" + a.icon() + " " + a.name()
                        + " —— " + a.desc());
            }
        }
    }

    private double buffValue(GameState s, Buff.Type type, double defaultValue) {
        double v = defaultValue;
        for (Buff b : s.getBuffs()) {
            if (b.getType() == type) v *= b.getValue();
        }
        return v;
    }

    private int buffFlat(GameState s, Buff.Type type) {
        int v = 0;
        for (Buff b : s.getBuffs()) {
            if (b.getType() == type) v += (int) b.getValue();
        }
        return v;
    }

    private double maxLoanRate(GameState s) {
        double r = 0;
        for (LoanState loan : s.getLoans()) {
            r = Math.max(r, loan.getMonthlyRate());
        }
        return r;
    }

    // ------------------------------------------------------------
    // 声誉漂移
    // ------------------------------------------------------------

    private void driftReputation(GameDefinition def, GameState s, DayReport r) {
        double rep = 0;
        // 有成交 + 基础
        if (r.getRevenue() > 0) rep += 0.3;
        // 货品丰富度
        int stockedCats = stockedCategoryCount(def, s);
        int totalCats = def.categoryList().size();
        rep += (stockedCats / (double) Math.max(1, totalCats)) * 0.5;
        // 定价合理性
        double avgMult = s.getPriceMultiplier().values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(1.0);
        if (Math.abs(avgMult - 1.0) <= 0.15) {
            rep += 0.2;
        } else if (avgMult > 1.35) {
            rep -= 0.25;
        }
        // 过期损耗
        if (!r.getExpiries().isEmpty()) {
            rep -= 0.1 * r.getExpiries().size();
        }
        // 装修自动声誉
        for (String uid : s.getUpgradesOwned()) {
            UpgradeDef up = def.upgrade(uid);
            if (up != null) rep += up.autoReputationPerDay();
        }
        // 事件 buff
        rep += buffValue(s, Buff.Type.REPUTATION_DAY, 0.0);

        double before = s.getReputation();
        s.setReputation(clamp(s.getReputation() + rep, 0, def.meta().maxReputation()));
        double after = s.getReputation();
        if (Math.abs(after - before) >= 0.5) {
            r.getNotices().add("声誉 " + (after >= before ? "+" : "")
                    + Money.fmt(after - before) + " → " + Money.fmt(after));
        }
    }

    // ------------------------------------------------------------
    // 胜负判定
    // ------------------------------------------------------------

    public double netWorth(GameDefinition def, GameState s) {
        double invValue = 0;
        for (ShopState sh : s.getShops().values()) {
            for (var e : sh.getInventory().entrySet()) {
                ProductDef p = def.product(e.getKey());
                if (p != null) {
                    invValue += p.wholesalePrice() * e.getValue().getQty();
                }
            }
        }
        double financeValue = 0;
        for (FinanceState f : s.getFinances()) financeValue += f.getAmount();
        return Money.round(s.getCash() + s.getBankBalance() + invValue + financeValue - s.getDebt());
    }

    private void checkEnd(GameDefinition def, GameState s, DayReport r) {
        if (s.isEnded()) return;

        // 破产判定
        double limit = def.lose() == null ? def.meta().debtLimit() : def.lose().debtLimit();
        int grace = def.lose() == null ? def.meta().loanGraceDays() : def.lose().graceDays();
        if (s.getDebt() > limit) {
            s.setDebtOverLimitDays(s.getDebtOverLimitDays() + 1);
            if (s.getDebtOverLimitDays() >= grace) {
                end(s, r, "lose", "负债 " + Money.fmt(s.getDebt()) + " 超过上限，连续 "
                        + grace + " 天资不抵债，店铺破产倒闭……");
                return;
            }
            r.getNotices().add("⚠ 负债已达 " + Money.fmt(s.getDebt()) + "，超过安全线！"
                    + (grace - s.getDebtOverLimitDays()) + " 天内必须还款，否则破产！");
        } else {
            s.setDebtOverLimitDays(0);
        }

        // 胜利判定
        if (def.win() != null && "networth".equals(def.win().type())) {
            double nw = netWorth(def, s);
            if (s.getDate().getYear() >= def.win().byYear() && nw >= def.win().targetNetWorth()) {
                end(s, r, "win", "第 " + s.getDate().getYear() + " 年，净资产达到 "
                        + Money.fmt(def.win().targetNetWorth()) + "！你的小店成为了街角传奇！");
            }
        }
    }

    private void end(GameState s, DayReport r, String type, String message) {
        s.setEnded(true);
        s.setEndType(type);
        s.setEndMessage(message);
        r.setGameEnded(true);
        r.setEndType(type);
        r.setEndMessage(message);
    }

    // ------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ============================================================
    // v1.10.0 供应链系统
    // ============================================================

    private SupplyDef supplyOf(GameDefinition def) {
        return def.gameplay() == null ? null : def.gameplay().supply();
    }

    private String supplyName(GameDefinition def, GameState s) {
        SupplyDef sp = supplyOf(def);
        return sp == null || !sp.enabled() ? "供应商" : sp.supplierName();
    }

    /** 当前供应商折扣（未启用供应链时为 1.0） */
    private double supplyDiscount(GameDefinition def, GameState s) {
        SupplyDef sp = supplyOf(def);
        if (sp == null || !sp.enabled()) return 1.0;
        return sp.discountAt(s.getSupplyPoints());
    }

    /** 是否已到战略伙伴（解锁独家商品） */
    private boolean supplyMaxTier(GameDefinition def, GameState s) {
        SupplyDef sp = supplyOf(def);
        return sp != null && sp.enabled() && sp.isMaxTier(s.getSupplyPoints());
    }

    /** 查看供应链（命令 supply） */
    private String supplyView(GameDefinition def, GameState s) {
        SupplyDef sp = supplyOf(def);
        if (sp == null || !sp.enabled()) throw new GameException("本游戏未配置供应链玩法");
        int idx = sp.tierIndex(s.getSupplyPoints());
        SupplyTierDef cur = sp.tier(idx);
        StringBuilder sb = new StringBuilder();
        sb.append("📦 ").append(sp.supplierName()).append("：累计合作值 ").append(Money.fmtInt(s.getSupplyPoints()))
                .append("，当前等级【").append(cur.name()).append("】（进货 ").append((int) Math.round((1 - cur.discount()) * 100))
                .append(" 折）");
        if (idx + 1 < sp.tiers().size()) {
            SupplyTierDef next = sp.tiers().get(idx + 1);
            sb.append("；升级到【").append(next.name()).append("】还需合作值 ").append(next.reqPoints() - (int) s.getSupplyPoints());
        }
        if (cur.creditLimit() > 0) {
            sb.append("；可赊账额度 ").append(Money.fmtInt(cur.creditLimit()))
                    .append("，已用 ").append(Money.fmt(s.getCreditUsed()));
            if (s.getCreditUsed() > 0) {
                sb.append("，到期 ").append(s.clockText()).append("（第").append(s.dayOf()).append("天）");
            }
        }
        if (sp.isMaxTier(s.getSupplyPoints()) && !sp.exclusiveProducts().isEmpty()) {
            sb.append("；已解锁独家商品：");
            List<String> names = new ArrayList<>();
            for (String pid : sp.exclusiveProducts()) {
                ProductDef p = def.product(pid);
                if (p != null) names.add(p.icon() + p.name() + "（毛利 " + (int) Math.round((p.baseRetailPrice() - p.wholesalePrice()) / p.wholesalePrice() * 100) + "%）");
            }
            sb.append(String.join("、", names));
        } else if (!sp.exclusiveProducts().isEmpty()) {
            SupplyTierDef top = sp.tiers().get(sp.tiers().size() - 1);
            sb.append("；达成【").append(top.name()).append("】可解锁独家商品");
        }
        return sb.toString();
    }

    /** 赊账进货（命令 buy_credit <商品id> <数量>；金牌客户以上可用，账期 30 游戏天） */
    private String buyCredit(GameDefinition def, GameState s, String productId, Integer qty) {
        SupplyDef sp = supplyOf(def);
        if (sp == null || !sp.enabled()) throw new GameException("本游戏未配置供应链玩法");
        if (productId == null || qty == null || qty <= 0) {
            throw new GameException("赊账进货格式：buy_credit <商品id> <数量>");
        }
        ProductDef p = def.product(productId);
        if (p == null) throw new GameException("未知商品：" + productId);
        if (p.unlockUpgradeId() != null && !s.getUpgradesOwned().contains(p.unlockUpgradeId())) {
            throw new GameException("【" + p.name() + "】尚未解锁，先完成对应装修吧");
        }
        if (sp.exclusiveProducts().contains(productId) && !sp.isMaxTier(s.getSupplyPoints())) {
            throw new GameException("【" + p.name() + "】是 " + sp.supplierName()
                    + " 的战略伙伴专供，先累计合作值到最高等级（supply 查看）");
        }
        double limit = sp.creditLimitAt(s.getSupplyPoints());
        if (limit <= 0) {
            throw new GameException("当前供应商等级不支持赊账，先提升合作值到金牌客户以上");
        }
        if (s.getCreditUsed() > 0 && s.getMinutes() < s.getCreditDueMin()) {
            throw new GameException("上一笔赊账尚未到期（" + (s.getCreditDueMin() - s.getMinutes())
                    + " 分钟后），到期自动扣款后再赊");
        }
        double buyerDisc = staffParamSum(def, s, "buy_discount", 0, 0.2) + staffSkillSum(def, s, "discount_bonus");
        double supplyDisc = supplyDiscount(def, s);
        double unitCost = Money.round(p.wholesalePrice() * (1 - buyerDisc) * supplyDisc);
        double total = Money.round(unitCost * qty);
        if (s.getCreditUsed() + total > limit) {
            throw new GameException("赊账额度不足：需 " + Money.fmt(total) + " " + def.meta().currency()
                    + "，可用额度 " + Money.fmt(limit - s.getCreditUsed()));
        }
        s.setCreditUsed(Money.round(s.getCreditUsed() + total));
        s.setCreditDueMin(s.getMinutes() + sp.creditTermMin());
        // 赊账也计入合作值（供应商升级不只看现金进货）
        s.setSupplyPoints(Money.round(s.getSupplyPoints() + total));
        InventorySlot slot = s.getInventory().computeIfAbsent(productId,
                k -> new InventorySlot(productId, 0, p.shelfLifeDays()));
        slot.setQty(slot.getQty() + qty);
        slot.setShelfLifeRemaining(p.shelfLifeDays());
        return "🤝 赊账进货 " + p.icon() + " " + p.name() + " ×" + qty + "，赊账 "
                + Money.fmt(total) + " " + def.meta().currency() + "，到期自动扣款（30 游戏天）";
    }

    /** 赊账到期结算：现金够则扣款清零；不够则罚息 10%、顺延一期、合作值 -200 */
    private void creditSettle(GameDefinition def, GameState s, DayReport r) {
        if (s.getCreditUsed() <= 0) return;
        if (s.getMinutes() < s.getCreditDueMin()) return;
        double due = s.getCreditUsed();
        if (s.getCash() >= due) {
            s.setCash(Money.round(s.getCash() - due));
            s.setCreditUsed(0);
            s.setCreditDueMin(0);
            s.getTodayNotices().add("💰 供应商赊账到期，自动还款 " + Money.fmt(due) + " " + def.meta().currency());
            pushLive(s, "💰 赊账到期，自动还款 " + Money.fmt(due) + " " + def.meta().currency());
        } else {
            double penalty = Money.round(due * 0.1);
            s.setCreditUsed(Money.round(due + penalty));
            s.setCreditDueMin(s.getMinutes() + 43200);
            s.setSupplyPoints(Math.max(0, s.getSupplyPoints() - 200));
            s.getTodayNotices().add("⚠ 赊账逾期！罚息 10%（+"
                    + Money.fmt(penalty) + "），账期顺延 30 天，供应商合作值 -200");
            pushLive(s, "⚠ 赊账逾期！罚息 10%，合作值 -200");
        }
    }

    // ============================================================
    // v1.10.0 口碑评级系统
    // ============================================================

    /**
     * 每日满意度结算（对刚过去的一天）：价格 30 分 + 新鲜 25 分 + 服务 45 分。
     * 价格：全店定价倍率均值偏离 1.0 越远越低（1.0 得满分 30，偏离 0.1 扣 6 分）。
     * 新鲜：当日过期损耗越少越高（无损耗 25 分，每损耗 1 件扣 0.6 分，扣完为止）。
     * 服务：员工数量 ×3（封顶 21）+ 装修级别 ×2（封顶 10）+ 促销活动 +5；
     *       欠薪 -15；小偷/停电/卫生/鼠患等负面事件每件 -5（封顶 -20）。
     */
    private void satisfactionSettle(GameDefinition def, GameState s, DayReport r) {
        // 价格分：所有店铺定价均值（多店取加权平均）
        double sum = 0, cnt = 0;
        for (ShopState sh : s.getShops().values()) {
            for (double m : sh.getPriceMultiplier().values()) {
                sum += m;
                cnt++;
            }
        }
        double avgMult = cnt > 0 ? sum / cnt : 1.0;
        double priceScore = clamp(30 - Math.abs(avgMult - 1.0) * 60, 0, 30);

        // 新鲜分
        double freshScore = clamp(25 - s.getTodayExpiredQty() * 0.6, 0, 25);

        // 服务分
        int staffCount = staffCount(def, s);
        double svc = Math.min(21, staffCount * 3.0);
        double maxLevel = 0;
        for (ShopState sh : s.getShops().values()) maxLevel = Math.max(maxLevel, sh.getShopLevel());
        svc += Math.min(10, maxLevel * 2.0);
        if (s.getPromoUntilMin() > s.getMinutes()) svc += 5;
        if (s.getPayrollDebtDays() > 0) svc -= 15;
        for (String ev : s.getTodayEvents()) {
            if (ev.contains("小偷") || ev.contains("停电") || ev.contains("卫生") || ev.contains("老鼠")) {
                svc -= 5;
            }
        }
        svc = clamp(svc, 0, 45);

        double score = clamp(Money.round(priceScore + freshScore + svc), 0, 100);
        s.setSatisfactionScore(score);
        s.getSatisfactionHistory().add(score);
        if (s.getSatisfactionHistory().size() > 60) s.getSatisfactionHistory().remove(0);
        s.setRatingMonthSum(s.getRatingMonthSum() + score);
        s.setRatingMonthDays(s.getRatingMonthDays() + 1);
        if (s.getRatingTier() == 0) {
            s.setRatingTier(1);   // 保底口碑小店（新店默认），评级由月结更新
        }

        // 每日清晨播报满意度（低于 40 或高于 90 时提示）
        if (score < 40) {
            r.getNotices().add("📉 昨日顾客满意度仅 " + (int) score + " 分，街坊们有些抱怨……（价格/新鲜/服务都会影响）");
        } else if (score >= 90) {
            r.getNotices().add("📈 昨日顾客满意度高达 " + (int) score + " 分，街坊交口称赞！");
        }

        // 每 30 天（一个游戏月）结算口碑评级
        if (s.getRatingMonthDays() >= 30) {
            double avg = s.getRatingMonthSum() / s.getRatingMonthDays();
            int newTier = avg >= 85 ? 3 : avg >= 70 ? 2 : avg >= 55 ? 1 : 0;
            s.setLastMonthAvg(Money.round(avg));
            if (newTier != s.getRatingTier()) {
                String[] tierNames = {"街坊小铺", "口碑小店", "邻里名店", "街区标杆"};
                s.setRatingTier(newTier);
                String msg = "🏅 月度口碑评级 →【" + tierNames[newTier] + "】（上月满意度 " + (int) avg + "）"
                        + (newTier == 3 ? "，客单价 +8% 全街最靓！" : newTier == 2 ? "，客单价 +4%。" : "");
                s.getTodayNotices().add(msg);
                pushLive(s, msg);
            } else {
                s.getTodayNotices().add("🏅 月度口碑评级维持【"
                        + (newTier == 3 ? "街区标杆" : newTier == 2 ? "邻里名店" : newTier == 1 ? "口碑小店" : "街坊小铺")
                        + "】（上月满意度 " + (int) avg + "）");
            }
            s.setRatingMonthSum(0);
            s.setRatingMonthDays(0);
        }
    }

    // ============================================================
    // v1.10.0 街区竞争系统
    // ============================================================

    private CompetitionDef competitionOf(GameDefinition def) {
        return def.gameplay() == null ? null : def.gameplay().competition();
    }

    /** 竞争对手抢客比例（0 ~ 0.4；对手热度×竞争力 与 玩家声誉 对抗） */
    private double competitionSteal(GameDefinition def, GameState s) {
        CompetitionDef cd = competitionOf(def);
        if (cd == null || !cd.enabled() || s.getCompetitors().isEmpty()) return 0;
        double heatSum = 0;
        for (CompetitorState c : s.getCompetitors()) {
            if (c.isClosed()) continue;
            heatSum += c.getHeat() * c.getStrength();
        }
        if (heatSum <= 0) return 0;
        double steal = heatSum / (heatSum + s.getReputation() * 2.0) * 0.55;
        return Math.min(0.4, steal);
    }

    /** 每日竞争结算：对手登场 / 热度变化 / 歇业 / 卷土重来 */
    private void competitionSettle(GameDefinition def, GameState s, DayReport r) {
        CompetitionDef cd = competitionOf(def);
        if (cd == null || !cd.enabled()) return;
        // 登场：按声望逐步引出对手池角色
        int activeCount = (int) s.getCompetitors().stream().filter(c -> !c.isClosed()).count();
        int spawnCount = (int) s.getCompetitors().size();
        if (activeCount < cd.maxCompetitors() && spawnCount < cd.pool().size()) {
            int need = s.getReputation() >= cd.thresholdReputation() + spawnCount * 50 ? 1 : 0;
            for (int i = 0; i < need; i++) {
                if (activeCount >= cd.maxCompetitors() || spawnCount >= cd.pool().size()) break;
                CompetitorPoolDef pool = cd.pool().get(spawnCount);
                CompetitorState c = new CompetitorState(pool.id(), pool.name(), pool.icon(),
                        pool.strength(), s.getMinutes());
                s.getCompetitors().add(c);
                spawnCount++;
                activeCount++;
                String msg = "🏴 竞争对手【" + pool.icon() + " " + pool.name() + "】在街区开业了！正在抢走你的客流（热度越高抢得越凶）";
                s.getTodayNotices().add(msg);
                pushLive(s, msg);
            }
        }
        // 对手状态推进
        List<CompetitorState> toRemove = new ArrayList<>();
        for (CompetitorState c : s.getCompetitors()) {
            if (c.isClosed()) {
                // 歇业 90 游戏天（43200 分钟）后卷土重来
                if (s.getMinutes() - c.getClosedSinceMin() >= 43200L) {
                    c.setClosed(false);
                    c.setHeat(40);
                    c.setLowHeatDays(0);
                    String msg = "⚔ " + c.getIcon() + " " + c.getName() + " 卷土重来，重新开店了！";
                    s.getTodayNotices().add(msg);
                    pushLive(s, msg);
                }
                continue;
            }
            // 登场前 3 天热度减半（适应期，让玩家有反应时间）
            double grow = 4.0;
            if (s.getMinutes() - c.getSpawnedSinceMin() < 3 * 1440L) grow = 2.0;
            c.setHeat(Math.min(100, c.getHeat() + grow));
            // 价格战期间对手客流被拉走：热度 -6/天
            if (s.getPriceWarUntilMin() > s.getMinutes()) {
                c.setHeat(Math.max(0, c.getHeat() - 6));
            }
            if (c.getHeat() <= 15) {
                c.setLowHeatDays(c.getLowHeatDays() + 1);
            } else {
                c.setLowHeatDays(0);
            }
            if (c.getLowHeatDays() >= 3) {
                c.setClosed(true);
                c.setClosedSinceMin(s.getMinutes());
                c.setHeat(0);
                String msg = "🏁 " + c.getIcon() + " " + c.getName() + " 生意惨淡，连续 3 天没客人，关门歇业！";
                s.getTodayNotices().add(msg);
                pushLive(s, msg);
            }
        }
    }

    /** 查看街区竞争（命令 compete） */
    private String competeView(GameDefinition def, GameState s) {
        CompetitionDef cd = competitionOf(def);
        if (cd == null || !cd.enabled()) throw new GameException("本游戏未配置街区竞争玩法");
        StringBuilder sb = new StringBuilder();
        if (s.getCompetitors().isEmpty()) {
            sb.append("🏘 街区目前没有竞争对手。声望达到 ").append(cd.thresholdReputation())
                    .append(" 后，对手可能会来抢生意。");
        } else {
            sb.append("🏘 街区竞争：你的声誉 ").append((int) s.getReputation())
                    .append("，被抢客流约 ").append((int) Math.round(competitionSteal(def, s) * 100)).append("%");
            for (CompetitorState c : s.getCompetitors()) {
                sb.append("\n  · ").append(c.getIcon()).append(" ").append(c.getName())
                        .append(c.isClosed() ? "（歇业中）" : "（热度 " + (int) c.getHeat() + "）");
            }
            sb.append("\n可行动：price_war 价格战（180 分钟定价 -10%、客流 +25%）/ ad_war 营销战（"
                    + Money.fmtInt(cd.adCost()) + " 元，对手热度 -8、客流 +10%）/ charm_off 口碑攻势（"
                    + Money.fmtInt(cd.charmCost()) + " 元，需街坊好感 ≥60，对手热度 -5、好感 +3）");
            long cdLeft = s.getCompeteCooldownUntil() - s.getMinutes();
            if (cdLeft > 0) sb.append("；行动冷却剩 ").append(cdLeft).append(" 分钟");
        }
        return sb.toString();
    }

    private void checkCompeteCooldown(GameState s) {
        if (s.getCompeteCooldownUntil() > s.getMinutes()) {
            throw new GameException("竞争行动冷却中，剩 " + (s.getCompeteCooldownUntil() - s.getMinutes()) + " 分钟");
        }
    }

    private CompetitorState firstActiveCompetitor(GameDefinition def, GameState s) {
        return s.getCompetitors().stream().filter(c -> !c.isClosed()).findFirst().orElse(null);
    }

    /** 价格战（命令 price_war）：180 分钟全店定价 -10%、客流 +25%，对手热度每日 -6 */
    private String priceWar(GameDefinition def, GameState s) {
        CompetitionDef cd = competitionOf(def);
        if (cd == null || !cd.enabled()) throw new GameException("本游戏未配置街区竞争玩法");
        if (firstActiveCompetitor(def, s) == null) throw new GameException("街区没有活跃竞争对手，无需价格战");
        checkCompeteCooldown(s);
        s.setPriceWarUntilMin(s.getMinutes() + 180);
        s.setCompeteCooldownUntil(s.getMinutes() + 720);
        pushLive(s, "⚔ 发起价格战！未来 180 分钟全店定价 -10%，客流 +25%");
        return "⚔ 价格战开始！180 分钟内全店定价 -10%、客流 +25%，对手热度每天 -6";
    }

    /** 营销战（命令 ad_war）：花钱投放广告，对手热度 -8、自身客流 +10% */
    private String adWar(GameDefinition def, GameState s) {
        CompetitionDef cd = competitionOf(def);
        if (cd == null || !cd.enabled()) throw new GameException("本游戏未配置街区竞争玩法");
        if (firstActiveCompetitor(def, s) == null) throw new GameException("街区没有活跃竞争对手，无需营销战");
        checkCompeteCooldown(s);
        if (s.getCash() < cd.adCost()) {
            throw new GameException("现金不足：营销战需 " + Money.fmtInt(cd.adCost()) + " " + def.meta().currency());
        }
        s.setCash(Money.round(s.getCash() - cd.adCost()));
        s.setAdWarUntilMin(s.getMinutes() + 1440);
        s.setCompeteCooldownUntil(s.getMinutes() + 720);
        CompetitorState target = firstActiveCompetitor(def, s);
        target.setHeat(Math.max(0, target.getHeat() - 8));
        s.setSatisfactionScore(Math.min(100, s.getSatisfactionScore() + 2));
        pushLive(s, "📣 营销战！" + cd.adCost() + " 元广告投放，对手热度 -8，今日客流 +10%");
        return "📣 营销战成功：" + cd.adCost() + " 元广告上线，对手热度 -8、今日客流 +10%、满意度 +2";
    }

    /** 口碑攻势（命令 charm_off）：借街坊好评压制对手，需一位 NPC 好感 ≥60 */
    private String charmOff(GameDefinition def, GameState s) {
        CompetitionDef cd = competitionOf(def);
        if (cd == null || !cd.enabled()) throw new GameException("本游戏未配置街区竞争玩法");
        if (firstActiveCompetitor(def, s) == null) throw new GameException("街区没有活跃竞争对手，无需口碑攻势");
        checkCompeteCooldown(s);
        if (s.getCash() < cd.charmCost()) {
            throw new GameException("现金不足：口碑攻势需 " + Money.fmtInt(cd.charmCost()) + " " + def.meta().currency());
        }
        // 找一位好感 ≥60 的街坊支持
        String supporter = null;
        for (var e : s.getNpcs().entrySet()) {
            if (e.getValue().getFavorability() >= 60) {
                supporter = e.getKey();
                break;
            }
        }
        if (supporter == null) {
            throw new GameException("没有街坊愿意帮你宣传（需要至少一位街坊好感 ≥60），先多互动提升好感吧");
        }
        NpcDef npc = def.npc(supporter);
        s.setCash(Money.round(s.getCash() - cd.charmCost()));
        s.setCompeteCooldownUntil(s.getMinutes() + 720);
        CompetitorState target = firstActiveCompetitor(def, s);
        target.setHeat(Math.max(0, target.getHeat() - 5));
        s.getNpcs().get(supporter).setFavorability(s.getNpcs().get(supporter).getFavorability() + 3);
        pushLive(s, "🗣 口碑攻势！" + (npc == null ? "街坊" : npc.name()) + " 四处替你宣传，对手热度 -5，好感 +3");
        return "🗣 口碑攻势成功：" + (npc == null ? "街坊" : npc.name()) + " 帮忙宣传，对手热度 -5、好感 +3";
    }

    // ------------------------------------------------------------
    // v1.11.0 节日庆典系统
    // ------------------------------------------------------------

    private FestivalDef festivalCfg(GameDefinition def) {
        return def.gameplay() == null ? null : def.gameplay().festival();
    }

    private FestivalItemDef festivalById(FestivalDef fd, String id) {
        if (fd == null || id == null) return null;
        for (FestivalItemDef f : fd.festivals()) if (f.id().equals(id)) return f;
        return null;
    }

    /** 是否有进行中的节日（已开幕且未结束） */
    private boolean festivalActive(GameState s) {
        FestivalState fs = s.getFestival();
        return fs != null && fs.getStartedMin() <= s.getMinutes() && s.getMinutes() < fs.getEndMin();
    }

    /** 节日客流加成：筹备度越高节日气氛越旺（筹备满额 +30% 客流） */
    private double festivalTrafficBonus(GameDefinition def, GameState s) {
        FestivalState fs = s.getFestival();
        if (!festivalActive(s)) return 0;
        FestivalItemDef f = festivalById(festivalCfg(def), fs.getId());
        if (f == null || f.setupCap() <= 0) return 0;
        return clamp(0, fs.getSetup() / f.setupCap() * 0.30, 0.30);
    }

    /** 下一节日的开始游戏分钟（从当前日期起最多找一年；找不到返回 -1） */
    private long nextFestivalStartMin(GameDefinition def, GameState s, FestivalDef fd) {
        GameMeta meta = def.meta();
        int d = meta.daysPerSeason();
        int seasons = def.seasons().size();
        int dayNum = s.getDate().getDayNumber();
        for (int off = 0; off < 366; off++) {
            int dn = dayNum + off;
            String season = def.seasons().get((dn - 1) / d % seasons).id();
            int day = (dn - 1) % d + 1;
            if (fd.bySeasonDay(season, day) != null) {
                return (long) (dn - 1) * MINUTES_PER_DAY;
            }
        }
        return -1;
    }

    /** 每日结算：节日开幕 / 闭幕结算 / 节日期间每日小事件 */
    private void festivalTick(GameDefinition def, GameState s, DayReport r) {
        FestivalDef fd = festivalCfg(def);
        if (fd == null || !fd.enabled()) return;
        FestivalState fs = s.getFestival();
        if (fs != null && fs.getEndMin() <= s.getMinutes() && !fs.isSettled()) {
            // 闭幕结算
            FestivalItemDef f = festivalById(fd, fs.getId());
            double reward = f == null ? 0 : fs.getSetup() * f.rewardMult();
            double extra = 0;
            if (f != null && f.setupCap() > 0 && fs.getSetup() >= f.setupCap()) {
                extra = fs.getFestivalRevenue() * 0.10;   // 筹备满额：节日营业额抽成
            }
            double total = Money.round(reward + extra);
            s.setCash(Money.round(s.getCash() + total));
            Stats st = s.getStats();
            st.setFestivalCount(st.getFestivalCount() + 1);
            st.setFestivalBonusEarned(Money.round(st.getFestivalBonusEarned() + total));
            String line = (f == null ? "🎉" : f.icon()) + " " + fs.getName() + " 收官：节日营业额 "
                    + Money.fmt(fs.getFestivalRevenue()) + "，筹备回报 +" + Money.fmt(total) + " " + def.meta().currency()
                    + (extra > 0 ? "（筹备满额奖励）" : "");
            r.getNotices().add(line);
            pushLive(s, line);
            List<String> hist = s.getFestivalHistory();
            hist.add(0, line);
            while (hist.size() > 8) hist.remove(hist.size() - 1);
            fs.setSettled(true);
            s.setFestival(null);
        }
        if (s.getFestival() == null) {
            // 开幕：今天恰好是节日
            FestivalItemDef f = fd.bySeasonDay(s.getDate().getSeasonId(), s.getDate().getDay());
            if (f != null) {
                long start = s.getMinutes();
                s.setFestival(new FestivalState(f.id(), f.name(), f.icon(), start, start + f.durationMin(),
                        s.getDate().getYear()));
                String line = f.icon() + " " + f.name() + " 开幕！节日气氛拉满"
                        + (f.demand().isEmpty() ? "" : "，特定商品更抢手")
                        + "（可投入筹备金提升客流，festival_setup <金额>）";
                r.getNotices().add(line);
                pushLive(s, line);
            }
        } else if (festivalActive(s)) {
            // 节日期间：每日随机小事件（良性为主）
            FestivalItemDef f = festivalById(fd, s.getFestival().getId());
            if (f != null && f.eventRate() > 0 && random.nextDouble() < f.eventRate()) {
                String[] good = {
                        f.icon() + " 节日大巡游！围观人群带来一波客流",
                        f.icon() + " 有街坊在节日市集摆摊，顺路来你店里消费",
                        f.icon() + " 节日彩灯点亮的夜晚，营业额悄悄上涨",
                        f.icon() + " 游客慕名而来，夸你店里节日氛围最好"
                };
                String[] bad = {
                        f.icon() + " 节日人群太挤，有顾客被挡在门口（客流转弱）",
                        f.icon() + " 忙碌中一位顾客忘带了钱包，白忙一场"
                };
                boolean isGood = random.nextBoolean();
                String ev = isGood ? good[random.nextInt(good.length)] : bad[random.nextInt(bad.length)];
                s.setSatisfactionScore((int) clamp(0, s.getSatisfactionScore() + (isGood ? 1 : -1), 100));
                pushLive(s, ev);
                r.getNotices().add(ev);
            }
        }
    }

    /** 节日查看（命令 festival） */
    private String festivalView(GameDefinition def, GameState s) {
        FestivalDef fd = festivalCfg(def);
        if (fd == null || !fd.enabled()) throw new GameException("本游戏未配置节日玩法");
        StringBuilder sb = new StringBuilder();
        FestivalState fs = s.getFestival();
        if (fs != null && festivalActive(s)) {
            FestivalItemDef f = festivalById(fd, fs.getId());
            sb.append(fs.getIcon()).append(" ").append(fs.getName()).append(" 进行中，还剩 ")
                    .append(fs.remainMin(s.getMinutes())).append(" 分钟");
            if (f != null) {
                sb.append("。筹备金 ").append(Money.fmt(fs.getSetup())).append(" / ").append(Money.fmt(f.setupCap()))
                        .append("，节日营业额 ").append(Money.fmt(fs.getFestivalRevenue()));
            }
        } else {
            long ns = nextFestivalStartMin(def, s, fd);
            if (ns < 0) {
                sb.append("🎊 今年没有安排节日了。");
            } else {
                long until = ns - s.getMinutes();
                int dn = (int) (ns / MINUTES_PER_DAY) + 1;
                int d = def.meta().daysPerSeason();
                int seasons = def.seasons().size();
                String season = def.seasons().get((dn - 1) / d % seasons).id();
                int day = (dn - 1) % d + 1;
                FestivalItemDef f = fd.bySeasonDay(season, day);
                sb.append("🎊 下一个节日：").append(f == null ? "？" : f.icon() + " " + f.name())
                        .append("，还有 ").append(until).append(" 分钟");
                if (f != null && until <= fd.prepWindowMin()) {
                    sb.append("（已进入筹备窗口，可用 festival_setup <金额> 投入筹备金）");
                }
            }
        }
        if (!s.getFestivalHistory().isEmpty()) {
            sb.append("\n最近战报：");
            for (String h : s.getFestivalHistory()) sb.append("\n  ").append(h);
        }
        return sb.toString();
    }

    /** 节日筹备（命令 festival_setup <金额>）：节日开始前 prepWindowMin 内或进行中可投 */
    private String festivalSetup(GameDefinition def, GameState s, double amount) {
        FestivalDef fd = festivalCfg(def);
        if (fd == null || !fd.enabled()) throw new GameException("本游戏未配置节日玩法");
        if (amount <= 0) throw new GameException("筹备金额必须大于 0");
        if (s.getCash() < amount) {
            throw new GameException("现金不足：筹备需 " + Money.fmt(amount) + " " + def.meta().currency());
        }
        FestivalState fs = s.getFestival();
        FestivalItemDef f;
        if (fs != null) {
            if (fs.isSettled()) throw new GameException("节日已结束");
            f = festivalById(fd, fs.getId());
            if (f == null) throw new GameException("节日数据异常");
            if (fs.getSetup() + amount > f.setupCap()) {
                throw new GameException("筹备金已接近上限（" + Money.fmt(f.setupCap()) + "），本次最多再投 "
                        + Money.fmt(Math.max(0, f.setupCap() - fs.getSetup())));
            }
            s.setCash(Money.round(s.getCash() - amount));
            fs.setSetup(Money.round(fs.getSetup() + amount));
            pushLive(s, "🎪 为「" + fs.getName() + "」追加筹备金 " + Money.fmt(amount) + "，气氛更旺（客流加成提升）");
            return "🎪 已为「" + fs.getName() + "」追加筹备金 " + Money.fmt(amount) + " " + def.meta().currency()
                    + "，累计 " + Money.fmt(fs.getSetup()) + " / " + Money.fmt(f.setupCap());
        }
        long ns = nextFestivalStartMin(def, s, fd);
        if (ns < 0) throw new GameException("今年没有节日可筹备了");
        long until = ns - s.getMinutes();
        if (until > fd.prepWindowMin()) {
            throw new GameException("下一个节日还有 " + until + " 分钟才到，进入筹备窗口（提前 "
                    + fd.prepWindowMin() + " 分钟）后才能投入筹备金");
        }
        int dn = (int) (ns / MINUTES_PER_DAY) + 1;
        int d = def.meta().daysPerSeason();
        int seasons = def.seasons().size();
        String season = def.seasons().get((dn - 1) / d % seasons).id();
        int day = (dn - 1) % d + 1;
        f = fd.bySeasonDay(season, day);
        if (f == null) throw new GameException("节日数据异常");
        if (amount > f.setupCap()) {
            throw new GameException("筹备金上限为 " + Money.fmt(f.setupCap()) + "，一次最多投这么多");
        }
        s.setCash(Money.round(s.getCash() - amount));
        s.setFestival(new FestivalState(f.id(), f.name(), f.icon(), ns, ns + f.durationMin(),
                s.getDate().getYear()));
        s.getFestival().setSetup(Money.round(amount));
        pushLive(s, "🎪 提前为「" + f.name() + "」投入筹备金 " + Money.fmt(amount) + "，节日开幕即享客流加成");
        return "🎪 已为「" + f.name() + "」投入筹备金 " + Money.fmt(amount) + " " + def.meta().currency()
                + "（开幕倒计时 " + until + " 分钟，节日期间客流 +"
                + Math.round(clamp(0, amount / f.setupCap() * 0.30, 0.30) * 100) + "%）";
    }

    // ------------------------------------------------------------
    // v1.11.0 员工培养晋升系统
    // ------------------------------------------------------------

    private CareerDef careerCfg(GameDefinition def) {
        StaffConfigDef cfg = staffCfg(def);
        return cfg == null ? null : cfg.career();
    }

    /** 员工技能参数值（固定加成，不乘效率）：按已学技能查角色技能池 */
    private double staffSkillValue(GameDefinition def, StaffState st, String key) {
        if (st == null || st.getSkillId() == null) return 0;
        StaffDef role = roleOf(def, st.getStaffId());
        if (role == null || role.skills() == null) return 0;
        for (StaffSkillDef sk : role.skills()) {
            if (sk.id().equals(st.getSkillId())) return sk.p(key);
        }
        return 0;
    }

    /** 全部在编员工技能参数汇总（当前店） */
    private double staffSkillSum(GameDefinition def, GameState s, String key) {
        double sum = 0;
        for (StaffState st : s.getStaff().values()) sum += staffSkillValue(def, st, key);
        return sum;
    }

    /** 店长光环：已晋升员工提供全员效率加成（efficiency_bonus 技能） */
    private double staffAura(GameDefinition def, Map<String, StaffState> staff) {
        double aura = 1.0;
        for (StaffState st : staff.values()) {
            if (st.isPromoted()) aura += staffSkillValue(def, st, "efficiency_bonus");
        }
        return aura;
    }

    /** 每日结算：员工经验增长 / 等级提升 / 忠诚微调 */
    private void careerTick(GameDefinition def, GameState s, DayReport r) {
        CareerDef cd = careerCfg(def);
        if (cd == null || !cd.enabled()) return;
        for (ShopState sh : s.getShops().values()) {
            for (StaffState st : sh.getStaff().values()) {
                st.setExp(st.getExp() + cd.expPerDay());
                int lvl = cd.levelOf(st.getExp());
                if (lvl > st.getLevel()) {
                    st.setLevel(lvl);
                    StaffDef role = roleOf(def, st.getStaffId());
                    r.getNotices().add("🎓 「" + sh.getName() + "」"
                            + (role == null ? st.getStaffId() : role.icon() + " " + role.name())
                            + " 升到 Lv." + lvl + "，效率 +5%！");
                }
                // 忠诚缓慢回升（上限 60）
                if (st.getLoyalty() < 60) st.setLoyalty(st.getLoyalty() + 0.5);
            }
        }
    }

    /** 晋升（命令 promote <员工id>）：满级 + 忠诚足够 + 花钱，晋升后选技能 */
    private String promoteStaff(GameDefinition def, GameState s, String staffId) {
        CareerDef cd = careerCfg(def);
        if (cd == null || !cd.enabled()) throw new GameException("本游戏未开启员工培养玩法（晋升不可用）");
        StaffState st = s.getStaff().get(staffId);
        if (st == null) throw new GameException("该员工不在职");
        StaffDef role = roleOf(def, staffId);
        String who = role == null ? staffId : role.icon() + " " + role.name();
        if (st.isPromoted()) throw new GameException(who + " 已是骨干员工（店长光环）");
        if (st.getLevel() < CareerDef.MAX_LEVEL) {
            throw new GameException(who + " 等级不足（Lv." + st.getLevel() + "/" + CareerDef.MAX_LEVEL
                    + "），累计经验升满 3 级才可晋升");
        }
        if (st.getLoyalty() < 60) {
            throw new GameException(who + " 忠诚度 " + Math.round(st.getLoyalty()) + " 不足 60，先发红包/按时发薪提升忠诚吧");
        }
        double cost = Money.round(st.getSalary() * cd.promoteCostMult());
        if (s.getCash() < cost) {
            throw new GameException("现金不足：晋升费用 = 月薪 × " + cd.promoteCostMult() + " = "
                    + Money.fmt(cost) + " " + def.meta().currency());
        }
        s.setCash(Money.round(s.getCash() - cost));
        st.setPromoted(true);
        st.setLoyalty(st.getLoyalty() - cd.loyaltyPromoteCost());
        if (role != null && !role.skills().isEmpty()) {
            StringBuilder skills = new StringBuilder("可选技能：");
            for (StaffSkillDef sk : role.skills()) {
                skills.append("\n  ").append(sk.id()).append(" - ").append(sk.icon()).append(" ").append(sk.name())
                        .append("（").append(sk.desc()).append("）");
            }
            skills.append("\n用 pick_skill <员工id> <技能id> 选择技能（或稍后在雇员页点选）");
            pushLive(s, "🎓 " + who + " 晋升为骨干！效率 +10%，解锁技能池");
            return "🎓 " + who + " 晋升成功：效率 +10%、全员店长光环，花费 " + Money.fmt(cost) + " 元。" + skills;
        }
        pushLive(s, "🎓 " + who + " 晋升为骨干！效率 +10%");
        return "🎓 " + who + " 晋升成功：效率 +10%（本月花掉 " + Money.fmt(cost) + " 元）";
    }

    /** 晋升后选择技能（命令 pick_skill <员工id> <技能id>） */
    private String pickSkill(GameDefinition def, GameState s, String staffId, String skillId) {
        CareerDef cd = careerCfg(def);
        if (cd == null || !cd.enabled()) throw new GameException("本游戏未开启员工培养玩法");
        StaffState st = s.getStaff().get(staffId);
        if (st == null) throw new GameException("该员工不在职");
        StaffDef role = roleOf(def, staffId);
        if (!st.isPromoted()) throw new GameException("该员工尚未晋升，无法学习技能");
        if (st.getSkillId() != null) throw new GameException("已学会技能，技能只能学一个");
        if (role == null || role.skills() == null || role.skills().isEmpty()) {
            throw new GameException("该角色没有技能可学");
        }
        StaffSkillDef skill = null;
        for (StaffSkillDef sk : role.skills()) if (sk.id().equals(skillId)) skill = sk;
        if (skill == null) throw new GameException("未知技能：" + skillId);
        st.setSkillId(skill.id());
        st.setLoyalty(st.getLoyalty() + 5);
        pushLive(s, "💡 " + (role.icon() + " " + role.name()) + " 学会技能「" + skill.name() + "」！");
        return "💡 " + role.icon() + " " + role.name() + " 学会技能「" + skill.name() + "」："
                + skill.desc() + "（忠诚 +5）";
    }

    /** 发红包（命令 redpacket <员工id>）：花一笔钱提升忠诚，节日期间效果翻倍 */
    private String redpacket(GameDefinition def, GameState s, String staffId) {
        CareerDef cd = careerCfg(def);
        if (cd == null || !cd.enabled()) throw new GameException("本游戏未开启员工培养玩法");
        StaffState st = s.getStaff().get(staffId);
        if (st == null) throw new GameException("该员工不在职");
        StaffDef role = roleOf(def, staffId);
        if (s.getCash() < cd.redpacketCost()) {
            throw new GameException("现金不足：红包需 " + Money.fmt(cd.redpacketCost()) + " 元");
        }
        s.setCash(Money.round(s.getCash() - cd.redpacketCost()));
        double gain = cd.redpacketCost() > 0 ? cd.redpacketCost() / 20.0 : 5; // 每 20 元 +1 忠诚，200 元 = +10
        gain = festivalActive(s) ? gain * 2 : gain;   // 节日期间效果 ×2
        st.setLoyalty(st.getLoyalty() + gain);
        pushLive(s, "🧧 给 " + (role == null ? staffId : role.icon() + " " + role.name()) + " 发了 "
                + Money.fmt(cd.redpacketCost()) + " 元红包（忠诚 +" + Math.round(gain) + "）"
                + (festivalActive(s) ? "，节日加成翻倍！" : ""));
        return "🧧 " + (role == null ? staffId : role.icon() + " " + role.name()) + " 收到红包，忠诚 +"
                + Math.round(gain) + "（当前 " + Math.round(st.getLoyalty()) + "）"
                + (festivalActive(s) ? "，节日加成翻倍！" : "");
    }
}
