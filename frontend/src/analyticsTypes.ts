export type ValuationStatus = "COMPLETE" | "MISSING_PRICES";
export type ValuedPosition = {
  symbol: string;
  quantity: string;
  costBasis: string;
  valuationPrice: string | null;
  marketValue: string | null;
  realizedPnl: string;
  unrealizedPnl: string | null;
  totalPnl: string | null;
  unrealizedReturnPercent: string | null;
};
export type ValuePoint = {
  date: string;
  status: ValuationStatus;
  cash: string;
  marketValue: string | null;
  totalValue: string | null;
  realizedPnl: string;
  unrealizedPnl: string | null;
  totalPnl: string | null;
  returnPercent: string | null;
  missingSymbols: string[];
};
export type Valuation = ValuePoint & {
  initialCapital: string;
  positions: ValuedPosition[];
};
export type ValueSeries = {
  from: string;
  to: string;
  simulationStartDate: string;
  currency: "USD";
  initialCapital: string;
  completeDays: number;
  missingDays: number;
  points: ValuePoint[];
};
export type Allocation = {
  date: string;
  status: "COMPLETE" | "MISSING_PRICES" | "ZERO_TOTAL_VALUE";
  missingSymbols: string[];
  items: {
    symbol: string;
    marketValue: string | null;
    weightPercent: string | null;
  }[];
};
export type Performance = {
  simulationStartDate: string;
  currency: "USD";
  summary: Valuation;
  series: ValueSeries;
  allocation: Allocation;
  period: {
    startingValue: string | null;
    endingValue: string | null;
    change: string | null;
    returnPercent: string | null;
    status: "COMPLETE" | "MISSING_ENDPOINT_PRICE" | "ZERO_START_VALUE";
  };
};
