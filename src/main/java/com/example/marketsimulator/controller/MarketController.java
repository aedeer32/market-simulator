package com.example.marketsimulator.controller;

import com.example.marketsimulator.model.Market;
import com.example.marketsimulator.model.Order;
import java.util.List;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class MarketController {
	
	private final SimpMessagingTemplate messagingTemplate;
	private final Market market = new Market();
	
	public MarketController(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}
	
	@MessageMapping("/order")
	public void receiveOrders(List<Order> orders) {
		market.applyOrders(orders);
		messagingTemplate.convertAndSend("/topic/market", market);
	}
}
