package com.example.marketsimulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarketSimulatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketSimulatorApplication.class, args);
	}

}
