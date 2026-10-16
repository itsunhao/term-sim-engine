package com.termgame.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 库存槽位：某商品的数量与剩余保质期。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventorySlot {

    private String productId;
    private int qty;
    private int shelfLifeRemaining;

    public InventorySlot() {
    }

    public InventorySlot(String productId, int qty, int shelfLifeRemaining) {
        this.productId = productId;
        this.qty = qty;
        this.shelfLifeRemaining = shelfLifeRemaining;
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

    public int getShelfLifeRemaining() {
        return shelfLifeRemaining;
    }

    public void setShelfLifeRemaining(int shelfLifeRemaining) {
        this.shelfLifeRemaining = shelfLifeRemaining;
    }
}
