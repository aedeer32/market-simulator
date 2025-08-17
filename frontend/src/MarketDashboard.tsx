import React, { useEffect, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

interface Order {
  agentName: string;
  price: number;
  type: 'BUY' | 'SELL';
}

interface Agent {
  name: string;
  lastOrders: Order[];
}

interface MarketSnapshot {
  price: number;
  agents: Agent[];
}

const MarketDashboard: React.FC = () => {
  const [snapshot, setSnapshot] = useState<MarketSnapshot | null>(null);

  useEffect(() => {
    console.log("🔄 MarketDashboard mounted");

    const socket = new SockJS('/ws-market');
    const client = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      debug: (str) => console.log('[STOMP]', str),
      onConnect: () => {
        console.log("✅ STOMP connected");
        client.subscribe('/topic/market', (msg) => {
          console.log("📩 Received:", msg.body);
          setSnapshot(JSON.parse(msg.body));
        });
      },
      onStompError: (frame) => {
        console.error("❌ STOMP error:", frame);
      },
    });

    client.activate();

    return () => {
      client.deactivate();
    };
  }, []);

  return (
    <div>
      <h2>📊 Market Price: {snapshot ? snapshot.price.toFixed(2) : 'Loading...'}</h2>
      {snapshot?.agents.map((agent) => (
        <div key={agent.name}>
          <h3>{agent.name}</h3>
          <ul>
            {agent.lastOrders.map((order, idx) => (
              <li key={idx}>
                {order.type} @ {order.price.toFixed(2)}
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
};

export default MarketDashboard;
