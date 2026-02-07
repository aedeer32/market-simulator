export interface Order {
  agentName: string;
  price: number;
  quantity: number;
  type: 'BUY' | 'SELL';
}

export interface AgentState {
  name: string;
  lastOrders: Order[];
  positionUnits: number;
  cashBalance: number;
  initialCash: number;
}

export interface MarketSnapshot {
  price: number;
  agents: AgentState[];
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
