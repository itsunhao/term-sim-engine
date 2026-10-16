package com.termgame.model.state;

/**
 * NPC 委托订单（进行中）。
 * productId: 订购商品；qty: 数量；reward: 报酬
 * deadlineDay: 截止全局天数（到期结算判定）
 * npcId: 发布委托的 NPC（完成后好感 +5）
 * flavor: 委托描述
 */
public class QuestState {

    private String productId;
    private int qty;
    private double reward;
    private int deadlineDay;
    private String npcId;
    private String flavor;

    public QuestState() {
    }

    public QuestState(String productId, int qty, double reward, int deadlineDay, String npcId, String flavor) {
        this.productId = productId;
        this.qty = qty;
        this.reward = reward;
        this.deadlineDay = deadlineDay;
        this.npcId = npcId;
        this.flavor = flavor;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public double getReward() {
        return reward;
    }

    public void setReward(double reward) {
        this.reward = reward;
    }

    public int getDeadlineDay() {
        return deadlineDay;
    }

    public void setDeadlineDay(int deadlineDay) {
        this.deadlineDay = deadlineDay;
    }

    public String getNpcId() {
        return npcId;
    }

    public void setNpcId(String npcId) {
        this.npcId = npcId;
    }

    public String getFlavor() {
        return flavor;
    }

    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }
}
