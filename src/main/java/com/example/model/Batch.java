package com.example.model;

import java.time.LocalDate;

public class Batch {
    private int id;
    private int itemId;
    private String batchNo;
    private LocalDate expiryDate; // null means no expiry
    private int qtyOnHand;
    private double purchasePrice;
    private double sellPrice;
    private String location;

    public Batch() {}

    public Batch(int id, int itemId, String batchNo, LocalDate expiryDate, int qtyOnHand,
                 double purchasePrice, double sellPrice, String location) {
        this.id = id;
        this.itemId = itemId;
        this.batchNo = batchNo;
        this.expiryDate = expiryDate;
        this.qtyOnHand = qtyOnHand;
        this.purchasePrice = purchasePrice;
        this.sellPrice = sellPrice;
        this.location = location;
    }

    public int getId() { return id; }
    public int getItemId() { return itemId; }
    public String getBatchNo() { return batchNo; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public int getQtyOnHand() { return qtyOnHand; }
    public double getPurchasePrice() { return purchasePrice; }
    public double getSellPrice() { return sellPrice; }
    public String getLocation() { return location; }

    public void setId(int id) { this.id = id; }
    public void setItemId(int itemId) { this.itemId = itemId; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public void setQtyOnHand(int qtyOnHand) { this.qtyOnHand = qtyOnHand; }
    public void setPurchasePrice(double purchasePrice) { this.purchasePrice = purchasePrice; }
    public void setSellPrice(double sellPrice) { this.sellPrice = sellPrice; }
    public void setLocation(String location) { this.location = location; }
}
