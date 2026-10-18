import { useEffect, useState } from "react";
import { api } from "./api";
import type { Execution, Ledger, Audit, Page } from "./api";
import { Notice, Pagination } from "./ui";
import { usd, qty, time, message } from "./format";

export function History({
  portfolioId,
  revision,
}: {
  portfolioId: string;
  revision: number;
}) {
  const [tab, setTab] = useState<"orders" | "ledger" | "audit">("orders");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Execution | Ledger | Audit> | null>(
    null,
  );
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const [filter, setFilter] = useState("");
  useEffect(() => {
    let cancelled = false;
    setData(null);
    setError("");
    api<Page<Execution | Ledger | Audit>>(
      `/portfolios/${portfolioId}/${tab}?page=${page}&size=10`,
    )
      .then((d) => {
        if (!cancelled) setData(d);
      })
      .catch((e) => {
        if (!cancelled) setError(message(e));
      });
    return () => {
      cancelled = true;
    };
  }, [portfolioId, revision, tab, page, retry]);
  const rows =
    data?.items.filter((row) =>
      JSON.stringify(row).toLowerCase().includes(filter.toLowerCase()),
    ) || [];
  return (
    <section className="panel history">
      <div className="section-heading">
        <div>
          <p className="eyebrow">TRACEABLE BY DESIGN</p>
          <h2>Activity & records</h2>
        </div>
        <span className="badge">APPEND-ONLY HISTORY</span>
      </div>
      <div className="history-toolbar">
        <div className="history-tabs" aria-label="Record type">
          {(["orders", "ledger", "audit"] as const).map((t) => (
            <button
              key={t}
              className={tab === t ? "selected" : ""}
              aria-pressed={tab === t}
              onClick={() => {
                setTab(t);
                setPage(0);
                setFilter("");
              }}
            >
              {t === "orders"
                ? "Transactions"
                : t === "ledger"
                  ? "Cash ledger"
                  : "Audit events"}
            </button>
          ))}
        </div>
        <label className="filter">
          Filter this page
          <input
            type="search"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Symbol, side or event…"
          />
        </label>
      </div>
      {error ? (
        <Notice error>
          {error}{" "}
          <button
            className="text-button"
            onClick={() => setRetry((n) => n + 1)}
          >
            Retry records
          </button>
        </Notice>
      ) : !data ? (
        <p className="table-message" role="status">
          Loading records…
        </p>
      ) : !rows.length ? (
        <p className="table-message">
          {filter
            ? "No records match this filter on this page."
            : "No records yet. Executed activity will appear here."}
        </p>
      ) : (
        <div
          className="table-scroll"
          tabIndex={0}
          role="region"
          aria-label="Financial history table"
        >
          <table>
            <caption className="sr-only">
              {tab === "orders"
                ? "Simulated transactions"
                : tab === "ledger"
                  ? "Cash ledger in USD"
                  : "Portfolio audit events"}
            </caption>
            <thead>
              <tr>
                {tab === "orders" ? (
                  <>
                    <th>Asset / side</th>
                    <th>Historical date</th>
                    <th className="numeric">Quantity</th>
                    <th className="numeric">Price (USD)</th>
                    <th className="numeric">Total (USD)</th>
                    <th className="numeric">Realized P/L</th>
                  </>
                ) : tab === "ledger" ? (
                  <>
                    <th>Entry</th>
                    <th>Recorded at</th>
                    <th className="numeric">Amount (USD)</th>
                    <th className="numeric">Balance (USD)</th>
                  </>
                ) : (
                  <>
                    <th>Event</th>
                    <th>Recorded at</th>
                    <th>Reference</th>
                  </>
                )}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) =>
                "side" in row ? (
                  <tr key={row.id}>
                    <td>
                      <b>{row.symbol}</b>{" "}
                      <span className={`side-tag ${row.side.toLowerCase()}`}>
                        {row.side}
                      </span>
                    </td>
                    <td>
                      {row.priceDate}
                      <small className="row-sub">
                        Synthetic close · 21:00 UTC
                      </small>
                    </td>
                    <td className="numeric">{qty(row.quantity)}</td>
                    <td className="numeric">
                      {Number(row.executionPrice).toFixed(4)}
                    </td>
                    <td className="numeric">{usd(row.grossAmount)}</td>
                    <td className="numeric">
                      {row.side === "SELL" ? usd(row.realizedPnl) : "—"}
                    </td>
                  </tr>
                ) : "kind" in row ? (
                  <tr key={row.id}>
                    <td>
                      <span className="side-tag">{row.kind}</span>
                    </td>
                    <td>{time(row.recordedAt)}</td>
                    <td className="numeric">{usd(row.amount)}</td>
                    <td className="numeric">{usd(row.balanceAfter)}</td>
                  </tr>
                ) : (
                  <tr key={row.id}>
                    <td>
                      <span className="side-tag">{row.eventType}</span>
                    </td>
                    <td>{time(row.recordedAt)}</td>
                    <td className="reference">{row.referenceId}</td>
                  </tr>
                ),
              )}
            </tbody>
          </table>
        </div>
      )}
      {data && (
        <div className="history-bottom">
          <span>
            {data.total} {data.total === 1 ? "record" : "records"} · Filter
            applies to this page only
          </span>
          <Pagination
            page={page}
            size={10}
            total={data.total}
            onPage={setPage}
          />
        </div>
      )}
    </section>
  );
}
