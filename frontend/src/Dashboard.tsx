import React, { useEffect, useRef, useState } from "react";
import { api } from "./api";
import type { User, Portfolio, Detail, Holding, Page } from "./api";
import { Brand, Notice, Disclaimer, Pagination } from "./ui";
import { usd, qty, message } from "./format";
import { TradeTicket } from "./TradeTicket";
import { History } from "./History";
import { AnalyticsPanel } from "./AnalyticsPanel";

export function Dashboard({
  user,
  signedOut,
}: {
  user: User;
  signedOut: () => void;
}) {
  const [portfolios, setPortfolios] = useState<Portfolio[]>([]);
  const [portfolioPage, setPortfolioPage] = useState(0);
  const [portfolioTotal, setPortfolioTotal] = useState(0);
  const [selected, setSelected] = useState("");
  const [detail, setDetail] = useState<Detail | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const request = useRef(0);
  const active = useRef(selected);
  active.current = selected;
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    api<Page<Portfolio>>(`/portfolios?page=${portfolioPage}&size=20`)
      .then((data) => {
        if (cancelled) return;
        setPortfolios(data.items);
        setPortfolioTotal(data.total);
        setSelected((current) =>
          data.items.some((p) => p.id === current)
            ? current
            : data.items[0]?.id || "",
        );
      })
      .catch((e) => {
        if (!cancelled) setError(message(e));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [portfolioPage, revision]);
  useEffect(() => {
    const n = ++request.current;
    setDetail((previous) =>
      previous?.portfolio.id === selected ? previous : null,
    );
    if (!selected) return;
    api<Detail>(`/portfolios/${selected}`)
      .then((data) => {
        if (n === request.current) setDetail(data);
      })
      .catch((e) => {
        if (n === request.current) setError(message(e));
      });
  }, [selected, revision]);
  async function create(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const data = new FormData(e.currentTarget);
    try {
      const p = await api<Portfolio>("/portfolios", "POST", {
        name: data.get("name"),
        initialCash: data.get("cash"),
      });
      setPortfolioPage(0);
      setSelected(p.id);
      setCreating(false);
      setRevision((r) => r + 1);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  async function logout() {
    try {
      await api("/auth/logout", "POST");
      signedOut();
    } catch (e) {
      setError(message(e));
    }
  }
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Brand />
        <p className="nav-caption">WORKSPACE</p>
        <a className="nav-item active" href="#main">
          <span aria-hidden="true">▦</span> Portfolio desk{" "}
          <span className="nav-dot" />
        </a>
        <div className="roadmap-note">
          <span className="eyebrow">BUILT ON TRANSPARENCY</span>
          <p>Every decision has a record.</p>
          <small>
            Valuation and performance, grounded in the record. Risk analysis is
            reserved for Phase 3.
          </small>
        </div>
        <div className="sidebar-bottom">
          <span className="status-dot" /> Synthetic dataset v1
          <small>2022 — 2025 · USD only</small>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span className="breadcrumb">
            Workspace <span>/</span> Portfolio desk
          </span>
          <div className="session">
            <span title={user.email}>{user.email}</span>
            <button className="subtle" onClick={() => void logout()}>
              Sign out
            </button>
          </div>
        </header>
        <main id="main" className="content">
          <div className="page-heading">
            <div>
              <p className="eyebrow">YOUR SIMULATION WORKSPACE</p>
              <h1>
                Portfolio desk<span className="heading-dot">.</span>
              </h1>
              <p className="muted">
                Build positions. Inspect the mechanics. Understand each
                decision.
              </p>
            </div>
            <button
              className="primary"
              onClick={() => setCreating(!creating)}
              aria-expanded={creating}
            >
              {creating ? "Close form" : "+ New portfolio"}
            </button>
          </div>
          <Disclaimer />
          {error && (
            <Notice error>
              {error}{" "}
              <button
                className="text-button"
                onClick={() => {
                  setError("");
                  setRevision((r) => r + 1);
                }}
              >
                Retry loading
              </button>
            </Notice>
          )}
          {creating && (
            <section className="panel create-panel">
              <div className="section-heading">
                <h2>Create a simulated portfolio</h2>
                <span className="badge">USD</span>
              </div>
              <form onSubmit={(e) => void create(e)} className="create-form">
                <label>
                  Portfolio name
                  <input
                    name="name"
                    required
                    maxLength={80}
                    placeholder="Long-term conviction"
                  />
                </label>
                <label>
                  Initial simulated cash (USD)
                  <input
                    name="cash"
                    type="number"
                    min="0.01"
                    max="1000000000"
                    step="0.01"
                    defaultValue="100000.00"
                    required
                  />
                </label>
                <button className="primary" disabled={busy}>
                  {busy ? "Creating…" : "Create portfolio"}
                </button>
              </form>
              <p className="help">
                Initial funding is recorded once in the immutable cash ledger.
                No bank connection or real money.
              </p>
            </section>
          )}
          {loading && !portfolios.length ? (
            <p role="status">Loading portfolios…</p>
          ) : !portfolios.length ? (
            <section className="panel empty-state">
              <div className="empty-glyph" aria-hidden="true">
                ↗
              </div>
              <h2>Your first decision starts here.</h2>
              <p>
                Create a portfolio, choose your simulated capital, and explore
                three fictional assets.
              </p>
              <button className="primary" onClick={() => setCreating(true)}>
                Create your first portfolio
              </button>
            </section>
          ) : (
            <>
              <div className="portfolio-toolbar">
                <label>
                  Active portfolio
                  <select
                    value={selected}
                    onChange={(e) => setSelected(e.target.value)}
                  >
                    {portfolios.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </label>
                <div>
                  <span className="badge">SIMULATED · USD</span>
                  <Pagination
                    page={portfolioPage}
                    size={20}
                    total={portfolioTotal}
                    onPage={setPortfolioPage}
                  />
                </div>
              </div>
              {!detail ? (
                <p role="status">Loading portfolio…</p>
              ) : (
                <PortfolioDesk
                  key={selected}
                  detail={detail}
                  revision={revision}
                  changed={() => {
                    if (active.current === selected) setRevision((r) => r + 1);
                  }}
                />
              )}
            </>
          )}
          <footer>
            AEGIS{" "}
            <span>
              Educational investment simulation · No brokerage connectivity
            </span>
            <span>PHASE 02</span>
          </footer>
        </main>
      </div>
    </div>
  );
}

function PortfolioDesk({
  detail,
  revision,
  changed,
}: {
  detail: Detail;
  revision: number;
  changed: () => void;
}) {
  const { portfolio, holdings } = detail;
  return (
    <>
      <AnalyticsPanel portfolio={portfolio} revision={revision} />
      <div className="execution-heading">
        <h2>Simulated execution desk</h2>
        <p>
          Latest executed cash and holdings below. Historical valuations above
          use the selected end date.
        </p>
      </div>
      <section
        className="summary-grid book-summary"
        aria-label="Latest execution balances"
      >
        <div className="metric panel">
          <span>Available simulated cash</span>
          <strong>{usd(portfolio.cash)}</strong>
          <small>USD · Settled balance from the cash ledger</small>
        </div>
        <div className="metric panel">
          <span>Open positions</span>
          <strong>{holdings.length.toString().padStart(2, "0")}</strong>
          <small>Fictional assets currently held</small>
        </div>
        <div className="metric panel">
          <span>Last execution date</span>
          <strong className="date-value">
            {portfolio.lastTradeDate || "No trades yet"}
          </strong>
          <small>Historical simulation · not a live market</small>
        </div>
      </section>
      <div className="desk-grid">
        <section className="panel holdings">
          <div className="section-heading">
            <div>
              <p className="eyebrow">POSITIONS</p>
              <h2>What you own</h2>
            </div>
            <span className="badge">FIFO COST BASIS</span>
          </div>
          {holdings.length ? (
            <Holdings holdings={holdings} />
          ) : (
            <div className="inline-empty">
              <span aria-hidden="true">◇</span>
              <h3>No positions yet</h3>
              <p>
                Your executed buys will appear here with their remaining
                purchase cost.
              </p>
            </div>
          )}
          <p className="help section-foot">
            Cost basis is the remaining settled purchase cost, not current
            market value. The dated market values and P/L appear in the
            valuation section above.
          </p>
        </section>
        <TradeTicket portfolio={portfolio} changed={changed} />
      </div>
      <History portfolioId={portfolio.id} revision={revision} />
    </>
  );
}

function Holdings({ holdings }: { holdings: Holding[] }) {
  return (
    <div className="table-scroll">
      <table>
        <caption className="sr-only">
          Open holdings and FIFO cost basis in USD
        </caption>
        <thead>
          <tr>
            <th>Asset</th>
            <th className="numeric">Quantity</th>
            <th className="numeric">Cost basis (USD)</th>
          </tr>
        </thead>
        <tbody>
          {holdings.map((h) => (
            <tr key={h.symbol}>
              <td>
                <span className={`asset-icon ${h.symbol.toLowerCase()}`}>
                  {h.symbol[0]}
                </span>
                <b>{h.symbol}</b>
                <small className="row-sub">Fictional asset</small>
              </td>
              <td className="numeric">{qty(h.quantity)}</td>
              <td className="numeric">{usd(h.costBasis)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
