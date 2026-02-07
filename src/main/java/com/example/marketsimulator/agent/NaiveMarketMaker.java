package com.example.marketsimulator.agent;

import com.example.marketsimulator.model.Market;
import com.example.marketsimulator.model.Order;
import java.util.List;

public class NaiveMarketMaker extends MarketMaker {
	private final double spread;
	
	public NaiveMarketMaker(String name, double spread) {
		super(name);
		this.spread = spread;
	}
	
	@Override
	public List<Order> decideAction(Market market) {
		double mid = market.getPrice();
		return List.of(
		        new Order(name, mid - spread / 2, 10, Order.Type.BUY), new Order(name, mid + spread / 2, 10, Order.Type.SELL));
	}
}
