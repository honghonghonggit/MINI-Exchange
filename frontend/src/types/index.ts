export interface PriceLevel {
  price: number;
  quantity: number;
  orderCount: number;
}

export interface OrderBookSnapshot {
  bids: PriceLevel[];
  asks: PriceLevel[];
}

export interface Trade {
  price: number;
  quantity: number;
  executedAt: string;
}

export interface Metrics {
  lastLatencyUs: number;
  avgLatencyUs: number;
  tps: number;
  openOrderCount: number;
}

export interface ViState {
  halted: boolean;
  referencePrice: number;
  until: number;
}
