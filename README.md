# 📈 Market Simulator

A real-time market simulation application built with **React (Vite)** frontend and **Spring Boot** backend using **WebSocket (STOMP + SockJS)** for live communication.

---

## 🧩 Project Structure

```
market-simulator/
├── src/main/java/com/example/marketsimulator
│   ├── controller/                 # WebSocket endpoints
│   ├── config/                     # WebSocket & Security configuration
│   ├── model/                      # Market and Order models
│   ├── service/                    # Market processing logic
│
└── frontend/                       # React frontend (Vite)
    ├── src/
    │   ├── App.tsx
    │   ├── MarketDashboard.tsx
    │   ├── main.tsx
    │   ├── index.css
    ├── index.html
    └── vite.config.ts

```

---

## 🚀 Getting Started

### ✅ Prerequisites

- Java 21 or later
- Node.js 18 or later
- Maven 3.x

---

## 🛠 Backend Setup (Spring Boot)

```bash
./mvnw spring-boot:run
```

- Runs at: `http://localhost:8080`
- WebSocket endpoint: `/ws-market`
- STOMP Topic: `/topic/market`
- STOMP App Destination: `/app/order`

---

## 💻 Frontend Setup (React + Vite)

```bash
cd frontend
npm install
npm run dev
```

- Local server: `http://localhost:5173`
- Requires backend running on port `8080`

---

## 🤖 CI

GitHub Actions workflow is defined in `.github/workflows/ci.yml`.

- Backend job: runs `./mvnw -B test spotless:check`
- Frontend job: runs `npm ci` and `npm run build`
- Triggered on `push` and `pull_request`

---

## 🐳 Docker

This repository now includes:

- Root `Dockerfile` for the Spring Boot backend
- `frontend/Dockerfile` for the React app served by Nginx
- `compose.yml` to run both containers together

### Build and run with Docker Compose

```bash
docker compose up --build
```

- Frontend: `http://localhost:5173`
- Backend API / WebSocket: `http://localhost:8080`

The frontend container proxies `/api` and `/ws-market` to the backend container, so the browser only needs to talk to the frontend origin.

---

## 🔁 Vite Proxy Configuration

Make sure the following is included in `vite.config.ts` to forward WebSocket traffic:

```ts
server: {
  proxy: {
    '/ws-market': {
      target: 'http://localhost:8080',
      ws: true,
      changeOrigin: true,
    },
  },
},
define: {
  global: 'globalThis',
},
```

---

## 📡 How It Works

1. The frontend connects to the backend via STOMP over SockJS (`/ws-market`).
2. The backend receives orders at `/app/order`.
3. Market logic calculates the new price based on the order book.
4. The new price is published to `/topic/market`.
5. Frontend receives updates in real-time and updates the UI.

---

## 🧪 Debugging Tips

- Chrome DevTools → **Network → WS tab** to inspect WebSocket traffic.
- Enable verbose logs:

  - **Frontend**: STOMP client has `debug` output in console.
  - **Backend**: Add in `application.properties`:

    ```properties
    logging.level.org.springframework.messaging=DEBUG
    logging.level.org.springframework.web.socket=DEBUG
    ```

---

## 🛠 Technologies Used

- **Frontend**: React, TypeScript, Vite, STOMP.js, SockJS
- **Backend**: Spring Boot 3, WebSocket (STOMP), Java 21
- **Protocol**: STOMP over WebSocket (with SockJS fallback)

---

## 🧾 License

This project is licensed under the MIT License.

---

## 🤝 Contributions

Pull requests are welcome. Feel free to fork this repo and submit your ideas!
