package com.example.model;

import java.time.LocalDate;

public class ReceivedBatch {
    private Integer itemId;          // optional if itemName provided
    private String itemName;         // optional if itemId provided
    private String batchNo;
    private LocalDate expiryDate;
    private int qty;
    private double purchasePrice;
    private double sellPrice;
    private String location;

    public ReceivedBatch() {}

    public ReceivedBatch(Integer itemId, String itemName, String batchNo, LocalDate expiryDate,
                         int qty, double purchasePrice, double sellPrice, String location) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.batchNo = batchNo;
        this.expiryDate = expiryDate;
        this.qty = qty;
        this.purchasePrice = purchasePrice;
        this.sellPrice = sellPrice;
        this.location = location;
    }

    public Integer getItemId() { return itemId; }
    public void setItemId(Integer itemId) { this.itemId = itemId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public double getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(double purchasePrice) { this.purchasePrice = purchasePrice; }

    public double getSellPrice() { return sellPrice; }
    public void setSellPrice(double sellPrice) { this.sellPrice = sellPrice; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
