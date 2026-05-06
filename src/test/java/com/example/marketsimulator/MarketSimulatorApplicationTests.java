package com.example.marketsimulator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "market.scheduling.enabled=false")
class MarketSimulatorApplicationTests {
	
	@Test
	void contextLoads() {
	}
}
