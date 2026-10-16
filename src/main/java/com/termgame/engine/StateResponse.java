package com.termgame.engine;

import com.termgame.model.state.DayReport;
import com.termgame.model.state.GameState;

import java.util.List;

/**
 * 状态轮询响应：最新游戏状态 + 本次推进产生的日报。
 */
public class StateResponse {

    private GameState state;
    private List<DayReport> reports;

    public StateResponse() {
    }

    public StateResponse(GameState state, List<DayReport> reports) {
        this.state = state;
        this.reports = reports;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public List<DayReport> getReports() {
        return reports;
    }

    public void setReports(List<DayReport> reports) {
        this.reports = reports;
    }
}
