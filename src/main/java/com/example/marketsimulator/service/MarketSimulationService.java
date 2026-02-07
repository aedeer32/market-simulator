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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MarketSimulationService {
	
	private final SimpMessagingTemplate messagingTemplate;
	private final Market market = new Market();
	private final List<Agent> agents = List.of(
	        new MarketMaker("MM1", 2.0),
	        new MarketMaker("MM2", 2.0),
	        new RandomTrader("RT1"),
	        new RandomTrader("RT2")
	);
	private final Map<String, Double> positions = new HashMap<>();
	private final Map<String, Double> cashBalances = new HashMap<>();
	private final double totalAssetUnits;
	private final double totalCash;
	private final Map<String, Double> initialPositions;
	
	public MarketSimulationService(
	        SimpMessagingTemplate messagingTemplate,
	        @Value("${market.total-asset-units:100}") double totalAssetUnits,
	        @Value("${market.total-cash:10000}") double totalCash,
	        @Value("${market.initial-positions:MM1:100,RT1:0}") String initialPositions
	) {
		this.messagingTemplate = messagingTemplate;
		this.totalAssetUnits = totalAssetUnits;
		this.totalCash = totalCash;
		this.initialPositions = parseInitialPositions(initialPositions);
		double initialCashPerAgent = totalCash / agents.size();
		for (Agent agent : agents) {
			positions.put(agent.getName(), this.initialPositions.getOrDefault(agent.getName(), 0.0));
			cashBalances.put(agent.getName(), initialCashPerAgent);
		}
	}
	
	@Scheduled(fixedRate = 1000)
	public void runMarketTick() {
		List<Order> allOrders = new ArrayList<>();
		List<MarketSnapshot.AgentState> agentStates = new ArrayList<>();
		
		for (Agent agent : agents) {
			List<Order> orders = agent.decideAction(market);
			allOrders.addAll(orders);
		}
		
		double lastTradePrice = matchAndSettle(allOrders);
		if (!Double.isNaN(lastTradePrice)) {
			market.updatePrice(lastTradePrice);
		}
		
		for (Agent agent : agents) {
			double positionUnits = positions.getOrDefault(agent.getName(), 0.0);
			double cashBalance = cashBalances.getOrDefault(agent.getName(), totalCash / agents.size());
			List<Order> orders = allOrders.stream().filter(o -> o.agentName.equals(agent.getName())).toList();
			agentStates.add(new MarketSnapshot.AgentState(agent.getName(), orders, positionUnits, cashBalance));
		}
		
		MarketSnapshot.MarketConfig config = new MarketSnapshot.MarketConfig(
		        totalAssetUnits,
		        totalCash,
		        new HashMap<>(initialPositions)
		);
		MarketSnapshot snapshot = new MarketSnapshot(market.getPrice(), agentStates, config);
		messagingTemplate.convertAndSend("/topic/market", snapshot);
	}

	private double matchAndSettle(List<Order> orders) {
		if (orders.isEmpty()) {
			return Double.NaN;
		}
		List<MutableOrder> buys = orders.stream()
		        .filter(o -> o.type == Order.Type.BUY)
		        .map(MutableOrder::new)
		        .sorted((a, b) -> Double.compare(b.price, a.price))
		        .toList();
		List<MutableOrder> sells = orders.stream()
		        .filter(o -> o.type == Order.Type.SELL)
		        .map(MutableOrder::new)
		        .sorted((a, b) -> Double.compare(a.price, b.price))
		        .toList();
		
		int bi = 0;
		int si = 0;
		double lastTradePrice = Double.NaN;
		while (bi < buys.size() && si < sells.size()) {
			MutableOrder buy = buys.get(bi);
			MutableOrder sell = sells.get(si);
			if (buy.price < sell.price) {
				break;
			}
			double tradePrice = (buy.price + sell.price) / 2.0;
			double sellerPosition = positions.getOrDefault(sell.agentName, 0.0);
			double buyerCash = cashBalances.getOrDefault(buy.agentName, totalCash / agents.size());
			double maxByCash = tradePrice > 0.0 ? buyerCash / tradePrice : 0.0;
			
			double tradable = Math.min(Math.min(buy.remaining, sell.remaining), Math.min(sellerPosition, maxByCash));
			if (tradable <= 0.0) {
				if (sellerPosition <= 0.0 || sell.remaining <= 0.0) {
					si++;
				} else if (buyerCash <= 0.0 || buy.remaining <= 0.0 || maxByCash <= 0.0) {
					bi++;
				} else {
					bi++;
				}
				continue;
			}
			
			positions.put(buy.agentName, positions.getOrDefault(buy.agentName, 0.0) + tradable);
			positions.put(sell.agentName, positions.getOrDefault(sell.agentName, 0.0) - tradable);
			cashBalances.put(buy.agentName, buyerCash - tradable * tradePrice);
			cashBalances.put(sell.agentName, cashBalances.getOrDefault(sell.agentName, totalCash / agents.size()) + tradable * tradePrice);
			buy.remaining -= tradable;
			sell.remaining -= tradable;
			lastTradePrice = tradePrice;
			
			if (buy.remaining <= 0.0) {
				bi++;
			}
			if (sell.remaining <= 0.0) {
				si++;
			}
		}
		return lastTradePrice;
	}
	
	private static class MutableOrder {
		final String agentName;
		final double price;
		double remaining;
		
		MutableOrder(Order order) {
			this.agentName = order.agentName;
			this.price = order.price;
			this.remaining = order.quantity;
		}
	}

	private Map<String, Double> parseInitialPositions(String raw) {
		Map<String, Double> parsed = new HashMap<>();
		if (raw != null && !raw.isBlank()) {
			String[] entries = raw.split(",");
			for (String entry : entries) {
				String trimmed = entry.trim();
				if (trimmed.isEmpty() || !trimmed.contains(":")) {
					continue;
				}
				String[] parts = trimmed.split(":", 2);
				String name = parts[0].trim();
				if (name.isEmpty()) {
					continue;
				}
				try {
					double value = Double.parseDouble(parts[1].trim());
					parsed.put(name, value);
				} catch (NumberFormatException ignored) {
					// ignore invalid values
				}
			}
		}
		if (parsed.isEmpty()) {
			parsed.put(agents.get(0).getName(), totalAssetUnits);
		}
		double sum = parsed.values().stream().mapToDouble(Double::doubleValue).sum();
		if (sum <= 0.0) {
			parsed.replaceAll((k, v) -> 0.0);
			parsed.put(agents.get(0).getName(), totalAssetUnits);
			return parsed;
		}
		if (Math.abs(sum - totalAssetUnits) > 1e-6) {
			double scale = totalAssetUnits / sum;
			parsed.replaceAll((k, v) -> v * scale);
		}
		return parsed;
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
