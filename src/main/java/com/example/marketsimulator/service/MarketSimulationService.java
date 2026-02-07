package com.example.marketsimulator.service;

import com.example.marketsimulator.agent.Agent;
import com.example.marketsimulator.agent.MarketMaker;
import com.example.marketsimulator.agent.RandomTrader;
import com.example.marketsimulator.model.Market;
import com.example.marketsimulator.model.MarketSnapshot;
import com.example.marketsimulator.model.Order;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MarketSimulationService {
	
	private final SimpMessagingTemplate messagingTemplate;
	private final Market market = new Market();
	private final List<Agent> agents = new ArrayList<>();
	private final Map<String, Double> positions = new HashMap<>();
	private final Map<String, Double> cashBalances = new HashMap<>();
	private final Map<String, Double> initialCashBalances = new HashMap<>();
	private final double totalAssetUnits;
	private double totalCash;
	private final Map<String, Double> initialPositions;
	private final double fundingRate;
	private final double dividendRate;
	private int mmCounter = 1;
	private int rtCounter = 1;
	
	public MarketSimulationService(
	        SimpMessagingTemplate messagingTemplate,
	        @Value("${market.total-asset-units:100}") double totalAssetUnits,
	        @Value("${market.total-cash:10000}") double totalCash,
	        @Value("${market.initial-positions:MM1:100,RT1:0}") String initialPositions,
	        @Value("${market.funding-rate:0.01}") double fundingRate,
	        @Value("${market.dividend-rate:0.02}") double dividendRate
	) {
		this.messagingTemplate = messagingTemplate;
		this.totalAssetUnits = totalAssetUnits;
		this.totalCash = totalCash;
		this.initialPositions = parseInitialPositions(initialPositions);
		this.fundingRate = fundingRate;
		this.dividendRate = dividendRate;
		agents.add(new MarketMaker("MM1", 2.0));
		agents.add(new MarketMaker("MM2", 2.0));
		agents.add(new RandomTrader("RT1"));
		agents.add(new RandomTrader("RT2"));
		mmCounter = 3;
		rtCounter = 3;
		double initialCashPerAgent = totalCash / agents.size();
		for (Agent agent : agents) {
			positions.put(agent.getName(), this.initialPositions.getOrDefault(agent.getName(), 0.0));
			cashBalances.put(agent.getName(), initialCashPerAgent);
			initialCashBalances.put(agent.getName(), initialCashPerAgent);
		}
	}
	
	@Scheduled(fixedRate = 1000)
	public void runMarketTick() {
		MarketSnapshot snapshot;
		synchronized (this) {
			List<Order> allOrders = new ArrayList<>();
			List<MarketSnapshot.AgentState> agentStates = new ArrayList<>();
			
			for (Agent agent : agents) {
				List<Order> orders = agent.decideAction(market);
				allOrders.addAll(orders);
			}
			
			applyFundingRate();
			applyDividendRate();
			
			double lastTradePrice = matchAndSettle(allOrders);
			if (!Double.isNaN(lastTradePrice)) {
				market.updatePrice(lastTradePrice);
			}

			List<Agent> bankruptCandidates = new ArrayList<>();
			for (Agent agent : agents) {
				double positionUnits = positions.getOrDefault(agent.getName(), 0.0);
				double cashBalance = cashBalances.getOrDefault(agent.getName(), 0.0);
				double totalValue = cashBalance + positionUnits * market.getPrice();
				if (totalValue < 0.0) {
					bankruptCandidates.add(agent);
				}
			}
			if (!bankruptCandidates.isEmpty()) {
				Map<String, Double> mmBids = new HashMap<>();
				for (Order order : allOrders) {
					if (order.type == Order.Type.BUY && order.agentName.startsWith("MM")) {
						mmBids.merge(order.agentName, order.price, Math::max);
					}
				}
				liquidateBankruptAgents(bankruptCandidates, mmBids);
				List<Agent> bankruptAgents = new ArrayList<>();
				for (Agent agent : bankruptCandidates) {
					double positionUnits = positions.getOrDefault(agent.getName(), 0.0);
					double cashBalance = cashBalances.getOrDefault(agent.getName(), 0.0);
					double totalValue = cashBalance + positionUnits * market.getPrice();
					if (totalValue < 0.0) {
						bankruptAgents.add(agent);
					}
				}
				for (Agent agent : bankruptAgents) {
					agents.remove(agent);
					positions.remove(agent.getName());
					cashBalances.remove(agent.getName());
				}
			}
			
			for (Agent agent : agents) {
				double positionUnits = positions.getOrDefault(agent.getName(), 0.0);
				double cashBalance = cashBalances.getOrDefault(agent.getName(), 0.0);
				double initialCash = initialCashBalances.getOrDefault(agent.getName(), 0.0);
				List<Order> orders = allOrders.stream().filter(o -> o.agentName.equals(agent.getName())).toList();
				agentStates.add(new MarketSnapshot.AgentState(agent.getName(), orders, positionUnits, cashBalance, initialCash));
			}
			
			double currentTotalCash = cashBalances.values().stream().mapToDouble(Double::doubleValue).sum();
			double currentTotalAssets = positions.values().stream().mapToDouble(Double::doubleValue).sum();
			MarketSnapshot.MarketConfig config = new MarketSnapshot.MarketConfig(
			        totalAssetUnits,
			        totalCash,
			        fundingRate,
			        dividendRate,
			        currentTotalAssets,
			        currentTotalCash,
			        new HashMap<>(initialPositions)
			);
			snapshot = new MarketSnapshot(market.getPrice(), agentStates, config);
		}
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
			double buyerCash = cashBalances.getOrDefault(buy.agentName, 0.0);
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
			cashBalances.put(sell.agentName, cashBalances.getOrDefault(sell.agentName, 0.0) + tradable * tradePrice);
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

	private void liquidateBankruptAgents(List<Agent> bankruptCandidates, Map<String, Double> mmBids) {
		List<Agent> marketMakers = new ArrayList<>();
		for (Agent agent : agents) {
			if (agent.getName().startsWith("MM")) {
				marketMakers.add(agent);
			}
		}
		for (Agent agent : bankruptCandidates) {
			double positionUnits = positions.getOrDefault(agent.getName(), 0.0);
			if (positionUnits <= 0.0) {
				continue;
			}
			double remaining = positionUnits;
			for (Agent mm : marketMakers) {
				if (mm.getName().equals(agent.getName())) {
					continue;
				}
				double price = mmBids.getOrDefault(mm.getName(), Double.NaN);
				if (Double.isNaN(price) || price <= 0.0) {
					continue;
				}
				double mmCash = cashBalances.getOrDefault(mm.getName(), 0.0);
				double maxBuy = mmCash / price;
				if (maxBuy <= 0.0) {
					continue;
				}
				double tradeUnits = Math.min(remaining, maxBuy);
				if (tradeUnits <= 0.0) {
					continue;
				}
				positions.put(agent.getName(), positions.getOrDefault(agent.getName(), 0.0) - tradeUnits);
				positions.put(mm.getName(), positions.getOrDefault(mm.getName(), 0.0) + tradeUnits);
				cashBalances.put(agent.getName(), cashBalances.getOrDefault(agent.getName(), 0.0) + tradeUnits * price);
				cashBalances.put(mm.getName(), mmCash - tradeUnits * price);
				remaining -= tradeUnits;
				if (remaining <= 0.0) {
					break;
				}
			}
		}
	}

	private void applyFundingRate() {
		if (fundingRate <= 0.0) {
			return;
		}
		for (Map.Entry<String, Double> entry : cashBalances.entrySet()) {
			double initialCash = initialCashBalances.getOrDefault(entry.getKey(), 0.0);
			double updated = entry.getValue() - (initialCash * fundingRate);
			entry.setValue(updated);
		}
	}

	private void applyDividendRate() {
		if (dividendRate <= 0.0) {
			return;
		}
		double price = market.getPrice();
		for (Map.Entry<String, Double> entry : cashBalances.entrySet()) {
			double positionUnits = positions.getOrDefault(entry.getKey(), 0.0);
			double updated = entry.getValue() + (positionUnits * price * dividendRate);
			entry.setValue(updated);
		}
	}

	public synchronized String addAgent(String type, String name, double initialCash) {
		if (initialCash < 0.0) {
			throw new IllegalArgumentException("initialCash must be >= 0");
		}
		String normalizedType = type == null ? "" : type.trim().toUpperCase();
		boolean isMm = "MM".equals(normalizedType) || "MARKET_MAKER".equals(normalizedType);
		boolean isRt = "RT".equals(normalizedType) || "RANDOM_TRADER".equals(normalizedType);
		if (!isMm && !isRt) {
			throw new IllegalArgumentException("type must be MM or RT");
		}
		Set<String> existingNames = new HashSet<>();
		for (Agent agent : agents) {
			existingNames.add(agent.getName());
		}
		String resolvedName = (name == null) ? "" : name.trim();
		if (resolvedName.isEmpty() || existingNames.contains(resolvedName)) {
			if (isMm) {
				while (existingNames.contains("MM" + mmCounter)) {
					mmCounter++;
				}
				resolvedName = "MM" + mmCounter;
				mmCounter++;
			} else {
				while (existingNames.contains("RT" + rtCounter)) {
					rtCounter++;
				}
				resolvedName = "RT" + rtCounter;
				rtCounter++;
			}
		}
		Agent newAgent = isMm ? new MarketMaker(resolvedName, 2.0) : new RandomTrader(resolvedName);
		agents.add(newAgent);
		positions.put(resolvedName, 0.0);
		cashBalances.put(resolvedName, initialCash);
		initialCashBalances.put(resolvedName, initialCash);
		totalCash += initialCash;
		return resolvedName;
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
