import type { Valuation } from "./analyticsTypes";
export type Metric = {
  status: string;
  value: string | null;
  observations: number;
  minimum: number;
};
export type VaR = {
  status: string;
  usd: string | null;
  percent: string | null;
  observations: number;
  minimum: number;
};
export type Exposure = {
  symbol: string;
  marketValue: string | null;
  weightPercent: string | null;
  dailyVolatility: Metric;
};
export type Returns = {
  status: string;
  candidatePairs: number;
  missingPairs: number;
  missingPrices: { symbol: string; date: string }[];
  points: {
    date: string;
    returnPercent: string;
    lossUsd: string;
    assetReturns: Record<string, string>;
  }[];
};
export type RiskSummary = {
  from: string;
  date: string;
  lookbackDays: number;
  currency: "USD";
  valuation: Valuation;
  analysis: {
    returns: Returns;
    dailyVolatility: Metric;
    annualVolatility: Metric;
    var95: VaR;
    var99: VaR;
    assets: Exposure[];
    concentration: string | null;
  };
  drawdown: {
    status: string;
    percent: string | null;
    peakDate: string | null;
    troughDate: string | null;
    observations: number;
    missingDates: string[];
  };
};
export type Scenario = {
  name: string;
  commonShockPercent: string;
  overrides: Record<string, string>;
};
export type StressResult = {
  name: string;
  commonShockPercent: string;
  cash: string;
  currentValue: string;
  stressedValue: string;
  gainLoss: string;
  gainLossPercent: string | null;
  assets: {
    symbol: string;
    shockPercent: string;
    currentPrice: string;
    stressedPrice: string;
    currentValue: string;
    stressedValue: string;
    gainLoss: string;
  }[];
};
