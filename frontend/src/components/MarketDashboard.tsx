import React, { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

type Agent = {
  name: string;
  lastOrders: { price: number; type: string }[];
};

type MarketSnapshot = {
  price: number;
  agents: Agent[];
};

const MarketDashboard: React.FC = () => {
  const [snapshot, setSnapshot] = useState<MarketSnapshot | null>(null);

  useEffect(() => {
    console.log("🔄 MarketDashboard mounted");

    const socket = new SockJS('/ws-market');
    console.log("🚀 SockJS created", socket);

    const client = new Client({
      webSocketFactory: () => socket,
      debug: (str) => console.log("[STOMP DEBUG]", str),
      reconnectDelay: 5000,
      onConnect: () => {
        console.log("✅ STOMP connected");
        client.subscribe('/topic/market', (msg) => {
          console.log("📩 Received message:", msg.body);
          try {
            const data = JSON.parse(msg.body);
            setSnapshot(data);
          } catch (err) {
            console.error("❌ JSON parse error", err);
          }
        });
      },
      onStompError: (frame) => {
        console.error("❌ STOMP error:", frame.headers['message']);
        console.error("Details:", frame.body);
      },
    });

    client.activate();

    return () => {
      client.deactivate();
    };
  }, []);

  return (
    <div style={{ padding: 20 }}>
      <h2>📊 Market Dashboard</h2>
      {snapshot ? (
        <div>
          <p>💰 Price: {snapshot.price}</p>
          <h3>🧑‍💼 Agents</h3>
          <ul>
            {snapshot.agents.map((agent) => (
              <li key={agent.name}>
                <strong>{agent.name}</strong>:{" "}
                {agent.lastOrders.map((order, index) => (
                  <span key={index}>
                    {order.type} @ {order.price.toFixed(2)}{" "}
                  </span>
                ))}
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p>⏳ Waiting for market data...</p>
      )}
    </div>
  );
};

export default MarketDashboard;
