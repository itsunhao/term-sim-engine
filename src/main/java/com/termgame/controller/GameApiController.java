package com.termgame.controller;

import com.termgame.engine.ActionRequest;
import com.termgame.engine.ActionResult;
import com.termgame.engine.GameManager;
import com.termgame.engine.GameEngine;
import com.termgame.engine.StateResponse;
import com.termgame.model.def.GameDefinition;
import com.termgame.model.state.DayReport;
import com.termgame.model.state.GameState;
import com.termgame.repo.GameCatalog;
import com.termgame.util.GameException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏操作 API。所有操作自动存档。
 *
 * 实时时间模型：/state 与 /act 都会先按现实时间推进游戏时间（tick），
 * 返回本次推进中产生的日报列表。读改写使用进程内锁串行化，避免轮询与操作竞态。
 */
@RestController
@RequestMapping("/api/game/{gameId}/saves/{saveName}")
public class GameApiController {

    private static final Object LOCK = new Object();

    private final GameManager manager;
    private final GameCatalog catalog;
    private final GameEngine engine;

    public GameApiController(GameManager manager, GameCatalog catalog, GameEngine engine) {
        this.manager = manager;
        this.catalog = catalog;
        this.engine = engine;
    }

    @GetMapping("/state")
    public StateResponse state(@PathVariable String gameId, @PathVariable String saveName) {
        synchronized (LOCK) {
            GameState state;
            try {
                state = manager.load(gameId, saveName);
            } catch (GameException e) {
                // 存档不存在/失效：返回 404，前端据此停止轮询并提示，而不是持续报错
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, e.getMessage());
            }
            GameDefinition def = catalog.get(state.getGameId());
            List<DayReport> reports = engine.tick(def, state, System.currentTimeMillis());
            manager.save(state);
            return new StateResponse(state, reports);
        }
    }

    @PostMapping("/act")
    public ActionResult act(@PathVariable String gameId, @PathVariable String saveName,
                            @RequestBody ActionRequest req) {
        synchronized (LOCK) {
            GameState state = manager.load(gameId, saveName);
            GameDefinition def = catalog.get(state.getGameId());
            try {
                // 先按现实时间推进
                List<DayReport> reports = new ArrayList<>(engine.tick(def, state, System.currentTimeMillis()));
                String msg;
                String action = req.getAction();
                if ("rest".equals(action)) {
                    DayReport r = engine.rest(def, state);
                    reports.add(r);
                    msg = "已打烊休息，快进到次日 06:00。";
                } else {
                    msg = engine.prepareAct(def, state, action, req.getProductId(), req.getQty(),
                            req.getMultiplier(), req.getAmount(), req.getLoanId(), req.getUpgradeId(),
                            req.getSubaction(), req.getStaffId() != null ? req.getStaffId() : req.getNpcId());
                }
                manager.save(state);
                return ActionResult.ok(msg, state, reports);
            } catch (GameException e) {
                return ActionResult.error(e.getMessage());
            } catch (Exception e) {
                return ActionResult.error("操作失败：" + e.getMessage());
            }
        }
    }

    @PostMapping("/save")
    public ActionResult save(@PathVariable String gameId, @PathVariable String saveName) {
        synchronized (LOCK) {
            GameState state = manager.load(gameId, saveName);
            GameDefinition def = catalog.get(state.getGameId());
            // 先推进时间再存档，保证「随时存档」落盘的是最新进度
            engine.tick(def, state, System.currentTimeMillis());
            manager.save(state);
            return ActionResult.ok("💾 已手动存档（" + state.clockText() + "）", state, (java.util.List<DayReport>) null);
        }
    }

    @PostMapping("/delete")
    public ActionResult delete(@PathVariable String gameId, @PathVariable String saveName) {
        try {
            manager.delete(gameId, saveName);
            return ActionResult.ok("存档已删除", null, (java.util.List<DayReport>) null);
        } catch (GameException e) {
            return ActionResult.error(e.getMessage());
        }
    }
}
