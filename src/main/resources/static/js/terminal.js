/* ============================================================
 * TermSim Engine —— 终端交互逻辑（实时时间模型 v2）
 * 时间：现实 1 秒 = 游戏 1 分钟；页面每秒轮询一次推进游戏时间。
 * 依赖：jQuery（webjar 本地提供）
 * ============================================================ */
(function ($) {
    'use strict';

    // ---------------- 全局状态 ----------------
    var DEF = INIT_DEF || {};
    var STATE = INIT_STATE || {};
    var CURRENCY = (DEF.meta || {}).currency || '元';
    var cmdHistory = [];
    var cmdIndex = -1;
    var booting = true;
    var pendingAct = 0;          // 操作请求在途计数，避免轮询用旧状态覆盖

    // 分页配置：key -> { page, size }
    var PAGER = {
        shelf:   { p: 1, s: 12 },
        price:   { p: 1, s: 12 },
        upgrade: { p: 1, s: 4 },
        loan:    { p: 1, s: 4 },
        npc:     { p: 1, s: 6 },
        social:  { p: 1, s: 4 },
        tradeWs: { p: 1, s: 10 },
        staff:   { p: 1, s: 4 }
    };

    // ---------------- 工具 ----------------
    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    function fmt(n) {
        return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    function fmt1(n) {
        return Number(n || 0).toLocaleString('zh-CN', { maximumFractionDigits: 1 });
    }
    function pad2(n) { return String(n).padStart(2, '0'); }
    function fmtClock(min) {
        min = Math.max(0, Math.round(min || 0));
        return pad2(Math.floor(min / 60)) + ':' + pad2(min % 60);
    }
    // 游戏时间：minutes 自第 1 天 00:00 起累计，开局为第 1 天 08:00（480 分钟）
    function gameDay() {
        return Math.floor((STATE.minutes || 0) / 1440) + 1;
    }
    function gameClock() {
        var mod = ((STATE.minutes || 0) % 1440 + 1440) % 1440;
        return pad2(Math.floor(mod / 60)) + ':' + pad2(mod % 60);
    }
    function seasonName(id) {
        var s = (DEF.seasons || []).filter(function (x) { return x.id === id; })[0];
        return s ? (s.name + ' ' + (s.icon || '')) : id;
    }
    function weatherInfo() {
        var w = (DEF.weathers || []).filter(function (x) { return x.id === STATE.weather_id; })[0];
        return w || { name: '-', icon: '' };
    }
    function productById(id) {
        return (DEF.products || []).filter(function (p) { return p.id === id; })[0];
    }
    function upgradeById(id) {
        return (DEF.upgrades || []).filter(function (u) { return u.id === id; })[0];
    }
    function loanById(id) {
        return (DEF.loans || []).filter(function (l) { return l.id === id; })[0];
    }
    function owned(id) { return (STATE.upgrades_owned || []).indexOf(id) >= 0; }
    function unlocked(p) {
        return !p.unlock_upgrade_id || owned(p.unlock_upgrade_id);
    }
    function netWorth() {
        var inv = 0;
        Object.keys(STATE.inventory || {}).forEach(function (pid) {
            var p = productById(pid);
            if (p) inv += p.wholesale_price * STATE.inventory[pid].qty;
        });
        return (STATE.cash || 0) + inv - (STATE.debt || 0);
    }
    function buffText() {
        var labels = {
            FOOT_TRAFFIC_MOD: '客流量×',
            FOOT_TRAFFIC_FLAT: '客流+',
            SALES_MOD: '营业额×',
            RESTOCK_DISCOUNT: '进货折扣',
            REPUTATION_DAY: '声誉/日'
        };
        return (STATE.buffs || []).map(function (b) {
            var v;
            if (b.type === 'RESTOCK_DISCOUNT') {
                v = Math.round(b.value * 10) + '折';
            } else if (b.type === 'FOOT_TRAFFIC_MOD' || b.type === 'SALES_MOD') {
                v = '×' + b.value;
            } else {
                v = (b.value > 0 ? '+' : '') + b.value;
            }
            return labels[b.type] + ' ' + v + '（剩 ' + b.days_remaining + ' 天）';
        });
    }
    function favorTier(f) {
        if (f >= 80) return { name: '忠实', cls: 'c-green c-b' };
        if (f >= 60) return { name: '熟客', cls: 'c-green' };
        if (f >= 35) return { name: '一般', cls: 'c-amber' };
        return { name: '冷淡', cls: 'c-dim' };
    }

    // ---------------- 分页 ----------------
    function pageSlice(key, items) {
        var cfg = PAGER[key];
        var total = Math.max(1, Math.ceil(items.length / cfg.s));
        if (cfg.p > total) cfg.p = total;
        if (cfg.p < 1) cfg.p = 1;
        var start = (cfg.p - 1) * cfg.s;
        return { rows: items.slice(start, start + cfg.s), page: cfg.p, total: total };
    }
    function pagerHtml(key, total) {
        var cfg = PAGER[key];
        return '<div class="pager">' +
            '<button class="pager-btn" data-key="' + key + '" data-dir="-1"' + (cfg.p <= 1 ? ' disabled' : '') + '>◀ 上一页</button>' +
            '<span class="pager-info">' + cfg.p + ' / ' + total + '</span>' +
            '<button class="pager-btn" data-key="' + key + '" data-dir="1"' + (cfg.p >= total ? ' disabled' : '') + '>下一页 ▶</button>' +
            '</div>';
    }
    $(document).on('click', '.pager-btn', function () {
        var k = $(this).data('key');
        var dir = parseInt($(this).data('dir'), 10);
        if (!PAGER[k] || $(this).prop('disabled')) return;
        PAGER[k].p += dir;
        renderAll();
    });

    // ---------------- 终端日志 ----------------
    function logLine(html) {
        var $c = $('#console');
        $c.append($('<div class="log-line">' + html + '</div>'));
        $c.scrollTop($c[0].scrollHeight);
    }
    function logText(text, cls) {
        logLine('<span class="' + (cls || 'c-dim') + '">' + esc(text) + '</span>');
    }
    function logAction(msg) {
        if (msg) logText('> ' + msg, 'c-green');
    }
    function logError(msg) {
        logText('✘ ' + msg, 'c-red');
    }
    function logSep(text) {
        var line = '─'.repeat(28) + ' ' + text + ' ' + '─'.repeat(28);
        $('#console').append($('<div class="log-sep">' + esc(line) + '</div>'));
    }
    function typeLine(text, cls, speed, cb) {
        var $c = $('#console');
        var $line = $('<div class="log-line"></div>');
        $c.append($line);
        var i = 0;
        (function tick() {
            if (i < text.length) {
                $line.html(esc(text.slice(0, i + 1)) + '<span class="cursor-blink">▊</span>');
                i++;
                setTimeout(tick, speed || 14);
            } else {
                $line.html(esc(text));
                $c.scrollTop($c[0].scrollHeight);
                if (cb) cb();
            }
        })();
    }

    // ---------------- 渲染 ----------------
    function renderAll() {
        // 促销按钮费用按游戏文件配置动态显示
        if (DEF.gameplay && DEF.gameplay.promo) {
            $('#btnPromo').text('📣 限时促销（' + DEF.gameplay.promo.cost + ' 元）');
        }
        renderStatusbar();
        renderShopSwitcher();
        renderShopStatus();
        renderNpcPanel();
        renderBuffPanel();
        renderShelf();
        renderPrice();
        renderUpgrades();
        renderLoans();
        renderReport();
        renderDebtPanel();
        renderAchievements();
        renderTrade();
        renderSupply();
        renderCompete();
        renderStaff();
        renderSocial();
        renderQuest();
        renderBank();
        renderMap();
        renderWardrobe();
        renderGameOver();
    }

    function renderStatusbar() {
        var w = weatherInfo();
        var paused = !!STATE.paused;
        var html =
            '<span class="stat-item"><span class="label">现金</span> <span class="value">' + fmt(STATE.cash) + ' ' + CURRENCY + '</span></span>' +
            '<span class="stat-item"><span class="label">负债</span> <span class="value ' + ((STATE.debt || 0) > 0 ? 'red' : '') + '">' + fmt(STATE.debt) + '</span></span>' +
            '<span class="stat-item"><span class="label">净资产</span> <span class="value cyan">' + fmt(netWorth()) + '</span></span>' +
            '<span class="stat-item"><span class="label">声誉</span> <span class="value">' + fmt1(STATE.reputation) + '/' + DEF.meta.max_reputation + '</span></span>' +
            '<span class="stat-item"><span class="label">游戏时间</span> <span class="value amber">第' + gameDay() + '天 ' + gameClock() + '</span>' +
            (paused ? ' <span class="value red">⏸</span>' : ' <span class="value green">▶</span>') + '</span>' +
            '<span class="stat-item"><span class="label">天气</span> <span class="value cyan">' + w.icon + ' ' + w.name + '</span></span>' +
            '<span class="stat-item"><span class="label">店铺</span> <span class="value">Lv.' + STATE.shop_level + '</span></span>' +
            '<span class="stat-item"><span class="label">口碑</span> <span class="value ' + ratingCls() + '">' + ratingName() + '</span></span>' +
            festivalBadgeHtml() +
            '<span style="flex:1;"></span>' +
            '<button class="amber" id="btnRest">◼ 打烊快进</button>';
        $('#statusbar').html(html);
        // 事件委托：状态栏每秒重建，直接绑定会因元素替换丢失点击（改为容器委托）
    }

    // ---------------- v1.9.0 分店系统：店铺切换条 / 开分店 ----------------
    function shopList() {
        var shops = STATE.shops || {};
        return Object.keys(shops).map(function (id) { return shops[id]; });
    }
    function locNameById(id) {
        var map = (DEF.gameplay && DEF.gameplay.map) || {};
        var locs = map.locations || [];
        for (var i = 0; i < locs.length; i++) {
            if (locs[i].id === id) return locs[i];
        }
        return null;
    }
    function renderShopSwitcher() {
        var shops = STATE.shops || {};
        var ids = Object.keys(shops);
        if (!ids.length) { $('#shopSwitcher').html(''); return; }
        var cur = STATE.current_shop_id || 'main';
        var html = '<span class="sw-label">🏬 店铺</span>';
        ids.forEach(function (id) {
            var sh = shops[id] || {};
            var loc = locNameById(sh.location_id);
            var rev = sh.today_revenue || 0;
            html += '<button class="shop-chip' + (id === cur ? ' active' : '') + '" data-shop="' + id + '" title="切换到这家店经营">' +
                (sh.icon || '🏪') + ' ' + esc(sh.name || id) +
                '<span class="chip-sub">' + (loc ? loc.icon + ' ' + loc.name : '新址') + ' · 今日 ' + fmt(rev) + '</span></button>';
        });
        html += '<button class="shop-chip add" id="btnOpenBranch" title="开设分店（独立库存/定价/装修，金钱共用）">＋ 开分店</button>';
        $('#shopSwitcher').html(html);
    }

    function openBranchModal() {
        var gp = (DEF.gameplay || {}).branching;
        if (!gp || !gp.enabled) { logError('当前游戏未开放开分店功能。'); return; }
        var map = (DEF.gameplay || {}).map || {};
        var locs = map.locations || [];
        var taken = {};
        Object.keys(STATE.shops || {}).forEach(function (id) {
            var sh = STATE.shops[id];
            if (sh.location_id) taken[sh.location_id] = true;
        });
        var opts = locs.filter(function (l) { return !taken[l.id]; })
            .map(function (l) {
                return '<option value="' + l.id + '">' + (l.icon || '') + ' ' + l.name +
                    '（客流×' + l.traffic_bonus + '，房价 ' + fmt(l.price || 0) + '）</option>';
            }).join('');
        $('#branchLocation').html(opts || '<option value="">（暂无可选新址）</option>');
        var n = Object.keys(STATE.shops || {}).length;
        var cost = (gp.base_open_cost || 2000) * Math.pow(gp.cost_growth || 1.6, Math.max(0, n - 1));
        $('#branchName').val('');
        $('#branchPreview').html(
            '📋 当前经营 <b>' + n + '</b> 家店（上限 ' + (gp.max_shops || 1) + ' 家），本次开店费用：<b style="color:var(--amber);">' + fmt(cost) + ' ' + CURRENCY + '</b><br>' +
            '🏅 需要声望 ≥ ' + (gp.require_reputation || 0) + '（当前 ' + fmt1(STATE.reputation) + '）<br>' +
            '🏷 新店拥有<b>独立</b>库存 / 定价 / 装修 / 雇员；现金、存款、贷款、理财为<b>全局共用</b>账户。');
        $('#branchMask').fadeIn(120);
    }

    function doOpenBranch() {
        var name = $('#branchName').val().trim() || '';
        var loc = $('#branchLocation').val();
        if (!loc) { logError('请选择开店选址'); return; }
        doAct({ action: 'open_branch', product_id: name, npc_id: loc });
        $('#branchMask').fadeOut(120);
    }

    $(document).on('click', '#btnOpenBranch', openBranchModal);
    $(document).on('click', '#btnBranchOpen', doOpenBranch);
    $(document).on('click', '#btnBranchClose, #btnBranchCancel', function () { $('#branchMask').fadeOut(120); });
    $(document).on('click', '#branchMask', function (e) { if (e.target === this) $('#branchMask').fadeOut(120); });
    $(document).on('click', '.shop-chip[data-shop]', function () {
        var id = $(this).data('shop');
        if (id === (STATE.current_shop_id || 'main')) return;
        doAct({ action: 'switch_shop', npc_id: String(id) });
    });

    // 状态栏按钮委托（初始化时绑定一次，不受每秒重建影响）
    $(document).on('click', '#btnRest', function () {
        doAct({ action: 'rest' });
    });
    $(document).on('click', '#btnBankDeposit', function () {
        var amt = parseFloat($('#bankDepositInput').val());
        if (!amt || amt <= 0) { logError('请输入有效的存款金额'); return; }
        doAct({ action: 'deposit', amount: amt });
    });
    $(document).on('click', '#btnBankWithdraw', function () {
        var amt = parseFloat($('#bankWithdrawInput').val());
        if (!amt || amt <= 0) { logError('请输入有效的取款金额'); return; }
        doAct({ action: 'withdraw', amount: amt });
    });
    $(document).on('click', '#btnLotteryBuy', function () {
        doAct({ action: 'lottery' });
    });

    // ---------------- v1.8.0 顶栏：设置 / 保存 / 返回 ----------------
    function saveGameNow(cb) {
        var parts = SAVE_ID.split('::');
        $.ajax({
            url: '/api/game/' + encodeURIComponent(parts[0]) + '/saves/' + encodeURIComponent(parts[1]) + '/save',
            method: 'POST',
            dataType: 'json'
        }).done(function (res) {
            if (res.ok) {
                if (res.state) { STATE = res.state; lastLiveLen = (STATE.live_log || []).length; renderAll(); }
                logAction(res.message || '💾 已存档');
                if (cb) cb();
            } else {
                logError(res.message || '存档失败');
            }
        }).fail(function (xhr) {
            logError('存档失败：' + (xhr.status || '') + '，请重试');
        });
    }
    function goHome() {
        window.location.href = '/';
    }
    function renderSettings() {
        var curSpeed = STATE.time_speed || 1.0;
        var paused = !!STATE.paused;
        var btns = [0.5, 1.0, 1.5, 2.0, 3.0, 5.0].map(function (sp) {
            var active = Math.abs(sp - curSpeed) < 0.001;
            return '<button class="btn-speed ' + (active ? 'primary' : '') + '" data-speed="' + sp + '">' + sp + '×</button>';
        }).join('');
        $('#setSpeedRow').html(btns);
        $('#btnSettingsPause').text(paused ? '▶ 继续时间' : '⏸ 暂停时间');
    }
    $(document).on('click', '#btnSettings', function () {
        renderSettings();
        $('#settingsMask').fadeIn(120);
    });
    $(document).on('click', '#btnSettingsClose', function () { $('#settingsMask').fadeOut(120); });
    $(document).on('click', '#settingsMask', function (e) {
        if (e.target === this) $('#settingsMask').fadeOut(120);
    });
    $(document).on('click', '#btnSettingsPause', function () {
        doAct({ action: STATE.paused ? 'resume' : 'pause' });
        setTimeout(function () { renderSettings(); }, 500);
    });
    $(document).on('click', '#btnSettingsSave', function () { saveGameNow(); });
    $(document).on('click', '#btnSettingsHome', goHome);
    $(document).on('click', '#btnSave', function () { saveGameNow(); });
    $(document).on('click', '#btnHome', goHome);
    // 速度按钮（设置弹层内，复用 .btn-speed 委托）
    $(document).on('click', '.btn-speed', function () {
        var sp = parseFloat($(this).data('speed'));
        doAct({ action: 'speed', multiplier: sp });
        setTimeout(function () { renderSettings(); }, 500);
    });

    // ---------------- v1.8.0 理财：买入 / 提前赎回 ----------------
    $(document).on('click', '[data-financebuy]', function () {
        doAct({ action: 'financebuy', staff_id: $(this).data('financebuy') });
    });
    $(document).on('click', '[data-financeredeem]', function () {
        doAct({ action: 'financeredeem', staff_id: String($(this).data('financeredeem')) });
    });

    // ---------------- v1.8.0 刮刮乐：即买即刮 ----------------
    $(document).on('click', '#btnScratchBuy', function () {
        doAct({ action: 'scratch' });
    });
    $(document).on('click', '#btnScratchClose, #btnScratchDone', function () { $('#scratchMask').fadeOut(120); });
    $(document).on('click', '#scratchMask', function (e) {
        if (e.target === this) $('#scratchMask').fadeOut(120);
    });
    $(document).on('click', '#btnScratchAgain', function () {
        doAct({ action: 'scratch' });
    });

    // v1.11.0 技能选择弹窗
    $(document).on('click', '#btnSkillClose', function () { $('#skillMask').fadeOut(120); });
    $(document).on('click', '#skillMask', function (e) {
        if (e.target === this) $('#skillMask').fadeOut(120);
    });

    // 刮卡交互：涂层遮罩 + 鼠标擦除，刮开 >40% 揭晓
    function openScratch(win) {
        $('#scratchMask').fadeIn(120);
        var won = typeof win === 'number' && win > 0;
        $('#scratchResult').text(won ? '🎉 恭喜中奖 +' + fmt(win) + ' ' + CURRENCY + '！' : '😅 谢谢参与，再接再厉');
        var cv = document.getElementById('scratchCanvas');
        if (!cv) return;
        var ctx = cv.getContext('2d');
        var W = cv.width, H = cv.height;
        ctx.globalCompositeOperation = 'source-over';
        // 涂层：银灰渐变 + 噪点 + 文字
        var g = ctx.createLinearGradient(0, 0, W, H);
        g.addColorStop(0, '#9aa3ad'); g.addColorStop(0.5, '#c2c9d1'); g.addColorStop(1, '#8b939c');
        ctx.fillStyle = g;
        ctx.fillRect(0, 0, W, H);
        for (var i = 0; i < 1400; i++) {
            ctx.fillStyle = 'rgba(255,255,255,' + (Math.random() * 0.18) + ')';
            ctx.fillRect(Math.random() * W, Math.random() * H, 2, 2);
        }
        ctx.fillStyle = '#5b646e';
        ctx.font = 'bold 22px monospace';
        ctx.textAlign = 'center';
        ctx.fillText('✦ 刮 开 此 处 ✦', W / 2, H / 2 + 8);
        var scratching = false;
        var revealed = false;
        function clearPx(e) {
            var rect = cv.getBoundingClientRect();
            var x = (e.clientX - rect.left) * (W / rect.width);
            var y = (e.clientY - rect.top) * (H / rect.height);
            ctx.globalCompositeOperation = 'destination-out';
            ctx.beginPath();
            ctx.arc(x, y, 26, 0, Math.PI * 2);
            ctx.fill();
        }
        function checkReveal() {
            if (revealed) return;
            var img = ctx.getImageData(0, 0, W, H).data;
            var cleared = 0;
            var step = 40;
            for (var y = 0; y < H; y += step) {
                for (var x = 0; x < W; x += step) {
                    if (img[(y * W + x) * 4 + 3] < 120) cleared++;
                }
            }
            var total = Math.ceil(H / step) * Math.ceil(W / step);
            if (cleared / total > 0.4) {
                revealed = true;
                ctx.globalCompositeOperation = 'destination-out';
                ctx.fillStyle = 'rgba(0,0,0,1)';
                ctx.fillRect(0, 0, W, H);
            }
        }
        $(cv).off('mousedown.scratch').on('mousedown.scratch', function (e) {
            scratching = true;
            clearPx(e);
        });
        $(document).off('mousemove.scratch').on('mousemove.scratch', function (e) {
            if (!scratching) return;
            clearPx(e);
            checkReveal();
        });
        $(document).off('mouseup.scratch').on('mouseup.scratch', function () {
            scratching = false;
            checkReveal();
        });
        cv.style.pointerEvents = 'auto';
    }

    function renderShopStatus() {
        var w = weatherInfo();
        var trafficMod = 1;
        var capBonus = 0;
        var autoRep = 0;
        (STATE.upgrades_owned || []).forEach(function (uid) {
            var u = upgradeById(uid);
            if (u) { trafficMod += u.foot_traffic_bonus; capBonus += u.max_customers_bonus; autoRep += u.auto_reputation_per_day; }
        });
        var repPct = Math.max(0, Math.min(100, (STATE.reputation / DEF.meta.max_reputation) * 100));
        var repCls = repPct >= 60 ? '' : (repPct >= 30 ? 'amber' : 'danger');
        var html =
            '<div class="kv"><span class="k">今日天气</span><span class="v">' + w.icon + ' ' + esc(w.name) + '</span></div>' +
            '<div class="kv"><span class="k">天气对客流</span><span class="v">×' + w.foot_traffic_modifier + '</span></div>' +
            '<div class="kv"><span class="k">声誉</span><span class="v green">' + fmt1(STATE.reputation) + ' / ' + DEF.meta.max_reputation + '</span></div>' +
            '<div class="meter ' + repCls + '"><div style="width:' + repPct + '%;"></div></div>' +
            '<div class="kv"><span class="k">今日客流基准</span><span class="v">' + fmt1(STATE.daily_foot_traffic) + ' 人/天</span></div>' +
            '<div class="kv"><span class="k">客流上限</span><span class="v">' + (25 + capBonus) + ' 人/天</span></div>' +
            '<div class="kv"><span class="k">装修加成</span><span class="v">×' + trafficMod.toFixed(2) + '</span></div>' +
            (autoRep > 0 ? '<div class="kv"><span class="k">自动声誉</span><span class="v green">+' + autoRep + '/天</span></div>' : '') +
            '<div class="kv"><span class="k">进货折扣</span><span class="v">' + discountText() + '</span></div>' +
            '<div class="kv"><span class="k">银行存款</span><span class="v amber">' + fmt(STATE.bank_balance || 0) + '</span></div>' +
            '<div class="kv"><span class="k">📍 当前地段</span><span class="v">' + currentLocName() + '</span></div>' +
            '<div class="kv"><span class="k">🧍 个人声望</span><span class="v amber">' + Math.floor(prestigeOf()) + '</span></div>';
        $('#shopStatus').html(html);
    }

    function currentLocName() {
        var map = (DEF.gameplay && DEF.gameplay.map);
        if (!map || !map.locations) return '-';
        for (var i = 0; i < map.locations.length; i++) {
            if (map.locations[i].id === STATE.current_location_id) {
                return (map.locations[i].icon || '🏪') + ' ' + map.locations[i].name;
            }
        }
        return '-';
    }

    function discountText() {
        var d = 1;
        (STATE.buffs || []).forEach(function (b) { if (b.type === 'RESTOCK_DISCOUNT') d *= b.value; });
        return d < 1 ? Math.round(d * 10) + ' 折' : '无';
    }

    // 最近一次打烊结算的各 NPC 好感变化（用于左栏持久显示 ▲▼，直到下次打烊）
    var lastNpcDelta = {};
    function renderNpcPanel() {
        var html = '';
        (DEF.npcs || []).forEach(function (npc) {
            var ns = STATE.npcs[npc.id] || { favorability: 30, visits: 0 };
            var cur = Math.round(ns.favorability);
            var pct = Math.max(0, Math.min(100, cur));
            var tier = favorTier(cur);
            var dInfo = lastNpcDelta[npc.id];
            var dMark = '';
            if (dInfo && dInfo.cur === cur && dInfo.delta !== 0) {
                var d = dInfo.delta;
                dMark = ' <span class="' + (d > 0 ? 'c-green' : 'c-red') + '" style="font-weight:600;">' +
                    (d > 0 ? '▲' : '▼') + Math.abs(d) + '</span>';
            }
            html +=
                '<div class="kv" style="align-items:center;">' +
                '<span class="k">' + npc.icon + ' ' + esc(npc.name) + '</span>' +
                '<span class="v ' + tier.cls + '">' + tier.name + ' ' + cur + dMark + '</span></div>' +
                '<div class="meter" style="height:6px;margin:2px 0 8px;"><div style="width:' + pct + '%;"></div></div>';
        });
        $('#npcPanel').html(html || '<span class="c-dim">暂无 NPC</span>');
    }

    function renderBuffPanel() {
        var lines = [];
        var now = STATE.minutes || 0;
        // 今日晨报
        if (STATE.daily_brief) {
            lines.push({ k: '📰', v: '今日晨报：' + STATE.daily_brief, cls: 'c-cyan' });
        }
        // 限时促销倒计时
        if (STATE.promo_until_min && STATE.promo_until_min > now) {
            var remain = STATE.promo_until_min - now;
            lines.push({ k: '📣', v: '限时促销：客流×' + STATE.promo_mult + '（剩 ' + remain + ' 分钟）', cls: 'c-green' });
        }
        // 爆款热卖倒计时
        if (STATE.hot_product_id && STATE.hot_until_min && STATE.hot_until_min > now) {
            var hp = productById(STATE.hot_product_id);
            lines.push({ k: '🔥', v: '爆款热卖：' + (hp ? hp.icon + ' ' + hp.name : STATE.hot_product_id)
                + ' 需求×2.5（剩 ' + (STATE.hot_until_min - now) + ' 分钟）', cls: 'c-amber' });
        }
        // 常规增益 buff
        buffText().forEach(function (b) {
            lines.push({ k: '◆', v: b, cls: 'c-amber' });
        });
        $('#buffPanel').html(lines.length
            ? lines.map(function (x) {
                return '<div class="kv"><span class="k ' + x.cls + '">' + x.k + '</span><span class="v ' + x.cls + '">' + esc(x.v) + '</span></div>';
            }).join('')
            : '<span class="c-dim">今日无增益效果</span>');
    }

    function renderAchievements() {
        var defs = DEF.achievements || [];
        if (!defs.length) { $('#achieveCount').text('0 / 0'); return; }
        var unlocked = STATE.achievements || {};
        var doneCount = defs.filter(function (a) { return unlocked[a.id]; }).length;
        $('#achieveCount').text(doneCount + ' / ' + defs.length);
        $('#achieveList').html(defs.map(function (a) {
            var done = !!unlocked[a.id];
            return '<div class="upgrade-card' + (done ? ' done' : '') + '">' +
                '<div class="head">' +
                '<span class="name">' + (done ? a.icon : '<span class="c-dim">' + (a.icon || '🏆') + '</span>') + ' ' + esc(a.name) + '</span>' +
                '<span class="' + (done ? 'c-green' : 'c-dim') + '">' + (done ? '✓ 已解锁' : '未解锁') + '</span>' +
                '</div>' +
                '<div class="desc c-dim">' + esc(a.desc || '') + '</div>' +
                '</div>';
        }).join(''));
    }

    // ---------------- 贸易（批发 / 外卖） ----------------
    function renderTrade() {
        var gp = DEF.gameplay || {};
        var trade = gp.trade;
        if (!trade) { $('#tradePanel').html('<span class="c-dim">本游戏未配置贸易玩法。</span>'); return; }
        var now = STATE.minutes || 0;
        var html = '';
        if (trade.wholesale && trade.wholesale.enabled) {
            var w = trade.wholesale;
            var cd = STATE.trade_cooldown_until && STATE.trade_cooldown_until > now
                ? (STATE.trade_cooldown_until - now) : 0;
            html += '<div class="upgrade-card">' +
                '<div class="head"><span class="name">🏷 ' + esc(w.broker_name || '批发商') + '（批量收购）</span>' +
                '<span class="' + (cd > 0 ? 'c-amber' : 'c-green') + '">' + (cd > 0 ? '清点货款中（剩 ' + cd + ' 分钟）' : '可交易') + '</span></div>' +
                '<div class="desc c-dim">收购价 = 建议零售价 × ' + w.price_ratio + '，' + w.min_qty + ' 件起批；批发不占用货架零售，快速回笼现金。</div></div>';
            // 商品表
            var rows = [];
            (DEF.products || []).forEach(function (p) {
                if (!unlocked(p)) return;
                var slot = STATE.inventory[p.id];
                var qty = slot ? slot.qty : 0;
                var unit = p.base_retail_price * w.price_ratio;
                rows.push('<tr data-pid="' + esc(p.id) + '">' +
                    '<td>' + esc(p.icon) + ' ' + esc(p.name) + '</td>' +
                    '<td class="num">' + qty + '</td>' +
                    '<td class="num">' + fmt(unit) + '</td>' +
                    '<td><div class="row-actions">' +
                    '<input type="number" min="' + w.min_qty + '" max="9999" value="' + w.min_qty + '" class="ws-qty">' +
                    '<button class="btn-wholesale" ' + (cd > 0 || qty < w.min_qty ? 'disabled' : '') + '>批发</button></div></td>' +
                    '</tr>');
            });
            var pg = pageSlice('tradeWs', rows);
            html += '<table class="tbl"><thead><tr>' +
                '<th>商品</th><th>库存</th><th>收购价/件</th><th>批发</th></tr></thead>' +
                '<tbody>' + pg.rows.join('') + '</tbody></table>' +
                '<div class="pager-row">' + pagerHtml('tradeWs', pg.total) + '</div>';
        } else if (trade.delivery && trade.delivery.enabled) {
            var dlv = trade.delivery;
            html += '<div class="upgrade-card">' +
                '<div class="head"><span class="name">🛵 外卖平台</span>' +
                '<span class="' + (STATE.delivery_open ? 'c-green' : 'c-dim') + '">' + (STATE.delivery_open ? '运营中' : '已关闭') + '</span></div>' +
                '<div class="desc c-dim">开通费 ' + fmt(dlv.setup_cost) + ' 元；每日自动接入 ' + dlv.orders_min + '~' + dlv.orders_max +
                ' 单（平台抽成后按 ×' + dlv.price_ratio + ' 结算）；占用厨房产能，堂食客流 ×' + (1 - dlv.traffic_penalty) +
                '；打烊后自动结算。</div></div>';
            html += '<div class="stat-grid">' +
                statCell('今日单数', STATE.delivery_today_orders || 0) +
                statCell('今日营收', fmt(STATE.delivery_today_revenue || 0) + ' 元') +
                statCell('累计单数', STATE.delivery_total_orders || 0) +
                statCell('累计营收', fmt(STATE.delivery_total_revenue || 0) + ' 元') +
                statCell('好评差评净分', (STATE.delivery_reviews || 0)) +
                '</div>';
            html += '<div style="margin-top:10px;">' +
                '<button class="' + (STATE.delivery_open ? 'btn-warn' : 'amber') + '" id="btnDeliveryToggle">' +
                (STATE.delivery_open ? '🚫 关闭外卖平台' : '🛵 开通外卖平台（' + fmt(dlv.setup_cost) + ' 元）') + '</button></div>';
        } else {
            html = '<span class="c-dim">本游戏未配置批发 / 外卖玩法。</span>';
        }
        $('#tradePanel').html(html);
        // 批发按钮
        $('#tradePanel').off('click', '.btn-wholesale').on('click', '.btn-wholesale', function () {
            var $tr = $(this).closest('tr');
            var pid = $tr.data('pid');
            var qty = parseInt($tr.find('.ws-qty').val(), 10);
            if (!qty || qty <= 0) { logError('批发数量无效'); return; }
            doAct({ action: 'trade', subaction: 'wholesale', product_id: pid, qty: qty });
        });
        var $dlvBtn = $('#btnDeliveryToggle');
        if ($dlvBtn.length) {
            $dlvBtn.off('click').on('click', function () {
                doAct({ action: 'trade', subaction: 'delivery', product_id: STATE.delivery_open ? 'off' : 'on' });
            });
        }
    }

    function statCell(k, v) {
        return '<div class="stat-item" style="flex:1 1 120px;"><div class="value" style="color:var(--cyan);">' + esc(v) + '</div><div class="label c-dim">' + esc(k) + '</div></div>';
    }

    // ---------------- 雇员 ----------------
    function renderStaff() {
        var cfg = (DEF.gameplay && DEF.gameplay.staff);
        var roles = (cfg && cfg.roles) || {};
        var roleIds = Object.keys(roles);
        var career = (cfg && cfg.career) || { enabled: false };
        var maxLv = 3;
        if (!roleIds.length) {
            $('#staffList').html('<span class="c-dim">本游戏未配置可雇佣角色。</span>');
            $('#staffPager').html('');
            return;
        }
        var rows = [];
        roleIds.forEach(function (rid) {
            var role = roles[rid];
            var st = STATE.staff && STATE.staff[rid];
            var hired = !!st;
            var salary = st ? st.salary : role.salary;
            var eff = st ? staffEffText(st) : '1.00×';
            var since = st ? '（已任职 ' + st.since_day + ' 天）' : '';
            var actionBtns;
            if (!hired) {
                actionBtns = '<button class="btn-hire" data-rid="' + esc(rid) + '">✅ 雇佣（月薪 ' + fmt(role.salary) + ' 元）</button>';
            } else {
                var promoteBtn = '';
                if (career.enabled) {
                    if (st.promoted) {
                        promoteBtn = '<button class="btn-skill" data-rid="' + esc(rid) + '"' + (st.skill_id ? ' disabled' : '') + '>' +
                            (st.skill_id ? '✨ ' + esc(skillNameById(rid, st.skill_id)) : '🎯 选择技能') + '</button>';
                    } else if (st.level >= maxLv && st.loyalty >= (career.promote_loyalty || 60)) {
                        promoteBtn = '<button class="btn-promote" data-rid="' + esc(rid) + '">🎓 晋升骨干</button>';
                    }
                }
                actionBtns =
                    '<button class="btn-raise" data-rid="' + esc(rid) + '">💹 涨薪</button>' +
                    '<button class="btn-cut" data-rid="' + esc(rid) + '">📉 降薪</button>' +
                    (career.enabled ? '<button class="btn-redpacket" data-rid="' + esc(rid) + '">🧧 红包</button>' : '') +
                    promoteBtn +
                    '<button class="btn-fire btn-warn" data-rid="' + esc(rid) + '">🚪 解雇</button>';
            }
            var effWarn = st && st.cut_level >= 2 ? ' <span class="c-red">可能离职</span>' : '';
            var careerHtml = '';
            if (career.enabled && st) {
                var lv = Math.min(st.level || 1, maxLv);
                var thr = career.level_threshold || 100;
                var pct = Math.min(100, Math.round((st.exp / thr) * 100));
                careerHtml =
                    '<div class="staff-exp" style="margin-top:6px;">' +
                    '<span class="c-cyan">Lv.' + lv + '</span><span class="exp-bar"><span class="exp-fill" style="width:' + pct + '%;"></span></span>' +
                    '<span class="c-dim">' + Math.round(st.exp) + '/' + thr + ' 经验</span></div>' +
                    '<div class="c-dim" style="margin-top:4px;">❤ 忠诚 ' + Math.round(st.loyalty) +
                    (st.promoted ? ' ｜ 🎓 骨干' + (st.skill_id ? ' ｜ ✨ ' + esc(skillNameById(rid, st.skill_id)) : '（待选技能）') : '') +
                    '</div>';
            }
            rows.push('<div class="upgrade-card">' +
                '<div class="head"><span class="name">' + (role.icon || '🧑') + ' ' + esc(role.name) + ' · <span class="c-cyan">' + esc(role.role === 'auto_buy' ? '自动进货' : roleNameZh(role.role)) + '</span></span>' +
                '<span class="' + (hired ? 'c-green' : 'c-dim') + '">' + (hired ? '在职 · 效率 ' + eff + effWarn + since : '未雇佣') + '</span></div>' +
                '<div class="desc c-dim" style="margin-bottom:8px;">' + esc(role.desc || '') + '</div>' +
                careerHtml +
                '<div class="row-actions" style="flex-wrap:wrap;">' + actionBtns + '</div>' +
                '</div>');
        });
        var pg = pageSlice('staff', rows);
        $('#staffList').html(pg.rows.join(''));
        $('#staffPager').html(pagerHtml('staff', pg.total));
        $('#staffList').off('click', 'button').on('click', 'button', function () {
            var rid = $(this).data('rid');
            if ($(this).hasClass('btn-hire')) doAct({ action: 'hire', staff_id: rid });
            else if ($(this).hasClass('btn-raise')) doAct({ action: 'raise', staff_id: rid });
            else if ($(this).hasClass('btn-cut')) doAct({ action: 'cut', staff_id: rid });
            else if ($(this).hasClass('btn-fire')) doAct({ action: 'fire', staff_id: rid });
            else if ($(this).hasClass('btn-redpacket')) doAct({ action: 'redpacket', staff_id: rid });
            else if ($(this).hasClass('btn-promote')) doAct({ action: 'promote', staff_id: rid });
            else if ($(this).hasClass('btn-skill')) openSkillPick(rid);
        });
    }

    function skillNameById(rid, skid) {
        var role = ((DEF.gameplay && DEF.gameplay.staff) || {}).roles && DEF.gameplay.staff.roles[rid];
        if (!role || !role.skills) return '技能';
        for (var i = 0; i < role.skills.length; i++) if (role.skills[i].id === skid) return role.skills[i].name;
        return '技能';
    }

    function roleNameZh(role) {
        return {
            auto_buy: '自动进货', promoter: '揽客', buyer: '采购', guard: '安保',
            runner: '配送', manager: '管理', cleaner: '保洁',
            realtime_restock: '实时补货', supplier: '供应商', chef: '大厨', waiter: '服务员'
        }[role] || role;
    }

    function staffEffText(st) {
        var eff = 1 + (st.raise_level - st.cut_level) * 0.15;
        eff += (st.level || 0) * 0.05;   // v1.11.0 等级加成
        if (st.promoted) eff += 0.10;    // v1.11.0 骨干加成
        eff = Math.max(0.55, Math.min(1.90, eff));
        return eff.toFixed(2) + '×';
    }

    // ---------------- 街坊社交 ----------------
    function renderSocial() {
        var opts = (DEF.gameplay && DEF.gameplay.social) || {};
        var optList = Object.keys(opts).map(function (k) { return { id: k, def: opts[k] }; });
        var rows = [];
        var now = STATE.minutes || 0;
        (DEF.npcs || []).forEach(function (npc) {
            var ns = STATE.npcs[npc.id] || { favorability: 30, visits: 0, last_comment: '' };
            var cur = Math.round(ns.favorability);
            var tier = favorTier(cur);
            var cdUntil = (STATE.social_cooldowns || {})[npc.id] || 0;
            var cdRemain = cdUntil > now ? (cdUntil - now) : 0;
            var btns = optList.map(function (o) {
                var disabled = cdRemain > 0 ? ' disabled' : '';
                return '<button class="social-btn" data-npc="' + esc(npc.id) + '" data-opt="' + esc(o.id) + '"' + disabled + '>' +
                    (o.def.icon || '💬') + ' ' + esc(o.def.name) + (o.def.cost > 0 ? ' ' + fmt(o.def.cost) + '元' : ' 免费') + '</button>';
            }).join('');
            rows.push('<div class="upgrade-card">' +
                '<div class="head"><span class="name">' + npc.icon + ' ' + esc(npc.name) + ' · <span class="' + tier.cls + '">' + tier.name + ' ' + cur + '</span></span>' +
                '<span class="' + (cdRemain > 0 ? 'c-amber' : 'c-dim') + '">' + (cdRemain > 0 ? '互动冷却 ' + cdRemain + ' 分钟' : '可以互动') + '</span></div>' +
                '<div class="meter" style="height:6px;margin:6px 0;"><div style="width:' + Math.max(0, Math.min(100, cur)) + '%;"></div></div>' +
                '<div class="desc c-dim" style="margin-bottom:8px;">' + esc(ns.last_comment || npc.motto || '还没聊过天') + '</div>' +
                '<div class="row-actions" style="flex-wrap:wrap;">' + btns + '</div>' +
                '</div>');
        });
        var pg = pageSlice('social', rows);
        $('#socialList').html(pg.rows.join('') || '<span class="c-dim">暂无街坊</span>');
        $('#socialPager').html(pagerHtml('social', pg.total));
        $('#socialList').off('click', '.social-btn').on('click', '.social-btn', function () {
            var npc = $(this).data('npc');
            var opt = $(this).data('opt');
            doAct({ action: 'social', npc_id: npc, subaction: opt });
        });
    }

    // ---------------- NPC 委托订单 ----------------
    function renderQuest() {
        var q = STATE.active_quest;
        var html = '';
        if (q) {
            var p = productById(q.product_id);
            var today = gameDay();
            var daysLeft = Math.max(0, q.deadline_day - today);
            html = '<div class="panel" style="background:linear-gradient(135deg,var(--bg-panel-2),rgba(255,193,7,0.12));border:1px solid rgba(255,193,7,0.45);">' +
                '<div class="panel-title">📋 NPC 委托订单</div>' +
                '<div class="kv"><span class="k">订单</span><span class="v">' + esc(q.flavor) + '</span></div>' +
                '<div class="kv"><span class="k">报酬</span><span class="v green">+' + fmt(q.reward) + ' ' + CURRENCY + '</span></div>' +
                '<div class="kv"><span class="k">期限</span><span class="v ' + (daysLeft <= 1 ? 'c-red' : 'c-amber') + '">' +
                '第 ' + q.deadline_day + ' 天结算' + (daysLeft <= 1 ? '（明天就交货！）' : '（还剩 ' + daysLeft + ' 天）') + '</span></div>' +
                '<div class="c-dim" style="margin-top:6px;">到期自动交货：库存足够 → 拿报酬 + 发布者好感；不足 → 声誉 -2。快去备货！</div>' +
                '</div>';
        } else {
            html = '<div class="panel" style="background:var(--bg-panel-2);">' +
                '<div class="panel-title">📋 NPC 委托订单</div>' +
                '<span class="c-dim">暂无委托。街坊可能会在清晨来下单：指定商品 N 件、限时交货、报酬比零售价更丰厚。</span>' +
                '</div>';
        }
        $('#questPanel').html(html);
    }

    // ---------------- 银行（存款/取款/彩票） ----------------
    function renderBank() {
        var lot = (DEF.gameplay && DEF.gameplay.lottery);
        var fin = (DEF.gameplay && DEF.gameplay.finance) || [];
        // 信息区：纯文本，每秒轮询重建无副作用
        $('#bankInfo').html(
            '<div class="panel-title">🏦 银行存款</div>' +
            '<div class="kv"><span class="k">存款余额</span><span class="v amber">' + fmt(STATE.bank_balance || 0) + ' ' + CURRENCY + '</span></div>' +
            '<div class="kv"><span class="k">累计利息</span><span class="v green">' + fmt(STATE.bank_interest_earned || 0) + ' ' + CURRENCY + '</span></div>' +
            '<div class="kv"><span class="k">年化利率</span><span class="v">' + (DEF.meta.bank_annual_rate * 100).toFixed(1) + '%（每日复利）</span></div>' +
            '<div class="c-dim" style="margin:4px 0 8px;">钱存银行每天生息、小偷也偷不到；但进货/发薪/还款需要手头现金，注意留足流动资金。</div>'
        );
        // 存取输入区：只渲染一次，轮询不重建，避免输入框闪跳无法录入
        var $in = $('#bankInputs');
        if (!$in.data('rendered')) {
            $in.html(
                '<input type="number" id="bankDepositInput" min="1" step="1" value="100" style="width:110px;">' +
                '<button class="btn-primary" id="btnBankDeposit">存入</button>' +
                '<input type="number" id="bankWithdrawInput" min="1" step="1" value="100" style="width:110px;">' +
                '<button class="btn-amber" id="btnBankWithdraw">取出</button>'
            );
            $in.data('rendered', true);
        }
        // 理财产品：定期锁定，到期兑付本金+利息
        var finHtml = '<div class="panel-title">💹 理财产品</div>';
        if (fin.length === 0) {
            finHtml += '<div class="c-dim">本游戏未开通理财产品。</div>';
        } else {
            finHtml += '<div class="c-dim" style="margin-bottom:6px;">定期锁定赚利息，到期自动兑付；提前赎回只退本金（利息归零）。</div>';
            finHtml += '<div class="finance-grid">';
            fin.forEach(function (f) {
                var est = Math.round(f.min_amount * f.annual_rate * f.period_days / 360);
                finHtml +=
                    '<div class="finance-card">' +
                    '<div class="fc-name">' + (f.icon || '💹') + ' ' + esc(f.name) + '</div>' +
                    '<div class="fc-meta">期限 <span class="fc-rate">' + f.period_days + ' 天</span> · 年化 <span class="fc-rate">' +
                    (f.annual_rate * 100).toFixed(1) + '%</span></div>' +
                    '<div class="fc-meta">起购 ' + fmt(f.min_amount) + ' ' + CURRENCY + ' · 预估收益 ~' + fmt(est) + '</div>' +
                    '<div class="fc-meta c-dim">' + esc(f.desc || '') + '</div>' +
                    '<div class="fc-actions"><button class="btn-primary" data-financebuy="' + f.id + '">买入</button></div>' +
                    '</div>';
            });
            finHtml += '</div>';
            // 持有列表
            var held = STATE.finances || [];
            if (held.length > 0) {
                var today = gameDay();
                finHtml += '<div style="font-size:12px;color:var(--fg-dim);margin:12px 0 4px;">持有 ' + held.length + ' 笔</div>';
                finHtml += '<table class="finance-held-table"><thead><tr><th>产品</th><th>本金</th><th>剩余</th><th>预估收益</th><th></th></tr></thead><tbody>';
                held.forEach(function (h, i) {
                    var left = Math.max(0, h.due_day - today);
                    var est2 = Math.round(h.amount * h.annual_rate * h.period_days / 360);
                    finHtml +=
                        '<tr><td>' + esc(h.product_id) + '</td><td>' + fmt(h.amount) + '</td><td>' +
                        (left === 0 ? '<span class="c-green">今日到期</span>' : left + ' 天') +
                        '</td><td class="c-green">+' + fmt(est2) + '</td>' +
                        '<td><button class="btn-amber" data-financeredeem="' + i + '">提前赎回</button></td></tr>';
                });
                finHtml += '</tbody></table>';
            } else {
                finHtml += '<div class="c-dim" style="margin-top:8px;">暂无持仓，买入一笔开启钱生钱。</div>';
            }
        }
        $('#financePanel').html(finHtml);

        // 彩票卡（普通彩票 + 刮刮乐）：数据可重建，按钮事件走 document 委托
        if (lot && lot.cost > 0) {
            var today = gameDay();
            var ticketDay = STATE.lottery_ticket_day || 0;
            var stateTxt, stateCls;
            if (ticketDay === today) {
                stateTxt = '已购彩票，明日清晨开奖';
                stateCls = 'c-amber';
            } else if (ticketDay > 0) {
                stateTxt = '有彩票待开奖';
                stateCls = 'c-amber';
            } else {
                stateTxt = '今日可买';
                stateCls = 'c-green';
            }
            var lastTxt = '';
            if (STATE.lottery_last_prize > 0) lastTxt = '<div class="c-green" style="margin-top:4px;">🎉 最近开奖：中奖 +' + STATE.lottery_last_prize + ' ' + CURRENCY + '！</div>';
            else if (STATE.lottery_last_prize === 0) lastTxt = '<div class="c-dim" style="margin-top:4px;">最近开奖：未中奖，再接再厉。</div>';
            var disabled = (ticketDay > 0) ? ' disabled' : '';
            var scratchHtml = '';
            if (lot.scratch_cost > 0) {
                var sLast = STATE.scratch_last_win;
                var sTxt = '';
                if (sLast > 0) sTxt = '<div class="c-green" style="margin-top:4px;">🎉 上次刮刮乐：中奖 +' + fmt(sLast) + ' ' + CURRENCY + '！</div>';
                else if (sLast === 0) sTxt = '<div class="c-dim" style="margin-top:4px;">上次刮刮乐：未中奖。</div>';
                scratchHtml =
                    '<div style="border-top:1px dashed rgba(255,193,7,0.3);margin-top:10px;padding-top:10px;">' +
                    '<div class="panel-title">🎫 刮刮乐 · 即买即刮</div>' +
                    '<div class="kv"><span class="k">价格 / 奖池</span><span class="v">' + fmt(lot.scratch_cost) + ' 元 · 中奖率 ' +
                    Math.round(lot.scratch_win_chance * 100) + '% · 奖金 ' + lot.scratch_prize_min + '~' + lot.scratch_prize_max + ' 元</span></div>' +
                    '<div class="kv"><span class="k">已刮</span><span class="v">' + (STATE.scratch_purchases || 0) + ' 张</span></div>' + sTxt +
                    '<div style="margin-top:8px;"><button class="btn-lottery" id="btnScratchBuy">🎫 买一张刮刮乐</button></div>' +
                    '<div class="c-dim" style="margin-top:6px;">即买即刮、上不封顶，适合手气正旺的你。</div></div>';
            }
            $('#lotteryInfo').html(
                '<div class="panel-title">🎰 幸运彩票</div>' +
                '<div class="kv"><span class="k">价格 / 奖池</span><span class="v">' + fmt(lot.cost) + ' 元 · 中奖率 ' + Math.round(lot.win_chance * 100) + '% · 奖金 ' + lot.prize_min + '~' + lot.prize_max + ' 元</span></div>' +
                '<div class="kv"><span class="k">状态</span><span class="v ' + stateCls + '">' + stateTxt + '</span></div>' + lastTxt +
                '<div style="margin-top:8px;"><button class="btn-lottery" id="btnLotteryBuy"' + disabled + '>🎫 买一张彩票</button></div>' +
                '<div class="c-dim" style="margin-top:6px;">每天限购一张，次日清晨开奖——小赌怡情，切勿上头。</div>' + scratchHtml
            );
        } else {
            $('#lotteryInfo').html('');
        }
    }

    // ---------------- 地图（地产 / 房屋贷款 / 迁移） ----------------
    function renderMap() {
        var map = (DEF.gameplay && DEF.gameplay.map);
        if (!map || !map.locations || !map.locations.length) {
            $('#mapList').html('<span class="c-dim">本游戏未配置地图。</span>');
            return;
        }
        var cur = STATE.current_location_id;
        var hl = STATE.house_loan;
        // 房贷信息卡
        var loanHtml = '';
        if (hl) {
            loanHtml = '<div class="panel" style="background:linear-gradient(135deg,var(--bg-panel-2),rgba(77,215,255,0.12));border:1px solid rgba(77,215,255,0.4);">' +
                '<div class="panel-title">🏠 房屋贷款</div>' +
                '<div class="kv"><span class="k">月供</span><span class="v amber">' + fmt(hl.monthly_payment) + ' ' + CURRENCY + ' / 期</span></div>' +
                '<div class="kv"><span class="k">剩余期数</span><span class="v">' + hl.months_left + ' 期（每 30 天一期）</span></div>' +
                '<div class="kv"><span class="k">剩余本金</span><span class="v">' + fmt(hl.principal) + ' ' + CURRENCY + '</span></div>' +
                (hl.overdue_days > 0 ? '<div class="c-red" style="margin-top:4px;">⚠️ 已逾期 ' + hl.overdue_days + ' 期，声誉受损，欠款继续计息！</div>' : '') +
                '</div>';
        } else {
            loanHtml = '<div class="panel" style="background:var(--bg-panel-2);">' +
                '<div class="panel-title">🏠 房屋贷款</div>' +
                '<span class="c-dim">暂无房贷。购买新地产时首付之外自动生成房贷（等额本息月供，每 30 天一期）。</span></div>';
        }
        $('#houseLoanCard').html(loanHtml);

        // 地产卡片列表
        var rows = map.locations.map(function (loc) {
            var owned = loc.id === cur;
            var down = loc.price * loc.down_payment_ratio;
            var principal = loc.price - down;
            var rate = (map.loan && map.loan.monthly_rate) || 0.02;
            var months = (map.loan && map.loan.months) || 12;
            var monthly = principal > 0 ? ((principal + principal * rate * months) / months) : 0;
            var repOk = (STATE.reputation || 0) >= (loc.unlock_reputation || 0);
            var cashOk = (STATE.cash || 0) >= down;
            var loanOk = !STATE.house_loan;
            var disabled = owned || !repOk || !cashOk || !loanOk;
            var reasons = [];
            if (owned) reasons.push('当前所在');
            if (!loanOk) reasons.push('房贷未还清');
            if (!repOk) reasons.push('需声誉 ' + loc.unlock_reputation);
            if (!cashOk) reasons.push('首付不足');
            var btn = owned
                ? '<span class="c-green" style="font-weight:700;">▶ 经营中</span>'
                : '<button class="btn-housebuy" data-loc="' + esc(loc.id) + '"' + (disabled ? ' disabled' : '') + '>' +
                  '🏠 申请房贷购买（首付 ' + fmt(down) + '）</button>';
            return '<div class="loc-card' + (owned ? ' owned' : '') + '">' +
                '<div class="loc-icon">' + (loc.icon || '🏪') + '</div>' +
                '<div class="loc-body">' +
                '<div class="loc-name">' + esc(loc.name) + (owned ? ' <span class="c-green">【当前】</span>' : '') + '</div>' +
                '<div class="loc-desc c-dim">' + esc(loc.desc || '') + '</div>' +
                '<div class="loc-stats">' +
                '<span class="stat-chip">👥 客流 ×' + loc.traffic_bonus.toFixed(2) + '</span>' +
                '<span class="stat-chip">💰 总价 ' + fmt(loc.price) + '</span>' +
                (principal > 0 ? '<span class="stat-chip">🏦 月供约 ' + fmt(monthly) + ' ×' + months + ' 期</span>' : '<span class="stat-chip">🆓 初始地产</span>') +
                '</div>' +
                (reasons.length ? '<div class="c-dim" style="margin-top:6px;">' + esc(reasons.join(' · ')) + '</div>' : '') +
                '<div style="margin-top:8px;">' + btn + '</div>' +
                '</div></div>';
        }).join('');
        $('#mapList').html('<div class="loc-grid">' + rows + '</div>');
        $('#mapList').off('click', '.btn-housebuy').on('click', '.btn-housebuy', function () {
            doAct({ action: 'housebuy', staff_id: $(this).data('loc') });
        });
    }

    // ---------------- 个人形象（装扮/资产/声望/NPC主动互动） ----------------
    function renderWardrobe() {
        var w = (DEF.gameplay && DEF.gameplay.wardrobe);
        var assets = (DEF.gameplay && DEF.gameplay.assets) || [];
        if (!w || !w.items || !w.items.length) return;
        var prestige = prestigeOf();
        var level = prestigeLevelOf(prestige);
        var lvNames = ['街坊平民', '崭露头角', '体面人士', '街坊名人', '城中名流', '传奇人物'];
        var nextLv = [10, 20, 50, 100, 200, 999];
        var cur = nextLv[Math.min(level, 5)];
        var next = nextLv[Math.min(level + 1, 5)];
        var pct = level >= 5 ? 100 : Math.min(100, Math.round((prestige - (level > 0 ? nextLv[level - 1] : 0)) / (next - (level > 0 ? nextLv[level - 1] : 0)) * 100));
        var worn = STATE.worn_items || {};
        var owned = STATE.owned_items || [];
        // 声望仪表
        $('#prestigePanel').html(
            '<div class="panel prestige-meter">' +
            '<div class="prestige-avatar">🧍</div>' +
            '<div><div class="c-dim" style="font-size:12px;">个人声望</div>' +
            '<div class="prestige-num">' + (Math.floor(prestige)) + '</div></div>' +
            '<span class="prestige-level-badge">' + lvNames[level] + '</span>' +
            '<div style="flex:1;min-width:140px;">' +
            '<div class="prestige-bar"><i style="width:' + pct + '%;"></i></div>' +
            '<div class="c-dim" style="font-size:11px;margin-top:4px;">' +
            (level >= 5 ? '已登顶：全城仰望的存在' : '距「' + lvNames[level + 1] + '」还需 ' + Math.max(0, next - Math.floor(prestige)) + ' 点') +
            '</div></div>' +
            '<div class="c-dim" style="font-size:12px;">👕 穿戴 + 🚗 资产（同品类取最高）→ 声望。<br>声望高：客单价溢价、街坊带客更勤、<b style="color:var(--green);">NPC 会主动上门互动</b>。</div>' +
            '</div>'
        );
        // 装扮区：按部位子 tab 分类显示
        var slotNames = { hat: '🎩 帽子', top: '👔 上衣', pants: '👖 裤子', watch: '⌚ 手表', shoes: '👟 鞋子' };
        var activeSlot = window.__wSlot || (w.slots[0]);
        if (w.slots.indexOf(activeSlot) < 0) activeSlot = w.slots[0];
        var subTabs = (w.slots || []).map(function (slot) {
            var isActive = slot === activeSlot;
            return '<span class="sub-tab' + (isActive ? ' active' : '') + '" data-slot="' + esc(slot) + '">' +
                (slotNames[slot] || slot) + (slot === worn[slot] ? '' : '') + '</span>';
        }).join('');
        var activeItems = w.items.filter(function (it) { return it.slot === activeSlot; });
        var wornItem = worn[activeSlot];
        var curIt = null;
        for (var i = 0; i < activeItems.length; i++) if (activeItems[i].id === wornItem) curIt = activeItems[i];
        var cards = activeItems.map(function (it) {
            var isWorn = it.id === wornItem;
            var isOwned = owned.indexOf(it.id) >= 0;
            var cls = isWorn ? 'wear-card worn' : (isOwned ? 'wear-card owned-not-worn' : 'wear-card');
            var btn = isWorn ? '<span class="c-green" style="font-size:11px;">✔ 已穿戴</span>'
                : (isOwned ? '<button class="btn-primary" style="padding:2px 10px;font-size:12px;" data-wear="' + esc(it.id) + '">穿戴</button>'
                           : '<button class="btn-lottery" style="padding:2px 10px;font-size:12px;" data-buyitem="' + esc(it.id) + '">购入 ' + fmt(it.price) + '</button>');
            return '<div class="' + cls + '">' +
                '<div class="w-icon">' + (it.icon || '🧥') + '</div>' +
                '<div class="w-brand">' + esc(it.brand) + '</div>' +
                '<div class="w-name">' + esc(it.name) + '</div>' +
                '<div class="w-price">' + fmt(it.price) + ' ' + CURRENCY + '</div>' +
                '<div class="w-prestige">声望 +' + (it.prestige || 0) + '</div>' +
                (it.desc ? '<div class="w-desc">' + esc(it.desc) + '</div>' : '') +
                '<div style="margin-top:6px;">' + btn + '</div></div>';
        }).join('');
        var head = '<div class="slot-head">' + (slotNames[activeSlot] || activeSlot) + '</div>' +
            (curIt ? '<span class="c-green" style="font-size:12px;margin-left:4px;">当前：' + (curIt.icon || '') + ' ' + esc(curIt.name) + '</span>' : '');
        $('#wardrobePanel').html('<div class="panel-title">🧍 个人装扮</div>' +
            '<div class="sub-tabs">' + subTabs + '</div>' +
            '<div class="slot-card">' + head + '<div class="wear-grid">' + cards + '</div></div>');
        // 资产区：按车/住宅子 tab 分类显示
        var activeCat = window.__wCat || 'vehicle';
        var assetCats = [['vehicle', '🚗 座驾'], ['house', '🏠 住宅']];
        var catTabs = assetCats.map(function (c) {
            var isActive = c[0] === activeCat;
            return '<span class="sub-tab' + (isActive ? ' active' : '') + '" data-cat="' + c[0] + '">' + c[1] + '</span>';
        }).join('');
        var assetHtml = assets.filter(function (a) { return (a.category || 'vehicle') === activeCat; }).map(function (a) {
            var isOwned = (STATE.owned_assets || []).indexOf(a.id) >= 0;
            var btn = isOwned
                ? '<span class="c-green" style="font-weight:700;">✔ 已购置</span>'
                : '<button class="btn-asset" data-buyasset="' + esc(a.id) + '">购入 ' + fmt(a.price) + ' ' + CURRENCY + '</button>';
            return '<div class="asset-card' + (isOwned ? ' owned' : '') + '">' +
                '<div class="asset-icon">' + (a.icon || '🚗') + '</div>' +
                '<div class="asset-name">' + esc(a.name) + '</div>' +
                '<div class="c-dim" style="font-size:12px;">声望 +' + (a.prestige || 0) + '</div>' +
                (a.desc ? '<div class="c-dim" style="font-size:12px;margin:4px 0;">' + esc(a.desc) + '</div>' : '') +
                '<div style="margin-top:8px;">' + btn + '</div></div>';
        }).join('');
        $('#assetPanel').html('<div class="panel-title">🏆 个人资产（车/住宅同品类取最高档计入声望）</div>' +
            '<div class="sub-tabs">' + catTabs + '</div>' +
            '<div class="asset-grid">' + assetHtml + '</div>');
        // 互动记录
        var today = gameDay();
        var doneToday = (STATE.prestige_interact_day || 0) === today;
        $('#prestigeLogPanel').html(
            '<div class="panel" style="background:var(--bg-panel-2);">' +
            '<div class="panel-title">💬 NPC 主动互动</div>' +
            '<div class="kv"><span class="k">累计互动</span><span class="v green">' + (STATE.prestige_interactions || 0) + ' 次</span></div>' +
            '<div class="kv"><span class="k">今日状态</span><span class="v ' + (doneToday ? 'amber' : 'c-dim') + '">' + (doneToday ? '已有人主动登门' : '待街坊登门（声望越高越可能）') + '</span></div>' +
            '<div class="c-dim" style="margin-top:6px;">声望 ≥10 起 NPC 会主动上门：崭露头角打招呼 → 体面送小礼 → 名望带客 → 名流求合影（声誉+1）→ 传奇收豪礼（80~200 元）。</div></div>'
        );
        // 按钮委托（含子 tab 切换）：先统一 off 再分别 on，避免链式 off 清掉已绑委托
        $('#wardrobePanel').off('click');
        $('#wardrobePanel').on('click', '[data-wear]', function () {
            doAct({ action: 'wear', staff_id: $(this).data('wear') });
        });
        $('#wardrobePanel').on('click', '[data-buyitem]', function () {
            doAct({ action: 'buyitem', staff_id: $(this).data('buyitem') });
        });
        $('#wardrobePanel').on('click', '[data-slot]', function () {
            window.__wSlot = $(this).data('slot');
            renderWardrobe();
        });
        $('#assetPanel').off('click');
        $('#assetPanel').on('click', '[data-buyasset]', function () {
            doAct({ action: 'buyasset', staff_id: $(this).data('buyasset') });
        });
        $('#assetPanel').on('click', '[data-cat]', function () {
            window.__wCat = $(this).data('cat');
            renderWardrobe();
        });
    }

    function prestigeOf() {
        var worn = STATE.worn_items || {};
        var items = (DEF.gameplay && DEF.gameplay.wardrobe && DEF.gameplay.wardrobe.items) || [];
        var assets = (DEF.gameplay && DEF.gameplay.assets) || [];
        var p = 0;
        for (var k in worn) {
            for (var i = 0; i < items.length; i++) if (items[i].id === worn[k]) p += items[i].prestige || 0;
        }
        var best = {};
        (STATE.owned_assets || []).forEach(function (aid) {
            for (var j = 0; j < assets.length; j++) {
                if (assets[j].id === aid) {
                    var cat = assets[j].category || 'x';
                    if (!best[cat] || best[cat] < (assets[j].prestige || 0)) best[cat] = assets[j].prestige || 0;
                }
            }
        });
        for (var c in best) p += best[c];
        return p;
    }
    function prestigeLevelOf(p) {
        if (p >= 200) return 5;
        if (p >= 100) return 4;
        if (p >= 50) return 3;
        if (p >= 20) return 2;
        if (p >= 10) return 1;
        return 0;
    }

    // ---------------- 实时日志流：购买记录 / 随机事件 -> 终端滚动显示 ----------------
    var lastLiveLen = 0;
    function appendLiveLog() {
        var log = (STATE && STATE.live_log) || [];
        if (!log.length) return;
        if (lastLiveLen > log.length) lastLiveLen = 0; // 存档切换
        for (var i = lastLiveLen; i < log.length; i++) {
            var line = log[i];
            if (line.indexOf('🔔') === 0) logLine('<span class="c-amber">' + esc(line) + '</span>');
            else if (line.indexOf('📋') === 0) logLine('<span class="c-cyan">' + esc(line) + '</span>');
            else logLine('<span class="c-dim">' + esc(line) + '</span>');
        }
        lastLiveLen = log.length;
    }

    function renderShelf() {
        var rows = [];
        (DEF.products || []).forEach(function (p) {
            var slot = STATE.inventory[p.id];
            var qty = slot ? slot.qty : 0;
            var life = slot ? slot.shelf_life_remaining : 0;
            var isLocked = !unlocked(p);
            if (isLocked) {
                var need = upgradeById(p.unlock_upgrade_id);
                rows.push('<tr class="locked">' +
                    '<td>' + esc(p.icon) + ' ' + esc(p.name) + '</td>' +
                    '<td>' + esc(p.category) + '</td>' +
                    '<td class="num">' + fmt(p.wholesale_price) + '</td>' +
                    '<td class="num">' + fmt(p.base_retail_price) + '</td>' +
                    '<td class="num" colspan="2">🔒 未解锁</td>' +
                    '<td colspan="2" style="color:var(--amber);">需装修：' + esc(need ? need.name : '?') + '</td>' +
                    '</tr>');
                return;
            }
            var lifeCls = life <= 1 ? 'c-red' : (life <= 3 ? 'c-amber' : 'c-dim');
            var lowCls = qty <= 0 ? 'class="c-red"' : '';
            rows.push('<tr data-pid="' + esc(p.id) + '">' +
                '<td><span ' + lowCls + '>' + esc(p.icon) + ' ' + esc(p.name) + '</span></td>' +
                '<td>' + esc(p.category) + '</td>' +
                '<td class="num">' + fmt(p.wholesale_price) + '</td>' +
                '<td class="num">' + fmt(p.base_retail_price) + '</td>' +
                '<td class="num"><span ' + lowCls + '>' + qty + '</span></td>' +
                '<td class="num"><span class="' + lifeCls + '">' + (slot ? life + ' 天' : '-') + '</span></td>' +
                '<td><div class="row-actions">' +
                '<input type="number" step="0.1" min="' + DEF.meta.price_min + '" max="' + DEF.meta.price_max + '" value="' + (STATE.price_multiplier[p.id] || 1) + '" class="mult-input">' +
                '<button class="btn-price">定价</button></div></td>' +
                '<td><div class="row-actions">' +
                '<input type="number" min="1" max="999" value="10" class="qty-input">' +
                '<button class="btn-buy">进货</button></div></td>' +
                '</tr>');
        });
        var pg = pageSlice('shelf', rows);
        $('#shelfTable tbody').html(pg.rows.join(''));
        $('#shelfPager').html(pagerHtml('shelf', pg.total));
        // 绑定事件（委托，页面切换后仍有效）
        // 注意：必须一次 off 后链式 on 多个选择器，避免二次 off 清掉前一个绑定
        $('#shelfTable tbody').off('click')
            .on('click', '.btn-buy', function () {
                var $tr = $(this).closest('tr');
                var pid = $tr.data('pid');
                var qty = parseInt($tr.find('.qty-input').val(), 10);
                if (!qty || qty <= 0) { logError('数量必须大于 0'); return; }
                doAct({ action: 'buy', product_id: pid, qty: qty });
            })
            .on('click', '.btn-price', function () {
                var $tr = $(this).closest('tr');
                var pid = $tr.data('pid');
                var mult = parseFloat($tr.find('.mult-input').val());
                doAct({ action: 'price', product_id: pid, multiplier: mult });
            });
    }

    function renderPrice() {
        var rows = [];
        (DEF.products || []).forEach(function (p) {
            if (!unlocked(p)) return;
            var mult = STATE.price_multiplier[p.id] || 1;
            var retail = p.base_retail_price * mult;
            var margin = retail - p.wholesale_price;
            rows.push('<tr data-pid="' + esc(p.id) + '">' +
                '<td>' + esc(p.icon) + ' ' + esc(p.name) + '</td>' +
                '<td class="num">' + fmt(p.base_retail_price) + '</td>' +
                '<td class="num c-green">' + fmt(retail) + '</td>' +
                '<td class="num c-amber">' + fmt(margin) + '（' + Math.round(margin / p.wholesale_price * 100) + '%）</td>' +
                '<td><div class="row-actions">' +
                '<input type="number" step="0.1" min="' + DEF.meta.price_min + '" max="' + DEF.meta.price_max + '" value="' + mult + '" class="pm-input">' +
                '<button class="btn-pset">应用</button></div></td>' +
                '</tr>');
        });
        var pg = pageSlice('price', rows);
        $('#priceTable tbody').html(pg.rows.join(''));
        $('#pricePager').html(pagerHtml('price', pg.total));
        $('#priceTable tbody').off('click', '.btn-pset').on('click', '.btn-pset', function () {
            var $tr = $(this).closest('tr');
            var pid = $tr.data('pid');
            var mult = parseFloat($tr.find('.pm-input').val());
            doAct({ action: 'price', product_id: pid, multiplier: mult });
        });
    }

    function renderUpgrades() {
        var cards = [];
        (DEF.upgrades || []).forEach(function (u) {
            var done = owned(u.id);
            var canBuy = !done && STATE.shop_level === u.level - 1;
            var afford = canBuy && STATE.cash >= u.cost;
            var effects = [];
            if (u.foot_traffic_bonus > 0) effects.push('客流量 +' + Math.round(u.foot_traffic_bonus * 100) + '%');
            if (u.max_customers_bonus > 0) effects.push('客流上限 +' + u.max_customers_bonus);
            if (u.reputation_bonus > 0) effects.push('声誉 +' + u.reputation_bonus);
            if (u.auto_reputation_per_day > 0) effects.push('每日声誉 +' + u.auto_reputation_per_day);
            if (u.unlock_categories && u.unlock_categories.length) effects.push('解锁分类：' + u.unlock_categories.join('、'));
            cards.push('<div class="upgrade-card' + (done ? ' done' : '') + '">' +
                '<div class="head">' +
                '<span class="name">' + u.icon + ' ' + esc(u.name) + ' <span class="c-dim">(Lv.' + u.level + ')</span></span>' +
                '<span class="cost">' + fmt(u.cost) + ' ' + CURRENCY + '</span></div>' +
                '<div class="desc">' + esc(u.description) + '</div>' +
                '<div class="effects">' + effects.join(' ｜ ') + '</div>' +
                '<div style="margin-top:8px;">' +
                (done ? '<span class="c-dim">✔ 已装修</span>'
                    : (!canBuy ? '<span class="c-dim">需先完成 Lv.' + (u.level - 1) + ' 装修</span>'
                        : '<button class="btn-renovate" data-id="' + esc(u.id) + '"' + (afford ? '' : ' disabled') + '>🔨 开始装修</button>' +
                        (afford ? '' : ' <span class="c-dim">（现金不足）</span>'))) +
                '</div></div>');
        });
        var pg = pageSlice('upgrade', cards);
        $('#upgradeList').html(pg.rows.join(''));
        $('#upgradePager').html(pagerHtml('upgrade', pg.total));
        $('#upgradeList').off('click', '.btn-renovate').on('click', '.btn-renovate', function () {
            doAct({ action: 'renovate', upgrade_id: $(this).data('id') });
        });
    }

    function renderLoans() {
        var cards = [];
        (DEF.loans || []).forEach(function (l) {
            cards.push('<div class="loan-card">' +
                '<div class="head">' +
                '<span class="name">' + l.icon + ' ' + esc(l.name) + '</span>' +
                '<span class="rate">额度 ' + fmt(l.max_amount) + ' · 月利率 ' + Math.round(l.monthly_rate * 100) + '%</span></div>' +
                '<div class="desc">' + esc(l.description) + '</div>' +
                '<button class="btn-loan" data-id="' + esc(l.id) + '">🏦 借款</button></div>');
        });
        var pg = pageSlice('loan', cards);
        $('#loanList').html(pg.rows.join(''));
        $('#loanPager').html(pagerHtml('loan', pg.total));
        $('#loanList').off('click', '.btn-loan').on('click', '.btn-loan', function () {
            doAct({ action: 'loan', loan_id: $(this).data('id') });
        });
    }

    function renderDebtPanel() {
        var interest = (STATE.debt || 0) * maxRate() / 30;
        // 数据区：纯文本，每秒轮询重建无副作用
        $('#debtInfo').html(
            '<div class="kv"><span class="k">当前负债</span><span class="v ' + (STATE.debt > 0 ? 'red' : 'green') + '">' + fmt(STATE.debt) + ' ' + CURRENCY + '</span></div>' +
            '<div class="kv"><span class="k">每日利息（游戏日）</span><span class="v amber">' + fmt(interest) + ' ' + CURRENCY + '</span></div>' +
            '<div class="kv"><span class="k">负债安全线</span><span class="v">' + fmt(DEF.lose.debt_limit) + ' ' + CURRENCY + '</span></div>'
        );
        // 还款输入区：只渲染一次，轮询仅切换显隐，避免输入框每秒被重建导致闪跳
        var $box = $('#repayBox');
        var needRepay = (STATE.debt || 0) > 0;
        if (needRepay) {
            if (!$box.data('rendered')) {
                $box.html('<div style="display:flex;gap:8px;margin-top:8px;align-items:center;">' +
                    '<input type="number" id="repayInput" min="1" step="10" placeholder="还款金额">' +
                    '<button class="btn-repay">还款</button></div>');
                $box.data('rendered', true);
                $box.off('click', '.btn-repay').on('click', '.btn-repay', function () {
                    var amount = parseFloat($('#repayInput').val());
                    doAct({ action: 'repay', amount: amount });
                });
            }
            $box.show();
        } else {
            if ($box.data('rendered')) {
                $('#repayInput').val('');
                $box.hide();
            } else {
                $box.html('<span class="c-dim">无负债，经营稳健。</span>');
            }
        }
    }

    function maxRate() {
        var r = 0;
        (STATE.loans || []).forEach(function (l) { r = Math.max(r, l.monthly_rate); });
        return r;
    }

    function renderReport() {
        var st = STATE.stats || {};
        var win = DEF.win;
        var npcRows = (DEF.npcs || []).map(function (npc) {
            var ns = STATE.npcs[npc.id] || {};
            var tier = favorTier(ns.favorability);
            return '<div class="kv"><span class="k">' + npc.icon + ' ' + esc(npc.name) + '（' + esc(npc.title) + '）</span>' +
                '<span class="v ' + tier.cls + '">' + tier.name + ' ' + Math.round(ns.favorability) + ' · 来访 ' + (ns.visits || 0) + ' 次</span></div>';
        });
        var npg = pageSlice('npc', npcRows);
        var html =
            '<div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:6px 18px;margin-bottom:14px;">' +
            kv('总营业额', fmt(st.total_revenue) + ' ' + CURRENCY, 'green') +
            kv('总利润', fmt(st.total_profit) + ' ' + CURRENCY, '') +
            kv('累计顾客', st.customers_served + ' 人', '') +
            kv('累计售出', st.items_sold + ' 件', '') +
            kv('营业天数', st.days_played + ' 天', '') +
            kv('最佳单日', fmt(st.best_day_revenue) + ' ' + CURRENCY, 'cyan') +
            kv('过期损耗', st.expired_items + ' 件', 'red') +
            kv('当前净资产', fmt(netWorth()) + ' ' + CURRENCY, 'amber') +
            '</div>' +
            '<div class="panel" style="background:var(--bg-panel-2);margin-bottom:10px;">' +
            '<div class="panel-title">经营目标</div>' +
            '<div class="kv"><span class="k">胜利条件</span><span class="v c-green">第 ' + win.by_year + ' 年结束前净资产 ≥ ' + fmt(win.target_net_worth) + ' ' + CURRENCY + '</span></div>' +
            '<div class="kv"><span class="k">破产条件</span><span class="v c-red">负债 > ' + fmt(DEF.lose.debt_limit) + ' 且持续 ' + DEF.lose.grace_days + ' 天</span></div>' +
            '<div class="kv"><span class="k">当前进度</span><span class="v ' + (netWorth() >= win.target_net_worth ? 'c-green c-b' : 'c-amber') + '">' + fmt(netWorth()) + ' / ' + fmt(win.target_net_worth) + '</span></div>' +
            '<div class="meter ' + (netWorth() >= win.target_net_worth ? '' : 'amber') + '" style="margin-top:6px;"><div style="width:' + Math.min(100, Math.max(0, netWorth() / win.target_net_worth * 100)) + '%;"></div></div>' +
            '</div>' +
            '<div class="panel" style="background:var(--bg-panel-2);margin-bottom:10px;">' +
            '<div class="panel-title">口碑评级 <span class="c-dim">（价格/新鲜/服务 → 每日满意度 → 月度评级）</span></div>' +
            '<div class="kv"><span class="k">当前评级</span><span class="v ' + ratingCls() + ' c-b">' + ratingName() + '</span>' +
            '<span class="c-dim"> · 上月满意度 ' + (STATE.last_month_avg || 0) + '</span></div>' +
            '<div class="kv"><span class="k">今日满意度</span><span class="v">' + Math.round(STATE.satisfaction_score || 0) + ' / 100</span>' +
            '<span class="c-dim"> · 评级加成：街区标杆 客单价+8% / 邻里名店 +4% / 街坊小铺 客流-5%</span></div>' +
            satHistoryBars() +
            '</div>' +
            '<div class="panel" style="background:var(--bg-panel-2);">' +
            '<div class="panel-title">街坊关系总览</div>' +
            npg.rows.join('') +
            pagerHtml('npc', npg.total) +
            '</div>' +
            renderFestivalCard();
        $('#reportPanel').html(html);
        bindFestivalPanel();
    }
    function kv(k, v, cls) {
        return '<div class="kv"><span class="k">' + esc(k) + '</span><span class="v ' + (cls ? cls : '') + '">' + esc(v) + '</span></div>';
    }

    // ---------------- v1.11.0 节日庆典 + 员工培养晋升 ----------------
    function festivalBadgeHtml() {
        var fs = STATE.festival;
        if (!fs) return '';
        if (STATE.minutes >= fs.started_min && STATE.minutes < fs.end_min) {
            return '<span class="stat-item"><span class="label">节日</span> <span class="value amber">' + esc(fs.icon) + ' ' + esc(fs.name) + '</span></span>';
        }
        if (STATE.minutes < fs.started_min) {
            return '<span class="stat-item"><span class="label">筹备</span> <span class="value cyan">🎪 ' + esc(fs.name) + '</span></span>';
        }
        return '';
    }
    function festivalDefById(id) {
        var f = (DEF.gameplay && DEF.gameplay.festival);
        if (!f || !f.festivals) return null;
        var hit = null;
        f.festivals.forEach(function (x) { if (x.id === id) hit = x; });
        return hit;
    }
    function festivalDay(fd) {
        var seasons = DEF.seasons || [];
        var idx = -1;
        for (var i = 0; i < seasons.length; i++) if (seasons[i].id === fd.season) idx = i;
        if (idx < 0) return 99999;
        return idx * (DEF.meta.days_per_season || 7) + fd.day;
    }
    function renderFestivalCard() {
        var f = (DEF.gameplay && DEF.gameplay.festival);
        if (!f || !f.enabled) return '';
        var fs = STATE.festival;
        var nowDay = gameDay();
        var html = '<div class="panel" style="background:var(--bg-panel-2);">' +
            '<div class="panel-title">🎪 节日庆典</div>';
        if (fs && STATE.minutes >= fs.started_min && STATE.minutes < fs.end_min) {
            var fd = festivalDefById(fs.id);
            var name = fd ? fd.icon + ' ' + esc(fd.name) : esc(fs.name);
            var cap = fd ? fd.setup_cap : 1000;
            var prep = Math.min(100, Math.round((fs.setup / cap) * 100));
            html +=
                '<div class="kv"><span class="k">' + name + ' 进行中</span><span class="v green">还剩 ' + fmtClock(fs.end_min - STATE.minutes) + '</span></div>' +
                '<div class="staff-exp" style="margin:6px 0;"><span class="c-dim">筹备气氛 ' + fmt(fs.setup) + '/' + fmt(cap) + '</span>' +
                '<span class="exp-bar"><span class="exp-fill" style="width:' + prep + '%;"></span></span></div>' +
                '<div class="row-actions" style="flex-wrap:wrap;">' +
                '<button class="btn-fsetup" data-amount="100">🎊 追加筹备金 100</button>' +
                '<button class="btn-fsetup" data-amount="500">🔥 追加筹备金 500</button></div>' +
                '<div class="desc c-dim" style="margin-top:6px;">节日客流 +30%、客单价 +10%，特定商品需求暴涨；闭幕按筹备金回报，筹备满额另享节日营业额 10% 抽成。</div>';
        } else if (fs && STATE.minutes < fs.started_min) {
            var fd2 = festivalDefById(fs.id);
            var cap2 = fd2 ? fd2.setup_cap : 1000;
            var prep2 = Math.min(100, Math.round((fs.setup / cap2) * 100));
            html +=
                '<div class="kv"><span class="k">🎊 ' + esc(fs.name) + ' 筹备中</span><span class="v cyan">' + fmtClock(fs.started_min - STATE.minutes) + ' 后开幕</span></div>' +
                '<div class="staff-exp" style="margin:6px 0;"><span class="c-dim">筹备金 ' + fmt(fs.setup) + '/' + fmt(cap2) + '</span>' +
                '<span class="exp-bar"><span class="exp-fill" style="width:' + prep2 + '%;"></span></span></div>' +
                '<div class="row-actions" style="flex-wrap:wrap;">' +
                '<button class="btn-fsetup" data-amount="100">🎊 追加筹备金 100</button>' +
                '<button class="btn-fsetup" data-amount="500">🔥 追加筹备金 500</button></div>' +
                '<div class="desc c-dim" style="margin-top:6px;">筹备金越高，开幕后节日气氛越旺（客流加成最高 +30%）。</div>';
        } else {
            var next = null;
            (f.festivals || []).forEach(function (fd0) {
                var d = festivalDay(fd0);
                if (d >= nowDay && (!next || d < festivalDay(next))) next = fd0;
            });
            if (next) {
                var nd = festivalDay(next);
                var daysLeft = nd - nowDay;
                html +=
                    '<div class="kv"><span class="k">下一节日</span><span class="v cyan">' + next.icon + ' ' + esc(next.name) + ' · ' + (daysLeft === 0 ? '今日开幕' : daysLeft + ' 天后开幕') + '</span></div>' +
                    '<div class="desc c-dim" style="margin-top:6px;">节日开幕前可投入筹备金（festival_setup <金额>），提升节日气氛与战报奖金。</div>';
            } else {
                html += '<div class="desc c-dim">本年度节日均已结束，来年再见。</div>';
            }
        }
        var hist = STATE.festival_history || [];
        if (hist.length) {
            html += '<div class="kv"><span class="k">上次战报</span><span class="v amber">' + esc(hist[0]) + '</span></div>';
        }
        html += '</div>';
        return html;
    }
    function bindFestivalPanel() {
        $('#reportPanel').off('click', '.btn-fprep').on('click', '.btn-fprep', function () {
            doAct({ action: 'festival_setup', amount: 100 });
        });
        $('#reportPanel').off('click', '.btn-fsetup').on('click', '.btn-fsetup', function () {
            doAct({ action: 'festival_setup', amount: parseInt($(this).data('amount'), 10) });
        });
    }
    function openSkillPick(rid) {
        var role = ((DEF.gameplay && DEF.gameplay.staff) || {}).roles && DEF.gameplay.staff.roles[rid];
        var skills = (role && role.skills) || [];
        if (!skills.length) { logError('该角色没有可选的晋升技能'); return; }
        var html = '<div class="desc c-dim" style="margin-bottom:10px;">为 ' + esc(role.icon || '') + ' ' + esc(role.name) + ' 选择一项晋升技能（选择后不可更换）：</div>';
        skills.forEach(function (sk) {
            html += '<button class="skill-pick" data-rid="' + esc(rid) + '" data-skill="' + esc(sk.id) + '" style="width:100%;margin-bottom:8px;text-align:left;">' +
                sk.icon + ' <b>' + esc(sk.name) + '</b><span class="c-dim"> — ' + esc(sk.desc) + '</span></button>';
        });
        $('#skillList').html(html);
        $('#skillMask').fadeIn(120);
        $('#skillList').off('click', 'button').on('click', 'button', function () {
            var srid = $(this).data('rid');
            var skid = $(this).data('skill');
            $('#skillMask').fadeOut(120);
            doAct({ action: 'pick_skill', staff_id: srid, skill_id: skid });
        });
    }

    // ---------------- v1.10.0 口碑评级 / 供应链 / 街区竞争 ----------------
    function ratingName() {
        var names = ['街坊小铺', '口碑小店', '邻里名店', '街区标杆'];
        return names[STATE.rating_tier || 1] || '口碑小店';
    }
    function ratingCls() {
        var t = STATE.rating_tier || 1;
        return t === 3 ? 'c-gold' : t === 2 ? 'c-cyan' : t === 1 ? 'c-green' : 'c-red';
    }
    function satHistoryBars() {
        var h = STATE.satisfaction_history || [];
        if (!h.length) return '<div class="c-dim" style="margin-top:4px;">暂无满意度记录（过一天后生成）。</div>';
        var max = 30;
        var slice = h.slice(Math.max(0, h.length - max));
        var cells = slice.map(function (v, i) {
            var idx = slice.length - i - 1;
            var cls = v >= 85 ? 'bar-high' : v >= 70 ? 'bar-mid' : v >= 55 ? 'bar-low' : 'bar-bad';
            return '<div class="sat-bar ' + cls + '" title="' + idx + ' 天前 ' + Math.round(v) + ' 分" style="height:' + Math.max(6, Math.round(v)) + '%"></div>';
        }).join('');
        return '<div class="sat-history" style="height:56px;margin-top:8px;">' + cells + '</div>';
    }

    /** 供应链卡（贸易页顶部） */
    function renderSupply() {
        var sp = (DEF.gameplay || {}).supply;
        if (!sp || !sp.enabled) { $('#supplyPanel').html(''); return; }
        var pts = STATE.supply_points || 0;
        var tiers = sp.tiers || [];
        var idx = 0;
        for (var i = 0; i < tiers.length; i++) { if (pts >= tiers[i].req_points) idx = i; }
        var cur = tiers[idx] || tiers[0];
        var next = tiers[idx + 1];
        var maxPts = tiers.length ? tiers[tiers.length - 1].req_points : 1;
        var pct = Math.min(100, Math.round(pts / maxPts * 100));
        var html =
            '<div class="panel" style="background:linear-gradient(135deg,var(--bg-panel-2),rgba(139,200,234,0.08));border:1px solid rgba(139,200,234,0.35);">' +
            '<div class="panel-title">📦 ' + esc(sp.supplier_name || '供应商') + ' <span class="c-dim">（供应链 · v1.10）</span></div>' +
            '<div class="kv"><span class="k">合作值</span><span class="v c-cyan">' + fmt1(pts) + ' / ' + fmt1(maxPts) + '</span>' +
            '<span class="c-dim"> · 当前等级【' + esc(cur.name) + '】进货 ' + Math.round((1 - cur.discount) * 100) + ' 折</span></div>' +
            '<div class="meter cyan" style="margin:4px 0 8px;"><div style="width:' + pct + '%;"></div></div>' +
            (next ? '<div class="kv"><span class="k">下一级</span><span class="v">【' + esc(next.name) + '】需 ' + (next.req_points - Math.floor(pts)) + ' 合作值（进货 ' + Math.round((1 - next.discount) * 100) + ' 折' + (next.credit_limit > 0 ? '，解锁赊账 ' + fmt1(next.credit_limit) : '') + '）</span></div>' : '') +
            (cur.credit_limit > 0 ? '<div class="kv"><span class="k">赊账</span><span class="v">额度 ' + fmt1(cur.credit_limit) + '，已用 ' + fmt1(STATE.credit_used || 0) + (STATE.credit_used > 0 ? '，到期自动扣款' : '') + '</span></div>' : '') +
            '<div class="row-actions" style="margin-top:6px;">' +
            '<button class="btn" id="btnSupplyView" title="supply 命令">📋 查看详情</button>' +
            (cur.credit_limit > 0 ? '<input type="number" id="supplyCreditQty" class="num-input" min="1" placeholder="数量" style="width:72px;">' +
            '<button class="btn cyan" id="btnBuyCredit" title="buy_credit 命令">🤝 赊账进货</button>' : '') +
            '</div>' +
            '<div class="c-dim" style="font-size:11px;margin-top:4px;">进货实付金额 = 合作值；达到【' + esc(tiers[tiers.length - 1].name) + '】可解锁独家高毛利商品：' +
            (sp.exclusive_products || []).map(function (pid) {
                var pr = productById(pid);
                return pr ? pr.icon + ' ' + pr.name + '（进 ' + fmt(pr.wholesale_price) + ' 售 ' + fmt(pr.base_retail_price) + '）' : pid;
            }).join('、') + '</div>' +
            '</div>';
        $('#supplyPanel').html(html);
        // 事件：查看详情 / 赊账进货（容器委托，避免每秒重建丢失）
        $('#supplyPanel').off('click', '#btnSupplyView').on('click', '#btnSupplyView', function () { doAct({ action: 'supply' }); });
        $('#supplyPanel').off('click', '#btnBuyCredit').on('click', '#btnBuyCredit', function () {
            var qty = parseInt($('#supplyCreditQty').val(), 10);
            if (!qty || qty <= 0) { logError('请先输入赊账进货数量。'); return; }
            // 赊账进货默认补第一件已解锁商品（货架页也可对具体商品用 buy_credit 命令）
            var first = null;
            (DEF.products || []).forEach(function (p) {
                if (!first && unlocked(p)) first = p;
            });
            if (!first) { logError('暂无可进货的已解锁商品。'); return; }
            doAct({ action: 'buy_credit', product_id: first.id, qty: qty });
        });
    }

    /** 街区竞争卡（竞争页） */
    function renderCompete() {
        var cd = (DEF.gameplay || {}).competition;
        if (!cd || !cd.enabled) { $('#competePanel').html('<span class="c-dim">本游戏未配置街区竞争玩法。</span>'); return; }
        var comps = STATE.competitors || [];
        if (!comps.length) {
            $('#competePanel').html('<div class="panel" style="background:var(--bg-panel-2);">' +
                '<div class="panel-title">🏘 街区竞争</div>' +
                '<div class="c-dim">街区目前没有竞争对手。声誉达到 ' + (cd.threshold_reputation || 40) + ' 后，对手可能会来抢生意。</div></div>');
            return;
        }
        var steal = Math.round(stealPct() * 100);
        var cooldown = (STATE.compete_cooldown_until || 0) - gameMin();
        var cards = comps.map(function (c) {
            var heat = c.closed ? 0 : Math.round(c.heat || 0);
            var status = c.closed
                ? '<span class="c-red">🏁 歇业中</span>'
                : '<span class="' + (heat <= 15 ? 'c-red' : heat >= 70 ? 'c-gold' : 'c-green') + '">热度 ' + heat + '</span>';
            return '<div class="comp-card" style="border:1px solid ' + (c.closed ? 'rgba(234,102,104,0.5)' : 'rgba(52,211,153,0.4)') + ';background:var(--bg-panel-2);border-radius:10px;padding:10px 12px;margin-bottom:8px;">' +
                '<div class="kv"><span class="k">' + (c.icon || '🏪') + ' ' + esc(c.name || c.id) + '</span><span class="v">' + status + '</span></div>' +
                (c.closed ? '' : '<div class="meter ' + (heat <= 15 ? 'red' : '') + '" style="margin-top:6px;"><div style="width:' + heat + '%;"></div></div>') +
                '<div class="c-dim" style="font-size:11px;">竞争力 ×' + (c.strength || 1) + (c.closed ? ' · 90 天后可能卷土重来' : ' · 热度越高抢客越凶') + '</div>' +
                '</div>';
        }).join('');
        var html =
            '<div class="panel" style="background:linear-gradient(135deg,var(--bg-panel-2),rgba(255,193,7,0.08));border:1px solid rgba(255,193,7,0.35);">' +
            '<div class="panel-title">🏘 街区竞争 <span class="c-dim">（你的声誉 ' + Math.round(STATE.reputation || 0) + '，客流被抢约 ' + steal + '%）</span></div>' +
            cards +
            '<div class="row-actions" style="margin-top:8px;flex-wrap:wrap;">' +
            '<button class="btn amber" id="btnPriceWar" title="price_war 命令：180 分钟定价 -10%、客流 +25%">⚔ 价格战</button>' +
            '<button class="btn cyan" id="btnAdWar" title="ad_war 命令：' + fmt(cd.ad_cost) + ' 元广告，对手热度 -8、客流 +10%">📣 营销战（' + fmt(cd.ad_cost) + ' 元）</button>' +
            '<button class="btn" id="btnCharmOff" title="charm_off 命令：' + fmt(cd.charm_cost) + ' 元 + 街坊好感 ≥60，对手热度 -5">🗣 口碑攻势（' + fmt(cd.charm_cost) + ' 元）</button>' +
            (cooldown > 0 ? '<span class="c-dim">行动冷却剩 ' + cooldown + ' 分钟</span>' : '') +
            '</div>' +
            '<div class="c-dim" style="font-size:11px;margin-top:4px;">把对手热度压到 ≤15 并持续 3 天，即可将其打到歇业（成就「街区称霸」）；歇业 90 天后可能卷土重来。</div>' +
            '</div>';
        $('#competePanel').html(html);
        $('#competePanel').off('click', '#btnPriceWar').on('click', '#btnPriceWar', function () { doAct({ action: 'price_war' }); });
        $('#competePanel').off('click', '#btnAdWar').on('click', '#btnAdWar', function () { doAct({ action: 'ad_war' }); });
        $('#competePanel').off('click', '#btnCharmOff').on('click', '#btnCharmOff', function () { doAct({ action: 'charm_off' }); });
    }
    /** 被抢客流百分比（与后端 competitionSteal 一致：0~40%） */
    function stealPct() {
        var comps = STATE.competitors || [];
        var heatSum = 0;
        comps.forEach(function (c) { if (!c.closed) heatSum += (c.heat || 0) * (c.strength || 1); });
        if (heatSum <= 0) return 0;
        var steal = heatSum / (heatSum + (STATE.reputation || 0) * 2) * 0.55;
        return Math.min(0.4, steal);
    }
    function gameMin() {
        return (STATE.minutes || 0);
    }

    function renderGameOver() {
        if (!STATE.ended) return;
        var win = STATE.end_type === 'win';
        var $box = $('#gameoverBox').removeClass('win lose');
        $box.addClass(win ? 'win' : 'lose');
        $('#goTitle').text(win ? '★ 经营成功 ★' : '☠ 破产倒闭 ☠');
        $('#goMsg').text(STATE.end_message || '');
        $('#gameover').addClass('show');
    }

    // ---------------- 操作 ----------------
    function doAct(payload, silent) {
        if (booting) return;
        var parts = SAVE_ID.split('::');
        var url = '/api/game/' + encodeURIComponent(parts[0]) + '/saves/' + encodeURIComponent(parts[1]) + '/act';
        pendingAct++;
        $.ajax({
            url: url,
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(payload),
            dataType: 'json'
        }).done(function (res) {
            pendingAct--;
            if (!res.ok) {
                logError(res.message || '操作失败');
                return;
            }
            if (res.message && !silent) logAction(res.message);
            if (res.state) {
                STATE = res.state;
                lastLiveLen = (STATE.live_log || []).length; // 操作返回后同步，避免轮询重复打印
                renderAll();
            }
            if (res.ok && payload.action === 'scratch' && res.state) {
                setTimeout(function () { openScratch(res.state.scratch_last_win); }, 150);
            }
            (res.reports || []).forEach(function (r) { renderDayReport(r); });
            if (res.report) renderDayReport(res.report);
            if (STATE.ended) {
                setTimeout(function () { renderGameOver(); }, 600);
            }
        }).fail(function (xhr) {
            pendingAct--;
            logError('网络错误：' + (xhr.status || '') + '，请重试');
        });
    }

    // ---------------- 轮询：实时推进游戏时间 ----------------
    var pollTimer = null;
    function pollState() {
        if (booting || pendingAct > 0 || (STATE && STATE.ended)) return;
        var parts = SAVE_ID.split('::');
        var url = '/api/game/' + encodeURIComponent(parts[0]) + '/saves/' + encodeURIComponent(parts[1]) + '/state';
        $.ajax({
            url: url,
            method: 'GET',
            dataType: 'json'
        }).done(function (res) {
            if (!res || !res.state || pendingAct > 0) return; // 有操作在途时以操作结果为准
            STATE = res.state;
            renderStatusbar();
            renderShopSwitcher();
            renderShopStatus();
            renderNpcPanel();
            renderBuffPanel();
            renderDebtPanel();
            renderAchievements();
            renderQuest();
            renderBank();
            appendLiveLog();
            updateCellsInPlace();
            (res.reports || []).forEach(function (r) { renderDayReport(r); });
            if (STATE.ended) renderGameOver();
        }).fail(function (xhr) {
            // 存档不存在/被删除：停止轮询并给出提示，避免持续刷错误
            if (xhr.status === 404) {
                stopPolling();
                showSaveLostOverlay();
            }
            // 其他网络错误静默，下次重试
        });
    }
    function stopPolling() {
        if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    }
    function showSaveLostOverlay() {
        if ($('#saveLostOverlay').length) return;
        $('body').append(
            '<div id="saveLostOverlay" style="position:fixed;inset:0;z-index:9999;display:flex;align-items:center;justify-content:center;' +
            'background:rgba(3,12,8,0.92);backdrop-filter:blur(4px);">' +
            '<div style="max-width:520px;width:90%;padding:30px 34px;border-radius:16px;text-align:center;' +
            'border:1px solid var(--border-bright);background:var(--bg-panel-2);box-shadow:0 0 40px rgba(255,92,92,0.15);">' +
            '<div style="font-size:44px;margin-bottom:12px;">🛑</div>' +
            '<div style="font-size:20px;font-weight:700;color:#ff8f8f;letter-spacing:2px;text-shadow:0 0 12px rgba(255,92,92,0.5);">存档已不存在</div>' +
            '<div style="margin:14px 0 20px;color:var(--fg-dim);font-size:13px;line-height:1.8;">' +
            '当前存档「' + esc(SAVE_ID) + '」已被删除或失效，实时时间流已停止。<br>返回启动器选择其他存档或重新开始一局。</div>' +
            '<a href="/" class="btn-primary" style="padding:10px 28px;text-decoration:none;">返回启动器</a>' +
            '</div></div>'
        );
    }
    function startPolling() {
        if (pollTimer) return;
        pollTimer = setInterval(pollState, 1000);
    }

    // 轮询时只更新可见单元格（库存/保质期/售价/毛利），不重建整表
    function updateCellsInPlace() {
        $('#shelfTable tbody tr[data-pid]').each(function () {
            var $tr = $(this);
            var pid = $tr.data('pid');
            var slot = STATE.inventory[pid];
            var qty = slot ? slot.qty : 0;
            var life = slot ? slot.shelf_life_remaining : 0;
            var $tds = $tr.find('td');
            $tds.eq(4).html('<span class="' + (qty <= 0 ? 'c-red' : '') + '">' + qty + '</span>');
            var lifeCls = life <= 1 ? 'c-red' : (life <= 3 ? 'c-amber' : 'c-dim');
            $tds.eq(5).html('<span class="' + lifeCls + '">' + (slot ? life + ' 天' : '-') + '</span>');
        });
        $('#priceTable tbody tr[data-pid]').each(function () {
            var $tr = $(this);
            var pid = $tr.data('pid');
            var p = productById(pid);
            if (!p) return;
            var mult = STATE.price_multiplier[pid] || 1;
            var retail = p.base_retail_price * mult;
            var margin = retail - p.wholesale_price;
            var $tds = $tr.find('td');
            $tds.eq(2).html('<span class="c-green">' + fmt(retail) + '</span>');
            $tds.eq(3).html('<span class="c-amber">' + fmt(margin) + '（' + Math.round(margin / p.wholesale_price * 100) + '%）</span>');
        });
    }

    // ---------------- 日报渲染 ----------------
    function renderDayReport(r) {
        logSep(r.day_label + (r.closed ? ' · 休息' : ' · 营业日报'));
        var w = r.weather_icon + ' ' + r.weather_name;
        var flow = [];
        Object.keys(r.period_flow || {}).forEach(function (k) {
            flow.push(k + ' ' + r.period_flow[k]);
        });
        logLine('<span class="c-cyan">☁ ' + esc(w) + '</span>　<span class="c-dim">客流量 ' + esc(fmt1(r.foot_traffic)) + '</span>　' +
            '<span class="c-green">顾客 ' + r.customers + ' 人</span>　<span class="c-dim">时段</span> ' + flow.join(' / '));
        logLine('<span class="c-b">💰 营业额 ' + esc(fmt(r.revenue)) + ' ' + CURRENCY + '</span>' +
            '　<span class="c-dim">成本 ' + esc(fmt(r.cost_of_goods)) + '</span>' +
            '　<span class="' + (r.profit >= 0 ? 'c-green' : 'c-red') + '">利润 ' + esc(fmt(r.profit)) + '</span>' +
            '　<span class="c-dim">售出 ' + r.items_sold + ' 件</span>');
        (r.events || []).forEach(function (e) { logLine('<span class="c-amber">🎈 ' + esc(e) + '</span>'); });
        (r.expiries || []).forEach(function (e) { logLine('<span class="c-red">⚠ ' + esc(e) + '</span>'); });
        if (r.interest_paid > 0) {
            logLine('<span class="c-amber">💳 贷款利息 ' + esc(fmt(r.interest_paid)) + ' ' + CURRENCY + '（计入负债）</span>');
        }
        (r.notices || []).forEach(function (n) { logLine('<span class="c-amber">◈ ' + esc(n) + '</span>'); });
        (r.npc_comments || []).forEach(function (nc) {
            var delta = nc.favor_after - nc.favor_before;
            var dCls = delta > 0 ? 'c-green' : (delta < 0 ? 'c-red' : 'c-dim');
            lastNpcDelta[nc.npc_id] = { cur: nc.favor_after, delta: delta };
            logLine('<span class="c-purple">' + nc.icon + ' ' + esc(nc.npc_name) + '：</span>' +
                '“' + esc(nc.comment) + '”　<span class="c-dim">好感</span> ' + nc.favor_before +
                ' → <span class="' + dCls + '">' + nc.favor_after + '</span> <span class="' + dCls + '">(' + (delta > 0 ? '+' : '') + delta + ')</span>');
        });
        logLine('<span class="c-dim">现金 ' + esc(fmt(r.cash_after)) + ' ｜ 负债 ' + esc(fmt(r.debt_after)) +
            ' ｜ 声誉 ' + esc(fmt1(r.reputation_after)) + ' ｜ 净资产 ' + esc(fmt(r.net_worth)) + '</span>');
        if (r.game_ended) {
            logLine('<span class="' + (r.end_type === 'win' ? 'c-green c-b' : 'c-red c-b') + '">' + esc(r.end_message) + '</span>');
        }
    }

    // ---------------- 启动动画 ----------------
    function boot() {
        var lines = [
            ['> TermSim Engine v' + ENGINE_VERSION + ' booting...', 'c-dim', 8],
            ['> 载入游戏定义 "' + DEF.name + '" v' + DEF.version + ' ... OK', 'c-dim', 6],
            ['> 载入存档 "' + STATE.save_name + '" ... OK', 'c-dim', 6],
            ['> 连接街角商铺终端 ... OK', 'c-dim', 6],
            ['> 实时时间流已就绪（现实 1 秒 = 游戏 1 分钟，可调 0.5×~5×）。', 'c-green', 8],
            ['', '', 1]
        ];
        var i = 0;
        (function next() {
            if (i >= lines.length) {
                booting = false;
                lastLiveLen = (STATE.live_log || []).length; // 旧存档实时日志不重复回放
                logLine('<span class="c-green c-b">欢迎回来，老板！</span> <span class="c-dim">输入 <span class="c-cyan">help</span> 查看命令。</span>');
                logAction('当前时间 第' + gameDay() + '天 ' + gameClock() + '，店铺营业中（06:00-24:00）。输入 help 查看命令。');
                $('#cmdInput').focus();
                startPolling();
                return;
            }
            var l = lines[i++];
            if (l[0] === '') { $('#console').append('<div class="log-line">&nbsp;</div>'); next(); return; }
            typeLine(l[0], l[1], l[2], next);
        })();
    }

    // ---------------- 命令行 ----------------
    var COMMANDS = [
        ['help', '显示帮助'],
        ['status', '查看经营状态'],
        ['inventory', '查看库存'],
        ['buy <商品> <数量>', '进货（支持 id 或名称）'],
        ['price <商品> <倍率>', '单件定价'],
        ['allprice <倍率>', '全部定价'],
        ['loan <贷款id>', '借款（loan_small / loan_growth / loan_big）'],
        ['repay <金额>', '还款'],
        ['renovate <装修id>', '装修（upg_level2~5）'],
        ['open', '查看当前时间与营业状态'],
        ['rest', '打烊快进到次日 06:00'],
        ['pause', '暂停时间流逝'],
        ['resume', '继续时间流逝'],
        ['speed <倍速>', '时间倍速（0.5 / 1 / 1.5 / 2 / 3 / 5）'],
        ['promo', '发起限时促销（客流×' + (DEF.gameplay && DEF.gameplay.promo ? DEF.gameplay.promo.traffic_mult : 1.5) + '，' + (DEF.gameplay && DEF.gameplay.promo ? DEF.gameplay.promo.duration_min : 240) + ' 分钟）'],
        ['social <街坊> <互动>', '与街坊互动（chat/gift/tea 等，见「街坊」页）'],
        ['trade', '查看贸易状态'],
        ['trade wholesale <商品> <数量>', '批发（小卖部）'],
        ['trade delivery on|off', '外卖平台开关（饭馆）'],
        ['staff', '查看雇员列表'],
        ['hire <角色>', '雇佣员工（如 hire assistant）'],
        ['raise <角色> / cut <角色>', '涨薪 / 降薪（影响效率）'],
        ['fire <角色>', '解雇员工'],
        ['bank', '查看银行存款与利息'],
        ['deposit <金额>', '存款（每日复利计息）'],
        ['withdraw <金额>', '取款'],
        ['lottery', '买一张幸运彩票（次日开奖）'],
        ['scratch', '买一张刮刮乐（即买即刮，不限次数）'],
        ['finance', '查看理财产品与持仓'],
        ['financebuy <产品id>', '买入理财产品'],
        ['financeredeem <序号>', '提前赎回理财（只退本金）'],
        ['save', '手动存档'],
        ['quest', '查看 NPC 委托'],
        ['map', '查看地图地产与房贷'],
        ['housebuy <地产id>', '申请房贷购买新地产并迁移'],
        ['wardrobe', '查看个人装扮与声望'],
        ['buyitem <装扮id>', '购买装扮单品（购买即上身）'],
        ['wear <装扮id>', '换装已购装扮'],
        ['buyasset <资产id>', '购买车/住宅等资产（加声望）'],
        ['report', '查看报表'],
        ['supply', '查看供应商合作与赊账'],
        ['buy_credit <商品> <数量>', '赊账进货（金牌客户以上）'],
        ['compete', '查看街区竞争'],
        ['price_war', '价格战：180 分钟定价 -10%、客流 +25%'],
        ['ad_war', '营销战：投放广告压低对手热度'],
        ['charm_off', '口碑攻势：借街坊好评压制对手'],
        ['npc', '查看街坊好感'],
        ['achieve', '查看成就列表'],
        ['festival', '查看节日庆典状态'],
        ['festival_setup <金额>', '投入节日筹备金（开幕前 1 天窗口或进行中）'],
        ['promote <员工id>', '晋升满级高忠诚员工为骨干'],
        ['pick_skill <员工id> <技能id>', '为骨干选择晋升技能'],
        ['redpacket <员工id>', '发红包提升员工忠诚度'],
        ['clear', '清屏']
    ];

    function execCommand(raw) {
        var args = raw.trim().split(/\s+/);
        var cmd = args[0].toLowerCase();
        switch (cmd) {
            case 'help':
                COMMANDS.forEach(function (c) {
                    logLine('<span class="c-cyan">' + esc(c[0].padEnd(26)) + '</span><span class="c-dim">' + esc(c[1]) + '</span>');
                });
                break;
            case 'status': case 'st':
                logLine(esc('现金 ' + fmt(STATE.cash) + ' ｜ 负债 ' + fmt(STATE.debt) + ' ｜ 净资产 ' + fmt(netWorth()) +
                    ' ｜ 声誉 ' + fmt1(STATE.reputation) + ' ｜ 第' + gameDay() + '天 ' + gameClock() +
                    (STATE.paused ? ' ｜ ⏸ 已暂停' : ' ｜ ▶ 时间流逝中')));
                break;
            case 'inventory': case 'inv':
                var any = false;
                Object.keys(STATE.inventory || {}).forEach(function (pid) {
                    var slot = STATE.inventory[pid];
                    if (slot.qty > 0) {
                        var p = productById(pid);
                        if (p) { any = true; logLine(esc(p.icon + ' ' + p.name + ' ×' + slot.qty + '（保质期剩 ' + slot.shelf_life_remaining + ' 天）')); }
                    }
                });
                if (!any) logText('货架空空如也，快去进货吧。', 'c-amber');
                break;
            case 'buy':
                if (args.length < 3) { logError('用法：buy <商品> <数量>'); break; }
                doAct({ action: 'buy', product_id: findProduct(args[1]), qty: parseInt(args[2], 10) });
                break;
            case 'price':
                if (args.length < 3) { logError('用法：price <商品> <倍率>'); break; }
                doAct({ action: 'price', product_id: findProduct(args[1]), multiplier: parseFloat(args[2]) });
                break;
            case 'allprice':
                if (args.length < 2) { logError('用法：allprice <倍率>'); break; }
                doAct({ action: 'all_price', multiplier: parseFloat(args[1]) });
                break;
            case 'loan':
                if (args.length < 2) { logError('用法：loan <贷款id>'); break; }
                doAct({ action: 'loan', loan_id: args[1] });
                break;
            case 'repay':
                if (args.length < 2) { logError('用法：repay <金额>'); break; }
                doAct({ action: 'repay', amount: parseFloat(args[1]) });
                break;
            case 'renovate':
                if (args.length < 2) { logError('用法：renovate <装修id>'); break; }
                doAct({ action: 'renovate', upgrade_id: args[1] });
                break;
            case 'open':
                doAct({ action: 'open' });
                break;
            case 'rest':
                doAct({ action: 'rest' });
                break;
            case 'pause':
                doAct({ action: 'pause' });
                break;
            case 'resume':
                doAct({ action: 'resume' });
                break;
            case 'speed':
                if (args.length < 2) { logError('用法：speed <倍速>（0.5 / 1 / 1.5 / 2 / 3 / 5）'); break; }
                doAct({ action: 'speed', multiplier: parseFloat(args[1]) });
                break;
            case 'promo':
                doAct({ action: 'promo' });
                break;
            case 'social':
                if (args.length < 3) { logError('用法：social <街坊id> <互动>（如 social npc_wang chat）'); break; }
                doAct({ action: 'social', npc_id: args[1], subaction: args[2] });
                break;
            case 'trade':
                if (args.length === 1) {
                    var tg = (DEF.gameplay && DEF.gameplay.trade) || {};
                    if (tg.wholesale && tg.wholesale.enabled) {
                        logLine('🏷 批发商：' + esc(tg.wholesale.broker_name) + '（收购价 ×' + tg.wholesale.price_ratio +
                            '，' + tg.wholesale.min_qty + ' 件起批，冷却 ' + tg.wholesale.cooldown_min + ' 分钟）');
                    } else if (tg.delivery && tg.delivery.enabled) {
                        logLine('🛵 外卖平台：' + (STATE.delivery_open ? '运营中' : '已关闭') +
                            '（今日 ' + (STATE.delivery_today_orders || 0) + ' 单 / 营收 ' + fmt(STATE.delivery_today_revenue || 0) +
                            '，累计 ' + (STATE.delivery_total_orders || 0) + ' 单 / ' + fmt(STATE.delivery_total_revenue || 0) + '）');
                    } else {
                        logError('本游戏未配置贸易玩法');
                    }
                    break;
                }
                if (args[1] === 'wholesale') {
                    if (args.length < 4) { logError('用法：trade wholesale <商品> <数量>'); break; }
                    var pid2 = findProduct(args[2]);
                    if (pid2) doAct({ action: 'trade', subaction: 'wholesale', product_id: pid2, qty: parseInt(args[3], 10) });
                } else if (args[1] === 'delivery') {
                    doAct({ action: 'trade', subaction: 'delivery', product_id: args[2] === 'on' ? 'on' : 'off' });
                } else {
                    logError('用法：trade wholesale <商品> <数量> 或 trade delivery on|off');
                }
                break;
            case 'staff':
                var scfg = (DEF.gameplay && DEF.gameplay.staff);
                if (!scfg || !scfg.roles || !Object.keys(scfg.roles).length) { logError('本游戏未配置可雇佣角色'); break; }
                Object.keys(scfg.roles).forEach(function (rid) {
                    var role = scfg.roles[rid];
                    var st = STATE.staff && STATE.staff[rid];
                    if (st) {
                        logLine(esc((role.icon || '🧑') + ' ' + role.name + ' 在职：月薪 ' + fmt(st.salary) +
                            '，效率 ' + staffEffText(st) + (st.cut_level >= 2 ? '（可能离职）' : '')));
                    } else {
                        logLine(esc((role.icon || '🧑') + ' ' + role.name + ' 未雇佣：月薪 ' + fmt(role.salary) +
                            '（' + role.role + '）'));
                    }
                });
                logLine('<span class="c-dim">发薪周期 ' + scfg.pay_interval_days + ' 天/次' +
                    (STATE.payroll_debt_days > 0 ? '；已欠薪 ' + STATE.payroll_debt_days + ' 期' : '') + '</span>');
                break;
            case 'hire':
                if (args.length < 2) { logError('用法：hire <角色id>（如 hire assistant）'); break; }
                doAct({ action: 'hire', staff_id: args[1] });
                break;
            case 'raise': case 'cut':
                if (args.length < 2) { logError('用法：' + cmd + ' <角色id>'); break; }
                doAct({ action: cmd, staff_id: args[1] });
                break;
            case 'fire':
                if (args.length < 2) { logError('用法：fire <角色id>'); break; }
                doAct({ action: 'fire', staff_id: args[1] });
                break;
            case 'bank':
                logLine(esc('银行存款 ' + fmt(STATE.bank_balance || 0) + ' ｜ 累计利息 ' + fmt(STATE.bank_interest_earned || 0)
                    + ' ｜ 年化利率 ' + (DEF.meta.bank_annual_rate * 100).toFixed(1) + '%'
                    + '（每日复利；现金放店里可能被小偷偷，存银行更稳）'));
                break;
            case 'deposit':
                if (args.length < 2 || isNaN(parseFloat(args[1])) || parseFloat(args[1]) <= 0) {
                    logError('用法：deposit <金额>'); break;
                }
                doAct({ action: 'deposit', amount: parseFloat(args[1]) });
                break;
            case 'withdraw':
                if (args.length < 2 || isNaN(parseFloat(args[1])) || parseFloat(args[1]) <= 0) {
                    logError('用法：withdraw <金额>'); break;
                }
                doAct({ action: 'withdraw', amount: parseFloat(args[1]) });
                break;
            case 'lottery':
                doAct({ action: 'lottery' });
                break;
            case 'scratch':
                doAct({ action: 'scratch' });
                break;
            case 'finance':
                doAct({ action: 'finance' });
                break;
            case 'financebuy':
                if (args.length < 2) { logError('用法：financebuy <产品id>（finance 查看）'); break; }
                doAct({ action: 'financebuy', staff_id: args[1] });
                break;
            case 'financeredeem':
                if (args.length < 2) { logError('用法：financeredeem <序号>（finance 查看）'); break; }
                doAct({ action: 'financeredeem', staff_id: args[1] });
                break;
            case 'save':
                saveGameNow();
                break;
            case 'quest':
                if (STATE.active_quest) {
                    var q = STATE.active_quest;
                    logLine(esc('📋 ' + q.flavor + '（截止：第 ' + q.deadline_day + ' 天）'));
                } else {
                    logLine('<span class="c-dim">当前没有进行中的委托。每日清晨街坊可能会来下单。</span>');
                }
                break;
            case 'open_branch':
                if (args.length < 2) { logError('用法：open_branch <店名> [选址id]（map 查看选址）'); break; }
                doAct({ action: 'open_branch', product_id: args[1], npc_id: args[2] || '' });
                break;
            case 'switch_shop':
                if (args.length < 2) { logError('用法：switch_shop <店铺id>（如 main / branch_1）'); break; }
                doAct({ action: 'switch_shop', npc_id: args[1] });
                break;
            case 'map':
                doAct({ action: 'map' });
                break;
            case 'housebuy':
                if (args.length < 2) { logError('用法：housebuy <地产id>（map 查看）'); break; }
                doAct({ action: 'housebuy', staff_id: args[1] });
                break;
            case 'wardrobe':
                doAct({ action: 'wardrobe' });
                break;
            case 'buyitem':
                if (args.length < 2) { logError('用法：buyitem <装扮id>'); break; }
                doAct({ action: 'buyitem', staff_id: args[1] });
                break;
            case 'wear':
                if (args.length < 2) { logError('用法：wear <装扮id>'); break; }
                doAct({ action: 'wear', staff_id: args[1] });
                break;
            case 'buyasset':
                if (args.length < 2) { logError('用法：buyasset <资产id>'); break; }
                doAct({ action: 'buyasset', staff_id: args[1] });
                break;
            case 'festival':
                doAct({ action: 'festival' });
                break;
            case 'festival_setup':
                if (args.length < 2) { logError('用法：festival_setup <金额>'); break; }
                doAct({ action: 'festival_setup', amount: parseFloat(args[1]) });
                break;
            case 'promote':
                if (args.length < 2) { logError('用法：promote <员工id>'); break; }
                doAct({ action: 'promote', staff_id: args[1] });
                break;
            case 'pick_skill':
                if (args.length < 3) { logError('用法：pick_skill <员工id> <技能id>'); break; }
                doAct({ action: 'pick_skill', staff_id: args[1], skill_id: args[2] });
                break;
            case 'redpacket':
                if (args.length < 2) { logError('用法：redpacket <员工id>'); break; }
                doAct({ action: 'redpacket', staff_id: args[1] });
                break;
            case 'achieve':
                (DEF.achievements || []).forEach(function (a) {
                    var done = (STATE.achievements || {})[a.id];
                    logLine(esc((done ? a.icon + ' ' + a.name + ' ✓' : '🔒 ' + a.name))
                        + ' <span class="c-dim">' + esc(a.desc) + '</span>');
                });
                break;
            case 'report':
                logLine(esc('总营业额 ' + fmt(STATE.stats.total_revenue) + ' ｜ 总利润 ' + fmt(STATE.stats.total_profit) +
                    ' ｜ 目标 ' + fmt(DEF.win.target_net_worth) + '（第 ' + DEF.win.by_year + ' 年） ｜ 当前净资产 ' + fmt(netWorth())));
                break;
            case 'supply':
                doAct({ action: 'supply' });
                break;
            case 'buy_credit':
                if (args.length < 3) { logError('用法：buy_credit <商品> <数量>'); break; }
                doAct({ action: 'buy_credit', product_id: findProduct(args[1]), qty: parseInt(args[2], 10) });
                break;
            case 'compete':
                doAct({ action: 'compete' });
                break;
            case 'price_war':
                doAct({ action: 'price_war' });
                break;
            case 'ad_war':
                doAct({ action: 'ad_war' });
                break;
            case 'charm_off':
                doAct({ action: 'charm_off' });
                break;
            case 'npc':
                (DEF.npcs || []).forEach(function (npc) {
                    var ns = STATE.npcs[npc.id] || {};
                    var tier = favorTier(ns.favorability);
                    logLine(esc(npc.icon + ' ' + npc.name + '（' + npc.title + '） 好感 ' + Math.round(ns.favorability) + ' [' + tier.name + '] 来访 ' + (ns.visits || 0) + ' 次'));
                    if (ns.last_comment) logLine('<span class="c-dim">　最近评价：' + esc(ns.last_comment) + '</span>');
                });
                break;
            case 'clear':
                $('#console').empty();
                break;
            default:
                logError('未知命令：' + cmd + '（输入 help 查看帮助）');
        }
    }

    function findProduct(key) {
        var k = String(key || '').trim().toLowerCase();
        var p = productById(k);
        if (p) return p.id;
        var hit = (DEF.products || []).filter(function (x) {
            return x.name.indexOf(k) >= 0 || x.id.toLowerCase().indexOf(k) >= 0;
        })[0];
        if (hit) return hit.id;
        logError('未找到商品：' + key);
        return null;
    }

    // ---------------- 初始化 ----------------
    $(function () {
        // 顶栏时钟（HH:MM:SS，不显示年月日）
        setInterval(function () {
            var d = new Date();
            $('#clock').text('[' + pad2(d.getHours()) + ':' + pad2(d.getMinutes()) + ':' + pad2(d.getSeconds()) + ']');
        }, 1000);

        // 标签页
        $('.tab').on('click', function () {
            $('.tab').removeClass('active');
            $('.tab-pane').removeClass('active');
            $(this).addClass('active');
            $('#pane-' + $(this).data('tab')).addClass('active');
        });

        // 快捷按钮
        // 智能补货：多档位（20/50/100/200/300），对所有已解锁商品无条件各补所选档位（不要求库存为 0）
        function doAutoStock(qty) {
            var targets = [];
            (DEF.products || []).forEach(function (p) {
                if (!unlocked(p)) return;
                targets.push(p.id);
            });
            if (!targets.length) { logText('暂无可进货的已解锁商品。', 'c-amber'); return; }
            var cost = 0;
            targets.forEach(function (pid) {
                var p = productById(pid);
                cost += p.wholesale_price * qty;
            });
            if (cost > STATE.cash) {
                logError('现金不足，按各补 ' + qty + ' 件计算需约 ' + fmt(cost) + ' ' + CURRENCY + '，当前 ' + fmt(STATE.cash));
                return;
            }
            var k = 0;
            var doOne = function () {
                if (k >= targets.length) { renderAll(); return; }
                doAct({ action: 'buy', product_id: targets[k++], qty: qty }, true);
                setTimeout(doOne, 120);
            };
            doOne();
        }
        $('.auto-stock').on('click', function () {
            doAutoStock(parseInt($(this).data('qty'), 10) || 20);
        });
        $('#btnDiscard').on('click', function () {
            var cnt = 0;
            Object.keys(STATE.inventory || {}).forEach(function (pid) {
                var slot = STATE.inventory[pid];
                if (slot.shelf_life_remaining <= 1) { cnt += slot.qty; slot.qty = 0; }
            });
            renderAll();
            logAction(cnt > 0 ? '已丢弃临期商品 ' + cnt + ' 件。' : '没有需要丢弃的临期商品。');
        });
        $('#btnPromo').on('click', function () {
            doAct({ action: 'promo' });
        });

        // 批量定价
        $('.price-preset').on('click', function () {
            doAct({ action: 'all_price', multiplier: parseFloat($(this).data('mult')) });
        });

        // 命令行
        $('#cmdInput').on('keydown', function (e) {
            if (e.key === 'Enter') {
                var raw = $(this).val().trim();
                if (!raw) return;
                logLine('<span class="c-amber">$ ' + esc(raw) + '</span>');
                cmdHistory.push(raw);
                cmdIndex = cmdHistory.length;
                $(this).val('');
                execCommand(raw);
                $('#cmdHint').text('');
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                if (cmdIndex > 0) { cmdIndex--; $(this).val(cmdHistory[cmdIndex]); }
            } else if (e.key === 'ArrowDown') {
                e.preventDefault();
                if (cmdIndex < cmdHistory.length - 1) { cmdIndex++; $(this).val(cmdHistory[cmdIndex]); }
                else { cmdIndex = cmdHistory.length; $(this).val(''); }
            }
        });

        renderAll();
        boot();
    });
})(jQuery);
