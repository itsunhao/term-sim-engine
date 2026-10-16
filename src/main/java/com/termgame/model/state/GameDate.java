package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 游戏内日期。一年 = 四季 × daysPerSeason。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameDate {

    private int year;
    private String seasonId;
    private int day;
    private int dayNumber; // 全局天数（从 1 起）

    public GameDate() {
    }

    public GameDate(int year, String seasonId, int day, int dayNumber) {
        this.year = year;
        this.seasonId = seasonId;
        this.day = day;
        this.dayNumber = dayNumber;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(String seasonId) {
        this.seasonId = seasonId;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(int dayNumber) {
        this.dayNumber = dayNumber;
    }
}
