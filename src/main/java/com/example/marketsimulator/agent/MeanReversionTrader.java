package com.example.marketsimulator.agent;

import com.example.marketsimulator.model.Market;
import com.example.marketsimulator.model.Order;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class MeanReversionTrader extends Trader {
	private final Deque<Double> window = new ArrayDeque<>();
	private final int windowSize;
	private double sum = 0.0;
	
	public MeanReversionTrader(String name) {
		this(name, 5);
	}
	
	public MeanReversionTrader(String name, int windowSize) {
		super(name);
		this.windowSize = Math.max(1, windowSize);
	}
	
	@Override
	public List<Order> decideAction(Market market) {
		double current = market.getPrice();
		window.addLast(current);
		sum += current;
		if (window.size() > windowSize) {
			sum -= window.removeFirst();
		}
		double avg = sum / window.size();
		Order.Type type = current > avg ? Order.Type.SELL : Order.Type.BUY;
		return List.of(new Order(name, current, 10, type));
	}
}
