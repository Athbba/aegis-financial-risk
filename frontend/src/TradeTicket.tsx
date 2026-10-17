import React, { useEffect, useRef, useState } from "react";
import { api, ApiError } from "./api";
import type { Portfolio, Quote, Execution } from "./api";
import { Notice } from "./ui";
import { usd, qty, priceUsd, message } from "./format";

export function TradeTicket({
  portfolio,
  changed,
}: {
  portfolio: Portfolio;
  changed: () => void;
}) {
  const [date, setDate] = useState(portfolio.lastTradeDate || "2025-12-31");
  const [quotes, setQuotes] = useState<Quote[]>([]);
  const [symbol, setSymbol] = useState("ALTA");
  const [side, setSide] = useState<"BUY" | "SELL">("BUY");
  const [quantity, setQuantity] = useState("1");
  const [busy, setBusy] = useState(false);
  const [quoteLoading, setQuoteLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  // Preserve the key for retries of an identical payload, including uncertain network responses.
  const attempt = useRef<{ payload: string; key: string } | null>(null);
  useEffect(() => {
    let cancelled = false;
    setQuoteLoading(true);
    setQuotes([]);
    setError("");
    api<Quote[]>(`/assets?date=${date}`)
      .then((q) => {
        if (!cancelled) setQuotes(q);
      })
      .catch((e) => {
        if (!cancelled) setError(message(e));
      })
      .finally(() => {
        if (!cancelled) setQuoteLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [date]);
  const quote = quotes.find((q) => q.symbol === symbol);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setSuccess("");
    const body = { symbol, side, quantity, priceDate: date };
    const payload = JSON.stringify(body);
    if (attempt.current?.payload !== payload)
      attempt.current = { payload, key: crypto.randomUUID() };
    try {
      const execution = await api<Execution>(
        `/portfolios/${portfolio.id}/orders`,
        "POST",
        body,
        attempt.current!.key,
      );
      setSuccess(
        `${execution.side === "BUY" ? "Bought" : "Sold"} ${qty(execution.quantity)} ${execution.symbol} for ${usd(execution.grossAmount)}. Synthetic close: ${execution.priceDate}, 21:00 UTC. ${execution.side === "SELL" ? `Realized P/L: ${usd(execution.realizedPnl)}.` : ""}`,
      );
      attempt.current = null;
      changed();
    } catch (e) {
      setError(
        message(e) +
          (e instanceof ApiError
            ? ""
            : " Your order may have executed. Retry the unchanged order to reuse its request key."),
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="panel ticket">
      <div className="section-heading">
        <div>
          <p className="eyebrow">HISTORICAL EXECUTION</p>
          <h2>Simulate an order</h2>
        </div>
        <span className="ticket-symbol" aria-hidden="true">
          →
        </span>
      </div>
      <form onSubmit={(e) => void submit(e)}>
        <fieldset disabled={busy}>
          <legend className="sr-only">Simulated order details</legend>
          <div className="segmented" aria-label="Order side">
            <button
              type="button"
              className={side === "BUY" ? "selected" : ""}
              aria-pressed={side === "BUY"}
              onClick={() => setSide("BUY")}
            >
              Buy
            </button>
            <button
              type="button"
              className={side === "SELL" ? "selected" : ""}
              aria-pressed={side === "SELL"}
              onClick={() => setSide("SELL")}
            >
              Sell
            </button>
          </div>
          <label>
            Fictional asset
            <select value={symbol} onChange={(e) => setSymbol(e.target.value)}>
              <option value="ALTA">ALTA · Alta Systems</option>
              <option value="BRIM">BRIM · Brim Infrastructure</option>
              <option value="COVE">COVE · Cove Energy</option>
            </select>
          </label>
          <div className="field-grid">
            <label>
              Quantity
              <input
                aria-label="Quantity"
                type="number"
                min="0.000001"
                max="1000000"
                step="0.000001"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                required
              />
            </label>
            <label>
              Execution date
              <input
                type="date"
                min={portfolio.lastTradeDate || "2022-01-03"}
                max="2025-12-31"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                required
              />
            </label>
          </div>
          <div className="quote-box">
            <span>Synthetic closing price</span>
            <strong>
              {quoteLoading
                ? "Loading…"
                : quote?.closePrice
                  ? `${priceUsd(quote.closePrice)} USD`
                  : "Unavailable"}
            </strong>
          </div>
          <p className="help">
            Execution uses the exact stored price (4 decimals), settled to USD
            cents. Weekdays in 2022–2025 only; dates must progress forward. No
            fees or slippage.
          </p>
          <button
            className="primary full"
            disabled={quoteLoading || !quote?.closePrice}
          >
            {busy ? "Executing…" : `Simulate ${side.toLowerCase()}`}{" "}
            <span aria-hidden="true">→</span>
          </button>
        </fieldset>
        {error && <Notice error>{error}</Notice>}
        {success && <Notice>{success}</Notice>}
      </form>
    </section>
  );
}
