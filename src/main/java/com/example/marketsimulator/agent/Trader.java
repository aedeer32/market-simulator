package com.example.marketsimulator.agent;

import com.example.marketsimulator.model.Market;
import com.example.marketsimulator.model.Order;
import java.util.List;

public abstract class Trader extends Agent {
	public Trader(String name) {
		super(name);
	}
	
	@Override
	public abstract List<Order> decideAction(Market market);
}
