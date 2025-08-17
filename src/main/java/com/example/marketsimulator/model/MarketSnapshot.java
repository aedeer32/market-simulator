package com.example.marketsimulator.model;

import java.util.List;

public class MarketSnapshot {
  public double price;
  public List<AgentState> agents;

  public MarketSnapshot(double price, List<AgentState> agents) {
    this.price = price;
    this.agents = agents;
  }

  public static class AgentState {
    public String name;
    public List<Order> lastOrders;

    public AgentState(String name, List<Order> lastOrders) {
      this.name = name;
      this.lastOrders = lastOrders;
    }
  }
}
