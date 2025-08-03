package com.example.marketsimulator.service;

import com.example.marketsimulator.agent.Agent;
import com.example.marketsimulator.agent.MarketMaker;
import com.example.marketsimulator.agent.RandomTrader;
import com.example.marketsimulator.model.Market;
import com.example.marketsimulator.model.MarketSnapshot;
import com.example.marketsimulator.model.Order;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MarketSimulationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Market market = new Market();
    private final List<Agent> agents = List.of(
            new MarketMaker("MM1", 2.0),
            new RandomTrader("RT1")
    );

    public MarketSimulationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 1000)
    public void runMarketTick() {
        List<Order> allOrders = new ArrayList<>();
        List<MarketSnapshot.AgentState> agentStates = new ArrayList<>();

        for (Agent agent : agents) {
            List<Order> orders = agent.decideAction(market);
            allOrders.addAll(orders);
            agentStates.add(new MarketSnapshot.AgentState(agent.getName(), orders));
        }

        market.applyOrders(allOrders);

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
