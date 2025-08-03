// App.tsx
import React, { useEffect } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const App: React.FC = () => {
  useEffect(() => {
    console.log("🔄 App mounted");

    const socket = new SockJS('/ws-market');
    console.log("🚀 SockJS created", socket);

    const client = new Client({
      webSocketFactory: () => socket,
      debug: (str) => console.log("[STOMP DEBUG]", str), // ← これがデバッグ支援ログ
      reconnectDelay: 5000,
      onConnect: () => {
        console.log("✅ STOMP connected");
        client.subscribe('/topic/market', (msg) => {
          console.log("📩 Received message:", msg.body);
        });
      },
      onStompError: (frame) => {
        console.error("❌ Broker error:", frame.headers['message']);
        console.error("Details:", frame.body);
      },
    });

    client.activate();

    return () => {
      client.deactivate();
    };
  }, []);

  return <h1>📈 Market Simulator UI</h1>;
};

export default App;
