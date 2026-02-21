package com.example.marketsimulator.controller;

import com.example.marketsimulator.service.MarketSimulationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
	private final MarketSimulationService marketSimulationService;
	
	public AgentController(MarketSimulationService marketSimulationService) {
		this.marketSimulationService = marketSimulationService;
	}
	
	@PostMapping
	public AddAgentResponse addAgent(@RequestBody AddAgentRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
		}
		try {
			String name = marketSimulationService.addAgent(request.type, request.name, null);
			return new AddAgentResponse(name);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}
	
	public static class AddAgentRequest {
		public String type;
		public String name;
	}
	
	public static class AddAgentResponse {
		public String name;
		
		public AddAgentResponse(String name) {
			this.name = name;
		}
	}
}
