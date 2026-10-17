import React, { useEffect, useState } from "react";
import { api, ApiError } from "./api";
import type { Portfolio, Quote, Execution } from "./api";
import { Notice } from "./ui";
import { loadIntent, saveIntent, clearIntent } from "./pendingOrder";
import type { OrderIntent } from "./pendingOrder";
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
  const [pending, setPending] = useState<OrderIntent | null>(null);
  const [recovering, setRecovering] = useState(false);
  const [storageError, setStorageError] = useState("");
  function confirmed(execution: Execution, recovered = false) {
    clearIntent(portfolio.id);
    setPending(null);
    setSuccess(
      `${recovered ? "Recovered confirmation: " : ""}${execution.side === "BUY" ? "Bought" : "Sold"} ${qty(execution.quantity)} ${execution.symbol} for ${usd(execution.grossAmount)}. Synthetic close: ${execution.priceDate}, 21:00 UTC. ${execution.side === "SELL" ? `Realized P/L: ${usd(execution.realizedPnl)}.` : ""}`,
    );
    changed();
  }
  useEffect(() => {
    let cancelled = false;
    try {
      const saved = loadIntent(portfolio.id);
      if (saved) {
        setPending(saved);
        setSide(saved.request.side);
        setSymbol(saved.request.symbol);
        setQuantity(saved.request.quantity);
        setDate(saved.request.priceDate);
        setRecovering(true);
        api<Execution>(
          `/portfolios/${portfolio.id}/orders/by-key/${encodeURIComponent(saved.key)}`,
        )
          .then((execution) => {
            if (!cancelled) confirmed(execution, true);
          })
          .catch((e) => {
            if (!cancelled)
              setError(
                e instanceof ApiError && e.status === 404
                  ? "No committed record is visible yet. Retry the saved order with its original key; it may still be in flight."
                  : message(e),
              );
          })
          .finally(() => {
            if (!cancelled) setRecovering(false);
          });
      }
    } catch {
      setStorageError(
        "Tab storage is unavailable or contains an invalid order intent. Review transaction history before submitting another order.",
      );
    }
    return () => {
      cancelled = true;
    };
    // Only inspect this portfolio's journal on mount; changing quote inputs never changes an unresolved intent.
  }, [portfolio.id]);
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
  async function send(intent: OrderIntent) {
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      const execution = await api<Execution>(
        `/portfolios/${portfolio.id}/orders`,
        "POST",
        intent.request,
        intent.key,
      );
      confirmed(execution);
    } catch (e) {
      // 400/422 are definitive validation/business rejections. Transport, 5xx, auth,
      // throttle and conflict outcomes preserve the original intent for safe recovery.
      if (e instanceof ApiError && (e.status === 400 || e.status === 422)) {
        clearIntent(portfolio.id);
        setPending(null);
      }
      setError(
        message(e) +
          (e instanceof ApiError && [400, 422].includes(e.status)
            ? ""
            : " Confirmation is uncertain. Retry the saved order; its original key prevents a duplicate execution."),
      );
    } finally {
      setBusy(false);
    }
  }
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (pending || storageError) return;
    const intent: OrderIntent = {
      key: crypto.randomUUID(),
      request: { symbol, side, quantity, priceDate: date },
    };
    try {
      saveIntent(portfolio.id, intent);
      setPending(intent);
    } catch {
      setStorageError(
        "Unable to save the order recovery key in this tab. Enable session storage before submitting.",
      );
      return;
    }
    await send(intent);
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
        <fieldset disabled={busy || pending !== null || !!storageError}>
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
        {pending && (
          <Notice>
            Saved order awaiting confirmation: {pending.request.side}{" "}
            {pending.request.quantity} {pending.request.symbol} on{" "}
            {pending.request.priceDate}. Inputs stay locked until resolved.{" "}
            <button
              type="button"
              className="text-button"
              disabled={busy || recovering}
              onClick={() => void send(pending)}
            >
              {busy || recovering
                ? "Checking confirmation…"
                : "Retry saved order"}
            </button>
          </Notice>
        )}
        {storageError && <Notice error>{storageError}</Notice>}
        {error && <Notice error>{error}</Notice>}
        {success && <Notice>{success}</Notice>}
      </form>
    </section>
  );
}
