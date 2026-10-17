import { lazy, Suspense, useEffect, useId, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { api } from "./api";
import type { Portfolio } from "./api";
import type {
  Metric,
  RiskSummary,
  Scenario,
  StressResult,
  VaR,
} from "./riskTypes";
import { message, priceUsd, usd } from "./format";
import { Notice, Pagination } from "./ui";
const ExposureChart = lazy(() =>
  import("./RiskCharts").then((m) => ({ default: m.ExposureChart })),
);
const DistributionChart = lazy(() =>
  import("./RiskCharts").then((m) => ({ default: m.DistributionChart })),
);
const GainLossChart = lazy(() =>
  import("./RiskCharts").then((m) => ({ default: m.GainLossChart })),
);
const pct = (v: string | null) =>
  v === null ? "Unavailable" : `${Number(v).toFixed(2)}%`;
const money = (v: string | null) => (v === null ? "Unavailable" : usd(v));
const reason = (s: string) =>
  ({
    COMPLETE: "Complete aligned sample",
    CASH_ONLY: "Cash only · zero asset-price exposure",
    MISSING_PRICES: "Exact selected-date prices are missing",
    MISSING_HISTORY: "Missing weekday history · sample estimates unavailable",
    MISSING_VALUATIONS: "Incomplete weekday equity history",
    ZERO_EQUITY: "Zero equity · percentage undefined",
    INSUFFICIENT_OBSERVATIONS: "Insufficient observations",
  })[s] ?? s;
function Term({ name, children }: { name: string; children: ReactNode }) {
  const id = useId();
  return (
    <span className="risk-term">
      <button
        type="button"
        aria-label={`Explain ${name}`}
        aria-describedby={id}
      >
        ?
      </button>
      <span id={id} role="tooltip">
        {children}
      </span>
    </span>
  );
}
function VolCard({
  title,
  metric,
  annual = false,
}: {
  title: string;
  metric: Metric;
  annual?: boolean;
}) {
  return (
    <article className="metric panel">
      <span>
        {title}
        <Term name={title}>
          Sample standard deviation of daily fixed-exposure historical returns.
          {annual
            ? " Multiplied by √252; an annualization convention, not a forecast."
            : " Measures dispersion, not the direction of returns."}
        </Term>
      </span>
      <strong data-testid={annual ? "risk-annual-vol" : "risk-daily-vol"}>
        {pct(metric.value)}
      </strong>
      <small>
        {reason(metric.status)}
        {metric.status === "INSUFFICIENT_OBSERVATIONS"
          ? ` · ${metric.observations}/${metric.minimum} required`
          : ""}
      </small>
    </article>
  );
}
function VarCard({ confidence, value }: { confidence: number; value: VaR }) {
  return (
    <article className="metric panel">
      <span>
        One-day historical VaR · {confidence}%
        <Term name={`VaR ${confidence}%`}>
          Nearest-rank {confidence}th percentile of hypothetical USD losses
          under past shocks. VaR is not the maximum possible loss.
        </Term>
      </span>
      <strong data-testid={`risk-var${confidence}`}>{money(value.usd)}</strong>
      <small>
        {value.percent !== null
          ? `${pct(value.percent)} of selected equity · `
          : ""}
        {reason(value.status)}
        {value.status === "INSUFFICIENT_OBSERVATIONS"
          ? ` · ${value.observations}/${value.minimum} required`
          : ""}
      </small>
    </article>
  );
}
export function RiskLab({
  portfolio,
  revision,
}: {
  portfolio: Portfolio;
  revision: number;
}) {
  const [date, setDate] = useState("2025-12-31"),
    [lookback, setLookback] = useState("730");
  const [query, setQuery] = useState({ date: "2025-12-31", lookback: "730" });
  const [data, setData] = useState<RiskSummary | null>(null),
    [error, setError] = useState(""),
    [retry, setRetry] = useState(0),
    [formError, setFormError] = useState("");
  const [page, setPage] = useState(0);
  useEffect(() => {
    let cancelled = false;
    setData(null);
    setError("");
    setPage(0);
    api<RiskSummary>(
      `/portfolios/${portfolio.id}/risk/summary?date=${query.date}&lookbackDays=${query.lookback}`,
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
  }, [portfolio.id, revision, query, retry]);
  function apply(e: FormEvent) {
    e.preventDefault();
    if (
      !date ||
      date < portfolio.simulationStartDate ||
      date > "2025-12-31" ||
      Number(lookback) < 2 ||
      Number(lookback) > 1461
    ) {
      setFormError("Choose a supported dataset date and 2–1461 calendar days.");
      return;
    }
    setFormError("");
    setQuery({ date, lookback });
  }
  return (
    <section className="risk-lab" aria-label="Risk Lab" id="risk-lab">
      <div className="risk-heading">
        <div>
          <p className="eyebrow">HISTORICAL RISK / HYPOTHETICAL SCENARIOS</p>
          <h2>Understand the exposure.</h2>
          <p className="muted">
            Explore past shocks. Challenge your assumptions. See what drives the
            result.
          </p>
        </div>
        <span className="badge">EDUCATIONAL · SYNTHETIC DATA</span>
      </div>
      <form className="risk-controls panel" onSubmit={apply}>
        <label>
          Risk valuation date
          <input
            aria-label="Risk valuation date"
            type="date"
            value={date}
            min={portfolio.simulationStartDate}
            max="2025-12-31"
            required
            onChange={(e) => setDate(e.target.value)}
          />
        </label>
        <label>
          Risk lookback window
          <select
            value={lookback}
            onChange={(e) => setLookback(e.target.value)}
          >
            <option value="30">30 calendar days</option>
            <option value="90">90 calendar days</option>
            <option value="365">1 year · 365 days</option>
            <option value="730">2 years · 730 days</option>
            <option value="1461">Full dataset · up to 1,461 days</option>
          </select>
        </label>
        <button className="primary">Analyze risk</button>
        <p className="help">
          Uses only records on or before your selected date. Weekends are
          excluded from samples.
        </p>
      </form>
      {formError && <Notice error>{formError}</Notice>}
      {error ? (
        <Notice error>
          {error}{" "}
          <button
            className="text-button"
            onClick={() => setRetry((r) => r + 1)}
          >
            Retry risk analytics
          </button>
        </Notice>
      ) : !data ? (
        <div className="panel risk-loading" role="status">
          Reconstructing equity and aligning historical shocks…
        </div>
      ) : (
        <>
          <div className="risk-context">
            <span className="status-dot" />
            <p data-testid="risk-window">
              Analysis: {data.from} — {data.date} ·{" "}
              {data.analysis.returns.points.length} valid daily shocks /{" "}
              {data.analysis.returns.candidatePairs} candidate pairs
            </p>
            <strong>Selected equity {money(data.valuation.totalValue)}</strong>
          </div>
          <Notice>
            Current-exposure risk holds the selected date’s weights fixed,
            including cash. This distribution is hypothetical; it is not
            historical realized strategy performance. Volatility and VaR are
            risk measures, not forecasts.
          </Notice>
          {!["COMPLETE", "CASH_ONLY"].includes(
            data.analysis.returns.status,
          ) && (
            <Notice error>
              {reason(data.analysis.returns.status)}.{" "}
              {data.analysis.returns.missingPairs} return pairs affected;{" "}
              {data.analysis.returns.missingPrices.length} missing symbol/date
              closes. No prices are carried forward.
            </Notice>
          )}
          <div className="risk-metrics">
            <VolCard
              title="Daily historical volatility"
              metric={data.analysis.dailyVolatility}
            />
            <VolCard
              title="Annualized volatility"
              metric={data.analysis.annualVolatility}
              annual
            />
            <VarCard confidence={95} value={data.analysis.var95} />
            <VarCard confidence={99} value={data.analysis.var99} />
          </div>
          <div className="risk-grid">
            <section className="panel risk-panel">
              <div className="section-heading">
                <h3>Exposure & concentration</h3>
                <Term name="Concentration">
                  Sum of squared risky-asset weights in total equity. Cash
                  excluded; range 0–1. This is exposure concentration, not a
                  variance contribution or risk score.
                </Term>
              </div>
              {data.valuation.totalValue !== null &&
              Number(data.valuation.totalValue) > 0 ? (
                <Suspense
                  fallback={<p role="status">Loading exposure chart…</p>}
                >
                  <ExposureChart assets={data.analysis.assets} />
                </Suspense>
              ) : (
                <p className="help">
                  Exposure chart unavailable without positive, complete equity.
                </p>
              )}
              <div className="table-wrap">
                <table aria-label="Risk exposures and per-asset volatility">
                  <thead>
                    <tr>
                      <th>Asset</th>
                      <th>Value</th>
                      <th>Weight</th>
                      <th>Daily vol.</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.analysis.assets.map((a) => (
                      <tr key={a.symbol}>
                        <td>{a.symbol}</td>
                        <td>{money(a.marketValue)}</td>
                        <td>{pct(a.weightPercent)}</td>
                        <td>
                          <span title={reason(a.dailyVolatility.status)}>
                            {pct(a.dailyVolatility.value)}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="help">
                Concentration Σw²:{" "}
                {data.analysis.concentration === null
                  ? "Unavailable"
                  : Number(data.analysis.concentration).toFixed(4)}{" "}
                · weights include cash in the denominator.
              </p>
            </section>
            <section className="panel risk-panel">
              <div className="section-heading">
                <h3>Distribution of historical shocks</h3>
                <Term name="Historical shocks">
                  Past consecutive weekday close-to-close asset returns applied
                  independently to selected-date marked exposures. Cash has zero
                  return. Missing weekdays invalidate adjacent pairs.
                </Term>
              </div>
              <p className="help">
                {reason(data.analysis.returns.status)} ·{" "}
                {data.analysis.returns.points.length} aligned observations
              </p>
              <Suspense fallback={<p role="status">Loading distribution…</p>}>
                <DistributionChart returns={data.analysis.returns} />
              </Suspense>
              <p className="risk-caveat">
                VaR is not a maximum-loss guarantee. Unseen shocks can be worse;
                500 observations still contain only about five in the 1% tail.
                Tail estimates remain uncertain.
              </p>
            </section>
          </div>
          <section className="panel drawdown-panel">
            <div>
              <p className="eyebrow">ACTUAL RECONSTRUCTED SIMULATED HISTORY</p>
              <h3>
                Maximum portfolio drawdown
                <Term name="Maximum drawdown">
                  Largest decline from a running equity peak to a later trough,
                  including trades effective on each historical day. An
                  incomplete weekday makes the entire selected window
                  unavailable.
                </Term>
              </h3>
              <p className="help">
                {reason(data.drawdown.status)} · {data.drawdown.observations}{" "}
                complete weekday equity observations
              </p>
            </div>
            <div>
              <strong data-testid="risk-drawdown">
                {pct(data.drawdown.percent)}
              </strong>
              <p className="help">
                Peak {data.drawdown.peakDate ?? "unavailable"} → trough{" "}
                {data.drawdown.troughDate ?? "unavailable"}
              </p>
            </div>
            <p className="help">
              Drawdown describes the observed simulated strategy history. VaR
              describes current exposures under past shocks. The measures use
              different holdings over time.
            </p>
          </section>
          <StressLab
            key={`${portfolio.id}:${data.date}:${revision}:${retry}`}
            id={portfolio.id}
            data={data}
          />
          <details className="panel risk-panel">
            <summary>Inspect historical observations & data gaps</summary>
            <p className="help">
              Valid aligned shocks only; incomplete samples are not used for
              reported estimates. Loss is signed: negative means a gain.
            </p>
            <div className="table-wrap">
              <table aria-label="Historical risk observations">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Shock return</th>
                    <th>Hypothetical loss (USD)</th>
                  </tr>
                </thead>
                <tbody>
                  {data.analysis.returns.points
                    .slice(page * 20, page * 20 + 20)
                    .map((p) => (
                      <tr key={p.date}>
                        <td>{p.date}</td>
                        <td>{pct(p.returnPercent)}</td>
                        <td>{usd(p.lossUsd)}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={page}
              size={20}
              total={data.analysis.returns.points.length}
              onPage={setPage}
            />
            {data.analysis.returns.missingPrices.length > 0 && (
              <p className="help">
                Missing closes (first 50 of{" "}
                {data.analysis.returns.missingPrices.length}):{" "}
                {data.analysis.returns.missingPrices
                  .slice(0, 50)
                  .map((p) => `${p.symbol} ${p.date}`)
                  .join(", ")}
              </p>
            )}
            {data.drawdown.missingDates.length > 0 && (
              <p className="help">
                Missing weekday valuations (first 50):{" "}
                {data.drawdown.missingDates.slice(0, 50).join(", ")}
              </p>
            )}
          </details>
          <details className="panel risk-panel">
            <summary>Methodology & model limits</summary>
            <div className="risk-method">
              <p>
                Sample standard deviation uses n−1; annual volatility multiplies
                daily volatility by √252. Required samples: 30 for volatility,
                100 for VaR95, 500 for VaR99. Cash-only zero price risk is
                analytical and does not require a synthetic sample.
              </p>
              <p>
                VaR sorts decimal losses and selects rank ceil(confidence × n),
                floored at zero. Exposure is quantity × selected close rounded
                to cents. Return calculations retain 18 decimal places;
                statistical square roots use floating point. No future
                observations or trades enter this analysis.
              </p>
              <p>
                The lookback is calendar days, clipped at simulation start; the
                first weekday is only a price baseline. Drawdown starts from the
                first eligible equity in that window. Missing weekdays make
                affected estimates unavailable; weekends are non-trading days.
                Synthetic data does not model exchange holidays.
              </p>
              <p>
                No liquidity, spreads, taxes, interest, leverage, FX or trading
                costs are modeled. These historical and illustrative scenarios
                do not predict future losses. AEGIS is educational software, not
                an institution-grade risk model.
              </p>
            </div>
          </details>
        </>
      )}
    </section>
  );
}
const presets: Scenario[] = [
  { name: "Broad selloff", commonShockPercent: "-10", overrides: {} },
  { name: "Severe selloff", commonShockPercent: "-25", overrides: {} },
  { name: "Relief rally", commonShockPercent: "10", overrides: {} },
];
function StressLab({ id, data }: { id: string; data: RiskSummary }) {
  const held = data.valuation.positions.filter((p) => Number(p.quantity) > 0);
  const [common, setCommon] = useState("0"),
    [overrides, setOverrides] = useState<Record<string, string>>({}),
    [saved, setSaved] = useState<Scenario[]>([]),
    [name, setName] = useState("");
  const [results, setResults] = useState<StressResult[] | null>(null),
    [error, setError] = useState(""),
    [retry, setRetry] = useState(0),
    [selected, setSelected] = useState("Custom");
  const custom: Scenario = {
    name: "Custom",
    commonShockPercent: common,
    overrides,
  };
  const available = data.valuation.totalValue !== null;
  useEffect(() => {
    let cancelled = false;
    setResults(null);
    setError("");
    if (!available) return;
    const timer = setTimeout(() => {
      api<StressResult[]>(`/portfolios/${id}/risk/stress`, "POST", {
        date: data.date,
        scenarios: [
          ...presets,
          ...saved,
          { name: "Custom", commonShockPercent: common, overrides },
        ],
      })
        .then((r) => {
          if (!cancelled) setResults(r);
        })
        .catch((e) => {
          if (!cancelled) setError(message(e));
        });
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [id, data.date, available, common, overrides, saved, retry]);
  const active = results?.find((r) => r.name === selected) ?? results?.at(-1);
  function save(e: FormEvent) {
    e.preventDefault();
    const next = name.trim();
    if (
      !next ||
      saved.length >= 4 ||
      [...presets, ...saved, custom].some((s) => s.name === next)
    )
      return;
    setSaved((s) => [
      ...s,
      { ...custom, name: next, overrides: { ...overrides } },
    ]);
    setSelected(next);
    setName("");
  }
  return (
    <section className="panel stress-lab" aria-label="Stress-test laboratory">
      <div className="section-heading">
        <div>
          <p className="eyebrow">THE STRESS-TEST LABORATORY</p>
          <h3>What if prices moved?</h3>
        </div>
        <span className="badge">READ-ONLY SIMULATION</span>
      </div>
      <p className="muted">
        Reprice the selected date’s quantities. Cash stays unchanged. No trades
        are executed or financial records changed.
      </p>
      {!available ? (
        <Notice>
          Stress simulation unavailable: exact selected-date asset prices are
          required.
        </Notice>
      ) : (
        <>
          <div className="stress-layout">
            <div className="stress-controls">
              <label>
                Common asset shock <output>{common}%</output>
                <input
                  aria-label="Common asset shock"
                  type="range"
                  min="-100"
                  max="100"
                  step="1"
                  value={common}
                  onChange={(e) => {
                    setCommon(e.target.value);
                    setSelected("Custom");
                  }}
                />
              </label>
              <div className="stress-scale">
                <span>−100% / total asset loss</span>
                <span>+100%</span>
              </div>
              <div className="scenario-buttons">
                {presets.map((p) => (
                  <button
                    key={p.name}
                    className="subtle"
                    onClick={() => {
                      setCommon(p.commonShockPercent);
                      setOverrides({});
                      setSelected("Custom");
                    }}
                  >
                    {p.name} {p.commonShockPercent}%
                  </button>
                ))}
                <button
                  className="subtle"
                  onClick={() => {
                    setCommon("0");
                    setOverrides({});
                    setSelected("Custom");
                  }}
                >
                  Reset to zero
                </button>
              </div>
              <fieldset>
                <legend>Per-asset overrides</legend>
                {held.length ? (
                  held.map((p) => (
                    <div className="asset-shock" key={p.symbol}>
                      <label>
                        <input
                          type="checkbox"
                          checked={p.symbol in overrides}
                          onChange={(e) => {
                            setOverrides((current) => {
                              const next = { ...current };
                              if (e.target.checked) next[p.symbol] = common;
                              else delete next[p.symbol];
                              return next;
                            });
                            setSelected("Custom");
                          }}
                        />{" "}
                        Override {p.symbol}
                      </label>
                      <label className="sr-only" htmlFor={`shock-${p.symbol}`}>
                        {p.symbol} shock
                      </label>
                      <input
                        id={`shock-${p.symbol}`}
                        aria-label={`${p.symbol} shock`}
                        type="range"
                        min="-100"
                        max="100"
                        step="1"
                        disabled={!(p.symbol in overrides)}
                        value={overrides[p.symbol] ?? common}
                        onChange={(e) => {
                          setOverrides((o) => ({
                            ...o,
                            [p.symbol]: e.target.value,
                          }));
                          setSelected("Custom");
                        }}
                      />
                      <output>{overrides[p.symbol] ?? common}%</output>
                    </div>
                  ))
                ) : (
                  <p className="help">
                    Cash-only portfolio: every valid scenario leaves value
                    unchanged.
                  </p>
                )}
              </fieldset>
              <form className="save-scenario" onSubmit={save}>
                <label>
                  Scenario name
                  <input
                    value={name}
                    maxLength={60}
                    placeholder="My scenario"
                    onChange={(e) => setName(e.target.value)}
                  />
                </label>
                <button
                  className="subtle"
                  disabled={
                    !name.trim() ||
                    saved.length >= 4 ||
                    [...presets, ...saved, custom].some(
                      (s) => s.name === name.trim(),
                    )
                  }
                >
                  Save comparison
                </button>
              </form>
              <p className="help">
                Save up to 4 custom snapshots alongside the 3 illustrative
                presets and current custom scenario. Names must be unique.
              </p>
              {saved.length > 0 && (
                <div className="scenario-buttons">
                  {saved.map((s) => (
                    <button
                      className="subtle"
                      key={s.name}
                      onClick={() => {
                        setSaved((all) => all.filter((x) => x.name !== s.name));
                        setSelected("Custom");
                      }}
                    >
                      Remove {s.name}
                    </button>
                  ))}
                </div>
              )}
            </div>
            <div className="stress-results" aria-live="polite">
              {error ? (
                <Notice error>
                  {error}{" "}
                  <button
                    className="text-button"
                    onClick={() => setRetry((r) => r + 1)}
                  >
                    Retry stress simulation
                  </button>
                </Notice>
              ) : !results ? (
                <p role="status">Calculating scenario comparisons…</p>
              ) : (
                <>
                  <p className="eyebrow">
                    {active?.name} / {data.date}
                  </p>
                  <div className="stress-value">
                    <span>
                      Current equity{" "}
                      <b>{money(active?.currentValue ?? null)}</b>
                    </span>
                    <span aria-hidden="true">→</span>
                    <span>
                      Stressed equity{" "}
                      <b data-testid="stress-value">
                        {money(active?.stressedValue ?? null)}
                      </b>
                    </span>
                  </div>
                  <p
                    className={`stress-change ${Number(active?.gainLoss) < 0 ? "negative" : "positive"}`}
                    data-testid="stress-change"
                  >
                    {money(active?.gainLoss ?? null)} /{" "}
                    {pct(active?.gainLossPercent ?? null)}
                    <small>
                      Hypothetical signed gain / loss · cash{" "}
                      {money(active?.cash ?? null)} unchanged
                    </small>
                  </p>
                  <Suspense
                    fallback={<p role="status">Loading scenario chart…</p>}
                  >
                    <GainLossChart
                      label="Stress scenario gain and loss comparison"
                      rows={results.map((r) => ({
                        name: r.name,
                        value: r.gainLoss,
                      }))}
                    />
                  </Suspense>
                </>
              )}
            </div>
          </div>
          {results && (
            <>
              <div className="table-wrap">
                <table aria-label="Stress scenario comparison">
                  <thead>
                    <tr>
                      <th>Scenario</th>
                      <th>Default shock</th>
                      <th>Current value</th>
                      <th>Stressed value</th>
                      <th>Gain / loss</th>
                      <th>% of equity</th>
                    </tr>
                  </thead>
                  <tbody>
                    {results.map((r) => (
                      <tr
                        key={r.name}
                        className={
                          active?.name === r.name ? "selected-scenario" : ""
                        }
                      >
                        <td>
                          <button
                            className="text-button"
                            aria-pressed={active?.name === r.name}
                            onClick={() => setSelected(r.name)}
                          >
                            Inspect {r.name}
                          </button>
                        </td>
                        <td>
                          {pct(r.commonShockPercent)}
                          {r.assets.some(
                            (a) => a.shockPercent !== r.commonShockPercent,
                          )
                            ? " · overrides"
                            : ""}
                        </td>
                        <td>{usd(r.currentValue)}</td>
                        <td>{usd(r.stressedValue)}</td>
                        <td>{usd(r.gainLoss)}</td>
                        <td>{pct(r.gainLossPercent)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {active && (
                <div className="stress-breakdown">
                  <h3>{active.name}: assumptions & asset contributions</h3>
                  <p className="help">
                    All presets are illustrative: broad −10%, severe −25%,
                    relief +10% on every risky asset. Overrides below replace
                    the default shock. Cash has no price shock; prices cannot go
                    below zero.
                  </p>
                  <div className="table-wrap">
                    <table aria-label="Stress asset assumptions and contributions">
                      <thead>
                        <tr>
                          <th>Asset</th>
                          <th>Effective shock</th>
                          <th>Current price</th>
                          <th>Stressed price</th>
                          <th>Stressed value</th>
                          <th>Contribution (USD)</th>
                        </tr>
                      </thead>
                      <tbody>
                        {active.assets.map((a) => (
                          <tr key={a.symbol}>
                            <td>{a.symbol}</td>
                            <td>{pct(a.shockPercent)}</td>
                            <td>{priceUsd(a.currentPrice)}</td>
                            <td>{priceUsd(a.stressedPrice)}</td>
                            <td>{usd(a.stressedValue)}</td>
                            <td>{usd(a.gainLoss)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {active.assets.length > 0 && (
                    <Suspense
                      fallback={<p role="status">Loading contributions…</p>}
                    >
                      <GainLossChart
                        label="Asset contributions to stress gain and loss"
                        rows={active.assets.map((a) => ({
                          name: a.symbol,
                          value: a.gainLoss,
                        }))}
                      />
                    </Suspense>
                  )}
                </div>
              )}
            </>
          )}
          <p className="risk-caveat">
            Price′ = price × (1 + shock / 100). Each aggregate position is
            marked to cents with HALF_EVEN rounding; contributions reconcile to
            the total. Shocks are limited to −100%…+100%. Scenarios omit
            liquidity and trading costs and are not forecasts.
          </p>
        </>
      )}
    </section>
  );
}
