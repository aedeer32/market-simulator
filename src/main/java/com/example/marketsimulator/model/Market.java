package com.example.marketsimulator.model;

import java.util.List;
import lombok.Getter;

@Getter
public class Market {
	private double price = 100.0;
	
	public double getPrice() {
		return price;
	}
	
	public void applyOrders(List<Order> orders) {
		double buyMax = orders.stream().filter(o -> o.type == Order.Type.BUY).mapToDouble(o -> o.price).max().orElse(Double.NaN);
		double sellMin = orders.stream().filter(o -> o.type == Order.Type.SELL).mapToDouble(o -> o.price).min().orElse(Double.NaN);
		if (!Double.isNaN(buyMax) && !Double.isNaN(sellMin) && buyMax >= sellMin) {
			this.price = (buyMax + sellMin) / 2.0;
		}
	}
}
