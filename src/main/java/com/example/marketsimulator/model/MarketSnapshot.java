package com.example.marketsimulator.model;

import java.util.List;

public class MarketSnapshot {
	public double price;
	public List<AgentState> agents;
	public MarketConfig config;
	
	public MarketSnapshot(double price, List<AgentState> agents, MarketConfig config) {
		this.price = price;
		this.agents = agents;
		this.config = config;
	}
	
	public static class AgentState {
		public String name;
		public List<Order> lastOrders;
		public double positionUnits;
		public double cashBalance;
		
		public AgentState(String name, List<Order> lastOrders, double positionUnits, double cashBalance) {
			this.name = name;
			this.lastOrders = lastOrders;
			this.positionUnits = positionUnits;
			this.cashBalance = cashBalance;
		}
	}
	
	public static class MarketConfig {
		public double totalAssetUnits;
		public double totalCash;
		public java.util.Map<String, Double> initialPositions;
		
		public MarketConfig(double totalAssetUnits, double totalCash, java.util.Map<String, Double> initialPositions) {
			this.totalAssetUnits = totalAssetUnits;
			this.totalCash = totalCash;
			this.initialPositions = initialPositions;
		}
	}
}
