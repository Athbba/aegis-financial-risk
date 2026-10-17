export type User = { id: string; email: string };
export type Portfolio = {
  id: string;
  name: string;
  currency: "USD";
  cash: string;
  lastTradeDate: string | null;
  createdAt: string;
};
export type Holding = { symbol: string; quantity: string; costBasis: string };
export type Detail = { portfolio: Portfolio; holdings: Holding[] };
export type Quote = {
  symbol: string;
  name: string;
  sector: string;
  currency: string;
  datasetVersion: string;
  priceDate: string;
  closePrice: string | null;
};
export type Execution = {
  id: string;
  symbol: string;
  side: "BUY" | "SELL";
  quantity: string;
  executionPrice: string;
  grossAmount: string;
  realizedPnl: string;
  priceDate: string;
  executedAt: string;
  recordedAt: string;
  idempotencyKey: string;
};
export type Ledger = {
  id: string;
  orderId: string | null;
  kind: string;
  amount: string;
  balanceAfter: string;
  recordedAt: string;
};
export type Audit = {
  id: string;
  eventType: string;
  referenceId: string;
  recordedAt: string;
};
export type Page<T> = { items: T[]; page: number; size: number; total: number };

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code: string,
  ) {
    super(message);
  }
}
let csrf: string | null = null;
export function clearCsrf() {
  csrf = null;
}
export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
  key?: string,
): Promise<T> {
  if (method !== "GET" && !csrf) {
    const res = await fetch("/api/auth/csrf", { credentials: "same-origin" });
    if (!res.ok)
      throw new Error("Unable to secure this session. Please retry.");
    csrf = ((await res.json()) as { token: string }).token;
  }
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method,
      credentials: "same-origin",
      headers: {
        ...(body ? { "Content-Type": "application/json" } : {}),
        ...(csrf && method !== "GET" ? { "X-XSRF-TOKEN": csrf } : {}),
        ...(key ? { "Idempotency-Key": key } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new Error("Connection lost. Check your connection and retry.");
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({
      detail: "The server could not complete this request.",
      code: "SERVER_ERROR",
    }));
    if (response.status === 403) clearCsrf();
    throw new ApiError(
      response.status,
      error.detail || "Request failed.",
      error.code || "REQUEST_FAILED",
    );
  }
  return response.status === 204
    ? (undefined as T)
    : ((await response.json()) as T);
}
