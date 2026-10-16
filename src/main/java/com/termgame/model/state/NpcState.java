package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * NPC 关系状态。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NpcState {

    private double favorability;   // 好感度 0-100
    private int visits;            // 累计来访次数
    private int lastVisitDay;      // 最近来访的全局天数
    private String lastComment;    // 最近一次评价

    public NpcState() {
    }

    public double getFavorability() {
        return favorability;
    }

    public void setFavorability(double favorability) {
        this.favorability = favorability;
    }

    public int getVisits() {
        return visits;
    }

    public void setVisits(int visits) {
        this.visits = visits;
    }

    public int getLastVisitDay() {
        return lastVisitDay;
    }

    public void setLastVisitDay(int lastVisitDay) {
        this.lastVisitDay = lastVisitDay;
    }

    public String getLastComment() {
        return lastComment;
    }

    public void setLastComment(String lastComment) {
        this.lastComment = lastComment;
    }
}
