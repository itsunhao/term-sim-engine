package com.termgame.engine;

import com.termgame.model.state.GameState;

/**
 * 前端操作请求。
 */
public class ActionRequest {

    private String action;       // buy / price / allPrice / loan / repay / renovate / open / rest / promo / social / trade
    private String productId;
    private Integer qty;
    private Double multiplier;
    private Double amount;
    private String loanId;
    private String upgradeId;
    private String subaction;    // social: chat/gift/tea...; trade: wholesale/delivery
    private String npcId;        // social 目标 NPC
    private String staffId;      // hire/fire/raise/cut 目标员工

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public Double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(Double multiplier) {
        this.multiplier = multiplier;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public String getUpgradeId() {
        return upgradeId;
    }

    public void setUpgradeId(String upgradeId) {
        this.upgradeId = upgradeId;
    }

    public String getSubaction() {
        return subaction;
    }

    public void setSubaction(String subaction) {
        this.subaction = subaction;
    }

    public String getNpcId() {
        return npcId;
    }

    public void setNpcId(String npcId) {
        this.npcId = npcId;
    }

    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        this.staffId = staffId;
    }
}
