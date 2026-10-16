package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * NPC 单次评价记录。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NpcComment {

    private String npcId;
    private String npcName;
    private String icon;
    private String period;
    private int favorBefore;
    private int favorAfter;
    private String comment;

    public NpcComment() {
    }

    public String getNpcId() {
        return npcId;
    }

    public void setNpcId(String npcId) {
        this.npcId = npcId;
    }

    public String getNpcName() {
        return npcName;
    }

    public void setNpcName(String npcName) {
        this.npcName = npcName;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public int getFavorBefore() {
        return favorBefore;
    }

    public void setFavorBefore(int favorBefore) {
        this.favorBefore = favorBefore;
    }

    public int getFavorAfter() {
        return favorAfter;
    }

    public void setFavorAfter(int favorAfter) {
        this.favorAfter = favorAfter;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
