package com.termgame.engine;

import com.termgame.model.state.DayReport;
import com.termgame.model.state.GameState;

import java.util.List;

/**
 * 操作响应。
 */
public class ActionResult {

    private boolean ok;
    private String message;
    private GameState state;
    private DayReport report;
    private List<DayReport> reports;

    public static ActionResult ok(String message, GameState state, DayReport report) {
        ActionResult r = new ActionResult();
        r.ok = true;
        r.message = message;
        r.state = state;
        r.report = report;
        return r;
    }

    public static ActionResult ok(String message, GameState state, List<DayReport> reports) {
        ActionResult r = new ActionResult();
        r.ok = true;
        r.message = message;
        r.state = state;
        r.reports = reports;
        return r;
    }

    public static ActionResult error(String message) {
        ActionResult r = new ActionResult();
        r.ok = false;
        r.message = message;
        return r;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public DayReport getReport() {
        return report;
    }

    public void setReport(DayReport report) {
        this.report = report;
    }

    public List<DayReport> getReports() {
        return reports;
    }

    public void setReports(List<DayReport> reports) {
        this.reports = reports;
    }
}
