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
		public double initialCash;
		
		public AgentState(String name, List<Order> lastOrders, double positionUnits, double cashBalance, double initialCash) {
			this.name = name;
			this.lastOrders = lastOrders;
			this.positionUnits = positionUnits;
			this.cashBalance = cashBalance;
			this.initialCash = initialCash;
		}
	}
	
	public static class MarketConfig {
		public double totalAssetUnits;
		public double totalCash;
		public double fundingRate;
		public double dividendRate;
		public double currentTotalAssets;
		public double currentTotalCash;
		public java.util.Map<String, Double> initialPositions;
		
		public MarketConfig(double totalAssetUnits, double totalCash, double fundingRate, double dividendRate, double currentTotalAssets, double currentTotalCash, java.util.Map<String, Double> initialPositions) {
			this.totalAssetUnits = totalAssetUnits;
			this.totalCash = totalCash;
			this.fundingRate = fundingRate;
			this.dividendRate = dividendRate;
			this.currentTotalAssets = currentTotalAssets;
			this.currentTotalCash = currentTotalCash;
			this.initialPositions = initialPositions;
		}
	}
}
