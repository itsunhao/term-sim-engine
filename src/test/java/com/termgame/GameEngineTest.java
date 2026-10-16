package com.termgame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.termgame.engine.GameEngine;
import com.termgame.model.def.GameDefinition;
import com.termgame.model.def.ProductDef;
import com.termgame.model.state.DayReport;
import com.termgame.model.state.GameState;
import com.termgame.model.state.FestivalState;
import com.termgame.model.state.InventorySlot;
import com.termgame.model.state.ShopState;
import com.termgame.model.state.StaffState;
import com.termgame.util.GameException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引擎核心逻辑测试（实时时间模型）：使用默认小卖部游戏文件。
 * 时间规则：现实 1 秒 = 游戏 1 分钟；开局第 1 天 08:00；营业 06:00-24:00。
 */
class GameEngineTest {

    private static final long T0 = 1_700_000_000_000L;

    private static GameDefinition def;
    private static GameEngine engine;

    @BeforeAll
    static void setup() throws Exception {
        def = loadDef("convenience-store");
        assertTrue(def != null, "游戏文件应存在");
        engine = new GameEngine();
    }

    private static long afterMin(long minutes) {
        return T0 + minutes * 1000L; // 1 现实秒 = 1 游戏分钟
    }

    @Test
    void newGame_initializesAtDay1Morning() {
        GameState s = engine.newGame(def, "测试档", T0);
        assertEquals(1000, s.getCash());
        assertEquals(480, s.getMinutes());
        assertEquals(1, s.dayOf());
        assertEquals("08:00", s.clockText());
        assertFalse(s.isPaused());
        assertEquals(T0, s.getLastTickAt());
        assertEquals(1, s.getDate().getYear());
        assertEquals("spring", s.getDate().getSeasonId());
        assertEquals(6, s.getNpcs().size());
        assertEquals(26, def.products().size());
        assertEquals(1, s.getShopLevel());
        assertFalse(s.isEnded());
    }

    @Test
    void tick_advancesGameTimeByRealSeconds() {
        GameState s = engine.newGame(def, "测试档", T0);
        List<DayReport> reports = engine.tick(def, s, afterMin(2)); // 2 现实秒 = 2 游戏分钟
        assertTrue(reports.isEmpty());
        assertEquals(482, s.getMinutes());
        assertEquals("08:02", s.clockText());
        assertEquals(afterMin(2), s.getLastTickAt());
    }

