package com.example.marketsimulator.service;

import com.example.marketsimulator.agent.Agent;
import com.example.marketsimulator.agent.MarketMaker;
import com.example.marketsimulator.agent.RandomTrader;
import com.example.marketsimulator.model.Market;
import com.example.marketsimulator.model.MarketSnapshot;
import com.example.marketsimulator.model.Order;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MarketSimulationService {
	
	private final SimpMessagingTemplate messagingTemplate;
	private final Market market = new Market();
	private final List<Agent> agents = List.of(new MarketMaker("MM1", 2.0), new RandomTrader("RT1"));
	private final Map<String, Double> positions = new HashMap<>();
	private final Map<String, Double> cashBalances = new HashMap<>();
	private static final double INITIAL_CASH = 100000.0;
	
	public MarketSimulationService(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
		for (Agent agent : agents) {
			positions.put(agent.getName(), 0.0);
			cashBalances.put(agent.getName(), INITIAL_CASH);
		}
	}
	
	@Scheduled(fixedRate = 1000)
	public void runMarketTick() {
		List<Order> allOrders = new ArrayList<>();
		List<MarketSnapshot.AgentState> agentStates = new ArrayList<>();
		double buyMax = Double.NaN;
		double sellMin = Double.NaN;
		
		for (Agent agent : agents) {
			List<Order> orders = agent.decideAction(market);
			allOrders.addAll(orders);
		}

		if (!allOrders.isEmpty()) {
			buyMax = allOrders.stream().filter(o -> o.type == Order.Type.BUY).mapToDouble(o -> o.price).max().orElse(Double.NaN);
			sellMin = allOrders.stream().filter(o -> o.type == Order.Type.SELL).mapToDouble(o -> o.price).min().orElse(Double.NaN);
		}
		
		market.applyOrders(allOrders);

		boolean crossed = !Double.isNaN(buyMax) && !Double.isNaN(sellMin) && buyMax >= sellMin;
		if (crossed) {
			double tradePrice = market.getPrice();
			for (Order order : allOrders) {
				double delta = order.quantity * (order.type == Order.Type.BUY ? 1.0 : -1.0);
				positions.put(order.agentName, positions.getOrDefault(order.agentName, 0.0) + delta);
				double cashDelta = order.quantity * tradePrice * (order.type == Order.Type.BUY ? -1.0 : 1.0);
				cashBalances.put(order.agentName, cashBalances.getOrDefault(order.agentName, INITIAL_CASH) + cashDelta);
			}
		}
		
		for (Agent agent : agents) {
			double positionUnits = positions.getOrDefault(agent.getName(), 0.0);
			double cashBalance = cashBalances.getOrDefault(agent.getName(), INITIAL_CASH);
			List<Order> orders = allOrders.stream().filter(o -> o.agentName.equals(agent.getName())).toList();
			agentStates.add(new MarketSnapshot.AgentState(agent.getName(), orders, positionUnits, cashBalance));
		}
		
		MarketSnapshot snapshot = new MarketSnapshot(market.getPrice(), agentStates);
		messagingTemplate.convertAndSend("/topic/market", snapshot);
	}
	
	//    @Scheduled(fixedRate = 1000)
	//    public void runMarketTick() {
	//        List<Order> allOrders = new ArrayList<>();
	//        agents.forEach(agent -> allOrders.addAll(agent.decideAction(market)));
	//        market.applyOrders(allOrders);
	//
	//        double newPrice = market.getPrice();
	//        messagingTemplate.convertAndSend("/topic/market", newPrice);
	//    }
	
	//    @Scheduled(fixedRate = 1000)
	//    public void simulate() {
	//        CompletableFuture.runAsync(
	//            () -> {
	//                List<Agent> agents = List.of(new MarketMaker());
	//
	//                for (Agent agent : agents) {
	//                    List<Order> orders = agent.decideAction(market);
	//                    market.applyOrders(orders);
	//
	//                    messagingTemplate.convertAndSend("/topic/market", orders);
	//                }
	//            }
	//        );
	//    }
}
