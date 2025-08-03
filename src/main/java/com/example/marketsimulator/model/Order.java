package com.example.marketsimulator.model;

public class Order {
    public enum Type {BUY, SELL}
    public final String agentName;
    public final double price;
    public final int quantity;
    public final Type type;

    public Order(String agentName, double price, int quantity, Type type) {
        this.agentName = agentName;
        this.price = price;
        this.quantity = quantity;
        this.type = type;
    }
}
