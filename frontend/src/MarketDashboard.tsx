import React, { useEffect, useMemo, useState } from "react";
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";

interface Order {
  agentName: string;
  price: number;
  quantity: number;
  type: "BUY" | "SELL";
}

interface Agent {
  name: string;
  lastOrders: Order[];
  positionUnits: number;
  cashBalance: number;
  initialCash: number;
}

interface MarketSnapshot {
  price: number;
  agents: Agent[];
  config?: {
    totalAssetUnits: number;
    totalCash: number;
    fundingRate: number;
    dividendRate: number;
    currentTotalAssets: number;
    currentTotalCash: number;
    initialPositions: Record<string, number>;
  };
}

const MarketDashboard: React.FC = () => {
  const [snapshot, setSnapshot] = useState<MarketSnapshot | null>(null);
  const [priceHistory, setPriceHistory] = useState<
    Array<{ price: number; time: number }>
  >([]);
  const [agentHistory, setAgentHistory] = useState<
    Record<string, Array<{ time: number; position: number; value: number }>>
  >({});
  const [collapsed, setCollapsed] = useState({ mm: false, rt: false });
  const [newAgentType, setNewAgentType] = useState<"MM" | "RT">("MM");
  const [newAgentName, setNewAgentName] = useState("");
  const [newAgentCash, setNewAgentCash] = useState("100");
  const [addError, setAddError] = useState<string | null>(null);
  const [isAdding, setIsAdding] = useState(false);
  const [fundingInput, setFundingInput] = useState("");
  const [dividendInput, setDividendInput] = useState("");
  const [rateError, setRateError] = useState<string | null>(null);
  const [isUpdatingRates, setIsUpdatingRates] = useState(false);
  const maxHistoryPoints = 120;

  const chartMeta = useMemo(() => {
    if (priceHistory.length === 0) {
      return null;
    }
    const prices = priceHistory.map((p) => p.price);
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const last = prices[prices.length - 1];
    const times = priceHistory.map((p) => p.time);
    const start = times[0];
    const end = times[times.length - 1];
    const mid = start + (end - start) / 2;
    const padding = Math.max((max - min) * 0.1, 1);
    return {
      min,
      max,
      last,
      start,
      mid,
      end,
      minY: min - padding,
      maxY: max + padding,
    };
  }, [priceHistory]);

  const pricePath = useMemo(() => {
    if (priceHistory.length === 0 || !chartMeta) return "";
    const w = 1000;
    const h = 240;
    const margin = { left: 70, right: 20, top: 20, bottom: 35 };
    const plotW = w - margin.left - margin.right;
    const plotH = h - margin.top - margin.bottom;
    const xStep = plotW / Math.max(priceHistory.length - 1, 1);
    const points = priceHistory.map((price, idx) => {
      const x = margin.left + idx * xStep;
      const y =
        margin.top +
        (1 -
          (price.price - chartMeta.minY) / (chartMeta.maxY - chartMeta.minY)) *
          plotH;
      return `${x},${y}`;
    });
    return `M ${points.join(" L ")}`;
  }, [priceHistory, chartMeta]);

  const formatTime = (ms: number) =>
    new Date(ms).toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });

  const estimateTextWidth = (text: string, fontSize: number) =>
    text.length * fontSize * 0.6;

  const buildSparklinePath = (values: number[], w: number, h: number) => {
    if (values.length === 0) return "";
    const min = Math.min(...values);
    const max = Math.max(...values);
    const padding = Math.max((max - min) * 0.1, 1);
    const minY = min - padding;
    const maxY = max + padding;
    const xStep = w / Math.max(values.length - 1, 1);
    const points = values.map((v, idx) => {
      const x = idx * xStep;
      const y = h - ((v - minY) / (maxY - minY)) * h;
      return `${x},${y}`;
    });
    return `M ${points.join(" L ")}`;
  };

  const formatNumber = (value: number) =>
    value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  const submitNewAgent = async () => {
    const cash = Number(newAgentCash);
    if (Number.isNaN(cash) || cash < 0) {
      setAddError("Cash must be a non-negative number.");
      return;
    }
    setAddError(null);
    setIsAdding(true);
    try {
      const res = await fetch("/api/agents", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type: newAgentType,
          name: newAgentName.trim() === "" ? undefined : newAgentName.trim(),
          cash,
        }),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || "Failed to add agent");
      }
      setNewAgentName("");
      setNewAgentCash("100");
    } catch (err) {
      setAddError(err instanceof Error ? err.message : "Failed to add agent");
    } finally {
      setIsAdding(false);
    }
  };

  useEffect(() => {
    if (!snapshot?.config) return;
    setFundingInput(snapshot.config.fundingRate.toString());
    setDividendInput(snapshot.config.dividendRate.toString());
  }, [snapshot?.config?.fundingRate, snapshot?.config?.dividendRate]);

  const submitRates = async () => {
    const fundingRate = fundingInput === "" ? null : Number(fundingInput);
    const dividendRate = dividendInput === "" ? null : Number(dividendInput);
    if (
      (fundingRate !== null && (Number.isNaN(fundingRate) || fundingRate < 0)) ||
      (dividendRate !== null && (Number.isNaN(dividendRate) || dividendRate < 0))
    ) {
      setRateError("Rates must be non-negative numbers.");
      return;
    }
    setRateError(null);
    setIsUpdatingRates(true);
    try {
      const res = await fetch("/api/config", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          fundingRate,
          dividendRate,
        }),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || "Failed to update rates");
      }
    } catch (err) {
      setRateError(err instanceof Error ? err.message : "Failed to update rates");
    } finally {
      setIsUpdatingRates(false);
    }
  };

  const callControl = async (path: string, label: string) => {
    try {
      const res = await fetch(`/api/config/${path}`, { method: "PATCH" });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Failed to ${label}`);
      }
    } catch (err) {
      setRateError(err instanceof Error ? err.message : `Failed to ${label}`);
    }
  };

  useEffect(() => {
    console.log("🔄 MarketDashboard mounted");

    const socket = new SockJS("/ws-market");
    const client = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      debug: (str) => console.log("[STOMP]", str),
      onConnect: () => {
        console.log("✅ STOMP connected");
        client.subscribe("/topic/market", (msg) => {
          console.log("📩 Received:", msg.body);
          const parsed = JSON.parse(msg.body) as MarketSnapshot;
          setSnapshot(parsed);
          setPriceHistory((prev) => {
            const next = [...prev, { price: parsed.price, time: Date.now() }];
            if (next.length > maxHistoryPoints) {
              next.splice(0, next.length - maxHistoryPoints);
            }
            return next;
          });
          setAgentHistory((prev) => {
            const next = { ...prev };
            const now = Date.now();
            for (const agent of parsed.agents) {
              const totalValue =
                agent.cashBalance + agent.positionUnits * parsed.price;
              const history = next[agent.name] ? [...next[agent.name]] : [];
              history.push({
                time: now,
                position: agent.positionUnits,
                value: totalValue,
              });
              if (history.length > maxHistoryPoints) {
                history.splice(0, history.length - maxHistoryPoints);
              }
              next[agent.name] = history;
            }
            return next;
          });
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
    <div style={{ display: "flex", alignItems: "stretch", minHeight: "100vh" }}>
      {snapshot && (
        <aside
          style={{
            flex: "0 0 260px",
            minWidth: 240,
            borderRight: "1px solid #d6d6d6",
            padding: "16px 12px",
            background: "#eef1f6",
            boxShadow: "inset -1px 0 0 #e2e6ee",
          }}
        >
          <div style={{ fontSize: 14, color: "#333", fontWeight: 700, marginBottom: 8 }}>
            Simulation Configuration
          </div>
          <div style={{ fontSize: 13, color: "#333" }}>
            <div>
              Total Assets:{" "}
              {snapshot.config?.currentTotalAssets.toFixed(2) ?? "N/A"}
            </div>
            <div>
              Total Cash:{" "}
              {snapshot.config
                ? formatNumber(snapshot.config.currentTotalCash)
                : "N/A"}
            </div>
          </div>
          <div
            style={{
              marginTop: 14,
              fontSize: 14,
              color: "#333",
              fontWeight: 700,
            }}
          >
            Market Configuration
          </div>
          <div style={{ marginTop: 6, display: "flex", flexDirection: "column", gap: 6 }}>
            <label style={{ fontSize: 12 }}>
              Funding Rate
              <input
                type="number"
                min="0"
                step="0.001"
                value={fundingInput}
                onChange={(e) => setFundingInput(e.target.value)}
                style={{ marginLeft: 6, width: "100%" }}
              />
            </label>
            <label style={{ fontSize: 12 }}>
              Dividend Rate
              <input
                type="number"
                min="0"
                step="0.001"
                value={dividendInput}
                onChange={(e) => setDividendInput(e.target.value)}
                style={{ marginLeft: 6, width: "100%" }}
              />
            </label>
            <button
              type="button"
              onClick={submitRates}
              disabled={isUpdatingRates}
              style={{
                marginTop: 4,
                padding: "6px 8px",
                borderRadius: 6,
                border: "1px solid #c9cdd6",
                background: "#ffffff",
                cursor: "pointer",
              }}
            >
              {isUpdatingRates ? "Updating..." : "Update Rates"}
            </button>
            {rateError && (
              <div style={{ color: "#b00020", fontSize: 12 }}>{rateError}</div>
            )}
          </div>
          <div
            style={{
              marginTop: 14,
              fontSize: 14,
              color: "#333",
              fontWeight: 700,
            }}
          >
            Simulation Controls
          </div>
          <div style={{ marginTop: 6, display: "flex", flexDirection: "column", gap: 6 }}>
            <button
              type="button"
              onClick={() => callControl("pause", "pause")}
              style={{
                padding: "6px 8px",
                borderRadius: 6,
                border: "1px solid #c9cdd6",
                background: "#ffffff",
                cursor: "pointer",
              }}
            >
              Pause
            </button>
            <button
              type="button"
              onClick={() => callControl("resume", "resume")}
              style={{
                padding: "6px 8px",
                borderRadius: 6,
                border: "1px solid #c9cdd6",
                background: "#ffffff",
                cursor: "pointer",
              }}
            >
              Resume
            </button>
            <button
              type="button"
              onClick={() => callControl("reset", "reset")}
              style={{
                padding: "6px 8px",
                borderRadius: 6,
                border: "1px solid #c9cdd6",
                background: "#fff5f5",
                cursor: "pointer",
              }}
            >
              Reset
            </button>
          </div>
          <div
            style={{
              marginTop: 14,
              fontSize: 14,
              color: "#333",
              fontWeight: 700,
            }}
          >
            Add Agent
          </div>
          <div
            style={{
              marginTop: 6,
              display: "flex",
              flexDirection: "column",
              gap: 6,
            }}
          >
            <label style={{ fontSize: 12 }}>
              Type
              <select
                value={newAgentType}
                onChange={(e) => setNewAgentType(e.target.value as "MM" | "RT")}
                style={{ marginLeft: 6 }}
              >
                <option value="MM">Naive Market Maker</option>
                <option value="RT">Random Trader</option>
              </select>
            </label>
            <label style={{ fontSize: 12 }}>
              Name (optional)
              <input
                type="text"
                value={newAgentName}
                onChange={(e) => setNewAgentName(e.target.value)}
                style={{ marginLeft: 6, width: "100%" }}
                placeholder="auto"
              />
            </label>
            <label style={{ fontSize: 12 }}>
              Initial Cash
              <input
                type="number"
                min="0"
                value={newAgentCash}
                onChange={(e) => setNewAgentCash(e.target.value)}
                style={{ marginLeft: 6, width: "100%" }}
              />
            </label>
            <button
              type="button"
              onClick={submitNewAgent}
              disabled={isAdding}
              style={{
                marginTop: 4,
                padding: "6px 8px",
                borderRadius: 6,
                border: "1px solid #c9cdd6",
                background: "#ffffff",
                cursor: "pointer",
              }}
            >
              {isAdding ? "Adding..." : "Add Agent"}
            </button>
            {addError && (
              <div style={{ color: "#b00020", fontSize: 12 }}>{addError}</div>
            )}
          </div>
        </aside>
      )}
      <div
        style={{ flex: "1 1 auto", minWidth: 320, padding: "0 16px 16px 16px" }}
      >
        <div style={{ margin: "0 0 20px 0" }}>
          <h2 style={{ margin: "0 0 6px 70px" }}>
            📊 Market Price:{" "}
            {snapshot ? snapshot.price.toFixed(2) : "Loading..."}
          </h2>
          <div style={{ fontWeight: 600, margin: "0 0 6px 70px" }}>
            Price Trend
          </div>
          {priceHistory.length === 0 || !chartMeta ? (
            <div>Loading price history...</div>
          ) : (
            <svg
              viewBox="0 0 1000 240"
              width="100%"
              height="200"
              role="img"
              aria-label="Price trend"
            >
              {(() => {
                const w = 1000;
                const h = 240;
                const margin = { left: 70, right: 20, top: 20, bottom: 35 };
                const plotW = w - margin.left - margin.right;
                const plotH = h - margin.top - margin.bottom;
                const hLines = 4;
                const vLines = 4;
                const timeFontSize = 16;
                const timeStart = formatTime(chartMeta.start);
                const timeMid = formatTime(chartMeta.mid);
                const timeEnd = formatTime(chartMeta.end);
                const startW = estimateTextWidth(timeStart, timeFontSize);
                const midW = estimateTextWidth(timeMid, timeFontSize);
                const endW = estimateTextWidth(timeEnd, timeFontSize);
                const gap = 8;
                let xStart = margin.left;
                let xMid = margin.left + plotW / 2 - midW / 2;
                let xEnd = w - margin.right - endW;
                if (xMid < xStart + startW + gap) {
                  xMid = xStart + startW + gap;
                }
                if (xEnd < xMid + midW + gap) {
                  xEnd = xMid + midW + gap;
                }
                const maxEndX = w - margin.right;
                if (xEnd + endW > maxEndX) {
                  const shift = xEnd + endW - maxEndX;
                  xEnd -= shift;
                  xMid = Math.max(margin.left, xMid - shift);
                  xStart = Math.max(margin.left, xStart - shift);
                }
                return (
                  <>
                    <rect
                      x="0"
                      y="0"
                      width={w}
                      height={h}
                      fill="#f7f8fb"
                      rx="6"
                    />
                    <rect
                      x={margin.left}
                      y={margin.top}
                      width={plotW}
                      height={plotH}
                      fill="#ffffff"
                      stroke="#d6d6d6"
                      strokeWidth="1"
                    />
                    <g stroke="#e6e6e6" strokeWidth="1">
                      {Array.from({ length: hLines + 1 }).map((_, i) => {
                        const y = margin.top + (plotH / hLines) * i;
                        return (
                          <line
                            key={`h-${i}`}
                            x1={margin.left}
                            y1={y}
                            x2={w - margin.right}
                            y2={y}
                          />
                        );
                      })}
                      {Array.from({ length: vLines + 1 }).map((_, i) => {
                        const x = margin.left + (plotW / vLines) * i;
                        return (
                          <line
                            key={`v-${i}`}
                            x1={x}
                            y1={margin.top}
                            x2={x}
                            y2={h - margin.bottom}
                          />
                        );
                      })}
                    </g>
                    <path
                      d={pricePath}
                      fill="none"
                      stroke="#1f77b4"
                      strokeWidth="3"
                    />
                    <g fill="#444" fontSize="18" fontFamily="sans-serif">
                      <text x="6" y={margin.top + 6}>
                        {chartMeta.max.toFixed(2)}
                      </text>
                      <text x="6" y={h - margin.bottom + 6}>
                        {chartMeta.min.toFixed(2)}
                      </text>
                      <text x="6" y={margin.top + plotH / 2 + 6}>
                        {chartMeta.last.toFixed(2)}
                      </text>
                    </g>
                    <g
                      fill="#444"
                      fontSize={timeFontSize}
                      fontFamily="sans-serif"
                    >
                      <text x={xStart} y={h - 8}>
                        {timeStart}
                      </text>
                      <text x={xMid} y={h - 8}>
                        {timeMid}
                      </text>
                      <text x={xEnd} y={h - 8}>
                        {timeEnd}
                      </text>
                    </g>
                  </>
                );
              })()}
            </svg>
          )}
        </div>
        {snapshot &&
          (() => {
            const isMarketMakerName = (name: string) => name.startsWith("NMM");
            const mmAgents = snapshot.agents.filter((a) =>
              isMarketMakerName(a.name),
            );
            const rtAgents = snapshot.agents.filter((a) =>
              a.name.startsWith("RT"),
            );
            const renderAgent = (agent: Agent) => (
              <div
                key={agent.name}
                style={{
                  border: "1px solid #d6d6d6",
                  borderRadius: 8,
                  padding: 10,
                  background: "#ffffff",
                  minWidth: 208,
                  flex: "1 1 224px",
                }}
              >
                <div
                  style={{
                    display: "flex",
                    alignItems: "baseline",
                    gap: 8,
                    marginBottom: 3,
                  }}
                >
                  <div style={{ fontWeight: 700 }}>{agent.name}</div>
                  <div style={{ fontSize: 12, color: "#555" }}>
                    Initial Cash: {formatNumber(agent.initialCash)}
                  </div>
                </div>
                <div style={{ fontSize: 12, color: "#333", paddingLeft: 6 }}>
                  Holdings: {formatNumber(agent.positionUnits)} | Cash:{" "}
                  {formatNumber(agent.cashBalance)} | Total:{" "}
                  {formatNumber(
                    agent.cashBalance +
                      agent.positionUnits * snapshot.price,
                  )}
                </div>
                {isMarketMakerName(agent.name) ? (
                  (() => {
                    const buy = agent.lastOrders.find((o) => o.type === "BUY");
                    const sell = agent.lastOrders.find(
                      (o) => o.type === "SELL",
                    );
                    if (!buy || !sell) {
                      return (
                        <ul
                          style={{
                            margin: "4px 0 6px 0",
                            paddingLeft: 18,
                            fontSize: 12,
                            color: "#333",
                            listStyle: "none",
                          }}
                        >
                          {agent.lastOrders.map((order, idx) => (
                            <li key={idx}>
                              {order.type} {order.quantity} @{" "}
                              {order.price.toFixed(2)}
                            </li>
                          ))}
                        </ul>
                      );
                    }
                    return (
                      <div
                        style={{
                          margin: "4px 0 6px 0",
                          fontSize: 12,
                          color: "#333",
                          paddingLeft: 18,
                        }}
                      >
                        Markets: {formatNumber(buy.quantity)} @{" "}
                        {formatNumber(buy.price)} / {formatNumber(sell.quantity)} @{" "}
                        {formatNumber(sell.price)}
                      </div>
                    );
                  })()
                ) : (
                  <ul
                    style={{
                      margin: "4px 0 6px 0",
                      paddingLeft: 18,
                      fontSize: 12,
                      color: "#333",
                      listStyle: "none",
                    }}
                  >
                    {agent.lastOrders.map((order, idx) => (
                      <li key={idx}>
                        Trades: {order.type} {formatNumber(order.quantity)} @{" "}
                        {formatNumber(order.price)}
                      </li>
                    ))}
                  </ul>
                )}
                <div style={{ marginTop: 4 }}>
                  <div style={{ fontSize: 11, color: "#555", marginBottom: 3 }}>
                    Total Value Trend
                  </div>
                  <svg
                    viewBox="0 0 280 50"
                    width="280"
                    height="50"
                    role="img"
                    aria-label="Total value trend"
                  >
                    <rect
                      x="0"
                      y="0"
                      width="280"
                      height="50"
                      fill="#ffffff"
                      stroke="#d6d6d6"
                      strokeWidth="1"
                      rx="4"
                    />
                    <path
                      d={buildSparklinePath(
                        (agentHistory[agent.name] || []).map((p) => p.value),
                        280,
                        50,
                      )}
                      fill="none"
                      stroke="#ff7f0e"
                      strokeWidth="2"
                    />
                  </svg>
                </div>
              </div>
            );
            return (
              <div
                style={{
                  display: "flex",
                  gap: 16,
                  flexWrap: "wrap",
                  alignItems: "flex-start",
                }}
              >
                <div style={{ flex: "1 1 360px", minWidth: 300 }}>
                  <button
                    type="button"
                    onClick={() =>
                      setCollapsed((prev) => ({ ...prev, mm: !prev.mm }))
                    }
                    style={{
                      fontWeight: 700,
                      marginBottom: 8,
                      background: "#f1f3f6",
                      border: "1px solid #d6d6d6",
                      borderRadius: 6,
                      padding: "6px 10px",
                      cursor: "pointer",
                    }}
                    aria-expanded={!collapsed.mm}
                  >
                    {collapsed.mm ? "▶" : "▼"} Market Makers
                  </button>
                  {!collapsed.mm && (
                    <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                      {mmAgents.map(renderAgent)}
                    </div>
                  )}
                </div>
                <div style={{ flex: "1 1 360px", minWidth: 300 }}>
                  <button
                    type="button"
                    onClick={() =>
                      setCollapsed((prev) => ({ ...prev, rt: !prev.rt }))
                    }
                    style={{
                      fontWeight: 700,
                      marginBottom: 8,
                      background: "#f1f3f6",
                      border: "1px solid #d6d6d6",
                      borderRadius: 6,
                      padding: "6px 10px",
                      cursor: "pointer",
                    }}
                    aria-expanded={!collapsed.rt}
                  >
                    {collapsed.rt ? "▶" : "▼"} Traders
                  </button>
                  {!collapsed.rt && (
                    <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                      {rtAgents.map(renderAgent)}
                    </div>
                  )}
                </div>
              </div>
            );
          })()}
      </div>
    </div>
  );
};

export default MarketDashboard;
