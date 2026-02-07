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
}

export interface MarketSnapshot {
  price: number;
  agents: AgentState[];
}