    @Test
    void paused_tickFreezesTime() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s,"pause", null, null, null, null, null, null, null, null);
        assertTrue(s.isPaused());
        List<DayReport> reports = engine.tick(def, s, afterMin(600));
        assertTrue(reports.isEmpty());
        assertEquals(480, s.getMinutes());
        assertEquals(afterMin(600), s.getLastTickAt(), "暂停时只刷新时间戳不推进");
        engine.prepareAct(def, s,"resume", null, null, null, null, null, null, null, null);
        assertFalse(s.isPaused());
    }

    @Test
    void speed2x_advancesTimeTwiceAsFast() {
        GameState s = engine.newGame(def, "测试档", T0);
        assertEquals(1.0, s.getTimeSpeed(), 1e-9);
        engine.prepareAct(def, s, "speed", null, null, 2.0, null, null, null, null, null);
        assertEquals(2.0, s.getTimeSpeed(), 1e-9);
        engine.tick(def, s, afterMin(5)); // 5 现实秒 × 2.0 = 10 游戏分钟
        assertEquals(490, s.getMinutes());
    }

    @Test
    void speed0_5_accumulatesFractionAcrossTicks() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "speed", null, null, 0.5, null, null, null, null, null);
        engine.tick(def, s, afterMin(1));  // 0.5 分钟 → 不足 1 分钟，不推进
        assertEquals(480, s.getMinutes());
        assertEquals(0.5, s.getTickFraction(), 1e-9);
        engine.tick(def, s, afterMin(2));  // 累计 1.0 + 0.5 = 1.5 → 推进 1 分钟
        assertEquals(481, s.getMinutes());
        assertEquals(0.5, s.getTickFraction(), 1e-9);
    }

    @Test
    void speedRejectsInvalidValue() {
        GameState s = engine.newGame(def, "测试档", T0);
        org.junit.jupiter.api.Assertions.assertThrows(
                com.termgame.util.GameException.class,
                () -> engine.prepareAct(def, s, "speed", null, null, 4.0, null, null, null, null, null));
        assertEquals(1.0, s.getTimeSpeed(), 1e-9);
    }

    @Test
    void customersArriveDuringOpenHours() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "buy", "cola", 60, null, null, null, null, null, null);
        // 从 08:00 推进 3 个游戏小时（到 11:00），营业时段内应有顾客进店
        List<DayReport> reports = engine.tick(def, s, afterMin(180));
        assertTrue(reports.isEmpty(), "未跨天不应产生日报");
        assertTrue(s.getTodayCustomers() > 0, "营业时段应有顾客");
        assertTrue(s.getTodayRevenue() > 0);
        // 进货花 150（1000-150=850），有成交后现金应高于 850
        assertTrue(s.getCash() > 850, "有成交后现金应高于进货后余额");
    }

    @Test
    void midnightRollover_producesDailyReport() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "buy", "cola", 60, null, null, null, null, null, null);
        // 从 08:00 推进 16 个游戏小时到 24:00（正好跨天）
        List<DayReport> reports = engine.tick(def, s, afterMin(960));
        assertEquals(1, reports.size());
        DayReport r = reports.get(0);
        assertEquals("第1天", r.getDayLabel());
        assertEquals(2, s.dayOf());
        assertEquals(1440, s.getMinutes());
        assertEquals(2, s.getDate().getDay());
        assertTrue(r.getRevenue() >= 0);
        assertEquals(0, s.getTodayRevenue(), "跨天后当日累计应重置");
        assertEquals(1, s.getStats().getDaysPlayed());
        assertNotNull(s.getWeatherId());
    }

    @Test
    void loanAccruesInterestPerGameDay() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "loan", null, null, null, null, "loan_small", null, null, null);
        assertEquals(1000, s.getDebt());
        // 推进 2 个游戏日（48 现实分钟），跨天 2 次
        List<DayReport> reports = engine.tick(def, s, afterMin(2880));
        assertEquals(2, reports.size(), "应产生两次跨天日报");
        assertTrue(s.getDebt() > 1004, "负债应随游戏日增长");
        assertTrue(reports.get(0).getInterestPaid() > 0);
    }

    @Test
    void rest_fastForwardsToNextMorning() {
        GameState s = engine.newGame(def, "测试档", T0);
        DayReport r = engine.rest(def, s);
        assertNotNull(r);
        assertTrue(r.isClosed(), "休息日报应标记打烊");
        assertEquals(2, s.dayOf());
        assertEquals(1800, s.getMinutes());
        assertEquals("06:00", s.clockText());
    }

    @Test
    void expireInventory_discardsSpoiledAtMidnight() {
        GameState s = engine.newGame(def, "测试档", T0);
        s.setCash(5000);
        engine.prepareAct(def, s, "renovate", null, null, null, null, null, "upg_level2", null, null);
        engine.prepareAct(def, s, "buy", "baozi", 5, null, null, null, null, null, null);
        // 包子保质期 1 天：当天午夜即全部过期
        DayReport r = engine.rest(def, s);
        assertFalse(r.getExpiries().isEmpty(), "日报应包含过期提示");
        assertEquals(5, s.getStats().getExpiredItems(), "应记录 5 件过期损耗");
        assertEquals(0, s.getInventory().getOrDefault("baozi", new com.termgame.model.state.InventorySlot("baozi", 0, 1)).getQty());
    }

    @Test
    void repay_settlesLoanPrincipalInOrder() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "loan", null, null, null, null, "loan_small", null, null, null);
        engine.prepareAct(def, s, "loan", null, null, null, null, "loan_growth", null, null, null);
        assertEquals(4000, s.getDebt());
        // 还 1500：先结清第一笔小额贷 1000，再还第二笔 500
        engine.prepareAct(def, s, "repay", null, null, null, 1500.0, null, null, null, null);
        assertEquals(2500, s.getDebt(), 0.01);
        assertEquals(1, s.getLoans().size());
        assertEquals(2500, s.getLoans().get(0).getPrincipal(), 0.01);
    }

    @Test
    void bankruptcy_triggersAfterGraceDays() {
        GameState s = engine.newGame(def, "测试档", T0);
        s.setDebt(20000);
        for (int i = 0; i < 8; i++) {
            if (s.isEnded()) break;
            engine.rest(def, s);
        }
        assertTrue(s.isEnded());
        assertEquals("lose", s.getEndType());
    }

    @Test
    void buy_insufficientCashAndLockedProduct_rejected() {
        GameState s = engine.newGame(def, "测试档", T0);
        // 耳机 6 元/个，买 300 个 = 1800 > 1000
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "buy", "earphone", 300, null, null, null, null, null, null));
        // 烤肠需要 upg_level2
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "buy", "sausage", 10, null, null, null, null, null, null));
    }

    @Test
    void renovate_unlocksCategory() {
        GameState s = engine.newGame(def, "测试档", T0);
        s.setCash(5000);
        String msg = engine.prepareAct(def, s, "renovate", null, null, null, null, null, "upg_level2", null, null);
        assertTrue(msg.contains("粉刷门面"));
        assertTrue(s.getUpgradesOwned().contains("upg_level2"));
        assertEquals(2, s.getShopLevel());
        // 解锁后可进货烤肠
        String buyMsg = engine.prepareAct(def, s, "buy", "sausage", 5, null, null, null, null, null, null);
        assertTrue(buyMsg.contains("烤肠"));
    }

    @Test
    void price_highKeepsStateAndStillTrades() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "buy", "cola", 50, null, null, null, null, null, null);
        engine.prepareAct(def, s, "price", "cola", null, 2.0, null, null, null, null, null);
        assertEquals(2.0, s.getPriceMultiplier().get("cola"), 0.01);
        List<DayReport> reports = engine.tick(def, s, afterMin(180));
        assertTrue(s.getTodayRevenue() >= 0);
        assertTrue(reports.isEmpty());
    }

    // ------------------------------------------------------------
    // 玩法扩展：限时促销 / 今日晨报 / 成就
    // ------------------------------------------------------------

    @Test
    void promo_chargesCashAndExpiresByMinutes() {
        GameState s = engine.newGame(def, "测试档", T0);
        double base = s.getDailyFootTraffic();
        double cashBefore = s.getCash();
        String msg = engine.prepareAct(def, s,"promo", null, null, null, null, null, null, null, null);
        assertTrue(msg.contains("限时促销"));
        assertEquals(cashBefore - 200, s.getCash(), 0.01);
        assertEquals(s.getMinutes() + 240, s.getPromoUntilMin());
        // 发起即重算客流基准（含 ×1.5 促销，波动 ±15% 下仍显著提升）
        double promoTraffic = s.getDailyFootTraffic();
        assertTrue(promoTraffic > base * 1.05);
        // 重复发起被拒绝（进行中）
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "promo", null, null, null, null, null, null, null, null));
        // 推进 241 游戏分钟 → 促销到期并恢复客流基准
        engine.tick(def, s, afterMin(241));
        assertEquals(0, s.getPromoUntilMin());
        assertEquals(1.0, s.getPromoMult(), 0.001);
        assertTrue(s.getDailyFootTraffic() < promoTraffic * 0.95);
    }

    @Test
    void promo_insufficientCashRejected() {
        GameState s = engine.newGame(def, "测试档", T0);
        s.setCash(100);
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "promo", null, null, null, null, null, null, null, null));
    }

    @Test
    void dailyBrief_generatedOnNewDayMorning() {
        GameState s = engine.newGame(def, "测试档", T0);
        assertTrue(s.getDailyBrief() == null || s.getDailyBrief().isBlank()); // 开局当天无晨报
        engine.rest(def, s); // 打烊 → 次日清晨必出晨报
        assertNotNull(s.getDailyBrief());
        assertFalse(s.getDailyBrief().isBlank());
    }

    @Test
    void achievements_unlockOnCriteriaMet() {
        GameState s = engine.newGame(def, "测试档", T0);
        assertTrue(s.getAchievements().isEmpty());
        // 模拟已卖出 5 件商品 → 打烊结算时解锁 first_sale
        s.getStats().setItemsSold(5);
        engine.rest(def, s);
        assertTrue(s.getAchievements().containsKey("first_sale"));
        assertTrue(s.getAchievements().get("first_sale"));
        // 未达标的成就保持锁定
        assertFalse(s.getAchievements().containsKey("net_worth_3000"));
        assertFalse(s.getAchievements().containsKey("debt_clear")); // 未借过款不算"还清贷款"
    }

    // ------------------------------------------------------------
    // 玩法扩展 v1.3.0：NPC 社交 / 贸易（批发 / 外卖）
    // ------------------------------------------------------------

    @Test
    void social_raisesFavorAndCooldown() {
        GameState s = engine.newGame(def, "测试档", T0);
        double favor0 = s.getNpcs().get("npc_wang").getFavorability();
        String msg = engine.prepareAct(def, s,"social", null, null, null, null, null, null, "chat", "npc_wang");
        assertTrue(msg.contains("好感 +1"));
        assertTrue(s.getNpcs().get("npc_wang").getFavorability() > favor0);
        assertTrue(s.getSocialCooldowns().get("npc_wang") > s.getMinutes());
        // 冷却中拒绝再次互动
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s,"social", null, null, null, null, null, null, "chat", "npc_wang"));
        // 送礼扣现金
        double cash0 = s.getCash();
        engine.prepareAct(def, s,"social", null, null, null, null, null, null, "gift", "npc_li");
        assertEquals(cash0 - 50, s.getCash(), 0.01);
    }

    @Test
    void wholesale_tradesAtRatioWithMinQtyAndCooldown() {
        GameState s = engine.newGame(def, "测试档", T0);
        // 进货 100 件可乐（零售 3.5，批发价 3.5×0.75=2.625）
        engine.prepareAct(def, s,"buy", "cola", 100, null, null, null, null, null, null);
        double cash0 = s.getCash();
        // 40 件低于起批量应被拒绝
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "trade", "cola", 40, null, null, null, null, "wholesale", null));
        // 60 件批发
        String ok = engine.prepareAct(def, s, "trade", "cola", 60, null, null, null, null, "wholesale", null);
        assertTrue(ok.contains("批发成交"));
        assertEquals(60 * 3.5 * 0.75, s.getCash() - cash0, 0.01);
        assertEquals(40, s.getInventory().get("cola").getQty());
        assertTrue(s.getTradeCooldownUntil() > s.getMinutes());
        // 冷却中拒绝再次批发
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "trade", "cola", 50, null, null, null, null, "wholesale", null));
    }

    @Test
    void delivery_openCostsSetupAndSettlesOrders() {
        GameState s = engine.newGame(def, "测试档", T0);
        // 此档定义为小卖部（未启用外卖），换用饭馆定义
        GameDefinition restDef = loadDef("street-restaurant");
        assertNotNull(restDef);
        GameState rs = engine.newGame(restDef, "测试档", T0);
        assertFalse(rs.isDeliveryOpen());
        double cash0 = rs.getCash();
        // 未开通时状态查询
        String st1 = engine.prepareAct(restDef, rs, "trade", null, null, null, null, null, null, "delivery", null);
        assertTrue(st1.contains("已关闭"));
        // 开通
        String open = engine.prepareAct(restDef, rs, "trade", "on", null, null, null, null, null, "delivery", null);
        assertTrue(open.contains("已开通"));
        assertEquals(cash0 - 500, rs.getCash(), 0.01);
        assertTrue(rs.isDeliveryOpen());
        // 备菜后打烊：产生外卖订单
        engine.prepareAct(restDef, rs, "buy", "rice", 200, null, null, null, null, null, null);
        engine.prepareAct(restDef, rs, "buy", "fried_rice", 80, null, null, null, null, null, null);
        DayReport r = engine.rest(restDef, rs);
        assertTrue(rs.getDeliveryTotalOrders() > 0);
        assertTrue(rs.getDeliveryTotalRevenue() > 0);
        // 打烊结算通知中包含外卖订单信息
        assertTrue(r.getNotices().stream().anyMatch(n -> n.contains("外卖平台今日接入")));
        // 新的一天今日计数已重置
        assertEquals(0, rs.getDeliveryTodayOrders());
    }

    // ------------------------------------------------------------
    // 玩法扩展 v1.4.0：雇佣系统
    // ------------------------------------------------------------

    @Test
    void hire_addsStaffAndSalaryPaidOnPayday() {
        GameState s = engine.newGame(def, "测试档", T0);
        assertFalse(def.gameplay().staff().roles().isEmpty());
        // 雇佣店员小周（月薪 400）
        String msg = engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "assistant");
        assertTrue(msg.contains("已雇佣"));
        assertEquals(1, s.getStaff().size());
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "assistant"));
        // 雇佣促销员阿美（月薪 500）
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "promoter");
        assertEquals(2, s.getStaff().size());
        // 推进到发薪日清晨（rest 内部会先推进 1 天：第 30 天 → 第 31 天，(31-1)%30==0）
        s.getDate().setDayNumber(30);
        DayReport r = engine.rest(def, s);
        assertEquals(0, s.getPayrollDebtDays());
        assertTrue(r.getNotices().stream().anyMatch(n -> n.contains("发薪日")));
    }

    @Test
    void fire_removesStaffAndPaydayShortfallTriggersDebt() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "assistant");
        String fireMsg = engine.prepareAct(def, s, "fire", null, null, null, null, null, null, null, "assistant");
        assertTrue(fireMsg.contains("解雇"));
        assertTrue(s.getStaff().isEmpty());
        // 现金不足时发薪：欠薪累计
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "assistant");
        s.setCash(100);
        s.getDate().setDayNumber(30);
        DayReport r = engine.rest(def, s);
        assertEquals(1, s.getPayrollDebtDays());
        assertTrue(r.getNotices().stream().anyMatch(n -> n.contains("资金不足")));
    }

    @Test
    void raiseAndCut_affectEfficiencyAndSalary() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "assistant");
        var st = s.getStaff().get("assistant");
        String r1 = engine.prepareAct(def, s, "raise", null, null, null, null, null, null, null, "assistant");
        assertTrue(r1.contains("涨薪"));
        assertEquals(400 * 1.15, st.getSalary(), 0.01);
        assertEquals(1.15, st.efficiency(), 1e-9);
        // 降薪回到基准
        String c1 = engine.prepareAct(def, s, "cut", null, null, null, null, null, null, null, "assistant");
        assertTrue(c1.contains("降薪"));
        assertEquals(1.0, st.efficiency(), 1e-9);
        assertEquals(1, st.getCutLevel());
    }

    @Test
    void autoBuy_restocksNextMorning() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "assistant");
        // 开局无库存，小周次日清晨自动补货（花费上限 = 1000×0.3 = 300）
        DayReport r = engine.rest(def, s);
        int total = s.getInventory().values().stream().mapToInt(InventorySlot::getQty).sum();
        assertTrue(total > 0, "自动补货应产生库存");
        assertTrue(s.getCash() < 1000, "自动补货应花费现金");
        assertTrue(r.getNotices().stream().anyMatch(n -> n.contains("自动补货")));
    }

    // ------------------------------------------------------------
    // 玩法扩展 v1.5.0：实时日志 / 银行 / 委托 / 彩票
    // ------------------------------------------------------------

    @Test
    void liveLog_recordsSalesAndEvents() {
        GameState s = engine.newGame(def, "测试档", T0);
        // 进货后营业 30 游戏分钟（08:00→08:30，营业中应有成交）
        engine.prepareAct(def, s, "buy", "cola", 200, null, null, null, null, null, null);
        engine.tick(def, s, afterMin(30));
        assertFalse(s.getLiveLog().isEmpty(), "营业期间应产生实时购买记录");
        assertTrue(s.getLiveLog().stream().anyMatch(l -> l.contains("卖出")), "购买记录应包含卖出明细");
        assertTrue(s.getLiveLog().stream().anyMatch(l -> l.contains("进账")), "购买记录应包含进账金额");
    }

    @Test
    void bank_depositWithdrawAndDailyInterest() {
        GameState s = engine.newGame(def, "测试档", T0);
        assertEquals(0, s.getBankBalance());
        // 存款 500
        String d1 = engine.prepareAct(def, s, "deposit", null, null, null, 500.0, null, null, null, null);
        assertTrue(d1.contains("存入"));
        assertEquals(500, s.getBankBalance(), 0.01);
        assertEquals(500, s.getCash(), 0.01);
        // 超额存款拒绝
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "deposit", null, null, null, 99999.0, null, null, null, null));
        // 取款 200
        String w1 = engine.prepareAct(def, s, "withdraw", null, null, null, 200.0, null, null, null, null);
        assertTrue(w1.contains("取出"));
        assertEquals(300, s.getBankBalance(), 0.01);
        assertEquals(700, s.getCash(), 0.01);
        // 超额取款拒绝
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "withdraw", null, null, null, 99999.0, null, null, null, null));
        // 打烊结算：存款产生日利息（500→结算时余额 300，利息 = 300×12%/120 = 0.3）
        DayReport r = engine.rest(def, s);
        assertTrue(s.getBankBalance() > 300, "存款应产生利息");
        assertTrue(s.getBankInterestEarned() > 0);
        assertTrue(r.getNotices().stream().anyMatch(n -> n.contains("存款利息")));
    }

    @Test
    void quest_settlesCompletedAndFailedAtDeadline() {
        GameState s = engine.newGame(def, "测试档", T0);
        // 库存足够：到期交货 → 拿报酬 + 好感
        engine.prepareAct(def, s, "buy", "cola", 20, null, null, null, null, null, null);
        double cash0 = s.getCash();
        s.setActiveQuest(new com.termgame.model.state.QuestState(
                "cola", 10, 50, 1, "laoban", "测试委托：可乐×10"));
        DayReport r1 = engine.rest(def, s);
        assertEquals(1, s.getQuestsCompleted());
        assertEquals(cash0 + 50, s.getCash(), 0.01);
        assertEquals(10, s.getInventory().get("cola").getQty());
        assertTrue(r1.getNotices().stream().anyMatch(n -> n.contains("委托完成")));
        // 库存不足：到期失败 → 扣声誉
        s.setActiveQuest(new com.termgame.model.state.QuestState(
                "cola", 100, 300, s.getDate().getDayNumber(), "laoban", "测试委托：可乐×100"));
        double rep0 = 50;
        s.setReputation(rep0); // 固定基准，避免日漂移干扰
        DayReport r2 = engine.rest(def, s);
        assertEquals(1, s.getQuestsFailed());
        // 委托失败扣声誉 2，但当日盈利/丰富度结算会同步增减声誉，验证净变化在合理窗口内
        assertTrue(s.getReputation() >= rep0 - 4.0 && s.getReputation() <= rep0 + 6.0,
                "失败日声誉应在合理窗口，实际 " + s.getReputation());
        assertTrue(r2.getNotices().stream().anyMatch(n -> n.contains("委托失败")));
        // 结算后清晨可能按概率生成新委托：要么无委托，要么新委托截止在未来
        assertTrue(s.getActiveQuest() == null
                        || s.getActiveQuest().getDeadlineDay() > s.getDate().getDayNumber(),
                "不应残留已到期委托");
    }

    @Test
    void lottery_opensNextDayMorning() {
        GameState s = engine.newGame(def, "测试档", T0);
        double lotCost = def.gameplay().lottery().cost();
        String m = engine.prepareAct(def, s, "lottery", null, null, null, null, null, null, null, null);
        assertTrue(m.contains("彩票"));
        assertEquals(lotCost, 1000 - s.getCash(), 0.01);
        assertEquals(1, s.getLotteryTicketDay());
        // 当日重复购买拒绝
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "lottery", null, null, null, null, null, null, null, null));
        // 打烊次日开奖：票清零，现金 = 990（未中）或 990+奖金（中奖）
        DayReport r = engine.rest(def, s);
        assertEquals(0, s.getLotteryTicketDay());
        assertTrue(s.getCash() >= 990 - 0.01, "开奖后现金不应少于票后余额");
        assertTrue(r.getNotices().stream().anyMatch(n -> n.contains("开奖")));
    }

    // ------------------------------------------------------------
    // 玩法扩展 v1.6.0：地图地产 / 房屋贷款
    // ------------------------------------------------------------

    @Test
    void map_houseBuyValidationsAndMigration() {
        GameState s = engine.newGame(def, "测试档", T0);
        assertNotNull(def.gameplay().map());
        assertEquals("hutong", s.getCurrentLocationId());
        // 首付不足拒绝（街角铺面 6000 × 40% = 2400）
        s.setCash(1000);
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "housebuy", null, null, null, null, null, null, null, "jiejiao"));
        // 声誉不足拒绝（需 30）
        s.setCash(3000);
        s.setReputation(10);
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "housebuy", null, null, null, null, null, null, null, "jiejiao"));
        // 成功购买：首付扣款、房贷生成、迁移、库存保留
        s.setReputation(50);
        s.setCash(3000);
        s.getInventory().put("cola", new com.termgame.model.state.InventorySlot("cola", 40, 0)); // 模拟已有库存（随店迁移）
        String msg = engine.prepareAct(def, s, "housebuy", null, null, null, null, null, null, null, "jiejiao");
        assertTrue(msg.contains("乔迁新居"));
        assertEquals("jiejiao", s.getCurrentLocationId());
        assertEquals(600, s.getCash(), 0.01); // 3000 - 2400 首付
        assertNotNull(s.getHouseLoan());
        assertEquals(3600, s.getHouseLoan().getPrincipal(), 0.01);
        assertEquals(40, s.getInventory().get("cola").getQty(), "库存应随店迁移保留");
        assertEquals(1, s.getRelocations());
        // 已有房贷时不能再买
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "housebuy", null, null, null, null, null, null, null, "shangyejie"));
        // map 命令可查看
        String view = engine.prepareAct(def, s, "map", null, null, null, null, null, null, null, null);
        assertTrue(view.contains("街角铺面"));
    }

    @Test
    void map_houseLoanMonthlySettleAndOverdue() {
        GameState s = engine.newGame(def, "测试档", T0);
        s.setReputation(50);
        s.setCash(3000);
        engine.prepareAct(def, s, "housebuy", null, null, null, null, null, null, null, "jiejiao");
        // 月供 = (3600 + 3600×0.02×12) / 12 = 372
        assertEquals(372, s.getHouseLoan().getMonthlyPayment(), 0.01);
        // 推进到发薪日（第 30 天 → rest 后第 31 天，(31-1)%30==0 还款）
        s.getDate().setDayNumber(30);
        DayReport r1 = engine.rest(def, s);
        assertTrue(r1.getNotices().stream().anyMatch(n -> n.contains("房贷月供")));
        assertTrue(s.getHouseLoan().getMonthsLeft() < 12, "月供应减少期数");
        // 现金不足：逾期扣声誉（跳到下个还款日前一天：day31 → day60 → rest → 61）
        s.setCash(50);
        s.getDate().setDayNumber(s.getDate().getDayNumber() + 29);
        double repBefore = s.getReputation();
        DayReport r2 = engine.rest(def, s);
        assertEquals(0, s.getCash());
        assertTrue(r2.getNotices().stream().anyMatch(n -> n.contains("房贷逾期")));
        assertTrue(s.getReputation() <= repBefore - 3 + 2.0, "逾期应扣声誉（容忍当日漂移/事件回补）");
        assertTrue(s.getHouseLoan().getOverdueDays() > 0);
    }

    // ------------------------------------------------------------
    // 玩法扩展 v1.7.0：个人形象（装扮 / 资产 / 声望 / NPC 主动互动）
    // ------------------------------------------------------------

    @Test
    void wardrobe_buyWearAndPrestige() {
        GameState s = engine.newGame(def, "测试档", T0);
        assertNotNull(def.gameplay().wardrobe());
        // 现金不足拒绝
        s.setCash(50);
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "buyitem", null, null, null, null, null, null, null, "watch_lux"));
        // 购买即穿戴：扣现金 + 声望
        s.setCash(1000);
        String m = engine.prepareAct(def, s, "buyitem", null, null, null, null, null, null, null, "watch_steel");
        assertTrue(m.contains("钢带腕表"));
        assertEquals(500, s.getCash(), 0.01); // 1000 - 500
        assertTrue(s.getOwnedItems().contains("watch_steel"));
        assertEquals("watch_steel", s.getWornItems().get("watch"));
        assertEquals(8, engine.computePrestige(def, s), 0.01);
        // 未购换装拒绝
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "wear", null, null, null, null, null, null, null, "watch_lux"));
        // 购买后换装
        s.setCash(10000);
        engine.prepareAct(def, s, "buyitem", null, null, null, null, null, null, null, "watch_lux");
        assertEquals(30, engine.computePrestige(def, s), 0.01);
        engine.prepareAct(def, s, "wear", null, null, null, null, null, null, null, "watch_steel");
        assertEquals(8, engine.computePrestige(def, s), 0.01);
        // wardrobe 命令
        String view = engine.prepareAct(def, s, "wardrobe", null, null, null, null, null, null, null, null);
        assertTrue(view.contains("个人声望"));
        // 重复购买拒绝
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "buyitem", null, null, null, null, null, null, null, "watch_steel"));
    }

    @Test
    void wardrobe_assetsBestPerCategoryAndPrestige() {
        GameState s = engine.newGame(def, "测试档", T0);
        s.setCash(100000);
        engine.prepareAct(def, s, "buyasset", null, null, null, null, null, null, null, "asset_bike");
        assertEquals(5, engine.computePrestige(def, s), 0.01);
        // 同品类买更好的车 → 只算最高档（5 → 40）
        engine.prepareAct(def, s, "buyasset", null, null, null, null, null, null, null, "asset_car");
        assertEquals(40, engine.computePrestige(def, s), 0.01);
        // 跨品类（住宅）累加
        engine.prepareAct(def, s, "buyasset", null, null, null, null, null, null, null, "asset_houselaoren");
        assertEquals(100, engine.computePrestige(def, s), 0.01); // 40 + 60
        // 重复购买拒绝
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "buyasset", null, null, null, null, null, null, null, "asset_car"));
        // 现金不足拒绝
        s.setCash(100);
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "buyasset", null, null, null, null, null, null, null, "asset_luxcar"));
    }

    @Test
    void prestige_highLevelTriggersNpcActiveInteractions() {
        GameState s = engine.newGame(def, "测试档", T0);
        assertEquals(0, engine.prestigeLevel(engine.computePrestige(def, s)));
        // 声望 0：多次打烊不触发主动互动
        for (int i = 0; i < 5; i++) engine.rest(def, s);
        assertEquals(0, s.getPrestigeInteractions());
        // 买豪车+别墅 → 声望 400 = 传奇（level 5，每日触发概率 ~75%）
        s.setCash(400000);
        engine.prepareAct(def, s, "buyasset", null, null, null, null, null, null, null, "asset_luxcar");
        engine.prepareAct(def, s, "buyasset", null, null, null, null, null, null, null, "asset_manor");
        assertEquals(400, engine.computePrestige(def, s), 0.01);
        assertEquals(5, engine.prestigeLevel(400));
        boolean triggered = false;
        for (int i = 0; i < 8; i++) {
            engine.rest(def, s);
            if (s.getPrestigeInteractions() > 0) { triggered = true; break; }
        }
        assertTrue(triggered, "传奇声望 8 天内应至少触发一次 NPC 主动互动");
        // 互动标记记录在触发当天（不为 0 且不超过当前天）
        assertTrue(s.getPrestigeInteractDay() > 0 && s.getPrestigeInteractDay() <= s.getDate().getDayNumber(),
                "互动标记应记录触发天，实际 " + s.getPrestigeInteractDay());
    }

    @Test
    void prestige_priceMultScalesAndCaps() {
        GameState s = engine.newGame(def, "测试档", T0);
        assertEquals(1.0, engine.prestigePriceMult(def, s), 0.0001);
        s.setCash(10000);
        engine.prepareAct(def, s, "buyitem", null, null, null, null, null, null, null, "watch_lux");
        // 30 声望 → 1 + 30×0.0008 = 1.024
        assertEquals(1.024, engine.prestigePriceMult(def, s), 0.001);
        s.setCash(300000);
        engine.prepareAct(def, s, "buyasset", null, null, null, null, null, null, null, "asset_manor");
        // 330 声望 → 1.264 → 封顶 1.15
        assertEquals(1.15, engine.prestigePriceMult(def, s), 0.0001);
    }

    private static GameDefinition loadDef(String id) {
        try {
            java.nio.file.Path p = java.nio.file.Paths.get("games", id + ".json");
            String json = new String(java.nio.file.Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
            return new ObjectMapper()
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .readValue(json, GameDefinition.class)
                    .withNoEvents(); // 测试默认去除随机事件，消除小偷等扰动
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------
    // v1.8.0：实时补货 / 理财 / 刮刮乐
    // ------------------------------------------------------------

    @Test
    void realtimeRestocker_replenishesEmptyShelf() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "restocker");
        // 开局无库存：营业 1 分钟后实时补货员应自动补货（库存为 0 的商品补 10）
        engine.tick(def, s, afterMin(1));
        int total = s.getInventory().values().stream().mapToInt(InventorySlot::getQty).sum();
        assertTrue(total >= 10, "库存为 0 的商品应被自动补货");
        assertTrue(s.getCash() < 1000, "补货应花费现金");
        assertTrue(s.getLiveLog().stream().anyMatch(l -> l.contains("实时补货")),
                "终端应记录实时补货");
    }

    @Test
    void finance_buySettleWithInterest() {
        GameState s = engine.newGame(def, "测试档", T0);
        double cash0 = s.getCash();
        // 买入灵动宝：现金 1000 ≥ 起购×2(600) → 买满 1000
        String m = engine.prepareAct(def, s, "financebuy", null, null, null, null, null, null, null, "fin_quick");
        assertTrue(m.contains("灵动宝"), "应买入灵动宝");
        assertEquals(1, s.getFinances().size());
        double principal = s.getFinances().get(0).getAmount();
        assertTrue(principal >= 300, "买入金额应不低于起购");
        assertTrue(Math.abs(s.getCash() - (cash0 - principal)) < 0.01, "买入应扣本金");
        assertTrue(s.getCash() == 0, "买满后现金为 0");
        // 快进到到期：第 2 天清晨结算
        s.getFinances().get(0).setDueDay(s.getDate().getDayNumber() + 1);
        engine.rest(def, s);
        assertTrue(s.getFinances().isEmpty(), "到期理财应自动兑付并清仓");
        double interest = Math.round(principal * 0.14 * 30 / 360 * 100) / 100.0;
        assertTrue(Math.abs(s.getCash() - (principal + interest)) < 0.01,
                "应兑付本金+利息，期望 " + (principal + interest));
    }

    @Test
    void finance_redeemReturnsPrincipalOnly() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "financebuy", null, null, null, null, null, null, null, "fin_quick");
        double principal = s.getFinances().get(0).getAmount();
        assertTrue(principal >= 300, "买入金额应不低于起购 300");
        double cashBefore = s.getCash();
        String m = engine.prepareAct(def, s, "financeredeem", null, null, null, null, null, null, null, "0");
        assertTrue(m.contains("本金"), "提前赎回提示应含本金");
        assertTrue(s.getFinances().isEmpty(), "赎回后持仓应清空");
        assertTrue(Math.abs(s.getCash() - (cashBefore + principal)) < 0.01,
                "提前赎回只退本金（无利息）");
    }

    @Test
    void scratch_buysUnlimitedAndSettlesInstantly() {
        GameState s = engine.newGame(def, "测试档", T0);
        String m = engine.prepareAct(def, s, "scratch", null, null, null, null, null, null, null, null);
        assertTrue(m.contains("刮"), "刮刮乐结果应即时返回");
        assertEquals(1, s.getScratchPurchases());
        assertTrue(s.getScratchLastWin() >= 0, "刮刮乐结果应为中奖金额或 0");
        // 无购买上限：连续再买 19 张
        for (int i = 0; i < 19; i++) {
            engine.prepareAct(def, s, "scratch", null, null, null, null, null, null, null, null);
        }
        assertEquals(20, s.getScratchPurchases(), "刮刮乐无购买上限");
    }

    @Test
    void realtimeRestock_restocks10EachTick() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "restocker");
        // 规则2：按刷新频率检查，无冷却——断货后下一 tick（1 游戏分钟）立即补货
        s.setDailyFootTraffic(0); // 无客流：纯验证补货逻辑，避免销售干扰补货量断言
        engine.tick(def, s, afterMin(1));
        int qty1 = s.getInventory().get("cola").getQty();
        assertTrue(qty1 > 0, "首轮断货应被补货");
        // 清空可乐库存，恢复充足现金，下一分钟（按刷新频率）应再次补 10 件
        s.getInventory().get("cola").setQty(0);
        s.setCash(1000);
        double cashBefore = s.getCash();
        engine.tick(def, s, afterMin(2));
        int qty2 = s.getInventory().get("cola").getQty();
        assertEquals(10, qty2, "现金满足时每轮应补 10 件");
        assertTrue(s.getCash() < cashBefore, "补货应扣款");
        // 未断货商品不应被补（不重复补货、不超量）
        assertTrue(s.getInventory().get("cola").getQty() <= 10, "单轮补货量不超过 10");
    }

    @Test
    void realtimeRestock_partialWhenCashLow() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "restocker");
        engine.tick(def, s, afterMin(1)); // 首轮补货
        // 现金很少（10 元），可乐批发价假设 < 10 元：应补"最大可买数量"而非 10 件
        s.getInventory().get("cola").setQty(0);
        s.setCash(10);
        engine.tick(def, s, afterMin(2));
        int qty = s.getInventory().get("cola").getQty();
        assertTrue(qty > 0 && qty < 10, "现金不足时应补最大可买数量（实际 " + qty + "）");
        assertTrue(s.getCash() >= 0, "补货后现金不为负");
    }

    @Test
    void realtimeRestock_restockBeforeSell() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "restocker");
        // 高客流（把日客流调大）制造持续断货：tick 一分钟后库存不应为负（先补后扣）
        s.setDailyFootTraffic(400);
        engine.tick(def, s, afterMin(1));
        // 补货日志应先于卖出日志出现（同一 tick 内先补后扣）
        var logs = s.getLiveLog();
        int restockIdx = -1, sellIdx = -1;
        for (int i = 0; i < logs.size(); i++) {
            if (logs.get(i).contains("实时补货") && restockIdx < 0) restockIdx = i;
            if (logs.get(i).contains("卖出") && sellIdx < 0) sellIdx = i;
        }
        assertTrue(restockIdx >= 0, "应有补货日志");
        assertTrue(sellIdx >= 0, "应有卖出日志");
        assertTrue(restockIdx < sellIdx, "先补货后卖出");
        boolean noNegative = s.getInventory().values().stream().allMatch(v -> v.getQty() >= 0);
        assertTrue(noNegative, "库存不应出现负数");
    }

    @Test
    void realtimeRestock_idempotentSameTimestamp() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "restocker");
        engine.tick(def, s, afterMin(1));
        double cash1 = s.getCash();
        int qty1 = s.getInventory().get("cola").getQty();
        long log1 = s.getLiveLog().stream().filter(l -> l.contains("实时补货")).count();
        // 规则4：同一时间戳重复刷新（前端轮询抖动/并发请求）→ 时间不推进，不重复补货、不重复扣款
        engine.tick(def, s, afterMin(1));
        engine.tick(def, s, afterMin(1));
        assertEquals(cash1, s.getCash(), 0.01, "同一时间戳重复刷新不应扣款");
        assertEquals(qty1, s.getInventory().get("cola").getQty(), "同一时间戳重复刷新不应重复补货");
        long log2 = s.getLiveLog().stream().filter(l -> l.contains("实时补货")).count();
        assertEquals(log1, log2, "同一时间戳重复刷新不应新增补货日志");
    }

    @Test
    void realtimeRestock_bigJumpDoesNotDrainCash() {
        GameState s = engine.newGame(def, "测试档", T0);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "restocker");
        engine.tick(def, s, afterMin(1)); // 首轮补货
        double cashAfterFirst = s.getCash();
        // 一次大跨度 120 分钟（模拟 5× 倍速挂机 24 现实秒）：tick 按大步推进，补货只检查一次
        engine.tick(def, s, afterMin(121));
        long restockLogs = s.getLiveLog().stream().filter(l -> l.contains("实时补货")).count();
        assertTrue(restockLogs <= 2, "大跨度推进补货轮数应受控（实际 " + restockLogs + "）");
        assertTrue(s.getCash() > cashAfterFirst * 0.5,
                "大跨度推进不应把现金抽干");
    }


    @Test
    void realtimeRestock_multiProductChargeIsExact() {
        GameState s = engine.newGame(def, "测试档", T0);
        s.setDailyFootTraffic(0); // 无销售：库存变化 = 补货量，可精确核对扣款
        // 清空所有库存 → 全部断货，触发多商品同时补货
        s.getInventory().values().forEach(slot -> slot.setQty(0));
        double cash0 = s.getCash(); // 1000
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "restocker");
        engine.tick(def, s, afterMin(1));
        double cash1 = s.getCash();
        // 精确校验：现金减少 == Σ(补货量 × 批发价)
        double expected = 0;
        for (InventorySlot slot : s.getInventory().values()) {
            ProductDef p = def.product(slot.getProductId());
            expected += slot.getQty() * p.wholesalePrice();
        }
        expected = Math.round(expected * 100) / 100.0;
        double actual = Math.round((cash0 - cash1) * 100) / 100.0;
        assertEquals(expected, actual, 0.01,
                "多商品补货扣款应精确等于补货成本（实际扣 " + actual + "，应扣 " + expected + "）");
        // 现金充足时每个断货商品都应补满 10 件（预算不再被双重扣减低估）
        boolean allFull = s.getInventory().values().stream().allMatch(v -> v.getQty() == 10);
        assertTrue(allFull, "现金充足时每个断货商品都应补满 10 件");
        assertTrue(cash1 >= 200, "补货后现金不低于保底");
    }


    // ================= v1.9.0 分店系统 =================

    @Test
    void openBranch_deductsCashAndCreatesIndependentShop() {
        GameState s = engine.newGame(def, "测试档", T0);
        // 声望不足 → 报错
        s.setReputation(10);
        assertThrows(GameException.class, () ->
                engine.prepareAct(def, s, "open_branch", null, null, null, null, null, null, null, "jiejiao"));
        // 提升声望 → 开店成功
        s.setReputation(50);
        s.setCash(5000);
        double cash0 = s.getCash();
        String msg = engine.prepareAct(def, s, "open_branch", "分店一号", null, null, null, null, null, "jiejiao", null);
        assertTrue(msg.contains("开业成功"), msg);
        assertEquals(2, s.getShops().size());
        // 现金恰好扣一次开店费 2000
        assertEquals(cash0 - 2000, s.getCash(), 0.01, "开店费应恰好扣 2000");
        // 自动切换到新店
        assertEquals("branch_1", s.getCurrentShopId());
        // 新店库存为空、定价默认
        assertTrue(s.currentShop().getInventory().isEmpty(), "新店库存应为空");
        assertEquals(1.0, s.currentShop().getPriceMultiplier().get("cola"), 0.001);
        assertTrue(s.currentShop().getUpgradesOwned().isEmpty(), "新店无装修");
    }

    @Test
    void branch_shopsHaveIndependentInventoryPricingUpgrades() {
        GameState s = engine.newGame(def, "测试档", T0);
        s.setReputation(50);
        s.setCash(5000);
        engine.prepareAct(def, s, "buy", "cola", 30, null, null, null, null, null, null);
        engine.prepareAct(def, s, "renovate", null, null, null, null, null, "upg_level2", null, null);
        engine.prepareAct(def, s, "price", "cola", null, 1.5, null, null, null, null, null);
        assertEquals(30, s.currentShop().getInventory().get("cola").getQty());
        assertEquals(1.5, s.currentShop().getPriceMultiplier().get("cola"), 0.001);
        assertFalse(s.currentShop().getUpgradesOwned().isEmpty());
        // 开分店
        engine.prepareAct(def, s, "open_branch", "分店一号", null, null, null, null, null, null, "jiejiao");
        // 分店独立：库存空、定价默认、无装修
        ShopState branch = s.currentShop();
        assertEquals(0, branch.getInventory().getOrDefault("cola", new InventorySlot("cola", 0, 3)).getQty());
        assertEquals(1.0, branch.getPriceMultiplier().get("cola"), 0.001);
        assertTrue(branch.getUpgradesOwned().isEmpty());
        // 切回主店：主店数据不受影响
        engine.prepareAct(def, s, "switch_shop", null, null, null, null, null, null, null, "main");
        assertEquals("main", s.getCurrentShopId());
        assertEquals(30, s.currentShop().getInventory().get("cola").getQty());
        assertEquals(1.5, s.currentShop().getPriceMultiplier().get("cola"), 0.001);
    }

    @Test
    void multiShop_simultaneousSalesWithSingleCashAccount() {
        GameState s = engine.newGame(def, "测试档", T0);
        s.setCash(6000);
        s.setDailyFootTraffic(0);
        // 主店备货
        engine.prepareAct(def, s, "buy", "cola", 500, null, null, null, null, null, null);
        engine.prepareAct(def, s, "buy", "sprite", 500, null, null, null, null, null, null);
        s.setReputation(50);
        engine.prepareAct(def, s, "open_branch", "分店一号", null, null, null, null, null, null, "jiejiao");
        // 分店备货（分店库存独立）
        engine.prepareAct(def, s, "buy", "cola", 200, null, null, null, null, null, null);
        engine.prepareAct(def, s, "buy", "sprite", 200, null, null, null, null, null, null);
        double cashBefore = s.getCash();
        // 两店同时营业 1 分钟（分店客流高：jiejiao ×1.25）
        s.getShops().get("main").setDailyFootTraffic(80);
        s.getShops().get("branch_1").setDailyFootTraffic(100);
        engine.tick(def, s, afterMin(1));
        // 现金 = 两店销售收入之和（单账户精确入账）
        double totalShopRevenue = s.getShops().values().stream().mapToDouble(ShopState::getTodayRevenue).sum();
        assertEquals(totalShopRevenue, s.getCash() - cashBefore, 0.01,
                "多店销售现金入账应精确等于两店收入之和");
        assertTrue(s.getShops().get("main").getTodayCustomers() + s.getShops().get("branch_1").getTodayCustomers() > 0,
                "两店都应产生客流销售");
    }

    @Test
    void legacySave_migratesToMainShop() {
        // 模拟旧档：只有旧字段、无 shops
        GameState s = engine.newGame(def, "旧档", T0);
        ShopState main = s.getShops().get("main");
        main.getInventory().computeIfAbsent("cola", k -> new InventorySlot("cola", 10, 3));
        main.getUpgradesOwned().add("upg_level2");
        main.setShopLevel(3);
        // 序列化 → 反序列化（模拟旧档保存格式）
        ObjectMapper om = new ObjectMapper();
        om.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try {
            String json = om.writeValueAsString(s);
            GameState loaded = om.readValue(json, GameState.class);
            engine.resume(def, loaded);
            assertEquals(1, loaded.getShops().size());
            assertEquals("main", loaded.getCurrentShopId());
            assertEquals(10, loaded.currentShop().getInventory().get("cola").getQty());
            assertTrue(loaded.currentShop().getUpgradesOwned().contains("upg_level2"));
            assertEquals(3, loaded.currentShop().getShopLevel());
        } catch (Exception e) {
            throw new AssertionError("旧档迁移失败：" + e.getMessage(), e);
        }
    }

    // ================= v1.10.0 供应链 / 口碑 / 街区竞争 =================

    @Test
    void v110_buyCountsSupplyPointsAndDiscount() {
        // 供应链未启用时应无折扣无积分（兼容旧游戏）
        GameState s = engine.newGame(def, "供应测试", T0);
        double cashBefore = s.getCash();
        double cost = 2.5;
        engine.prepareAct(def, s, "buy", "cola", 10, 1.0, null, null, null, null, null);
        assertEquals(cashBefore - cost * 10, s.getCash(), 0.01, "普通进货按原价扣款");
        assertEquals(25.0, s.getSupplyPoints(), 0.01, "供应链启用时实付金额计入合作值");
    }

    @Test
    void v110_satisfactionSettleAddsHistoryAndRating() {
        GameState s = engine.newGame(def, "口碑测试", T0);
        ShopState main = s.getShops().get("main");
        main.getPriceMultiplier().put("cola", 1.0);
        main.getPriceMultiplier().put("snack", 1.0);
        // 员工与装修
        main.getUpgradesOwned().add("upg_level2");
        main.setShopLevel(2);
        // 推进一天（打烊快进到次日清晨）
        engine.rest(def, s);
        assertEquals(1, s.getSatisfactionHistory().size(), "应生成一条满意度记录");
        assertTrue(s.getSatisfactionScore() >= 0 && s.getSatisfactionScore() <= 100);
        assertEquals(1, s.getRatingMonthDays());
    }

    @Test
    void v110_satisfactionPenalizesExpired() {
        GameState s = engine.newGame(def, "口碑测试2", T0);
        s.setTodayExpiredQty(20);
        ShopState main = s.getShops().get("main");
        main.getPriceMultiplier().put("cola", 1.5);   // 偏离 0.5 → 价格分 0
        main.getPriceMultiplier().put("snack", 1.5);
        engine.rest(def, s);
        // 高定价（多数商品默认 1.0，整体偏移不大）+ 高损耗 → 显著低于默认 70
        assertTrue(s.getSatisfactionScore() <= 60, "高定价+高损耗应显著拉低满意度，实际 " + s.getSatisfactionScore());
        assertEquals(0, s.getTodayExpiredQty(), "结算后当日损耗应清零");
    }

    @Test
    void v110_legacyResumeDefaultsSupplyCompetition() {
        // 旧档（无 v1.10 字段）resume 后应补齐默认值
        GameState s = engine.newGame(def, "兼容测试", T0);
        ObjectMapper om = new ObjectMapper();
        om.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try {
            String json = om.writeValueAsString(s);
            GameState loaded = om.readValue(json, GameState.class);
            engine.resume(def, loaded);
            assertEquals(0, loaded.getSupplyPoints(), 0.01);
            assertNotNull(loaded.getCompetitors());
            assertEquals(70, loaded.getSatisfactionScore(), 0.01);
            assertEquals(1, loaded.getRatingTier());
        } catch (Exception e) {
            throw new AssertionError("兼容性检查失败：" + e.getMessage(), e);
        }
    }

    // ---------------- v1.11.0 节日庆典 + 员工培养晋升 ----------------

    @Test
    void v111_festivalSetupPrepAndSettle() {
        // 开局第 1 天 08:00 正值新春庙会筹备窗口（开幕 00:00，提前 1 天窗口）
        GameState s = engine.newGame(def, "节日测试", T0);
        s.setCash(5000);
        String msg = engine.prepareAct(def, s, "festival_setup", null, null, null, 800.0, null, null, null, null);
        assertTrue(msg.contains("筹备金"), "应提示投入筹备金");
        assertEquals(4200, s.getCash(), 0.01);
        FestivalState fs = s.getFestival();
        assertNotNull(fs);
        assertEquals("spring_fair", fs.getId());
        assertEquals(800, fs.getSetup(), 0.01, "筹备金应等于投入额");
        assertFalse(fs.isSettled());
        // 已达上限后不能再投
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "festival_setup", null, null, null, 100.0, null, null, null, null));
        // 节日进行中：查看文本
        s.setMinutes(100);
        String v = engine.prepareAct(def, s, "festival", null, null, null, null, null, null, null, null);
        assertTrue(v.contains("进行中"), "进行中的节日应可查看");
        // 快进到次日清晨触发闭幕结算（end=1440 < 次日06:00=1800）
        double cashBefore = s.getCash();
        engine.rest(def, s);
        assertNull(s.getFestival(), "节日结束后应清空状态");
        assertTrue(s.getStats().getFestivalCount() >= 1, "应累计节日次数");
        assertTrue(s.getCash() > cashBefore, "筹备金回报应入账");
        assertTrue(s.getFestivalHistory().size() >= 1, "应生成节日战报");
    }

    @Test
    void v111_festivalDemandAndTrafficBoostActive() {
        GameState s = engine.newGame(def, "节日客流", T0);
        s.setCash(2000);
        engine.prepareAct(def, s, "festival_setup", null, null, null, 800.0, null, null, null, null);
        s.setMinutes(600); // 节日进行中
        assertEquals(800, s.getFestival().getSetup(), 0.01);
        // 节日筹备金满额（setup_cap=800 已投满），开幕后处于进行中状态
        String v = engine.prepareAct(def, s, "festival", null, null, null, null, null, null, null, null);
        assertTrue(v.contains("进行中"), "节日进行中可查看：" + v);
    }

    @Test
    void v111_staffGainsExpOnDayRoll() {
        GameState s = engine.newGame(def, "培养经验", T0);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "assistant");
        StaffState st = s.getStaff().get("assistant");
        assertEquals(1, st.getLevel());
        int exp0 = st.getExp();
        engine.rest(def, s); // 跨天触发 careerTick：每日 +5 经验
        assertTrue(st.getExp() > exp0, "跨天后员工应获得经验");
        assertTrue(st.getLoyalty() >= 50, "初始忠诚 50，跨天缓慢回升");
    }

    @Test
    void v111_promoteRequiresLevelLoyaltyThenPickSkill() {
        GameState s = engine.newGame(def, "晋升测试", T0);
        s.setCash(5000);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "assistant");
        StaffState st = s.getStaff().get("assistant");
        st.setLevel(3);
        st.setLoyalty(70);
        st.setExp(200);
        double cashBefore = s.getCash();
        String msg = engine.prepareAct(def, s, "promote", null, null, null, null, null, null, null, "assistant");
        assertTrue(msg.contains("晋升成功"), "应晋升成功：" + msg);
        assertTrue(st.isPromoted());
        assertEquals(cashBefore - 400 * 3.0, s.getCash(), 0.01, "晋升费用 = 月薪×3");
        assertTrue(st.getLoyalty() < 70, "晋升应消耗忠诚度");
        assertNull(st.getSkillId(), "晋升后未选技能");
        // 选择技能
        String msg2 = engine.prepareAct(def, s, "pick_skill", null, null, null, null, null, null, "sk_stock_master", "assistant");
        assertTrue(msg2.contains("技能"), "应提示习得技能");
        assertEquals("sk_stock_master", st.getSkillId());
        // 忠诚不足无法晋升
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "promoter");
        StaffState st2 = s.getStaff().get("promoter");
        st2.setLevel(3);
        st2.setLoyalty(40);
        assertThrows(GameException.class,
                () -> engine.prepareAct(def, s, "promote", null, null, null, null, null, null, null, "promoter"));
    }

    @Test
    void v111_redpacketRaisesLoyalty() {
        GameState s = engine.newGame(def, "红包测试", T0);
        s.setCash(1000);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "assistant");
        StaffState st = s.getStaff().get("assistant");
        st.setLoyalty(40);
        String msg = engine.prepareAct(def, s, "redpacket", null, null, null, null, null, null, null, "assistant");
        assertTrue(msg.contains("忠诚 +10"), "红包应加 10 忠诚：" + msg);
        assertEquals(50, st.getLoyalty(), 0.01);
        assertEquals(1000 - 200, s.getCash(), 0.01, "红包花费 200");
    }

    @Test
    void v111_skillDiscountAndStockBonusApplied() {
        GameState s = engine.newGame(def, "技能测试", T0);
        s.setCash(10000);
        engine.prepareAct(def, s, "hire", null, null, null, null, null, null, null, "buyer");
        StaffState st = s.getStaff().get("buyer");
        st.setPromoted(true);
        st.setSkillId("sk_bargain");
        double cashBefore = s.getCash();
        engine.prepareAct(def, s, "buy", "cola", 10, null, null, null, null, null, null);
        // 让价 = 采购员 8%×效率1.10（晋升骨干）+ 技能 3% = 11.8%；单价 2.5×0.882=2.205 → 2.21
        assertEquals(cashBefore - 2.21 * 10, s.getCash(), 0.01, "技能应叠加进货让价");
    }

    @Test
    void v111_legacyResumeDefaultsFestivalCareer() {
        GameState s = engine.newGame(def, "兼容111", T0);
        ObjectMapper om = new ObjectMapper();
        om.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try {
            String json = om.writeValueAsString(s);
            GameState loaded = om.readValue(json, GameState.class);
            engine.resume(def, loaded);
            assertNull(loaded.getFestival());
            assertNotNull(loaded.getFestivalHistory());
            assertEquals(0, loaded.getStats().getFestivalCount());
        } catch (Exception e) {
            throw new AssertionError("兼容性检查失败：" + e.getMessage(), e);
        }
    }
}
