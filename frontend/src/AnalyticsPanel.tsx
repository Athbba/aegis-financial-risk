import { lazy, Suspense, useEffect, useState } from "react";
import type React from "react";
import { api } from "./api";
import type { Portfolio } from "./api";
import type { Performance, Valuation, ValueSeries } from "./analyticsTypes";
import { Notice } from "./ui";
import { usd, qty, priceUsd, message } from "./format";
const PerformanceChart = lazy(() =>
  import("./Charts").then((module) => ({ default: module.PerformanceChart })),
);
const AllocationChart = lazy(() =>
  import("./Charts").then((module) => ({ default: module.AllocationChart })),
);
const END = "2025-12-31";
const money = (value: string | null) =>
  value === null ? "Unavailable" : usd(value);
const percent = (value: string | null) =>
  value === null ? "Unavailable" : `${Number(value).toFixed(2)}%`;
const tone = (value: string | null) =>
  value === null || Number(value) === 0
    ? ""
    : Number(value) > 0
      ? "positive"
      : "negative";
const daysBefore = (end: string, days: number) => {
  const date = new Date(end + "T00:00:00Z");
  date.setUTCDate(date.getUTCDate() - days);
  return date.toISOString().slice(0, 10);
};

export function AnalyticsPanel({
  portfolio,
  revision,
}: {
  portfolio: Portfolio;
  revision: number;
}) {
  const start = portfolio.simulationStartDate;
  const [range, setRange] = useState({ from: daysBefore(END, 89), to: END });
  const [from, setFrom] = useState(range.from);
  const [to, setTo] = useState(range.to);
  const [data, setData] = useState<Performance | null>(null);
  const [error, setError] = useState("");
  const [formError, setFormError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let cancelled = false;
    setData(null);
    setError("");
    api<Performance>(
      `/portfolios/${portfolio.id}/performance?from=${range.from}&to=${range.to}`,
    )
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((e) => {
        if (!cancelled) setError(message(e));
      });
    return () => {
      cancelled = true;
    };
  }, [portfolio.id, revision, range, retry]);
  function apply(event: React.FormEvent) {
    event.preventDefault();
    if (!from || !to || from > to || from < start || to > END) {
      setFormError(`Choose an ordered date range between ${start} and ${END}.`);
      return;
    }
    setFormError("");
    setRange({ from, to });
  }
  function preset(days: number | null) {
    const nextFrom =
      days === null ? start : [start, daysBefore(END, days - 1)].sort().at(-1)!;
    setFrom(nextFrom);
    setTo(END);
    setFormError("");
    setRange({ from: nextFrom, to: END });
  }
  return (
    <section
      className="analytics"
      aria-label="Portfolio valuation and performance"
    >
      <div className="analytics-heading">
        <div>
          <p className="eyebrow">VALUATION & PERFORMANCE</p>
          <h2>Your capital, in perspective.</h2>
          <p className="muted">
            End-of-day values reconstructed from the immutable trading record.
          </p>
        </div>
        <span className="badge">SYNTHETIC · USD</span>
      </div>
      <div className="panel range-panel">
        <form onSubmit={apply} noValidate className="range-form">
          <label>
            Performance start date
            <input
              type="date"
              min={start}
              max={END}
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              required
            />
          </label>
          <label>
            Valuation / end date
            <input
              type="date"
              min={start}
              max={END}
              value={to}
              onChange={(e) => setTo(e.target.value)}
              required
            />
          </label>
          <button className="primary" type="submit">
            Apply dates
          </button>
        </form>
        <div className="range-presets" aria-label="Quick date ranges">
          {[
            { name: "1M", days: 30 },
            { name: "3M", days: 90 },
            { name: "1Y", days: 365 },
            { name: "All dates", days: null },
          ].map((p) => (
            <button
              key={p.name}
              className="subtle"
              onClick={() => preset(p.days)}
            >
              {p.name}
            </button>
          ))}
          <span>Latest dataset close: {END} · not a live market</span>
        </div>
        {formError && <Notice error>{formError}</Notice>}
      </div>
      {error ? (
        <Notice error>
          {error}{" "}
          <button
            className="text-button"
            onClick={() => setRetry((n) => n + 1)}
          >
            Retry analytics
          </button>
        </Notice>
      ) : !data ? (
        <div className="panel analytics-loading" role="status">
          Reconstructing portfolio values…
        </div>
      ) : (
        <>
          <p className="valuation-date">
            VALUATION DATE <b>{data.summary.date}</b>
            <span>
              Displayed range: {data.series.from} — {data.series.to}
            </span>
          </p>
          {data.summary.status === "MISSING_PRICES" && (
            <Notice>
              Valuation incomplete: no exact closing price for{" "}
              {data.summary.missingSymbols.join(", ")} on {data.summary.date}.
              Cash and realized P/L remain known; totals and returns are
              unavailable.
            </Notice>
          )}
          <Summary summary={data.summary} />
          <details className="methodology">
            <summary>How funding, returns and missing prices work</summary>
            <p>
              Initial simulated capital is available at the opening of{" "}
              {data.simulationStartDate}, regardless of the account's real
              creation date. It is capital, never a gain. Since-start return is
              total P/L divided by that initial capital. Range return compares
              the two end-of-day values. Neither is a time-weighted or
              money-weighted return. Only exact-date prices are used; held
              assets have explicit gaps on weekends or missing observations.
              Prices are four decimals; values and P/L are rounded to USD cents,
              HALF_EVEN.
            </p>
          </details>
          <div className="analytics-chart-grid">
            <section className="panel performance-panel">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">HISTORICAL EQUITY</p>
                  <h2>Portfolio value over time</h2>
                </div>
                <span className="badge">END OF DAY</span>
              </div>
              <div className="period-strip">
                <div>
                  <span>Range change</span>
                  <b className={tone(data.period.change)}>
                    {money(data.period.change)}
                  </b>
                </div>
                <div>
                  <span>Range return</span>
                  <b className={tone(data.period.returnPercent)}>
                    {percent(data.period.returnPercent)}
                  </b>
                </div>
                <p>
                  {data.period.status === "ZERO_START_VALUE"
                    ? "The starting value is zero; range percentage is undefined."
                    : data.period.status === "MISSING_ENDPOINT_PRICE"
                      ? "An endpoint price is missing; range performance is unavailable."
                      : "Ending value versus starting end-of-day value."}
                </p>
              </div>
              <Suspense
                fallback={
                  <p className="table-message" role="status">
                    Loading chart…
                  </p>
                }
              >
                <PerformanceChart series={data.series} />
              </Suspense>
              <p className="chart-note">
                {data.series.completeDays} valued observations ·{" "}
                {data.series.missingDays} price-gap days. Gaps are not
                interpolated. Dashed line: deposited capital.
              </p>
              <SeriesTable series={data.series} />
            </section>
            <section className="panel allocation-panel">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">CAPITAL COMPOSITION</p>
                  <h2>Cash & asset allocation</h2>
                </div>
              </div>
              <p className="allocation-date">
                By market value on {data.summary.date}
              </p>
              <Suspense
                fallback={
                  <p className="table-message" role="status">
                    Loading allocation…
                  </p>
                }
              >
                <AllocationChart allocation={data.allocation} />
              </Suspense>
              <p className="help section-foot">
                Includes cash. Weights use market value, not purchase cost;
                rounded percentages may not sum to exactly 100%.
              </p>
            </section>
          </div>
          <MarkedHoldings summary={data.summary} />
        </>
      )}
    </section>
  );
}
function Summary({ summary: s }: { summary: Valuation }) {
  return (
    <>
      <div className="valuation-metrics" aria-label="Dated portfolio summary">
        <div className="metric panel">
          <span>Total portfolio value</span>
          <strong data-testid="total-value">{money(s.totalValue)}</strong>
          <small>Cash + marked holdings · {s.date}</small>
        </div>
        <div className="metric panel">
          <span>Total profit / loss</span>
          <strong className={tone(s.totalPnl)} data-testid="total-pnl">
            {money(s.totalPnl)}
          </strong>
          <small>
            <b data-testid="initial-return">{percent(s.returnPercent)}</b>{" "}
            simple return on initial capital
          </small>
        </div>
        <div className="metric panel">
          <span>Cash on valuation date</span>
          <strong data-testid="dated-cash">{usd(s.cash)}</strong>
          <small>Initial capital + sales − purchases</small>
        </div>
        <div className="metric panel">
          <span>Invested assets market value</span>
          <strong data-testid="market-value">{money(s.marketValue)}</strong>
          <small>Exact-date closes · no forward-fill</small>
        </div>
      </div>
      <dl className="pnl-breakdown">
        <div>
          <dt>Initial deposited capital</dt>
          <dd data-testid="initial-capital">{usd(s.initialCapital)}</dd>
        </div>
        <div>
          <dt>
            Realized P/L <small>(executed sales)</small>
          </dt>
          <dd className={tone(s.realizedPnl)} data-testid="realized-pnl">
            {usd(s.realizedPnl)}
          </dd>
        </div>
        <div>
          <dt>
            Unrealized P/L <small>(remaining positions)</small>
          </dt>
          <dd className={tone(s.unrealizedPnl)} data-testid="unrealized-pnl">
            {money(s.unrealizedPnl)}
          </dd>
        </div>
      </dl>
    </>
  );
}
function MarkedHoldings({ summary }: { summary: Valuation }) {
  return (
    <section className="panel marked-holdings">
      <div className="section-heading">
        <div>
          <p className="eyebrow">POSITION PERFORMANCE</p>
          <h2>Holdings at the valuation date</h2>
        </div>
        <span className="badge">{summary.date}</span>
      </div>
      {!summary.positions.length ? (
        <div className="inline-empty">
          <h3>No positions at this date</h3>
          <p>
            Capital remains in cash until the first simulated trade. Future
            trades are not included.
          </p>
        </div>
      ) : (
        <div className="table-scroll">
          <table>
            <caption className="sr-only">
              Marked holdings and position performance in USD
            </caption>
            <thead>
              <tr>
                <th>Asset</th>
                <th className="numeric">Quantity</th>
                <th className="numeric">FIFO cost</th>
                <th className="numeric">Close price</th>
                <th className="numeric">Market value</th>
                <th className="numeric">Unrealized P/L</th>
                <th className="numeric">Realized P/L</th>
                <th className="numeric">Total P/L</th>
              </tr>
            </thead>
            <tbody>
              {summary.positions.map((p) => (
                <tr key={p.symbol}>
                  <td>
                    <b>{p.symbol}</b>
                    <small className="row-sub">
                      {Number(p.quantity) === 0
                        ? "Closed position"
                        : "Fictional asset"}
                    </small>
                  </td>
                  <td className="numeric">{qty(p.quantity)}</td>
                  <td className="numeric">{usd(p.costBasis)}</td>
                  <td className="numeric">
                    {p.valuationPrice === null
                      ? Number(p.quantity) === 0
                        ? "Not required"
                        : "Missing"
                      : priceUsd(p.valuationPrice)}
                  </td>
                  <td className="numeric">{money(p.marketValue)}</td>
                  <td className={`numeric ${tone(p.unrealizedPnl)}`}>
                    {money(p.unrealizedPnl)}
                    <small className="row-sub">
                      {p.unrealizedReturnPercent === null
                        ? "Return undefined"
                        : `${percent(p.unrealizedReturnPercent)} on remaining cost`}
                    </small>
                  </td>
                  <td className={`numeric ${tone(p.realizedPnl)}`}>
                    {usd(p.realizedPnl)}
                  </td>
                  <td className={`numeric ${tone(p.totalPnl)}`}>
                    {money(p.totalPnl)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="help section-foot">
        Historical quantities and costs come from executions and immutable FIFO
        sell allocations. Closed positions retain realized P/L and need no
        valuation price.
      </p>
    </section>
  );
}
function SeriesTable({ series }: { series: ValueSeries }) {
  const [page, setPage] = useState(0);
  const size = 20;
  const rows = series.points.slice(page * size, (page + 1) * size);
  useEffect(() => setPage(0), [series]);
  return (
    <details className="series-details">
      <summary>View dated values and price gaps</summary>
      <div className="table-scroll">
        <table>
          <caption className="sr-only">
            Historical portfolio values in USD
          </caption>
          <thead>
            <tr>
              <th>Date</th>
              <th className="numeric">Cash</th>
              <th className="numeric">Market value</th>
              <th className="numeric">Portfolio value</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.date}>
                <td>{row.date}</td>
                <td className="numeric">{usd(row.cash)}</td>
                <td className="numeric">{money(row.marketValue)}</td>
                <td className="numeric">{money(row.totalValue)}</td>
                <td>
                  {row.status === "COMPLETE"
                    ? "Valued"
                    : `Missing: ${row.missingSymbols.join(", ")}`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="series-pagination">
        <button
          className="subtle"
          disabled={page === 0}
          onClick={() => setPage((p) => p - 1)}
        >
          Earlier values
        </button>
        <span>
          {page + 1} / {Math.ceil(series.points.length / size)}
        </span>
        <button
          className="subtle"
          disabled={(page + 1) * size >= series.points.length}
          onClick={() => setPage((p) => p + 1)}
        >
          Later values
        </button>
      </div>
    </details>
  );
}
