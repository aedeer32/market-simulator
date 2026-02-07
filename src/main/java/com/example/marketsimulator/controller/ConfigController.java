package com.example.marketsimulator.controller;

import com.example.marketsimulator.service.MarketSimulationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/config")
public class ConfigController {
	private final MarketSimulationService marketSimulationService;
	
	public ConfigController(MarketSimulationService marketSimulationService) {
		this.marketSimulationService = marketSimulationService;
	}
	
	@PatchMapping
	public void updateConfig(@RequestBody UpdateConfigRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body required");
		}
		try {
			marketSimulationService.updateRates(request.fundingRate, request.dividendRate);
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	@PatchMapping("/pause")
	public void pause() {
		marketSimulationService.pause();
	}

	@PatchMapping("/resume")
	public void resume() {
		marketSimulationService.resume();
	}

	@PatchMapping("/reset")
	public void reset() {
		marketSimulationService.reset();
	}
	
	public static class UpdateConfigRequest {
		public Double fundingRate;
		public Double dividendRate;
	}
}
