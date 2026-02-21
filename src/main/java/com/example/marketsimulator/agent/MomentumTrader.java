package com.example.marketsimulator.agent;

import com.example.marketsimulator.model.Market;
import com.example.marketsimulator.model.Order;
import java.util.List;

public class MomentumTrader extends Trader {
	private Double lastPrice = null;
	
	public MomentumTrader(String name) {
		super(name);
	}
	
	@Override
	public List<Order> decideAction(Market market) {
		double current = market.getPrice();
		Order.Type type;
		if (lastPrice == null) {
			type = Order.Type.BUY;
		} else if (current > lastPrice) {
			type = Order.Type.BUY;
		} else if (current < lastPrice) {
			type = Order.Type.SELL;
		} else {
			type = Order.Type.BUY;
		}
		lastPrice = current;
		return List.of(new Order(name, current, 10, type));
	}
}
