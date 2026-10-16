package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 完整游戏状态（可序列化为存档 JSON）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameState {

    private String gameId;
    private String saveName;

    private double cash;
    private double debt;

    /** 游戏内时间（分钟）。0 = 第 1 天 08:00。现实 1 秒 = 游戏 1 分钟（倍速 1.0）。 */
    private long minutes;
    /** 上次结算的现实时间戳（epoch 毫秒），用于按现实时间推进游戏时间。 */
    private long lastTickAt;
    /** 是否暂停时间流逝。 */
    private boolean paused;
    /** 时间倍速（0.5 / 1.0 / 1.5 / 2.0 / 3.0 / 5.0），默认 1.0。 */
    private double timeSpeed = 1.0;
    /** 倍速推进时不足 1 游戏分钟的小数部分（跨 tick 累积，避免 0.5× 丢时间）。 */
    private double tickFraction = 0.0;

    private GameDate date;
    private String weatherId;
    /** 当日客流基准（每天跨天时按天气/季节/装修/声誉/事件重算）。 */
    private double dailyFootTraffic;

    private double todayRevenue;
    private double todayCost;
    private int todayItems;
    private int todayCustomers;
    /** 当日累计利息（未舍入，跨天结算时一次性入账）。 */
    private double todayInterest;
    private boolean todayClosed;   // 当日是否打烊休息
    private Map<String, Integer> todayPeriodFlow = new LinkedHashMap<>();
    private List<String> todayEvents = new ArrayList<>();
    private List<String> todayNotices = new ArrayList<>();

    private double reputation;
    // ---- v1.9.0 分店系统 ----
    /** 全部已开店铺：shopId → 店铺数据（主店 "main" + 分店 "branch_N"）。库存/定价/装修/雇员/当日经营每店独立。 */
    private Map<String, ShopState> shops = new LinkedHashMap<>();
    /** 当前正在查看/经营的店铺 id（前端切换）。 */
    private String currentShopId = "main";
    /** 旧版存档迁移用的主店字段（新档不再直接使用，仅用于反序列化旧档后搬运） */
    private int shopLevel = 1;
    private List<String> upgradesOwned = new ArrayList<>();
    private Map<String, InventorySlot> inventory = new LinkedHashMap<>();
    private Map<String, Double> priceMultiplier = new LinkedHashMap<>();
    private Map<String, NpcState> npcs = new LinkedHashMap<>();
    private List<LoanState> loans = new ArrayList<>();
    private List<Buff> buffs = new ArrayList<>();
    private Stats stats = new Stats();
    private List<DayReport> dayHistory = new ArrayList<>();

    // ---- 玩法扩展：今日晨报 / 限时促销 / 爆款热卖 / 成就 ----
    /** 今日晨报标题（每日清晨刷新，可能为空）。 */
    private String dailyBrief;
    /** 限时促销截止时间（全局游戏分钟，0 = 无促销）。 */
    private long promoUntilMin;
    /** 促销期间客流倍率。 */
    private double promoMult = 1.0;
    /** 爆款商品 id（null = 无爆款）。 */
    private String hotProductId;
    /** 爆款截止时间（全局游戏分钟，0 = 无爆款）。 */
    private long hotUntilMin;
    /** 累计经历爆款次数（成就用）。 */
    private int hotCount;
    /** 已解锁成就：id -> true。 */
    private Map<String, Boolean> achievements = new LinkedHashMap<>();

    // ---- NPC 社交 / 贸易策略 ----
    /** NPC id → 社交冷却到期分钟 */
    private Map<String, Long> socialCooldowns = new LinkedHashMap<>();
    /** 批发冷却到期分钟（0 = 可批发） */
    private long tradeCooldownUntil;
    /** 外卖平台是否已开通 */
    private boolean deliveryOpen;
    /** 外卖：今日累计单数 / 收入 / 食材成本 / 好评差评净分 */
    private int deliveryTodayOrders;
    private double deliveryTodayRevenue;
    private double deliveryTodayCost;
    private int deliveryReviews;
    /** 外卖：累计单数 / 累计收入 */
    private int deliveryTotalOrders;
    private double deliveryTotalRevenue;
    /** 今日高好感 NPC 带客上门人数（展示用） */
    private int npcVisitBonus;

    // ---- 雇佣系统 ----
    /** 在编员工：staff id → 雇佣状态 */
    private Map<String, StaffState> staff = new LinkedHashMap<>();
    /** 连续欠薪天数（发薪日现金不足 +1，发薪成功清零；欠薪期间离职风险升高） */
    private int payrollDebtDays;

    // ---- 实时日志流（购买记录 / 随机事件，前端终端滚动显示） ----
    private List<String> liveLog = new ArrayList<>();

    // ---- v1.11.0 节日庆典 ----
    /** 进行中的节日（null = 无节日） */
    private FestivalState festival;
    /** 已过节日的战报（最近 8 场）：{icon} {名称}：节日营业额 X，筹备回报 +Y 元 */
    private List<String> festivalHistory = new ArrayList<>();

    // ---- 银行 ----
    /** 银行存款（每日按年化利率结算利息，计入存款复利） */
    private double bankBalance;
    /** 累计存款利息（成就用） */
    private double bankInterestEarned;

    // ---- NPC 委托订单 ----
    private QuestState activeQuest;
    private int questsCompleted;
    private int questsFailed;

    // ---- 幸运彩票 ----
    /** 已购彩票对应的全局天数（0 = 无待开奖彩票；次日清晨开奖） */
    private int lotteryTicketDay;
    /** 最近一次开奖结果（>0 中奖金额 / 0 未中 / 无记录） */
    private int lotteryLastPrize;

    // ---- 刮刮乐（即时开奖，不限次数） ----
    /** 最近一次刮刮乐结果（>0 中奖金额 / 0 未中 / -1 从未购买） */
    private double scratchLastWin = -1;
    /** 刮刮乐累计购买次数 */
    private int scratchPurchases;

    // ---- 理财产品持仓 ----
    private List<FinanceState> finances = new ArrayList<>();

    // ---- 地图地产 ----
    /** 当前所在地产 id（迁移后切换；客流量加成/房贷跟随） */
    private String currentLocationId;
    /** 进行中的房屋贷款（同一时间仅一套；还清后置空） */
    private HouseLoanState houseLoan;
    /** 累计迁移次数（成就用） */
    private int relocations;

    // ---- 个人形象 ----
    /** 已购装扮单品 id 集合 */
    private java.util.List<String> ownedItems = new java.util.ArrayList<>();
    /** 当前穿戴：slot -> 单品 id */
    private java.util.Map<String, String> wornItems = new java.util.HashMap<>();
    /** 已购资产 id 集合 */
    private java.util.List<String> ownedAssets = new java.util.ArrayList<>();
    /** 最近一次 NPC 主动互动的全局天数（每天最多一次） */
    private int prestigeInteractDay;
    /** 累计 NPC 主动互动次数（统计/成就参考） */
    private int prestigeInteractions;

    // ---- 供应链系统（v1.10.0）----
    /** 累计合作值（= 累计实付进货金额，决定供应商等级） */
    private double supplyPoints;
    /** 赊账已用未还金额 */
    private double creditUsed;
    /** 赊账到期游戏分钟（到期自动从现金扣款） */
    private long creditDueMin;

    // ---- 口碑评级系统（v1.10.0）----
    /** 当日满意度得分（0~100，由价格/新鲜/服务综合） */
    private double satisfactionScore = 70;
    /** 当日过期损耗件数（结算满意度新鲜分用） */
    private int todayExpiredQty;
    /** 每日满意度历史（最近 60 天，用于月度评级与趋势展示） */
    private java.util.List<Double> satisfactionHistory = new java.util.ArrayList<>();
    /** 当月满意度累计（每日结算后累加，月底结算评级后清零） */
    private double ratingMonthSum;
    /** 当月已结算天数 */
    private int ratingMonthDays;
    /** 当前口碑评级 0 街坊小铺 / 1 口碑小店 / 2 邻里名店 / 3 街区标杆 */
    private int ratingTier = 1;
    /** 上月平均满意度（展示用） */
    private double lastMonthAvg = 70;

    // ---- 街区竞争系统（v1.10.0）----
    /** 竞争对手列表 */
    private java.util.List<CompetitorState> competitors = new java.util.ArrayList<>();
    /** 竞争行动冷却截止游戏分钟 */
    private long competeCooldownUntil;
    /** 价格战生效截止游戏分钟（期间全店定价 ×0.9 但客流 +25%） */
    private long priceWarUntilMin;
    /** 营销战生效截止游戏分钟（期间客流 +10%、对手热度 -8） */
    private long adWarUntilMin;

    private boolean ended;
    private String endType;     // "win" / "lose"
    private String endMessage;
    private int debtOverLimitDays;

    public GameState() {
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getSaveName() {
        return saveName;
    }

    public void setSaveName(String saveName) {
        this.saveName = saveName;
    }

    public double getCash() {
        return cash;
    }

    public void setCash(double cash) {
        this.cash = cash;
    }

    public double getDebt() {
        return debt;
    }

    public void setDebt(double debt) {
        this.debt = debt;
    }

    // ---- v1.9.0 分店系统 ----

    public Map<String, ShopState> getShops() {
        return shops;
    }

    public void setShops(Map<String, ShopState> shops) {
        this.shops = shops;
    }

    public String getCurrentShopId() {
        return currentShopId;
    }

    public void setCurrentShopId(String currentShopId) {
        this.currentShopId = currentShopId;
    }

    /** 当前查看的店铺（不存在则兜底到主店）。 */
    public ShopState currentShop() {
        ensureShops();
        ShopState c = shops.get(currentShopId);
        return c != null ? c : shops.get("main");
    }

    /**
     * 分店系统初始化 / 旧档迁移：
     * - 新档：调用方在设置完 locationId 等基础字段后调用本方法创建主店；
     * - 旧档（无 shops 字段）：把旧版店铺字段搬运进主店，清空旧字段，防止后续冗余。
     */
    public void ensureShops() {
        if (shops == null) shops = new LinkedHashMap<>();
        if (shops.isEmpty()) {
            ShopState main = new ShopState();
            main.setShopId("main");
            main.setName("主店");
            main.setLocationId(currentLocationId);
            main.setShopLevel(shopLevel);
            main.setUpgradesOwned(upgradesOwned != null ? upgradesOwned : new ArrayList<>());
            main.setInventory(inventory != null ? inventory : new LinkedHashMap<>());
            main.setPriceMultiplier(priceMultiplier != null ? priceMultiplier : new LinkedHashMap<>());
            main.setStaff(staff != null ? staff : new LinkedHashMap<>());
            main.setPayrollDebtDays(payrollDebtDays);
            main.setDailyFootTraffic(dailyFootTraffic);
            main.setTodayRevenue(todayRevenue);
            main.setTodayCost(todayCost);
            main.setTodayItems(todayItems);
            main.setTodayCustomers(todayCustomers);
            main.setTodayPeriodFlow(todayPeriodFlow != null ? todayPeriodFlow : new LinkedHashMap<>());
            main.setTodayEvents(todayEvents != null ? todayEvents : new ArrayList<>());
            main.setTodayNotices(todayNotices != null ? todayNotices : new ArrayList<>());
            shops.put("main", main);
            // 清空旧字段，避免新档冗余/混用
            shopLevel = 1;
            upgradesOwned = new ArrayList<>();
            inventory = new LinkedHashMap<>();
            priceMultiplier = new LinkedHashMap<>();
            staff = new LinkedHashMap<>();
            payrollDebtDays = 0;
            dailyFootTraffic = 0;
            todayRevenue = 0;
            todayCost = 0;
            todayItems = 0;
            todayCustomers = 0;
            todayPeriodFlow = new LinkedHashMap<>();
            todayEvents = new ArrayList<>();
            todayNotices = new ArrayList<>();
        }
        if (currentShopId == null || !shops.containsKey(currentShopId)) {
            currentShopId = shops.containsKey("main") ? "main" : shops.keySet().iterator().next();
        }
    }

    public long getMinutes() {
        return minutes;
    }

    public void setMinutes(long minutes) {
        this.minutes = minutes;
    }

    public long getLastTickAt() {
        return lastTickAt;
    }

    public void setLastTickAt(long lastTickAt) {
        this.lastTickAt = lastTickAt;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public double getTimeSpeed() {
        return timeSpeed;
    }

    public void setTimeSpeed(double timeSpeed) {
        this.timeSpeed = timeSpeed;
    }

    public double getTickFraction() {
        return tickFraction;
    }

    public void setTickFraction(double tickFraction) {
        this.tickFraction = tickFraction;
    }

    public double getDailyFootTraffic() {
        return currentShop().getDailyFootTraffic();
    }

    public void setDailyFootTraffic(double dailyFootTraffic) {
        currentShop().setDailyFootTraffic(dailyFootTraffic);
    }

    public double getTodayRevenue() {
        return currentShop().getTodayRevenue();
    }

    public void setTodayRevenue(double todayRevenue) {
        currentShop().setTodayRevenue(todayRevenue);
    }

    public double getTodayCost() {
        return currentShop().getTodayCost();
    }

    public void setTodayCost(double todayCost) {
        currentShop().setTodayCost(todayCost);
    }

    public int getTodayItems() {
        return currentShop().getTodayItems();
    }

    public void setTodayItems(int todayItems) {
        currentShop().setTodayItems(todayItems);
    }

    public int getTodayCustomers() {
        return currentShop().getTodayCustomers();
    }

    public void setTodayCustomers(int todayCustomers) {
        currentShop().setTodayCustomers(todayCustomers);
    }

    public double getTodayInterest() {
        return todayInterest;
    }

    public void setTodayInterest(double todayInterest) {
        this.todayInterest = todayInterest;
    }

    public boolean isTodayClosed() {
        return todayClosed;
    }

    public void setTodayClosed(boolean todayClosed) {
        this.todayClosed = todayClosed;
    }

    public Map<String, Integer> getTodayPeriodFlow() {
        return currentShop().getTodayPeriodFlow();
    }

    public void setTodayPeriodFlow(Map<String, Integer> todayPeriodFlow) {
        currentShop().setTodayPeriodFlow(todayPeriodFlow);
    }

    public List<String> getTodayEvents() {
        return currentShop().getTodayEvents();
    }

    public void setTodayEvents(List<String> todayEvents) {
        currentShop().setTodayEvents(todayEvents);
    }

    public List<String> getTodayNotices() {
        return currentShop().getTodayNotices();
    }

    public void setTodayNotices(List<String> todayNotices) {
        currentShop().setTodayNotices(todayNotices);
    }

    /** 当前游戏天数（从 1 起） */
    public int dayOf() {
        return (int) (minutes / 1440) + 1;
    }

    /** 当前游戏日内的分钟数（0-1439） */
    public int minuteOfDay() {
        return (int) (minutes % 1440);
    }

    public int hour() {
        return minuteOfDay() / 60;
    }

    public int minute() {
        return minuteOfDay() % 60;
    }

    /** HH:MM 格式的游戏时间 */
    public String clockText() {
        return String.format("%02d:%02d", hour(), minute());
    }

    public GameDate getDate() {
        return date;
    }

    public void setDate(GameDate date) {
        this.date = date;
    }

    public String getWeatherId() {
        return weatherId;
    }

    public void setWeatherId(String weatherId) {
        this.weatherId = weatherId;
    }

    public double getReputation() {
        return reputation;
    }

    public void setReputation(double reputation) {
        this.reputation = reputation;
    }

    public int getShopLevel() {
        return currentShop().getShopLevel();
    }

    public void setShopLevel(int shopLevel) {
        currentShop().setShopLevel(shopLevel);
    }

    public List<String> getUpgradesOwned() {
        return currentShop().getUpgradesOwned();
    }

    public void setUpgradesOwned(List<String> upgradesOwned) {
        currentShop().setUpgradesOwned(upgradesOwned);
    }

    public Map<String, InventorySlot> getInventory() {
        return currentShop().getInventory();
    }

    public void setInventory(Map<String, InventorySlot> inventory) {
        currentShop().setInventory(inventory);
    }

    public Map<String, Double> getPriceMultiplier() {
        return currentShop().getPriceMultiplier();
    }

    public void setPriceMultiplier(Map<String, Double> priceMultiplier) {
        currentShop().setPriceMultiplier(priceMultiplier);
    }

    public Map<String, NpcState> getNpcs() {
        return npcs;
    }

    public void setNpcs(Map<String, NpcState> npcs) {
        this.npcs = npcs;
    }

    public List<LoanState> getLoans() {
        return loans;
    }

    public void setLoans(List<LoanState> loans) {
        this.loans = loans;
    }

    public List<Buff> getBuffs() {
        return buffs;
    }

    public void setBuffs(List<Buff> buffs) {
        this.buffs = buffs;
    }

    public Stats getStats() {
        return stats;
    }

    public void setStats(Stats stats) {
        this.stats = stats;
    }

    public List<DayReport> getDayHistory() {
        return dayHistory;
    }

    public void setDayHistory(List<DayReport> dayHistory) {
        this.dayHistory = dayHistory;
    }

    public boolean isEnded() {
        return ended;
    }

    public void setEnded(boolean ended) {
        this.ended = ended;
    }

    public String getEndType() {
        return endType;
    }

    public void setEndType(String endType) {
        this.endType = endType;
    }

    public String getEndMessage() {
        return endMessage;
    }

    public void setEndMessage(String endMessage) {
        this.endMessage = endMessage;
    }

    public int getDebtOverLimitDays() {
        return debtOverLimitDays;
    }

    public void setDebtOverLimitDays(int debtOverLimitDays) {
        this.debtOverLimitDays = debtOverLimitDays;
    }

    public String getDailyBrief() {
        return dailyBrief;
    }

    public void setDailyBrief(String dailyBrief) {
        this.dailyBrief = dailyBrief;
    }

    public long getPromoUntilMin() {
        return promoUntilMin;
    }

    public void setPromoUntilMin(long promoUntilMin) {
        this.promoUntilMin = promoUntilMin;
    }

    public double getPromoMult() {
        return promoMult;
    }

    public void setPromoMult(double promoMult) {
        this.promoMult = promoMult;
    }

    public String getHotProductId() {
        return hotProductId;
    }

    public void setHotProductId(String hotProductId) {
        this.hotProductId = hotProductId;
    }

    public long getHotUntilMin() {
        return hotUntilMin;
    }

    public void setHotUntilMin(long hotUntilMin) {
        this.hotUntilMin = hotUntilMin;
    }

    public int getHotCount() {
        return hotCount;
    }

    public void setHotCount(int hotCount) {
        this.hotCount = hotCount;
    }

    public Map<String, Boolean> getAchievements() {
        return achievements;
    }

    public void setAchievements(Map<String, Boolean> achievements) {
        this.achievements = achievements;
    }

    // ---- NPC 社交 / 贸易策略 ----

    public Map<String, Long> getSocialCooldowns() {
        return socialCooldowns;
    }

    public void setSocialCooldowns(Map<String, Long> socialCooldowns) {
        this.socialCooldowns = socialCooldowns;
    }

    public long getTradeCooldownUntil() {
        return tradeCooldownUntil;
    }

    public void setTradeCooldownUntil(long tradeCooldownUntil) {
        this.tradeCooldownUntil = tradeCooldownUntil;
    }

    public boolean isDeliveryOpen() {
        return deliveryOpen;
    }

    public void setDeliveryOpen(boolean deliveryOpen) {
        this.deliveryOpen = deliveryOpen;
    }

    public int getDeliveryTodayOrders() {
        return deliveryTodayOrders;
    }

    public void setDeliveryTodayOrders(int deliveryTodayOrders) {
        this.deliveryTodayOrders = deliveryTodayOrders;
    }

    public double getDeliveryTodayRevenue() {
        return deliveryTodayRevenue;
    }

    public void setDeliveryTodayRevenue(double deliveryTodayRevenue) {
        this.deliveryTodayRevenue = deliveryTodayRevenue;
    }

    public double getDeliveryTodayCost() {
        return deliveryTodayCost;
    }

    public void setDeliveryTodayCost(double deliveryTodayCost) {
        this.deliveryTodayCost = deliveryTodayCost;
    }

    public int getDeliveryReviews() {
        return deliveryReviews;
    }

    public void setDeliveryReviews(int deliveryReviews) {
        this.deliveryReviews = deliveryReviews;
    }

    public int getDeliveryTotalOrders() {
        return deliveryTotalOrders;
    }

    public void setDeliveryTotalOrders(int deliveryTotalOrders) {
        this.deliveryTotalOrders = deliveryTotalOrders;
    }

    public double getDeliveryTotalRevenue() {
        return deliveryTotalRevenue;
    }

    public void setDeliveryTotalRevenue(double deliveryTotalRevenue) {
        this.deliveryTotalRevenue = deliveryTotalRevenue;
    }

    public int getNpcVisitBonus() {
        return npcVisitBonus;
    }

    public void setNpcVisitBonus(int npcVisitBonus) {
        this.npcVisitBonus = npcVisitBonus;
    }

    public Map<String, StaffState> getStaff() {
        return currentShop().getStaff();
    }

    public void setStaff(Map<String, StaffState> staff) {
        currentShop().setStaff(staff);
    }

    public int getPayrollDebtDays() {
        return currentShop().getPayrollDebtDays();
    }

    public void setPayrollDebtDays(int payrollDebtDays) {
        currentShop().setPayrollDebtDays(payrollDebtDays);
    }

    public List<String> getLiveLog() {
        return liveLog;
    }

    public void setLiveLog(List<String> liveLog) {
        this.liveLog = liveLog;
    }

    public FestivalState getFestival() {
        return festival;
    }

    public void setFestival(FestivalState festival) {
        this.festival = festival;
    }

    public List<String> getFestivalHistory() {
        return festivalHistory;
    }

    public void setFestivalHistory(List<String> festivalHistory) {
        this.festivalHistory = festivalHistory;
    }

    public double getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(double bankBalance) {
        this.bankBalance = bankBalance;
    }

    public double getBankInterestEarned() {
        return bankInterestEarned;
    }

    public void setBankInterestEarned(double bankInterestEarned) {
        this.bankInterestEarned = bankInterestEarned;
    }

    public QuestState getActiveQuest() {
        return activeQuest;
    }

    public void setActiveQuest(QuestState activeQuest) {
        this.activeQuest = activeQuest;
    }

    public int getQuestsCompleted() {
        return questsCompleted;
    }

    public void setQuestsCompleted(int questsCompleted) {
        this.questsCompleted = questsCompleted;
    }

    public int getQuestsFailed() {
        return questsFailed;
    }

    public void setQuestsFailed(int questsFailed) {
        this.questsFailed = questsFailed;
    }

    public int getLotteryTicketDay() {
        return lotteryTicketDay;
    }

    public void setLotteryTicketDay(int lotteryTicketDay) {
        this.lotteryTicketDay = lotteryTicketDay;
    }

    public int getLotteryLastPrize() {
        return lotteryLastPrize;
    }

    public void setLotteryLastPrize(int lotteryLastPrize) {
        this.lotteryLastPrize = lotteryLastPrize;
    }

    public double getScratchLastWin() {
        return scratchLastWin;
    }

    public void setScratchLastWin(double scratchLastWin) {
        this.scratchLastWin = scratchLastWin;
    }

    public int getScratchPurchases() {
        return scratchPurchases;
    }

    public void setScratchPurchases(int scratchPurchases) {
        this.scratchPurchases = scratchPurchases;
    }

    public List<FinanceState> getFinances() {
        return finances;
    }

    public void setFinances(List<FinanceState> finances) {
        this.finances = finances;
    }

    /** 当前地段委托给当前店（分店系统 v1.9.0：每家店独立选址） */
    public String getCurrentLocationId() {
        ShopState sh = currentShop();
        return sh == null ? currentLocationId : sh.getLocationId();
    }

    public void setCurrentLocationId(String currentLocationId) {
        ShopState sh = currentShop();
        if (sh != null) {
            sh.setLocationId(currentLocationId);
        }
        this.currentLocationId = currentLocationId;
    }

    public HouseLoanState getHouseLoan() {
        return houseLoan;
    }

    public void setHouseLoan(HouseLoanState houseLoan) {
        this.houseLoan = houseLoan;
    }

    public int getRelocations() {
        return relocations;
    }

    public void setRelocations(int relocations) {
        this.relocations = relocations;
    }

    public java.util.List<String> getOwnedItems() {
        return ownedItems;
    }

    public void setOwnedItems(java.util.List<String> ownedItems) {
        this.ownedItems = ownedItems;
    }

    public java.util.Map<String, String> getWornItems() {
        return wornItems;
    }

    public void setWornItems(java.util.Map<String, String> wornItems) {
        this.wornItems = wornItems;
    }

    public java.util.List<String> getOwnedAssets() {
        return ownedAssets;
    }

    public void setOwnedAssets(java.util.List<String> ownedAssets) {
        this.ownedAssets = ownedAssets;
    }

    public int getPrestigeInteractDay() {
        return prestigeInteractDay;
    }

    public void setPrestigeInteractDay(int prestigeInteractDay) {
        this.prestigeInteractDay = prestigeInteractDay;
    }

    public int getPrestigeInteractions() {
        return prestigeInteractions;
    }

    public void setPrestigeInteractions(int prestigeInteractions) {
        this.prestigeInteractions = prestigeInteractions;
    }

    // ---- 供应链系统 ----

    public double getSupplyPoints() {
        return supplyPoints;
    }

    public void setSupplyPoints(double supplyPoints) {
        this.supplyPoints = supplyPoints;
    }

    public double getCreditUsed() {
        return creditUsed;
    }

    public void setCreditUsed(double creditUsed) {
        this.creditUsed = creditUsed;
    }

    public long getCreditDueMin() {
        return creditDueMin;
    }

    public void setCreditDueMin(long creditDueMin) {
        this.creditDueMin = creditDueMin;
    }

    // ---- 口碑评级系统 ----

    public double getSatisfactionScore() {
        return satisfactionScore;
    }

    public void setSatisfactionScore(double satisfactionScore) {
        this.satisfactionScore = satisfactionScore;
    }

    public int getTodayExpiredQty() {
        return todayExpiredQty;
    }

    public void setTodayExpiredQty(int todayExpiredQty) {
        this.todayExpiredQty = todayExpiredQty;
    }

    public java.util.List<Double> getSatisfactionHistory() {
        return satisfactionHistory;
    }

    public void setSatisfactionHistory(java.util.List<Double> satisfactionHistory) {
        this.satisfactionHistory = satisfactionHistory;
    }

    public double getRatingMonthSum() {
        return ratingMonthSum;
    }

    public void setRatingMonthSum(double ratingMonthSum) {
        this.ratingMonthSum = ratingMonthSum;
    }

    public int getRatingMonthDays() {
        return ratingMonthDays;
    }

    public void setRatingMonthDays(int ratingMonthDays) {
        this.ratingMonthDays = ratingMonthDays;
    }

    public int getRatingTier() {
        return ratingTier;
    }

    public void setRatingTier(int ratingTier) {
        this.ratingTier = ratingTier;
    }

    public double getLastMonthAvg() {
        return lastMonthAvg;
    }

    public void setLastMonthAvg(double lastMonthAvg) {
        this.lastMonthAvg = lastMonthAvg;
    }

    // ---- 街区竞争系统 ----

    public java.util.List<CompetitorState> getCompetitors() {
        return competitors;
    }

    public void setCompetitors(java.util.List<CompetitorState> competitors) {
        this.competitors = competitors;
    }

    public long getCompeteCooldownUntil() {
        return competeCooldownUntil;
    }

    public void setCompeteCooldownUntil(long competeCooldownUntil) {
        this.competeCooldownUntil = competeCooldownUntil;
    }

    public long getPriceWarUntilMin() {
        return priceWarUntilMin;
    }

    public void setPriceWarUntilMin(long priceWarUntilMin) {
        this.priceWarUntilMin = priceWarUntilMin;
    }

    public long getAdWarUntilMin() {
        return adWarUntilMin;
    }

    public void setAdWarUntilMin(long adWarUntilMin) {
        this.adWarUntilMin = adWarUntilMin;
    }
}
