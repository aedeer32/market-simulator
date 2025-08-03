package com.example.marketsimulator.agent;

import com.example.marketsimulator.model.Market;
import com.example.marketsimulator.model.Order;

import java.util.List;

public abstract class Agent {
    protected String name;
    public Agent(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract List<Order> decideAction(Market market);
}
