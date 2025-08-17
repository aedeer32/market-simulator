package com.example.marketsimulator.agent;

import com.example.marketsimulator.model.Market;
import com.example.marketsimulator.model.Order;
import java.util.List;
import java.util.Random;

public class RandomTrader extends Agent {
  private final Random random = new Random();

  public RandomTrader(String name) {
    super(name);
  }

  @Override
  public List<Order> decideAction(Market market) {
    double base = market.getPrice();
    double price = base + (random.nextDouble() - 0.5) * 10;
    Order.Type type = random.nextBoolean() ? Order.Type.BUY : Order.Type.SELL;
    return List.of(new Order(name, price, 10, type));
  }
}
